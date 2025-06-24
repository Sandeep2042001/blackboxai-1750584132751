package com.henuka.imitations.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AwsConfig {

    @Value("${aws.access.key.id}")
    private String accessKeyId;

    @Value("${aws.secret.access.key}")
    private String secretAccessKey;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.s3.bucket}")
    private String s3Bucket;

    @Value("${aws.sns.topic.arn}")
    private String snsTopicArn;

    @Value("${aws.sqs.queue.url}")
    private String sqsQueueUrl;

    /**
     * Configure AWS credentials
     */
    private AWSStaticCredentialsProvider getCredentialsProvider() {
        BasicAWSCredentials credentials = new BasicAWSCredentials(accessKeyId, secretAccessKey);
        return new AWSStaticCredentialsProvider(credentials);
    }

    /**
     * Configure S3 client
     */
    @Bean
    public AmazonS3 amazonS3() {
        return AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(getCredentialsProvider())
                .build();
    }

    /**
     * Configure SNS client
     */
    @Bean
    public AmazonSNS amazonSNS() {
        return AmazonSNSClientBuilder.standard()
                .withRegion(region)
                .withCredentials(getCredentialsProvider())
                .build();
    }

    /**
     * Configure SQS client
     */
    @Bean
    public AmazonSQS amazonSQS() {
        return AmazonSQSClientBuilder.standard()
                .withRegion(region)
                .withCredentials(getCredentialsProvider())
                .build();
    }
}

/**
 * AWS service
 */
@org.springframework.stereotype.Service
class AwsService {
    
    private final AmazonS3 s3Client;
    private final AmazonSNS snsClient;
    private final AmazonSQS sqsClient;
    private final String s3Bucket;
    private final String snsTopicArn;
    private final String sqsQueueUrl;
    private final AwsMetrics awsMetrics;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AwsService.class);

    public AwsService(AmazonS3 s3Client,
                     AmazonSNS snsClient,
                     AmazonSQS sqsClient,
                     @Value("${aws.s3.bucket}") String s3Bucket,
                     @Value("${aws.sns.topic.arn}") String snsTopicArn,
                     @Value("${aws.sqs.queue.url}") String sqsQueueUrl,
                     AwsMetrics awsMetrics) {
        this.s3Client = s3Client;
        this.snsClient = snsClient;
        this.sqsClient = sqsClient;
        this.s3Bucket = s3Bucket;
        this.snsTopicArn = snsTopicArn;
        this.sqsQueueUrl = sqsQueueUrl;
        this.awsMetrics = awsMetrics;
    }

    /**
     * Upload file to S3
     */
    public String uploadFile(byte[] content, String key, String contentType) {
        try {
            long startTime = System.currentTimeMillis();
            
            com.amazonaws.services.s3.model.ObjectMetadata metadata = 
                new com.amazonaws.services.s3.model.ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(content.length);

            s3Client.putObject(s3Bucket, key, 
                new java.io.ByteArrayInputStream(content), metadata);
            
            long duration = System.currentTimeMillis() - startTime;
            awsMetrics.recordS3Operation("upload", duration, true);
            
            return s3Client.getUrl(s3Bucket, key).toString();
        } catch (Exception e) {
            log.error("Failed to upload file to S3: {}", key, e);
            awsMetrics.recordS3Operation("upload", 0, false);
            throw new AwsException("Failed to upload file", e);
        }
    }

    /**
     * Download file from S3
     */
    public byte[] downloadFile(String key) {
        try {
            long startTime = System.currentTimeMillis();
            
            com.amazonaws.services.s3.model.S3Object object = s3Client.getObject(s3Bucket, key);
            byte[] content = org.apache.commons.io.IOUtils.toByteArray(object.getObjectContent());
            
            long duration = System.currentTimeMillis() - startTime;
            awsMetrics.recordS3Operation("download", duration, true);
            
            return content;
        } catch (Exception e) {
            log.error("Failed to download file from S3: {}", key, e);
            awsMetrics.recordS3Operation("download", 0, false);
            throw new AwsException("Failed to download file", e);
        }
    }

    /**
     * Publish SNS message
     */
    public String publishMessage(String message) {
        try {
            long startTime = System.currentTimeMillis();
            
            String messageId = snsClient.publish(snsTopicArn, message).getMessageId();
            
            long duration = System.currentTimeMillis() - startTime;
            awsMetrics.recordSnsOperation("publish", duration, true);
            
            return messageId;
        } catch (Exception e) {
            log.error("Failed to publish SNS message", e);
            awsMetrics.recordSnsOperation("publish", 0, false);
            throw new AwsException("Failed to publish message", e);
        }
    }

    /**
     * Send SQS message
     */
    public String sendMessage(String message) {
        try {
            long startTime = System.currentTimeMillis();
            
            String messageId = sqsClient.sendMessage(sqsQueueUrl, message).getMessageId();
            
            long duration = System.currentTimeMillis() - startTime;
            awsMetrics.recordSqsOperation("send", duration, true);
            
            return messageId;
        } catch (Exception e) {
            log.error("Failed to send SQS message", e);
            awsMetrics.recordSqsOperation("send", 0, false);
            throw new AwsException("Failed to send message", e);
        }
    }
}

/**
 * AWS exception
 */
class AwsException extends RuntimeException {
    
    public AwsException(String message) {
        super(message);
    }

    public AwsException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * AWS metrics
 */
@org.springframework.stereotype.Component
class AwsMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;

    public AwsMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordS3Operation(String operation, long duration, boolean success) {
        registry.timer("aws.s3.operation.duration",
            "operation", operation).record(java.time.Duration.ofMillis(duration));
        
        registry.counter("aws.s3.operation",
            "operation", operation,
            "status", success ? "success" : "failure").increment();
    }

    public void recordSnsOperation(String operation, long duration, boolean success) {
        registry.timer("aws.sns.operation.duration",
            "operation", operation).record(java.time.Duration.ofMillis(duration));
        
        registry.counter("aws.sns.operation",
            "operation", operation,
            "status", success ? "success" : "failure").increment();
    }

    public void recordSqsOperation(String operation, long duration, boolean success) {
        registry.timer("aws.sqs.operation.duration",
            "operation", operation).record(java.time.Duration.ofMillis(duration));
        
        registry.counter("aws.sqs.operation",
            "operation", operation,
            "status", success ? "success" : "failure").increment();
    }
}
