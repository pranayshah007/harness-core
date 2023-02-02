/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils.k8s.client;

import io.harness.exception.InvalidRequestException;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesConfig.KubernetesConfigBuilder;
import io.harness.utils.k8s.exception.ClusterCredentialsNotFoundException;

import com.google.inject.Inject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class K8sAPIClient implements K8sClient {
  @Inject private KubernetesHelperService kubernetesHelperService;

  @Override
  public void updateSecret(String namespace, String secretName, Map<String, byte[]> data) throws ApiException {
    KubernetesConfig kubernetesConfig = getKubernetesConfig(namespace);
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    V1Secret secret = getSecret(coreV1Api, namespace, secretName);
    Map<String, byte[]> secretData = secret.getData();
    secretData = secretData == null ? new HashMap<>() : secretData;
    secretData.putAll(data);
    secret.setData(secretData);
    replaceSecret(coreV1Api, namespace, secret);
  }

  private V1Secret getSecret(CoreV1Api coreV1Api, String namespace, String secretName) throws ApiException {
    try {
      return coreV1Api.readNamespacedSecret(secretName, namespace, null);
    } catch (ApiException e) {
      log.error("Error fetching Secret {} in namespace {}", secretName, namespace, e);
      throw e;
    }
  }

  private void replaceSecret(CoreV1Api coreV1Api, String namespace, V1Secret secret) throws ApiException {
    String secretName = Objects.requireNonNull(secret.getMetadata()).getName();
    try {
      coreV1Api.replaceNamespacedSecret(secretName, namespace, secret, null, null, null, null);
    } catch (ApiException e) {
      log.error("Error updating secret {} in namespace {}", secretName, namespace, e);
      throw e;
    }
  }

  private KubernetesConfig getKubernetesConfig(String namespace) {
    if (StringUtils.isBlank(namespace)) {
      throw new InvalidRequestException("Empty namespace");
    }
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
