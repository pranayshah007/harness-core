/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.utils;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sTaskCleanupDTO;
import io.harness.delegate.task.k8s.RancherK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.rancher.RancherHelper;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.rancher.RancherConnectionHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class K8sTaskCleaner {
  @Inject private RancherConnectionHelperService rancherConnectionHelperService;

  public void cleanup(K8sTaskCleanupDTO cleanupDTO) {
    try {
      cleanUpInternal(cleanupDTO);
    } catch (Exception e) {
      log.debug("Cleanup failed for K8s task.");
      // Ignore cleanup errors
    }
  }

  private void cleanUpInternal(K8sTaskCleanupDTO cleanupDTO) {
    K8sInfraDelegateConfig k8sInfraDelegateConfig = cleanupDTO.getInfraDelegateConfig();
    if (k8sInfraDelegateConfig instanceof RancherK8sInfraDelegateConfig) {
      performRancherTokenCleanup(
          cleanupDTO.getGeneratedKubeConfig(), (RancherK8sInfraDelegateConfig) k8sInfraDelegateConfig);
    }
  }

  private void performRancherTokenCleanup(
      KubernetesConfig kubernetesConfig, RancherK8sInfraDelegateConfig rancherK8sInfraDelegateConfig) {
    String rancherUrl = RancherHelper.getRancherUrl(rancherK8sInfraDelegateConfig);
    String bearerToken = RancherHelper.getRancherBearerToken(rancherK8sInfraDelegateConfig);
    String tokenId = RancherHelper.getKubeConfigTokenName(kubernetesConfig);
    rancherConnectionHelperService.deleteKubeconfigToken(rancherUrl, bearerToken, tokenId);
  }
}
