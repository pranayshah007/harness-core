/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.rancher;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.sanitizer.ExceptionMessageSanitizer.sanitizeException;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ngexception.RancherClientRuntimeException;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.rancher.RancherConnectionHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.util.KubeConfig;
import java.io.StringReader;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class RancherKubeConfigGenerator {
  static final String KUBECONFIG_GEN_ERROR_LOG_MESSAGE =
      "Failed to generate kubeconfig from rancher cluster [%s : %s]. ";
  @Inject private RancherConnectionHelperService rancherConnectionHelperService;
  public KubernetesConfig createKubernetesConfig(RancherClusterActionDTO clusterActionDTO) {
    String rancherUrl = clusterActionDTO.getClusterUrl();
    String clusterId = clusterActionDTO.getClusterName();
    try {
      String generatedKubeConfig =
          rancherConnectionHelperService.generateKubeconfig(rancherUrl, clusterActionDTO.getBearerToken(), clusterId);
      ensureKubeConfigIsNotEmpty(generatedKubeConfig, clusterActionDTO);
      return createKubernetesConfig(clusterActionDTO, generatedKubeConfig);
    } catch (RancherClientRuntimeException e) {
      throw e;
    } catch (Exception e) {
      Exception sanitizedException = sanitizeException(e);
      String errorMessage = format(KUBECONFIG_GEN_ERROR_LOG_MESSAGE, rancherUrl, clusterId);
      log.error(errorMessage, sanitizedException);
      throw new RancherClientRuntimeException(errorMessage + ExceptionUtils.getMessage(sanitizedException));
    }
  }

  private KubernetesConfig createKubernetesConfig(
      RancherClusterActionDTO clusterActionDTO, String generatedKubeConfig) {
    KubeConfig kubeConfig = KubeConfig.loadKubeConfig(new StringReader(generatedKubeConfig));
    return createKubernetesConfig(clusterActionDTO, kubeConfig);
  }

  private KubernetesConfig createKubernetesConfig(RancherClusterActionDTO clusterActionDTO, KubeConfig kubeConfig) {
    String bearerToken = extractToken(kubeConfig);
    ensureRancherKubeConfigContainsRequiredFields(kubeConfig.getServer(), bearerToken, clusterActionDTO);
    return KubernetesConfig.builder()
        .masterUrl(kubeConfig.getServer())
        .namespace(clusterActionDTO.getNamespace())
        .serviceAccountTokenSupplier(() -> bearerToken)
        .caCert(isNotEmpty(kubeConfig.getCertificateAuthorityData())
                ? kubeConfig.getCertificateAuthorityData().toCharArray()
                : null)
        .build();
  }

  private void ensureRancherKubeConfigContainsRequiredFields(
      String clusterUrl, String bearerToken, RancherClusterActionDTO clusterActionDTO) {
    if (isEmpty(bearerToken) || isEmpty(clusterUrl)) {
      throwKubeConfigGenerationException(clusterActionDTO.getClusterUrl(), clusterActionDTO.getClusterName());
    }
  }

  @SuppressWarnings("unchecked")
  private String extractToken(KubeConfig kubeConfig) {
    try {
      Map<String, Object> userWrapper = (Map<String, Object>) kubeConfig.getUsers().get(0);
      Map<String, Object> user = (Map<String, Object>) userWrapper.get("user");
      return (String) user.get("token");
    } catch (Exception e) {
      return StringUtils.EMPTY;
    }
  }

  private void ensureKubeConfigIsNotEmpty(String rancherGeneratedKubeConfig, RancherClusterActionDTO clusterActionDTO) {
    if (isEmpty(rancherGeneratedKubeConfig)) {
      throwKubeConfigGenerationException(clusterActionDTO.getClusterUrl(), clusterActionDTO.getClusterName());
    }
  }

  private void throwKubeConfigGenerationException(String clusterUrl, String clusterName) {
    throw new RancherClientRuntimeException(format(KUBECONFIG_GEN_ERROR_LOG_MESSAGE, clusterUrl, clusterName));
  }
}
