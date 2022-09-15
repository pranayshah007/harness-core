package io.harness.delegate.task.shell.ssh;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFileDelegateConfig;
import io.harness.delegate.beans.aws.s3.S3FileDetailRequest;
import io.harness.delegate.beans.aws.s3.S3FileDetailResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.AwsS3DelegateTaskHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import lombok.extern.slf4j.Slf4j;
import software.wings.service.impl.AwsApiHelperService;

import com.amazonaws.services.s3.model.S3Object;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static io.harness.logging.LogLevel.ERROR;
import static java.lang.String.format;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class AwsS3ArtifactCommandUnitHandler extends ArtifactCommandUnitHandler {

    private static final String AWS_S3_PATH = "https://s3.console.aws.amazon.com/s3/object/";

    @Inject private AwsNgConfigMapper awsNgConfigMapper;
    @Inject private AwsS3DelegateTaskHelper awsS3DelegateTaskHelper;
    @Inject private AwsApiHelperService awsApiHelperService;

    @Override
    protected InputStream downloadFromRemoteRepo(SshExecutorFactoryContext context, LogCallback logCallback) throws IOException {
        if(AwsS3FetchFileDelegateConfig.class.isAssignableFrom(context.getArtifactDelegateConfig().getClass())) {
            List<S3FileDetailResponse> s3responses = null;
            try {
                return getS3FileInputStream((AwsS3FetchFileDelegateConfig) context.getArtifactDelegateConfig(), logCallback);
            } catch (IOException e) {
                log.error(format("Error while fetching S3 artifact: %s", e.getMessage()));
            }
        }
        return null;
    }

    @Override
    public Long getArtifactSize(SshExecutorFactoryContext context, LogCallback logCallback) {
        if(AwsS3FetchFileDelegateConfig.class.isAssignableFrom(context.getArtifactDelegateConfig().getClass())) {

            try {
                updateArtifactMetadata(context);
                return getS3FileSize((AwsS3FetchFileDelegateConfig) context.getArtifactDelegateConfig(), logCallback);
            } catch (IOException e) {
                log.error(format("Error while fetching S3 artifact: %s", e.getMessage()));
            }
        }
        return 0L;
    }

    private Long getS3FileSize(AwsS3FetchFileDelegateConfig awsS3FetchFileDelegateConfig, LogCallback executionLogCallback) throws IOException {
        awsS3DelegateTaskHelper.decryptRequestDTOs(
                awsS3FetchFileDelegateConfig.getAwsConnector(), awsS3FetchFileDelegateConfig.getEncryptionDetails());
        AwsInternalConfig awsInternalConfig = getAwsInternalConfig(awsS3FetchFileDelegateConfig);
        S3FileDetailRequest s3FileDetailRequest = awsS3FetchFileDelegateConfig.getFileDetails().stream().findFirst().orElse(null);
        if (null != s3FileDetailRequest) {
            String fileKey = s3FileDetailRequest.getFileKey();
            String bucketName = s3FileDetailRequest.getBucketName();
            executionLogCallback.saveExecutionLog(format("Fetching %s file from s3 bucket: %s", fileKey, bucketName));
            try {
                S3Object s3Object = awsApiHelperService.getObjectFromS3(
                        awsInternalConfig, awsS3FetchFileDelegateConfig.getRegion(), bucketName, fileKey);
                return s3Object.getObjectMetadata().getContentLength();
            } catch (Exception e) {
                String errorMsg = format("Failed to fetch file %s from s3 bucket: %s : %s", fileKey, bucketName, e.getMessage());
                log.error(errorMsg, e.getMessage());
                executionLogCallback.saveExecutionLog(errorMsg, ERROR, CommandExecutionStatus.FAILURE);
                throw e;
            }
        }
        return 0L;
    }

    private InputStream getS3FileInputStream(
            AwsS3FetchFileDelegateConfig awsS3FetchFileDelegateConfig, LogCallback executionLogCallback) throws IOException {
        awsS3DelegateTaskHelper.decryptRequestDTOs(
                awsS3FetchFileDelegateConfig.getAwsConnector(), awsS3FetchFileDelegateConfig.getEncryptionDetails());
        AwsInternalConfig awsInternalConfig = getAwsInternalConfig(awsS3FetchFileDelegateConfig);
        S3FileDetailRequest s3FileDetailRequest = awsS3FetchFileDelegateConfig.getFileDetails().stream().findFirst().orElseThrow(IOException::new);
            String fileKey = s3FileDetailRequest.getFileKey();
            String bucketName = s3FileDetailRequest.getBucketName();

            executionLogCallback.saveExecutionLog(format("Fetching file: %s from s3 bucket: %s", fileKey, bucketName));
            try {
                S3Object s3Object = awsApiHelperService.getObjectFromS3(
                        awsInternalConfig, awsS3FetchFileDelegateConfig.getRegion(), bucketName, fileKey);
                return s3Object.getObjectContent();
            } catch (Exception e) {
                String errorMsg = format("Failed to fetch %s from s3 bucket: %s : %s", fileKey, bucketName, e.getMessage());
                log.error(errorMsg, e.getMessage());
                executionLogCallback.saveExecutionLog(errorMsg, ERROR, CommandExecutionStatus.FAILURE);
                throw e;
            }
    }

    private AwsInternalConfig getAwsInternalConfig(AwsS3FetchFileDelegateConfig awsS3FetchFileDelegateConfig) {
        AwsConnectorDTO awsConnectorDTO = awsS3FetchFileDelegateConfig.getAwsConnector();
        AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
        awsInternalConfig.setDefaultRegion(awsS3FetchFileDelegateConfig.getRegion());
        return awsInternalConfig;
    }

    private void updateArtifactMetadata(SshExecutorFactoryContext context) throws IOException{
        AwsS3FetchFileDelegateConfig awsS3FetchFileDelegateConfig =
                (AwsS3FetchFileDelegateConfig) context.getArtifactDelegateConfig();
        S3FileDetailRequest s3FileDetailRequest = awsS3FetchFileDelegateConfig.getFileDetails().stream().findFirst().orElseThrow(IOException::new);
        Map<String, String> artifactMetadata = context.getArtifactMetadata();
        String artifactPath = Paths
                .get(format("%s%s?region=%s&prefix=%s", AWS_S3_PATH, s3FileDetailRequest.getBucketName(), awsS3FetchFileDelegateConfig.getRegion(), s3FileDetailRequest.getFileKey()))
                .toString();
        artifactMetadata.put(io.harness.artifact.ArtifactMetadataKeys.artifactPath, artifactPath);
        artifactMetadata.put(ArtifactMetadataKeys.artifactName, s3FileDetailRequest.getFileKey());
    }
}
