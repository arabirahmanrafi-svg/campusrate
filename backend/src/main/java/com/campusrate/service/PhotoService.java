package com.campusrate.service;

import com.campusrate.dto.CommunityDtos.PhotoResponse;
import com.campusrate.exception.ApiException;
import com.campusrate.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Photo uploads.
 *
 * Images are saved as files in a folder on the server; MySQL stores only the file name.
 * Databases are bad at storing large files, and this way images can later move to cloud
 * storage by changing only this class.
 *
 * Security:
 *  - the file's first bytes must really be a JPEG, PNG or WebP image (the browser's
 *    "content type" can be faked, the bytes can't);
 *  - files get a random name, so users can't overwrite each other's files or guess paths;
 *  - new photos stay hidden until an admin approves them (configurable).
 *
 * This class uses JdbcClient with plain SQL (instead of JPA entities) because it mostly
 * runs small, specific queries; both styles share the same transactions.
 */
@Service
public class PhotoService {

    private static final Pattern SAFE_FILE_NAME = Pattern.compile("^[a-f0-9-]{36}\\.(jpg|png|webp)$");
    private static final int REPORTS_TO_HIDE = 3;

    private final JdbcClient jdbc;
    private final AuthService authService;
    private final Path uploadDir;
    private final boolean requireApproval;

    public PhotoService(JdbcClient jdbc, AuthService authService,
                        @Value("${app.upload-dir}") String uploadDir,
                        @Value("${app.photos.require-approval}") boolean requireApproval) throws IOException {
        this.jdbc = jdbc;
        this.authService = authService;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.requireApproval = requireApproval;
        Files.createDirectories(this.uploadDir);
    }

    static final RowMapper<PhotoResponse> PHOTO_ROW = (rs, rowNum) -> new PhotoResponse(
            rs.getLong("id"),
            rs.getLong("university_id"),
            "/files/" + rs.getString("file_name"),
            rs.getString("caption"),
            rs.getString("uploader_name"),
            (Long) rs.getObject("place_id", Long.class),
            (Long) rs.getObject("review_id", Long.class),
            rs.getString("status"),
            rs.getObject("created_at", java.time.LocalDateTime.class));

    static final String PHOTO_SELECT = """
            select p.id, p.university_id, p.file_name, p.caption, u.display_name as uploader_name,
                   p.place_id, p.review_id, p.status, p.created_at
            from photos p join users u on u.id = p.user_id
            """;

    // ------------------------------------------------------------------ upload

    @Transactional
    public PhotoResponse upload(Long userId, Long universityId, MultipartFile file, String caption,
                                Long placeId, Long reviewId) {
        User user = authService.findUser(userId);
        requireUniversity(universityId);

        if (placeId != null) {
            Long placeUniversity = jdbc.sql("select university_id from places where id = :id")
                    .param("id", placeId).query(Long.class).optional()
                    .orElseThrow(() -> ApiException.notFound("Place not found."));
            if (!placeUniversity.equals(universityId)) {
                throw ApiException.badRequest("That place belongs to a different university.");
            }
        }
        if (reviewId != null) {
            Map<String, Object> review = jdbc.sql("select user_id, university_id from reviews where id = :id")
                    .param("id", reviewId).query().listOfRows().stream().findFirst()
                    .orElseThrow(() -> ApiException.notFound("Review not found."));
            if (((Number) review.get("user_id")).longValue() != user.getId()
                    || ((Number) review.get("university_id")).longValue() != universityId) {
                throw ApiException.forbidden("You can only add photos to your own review.");
            }
        }

        String fileName = saveImage(file);
        String status = user.isAdmin() || !requireApproval ? "APPROVED" : "PENDING";
        String cleanCaption = caption == null || caption.isBlank() ? null : caption.trim();
        if (cleanCaption != null && cleanCaption.length() > 200) {
            cleanCaption = cleanCaption.substring(0, 200);
        }

        jdbc.sql("""
                        insert into photos (university_id, place_id, review_id, user_id, file_name, caption, status)
                        values (:universityId, :placeId, :reviewId, :userId, :fileName, :caption, :status)
                        """)
                .param("universityId", universityId)
                .param("placeId", placeId)
                .param("reviewId", reviewId)
                .param("userId", user.getId())
                .param("fileName", fileName)
                .param("caption", cleanCaption)
                .param("status", status)
                .update();

        return jdbc.sql(PHOTO_SELECT + " where p.file_name = :fileName")
                .param("fileName", fileName).query(PHOTO_ROW).single();
    }

