package com.cryptocinema.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.cryptocinema.exception.ConflictException;
import com.cryptocinema.exception.ResourceNotFoundException;

@Service
public class MediaStorageService {

    private static final Map<String, String> POSTER_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private static final Map<String, String> TRAILER_EXTENSIONS = Map.of(
            "video/mp4", ".mp4",
            "video/webm", ".webm");

    private final Path root;
    private final long maxPosterBytes;
    private final long maxTrailerBytes;

    public MediaStorageService(
            @Value("${app.uploads.root:uploads}") String uploadRoot,
            @Value("${app.uploads.max-poster-bytes:5242880}") long maxPosterBytes,
            @Value("${app.uploads.max-trailer-bytes:104857600}") long maxTrailerBytes
    ) {
        this.root = Path.of(uploadRoot).toAbsolutePath().normalize();
        this.maxPosterBytes = maxPosterBytes;
        this.maxTrailerBytes = maxTrailerBytes;
    }

    public String storePoster(MultipartFile file) {
        return store(file, "posters", POSTER_EXTENSIONS, maxPosterBytes);
    }

    public String storeTrailer(MultipartFile file) {
        return store(file, "trailers", TRAILER_EXTENSIONS, maxTrailerBytes);
    }

    public Resource load(String folder, String filename) {
        if (!StringUtils.hasText(folder) || !StringUtils.hasText(filename)) {
            throw new ResourceNotFoundException("Media not found");
        }
        Path file = root.resolve(folder).resolve(filename).normalize();
        if (!file.startsWith(root)) {
            throw new ResourceNotFoundException("Media not found");
        }
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Media not found");
            }
            return resource;
        } catch (IOException exception) {
            throw new ResourceNotFoundException("Media not found");
        }
    }

    private String store(
            MultipartFile file,
            String folder,
            Map<String, String> allowedExtensions,
            long maxBytes
    ) {
        if (file == null || file.isEmpty()) {
            throw new ConflictException("Upload file is required.");
        }
        if (file.getSize() > maxBytes) {
            throw new ConflictException("Upload file is too large.");
        }

        String contentType = file.getContentType();
        String extension = allowedExtensions.get(contentType);
        if (extension == null) {
            throw new ConflictException("Unsupported upload file type.");
        }

        Path directory = root.resolve(folder).normalize();
        if (!directory.startsWith(root)) {
            throw new ConflictException("Invalid upload path.");
        }

        String filename = UUID.randomUUID() + extension;
        Path destination = directory.resolve(filename).normalize();
        if (!destination.startsWith(directory)) {
            throw new ConflictException("Invalid upload path.");
        }

        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ConflictException("Could not store uploaded file.");
        }

        return "/api/media/" + folder + "/" + filename;
    }
}
