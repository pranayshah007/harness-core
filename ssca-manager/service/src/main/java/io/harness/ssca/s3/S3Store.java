/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.s3;

import io.harness.spec.server.ssca.v1.model.SbomProcessRequestBody;
import io.harness.ssca.S3Config;
import io.harness.ssca.entities.ArtifactEntity;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class S3Store {
  @Inject S3Config s3Config;

  public AmazonS3 newS3Client() {
    BasicAWSCredentials googleCreds = new BasicAWSCredentials(s3Config.getAccessKeyId(), s3Config.getAccessSecretKey());

    return AmazonS3ClientBuilder.standard()
        .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(s3Config.getEndpoint(), "auto"))
        .withCredentials(new AWSStaticCredentialsProvider(googleCreds))
        .build();
  }

  public void UploadSBOMToGCP(File file, String accountId, String orgIdentifier, String projectIdentifier,
      SbomProcessRequestBody sbomProcessRequestBody) {
    AmazonS3 s3Client = newS3Client();
    try {
      String s3FilePath = getSBOMGCSFolder(accountId, orgIdentifier, projectIdentifier,
          sbomProcessRequestBody.getSbomMetadata().getPipelineExecutionId(),
          sbomProcessRequestBody.getSbomMetadata().getStageIdentifier(),
          sbomProcessRequestBody.getSbomMetadata().getSequenceId(),
          sbomProcessRequestBody.getSbomMetadata().getPipelineIdentifier(),
          sbomProcessRequestBody.getSbomProcess().getName());
      PutObjectRequest request = new PutObjectRequest(s3Config.getBucket(), s3FilePath, file);
      PutObjectResult result = s3Client.putObject(request);
      log.info("File uploaded successfully. ETag: " + result.getETag());
    } catch (AmazonServiceException e) {
      log.error("Error uploading file: " + e.getErrorMessage());
    }
  }

  public File DownloadSBOMToGCP(ArtifactEntity artifactEntity) {
    AmazonS3 s3Client = newS3Client();
    String localFilePath = UUID.randomUUID().toString();
    File downloadedFile = new File(localFilePath);
    String s3FilePath = getSBOMGCSFolder(artifactEntity.getAccountId(), artifactEntity.getOrgId(),
        artifactEntity.getProjectId(), artifactEntity.getPipelineExecutionId(), artifactEntity.getStageId(),
        artifactEntity.getSequenceId(), artifactEntity.getPipelineId(), artifactEntity.getSbomName());
    try {
      S3Object s3Object = s3Client.getObject(new GetObjectRequest(s3Config.getBucket(), s3FilePath));
      InputStream objectData = s3Object.getObjectContent();

      try (FileOutputStream outputStream = new FileOutputStream(downloadedFile)) {
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = objectData.read(buffer)) != -1) {
          outputStream.write(buffer, 0, bytesRead);
        }
      }

      log.info("File downloaded successfully to: " + localFilePath);
    } catch (AmazonServiceException | IOException e) {
      log.error("Error downloading file: " + e.getMessage());
    }
    return downloadedFile;
  }

  private String getSBOMGCSFolder(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineExecutionId, String stageIdentifier, String sequenceId, String pipelineIdentifier,
      String sbomName) {
    return accountId + "/" + orgIdentifier + "/" + projectIdentifier + "/" + pipelineIdentifier + "/" + stageIdentifier
        + "/" + sequenceId + "/" + pipelineExecutionId + "/" + sbomName;
  }
}
