package com.example.calendar.web;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Раздача собранного фронтенда из classpath:/static/ (в Docker-образ его кладёт сборка). Пути
 * клиентского роутера (/book, /owner) отдают index.html, а /api/** и пути с расширением файла
 * остаются 404, чтобы опечатка в API или пропавший ассет не превращались в HTML.
 */
@Configuration
class SpaConfig implements WebMvcConfigurer {

  private static final String INDEX = "index.html";

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(true)
        .addResolver(new SpaResourceResolver());
  }

  private static final class SpaResourceResolver extends PathResourceResolver {
    @Override
    protected Resource getResource(String path, Resource location) throws IOException {
      Resource resource = location.createRelative(path);
      if (resource.exists() && resource.isReadable()) {
        return resource;
      }
      if (path.startsWith("api/") || path.equals("api") || lastSegment(path).contains(".")) {
        return null;
      }
      Resource index = location.createRelative(INDEX);
      return index.exists() && index.isReadable() ? index : null;
    }

    private static String lastSegment(String path) {
      return path.substring(path.lastIndexOf('/') + 1);
    }
  }
}
