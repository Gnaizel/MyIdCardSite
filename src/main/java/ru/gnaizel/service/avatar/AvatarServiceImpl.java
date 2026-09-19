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
       Поэтому любое присланное изображение приводится к тем же параметрам,
       что и лежавшее в сборке: квадрат 640 на 640 при колонке в 290px —
       ровно двойной размер под retina. */
    private static final float QUALITY = 0.85f;

    /* Столько байт максимум принимаем на вход. Больше телеграм и не пришлёт
       сжатым фото, а файл вполне может быть и таким. */
    private static final int MAX_UPLOAD = 10 * 1024 * 1024;

    /* Картинка 12 мегапикселей разворачивается в памяти примерно в 48 МБ.
       На сервере с гигабайтом это потолок, за которым начинается OutOfMemory,
       поэтому размер проверяется по заголовку, до распаковки. */
    private static final long MAX_PIXELS = 12_000_000L;

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

    /* Обрезаем по центру до квадрата, а не сжимаем прямоугольник: на карточке
       стоит object-fit: cover, и вытянутое фото там всё равно обрежется —
       лучше сделать это один раз здесь и не возить лишние байты. */
    private BufferedImage toSquare(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        BufferedImage cropped = source.getSubimage(
                (source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side);

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

    private byte[] encode(BufferedImage image) {
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
            params.setCompressionQuality(QUALITY);
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
