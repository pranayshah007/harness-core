/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cache;

import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.beans.cache.api.CacheMetadataDetail;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIDockerLayerCachingConfig;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.rule.Owner;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsResult;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.ws.rs.InternalServerErrorException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class S3DlcCacheManagerTest {
  String accountId;
  String bucketName;
  int keyCount;
  @Mock CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock AmazonS3 s3Client;
  @Spy @InjectMocks S3DlcCacheManager s3DlcCacheManager;

  @Before
  public void setup() {
    accountId = "test-account-id";
    bucketName = "test-bucket-name";
    keyCount = 3;
    MockitoAnnotations.initMocks(this);
  }

  private CIDockerLayerCachingConfig getConfig() {
    return CIDockerLayerCachingConfig.builder()
        .endpoint("endpoint")
        .bucket(bucketName)
        .accessKey("access_key")
        .secretKey("secret_key")
        .region("region")
        .build();
  }

  private String getKeyPath(int i) {
    return String.format("%s/key-%d", accountId, i);
  }

  private String getKeyWithoutPrefix(int i) {
    return String.format("key-%d", i);
  }

  private ListObjectsV2Result getListResult(boolean truncated, List<S3ObjectSummary> summaries) {
    ListObjectsV2Result result = new ListObjectsV2Result();
    result.setBucketName(bucketName);
    result.setTruncated(truncated);
    for (S3ObjectSummary summary : summaries) {
      result.getObjectSummaries().add(summary);
    }
    return result;
  }

  private List<S3ObjectSummary> getObjectSummaries(int start, int end) {
    List<S3ObjectSummary> summaries = new ArrayList<>();
    for (int i = start; i < end; i++) {
      S3ObjectSummary objectSummary = new S3ObjectSummary();
      objectSummary.setKey(getKeyPath(i));
      objectSummary.setBucketName(bucketName);
      objectSummary.setLastModified(new Date());
      objectSummary.setSize(3);
      summaries.add(objectSummary);
    }
    return summaries;
  }

  private DeleteObjectsResult getDeleteResult(int start, int end) {
    CIDockerLayerCachingConfig config = getConfig();
    List<DeleteObjectsResult.DeletedObject> deletedObjects = new ArrayList<>();
    for (int i = start; i < end; i++) {
      DeleteObjectsResult.DeletedObject obj = new DeleteObjectsResult.DeletedObject();
      obj.setKey(String.format("key-%d", i));
      deletedObjects.add(obj);
    }
    return new DeleteObjectsResult(deletedObjects);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheConfig() {
    CIDockerLayerCachingConfig expectedConfig = getConfig();
    when(ciExecutionServiceConfig.getDockerLayerCachingConfig()).thenReturn(expectedConfig);
    CIDockerLayerCachingConfig config = s3DlcCacheManager.getCacheConfig(accountId);
    assertThat(config).isEqualTo(expectedConfig);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheMetadataConfigNull() {
    when(ciExecutionServiceConfig.getDockerLayerCachingConfig()).thenReturn(null);
    List<CacheMetadataDetail> cacheMetadata = s3DlcCacheManager.getCacheMetadata(accountId);
    assertThat(cacheMetadata).isEmpty();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheMetadataSinglePage() {
    CIDockerLayerCachingConfig config = getConfig();
    when(ciExecutionServiceConfig.getDockerLayerCachingConfig()).thenReturn(config);
    when(s3DlcCacheManager.getClient(config)).thenReturn(s3Client);

    ListObjectsV2Result result = getListResult(false, getObjectSummaries(0, keyCount));
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(result);

    List<CacheMetadataDetail> cacheMetadata = s3DlcCacheManager.getCacheMetadata(accountId);
    assertThat(cacheMetadata.size()).isEqualTo(keyCount);
    for (int i = 0; i < cacheMetadata.size(); i++) {
      String detailCachePath = cacheMetadata.get(i).getCachePath();
      String expectedCachePath = getKeyWithoutPrefix(i);
      assertThat(detailCachePath).isEqualTo(expectedCachePath);
    }
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheMetadataMultiplePages() {
    CIDockerLayerCachingConfig config = getConfig();
    when(ciExecutionServiceConfig.getDockerLayerCachingConfig()).thenReturn(config);
    when(s3DlcCacheManager.getClient(config)).thenReturn(s3Client);

    ListObjectsV2Result result = getListResult(true, getObjectSummaries(0, keyCount));
    result.setNextContinuationToken("someToken");
    ListObjectsV2Result result2 = getListResult(false, getObjectSummaries(keyCount, 2 * keyCount));
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(result, result2);

    List<CacheMetadataDetail> cacheMetadata = s3DlcCacheManager.getCacheMetadata(accountId);
    assertThat(cacheMetadata.size()).isEqualTo(2 * keyCount);
    for (int i = 0; i < cacheMetadata.size(); i++) {
      String detailCachePath = cacheMetadata.get(i).getCachePath();
      String expectedCachePath = getKeyWithoutPrefix(i);
      assertThat(detailCachePath).isEqualTo(expectedCachePath);
    }
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheMetadataListFails() {
    CIDockerLayerCachingConfig config = getConfig();
    when(ciExecutionServiceConfig.getDockerLayerCachingConfig()).thenReturn(config);
    when(s3DlcCacheManager.getClient(config)).thenReturn(s3Client);

    ListObjectsV2Result result = getListResult(false, getObjectSummaries(0, keyCount));
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(new SdkClientException(""));

    assertThatThrownBy(() -> s3DlcCacheManager.getCacheMetadata(accountId)).matches(throwable -> {
      assertThat(throwable).isInstanceOf(InternalServerErrorException.class);
      assertThat(throwable).hasMessage("Failed to list DLC cache blobs");
      return true;
    });
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheMetadataListIsEmpty() {
    CIDockerLayerCachingConfig config = getConfig();
    when(ciExecutionServiceConfig.getDockerLayerCachingConfig()).thenReturn(config);
    when(s3DlcCacheManager.getClient(config)).thenReturn(s3Client);

    ListObjectsV2Result result = getListResult(false, getObjectSummaries(0, 0));
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(result);

    List<CacheMetadataDetail> cacheMetadata = s3DlcCacheManager.getCacheMetadata(accountId);
    assertThat(cacheMetadata.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheMetadataClientShutdownFails() {
    CIDockerLayerCachingConfig config = getConfig();
    when(ciExecutionServiceConfig.getDockerLayerCachingConfig()).thenReturn(config);
    when(s3DlcCacheManager.getClient(config)).thenReturn(s3Client);

    ListObjectsV2Result result = getListResult(false, getObjectSummaries(0, keyCount));
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(result);

    List<CacheMetadataDetail> cacheMetadata = s3DlcCacheManager.getCacheMetadata(accountId);
    assertThat(cacheMetadata.size()).isEqualTo(keyCount);
    for (int i = 0; i < cacheMetadata.size(); i++) {
      String detailCachePath = cacheMetadata.get(i).getCachePath();
      String expectedCachePath = getKeyWithoutPrefix(i);
      assertThat(detailCachePath).isEqualTo(expectedCachePath);
    }
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testDeleteCacheMetadataConfigNull() {
    when(ciExecutionServiceConfig.getDockerLayerCachingConfig()).thenReturn(null);
    List<CacheMetadataDetail> cacheMetadata = s3DlcCacheManager.deleteCache(accountId);
    assertThat(cacheMetadata).isEmpty();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testDeleteCacheMetadataSinglePage() {
    CIDockerLayerCachingConfig config = getConfig();
    when(ciExecutionServiceConfig.getDockerLayerCachingConfig()).thenReturn(config);
    when(s3DlcCacheManager.getClient(config)).thenReturn(s3Client);

    ListObjectsV2Result result = getListResult(false, getObjectSummaries(0, keyCount));
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(result);
    when(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(getDeleteResult(0, keyCount));

    List<CacheMetadataDetail> cacheMetadata = s3DlcCacheManager.deleteCache(accountId);
    assertThat(cacheMetadata.size()).isEqualTo(keyCount);
    for (int i = 0; i < cacheMetadata.size(); i++) {
      String detailCachePath = cacheMetadata.get(i).getCachePath();
      String expectedCachePath = getKeyWithoutPrefix(i);
      assertThat(detailCachePath).isEqualTo(expectedCachePath);
    }
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testDeleteCacheMetadataMultiplePages() {
    CIDockerLayerCachingConfig config = getConfig();
    when(ciExecutionServiceConfig.getDockerLayerCachingConfig()).thenReturn(config);
    when(s3DlcCacheManager.getClient(config)).thenReturn(s3Client);

    ListObjectsV2Result result = getListResult(true, getObjectSummaries(0, keyCount));
    ListObjectsV2Result result2 = getListResult(false, getObjectSummaries(keyCount, 2 * keyCount));
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(result, result2);
    when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
        .thenReturn(getDeleteResult(0, keyCount), getDeleteResult(keyCount, 2 * keyCount));

    List<CacheMetadataDetail> cacheMetadata = s3DlcCacheManager.deleteCache(accountId);
    assertThat(cacheMetadata.size()).isEqualTo(2 * keyCount);
    for (int i = 0; i < cacheMetadata.size(); i++) {
      String detailCachePath = cacheMetadata.get(i).getCachePath();
      String expectedCachePath = getKeyWithoutPrefix(i);
      assertThat(detailCachePath).isEqualTo(expectedCachePath);
    }
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testDeleteCacheMetadataListFails() {
    CIDockerLayerCachingConfig config = getConfig();
    when(ciExecutionServiceConfig.getDockerLayerCachingConfig()).thenReturn(config);
    when(s3DlcCacheManager.getClient(config)).thenReturn(s3Client);

    ListObjectsV2Result result = getListResult(false, getObjectSummaries(0, keyCount));
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(new SdkClientException(""));

    assertThatThrownBy(() -> s3DlcCacheManager.deleteCache(accountId)).matches(throwable -> {
      assertThat(throwable).isInstanceOf(InternalServerErrorException.class);
      assertThat(throwable).hasMessage("Failed to list DLC cache blobs");
      return true;
    });
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testDeleteCacheMetadataDeleteFails() {
    CIDockerLayerCachingConfig config = getConfig();
    when(ciExecutionServiceConfig.getDockerLayerCachingConfig()).thenReturn(config);
    when(s3DlcCacheManager.getClient(config)).thenReturn(s3Client);

    ListObjectsV2Result result = getListResult(false, getObjectSummaries(0, keyCount));
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(result);
    when(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenThrow(new SdkClientException(""));

    assertThatThrownBy(() -> s3DlcCacheManager.deleteCache(accountId)).matches(throwable -> {
      assertThat(throwable).isInstanceOf(InternalServerErrorException.class);
      assertThat(throwable).hasMessage("Failed to purge cache");
      return true;
    });
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testDeleteCacheMetadataListIsEmpty() {
    CIDockerLayerCachingConfig config = getConfig();
    when(ciExecutionServiceConfig.getDockerLayerCachingConfig()).thenReturn(config);
    when(s3DlcCacheManager.getClient(config)).thenReturn(s3Client);

    ListObjectsV2Result result = getListResult(false, getObjectSummaries(0, 0));
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(result);

    List<CacheMetadataDetail> cacheMetadata = s3DlcCacheManager.deleteCache(accountId);
    assertThat(cacheMetadata.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testDeleteCacheMetadataClientShutdownFails() {
    CIDockerLayerCachingConfig config = getConfig();
    when(ciExecutionServiceConfig.getDockerLayerCachingConfig()).thenReturn(config);
    when(s3DlcCacheManager.getClient(config)).thenReturn(s3Client);

    // Single page
    ListObjectsV2Result result = getListResult(false, getObjectSummaries(0, keyCount));
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(result);
    when(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(getDeleteResult(0, keyCount));
    doThrow(new SdkClientException("")).when(s3Client).shutdown();
    // Delete call
    List<CacheMetadataDetail> cacheMetadata = s3DlcCacheManager.deleteCache(accountId);

    // Assertions
    assertThat(cacheMetadata.size()).isEqualTo(keyCount);
    for (int i = 0; i < cacheMetadata.size(); i++) {
      String detailCachePath = cacheMetadata.get(i).getCachePath();
      String expectedCachePath = getKeyWithoutPrefix(i);
      assertThat(detailCachePath).isEqualTo(expectedCachePath);
    }
  }
}
