/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.support;

import static io.harness.batch.processing.writer.constants.K8sCCMConstants.RELEASE_NAME;

import io.harness.batch.processing.tasklet.util.CacheUtils;
import io.harness.ccm.HarnessServiceInfoNG;
import io.harness.instanceng.InstanceNGResourceClient;
import io.harness.utils.RestCallToNGManagerClientUtils;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HarnessServiceInfoFetcherNG extends CacheUtils {
  public final LoadingCache<CacheKey, Optional<HarnessServiceInfoNG>> getHarnessServiceInfoCacheNG;
  @Autowired private InstanceNGResourceClient instanceNGResourceClient;

  @Value
  private static class CacheKey {
    private String accountId;
    private String namespace;
    private String podName;
  }

  @Inject
  public HarnessServiceInfoFetcherNG() {
    this.getHarnessServiceInfoCacheNG =
        Caffeine.newBuilder()
            .recordStats()
            .expireAfterAccess(24, TimeUnit.HOURS)
            .maximumSize(1_000)
            .build(key -> this.getHarnessServiceInfoNG(key.accountId, key.namespace, key.podName));
  }

  public Optional<HarnessServiceInfoNG> fetchHarnessServiceInfoNG(
      String accountId, String namespace, String podName, Map<String, String> labelsMap) {
    try {
      if (labelsMap.containsKey(RELEASE_NAME)) {
        log.debug(
            "Fetching from cache. accountId: {}, podInfo.getPodName(): {}, podInfo.getNamespace(): {}, labels: {}",
            accountId, podName, namespace, labelsMap);
        return getHarnessServiceInfoCacheNG.get(new CacheKey(accountId, namespace, podName));
      }
      return Optional.empty();
    } catch (Exception ex) {
      log.error("Error while fetching data {}", ex);
      return Optional.empty();
    }
  }

  public Optional<HarnessServiceInfoNG> getHarnessServiceInfoNG(String accountId, String namespace, String podName) {
    Optional<HarnessServiceInfoNG> harnessServiceInfoNG;
    log.info("Building cache. accountId: {}, podInfo.getPodName(): {}, podInfo.getNamespace(): {}", accountId, podName,
        namespace);
    harnessServiceInfoNG = RestCallToNGManagerClientUtils.execute(
        instanceNGResourceClient.getInstanceNGData(accountId, podName, namespace));
    return harnessServiceInfoNG;
  }
}
