/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cache;

import io.harness.beans.cache.api.CacheMetadataDetail;
import io.harness.ci.config.CIDockerLayerCachingConfig;
import io.harness.ci.config.CIExecutionServiceConfig;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsResult;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.InternalServerErrorException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class S3DlcCacheManager implements DlcCacheManager {
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;

  public CIDockerLayerCachingConfig getCacheConfig(String accountId) {
    return ciExecutionServiceConfig.getDockerLayerCachingConfig();
  }

  public List<CacheMetadataDetail> getCacheMetadata(String accountId) {
    CIDockerLayerCachingConfig config = ciExecutionServiceConfig.getDockerLayerCachingConfig();
    if (config == null) {
      return new ArrayList<>();
    }
    log.info("Getting DLC cache metadata for account {}", accountId);
    List<CacheMetadataDetail> metadataList = new ArrayList<>();
    AmazonS3 s3Client = getClient(config);
    String bucket = config.getBucket();

    ListObjectsV2Request request = new ListObjectsV2Request().withBucketName(bucket).withPrefix(accountId);
    boolean done = false;
    while (!done) {
      ListObjectsV2Result result = getBlobs(accountId, s3Client, request);
      log.info("Found {} DLC cache blobs for account {} with truncated result {}", result.getObjectSummaries().size(),
          accountId, result.isTruncated());
      for (S3ObjectSummary blob : result.getObjectSummaries()) {
        metadataList.add(getDetailFromBlob(blob.getKey(), blob.getSize()));
      }
      if (result.getNextContinuationToken() == null) {
        done = true;
      }
      request = new ListObjectsV2Request()
                    .withBucketName(config.getBucket())
                    .withPrefix(accountId)
                    .withContinuationToken(result.getNextContinuationToken());
    }
    shutdownClient(s3Client);
    log.info("Found {} DLC cache blobs for account {}", metadataList.size(), accountId);
    return metadataList;
  }

  public List<CacheMetadataDetail> deleteCache(String accountId) {
    CIDockerLayerCachingConfig config = ciExecutionServiceConfig.getDockerLayerCachingConfig();
    if (config == null) {
      return new ArrayList<>();
    }
    log.info("Deleting DLC cache blobs for account {}", accountId);
    AmazonS3 s3Client = getClient(config);
    List<CacheMetadataDetail> deletedList = new ArrayList<>();
    String bucket = config.getBucket();

    boolean isResultTruncated = true;
    while (isResultTruncated) {
      // List all blobs matching the prefix (SDK returns maximum 1000 keys)
      ListObjectsV2Request listRequest = new ListObjectsV2Request().withBucketName(bucket).withPrefix(accountId);
      ListObjectsV2Result listResult = getBlobs(accountId, s3Client, listRequest);
      List<S3ObjectSummary> blobs = listResult.getObjectSummaries();
      isResultTruncated = listResult.isTruncated();
      log.info("Found {} DLC cache blobs for account {} with truncated result {}", blobs.size(), accountId,
          isResultTruncated);

      // Delete all blobs listed
      ArrayList<DeleteObjectsRequest.KeyVersion> keys = new ArrayList<>();
      for (S3ObjectSummary blob : blobs) {
        keys.add(new DeleteObjectsRequest.KeyVersion(blob.getKey()));
        deletedList.add(getDetailFromBlob(blob.getKey(), blob.getSize()));
      }
      if (keys.isEmpty()) {
        continue;
      }

      DeleteObjectsRequest deleteRequest = new DeleteObjectsRequest(bucket).withKeys(keys).withQuiet(false);
      DeleteObjectsResult deleteResult;
      try {
        deleteResult = s3Client.deleteObjects(deleteRequest);
      } catch (Exception ex) {
        log.error("Failed to batch delete DLC cache blobs for account {}", accountId, ex);
        shutdownClient(s3Client);
        throw new InternalServerErrorException("Failed to purge cache");
      }
      log.info(
          "Deleted batch of {} DLC cache blobs for account {}", deleteResult.getDeletedObjects().size(), accountId);
    }
    shutdownClient(s3Client);
    log.info("Deleted {} DLC cache blobs for account {}", deletedList.size(), accountId);
    return deletedList;
  }

  protected AmazonS3 getClient(CIDockerLayerCachingConfig config) {
    BasicAWSCredentials credentials = new BasicAWSCredentials(config.getAccessKey(), config.getSecretKey());
    return AmazonS3Client.builder()
        .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(config.getEndpoint(), config.getRegion()))
        .withCredentials(new AWSStaticCredentialsProvider(credentials))
        .build();
  }

  private ListObjectsV2Result getBlobs(String accountId, AmazonS3 s3Client, ListObjectsV2Request listRequest) {
    try {
      return s3Client.listObjectsV2(listRequest);
    } catch (Exception ex) {
      log.error("Failed to list DLC cache blobs for account {}", accountId, ex);
      shutdownClient(s3Client);
      throw new InternalServerErrorException("Failed to list DLC cache blobs");
    }
  }

  private void shutdownClient(AmazonS3 client) {
    // Shutdown the client to release the resources
    try {
      client.shutdown();
    } catch (Exception ex) {
      log.warn("Failed to shutdown the AWS S3 client");
    }
  }

  private CacheMetadataDetail getDetailFromBlob(String key, Long size) {
    String[] pathList = key.split("/");
    String deletedPath = String.join("/", Arrays.copyOfRange(pathList, 1, pathList.length));
    return CacheMetadataDetail.builder().cachePath(deletedPath).size(size).build();
  }
}
