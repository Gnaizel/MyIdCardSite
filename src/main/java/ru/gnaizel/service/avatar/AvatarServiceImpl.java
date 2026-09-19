package ru.gnaizel.service.avatar;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import ru.gnaizel.exception.AvatarRejected;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarServiceImpl implements AvatarService {
    /* Фото с телефона приходит на 2-5 МБ, а отдавать такое на страницу нельзя.
       Поэтому присланное приводится к квадрату 640 на 640: при колонке
       в 290px это ровно двойной размер под retina.

       Качество при этом подбирается под вес, а не задаётся жёстко: при
       фиксированном 0.85 гладкое фото весит 40 КБ, а детализированное 90.
       Разницу между ступенями видно на графике загрузки, а не на картинке,
       поэтому идём сверху вниз и останавливаемся, как только уложились. */
    private static final float[] QUALITY_STEPS = {0.85f, 0.78f, 0.70f, 0.62f};
    private static final int TARGET_BYTES = 55 * 1024;

    /* Столько байт максимум принимаем на вход. Больше телеграм и не пришлёт
       сжатым фото, а файл вполне может быть и таким. */
    private static final int MAX_UPLOAD = 10 * 1024 * 1024;

    /* Картинка 12 мегапикселей разворачивается в памяти примерно в 48 МБ.
       На сервере с гигабайтом это потолок, за которым начинается OutOfMemory,
       поэтому размер проверяется по заголовку, до распаковки. */
    private static final long MAX_PIXELS = 12_000_000L;

    /* Сторона картинки, по которой ищется самое насыщенное место. Двухсот
       точек хватает: ищем область, а не деталь. */
    private static final int ANALYSIS = 200;

    private final ResourceLoader resourceLoader;

    @Value("${avatar.dir:./data}")
    private String dir;

    @Value("${avatar.default:classpath:static/image/photo_2025-08-24_16-06-35.jpg}")
    private String fallback;

    @Value("${avatar.size:640}")
    private int size;

    /* Читают её потоки запросов, пишет поток бота, поэтому volatile и
       неизменяемый снимок целиком, а не два отдельных поля. */
    private volatile Avatar cached;

    @PostConstruct
    void load() {
        Path file = file();
        if (Files.isReadable(file)) {
            try {
                cached = snapshot(Files.readAllBytes(file));
                log.info("AVATAR: своя аватарка из {}", file);
                return;
            } catch (IOException e) {
                log.error("AVATAR: не прочиталась {}: {}", file, e.getMessage());
            }
        }
        cached = snapshot(defaultBytes());
        log.info("AVATAR: аватарка из сборки, своя не загружена");
    }

    @Override
    public void replace(byte[] image) {
        byte[] normalized = normalize(image);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            /* Пишем рядом и переставляем одним движением: иначе запрос,
               попавший в середину записи, получил бы обрезанный файл. */
            Path temp = Files.createTempFile(file.getParent(), "avatar", ".tmp");
            Files.write(temp, normalized);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new AvatarRejected("не удалось сохранить: " + e.getMessage());
        }
        cached = snapshot(normalized);
        log.info("AVATAR: заменена, {} КБ", normalized.length / 1024);
    }

    @Override
    public void reset() {
        try {
            Files.deleteIfExists(file());
        } catch (IOException e) {
            throw new AvatarRejected("не удалось удалить: " + e.getMessage());
        }
        cached = snapshot(defaultBytes());
        log.info("AVATAR: возвращена аватарка из сборки");
    }

    @Override
    public Avatar current() {
        return cached;
    }

    private byte[] normalize(byte[] image) {
        if (image == null || image.length == 0) {
            throw new AvatarRejected("пустой файл");
        }
        if (image.length > MAX_UPLOAD) {
            throw new AvatarRejected("больше " + MAX_UPLOAD / 1024 / 1024 + " МБ");
        }
        BufferedImage source = decode(image);
        BufferedImage square = toSquare(source);
        return encode(square);
    }

    /* Размер узнаём из заголовка и только потом распаковываем: иначе
       картинка-бомба съела бы всю память ещё до любой проверки. */
    private BufferedImage decode(byte[] image) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new AvatarRejected("это не картинка");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new AvatarRejected("слишком большая картинка");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new AvatarRejected("не читается: " + e.getMessage());
        }
    }

    /* Обрезаем до квадрата, а не сжимаем прямоугольник: на карточке стоит
       object-fit: cover, и вытянутое фото там всё равно обрежется — лучше
       сделать это один раз здесь и не возить лишние байты.

       Окно ставим не по центру, а туда, где больше деталей: у центральной
       обрезки персонаж, стоящий сбоку, так и остаётся сбоку, и кадр читается
       как криво обрезанный, хотя пропорция ровная. */
    private BufferedImage toSquare(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        boolean wide = source.getWidth() > source.getHeight();
        int offset = busiestOffset(source, side, wide);
        BufferedImage cropped = source.getSubimage(
                wide ? offset : 0, wide ? 0 : offset, side, side);

        Image scaled = cropped.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        /* JPEG не умеет прозрачность: без заливки прозрачные места станут
           чёрным мусором. Заливаем сразу чёрным — сайт тёмный. */
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, size, size);
        graphics.drawImage(scaled, 0, 0, null);
        graphics.dispose();
        return result;
    }

    /**
     * Смещение квадратного окна вдоль длинной стороны. Ищем полосу, где
     * сильнее всего меняется яркость: пустое небо, стена или поле дают почти
     * ноль, а лицо, фигура или текст — много. Это грубая замена «найди
     * главное на фото», но на аватарках она попадает почти всегда.
     */
    private int busiestOffset(BufferedImage source, int side, boolean wide) {
        int width = source.getWidth();
        int height = source.getHeight();
        int span = (wide ? width : height) - side;
        if (span <= 0) {
            return 0;
        }

        /* Считаем не по оригиналу: на 12 мегапикселях это миллионы лишних
           операций там, где хватает картинки со стороной в пару сотен. */
        int longSide = Math.min(ANALYSIS, wide ? width : height);
        int shortSide = Math.max(2, side * longSide / (wide ? width : height));
        int[][] gray = sample(source, wide ? longSide : shortSide, wide ? shortSide : longSide);

        int rows = gray.length;
        int columns = gray[0].length;
        /* Плотность деталей по каждой полосе поперёк длинной стороны. */
        long[] energy = new long[wide ? columns : rows];
        for (int y = 1; y < rows; y++) {
            for (int x = 1; x < columns; x++) {
                long delta = Math.abs(gray[y][x] - gray[y][x - 1])
                        + Math.abs(gray[y][x] - gray[y - 1][x]);
                energy[wide ? x : y] += delta;
            }
        }

        long[] prefix = new long[energy.length + 1];
        for (int i = 0; i < energy.length; i++) {
            prefix[i + 1] = prefix[i] + energy[i];
        }

        int window = Math.max(1, Math.min(energy.length, side * energy.length / (wide ? width : height)));
        int limit = energy.length - window;
        if (limit <= 0) {
            return span / 2;
        }

        int best = limit / 2;
        double bestScore = -1;
        for (int start = 0; start <= limit; start++) {
            long sum = prefix[start + window] - prefix[start];
            /* Лёгкий перевес центру: на почти одинаковых кадрах окно иначе
               уезжает к самому краю из-за случайного шума, а такой кадр
               выглядит намеренно кривым. Края теряют четверть веса — этого
               хватает на ничью и не мешает по-настоящему смещённому сюжету. */
            double offCenter = Math.abs(start - limit / 2.0) / (limit / 2.0);
            double score = sum * (1 - 0.25 * offCenter);
            if (score > bestScore) {
                bestScore = score;
                best = start;
            }
        }

        int offset = (int) Math.max(0, Math.min(span, (long) best * span / limit));
        log.info("AVATAR: кадр смещён на {}% от центра",
                Math.round((offset - span / 2.0) * 200.0 / span));
        return offset;
    }

    /* Прореживание, а не масштабирование: считать плотность деталей можно
       и по каждому n-му пикселю, а полноценное сглаживание тут стоило бы
       дороже самого поиска. */
    private int[][] sample(BufferedImage source, int columns, int rows) {
        int[][] gray = new int[rows][columns];
        for (int y = 0; y < rows; y++) {
            int sourceY = (int) ((long) y * source.getHeight() / rows);
            for (int x = 0; x < columns; x++) {
                int sourceX = (int) ((long) x * source.getWidth() / columns);
                int rgb = source.getRGB(sourceX, sourceY);
                gray[y][x] = (((rgb >> 16) & 0xFF) * 299
                        + ((rgb >> 8) & 0xFF) * 587
                        + (rgb & 0xFF) * 114) / 1000;
            }
        }
        return gray;
    }

    private byte[] encode(BufferedImage image) {
        byte[] result = null;
        for (float quality : QUALITY_STEPS) {
            result = encodeAt(image, quality);
            if (result.length <= TARGET_BYTES) {
                return result;
            }
        }
        /* Ниже последней ступени не спускаемся: фото с мелкой фактурой
           бывает не ужать до цели, не превратив его в кашу. */
        return result;
    }

    private byte[] encodeAt(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new AvatarRejected("в этой сборке java нет кодировщика jpeg");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new AvatarRejected("не запаковалась: " + e.getMessage());
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private byte[] defaultBytes() {
        Resource resource = resourceLoader.getResource(fallback);
        try (InputStream stream = resource.getInputStream()) {
            return stream.readAllBytes();
        } catch (IOException e) {
            /* Дальше идти некуда: без аватарки по умолчанию отдавать нечего,
               и лучше узнать об этом на старте, чем пустой картинкой у гостя. */
            throw new IllegalStateException("не найдена аватарка по умолчанию: " + fallback, e);
        }
    }

    private Avatar snapshot(byte[] bytes) {
        /* Тега достаточно слабого: он лишь должен меняться вместе с файлом,
           чтобы браузер не показывал старое фото после замены. */
        return new Avatar(bytes, "\"%d-%d\"".formatted(bytes.length, java.util.Arrays.hashCode(bytes)));
    }

    private Path file() {
        return Path.of(dir, "avatar.jpg");
    }
}
