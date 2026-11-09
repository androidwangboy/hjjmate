package vip.mate.plugin.api.minio;

import com.google.common.collect.Multimap;
import io.minio.CreateMultipartUploadResponse;
import io.minio.ListPartsResponse;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.errors.*;
import io.minio.messages.Part;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class CustomMinioClient extends MinioClient {
    public CustomMinioClient(MinioClient client) {
        super(client);
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
