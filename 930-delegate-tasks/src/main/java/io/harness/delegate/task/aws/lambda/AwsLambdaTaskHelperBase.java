/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.aws.lambda.exception.AwsLambdaExceptionConstants.BLANK_ARTIFACT_PATH;
import static io.harness.delegate.task.aws.lambda.exception.AwsLambdaExceptionConstants.BLANK_ARTIFACT_PATH_EXPLANATION;
import static io.harness.delegate.task.aws.lambda.exception.AwsLambdaExceptionConstants.BLANK_ARTIFACT_PATH_HINT;
import static io.harness.delegate.task.aws.lambda.exception.AwsLambdaExceptionConstants.DOWNLOAD_FROM_S3_EXPLANATION;
import static io.harness.delegate.task.aws.lambda.exception.AwsLambdaExceptionConstants.DOWNLOAD_FROM_S3_FAILED;
import static io.harness.delegate.task.aws.lambda.exception.AwsLambdaExceptionConstants.DOWNLOAD_FROM_S3_HINT;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;
import static io.harness.logging.LogLevel.ERROR;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;
import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.beans.DecryptableEntity;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.AwsLambdaToServerInstanceInfoMapper;
import io.harness.delegate.exception.AwsLambdaException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FileCreationException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.service.impl.AwsApiHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import software.amazon.awssdk.core.SdkBytes;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class AwsLambdaTaskHelperBase {
  @Inject private AwsLambdaInfraConfigHelper awsLambdaInfraConfigHelper;
  @Inject private AwsLambdaTaskHelper awsLambdaCommandTaskHelper;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private SecretDecryptionService secretDecryptionService;

  @Inject protected AwsApiHelperService awsApiHelperService;

  private final String AWS_LAMBDA_WORKING_DIR = "./aws-lambda-working-dir/";
  private final String TEMP_ARTIFACT_FILE = "tempArtifactFile";
  private final String latestVersionForAwsLambda = "$LATEST";
  public List<ServerInstanceInfo> getAwsLambdaServerInstanceInfo(AwsLambdaDeploymentReleaseData deploymentReleaseData)
      throws AwsLambdaException {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig =
        (AwsLambdaFunctionsInfraConfig) deploymentReleaseData.getAwsLambdaInfraConfig();
    awsLambdaInfraConfigHelper.decryptInfraConfig(awsLambdaFunctionsInfraConfig);
    AwsLambdaFunctionWithActiveVersions awsLambdaFunctionWithActiveVersions =
        awsLambdaCommandTaskHelper.getAwsLambdaFunctionWithActiveVersions(
            awsLambdaFunctionsInfraConfig, deploymentReleaseData.getFunction());
    if (awsLambdaFunctionWithActiveVersions != null && awsLambdaFunctionWithActiveVersions.getVersions() != null) {
      awsLambdaFunctionWithActiveVersions.getVersions().remove(latestVersionForAwsLambda);
    }
    return AwsLambdaToServerInstanceInfoMapper.toServerInstanceInfoList(awsLambdaFunctionWithActiveVersions,
        awsLambdaFunctionsInfraConfig.getRegion(), awsLambdaFunctionsInfraConfig.getInfraStructureKey());
  }

  /**
   * Download the previous version of artifact from S3 and prepare SdkBytes
   * @param funcCodeLocation Presigned URL to download file from S3
   * @param logCallback Used for logging
   * @return SdkBytes Aws Object that is required to update function for .zip packages
   * @throws IOException
   */
  protected SdkBytes downloadArtifactZipAndPrepareSdkBytesForRollback(String funcCodeLocation, LogCallback logCallback)
      throws IOException {
    // Create directory for downloading files
    String artifactDir = prepareArtifactDirectory();
    File tempArtifact = new File(Paths.get(artifactDir, TEMP_ARTIFACT_FILE).toAbsolutePath().toString());

    if (!tempArtifact.createNewFile()) {
      log.error("Failed to create new file");
      logCallback.saveExecutionLog("Failed to create a file for s3 object", ERROR);
      throw new FileCreationException("Failed to create file " + tempArtifact.getCanonicalPath(), null,
          ErrorCode.FILE_CREATE_ERROR, Level.ERROR, USER, null);
    }

    // Call S3 with presigned url to fetch file
    InputStream is;
    try {
      OkHttpClient client = new OkHttpClient();
      Request request = new Request.Builder().url(funcCodeLocation).build();
      Response response = client.newCall(request).execute();
      if (!response.isSuccessful()) {
        throw NestedExceptionUtils.hintWithExplanationException("Call to AWS S3 failed with error code: %s",
            String.format("", response.code()), new InvalidRequestException("Failed to download file"));
      }
      FileOutputStream fos = new FileOutputStream(tempArtifact);
      fos.write(response.body().bytes());
      fos.close();

      is = new FileInputStream(tempArtifact);
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      log.error("Failed to download artifact from s3. Rollback failed.", sanitizedException);
      logCallback.saveExecutionLog(
          "Failed to download artifact from s3. " + ExceptionUtils.getMessage(sanitizedException), ERROR);
      throw new InvalidRequestException("Unable to download file from S3. Rollback failed", sanitizedException);
    } finally {
      deleteDirectoryAndItsContentIfExists(AWS_LAMBDA_WORKING_DIR);
    }

    return SdkBytes.fromInputStream(is);
  }

  private String prepareArtifactDirectory() throws IOException {
    String workingDir = AWS_LAMBDA_WORKING_DIR + UUIDGenerator.generateUuid();
    String artifactDir = Paths.get(workingDir).toString();

    createDirectoryIfDoesNotExist(artifactDir);
    waitForDirectoryToBeAccessibleOutOfProcess(artifactDir, 10);
    return artifactDir;
  }

  protected SdkBytes downloadArtifactFromS3BucketAndPrepareSdkBytes(
      AwsLambdaS3ArtifactConfig s3ArtifactConfig, LogCallback executionLogCallback) throws IOException {
    if (EmptyPredicate.isEmpty(s3ArtifactConfig.getFilePath())) {
      executionLogCallback.saveExecutionLog("artifactPath or artifactPathFilter is blank", ERROR);
      throw NestedExceptionUtils.hintWithExplanationException(BLANK_ARTIFACT_PATH_HINT,
          String.format(BLANK_ARTIFACT_PATH_EXPLANATION, s3ArtifactConfig.getIdentifier()),
          new AwsLambdaException(BLANK_ARTIFACT_PATH));
    }

    String s3ArtifactDir = prepareArtifactDirectory();

    String artifactPath = Paths.get(s3ArtifactConfig.getBucketName(), s3ArtifactConfig.getFilePath()).toString();

    // Test if artifact file can be created in the directory
    checkCanCreateFile(executionLogCallback, s3ArtifactDir);

    executionLogCallback.saveExecutionLog(color(format("Downloading %s artifact with identifier: %s",
                                                    s3ArtifactConfig.getType(), s3ArtifactConfig.getIdentifier()),
        White, Bold));
    executionLogCallback.saveExecutionLog("S3 Object Path: " + artifactPath);
    List<DecryptableEntity> decryptableEntities =
        s3ArtifactConfig.getConnectorDTO().getConnectorConfig().getDecryptableEntities();
    if (isNotEmpty(decryptableEntities)) {
      for (DecryptableEntity entity : decryptableEntities) {
        secretDecryptionService.decrypt(entity, s3ArtifactConfig.getEncryptedDataDetails());
      }
    }
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        s3ArtifactConfig.getConnectorDTO().getConnectorConfig(), s3ArtifactConfig.getEncryptedDataDetails());
    AwsInternalConfig awsConfig = awsNgConfigMapper.createAwsInternalConfig(
        (AwsConnectorDTO) s3ArtifactConfig.getConnectorDTO().getConnectorConfig());
    String region =
        EmptyPredicate.isNotEmpty(s3ArtifactConfig.getRegion()) ? s3ArtifactConfig.getRegion() : AWS_DEFAULT_REGION;
    try (InputStream artifactInputStream =
             awsApiHelperService
                 .getObjectFromS3(awsConfig, region, s3ArtifactConfig.getBucketName(), s3ArtifactConfig.getFilePath())
                 .getObjectContent()) {
      if (artifactInputStream == null) {
        log.error("Failure in downloading artifact from S3");
        executionLogCallback.saveExecutionLog("Failed to download artifact from S3", ERROR);
        throw NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_FROM_S3_HINT,
            String.format(
                DOWNLOAD_FROM_S3_EXPLANATION, s3ArtifactConfig.getBucketName(), s3ArtifactConfig.getFilePath()),
            new AwsLambdaException(format(DOWNLOAD_FROM_S3_FAILED, s3ArtifactConfig.getIdentifier())));
      }
      executionLogCallback.saveExecutionLog(color("Successfully downloaded artifact..", White, Bold));
      return SdkBytes.fromInputStream(artifactInputStream);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in downloading artifact from s3", sanitizedException);
      executionLogCallback.saveExecutionLog(
          "Failed to download artifact from s3. " + ExceptionUtils.getMessage(sanitizedException), ERROR);
      throw NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_FROM_S3_HINT,
          format(DOWNLOAD_FROM_S3_EXPLANATION, s3ArtifactConfig.getBucketName(), s3ArtifactConfig.getFilePath()),
          new AwsLambdaException(
              format(DOWNLOAD_FROM_S3_FAILED, s3ArtifactConfig.getIdentifier()), sanitizedException));
    } finally {
      deleteDirectoryAndItsContentIfExists(AWS_LAMBDA_WORKING_DIR);
    }
  }

  protected void checkCanCreateFile(LogCallback executionLogCallback, String s3ArtifactDir) throws IOException {
    String artifactFilePath = Paths.get(s3ArtifactDir, TEMP_ARTIFACT_FILE).toAbsolutePath().toString();
    File artifactFile = new File(artifactFilePath);
    if (!artifactFile.createNewFile()) {
      log.error("Failed to create new file");
      executionLogCallback.saveExecutionLog("Failed to create a file for s3 object", ERROR);
      throw new FileCreationException("Failed to create file " + artifactFile.getCanonicalPath(), null,
          ErrorCode.FILE_CREATE_ERROR, Level.ERROR, USER, null);
    }
  }
}
