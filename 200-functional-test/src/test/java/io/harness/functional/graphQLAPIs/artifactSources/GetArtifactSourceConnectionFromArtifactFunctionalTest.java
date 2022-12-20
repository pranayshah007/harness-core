/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.graphQLAPIs.artifactSources;

import static io.harness.rule.OwnerRule.POOJA;

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
import software.wings.beans.artifact.ArtifactStream;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.ArtifactStreamService;

import com.google.inject.Inject;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GetArtifactSourceConnectionFromArtifactFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private ArtifactStreamService artifactStreamService;

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
  @Owner(developers = POOJA, intermittent = true)
  @Category(FunctionalTests.class)
  public void shouldGetArtifactByIdWithArtifactSources() {
    Artifact artifact = getArtifact(service, service.getAppId());
    String artifactId = artifact.getUuid();

    String query = getGraphQLQueryToFetchArtifact(artifactId);
    Map<Object, Object> response = GraphQLRestUtils.executeGraphQLQuery(bearerToken, service.getAccountId(), query);

    assertThat(response).isNotEmpty();
    assertThat(response.get("artifact")).isNotNull();
    Map<String, Object> executionData = (Map<String, Object>) response.get("artifact");
    assertThat(executionData.get("buildNo")).isEqualTo(artifact.getBuildNo());
    assertThat(executionData.get("id")).isEqualTo(artifact.getUuid());
    assertThat(executionData.get("artifactSource")).isNotNull();
    Map<String, Object> artifactSource = (Map<String, Object>) executionData.get("artifactSource");

    ArtifactStream artifactStream = artifactStreamService.get(artifact.getArtifactStreamId());
    assertThat(artifactSource.get("name")).isEqualTo(artifactStream.getName());
    assertThat(artifactSource.get("id")).isEqualTo(artifact.getArtifactStreamId());
  }

  private String getGraphQLQueryToFetchArtifact(String artifactId) {
    return $GQL(/*
query{
artifact(artifactId: "%s"){
buildNo
id
artifactSource {
name
id
__typename
}
}
}*/ artifactId);
  }

  private Artifact getArtifact(Service service, String appId) {
    return ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, appId, service.getArtifactStreamIds().get(0), 0);
  }
}
