/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.FeatureName;
import io.harness.ci.config.CIDockerLayerCachingConfig;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.ff.CIFeatureFlagService;

import com.google.inject.Inject;

public class CIDockerLayerCachingConfigService {
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject CIFeatureFlagService featureFlagService;

  public CIDockerLayerCachingConfig getDockerLayerCachingConfig(String accountId) {
    if (!featureFlagService.isEnabled(FeatureName.CI_ENABLE_DLC, accountId)) {
      return null;
    }
    // Placeholder for vendor APIs for bucket creation. Instead of the global keys, return the
    // keys specific to the bucket of the user when APIs are implemented
    return ciExecutionServiceConfig.getDockerLayerCachingConfig();
  }

  public String getCacheFromArg(CIDockerLayerCachingConfig config, String prefix) {
    String cacheFrom =
        String.format("type=s3,endpoint_url=%s,bucket=%s,region=%s,access_key_id=%s,secret_access_key=%s",
            config.getEndpoint(), config.getBucket(), config.getRegion(), config.getAccessKey(), config.getSecretKey());
    if (!isEmpty(prefix)) {
      cacheFrom = String.format("%s,prefix=%s", cacheFrom, prefix);
    }
    return cacheFrom;
  }

  public String getCacheToArg(CIDockerLayerCachingConfig config, String prefix) {
    String cacheTo =
        String.format("type=s3,endpoint_url=%s,bucket=%s,mode=max,region=%s,access_key_id=%s,secret_access_key=%s",
            config.getEndpoint(), config.getBucket(), config.getRegion(), config.getAccessKey(), config.getSecretKey());
    if (!isEmpty(prefix)) {
      cacheTo = String.format("%s,prefix=%s", cacheTo, prefix);
    }
    return cacheTo;
  }
}
