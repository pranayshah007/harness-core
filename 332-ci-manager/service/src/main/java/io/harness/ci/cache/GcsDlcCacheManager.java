/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cache;

import io.harness.beans.cache.api.CacheMetadataDetail;
import io.harness.ci.config.CIDockerLayerCachingConfig;
import io.harness.ci.config.CIDockerLayerCachingGCSConfig;
import io.harness.ci.config.CIExecutionServiceConfig;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageBatch;
import com.google.cloud.storage.StorageOptions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.InternalServerErrorException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GcsDlcCacheManager implements DlcCacheManager {
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;
  private static final int GCS_BATCH_DELETE_LENGTH = 1000;

  public CIDockerLayerCachingConfig getCacheConfig(String accountId) {
    return convertToDlcConfig(ciExecutionServiceConfig.getDockerLayerCachingGCSConfig());
  }

  public List<CacheMetadataDetail> getCacheMetadata(String accountId) {
    CIDockerLayerCachingGCSConfig config = ciExecutionServiceConfig.getDockerLayerCachingGCSConfig();
    if (config == null) {
      return new ArrayList<>();
    }
    log.info("Getting DLC cache metadata for account {}", accountId);
    List<CacheMetadataDetail> metadataList = new ArrayList<>();
    Storage storage = getClient(config.getProjectId());

    // Get all the blobs
    Page<Blob> blobPages = getBlobs(accountId, storage, config.getBucket());
    for (Blob blob : blobPages.iterateAll()) {
      metadataList.add(getDetailFromBlob(blob.getName(), blob.getSize()));
    }
    log.info("Found {} DLC cache blobs for account {}", metadataList.size(), accountId);
    return metadataList;
  }

  public List<CacheMetadataDetail> deleteCache(String accountId) {
    CIDockerLayerCachingGCSConfig config = ciExecutionServiceConfig.getDockerLayerCachingGCSConfig();
    if (config == null) {
      return new ArrayList<>();
    }
    log.info("Deleting DLC cache blobs for account {}", accountId);
    List<CacheMetadataDetail> deletedList = new ArrayList<>();
    Storage storage = getClient(config.getProjectId());

    // Get all the blobs
    Page<Blob> blobPages = getBlobs(accountId, storage, config.getBucket());
    List<Blob> blobs = new ArrayList<>();
    for (Blob blob : blobPages.iterateAll()) {
      blobs.add(blob);
    }
    log.info("Found {} DLC cache blobs for account {}", blobs.size(), accountId);

    // Delete the blobs in batches
    int batchIdx = 0;
    while (batchIdx < blobs.size()) {
      int batchEnd = Math.min(batchIdx + GCS_BATCH_DELETE_LENGTH, blobs.size());
      int deletedCount = batchEnd - batchIdx;

      // Create a batch request to delete
      StorageBatch batchRequest = storage.batch();
      while (batchIdx < batchEnd) {
        Blob blob = blobs.get(batchIdx);
        batchRequest.delete(blob.getBlobId());
        deletedList.add(getDetailFromBlob(blob.getName(), blob.getSize()));
        batchIdx++;
      }

      // Submit the batch request
      try {
        batchRequest.submit();
      } catch (Exception ex) {
        log.error("Failed to submit DLC batch delete request for account {}", accountId, ex);
        throw new InternalServerErrorException("Failed to purge cache");
      }
      log.info("Deleted batch of {} DLC cache blobs for account {}", deletedCount, accountId);
    }
    log.info("Deleted {} DLC cache blobs for account {}", deletedList.size(), accountId);
    return deletedList;
  }

  private Page<Blob> getBlobs(String accountId, Storage storage, String bucket) {
    try {
      return storage.list(bucket, Storage.BlobListOption.prefix(accountId));
    } catch (Exception ex) {
      log.error("Failed to list DLC cache blobs for account {}", accountId, ex);
      throw new InternalServerErrorException("Failed to list DLC cache blobs");
    }
  }

  protected Storage getClient(String projectId) {
    return StorageOptions.newBuilder().setProjectId(projectId).build().getService();
  }

  private CacheMetadataDetail getDetailFromBlob(String key, Long size) {
    String[] pathList = key.split("/");
    String deletedPath = String.join("/", Arrays.copyOfRange(pathList, 1, pathList.length));
    return CacheMetadataDetail.builder().cachePath(deletedPath).size(size).build();
  }

  private CIDockerLayerCachingConfig convertToDlcConfig(CIDockerLayerCachingGCSConfig gcsConfig) {
    if (gcsConfig == null) {
      return null;
    }
    return CIDockerLayerCachingConfig.builder()
        .endpoint(gcsConfig.getEndpoint())
        .bucket(gcsConfig.getBucket())
        .accessKey(gcsConfig.getAccessKey())
        .secretKey(gcsConfig.getSecretKey())
        .region(gcsConfig.getRegion())
        .build();
  }
}
