package vip.mate.plugin.api.minio;

import com.google.common.collect.Multimap;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class CustomMinioClient extends MinioClient {
    private final Logger log = LoggerFactory.getLogger(CustomMinioClient.class);

    public CustomMinioClient(MinioClient client) {
        super(client);
    }

    public ObjectWriteResponse put(String bucketName, String objectName, File file){
        try(InputStream inputStream = new FileInputStream(file)){
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, file.length(), -1)
                    .contentType(getContentType(file.getAbsolutePath()));
            return this.putObject(builder.build());
        } catch (Exception e) {
            log.error("上传minio失败", e);
            return null;
        }
    }

    public ObjectWriteResponse put(String bucketName, String objectName, byte[] content, String mimeType){
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = "application/octet-stream";
        }
        try(InputStream inputStream = new ByteArrayInputStream(content)){
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, content.length, -1)
                    .contentType(mimeType);
            return this.putObject(builder.build());
        } catch (Exception e) {
            log.error("上传minio失败", e);
            return null;
        }
    }

    private String getContentType(String fileName) {
        // 1. JDK 内置 MIME 表，零依赖，覆盖大部分常见类型（含 docx/xlsx/zip 等）
        String type = java.net.URLConnection.guessContentTypeFromName(fileName);
        if (type != null) {
            return type;
        }
        // 2. 退而求其次，用文件系统探针（依赖操作系统，可能返回 null）
        try {
            type = Files.probeContentType(Paths.get(fileName));
            if (type != null) {
                return type;
            }
        } catch (IOException ignored) {
        }
        // 3. 仍识别不出时兜底为二进制流
        return "application/octet-stream";
    }

    public String initMultiPartUpload(String bucketName, String region, String objectName, Multimap<String, String> headers
            , Multimap<String, String> extraQueryParams) throws ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, IOException, InvalidKeyException, XmlParserException, InvalidResponseException, InternalException {
        CreateMultipartUploadResponse multipartUpload = this.createMultipartUpload(bucketName, region, objectName, headers, extraQueryParams);
        return multipartUpload == null ? "" : multipartUpload.result().uploadId();
    }

    public ObjectWriteResponse mergeMultiPartUpload(String bucketName, String region, String objectName, String uploadId
            , Part[] parts, Multimap<String, String> extraHeaders, Multimap<String, String> extraQueryParams) throws ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, IOException, InvalidKeyException, XmlParserException, InvalidResponseException, InternalException {
        return this.completeMultipartUpload(bucketName, region, objectName, uploadId, parts, extraHeaders, extraQueryParams);
    }

    public ListPartsResponse listMultiPart(String bucketName, String region, String objectName, Integer maxParts
            , Integer partNumberMarker, String uploadId, Multimap<String, String> extraHeaders, Multimap<String, String> extraQueryParams) throws ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, IOException, InvalidKeyException, XmlParserException, InvalidResponseException, InternalException {
        return this.listParts(bucketName, region, objectName, maxParts, partNumberMarker, uploadId, extraHeaders, extraQueryParams);
    }
}