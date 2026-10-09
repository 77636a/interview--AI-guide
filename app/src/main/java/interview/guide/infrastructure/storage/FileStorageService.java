package interview.guide.infrastructure.storage;

import interview.guide.common.config.StorageConfigProperties;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCodes;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Pattern SAFE_EXTENSION = Pattern.compile("[a-zA-Z0-9]{1,10}");

    private final S3Client s3Client;
    private final StorageConfigProperties storageConfig;

    public String uploadResume(MultipartFile file) {
        return uploadFile(file, "resumes");
    }

    public byte[] downloadResume(String fileKey) {
        return downloadFile(fileKey);
    }

    public void deleteResume(String fileKey) {
        deleteFile(fileKey);
    }

    public String uploadKnowledgeBase(MultipartFile file) {
        return uploadFile(file, "knowledgebases");
    }

    public void deleteKnowledgeBase(String fileKey) {
        deleteFile(fileKey);
    }

    private String uploadFile(MultipartFile file, String directory) {
        validateUpload(file);
        String fileKey = generateFileKey(directory, file.getOriginalFilename());

        PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                .bucket(storageConfig.getBucket())
                .key(fileKey)
                .contentLength(file.getSize());
        if (StringUtils.hasText(file.getContentType())) {
            requestBuilder.contentType(file.getContentType());
        }

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(
                    requestBuilder.build(),
                    RequestBody.fromInputStream(inputStream, file.getSize()));
            return fileKey;
        } catch (IOException | SdkException exception) {
            log.error("Failed to upload object, key={}", fileKey, exception);
            throw new FileStorageException("Failed to upload file", exception);
        }
    }

    private byte[] downloadFile(String fileKey) {
        validateFileKey(fileKey);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(storageConfig.getBucket())
                .key(fileKey)
                .build();

        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            return response.asByteArray();
        } catch (S3Exception exception) {
            if (exception.statusCode() == HttpStatus.NOT_FOUND.value()) {
                throw BusinessException.notFound("file not found");
            }
            log.error("Failed to download object, key={}", fileKey, exception);
            throw new FileStorageException("Failed to download file", exception);
        } catch (SdkException exception) {
            log.error("Failed to download object, key={}", fileKey, exception);
            throw new FileStorageException("Failed to download file", exception);
        }
    }

    private void deleteFile(String fileKey) {
        validateFileKey(fileKey);
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(storageConfig.getBucket())
                .key(fileKey)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (SdkException exception) {
            log.error("Failed to delete object, key={}", fileKey, exception);
            throw new FileStorageException("Failed to delete file", exception);
        }
    }

    private String generateFileKey(String directory, String originalFilename) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        String extension = extractSafeExtension(originalFilename);
        return "%s/%d/%02d/%s%s".formatted(
                directory,
                now.getYear(),
                now.getMonthValue(),
                UUID.randomUUID(),
                extension);
    }

    private String extractSafeExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }

        String normalizedFilename = originalFilename.replace('\\', '/');
        int filenameStart = normalizedFilename.lastIndexOf('/') + 1;
        int dotIndex = normalizedFilename.lastIndexOf('.');
        if (dotIndex < filenameStart || dotIndex == normalizedFilename.length() - 1) {
            return "";
        }

        String extension = normalizedFilename.substring(dotIndex + 1);
        if (!SAFE_EXTENSION.matcher(extension).matches()) {
            return "";
        }
        return "." + extension.toLowerCase(Locale.ROOT);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    ErrorCodes.BASE_REQUEST_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "file must not be empty");
        }
    }

    private void validateFileKey(String fileKey) {
        if (!StringUtils.hasText(fileKey)) {
            throw new BusinessException(
                    ErrorCodes.BASE_REQUEST_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "file key must not be blank");
        }
    }
}
