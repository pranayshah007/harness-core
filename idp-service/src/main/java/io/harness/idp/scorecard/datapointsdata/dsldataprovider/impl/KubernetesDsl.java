/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl;

import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.MESSAGE_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DslDataProvider;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.spec.server.idp.v1.model.ClusterConfig;
import io.harness.spec.server.idp.v1.model.KubernetesConfig;

import com.google.inject.Inject;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.xerces.util.URI;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class KubernetesDsl implements DslDataProvider {
  DslClientFactory dslClientFactory;
  public static final Map<String, String> WORKLOAD_API_PATHS =
      Map.of("daemonset", "apis/apps/v1/daemonsets", "deployment", "apis/apps/v1/deployments", "statefulset",
          "apis/apps/v1/statefulsets", "job", "apis/batch/v1/jobs", "cronjob", "apis/batch/v1/cronjobs");

  @Override
  public Map<String, Object> getDslData(String accountIdentifier, Object config) {
    Map<String, Object> returnData = new HashMap<>();
    if (!(config instanceof KubernetesConfig)) {
      return returnData;
    }
    KubernetesConfig kubernetesConfig = (KubernetesConfig) config;
    String labelSelector = kubernetesConfig.getLabelSelector();
    List<ClusterConfig> clusters = kubernetesConfig.getClusters();

    for (ClusterConfig cluster : clusters) {
      URI uri;
      try {
        uri = new URI(cluster.getUrl());
      } catch (URI.MalformedURIException e) {
        log.warn("Url is malformed: {}", cluster.getUrl(), e);
        continue;
      }
      List<Object> items = new ArrayList<>();
      DslClient client = dslClientFactory.getClient(accountIdentifier, uri.getHost());
      for (Map.Entry<String, String> entry : WORKLOAD_API_PATHS.entrySet()) {
        try {
          ApiRequestDetails requestDetails = getApiRequestDetails(
              cluster.getUrl(), entry.getValue(), labelSelector, getAuthHeaders(cluster), entry.getKey());
          Response response = client.call(accountIdentifier, requestDetails);
          if (response.getStatus() == 500) {
            returnData.put(ERROR_MESSAGE_KEY, ((ResponseMessage) response.getEntity()).getMessage());
          } else if (response.getStatus() == 200) {
            Map<String, Object> convertedResponse =
                GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class);
            if (!((List<Object>) convertedResponse.get("items")).isEmpty()) {
              items.addAll((List<Object>) convertedResponse.get("items"));
            }
          } else {
            returnData.put(ERROR_MESSAGE_KEY,
                GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class).get(MESSAGE_KEY));
          }
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
          log.error("Could not make a call to {}", client);
          returnData.put(ERROR_MESSAGE_KEY, e.getMessage());
        }
      }
      returnData.put(cluster.getName(), items);
    }
    return returnData;
  }

  private Map<String, String> getAuthHeaders(ClusterConfig cluster) {
    return Map.of("Authorization", "Bearer " + cluster.getToken());
  }

  private ApiRequestDetails getApiRequestDetails(
      String baseUrl, String path, String labelSelector, Map<String, String> authHeaders, String workloadType) {
    log.info("Preparing request for {}", workloadType);
    String url = String.format("%s/%s?labelSelector=%s", baseUrl, path, labelSelector);
    return ApiRequestDetails.builder().method("GET").headers(authHeaders).url(url).build();
  }
}
