package interview.guide.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI interviewGuideOpenApi() {
        return new OpenAPI().info(new Info()
                .title("InterviewGuide API")
                .version("v0.1")
                .description("Backend APIs for the InterviewGuide project")
                .license(new License().name("UNLICENSED")));
    }
}

