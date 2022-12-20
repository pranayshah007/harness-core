/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.graphQLAPIs.artifactSources;

import static io.harness.rule.OwnerRule.MILAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.FeatureName;
import io.harness.category.element.FunctionalTests;
import io.harness.ff.FeatureFlagService;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.rule.Owner;
import io.harness.testframework.restutils.ArtifactRestUtils;
import io.harness.testframework.restutils.GraphQLRestUtils;

import software.wings.beans.Service;
import software.wings.persistence.artifact.Artifact;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GetArtifactConnectionFromArtifactSourceFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private FeatureFlagService featureFlagService;

  final Randomizer.Seed seed = new Randomizer.Seed(0);
  OwnerManager.Owners owners;
  private Service service;
  @Before
  public void setUp() {
    owners = ownerManager.create();
    service = serviceGenerator.ensureK8sTest(seed, owners, "k8s-service");

    if (!featureFlagService.isEnabled(FeatureName.GRAPHQL_DEV, service.getAccountId())) {
      featureFlagService.enableAccount(FeatureName.GRAPHQL_DEV, service.getAccountId());
    }
  }

  @Test
  @Owner(developers = MILAN, intermittent = true)
  @Category(FunctionalTests.class)
  public void shouldGetArtifactConnectionByServiceId() {
    List<Artifact> artifacts = getArtifacts(service, service.getAppId());

    String query = getGraphQLQueryToFetchArtifactConnection(service.getUuid());
    Map<Object, Object> response = GraphQLRestUtils.executeGraphQLQuery(bearerToken, service.getAccountId(), query);

    assertThat(response).isNotEmpty();

    assertThat(response.get("service")).isNotNull();
    Map<String, Object> serviceData = (Map<String, Object>) response.get("service");
    assertThat(serviceData.get("artifactSources")).isNotNull();
    List<Map<String, Object>> artifactSourcesDataList = (List<Map<String, Object>>) serviceData.get("artifactSources");
    assertThat(artifactSourcesDataList.get(0)).isNotNull();
    assertThat(artifactSourcesDataList.get(0).get("artifacts")).isNotNull();
    Map<String, Object> artifactsData = (Map<String, Object>) artifactSourcesDataList.get(0).get("artifacts");
    assertThat(artifactsData.get("nodes")).isNotNull();
    List<Map<String, Object>> nodesDataList = (List<Map<String, Object>>) artifactsData.get("nodes");
    assertThat(nodesDataList).isNotNull();

    List<String> originalArtifactsIds = artifacts.stream().map(Artifact::getUuid).collect(Collectors.toList());
    List<String> graphqlArtifactIds =
        nodesDataList.stream().map(map -> (String) map.get("id")).collect(Collectors.toList());
    assertThat(originalArtifactsIds).containsAll(graphqlArtifactIds);
  }

  private String getGraphQLQueryToFetchArtifactConnection(String serviceId) {
    return $GQL(/*
query{
service(serviceId: "%s") {
artifactSources {
  artifacts(limit: 10, offset: 0) {
    nodes {
      id
    }
  }
}
}
}*/ serviceId);
  }

  private List<Artifact> getArtifacts(Service service, String appId) {
    return ArtifactRestUtils.waitAndFetchArtifactListByArtfactStream(
        bearerToken, appId, service.getArtifactStreamIds().get(0));
  }
}
