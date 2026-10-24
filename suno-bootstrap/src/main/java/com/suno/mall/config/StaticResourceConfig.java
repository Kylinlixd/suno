package com.suno.mall.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/** /files/** 映射到上传目录（只读静态服务）。 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private final Path uploadDir;

    public StaticResourceConfig(@Value("${mall.upload.dir:./data/uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
