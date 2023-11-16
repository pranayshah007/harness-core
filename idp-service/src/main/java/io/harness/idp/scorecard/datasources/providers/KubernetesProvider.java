/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.CommonUtils.getHarnessHostForEnv;
import static io.harness.idp.common.Constants.HARNESS_ACCOUNT;
import static io.harness.idp.common.Constants.KUBERNETES_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.BODY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.common.JacksonUtils;
import io.harness.idp.proxy.services.IdpAuthInterceptor;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.ClusterConfig;
import io.harness.spec.server.idp.v1.model.DataPointInputValues;
import io.harness.spec.server.idp.v1.model.DataSourceLocationInfo;
import io.harness.spec.server.idp.v1.model.KubernetesConfig;
import io.harness.spec.server.idp.v1.model.KubernetesRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class KubernetesProvider extends HttpDataSourceProvider {
  private static final String KUBERNETES_LABEL_SELECTOR_ANNOTATION = "backstage.io/kubernetes-label-selector";
  final ConfigReader configReader;
  final IdpAuthInterceptor idpAuthInterceptor;
  final String env;

  protected KubernetesProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      ConfigReader configReader, IdpAuthInterceptor idpAuthInterceptor, String env,
      DataSourceRepository dataSourceRepository) {
    super(KUBERNETES_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
    this.configReader = configReader;
    this.idpAuthInterceptor = idpAuthInterceptor;
    this.env = env;
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, BackstageCatalogEntity entity, List<DataFetchDTO> dataFetchDTOS, String configs) {
    Map<String, String> replaceableHeaders = new HashMap<>(this.getAuthHeaders(accountIdentifier, null));
    replaceableHeaders.put(HARNESS_ACCOUNT, accountIdentifier);
    List<ClusterConfig> clustersConfigList = getClustersConfig(accountIdentifier, configs);
    String labelSelector = entity.getMetadata().getAnnotations().get(KUBERNETES_LABEL_SELECTOR_ANNOTATION);
    Map<String, String> possibleReplaceableRequestBodyPairs =
        prepareRequestBodyReplaceablePairs(clustersConfigList, labelSelector, dataFetchDTOS);
    return processOut(accountIdentifier, KUBERNETES_IDENTIFIER, entity, replaceableHeaders,
        possibleReplaceableRequestBodyPairs, prepareUrlReplaceablePairs(), dataFetchDTOS);
  }

  @Override
  protected Map<String, String> prepareUrlReplaceablePairs(String... keysValues) {
    String harnessHost = getHarnessHostForEnv(env);
    return Map.of(HOST, harnessHost);
  }

  @Override
  public Map<String, String> getAuthHeaders(String accountIdentifier, String configs) {
    return idpAuthInterceptor.getAuthHeaders();
  }

  private Map<String, String> prepareRequestBodyReplaceablePairs(
      List<ClusterConfig> clustersConfig, String labelSelector, List<DataFetchDTO> dataFetchDTOS) {
    List<DataPointInputValues> dataPoints = new ArrayList<>();
    for (DataFetchDTO dataFetchDTO : dataFetchDTOS) {
      DataPointInputValues dataPointInputValues = new DataPointInputValues();
      dataPointInputValues.setDataPointIdentifier(dataFetchDTO.getDataPoint().getIdentifier());
      dataPoints.add(dataPointInputValues);
    }
    DataSourceLocationInfo dataSourceLocationInfo = new DataSourceLocationInfo();
    dataSourceLocationInfo.setDataPoints(dataPoints);
    KubernetesConfig kubernetesConfig = new KubernetesConfig();
    kubernetesConfig.clusters(clustersConfig);
    kubernetesConfig.dataSourceLocation(dataSourceLocationInfo);
    kubernetesConfig.labelSelector(URLEncoder.encode(labelSelector, StandardCharsets.UTF_8));
    KubernetesRequest kubernetesRequest = new KubernetesRequest();
    kubernetesRequest.setRequest(kubernetesConfig);

    Map<String, String> possibleReplaceableRequestBodyPairs = new HashMap<>();
    possibleReplaceableRequestBodyPairs.put(BODY, JacksonUtils.write(kubernetesRequest));
    possibleReplaceableRequestBodyPairs.putAll(prepareUrlReplaceablePairs());
    return possibleReplaceableRequestBodyPairs;
  }

  private List<ClusterConfig> getClustersConfig(String accountIdentifier, String configs) {
    String clustersExpression = "appConfig.kubernetes.clusterLocatorMethods[0].clusters";
    List<Map<String, Object>> clusters =
        (List<Map<String, Object>>) configReader.getConfigValues(accountIdentifier, configs, clustersExpression);
    List<ClusterConfig> clustersConfig = new ArrayList<>();
    for (int i = 0; i < clusters.size(); ++i) {
      ClusterConfig clusterConfig = new ClusterConfig();
      String urlExpression = String.format("%s[%s].url", clustersExpression, i);
      String tokenExpression = String.format("%s[%s].serviceAccountToken", clustersExpression, i);
      String nameExpression = String.format("%s[%s].name", clustersExpression, i);
      String url = (String) configReader.getConfigValues(accountIdentifier, configs, urlExpression);
      String token = (String) configReader.getConfigValues(accountIdentifier, configs, tokenExpression);
      String name = (String) configReader.getConfigValues(accountIdentifier, configs, nameExpression);
      clusterConfig.setUrl(url);
      clusterConfig.setName(name);
      clusterConfig.setToken(token);
      clustersConfig.add(clusterConfig);
    }
    return clustersConfig;
  }
}