    /** Checks the file really is an image, then saves it under a random name. Returns that name. */
    String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Please choose a photo to upload.");
        }
        String extension;
        try (InputStream in = file.getInputStream()) {
            extension = detectImageType(in.readNBytes(12));
        } catch (IOException e) {
            throw ApiException.badRequest("Couldn't read that file.");
        }
        if (extension == null) {
            throw ApiException.badRequest("Only JPEG, PNG or WebP images are allowed.");
        }

        String fileName = UUID.randomUUID() + "." + extension;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, uploadDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the uploaded photo", e);
        }
        return fileName;
    }

    /** Identifies an image by its first bytes ("magic numbers"). Returns the file extension, or null. */
    static String detectImageType(byte[] head) {
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (head.length >= 4 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "png";
        }
        if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp";
        }
        return null;
    }

    // ------------------------------------------------------------------ reading

    public List<PhotoResponse> approvedForUniversity(Long universityId) {
        return jdbc.sql(PHOTO_SELECT + " where p.university_id = :id and p.status = 'APPROVED' order by p.created_at desc limit 60")
                .param("id", universityId).query(PHOTO_ROW).list();
    }

    public List<PhotoResponse> approvedForPlace(Long placeId) {
        return jdbc.sql(PHOTO_SELECT + " where p.place_id = :id and p.status = 'APPROVED' order by p.created_at desc")
                .param("id", placeId).query(PHOTO_ROW).list();
    }

    /** Approved photos for a page of reviews, in one query, grouped by review id. */
    public Map<Long, List<PhotoResponse>> approvedForReviews(Collection<Long> reviewIds) {
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql(PHOTO_SELECT + " where p.review_id in (:ids) and p.status = 'APPROVED' order by p.created_at")
                .param("ids", reviewIds).query(PHOTO_ROW).list().stream()
                .collect(Collectors.groupingBy(PhotoResponse::reviewId));
    }

    public List<PhotoResponse> pending() {
        return jdbc.sql(PHOTO_SELECT + " where p.status = 'PENDING' order by p.created_at").query(PHOTO_ROW).list();
    }

    /** Serves an image file. Only names we generated are accepted, which blocks "../" path tricks. */
    public Resource file(String fileName) {
        if (!SAFE_FILE_NAME.matcher(fileName).matches()) {
            throw ApiException.notFound("File not found.");
        }
        Path path = uploadDir.resolve(fileName);
        if (!Files.exists(path)) {
            throw ApiException.notFound("File not found.");
        }
        return new PathResource(path);
    }

    // ------------------------------------------------------------------ moderation

    /** A student reports a photo. After 3 reports from different students it's hidden until an admin checks it. */
    @Transactional
    public void report(Long userId, Long photoId) {
        User user = authService.findUser(userId);
        int added = jdbc.sql("insert ignore into photo_reports (user_id, photo_id) values (:userId, :photoId)")
                .param("userId", user.getId()).param("photoId", photoId).update();
        if (added == 0) {
            return; // already reported by this student
        }
        // MySQL applies SET assignments left to right, so status is decided before report_count changes.
        jdbc.sql("""
                        update photos
                        set status = case when report_count + 1 >= :limit and status = 'APPROVED' then 'PENDING' else status end,
                            report_count = report_count + 1
                        where id = :id
                        """)
                .param("limit", REPORTS_TO_HIDE).param("id", photoId).update();
    }

    @Transactional
    public void approve(Long photoId) {
        int updated = jdbc.sql("update photos set status = 'APPROVED', report_count = 0 where id = :id")
                .param("id", photoId).update();
        if (updated == 0) {
            throw ApiException.notFound("Photo not found.");
        }
        jdbc.sql("delete from photo_reports where photo_id = :id").param("id", photoId).update();
    }

    /** Rejecting deletes the file from disk too. */
    @Transactional
    public void reject(Long photoId) {
        String fileName = jdbc.sql("select file_name from photos where id = :id")
                .param("id", photoId).query(String.class).optional()
                .orElseThrow(() -> ApiException.notFound("Photo not found."));
        jdbc.sql("delete from photos where id = :id").param("id", photoId).update();
        try {
            Files.deleteIfExists(uploadDir.resolve(fileName));
        } catch (IOException e) {
            // The database row is gone, so the photo no longer shows anywhere; a leftover file is harmless.
        }
    }

    private void requireUniversity(Long universityId) {
        Integer count = jdbc.sql("select count(*) from universities where id = :id")
                .param("id", universityId).query(Integer.class).single();
        if (count == 0) {
            throw ApiException.notFound("University not found.");
        }
    }
}
