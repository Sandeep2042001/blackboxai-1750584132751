package com.henuka.imitations.config;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StorageConfig {

    @Value("${aws.access.key.id}")
    private String accessKeyId;

    @Value("${aws.secret.access.key}")
    private String secretAccessKey;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${storage.local.path}")
    private String localStoragePath;

    /**
     * Configure S3 client
     */
    @Bean
    @Profile("prod")
    public AmazonS3 s3Client() {
        BasicAWSCredentials credentials = new BasicAWSCredentials(accessKeyId, secretAccessKey);
        return AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .build();
    }

    /**
     * Configure storage service
     */
    @Bean
    public StorageService storageService() {
        if (org.springframework.core.env.Profiles.of("prod")
                .matches(org.springframework.core.env.StandardEnvironment::getActiveProfiles)) {
            return new S3StorageService(s3Client(), bucketName);
        }
        return new LocalStorageService(localStoragePath);
    }
}

/**
 * Storage service interface
 */
interface StorageService {
    String store(byte[] content, String filename, String contentType);
    byte[] retrieve(String filename);
    void delete(String filename);
    String getUrl(String filename);
}

/**
 * S3 storage service implementation
 */
class S3StorageService implements StorageService {
    
    private final AmazonS3 s3Client;
    private final String bucketName;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(S3StorageService.class);

    public S3StorageService(AmazonS3 s3Client, String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Override
    public String store(byte[] content, String filename, String contentType) {
        try {
            java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(content);
            com.amazonaws.services.s3.model.ObjectMetadata metadata = 
                new com.amazonaws.services.s3.model.ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(content.length);

            s3Client.putObject(bucketName, filename, inputStream, metadata);
            log.info("File {} uploaded to S3", filename);
            return filename;
        } catch (Exception e) {
            log.error("Failed to upload file to S3: {}", filename, e);
            throw new StorageException("Failed to store file", e);
        }
    }

    @Override
    public byte[] retrieve(String filename) {
        try {
            com.amazonaws.services.s3.model.S3Object object = s3Client.getObject(bucketName, filename);
            return org.apache.commons.io.IOUtils.toByteArray(object.getObjectContent());
        } catch (Exception e) {
            log.error("Failed to retrieve file from S3: {}", filename, e);
            throw new StorageException("Failed to retrieve file", e);
        }
    }

    @Override
    public void delete(String filename) {
        try {
            s3Client.deleteObject(bucketName, filename);
            log.info("File {} deleted from S3", filename);
        } catch (Exception e) {
            log.error("Failed to delete file from S3: {}", filename, e);
            throw new StorageException("Failed to delete file", e);
        }
    }

    @Override
    public String getUrl(String filename) {
        return s3Client.getUrl(bucketName, filename).toString();
    }
}

/**
 * Local storage service implementation
 */
class LocalStorageService implements StorageService {
    
    private final Path rootLocation;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LocalStorageService.class);

    public LocalStorageService(String storagePath) {
        this.rootLocation = Paths.get(storagePath);
        initializeStorage();
    }

    private void initializeStorage() {
        try {
            java.nio.file.Files.createDirectories(rootLocation);
        } catch (Exception e) {
            throw new StorageException("Could not initialize storage", e);
        }
    }

    @Override
    public String store(byte[] content, String filename, String contentType) {
        try {
            Path destinationFile = this.rootLocation.resolve(filename)
                    .normalize().toAbsolutePath();
            java.nio.file.Files.write(destinationFile, content);
            log.info("File {} stored locally", filename);
            return filename;
        } catch (Exception e) {
            log.error("Failed to store file locally: {}", filename, e);
            throw new StorageException("Failed to store file", e);
        }
    }

    @Override
    public byte[] retrieve(String filename) {
        try {
            Path file = rootLocation.resolve(filename);
            return java.nio.file.Files.readAllBytes(file);
        } catch (Exception e) {
            log.error("Failed to retrieve file locally: {}", filename, e);
            throw new StorageException("Failed to retrieve file", e);
        }
    }

    @Override
    public void delete(String filename) {
        try {
            Path file = rootLocation.resolve(filename);
            java.nio.file.Files.deleteIfExists(file);
            log.info("File {} deleted locally", filename);
        } catch (Exception e) {
            log.error("Failed to delete file locally: {}", filename, e);
            throw new StorageException("Failed to delete file", e);
        }
    }

    @Override
    public String getUrl(String filename) {
        return rootLocation.resolve(filename).toString();
    }
}

/**
 * Storage exception
 */
class StorageException extends RuntimeException {
    
    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Storage metrics
 */
@org.springframework.stereotype.Component
class StorageMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;

    public StorageMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordUpload(String storageType, long size) {
        registry.counter("storage.upload", "type", storageType).increment();
        registry.summary("storage.upload.size", "type", storageType).record(size);
    }

    public void recordDownload(String storageType, long size) {
        registry.counter("storage.download", "type", storageType).increment();
        registry.summary("storage.download.size", "type", storageType).record(size);
    }
}
