package com.cryptocinema.controller;

import java.io.IOException;
import java.nio.file.Files;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.cryptocinema.service.MediaStorageService;

@RestController
public class MediaController {

    private final MediaStorageService mediaStorageService;

    public MediaController(MediaStorageService mediaStorageService) {
        this.mediaStorageService = mediaStorageService;
    }

    @GetMapping("/api/media/{folder}/{filename}")
    public ResponseEntity<Resource> findMedia(
            @PathVariable String folder,
            @PathVariable String filename
    ) {
        Resource resource = mediaStorageService.load(folder, filename);
        return ResponseEntity.ok()
                .contentType(resolveContentType(resource))
                .body(resource);
    }

    private MediaType resolveContentType(Resource resource) {
        try {
            String contentType = Files.probeContentType(resource.getFile().toPath());
            if (contentType != null) {
                return MediaType.parseMediaType(contentType);
            }
        } catch (IOException ignored) {
            // Fall back to a safe generic media type.
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
