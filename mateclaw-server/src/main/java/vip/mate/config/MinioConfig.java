package vip.mate.config;

import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vip.mate.plugin.api.minio.CustomMinioClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Configuration
public class MinioConfig {
    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);
    @Autowired
    private MinioProperties minioProperties;

    @Value(value = "${minio.enabled}")
    private boolean minioEnabled;
    @Bean
    public CustomMinioClient minioClient() throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        if (!minioEnabled) {
            log.info("Minio enabled:{}", minioEnabled);
            return null;
        }
        MinioClient minioClient = MinioClient.builder()
                .endpoint(minioProperties.getIp(), minioProperties.getPort(), false)
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
        CustomMinioClient client = new CustomMinioClient(minioClient);
        log.info("Minio连接成功:{}:{}", minioProperties.getIp(), minioProperties.getPort());
        File file = new File("");
        FileInputStream inputStream = new FileInputStream("C:\\Users\\ben\\Desktop\\审核文件.docx");
        try {

            String objectName = UUID.randomUUID() + "_测试.docx" ;
            ObjectWriteResponse objectWriteResponse = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .stream(inputStream, file.length(), -1)
                            .contentType(getContentType(file.getAbsolutePath()))
                            .build()
            );
            System.out.println(objectWriteResponse);
        } finally {
            inputStream.close();
        }
        return client;
    }

    private String getContentType(String fileName) {
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".pdf")) return "application/pdf";
        if (fileName.endsWith(".txt")) return "text/plain";
        return "application/octet-stream"; // 默认二进制流
    }
}
