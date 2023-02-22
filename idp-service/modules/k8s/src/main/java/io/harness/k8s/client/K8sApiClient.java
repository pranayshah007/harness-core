/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.client;

import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.exception.ClusterCredentialsNotFoundException;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesConfig.KubernetesConfigBuilder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Secret;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
public class K8sApiClient implements K8sClient {
  private static final String MASTER_URL = "MASTER_URL";
  private static final String TOKEN = "TOKEN";
  private static final String CA_CRT = "CA_CRT";
  @Inject private KubernetesHelperService kubernetesHelperService;

  @Override
  public boolean updateSecretData(String namespace, String secretName, Map<String, byte[]> data, boolean replace)
      throws Exception {
    KubernetesConfig kubernetesConfig = getKubernetesConfig(namespace);
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    V1Secret secret = getSecret(coreV1Api, namespace, secretName);
    Map<String, byte[]> secretData = secret.getData();
    secretData = secretData == null ? new HashMap<>() : secretData;
    if (replace) {
      secretData.clear();
    }
    secretData.putAll(data);
    secret.setData(secretData);
    return replaceSecret(coreV1Api, secret);
  }

  @Override
  public boolean updateConfigMapData(String namespace, String configMapName, Map<String, String> data, boolean replace)
      throws Exception {
    KubernetesConfig kubernetesConfig = getKubernetesConfig(namespace);
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    V1ConfigMap configMap = getConfigMap(coreV1Api, namespace, configMapName);
    Map<String, String> configMapData = configMap.getData();
    configMapData = configMapData == null ? new HashMap<>() : configMapData;
    if (replace) {
      configMapData.clear();
    }
    configMapData.putAll(data);
    return replaceConfigMap(coreV1Api, configMap);
  }

  @Override
  public void removeSecretData(String namespace, String secretName, List<String> envNames) throws Exception {
    KubernetesConfig kubernetesConfig = getKubernetesConfig(namespace);
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    V1Secret secret = getSecret(coreV1Api, namespace, secretName);
    Map<String, byte[]> secretData = secret.getData();
    if (secretData != null) {
      envNames.forEach(secretData::remove);
    }
    secret.setData(secretData);
    replaceSecret(coreV1Api, secret);
    log.info(
        "Successfully removed [{}] environment secrets from [{}/Secret/{}]", envNames.size(), namespace, secretName);
  }

  private V1Secret getSecret(CoreV1Api coreV1Api, String namespace, String secretName) throws Exception {
    try {
      return coreV1Api.readNamespacedSecret(secretName, namespace, null);
    } catch (ApiException e) {
      ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
      String errorMessage = String.format("Failed to update secret [%s] in namespace [%s] ", secretName, namespace)
          + ExceptionUtils.getMessage(ex);
      log.error(errorMessage, ex);
      throw e;
    }
  }

  private boolean replaceSecret(CoreV1Api coreV1Api, V1Secret secret) throws ApiException {
    String secretName = Objects.requireNonNull(secret.getMetadata()).getName();
    String namespace = Objects.requireNonNull(secret.getMetadata()).getNamespace();
    try {
      coreV1Api.replaceNamespacedSecret(secretName, namespace, secret, null, null, null, null);
    } catch (ApiException e) {
      ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
      String errorMessage = String.format("Failed to update secret [%s] in namespace [%s] ", secretName, namespace)
          + ExceptionUtils.getMessage(ex);
      log.error(errorMessage, ex);
      throw e;
    }
    return true;
  }

  private V1ConfigMap getConfigMap(CoreV1Api coreV1Api, String namespace, String configMapName) throws ApiException {
    try {
      return coreV1Api.readNamespacedConfigMap(configMapName, namespace, null);
    } catch (ApiException e) {
      log.error("Error fetching config map {} in namespace {}", configMapName, namespace, e);
      throw e;
    }
  }

  private boolean replaceConfigMap(CoreV1Api coreV1Api, V1ConfigMap configMap) throws ApiException {
    String configMapName = Objects.requireNonNull(configMap.getMetadata()).getName();
    String namespace = Objects.requireNonNull(configMap.getMetadata()).getNamespace();
    try {
      coreV1Api.replaceNamespacedConfigMap(configMapName, namespace, configMap, null, null, null, null);
    } catch (ApiException e) {
      log.error("Error updating config map {} in namespace {}", configMapName, namespace, e);
      throw e;
    }
    return true;
  }

  public static KubernetesConfig getKubernetesConfig(String namespace) {
    if (StringUtils.isBlank(namespace)) {
      throw new InvalidRequestException("Empty namespace");
    }
    String masterURL = System.getenv(MASTER_URL);
    String token = System.getenv(TOKEN);
    if (StringUtils.isBlank(masterURL)) {
      throw new ClusterCredentialsNotFoundException("Master URL not found");
    }
    if (StringUtils.isBlank(token)) {
      throw new ClusterCredentialsNotFoundException("Service Account Token not found");
    }
    KubernetesConfigBuilder builder = KubernetesConfig.builder();
    builder.masterUrl(masterURL);
    builder.serviceAccountTokenSupplier(() -> token);

    String caCert = System.getenv(CA_CRT);
    if (StringUtils.isNotBlank(caCert)) {
      builder.clientCert(caCert.toCharArray());
    }
    return builder.build();
  }
}
