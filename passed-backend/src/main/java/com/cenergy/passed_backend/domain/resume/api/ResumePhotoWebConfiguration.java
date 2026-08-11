package com.cenergy.passed_backend.domain.resume.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/** 업로드 응답의 상대 URL을 로컬 개발 환경에서 바로 조회할 수 있게 연결한다. */
@Configuration
public class ResumePhotoWebConfiguration implements WebMvcConfigurer {
    private final Path storageDirectory;
    private final String publicPrefix;

    public ResumePhotoWebConfiguration(
            @Value("${app.resume-photo.storage-directory:uploads/resume-photos}") String storageDirectory,
            @Value("${app.resume-photo.public-prefix:/uploads/resume-photos}") String publicPrefix
    ) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
        this.publicPrefix = publicPrefix;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String pattern = (publicPrefix.endsWith("/") ? publicPrefix : publicPrefix + "/") + "**";
        registry.addResourceHandler(pattern)
                .addResourceLocations(storageDirectory.toUri().toString());
    }
}
