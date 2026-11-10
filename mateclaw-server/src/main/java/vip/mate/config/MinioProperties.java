package vip.mate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioProperties {
    @Value(value = "${minio.enabled:false}")
    private boolean enabled;

    @Value("${minio.ip:}")
    private String ip;

    @Value("${minio.port:0}")
    private int port;

    @Value("${minio.accessKey:}")
    private String accessKey;

    @Value("${minio.secretKey:}")
    private String secretKey;

    @Value("${minio.bucket:}")
    private String bucket;

    @Value("${minio.urlExpireTime:0}")
    private int urlExpireTime;

    @Value("${minio.maxParts:0}")
    private int maxParts;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public int getUrlExpireTime() {
        return urlExpireTime;
    }

    public void setUrlExpireTime(int urlExpireTime) {
        this.urlExpireTime = urlExpireTime;
    }

    public int getMaxParts() {
        return maxParts;
    }

    public void setMaxParts(int maxParts) {
        this.maxParts = maxParts;
    }
}