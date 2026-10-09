package interview.guide.infrastructure.storage;

import interview.guide.common.config.StorageConfigProperties;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(StorageConfigProperties storageConfig) {
        return S3Client.builder()
                .endpointOverride(URI.create(storageConfig.getEndpoint()))
                .region(Region.of(storageConfig.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        storageConfig.getAccessKey(),
                        storageConfig.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
