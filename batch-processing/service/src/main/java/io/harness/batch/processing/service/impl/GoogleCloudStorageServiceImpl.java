/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import static io.harness.ccm.billing.GcpServiceAccountServiceImpl.getCredentials;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.config.BillingDataPipelineConfig;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GoogleCloudStorageServiceImpl {
  private static final String GOOGLE_CREDENTIALS_PATH = "GOOGLE_CREDENTIALS_PATH";
  private BatchMainConfig config;
  @Autowired private FeatureFlagService featureFlagService;

  @Autowired
  public GoogleCloudStorageServiceImpl(BatchMainConfig config) {
    this.config = config;
  }

  public void uploadObject(String objectName, String filePath, String accountId) throws IOException {
    BillingDataPipelineConfig dataPipelineConfig = config.getBillingDataPipelineConfig();
    boolean usingWorkloadIdentity = Boolean.parseBoolean(System.getenv("USE_WORKLOAD_IDENTITY"));
    GoogleCredentials sourceCredentials;

    if (!usingWorkloadIdentity) {
      log.info("WI: In uploadObject. using older way");
      sourceCredentials = getCredentials(GOOGLE_CREDENTIALS_PATH);
    } else {
      log.info("WI: In uploadObject. using Google ADC");
      sourceCredentials = GoogleCredentials.getApplicationDefault();
    }
    String projectId = dataPipelineConfig.getGcpProjectId();
    String bucketName = dataPipelineConfig.getClusterDataGcsBucketName();
    String backupBucketName = dataPipelineConfig.getClusterDataGcsBackupBucketName();
    Storage storage =
        StorageOptions.newBuilder().setProjectId(projectId).setCredentials(sourceCredentials).build().getService();
    for (String bucket : new String[] {bucketName, backupBucketName}) {
      BlobId blobId = BlobId.of(bucket, objectName);
      BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
      if (!featureFlagService.isEnabled(FeatureName.RECOMMENDATION_EFFICIENCY_VIEW_UI, accountId)) {
        storage.create(blobInfo, Files.readAllBytes(Paths.get(filePath)));
      } else {
        try (FileChannel fileChannel = new FileInputStream(filePath).getChannel()) {
          log.info("avro fileSize: {}MB", fileChannel.size() / (1024 * 1024));
          try (WritableByteChannel writableChannel = storage.writer(blobInfo)) {
            ByteBuffer buffer = ByteBuffer.allocate(dataPipelineConfig.getBufferSizeInMB() * 1024 * 1024);
            int i = 0;
            while (fileChannel.read(buffer) != -1) {
              log.info("Buffer Size: {}MB. Processing chunk :: {}", buffer.capacity() / (1024 * 1024), i);
              buffer.flip();
              writableChannel.write(buffer);
              buffer.clear();
              i++;
            }
          }
        }
      }
      log.info("File " + filePath + " uploaded to bucket " + bucket + " as " + objectName);
    }
  }
}
