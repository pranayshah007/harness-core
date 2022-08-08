/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.rancher;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.model.KubernetesConfig.KubernetesConfigBuilder;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.KeyValuePair;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.http.HttpService;
import io.harness.http.beans.HttpInternalConfig;
import io.harness.http.beans.HttpInternalResponse;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonSubtypeResolver;

import software.wings.beans.RancherConfig;
import software.wings.jersey.JsonViews;
import software.wings.service.intfc.security.EncryptionService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.Cluster;
import io.fabric8.kubernetes.api.model.Config;
import io.fabric8.kubernetes.api.model.NamedCluster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class RancherTaskHelper {
  @Inject private EncryptionService encryptionService;
  @Inject private HttpService httpService;
  private static final ObjectMapper objectMapper;

  static {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new Jdk8Module());
    objectMapper.registerModule(new GuavaModule());
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.setSubtypeResolver(new JsonSubtypeResolver(objectMapper.getSubtypeResolver()));
    objectMapper.setConfig(objectMapper.getSerializationConfig().withView(JsonViews.Public.class));
    objectMapper.disable(FAIL_ON_UNKNOWN_PROPERTIES);
  }

  public RancherClusterDataResponse resolveRancherClusters(
      final RancherConfig rancherConfig, final List<EncryptedDataDetail> encryptedDataDetails) throws IOException {
    encryptionService.decrypt(rancherConfig, encryptedDataDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(rancherConfig, encryptedDataDetails);
    HttpInternalResponse httpResponse = makeRancherApi("GET", "/v3/clusters", rancherConfig);

    return objectMapper.readValue(httpResponse.getHttpResponseBody(), RancherClusterDataResponse.class);
  }

  @NotNull
  private HttpInternalResponse makeRancherApi(final String httpMethod, final String url, RancherConfig rancherConfig)
      throws IOException {
    StringBuilder urlBuffer = new StringBuilder();
    urlBuffer.append(rancherConfig.getRancherUrl()).append(url);

    List<KeyValuePair> headers = new ArrayList<>();
    headers.add(KeyValuePair.builder()
                    .key(HttpHeaders.AUTHORIZATION)
                    .value("Bearer " + new String(rancherConfig.getBearerToken()))
                    .build());

    HttpInternalResponse httpResponse =
        httpService.executeUrl(HttpInternalConfig.builder()
                                   .method(httpMethod)
                                   .headers(headers)
                                   .socketTimeoutMillis(10000)
                                   .url(urlBuffer.toString())
                                   // TODO: Check if useProxy field need to be added in Rancher cloud provider
                                   .useProxy(false)
                                   .isCertValidationRequired(rancherConfig.isCertValidationRequired())
                                   .build());

    if (Objects.isNull(httpResponse) || httpResponse.getHttpResponseCode() < 200
        || httpResponse.getHttpResponseCode() >= 300) {
      throw new InvalidRequestException("Rancher http call failed");
    }

    return httpResponse;
  }

  public KubernetesConfig createKubeconfig(final RancherConfig rancherConfig,
      final List<EncryptedDataDetail> encryptedDataDetails, final String clusterName, final String namespace)
      throws IOException {
    encryptionService.decrypt(rancherConfig, encryptedDataDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(rancherConfig, encryptedDataDetails);
    KubernetesConfigBuilder kubernetesConfigBuilder = KubernetesConfig.builder().namespace(namespace);

    RancherClusterDataResponse rancherClusterData = resolveRancherClusters(rancherConfig, encryptedDataDetails);
    RancherClusterDataResponse.ClusterData clusterData = rancherClusterData.getData()
                                                             .stream()
                                                             .filter(cluster -> cluster.getName().equals(clusterName))
                                                             .findFirst()
                                                             .orElse(null);

    if (Objects.isNull(clusterData)) {
      throw new IllegalArgumentException(
          "Unable to find cluster with display name: " + clusterName + " in list of clusters fetched from Rancher");
    }

    String kubeConfig = generateKubeConfigFromRancher(rancherConfig, clusterData.getId());
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    Config config = yamlMapper.readValue(kubeConfig, Config.class);

    String server = null;
    char[] caCert = null;
    String token = null;

    NamedCluster k8sCluster =
        config != null && CollectionUtils.isNotEmpty(config.getClusters()) ? config.getClusters().get(0) : null;

    if (k8sCluster != null) {
      Cluster cluster = k8sCluster.getCluster();

      if (cluster != null) {
        server = cluster.getServer();

        if (cluster.getCertificateAuthorityData() != null) {
          caCert = cluster.getCertificateAuthorityData().toCharArray();
        }
      }
    }

    if (config != null && CollectionUtils.isNotEmpty(config.getUsers()) && config.getUsers().get(0) != null
        && config.getUsers().get(0).getUser() != null) {
      token = config.getUsers().get(0).getUser().getToken();
    }

    String finalToken = token;
    return kubernetesConfigBuilder.masterUrl(server)
        .caCert(caCert)
        .serviceAccountTokenSupplier(() -> finalToken)
        .build();
  }

  @VisibleForTesting
  String generateKubeConfigFromRancher(RancherConfig rancherConfig, String clusterId) throws IOException {
    String url = String.format("/v3/clusters/%s?action=generateKubeconfig", clusterId);
    HttpInternalResponse response = makeRancherApi("POST", url, rancherConfig);

    RancherGenerateKubeconfigResponse kubeconfigResponse =
        objectMapper.readValue(response.getHttpResponseBody(), RancherGenerateKubeconfigResponse.class);

    return kubeconfigResponse.getConfig();
  }
}
