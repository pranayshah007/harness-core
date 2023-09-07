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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.cache.api.CacheMetadataDetail;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIDockerLayerCachingConfig;
import io.harness.ci.config.CIDockerLayerCachingGCSConfig;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.rule.Owner;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageBatch;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.InternalServerErrorException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class GcsDlcCacheManagerTest {
  String accountId;
  String bucketName;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock private Storage storage;
  @Mock private BlobId blobId;
  @Mock private Page<Blob> mockBlobPage;
  @Mock private StorageBatch batchRequest;
  @Spy @InjectMocks GcsDlcCacheManager gcsDlcCacheManager;

  @Before
  public void setup() {
    accountId = "test-account-id";
    bucketName = "test-bucket-name";
    MockitoAnnotations.initMocks(this);
  }

  private CIDockerLayerCachingConfig getDlcConfig() {
    return CIDockerLayerCachingConfig.builder()
        .endpoint("endpoint")
        .bucket(bucketName)
        .accessKey("access_key")
        .secretKey("secret_key")
        .region("region")
        .build();
  }

  private CIDockerLayerCachingGCSConfig getConfig() {
    return CIDockerLayerCachingGCSConfig.builder()
        .endpoint("endpoint")
        .bucket(bucketName)
        .accessKey("access_key")
        .secretKey("secret_key")
        .region("region")
        .projectId("project_id")
        .build();
  }

  private String getKeyPath(int i) {
    return String.format("%s/key-%d", accountId, i);
  }

  private String getKeyWithoutPrefix(int i) {
    return String.format("key-%d", i);
  }

  public void configureMockBlobPage(int n) {
    ArrayList<Blob> list = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      Blob blob = Mockito.mock(Blob.class);
      Mockito.when(blob.getName()).thenReturn(getKeyPath(i));
      Mockito.when(blob.getBlobId()).thenReturn(blobId);
      list.add(blob);
    }
    Mockito.when(mockBlobPage.iterateAll()).thenReturn(ImmutableList.copyOf(list));
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheConfig() {
    CIDockerLayerCachingConfig expectedConfig = getDlcConfig();
    when(ciExecutionServiceConfig.getDockerLayerCachingGCSConfig()).thenReturn(getConfig());
    CIDockerLayerCachingConfig config = gcsDlcCacheManager.getCacheConfig(accountId);
    assertThat(config).isEqualTo(expectedConfig);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheConfigNull() {
    when(ciExecutionServiceConfig.getDockerLayerCachingGCSConfig()).thenReturn(null);
    CIDockerLayerCachingConfig config = gcsDlcCacheManager.getCacheConfig(accountId);
    assertThat(config).isEqualTo(null);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheMetadataConfigNull() {
    when(ciExecutionServiceConfig.getDockerLayerCachingGCSConfig()).thenReturn(null);
    List<CacheMetadataDetail> cacheMetadata = gcsDlcCacheManager.getCacheMetadata(accountId);
    assertThat(cacheMetadata).isEmpty();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testDeleteCacheMetadataConfigNull() {
    when(ciExecutionServiceConfig.getDockerLayerCachingGCSConfig()).thenReturn(null);
    List<CacheMetadataDetail> cacheMetadata = gcsDlcCacheManager.deleteCache(accountId);
    assertThat(cacheMetadata).isEmpty();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheMetadata() {
    CIDockerLayerCachingGCSConfig config = getConfig();
    configureMockBlobPage(3);
    when(ciExecutionServiceConfig.getDockerLayerCachingGCSConfig()).thenReturn(config);
    when(gcsDlcCacheManager.getClient(anyString())).thenReturn(storage);
    when(storage.list(bucketName, Storage.BlobListOption.prefix(accountId))).thenReturn(mockBlobPage);

    List<CacheMetadataDetail> cacheMetadata = gcsDlcCacheManager.getCacheMetadata(accountId);
    assertThat(cacheMetadata.size()).isEqualTo(3);
    for (int i = 0; i < cacheMetadata.size(); i++) {
      String detailCachePath = cacheMetadata.get(i).getCachePath();
      String expectedCachePath = getKeyWithoutPrefix(i);
      assertThat(detailCachePath).isEqualTo(expectedCachePath);
    }
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheMetadataGetClientFails() {
    CIDockerLayerCachingGCSConfig config = getConfig();
    when(ciExecutionServiceConfig.getDockerLayerCachingGCSConfig()).thenReturn(config);
    when(gcsDlcCacheManager.getClient(anyString()))
        .thenThrow(new InternalServerErrorException("Failed to create client"));

    assertThatThrownBy(() -> gcsDlcCacheManager.getCacheMetadata(accountId)).matches(throwable -> {
      assertThat(throwable).isInstanceOf(InternalServerErrorException.class);
      assertThat(throwable).hasMessage("Failed to create client");
      return true;
    });
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheMetadataListFails() {
    CIDockerLayerCachingGCSConfig config = getConfig();
    when(ciExecutionServiceConfig.getDockerLayerCachingGCSConfig()).thenReturn(config);
    when(gcsDlcCacheManager.getClient(anyString())).thenReturn(storage);
    when(storage.list(bucketName, Storage.BlobListOption.prefix(accountId)))
        .thenThrow(new InternalServerErrorException(""));

    assertThatThrownBy(() -> gcsDlcCacheManager.getCacheMetadata(accountId)).matches(throwable -> {
      assertThat(throwable).isInstanceOf(InternalServerErrorException.class);
      assertThat(throwable).hasMessage("Failed to list DLC cache blobs");
      return true;
    });
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testDeleteCacheMetadata() {
    CIDockerLayerCachingGCSConfig config = getConfig();
    configureMockBlobPage(3);
    when(ciExecutionServiceConfig.getDockerLayerCachingGCSConfig()).thenReturn(config);
    when(gcsDlcCacheManager.getClient(anyString())).thenReturn(storage);
    when(storage.list(bucketName, Storage.BlobListOption.prefix(accountId))).thenReturn(mockBlobPage);
    when(storage.batch()).thenReturn(batchRequest);

    List<CacheMetadataDetail> cacheMetadata = gcsDlcCacheManager.deleteCache(accountId);
    verify(batchRequest, times(3)).delete(any(BlobId.class));
    verify(batchRequest, times(1)).submit();
    assertThat(cacheMetadata.size()).isEqualTo(3);
    for (int i = 0; i < cacheMetadata.size(); i++) {
      String detailCachePath = cacheMetadata.get(i).getCachePath();
      String expectedCachePath = getKeyWithoutPrefix(i);
      assertThat(detailCachePath).isEqualTo(expectedCachePath);
    }
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testDeleteCacheMetadataMultipleBatches() {
    CIDockerLayerCachingGCSConfig config = getConfig();
    int blobCount = 1500;
    configureMockBlobPage(blobCount);
    when(ciExecutionServiceConfig.getDockerLayerCachingGCSConfig()).thenReturn(config);
    when(gcsDlcCacheManager.getClient(anyString())).thenReturn(storage);
    when(storage.list(bucketName, Storage.BlobListOption.prefix(accountId))).thenReturn(mockBlobPage);
    when(storage.batch()).thenReturn(batchRequest);

    List<CacheMetadataDetail> cacheMetadata = gcsDlcCacheManager.deleteCache(accountId);
    verify(batchRequest, times(blobCount)).delete(any(BlobId.class));
    verify(batchRequest, times(2)).submit();
    assertThat(cacheMetadata.size()).isEqualTo(blobCount);
    for (int i = 0; i < cacheMetadata.size(); i++) {
      String detailCachePath = cacheMetadata.get(i).getCachePath();
      String expectedCachePath = getKeyWithoutPrefix(i);
      assertThat(detailCachePath).isEqualTo(expectedCachePath);
    }
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testDeleteCacheMetadataGetClientFails() {
    CIDockerLayerCachingGCSConfig config = getConfig();
    configureMockBlobPage(3);
    when(ciExecutionServiceConfig.getDockerLayerCachingGCSConfig()).thenReturn(config);
    when(gcsDlcCacheManager.getClient(anyString()))
        .thenThrow(new InternalServerErrorException("Failed to create client"));

    assertThatThrownBy(() -> gcsDlcCacheManager.deleteCache(accountId)).matches(throwable -> {
      assertThat(throwable).isInstanceOf(InternalServerErrorException.class);
      assertThat(throwable).hasMessage("Failed to create client");
      return true;
    });
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testDeleteCacheMetadataListFails() {
    CIDockerLayerCachingGCSConfig config = getConfig();
    configureMockBlobPage(3);
    when(ciExecutionServiceConfig.getDockerLayerCachingGCSConfig()).thenReturn(config);
    when(gcsDlcCacheManager.getClient(anyString())).thenReturn(storage);
    when(storage.list(bucketName, Storage.BlobListOption.prefix(accountId)))
        .thenThrow(new InternalServerErrorException(""));

    assertThatThrownBy(() -> gcsDlcCacheManager.deleteCache(accountId)).matches(throwable -> {
      assertThat(throwable).isInstanceOf(InternalServerErrorException.class);
      assertThat(throwable).hasMessage("Failed to list DLC cache blobs");
      return true;
    });
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testDeleteCacheMetadataBatchSubmitFails() {
    CIDockerLayerCachingGCSConfig config = getConfig();
    configureMockBlobPage(3);
    when(ciExecutionServiceConfig.getDockerLayerCachingGCSConfig()).thenReturn(config);
    when(gcsDlcCacheManager.getClient(anyString())).thenReturn(storage);
    when(storage.list(bucketName, Storage.BlobListOption.prefix(accountId))).thenReturn(mockBlobPage);
    when(storage.batch()).thenReturn(batchRequest);
    doThrow(new InternalServerErrorException("")).when(batchRequest).submit();

    assertThatThrownBy(() -> gcsDlcCacheManager.deleteCache(accountId)).matches(throwable -> {
      verify(batchRequest, times(3)).delete(any(BlobId.class));
      verify(batchRequest, times(1)).submit();
      assertThat(throwable).isInstanceOf(InternalServerErrorException.class);
      assertThat(throwable).hasMessage("Failed to purge cache");
      return true;
    });
  }
}
