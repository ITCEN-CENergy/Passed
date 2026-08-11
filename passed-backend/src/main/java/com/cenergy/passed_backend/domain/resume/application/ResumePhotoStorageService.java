package com.cenergy.passed_backend.domain.resume.application;

import com.cenergy.passed_backend.domain.resume.dto.ResumePhotoUploadResponse;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * 로컬 개발 기본 저장소다. 운영에서는 같은 응답 계약을 유지한 채 S3 같은 저장소로
 * 교체할 수 있다.
 *
 * <p>Q. 원본 파일명을 그대로 쓰지 않는 이유는 무엇인가요?</p>
 * <p>A. 같은 이름 충돌과 경로 조작을 막기 위해 서버가 UUID 이름을 발급한다.</p>
 */
@Service
public class ResumePhotoStorageService {
    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final Path storageDirectory;
    private final String publicPrefix;

    public ResumePhotoStorageService(
            @Value("${app.resume-photo.storage-directory:uploads/resume-photos}") String storageDirectory,
            @Value("${app.resume-photo.public-prefix:/uploads/resume-photos}") String publicPrefix
    ) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
        this.publicPrefix = publicPrefix.endsWith("/")
                ? publicPrefix.substring(0, publicPrefix.length() - 1)
                : publicPrefix;
    }

    public ResumePhotoUploadResponse store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw error(ErrorCode.RESUME_PHOTO_INVALID, "Resume photo is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw error(ErrorCode.RESUME_PHOTO_TOO_LARGE, "Resume photo must not exceed 5 MB");
        }
        String contentType = file.getContentType();
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw error(ErrorCode.RESUME_PHOTO_UNSUPPORTED_TYPE,
                    "Only JPEG, PNG, and WEBP are supported");
        }

        String filename = UUID.randomUUID() + extension;
        Path target = storageDirectory.resolve(filename).normalize();
        if (!target.startsWith(storageDirectory)) {
            throw error(ErrorCode.RESUME_PHOTO_INVALID, "Invalid resume photo path");
        }
        try {
            byte[] bytes = file.getBytes();
            if (!matchesSignature(contentType, bytes)) {
                throw error(ErrorCode.RESUME_PHOTO_UNSUPPORTED_TYPE,
                        "Resume photo content does not match its media type");
            }
            Files.createDirectories(storageDirectory);
            Files.write(target, bytes);
            return new ResumePhotoUploadResponse(publicPrefix + "/" + filename);
        } catch (IOException exception) {
            throw new ResumeException(
                    ErrorCode.RESUME_PHOTO_STORAGE_FAILED,
                    "Resume photo could not be stored",
                    exception
            );
        }
    }

    private boolean matchesSignature(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                    && bytes[2] == 0x4e && bytes[3] == 0x47
                    && bytes[4] == 0x0d && bytes[5] == 0x0a
                    && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private ResumeException error(ErrorCode code, String message) {
        return new ResumeException(code, message);
    }
}
