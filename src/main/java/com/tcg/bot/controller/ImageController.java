package com.tcg.bot.controller;

import com.tcg.bot.service.ImageCacheService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Sirve las imágenes de cartas guardadas en la PC (ver {@link ImageCacheService}). */
@RestController
public class ImageController {

    private final ImageCacheService images;

    public ImageController(ImageCacheService images) {
        this.images = images;
    }

    @GetMapping("/imagen")
    public ResponseEntity<byte[]> image(@RequestParam("u") String url) {
        var image = images.get(url);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType type;
        try {
            type = MediaType.parseMediaType(image.contentType());
        } catch (RuntimeException e) {
            type = MediaType.IMAGE_JPEG;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(image.bytes());
    }
}
