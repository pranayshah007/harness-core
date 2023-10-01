/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.cache.api.CacheMetadataDetail;
import io.harness.category.element.UnitTests;
import io.harness.ci.cache.GcsDlcCacheManager;
import io.harness.ci.cache.S3DlcCacheManager;
import io.harness.ci.config.CIDockerLayerCachingConfig;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CIDockerLayerCachingConfigServiceTest {
  String accountId;
  @Mock private GcsDlcCacheManager gcsDlcCacheManager;
  @Mock private S3DlcCacheManager s3DlcCacheManager;
  @Mock private CIFeatureFlagService featureFlagService;
  @InjectMocks private CIDockerLayerCachingConfigService ciDockerLayerCachingConfigService;

  @Before
  public void setup() {
    accountId = "test-account-id";
    MockitoAnnotations.initMocks(this);
  }

  private CIDockerLayerCachingConfig getConfig() {
    return CIDockerLayerCachingConfig.builder()
        .endpoint("endpoint")
        .bucket("bucket")
        .accessKey("access_key")
        .secretKey("secret_key")
        .region("region")
        .build();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetDockerLayerCachingConfig() {
    CIDockerLayerCachingConfig expectedConfig = getConfig();

    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_DLC, accountId)).thenReturn(true);
    when(s3DlcCacheManager.getCacheConfig(accountId)).thenReturn(expectedConfig);
    CIDockerLayerCachingConfig config = ciDockerLayerCachingConfigService.getDockerLayerCachingConfig(accountId, true);
    assertThat(expectedConfig).isEqualTo(config);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetDockerLayerCachingConfigFFDisabled() {
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_DLC, accountId)).thenReturn(false);
    CIDockerLayerCachingConfig config = ciDockerLayerCachingConfigService.getDockerLayerCachingConfig(accountId, true);
    assertThat(config).isNull();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetDockerLayerCachingGCSConfig() {
    CIDockerLayerCachingConfig expectedConfig = getConfig();

    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_DLC, accountId)).thenReturn(true);
    when(gcsDlcCacheManager.getCacheConfig(accountId)).thenReturn(expectedConfig);
    CIDockerLayerCachingConfig config = ciDockerLayerCachingConfigService.getDockerLayerCachingConfig(accountId, false);
    assertThat(expectedConfig).isEqualTo(config);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPurgeDockerLayerCacheDlcDisabled() {
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_DLC, accountId)).thenReturn(false);
    List<CacheMetadataDetail> detailList = ciDockerLayerCachingConfigService.purgeDockerLayerCache(accountId);
    assertThat(detailList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPurgeDockerLayerCache() {
    List<CacheMetadataDetail> expectedList = new ArrayList<>();
    expectedList.add(CacheMetadataDetail.builder().cachePath("cachePath").size(3).build());

    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_DLC, accountId)).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_BARE_METAL, accountId)).thenReturn(true);
    when(s3DlcCacheManager.getCacheConfig(accountId)).thenReturn(getConfig());
    when(s3DlcCacheManager.deleteCache(accountId)).thenReturn(expectedList);
    List<CacheMetadataDetail> detailList = ciDockerLayerCachingConfigService.purgeDockerLayerCache(accountId);
    assertThat(detailList).isEqualTo(expectedList);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPurgeDockerLayerCacheGCS() {
    List<CacheMetadataDetail> expectedList = new ArrayList<>();
    expectedList.add(CacheMetadataDetail.builder().cachePath("cachePath").size(3).build());

    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_DLC, accountId)).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_BARE_METAL, accountId)).thenReturn(false);
    when(gcsDlcCacheManager.getCacheConfig(accountId)).thenReturn(getConfig());
    when(gcsDlcCacheManager.deleteCache(accountId)).thenReturn(expectedList);
    List<CacheMetadataDetail> detailList = ciDockerLayerCachingConfigService.purgeDockerLayerCache(accountId);
    assertThat(detailList).isEqualTo(expectedList);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetDockerLayerCacheMetadataDlcDisabled() {
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_DLC, accountId)).thenReturn(false);
    List<CacheMetadataDetail> detailList = ciDockerLayerCachingConfigService.getDockerLayerCacheMetadata(accountId);
    assertThat(detailList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetDockerLayerCacheMetadata() {
    List<CacheMetadataDetail> expectedList = new ArrayList<>();
    expectedList.add(CacheMetadataDetail.builder().cachePath("cachePath").size(3).build());

    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_DLC, accountId)).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_BARE_METAL, accountId)).thenReturn(true);
    when(s3DlcCacheManager.getCacheConfig(accountId)).thenReturn(getConfig());
    when(s3DlcCacheManager.getCacheMetadata(accountId)).thenReturn(expectedList);
    List<CacheMetadataDetail> detailList = ciDockerLayerCachingConfigService.getDockerLayerCacheMetadata(accountId);
    assertThat(detailList).isEqualTo(expectedList);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetDockerLayerCacheMetadataGCS() {
    List<CacheMetadataDetail> expectedList = new ArrayList<>();
    expectedList.add(CacheMetadataDetail.builder().cachePath("cachePath").size(3).build());

    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_DLC, accountId)).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_BARE_METAL, accountId)).thenReturn(false);
    when(gcsDlcCacheManager.getCacheConfig(accountId)).thenReturn(getConfig());
    when(gcsDlcCacheManager.getCacheMetadata(accountId)).thenReturn(expectedList);
    List<CacheMetadataDetail> detailList = ciDockerLayerCachingConfigService.getDockerLayerCacheMetadata(accountId);
    assertThat(detailList).isEqualTo(expectedList);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheFrom() {
    CIDockerLayerCachingConfig config = getConfig();
    String expectedCacheFrom =
        "type=s3,endpoint_url=endpoint,bucket=bucket,region=region,access_key_id=access_key,secret_access_key=secret_key";
    String cacheFrom = ciDockerLayerCachingConfigService.getCacheFromArg(config, "");
    assertThat(expectedCacheFrom).isEqualTo(cacheFrom);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheFromWithPrefix() {
    CIDockerLayerCachingConfig config = getConfig();
    String expectedCacheFrom =
        "type=s3,endpoint_url=endpoint,bucket=bucket,region=region,access_key_id=access_key,secret_access_key=secret_key,prefix=test-account-id/test-prefix/";
    String cacheFrom = ciDockerLayerCachingConfigService.getCacheFromArg(config, accountId + "/test-prefix/");
    assertThat(expectedCacheFrom).isEqualTo(cacheFrom);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheTo() {
    CIDockerLayerCachingConfig config = getConfig();
    String expectedCacheTo =
        "type=s3,endpoint_url=endpoint,bucket=bucket,mode=max,region=region,access_key_id=access_key,secret_access_key=secret_key,ignore-error=true";
    String cacheTo = ciDockerLayerCachingConfigService.getCacheToArg(config, "");
    assertThat(expectedCacheTo).isEqualTo(cacheTo);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheToPrefix() {
    CIDockerLayerCachingConfig config = getConfig();
    String expectedCacheTo =
        "type=s3,endpoint_url=endpoint,bucket=bucket,mode=max,region=region,access_key_id=access_key,secret_access_key=secret_key,ignore-error=true,prefix=test-account-id/test-prefix/";
    String cacheTo = ciDockerLayerCachingConfigService.getCacheToArg(config, accountId + "/test-prefix/");
    assertThat(expectedCacheTo).isEqualTo(cacheTo);
  }
}
