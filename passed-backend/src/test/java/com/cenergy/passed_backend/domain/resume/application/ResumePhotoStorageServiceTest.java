package com.cenergy.passed_backend.domain.resume.application;

import com.cenergy.passed_backend.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumePhotoStorageServiceTest {
    @TempDir
    Path directory;

    @Test
    void storesSupportedImageUnderGeneratedName() throws Exception {
        ResumePhotoStorageService service = new ResumePhotoStorageService(
                directory.toString(), "/uploads/resume-photos"
        );
        MockMultipartFile file = new MockMultipartFile(
                "file", "profile.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}
        );

        var response = service.store(file);

        assertThat(response.fileUrl()).startsWith("/uploads/resume-photos/").endsWith(".png");
        assertThat(Files.list(directory)).hasSize(1);
    }

    @Test
    void rejectsUnsupportedContentType() {
        ResumePhotoStorageService service = new ResumePhotoStorageService(
                directory.toString(), "/uploads/resume-photos"
        );
        MockMultipartFile file = new MockMultipartFile(
                "file", "profile.gif", "image/gif", new byte[]{1}
        );

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOfSatisfying(ResumeException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.RESUME_PHOTO_UNSUPPORTED_TYPE));
    }
}
