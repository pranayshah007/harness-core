/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.support;

import static io.harness.batch.processing.writer.constants.K8sCCMConstants.HELM_RELEASE_NAME;
import static io.harness.batch.processing.writer.constants.K8sCCMConstants.RELEASE_NAME;

import io.harness.batch.processing.tasklet.util.CacheUtils;
import io.harness.ccm.commons.beans.HarnessServiceInfo;

import software.wings.api.DeploymentSummary;
import software.wings.api.DeploymentSummary.DeploymentSummaryBuilder;
import software.wings.api.K8sDeploymentInfo;
import software.wings.beans.container.Label;
import software.wings.beans.infrastructure.instance.key.deployment.ContainerDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.K8sDeploymentKey;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Maps k8s objects to harness services using labels.
 */
@Slf4j
@Component
public class K8sLabelServiceInfoFetcher extends CacheUtils {
  private final CloudToHarnessMappingService cloudToHarnessMappingService;

  @Value
  private static class CacheKey {
    private String accountId;
    private String releaseKey;
    private String releaseValue;
  }

  private final LoadingCache<CacheKey, Optional<HarnessServiceInfo>> harnessServiceInfoCache =
      Caffeine.newBuilder()
          .recordStats()
          .expireAfterAccess(1, TimeUnit.DAYS)
          .maximumSize(1_000)
          .build(key -> this.fetchHarnessServiceInfo(key.accountId, key.releaseKey, key.releaseValue));

  @Inject
  public K8sLabelServiceInfoFetcher(CloudToHarnessMappingService cloudToHarnessMappingService) {
    this.cloudToHarnessMappingService = cloudToHarnessMappingService;
  }

  public Optional<HarnessServiceInfo> fetchHarnessServiceInfoFromCache(
      String accountId, Map<String, String> labelsMap) {
    String releaseKey = "";
    String releaseValue = "";
    if (labelsMap.containsKey(RELEASE_NAME)) {
      releaseKey = RELEASE_NAME;
      releaseValue = labelsMap.get(RELEASE_NAME);
    } else if (labelsMap.containsKey(HELM_RELEASE_NAME)) {
      releaseKey = HELM_RELEASE_NAME;
      releaseValue = labelsMap.get(HELM_RELEASE_NAME);
    }

    return harnessServiceInfoCache.get(new CacheKey(accountId, releaseKey, releaseValue));
  }

  private Optional<HarnessServiceInfo> fetchHarnessServiceInfo(
      String accountId, String releaseKey, String releaseValue) {
    DeploymentSummaryBuilder deploymentSummaryBuilder = DeploymentSummary.builder().accountId(accountId);

    if (RELEASE_NAME.equals(releaseKey)) {
      K8sDeploymentKey k8sDeploymentKey = K8sDeploymentKey.builder().releaseName(releaseValue).build();
      K8sDeploymentInfo k8sDeploymentInfo = K8sDeploymentInfo.builder().releaseName(releaseValue).build();

      deploymentSummaryBuilder.k8sDeploymentKey(k8sDeploymentKey).deploymentInfo(k8sDeploymentInfo);
      return cloudToHarnessMappingService.getHarnessServiceInfo(deploymentSummaryBuilder.build());
    } else if (HELM_RELEASE_NAME.equals(releaseKey)) {
      Label label = Label.Builder.aLabel().withName(HELM_RELEASE_NAME).withValue(releaseValue).build();
      ContainerDeploymentKey containerDeploymentKey =
          ContainerDeploymentKey.builder().labels(Arrays.asList(label)).build();

      deploymentSummaryBuilder.containerDeploymentKey(containerDeploymentKey);
      return cloudToHarnessMappingService.getHarnessServiceInfo(deploymentSummaryBuilder.build());
    }

    return Optional.empty();
  }
}
