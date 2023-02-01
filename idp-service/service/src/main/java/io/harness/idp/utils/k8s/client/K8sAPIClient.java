/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.utils.k8s.client;

import io.harness.idp.utils.k8s.exception.ClusterCredentialsNotFoundException;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesConfig.KubernetesConfigBuilder;

import com.google.inject.Inject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class K8sAPIClient implements K8sClient {
  @Inject private KubernetesHelperService kubernetesHelperService;

  @Override
  public void updateSecret(String namespace, String secretName, Map<String, byte[]> data) throws ApiException {
    KubernetesConfig kubernetesConfig = getKubernetesConfig();
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    V1Secret secret;
    try {
      secret = coreV1Api.readNamespacedSecret(secretName, namespace, null);
    } catch (ApiException e) {
      log.error("Secret {} not found in namespace {}", secretName, namespace, e);
      throw e;
    }
    Map<String, byte[]> secretData = secret.getData();
    secretData = secretData == null ? new HashMap<>() : secretData;
    secretData.putAll(data);
    secret.setData(secretData);
    try {
      coreV1Api.replaceNamespacedSecret(secretName, namespace, secret, null, null, null, null);
    } catch (ApiException e) {
      log.error("Secret {} cannot be updated in namespace {}", secretName, namespace, e);
      throw e;
    }
  }

  private KubernetesConfig getKubernetesConfig() {
    String masterURL = System.getenv("MASTER_URL");
    String token = System.getenv("TOKEN");
    if (StringUtils.isBlank(masterURL)) {
      throw new ClusterCredentialsNotFoundException("Master URL not found");
    }
    if (StringUtils.isBlank(token)) {
      throw new ClusterCredentialsNotFoundException("Service Account Token not found");
    }
    KubernetesConfigBuilder builder = KubernetesConfig.builder();
    builder.masterUrl(masterURL);
    builder.serviceAccountTokenSupplier(() -> token);

    String caCert = System.getenv("CA_CRT");
    if (StringUtils.isNotBlank(caCert)) {
      builder.clientCert(caCert.toCharArray());
    }
    return builder.build();
  }
}
