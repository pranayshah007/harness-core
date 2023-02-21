package io.harness.delegate.task.sam;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.beans.DecryptableEntity;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.sam.AwsSamArtifactConfig;
import io.harness.delegate.task.aws.sam.AwsSamS3ArtifactConfig;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FileCreationException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.serverless.ServerlessCommandExecutionException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import software.wings.service.impl.AwsApiHelperService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.BLANK_ARTIFACT_PATH;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.BLANK_ARTIFACT_PATH_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.BLANK_ARTIFACT_PATH_HINT;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_S3_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_S3_FAILED;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.DOWNLOAD_FROM_S3_HINT;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;
import static io.harness.logging.LogLevel.ERROR;
import static java.lang.String.format;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;
import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AwsSamArtifactHelper {

  @Inject
  private SecretDecryptionService secretDecryptionService;

  @Inject private AwsNgConfigMapper awsNgConfigMapper;

  @Inject protected AwsApiHelperService awsApiHelperService;

  private static final String ARTIFACT_DIR_NAME = "harnessArtifact";
  private static final String ARTIFACT_FILE_NAME = "artifactFile";

  public void fetchArtifact(AwsSamArtifactConfig awsSamArtifactConfig, LogCallback logCallback,
                            String workingDirectory) throws IOException {

    if (awsSamArtifactConfig instanceof AwsSamS3ArtifactConfig) {
      AwsSamS3ArtifactConfig serverlessS3ArtifactConfig = (AwsSamS3ArtifactConfig) awsSamArtifactConfig;
      String s3Directory = Paths.get(workingDirectory, ARTIFACT_DIR_NAME).toString();
      createDirectoryIfDoesNotExist(s3Directory);
      waitForDirectoryToBeAccessibleOutOfProcess(s3Directory, 10);
      fetchS3Artifact(serverlessS3ArtifactConfig, logCallback, s3Directory, ARTIFACT_FILE_NAME);
    }

  }

  public void fetchS3Artifact(AwsSamS3ArtifactConfig awsSamS3ArtifactConfig, LogCallback executionLogCallback,
                              String s3Directory, String savedArtifactFileName) throws IOException {
    if (EmptyPredicate.isEmpty(awsSamS3ArtifactConfig.getFilePath())) {
      executionLogCallback.saveExecutionLog("artifactPath or artifactPathFilter is blank", ERROR);
      throw NestedExceptionUtils.hintWithExplanationException(BLANK_ARTIFACT_PATH_HINT,
              String.format(BLANK_ARTIFACT_PATH_EXPLANATION, awsSamS3ArtifactConfig.getIdentifier()),
              new ServerlessCommandExecutionException(BLANK_ARTIFACT_PATH));
    }

    String artifactPath = Paths.get(awsSamS3ArtifactConfig.getBucketName(), awsSamS3ArtifactConfig.getFilePath()).toString();
    String artifactFilePath = Paths.get(s3Directory, savedArtifactFileName).toAbsolutePath().toString();
    File artifactFile = new File(artifactFilePath);
    if (!artifactFile.createNewFile()) {
      log.error("Failed to create new file");
      executionLogCallback.saveExecutionLog("Failed to create a file for s3 object", ERROR);
      throw new FileCreationException("Failed to create file " + artifactFile.getCanonicalPath(), null,
              ErrorCode.FILE_CREATE_ERROR, Level.ERROR, USER, null);
    }
    executionLogCallback.saveExecutionLog(
            color(format("Downloading %s artifact with identifier: %s", awsSamS3ArtifactConfig.getAwsSamArtifactType(),
                            awsSamS3ArtifactConfig.getIdentifier()),
                    White, Bold));

    executionLogCallback.saveExecutionLog("S3 Object Path: " + artifactPath);

    List<DecryptableEntity> decryptableEntities =
            awsSamS3ArtifactConfig.getConnectorDTO().getConnectorConfig().getDecryptableEntities();
    if (isNotEmpty(decryptableEntities)) {
      for (DecryptableEntity entity : decryptableEntities) {
        secretDecryptionService.decrypt(entity, awsSamS3ArtifactConfig.getEncryptedDataDetails());
      }
    }
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
            awsSamS3ArtifactConfig.getConnectorDTO().getConnectorConfig(), awsSamS3ArtifactConfig.getEncryptedDataDetails());

    AwsInternalConfig awsConfig = awsNgConfigMapper.createAwsInternalConfig(
            (AwsConnectorDTO) awsSamS3ArtifactConfig.getConnectorDTO().getConnectorConfig());
    String region =
            EmptyPredicate.isNotEmpty(awsSamS3ArtifactConfig.getRegion()) ? awsSamS3ArtifactConfig.getRegion() : AWS_DEFAULT_REGION;
    try (InputStream artifactInputStream =
                 awsApiHelperService
                         .getObjectFromS3(awsConfig, region, awsSamS3ArtifactConfig.getBucketName(), awsSamS3ArtifactConfig.getFilePath())
                         .getObjectContent();
         FileOutputStream outputStream = new FileOutputStream(artifactFile)) {
      if (artifactInputStream == null) {
        log.error("Failure in downloading artifact from S3");
        executionLogCallback.saveExecutionLog("Failed to download artifact from S3.ø", ERROR);
        throw NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_FROM_S3_HINT,
                String.format(
                        DOWNLOAD_FROM_S3_EXPLANATION, awsSamS3ArtifactConfig.getBucketName(), awsSamS3ArtifactConfig.getFilePath()),
                new ServerlessCommandExecutionException(format(DOWNLOAD_FROM_S3_FAILED, awsSamS3ArtifactConfig.getIdentifier())));
      }
      IOUtils.copy(artifactInputStream, outputStream);
      executionLogCallback.saveExecutionLog(color("Successfully downloaded artifact..", White, Bold));
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in downloading artifact from s3", sanitizedException);
      executionLogCallback.saveExecutionLog(
              "Failed to download artifact from s3. " + ExceptionUtils.getMessage(sanitizedException), ERROR);
      throw NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_FROM_S3_HINT,
              String.format(DOWNLOAD_FROM_S3_EXPLANATION, awsSamS3ArtifactConfig.getBucketName(), awsSamS3ArtifactConfig.getFilePath()),
              new ServerlessCommandExecutionException(
                      format(DOWNLOAD_FROM_S3_FAILED, awsSamS3ArtifactConfig.getIdentifier()), sanitizedException));
    }
  }
}
