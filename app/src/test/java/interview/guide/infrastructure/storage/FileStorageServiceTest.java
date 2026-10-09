package interview.guide.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.config.StorageConfigProperties;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        StorageConfigProperties storageConfig = new StorageConfigProperties();
        storageConfig.setBucket("interview-guide");
        fileStorageService = new FileStorageService(s3Client, storageConfig);
    }

    @Test
    void uploadsResumeWithServerGeneratedKey() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../Resume.PDF",
                "application/pdf",
                "resume".getBytes(StandardCharsets.UTF_8));

        String fileKey = fileStorageService.uploadResume(file);

        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        assertThat(fileKey).matches(
                "resumes/%d/%02d/[0-9a-f-]{36}\\.pdf".formatted(now.getYear(), now.getMonthValue()));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("interview-guide");
        assertThat(requestCaptor.getValue().key()).isEqualTo(fileKey);
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("application/pdf");
    }

    @Test
    void downloadsResumeByStoredKey() {
        byte[] content = "resume".getBytes(StandardCharsets.UTF_8);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content));

        byte[] downloaded = fileStorageService.downloadResume("resumes/2026/09/file.pdf");

        assertThat(downloaded).isEqualTo(content);
    }

    @Test
    void deletesKnowledgeBaseByStoredKey() {
        String fileKey = "knowledgebases/2026/09/file.pdf";

        fileStorageService.deleteKnowledgeBase(fileKey);

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("interview-guide");
        assertThat(requestCaptor.getValue().key()).isEqualTo(fileKey);
    }
}
