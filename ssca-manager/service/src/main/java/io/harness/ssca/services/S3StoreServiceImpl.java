/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.ssca.S3Config;
import io.harness.ssca.entities.ArtifactEntity;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
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
public class S3StoreServiceImpl implements S3StoreService {
  @Inject S3Config s3Config;

  @Inject AmazonS3 s3Client;

  @Override
  public File downloadSBOM(ArtifactEntity artifactEntity) {
    String localFilePath = UUID.randomUUID().toString();
    File downloadedFile = new File(localFilePath);
    String s3FilePath = getSBOMGCSFolder(artifactEntity);
    try {
      S3Object s3Object = s3Client.getObject(new GetObjectRequest(s3Config.getBucket(), s3FilePath));
      long size = s3Object.getObjectMetadata().getContentLength();
      int bufferSize = (int) size + 4096;
      InputStream objectData = s3Object.getObjectContent();

      try (FileOutputStream outputStream = new FileOutputStream(downloadedFile)) {
        byte[] buffer = new byte[bufferSize];
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

  @Override
  public void uploadSBOM(File file, ArtifactEntity artifact) {
    try {
      String s3FilePath = getSBOMGCSFolder(artifact);
      PutObjectRequest request = new PutObjectRequest(s3Config.getBucket(), s3FilePath, file);
      PutObjectResult result = s3Client.putObject(request);
      log.info("File uploaded successfully. ETag: " + result.getETag());
    } catch (AmazonServiceException e) {
      log.error("Error uploading file: " + e.getErrorMessage());
    }
  }

  private String getSBOMGCSFolder(ArtifactEntity artifact) {
    return artifact.getAccountId() + "/" + artifact.getOrgId() + "/" + artifact.getProjectId() + "/"
        + artifact.getPipelineId() + "/" + artifact.getStageId() + "/" + artifact.getSequenceId() + "/"
        + artifact.getPipelineExecutionId() + "/" + artifact.getSbomName();
  }
}
