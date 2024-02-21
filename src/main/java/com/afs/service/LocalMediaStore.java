package com.afs.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("local-cms")
public class LocalMediaStore {
    public static final int MAX_BYTES = 32 * 1024 * 1024;

    public static Path directory() {
        return Path.of("data", "media").toAbsolutePath().normalize();
    }

    public Map<String, String> save(MultipartFile file, boolean imageOnly) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 32 MB 以内的文件");
        }
        Files.createDirectories(directory());
        try (var input = new BufferedInputStream(file.getInputStream())) {
            input.mark(16);
            String extension = imageExtension(input.readNBytes(12));
            input.reset();
            if (imageOnly && extension.equals("bin")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片须为 PNG、JPEG、GIF 或 WebP");
            }
            String name = UUID.randomUUID() + "." + extension;
            Files.copy(input, directory().resolve(name));
            return Map.of("name", file.getOriginalFilename() == null ? name : file.getOriginalFilename(),
                    "url", "/media/" + name);
        }
    }

    public String saveGeneratedImage(byte[] image) throws IOException {
        if (image.length > MAX_BYTES) throw new IOException("AI 图片超过 32 MB");
        String extension = imageExtension(image);
        if (extension.equals("bin")) throw new IOException("AI 平台返回了不支持的图片格式");
        Files.createDirectories(directory());
        String name = UUID.randomUUID() + "." + extension;
        Files.write(directory().resolve(name), image, java.nio.file.StandardOpenOption.CREATE_NEW);
        return "/media/" + name;
    }

    private static String imageExtension(byte[] bytes) {
        if (bytes.length < 12) return "bin";
        if (bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "png";
        if (bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) return "jpg";
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return "gif";
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "webp";
        return "bin";
    }
}
