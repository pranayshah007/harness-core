/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.preflight.connector;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.preflight.PreFlightStatus;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class ConnectorPreflightHandlerTest extends CategoryTest {
  Map<String, Object> fqnToObjectMapMergedYaml = new HashMap<>();

  @InjectMocks ConnectorPreflightHandler connectorPreflightHandler;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = getClass().getClassLoader();
    String filename = "failure-strategy.yaml";
    String yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);

    Map<FQN, Object> fqnObjectMap =
        FQNMapGenerator.generateFQNMap(YamlUtils.readTree(yaml).getNode().getCurrJsonNode());
    fqnObjectMap.keySet().forEach(fqn -> fqnToObjectMapMergedYaml.put(fqn.getExpressionFqn(), fqnObjectMap.get(fqn)));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testNonExistentConnectorsInPipeline() {
    Map<String, String> connectorIdentifierToFqn = new HashMap<>();
    connectorIdentifierToFqn.put(
        "my_git_connector", "pipeline.stages.qaStage.serviceConfig.manifests.baseValues.my_git_connector");

    List<ConnectorResponseDTO> connectorResponses = new ArrayList<>();
    List<ConnectorCheckResponse> connectorCheckResponse = connectorPreflightHandler.getConnectorCheckResponse(
        fqnToObjectMapMergedYaml, connectorResponses, connectorIdentifierToFqn);
    assertThat(connectorCheckResponse).isNotEmpty();
    ConnectorCheckResponse response = connectorCheckResponse.get(0);
    assertThat(response.getStatus()).isEqualTo(PreFlightStatus.FAILURE);
    assertThat(response.getConnectorIdentifier()).isEqualTo("my_git_connector");
    assertThat(response.getFqn())
        .isEqualTo("pipeline.stages.qaStage.serviceConfig.manifests.baseValues.my_git_connector");
    assertThat(response.getStageIdentifier()).isEqualTo("qaStage");
    assertThat(response.getStageName()).isEqualTo("qa stage");
    assertThat(response.getErrorInfo()).isNotNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testInvalidConnectorsInPipeline() {
    Map<String, String> connectorIdentifierToFqn = new HashMap<>();
    connectorIdentifierToFqn.put(
        "my_git_connector", "pipeline.stages.qaStage.serviceConfig.manifests.baseValues.my_git_connector");

    List<ConnectorResponseDTO> connectorResponses =
        Collections.singletonList(ConnectorResponseDTO.builder()
                                      .connector(ConnectorInfoDTO.builder().identifier("my_git_connector").build())
                                      .entityValidityDetails(EntityValidityDetails.builder().valid(false).build())
                                      .build());
    List<ConnectorCheckResponse> connectorCheckResponse = connectorPreflightHandler.getConnectorCheckResponse(
        fqnToObjectMapMergedYaml, connectorResponses, connectorIdentifierToFqn);
    assertThat(connectorCheckResponse).isNotEmpty();
    ConnectorCheckResponse response = connectorCheckResponse.get(0);
    assertThat(response.getStatus()).isEqualTo(PreFlightStatus.FAILURE);
    assertThat(response.getConnectorIdentifier()).isEqualTo("my_git_connector");
    assertThat(response.getFqn())
        .isEqualTo("pipeline.stages.qaStage.serviceConfig.manifests.baseValues.my_git_connector");
    assertThat(response.getStageIdentifier()).isEqualTo("qaStage");
    assertThat(response.getStageName()).isEqualTo("qa stage");
    assertThat(response.getErrorInfo()).isNotNull();
  }
}
