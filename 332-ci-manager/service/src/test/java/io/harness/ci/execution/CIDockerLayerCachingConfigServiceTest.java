/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution;

import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIDockerLayerCachingConfig;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CIDockerLayerCachingConfigServiceTest {
  String accountId;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
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
    when(ciExecutionServiceConfig.getDockerLayerCachingConfig()).thenReturn(expectedConfig);
    CIDockerLayerCachingConfig config = ciDockerLayerCachingConfigService.getDockerLayerCachingConfig(accountId);
    assertThat(expectedConfig).isEqualTo(config);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetDockerLayerCachingConfigFFDisabled() {
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_DLC, accountId)).thenReturn(false);
    CIDockerLayerCachingConfig config = ciDockerLayerCachingConfigService.getDockerLayerCachingConfig(accountId);
    assertThat(config).isNull();
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
    String expectedCacheFrom =
        "type=s3,endpoint_url=endpoint,bucket=bucket,mode=max,region=region,access_key_id=access_key,secret_access_key=secret_key";
    String cacheFrom = ciDockerLayerCachingConfigService.getCacheToArg(config, "");
    assertThat(expectedCacheFrom).isEqualTo(cacheFrom);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheToPrefix() {
    CIDockerLayerCachingConfig config = getConfig();
    String expectedCacheFrom =
        "type=s3,endpoint_url=endpoint,bucket=bucket,mode=max,region=region,access_key_id=access_key,secret_access_key=secret_key,prefix=test-account-id/test-prefix/";
    String cacheFrom = ciDockerLayerCachingConfigService.getCacheToArg(config, accountId + "/test-prefix/");
    assertThat(expectedCacheFrom).isEqualTo(cacheFrom);
  }
}
