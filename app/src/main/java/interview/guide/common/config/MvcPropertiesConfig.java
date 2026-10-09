package interview.guide.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers MVC-bound configuration properties in both the full application and MVC test slices.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class MvcPropertiesConfig implements WebMvcConfigurer {
}
