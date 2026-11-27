package vip.mate.config;

import io.minio.*;
import org.apache.commons.math3.exception.InsufficientDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vip.mate.plugin.api.minio.CustomMinioClient;

@Configuration
public class MinioConfig {
    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);
    @Autowired
    private MinioProperties minioProperties;



    @Bean
    @ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
    public CustomMinioClient minioClient() throws InsufficientDataException {
        MinioClient minioClient = MinioClient.builder()
                .endpoint(minioProperties.getIp(), minioProperties.getPort(), false)
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
        CustomMinioClient client = new CustomMinioClient(minioClient);
        log.info("Minio连接成功:{}:{}", minioProperties.getIp(), minioProperties.getPort());
        return client;
    }

}