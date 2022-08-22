/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.support;

import static io.harness.batch.processing.writer.constants.K8sCCMConstants.K8SV1_RELEASE_NAME;

import io.harness.batch.processing.tasklet.util.CacheUtils;
import io.harness.beans.FeatureName;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ff.FeatureFlagService;

import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HarnessServiceInfoFetcher extends CacheUtils {
  private final CloudToHarnessMappingService cloudToHarnessMappingService;
  private final K8sLabelServiceInfoFetcher k8sLabelServiceInfoFetcher;

  public final LoadingCache<CacheKey, Optional<HarnessServiceInfo>> getHarnessServiceInfoCache;
  private final FeatureFlagService featureFlagService;

  @Value
  private static class CacheKey {
    private String accountId;
    private String computeProviderId;
    private String namespace;
    private String podName;
  }

  @Inject
  public HarnessServiceInfoFetcher(K8sLabelServiceInfoFetcher k8sLabelServiceInfoFetcher,
      CloudToHarnessMappingService cloudToHarnessMappingService, FeatureFlagService featureFlagService) {
    this.k8sLabelServiceInfoFetcher = k8sLabelServiceInfoFetcher;
    this.cloudToHarnessMappingService = cloudToHarnessMappingService;
    this.featureFlagService = featureFlagService;
    this.getHarnessServiceInfoCache = Caffeine.newBuilder()
                                          .recordStats()
                                          .expireAfterAccess(24, TimeUnit.HOURS)
                                          .maximumSize(1_000)
                                          .build(key
                                              -> this.cloudToHarnessMappingService.getHarnessServiceInfo(
                                                  key.accountId, key.computeProviderId, key.namespace, key.podName));
  }

  public Optional<HarnessServiceInfo> fetchHarnessServiceInfo(
      String accountId, String computeProviderId, String namespace, String podName, Map<String, String> labelsMap) {
    try {
      Optional<HarnessServiceInfo> harnessServiceInfo = Optional.empty();
      if (labelsMap.containsKey(K8SV1_RELEASE_NAME)
          || featureFlagService.isEnabled(FeatureName.CE_HARNESS_INSTANCE_QUERY, accountId)) {
        harnessServiceInfo =
            getHarnessServiceInfoCache.get(new CacheKey(accountId, computeProviderId, namespace, podName));
      }
      if (!harnessServiceInfo.isPresent()) {
        harnessServiceInfo = k8sLabelServiceInfoFetcher.fetchHarnessServiceInfoFromCache(accountId, labelsMap);
      }
      return harnessServiceInfo;
    } catch (Exception ex) {
      log.error("Error while fetching data {}", ex);
      return Optional.ofNullable(null);
    }
  }
}
