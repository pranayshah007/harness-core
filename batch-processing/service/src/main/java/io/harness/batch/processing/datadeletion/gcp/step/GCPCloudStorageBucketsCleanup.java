/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion.gcp.step;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionStep.GCP_GCS_BUCKET;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@OwnedBy(CE)
public class GCPCloudStorageBucketsCleanup {
  @Autowired BatchMainConfig configuration;

  public boolean delete(String accountId, DataDeletionRecord dataDeletionRecord, boolean dryRun) throws Exception {
    String projectId = configuration.getGcpConfig().getGcpProjectId();
    Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
    Page<Bucket> buckets = storage.list();
    while (true) {
      for (Bucket bucket : buckets.iterateAll()) {
        log.info("Checking bucket: {}", bucket.getName());
        Page<Blob> accountFolders = bucket.list(Storage.BlobListOption.currentDirectory());
        while (true) {
          for (Blob accountFolder : accountFolders.iterateAll()) {
            if (accountFolder.getName().contains(accountId)) {
              Page<Blob> blobs = bucket.list(Storage.BlobListOption.prefix(accountFolder.getName()));
              while (true) {
                List<BlobId> blobIdList = new ArrayList<>();
                blobs.iterateAll().forEach(blob -> blobIdList.add(blob.getBlobId()));
                if (!dryRun) {
                  List<Boolean> success = storage.delete(blobIdList);
                  if (!success.stream().allMatch(Boolean::booleanValue)) {
                    throw new Exception("Couldn't delete some blobs in folder: " + accountFolder.getName());
                  }
                }
                dataDeletionRecord.getRecords()
                    .get(GCP_GCS_BUCKET.name())
                    .setRecordsCount(dataDeletionRecord.getRecords().get(GCP_GCS_BUCKET.name()).getRecordsCount()
                        + (long) blobIdList.size());
                if (!blobs.hasNextPage()) {
                  break;
                }
                blobs = blobs.getNextPage();
              }
            }
          }
          if (!accountFolders.hasNextPage()) {
            break;
          }
          accountFolders = accountFolders.getNextPage();
        }
      }
      if (!buckets.hasNextPage()) {
        break;
      }
      buckets = buckets.getNextPage();
    }
    return true;
  }
}
