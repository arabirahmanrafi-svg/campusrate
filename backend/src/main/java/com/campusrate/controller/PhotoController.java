package com.campusrate.controller;

import com.campusrate.dto.CommunityDtos.PhotoResponse;
import com.campusrate.service.PhotoService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

/** Photo uploads, galleries and serving the image files. */
@RestController
@RequestMapping("/api")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping("/universities/{id}/photos")
    public List<PhotoResponse> gallery(@PathVariable Long id) {
        return photoService.approvedForUniversity(id);
    }

    /**
     * Upload with a multipart form: "file" (the image) plus optional "caption", "placeId"
     * (to attach it to a dorm, dining hall, ...) and "reviewId" (to attach it to your review).
     */
    @PostMapping(value = "/universities/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoResponse upload(@AuthenticationPrincipal Long userId,
                                @PathVariable Long id,
                                @RequestPart("file") MultipartFile file,
                                @RequestParam(required = false) String caption,
                                @RequestParam(required = false) Long placeId,
                                @RequestParam(required = false) Long reviewId) {
        return photoService.upload(userId, id, file, caption, placeId, reviewId);
    }

    @PostMapping("/photos/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        photoService.report(userId, id);
    }

    /** Serves an uploaded image. Browsers may cache it for a week, since a file never changes. */
    @GetMapping("/files/{fileName}")
    public ResponseEntity<Resource> file(@PathVariable String fileName) {
        Resource resource = photoService.file(fileName);
        MediaType type = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
                .body(resource);
    }
}
