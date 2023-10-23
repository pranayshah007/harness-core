/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.preflight;

import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.preflight.connector.ConnectorCheckResponse;
import io.harness.pms.preflight.inputset.PipelineInputResponse;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PreflightCommonUtilsTest extends CategoryTest {
  Map<String, Object> fqnToObjectMapMergedYaml = new HashMap<>();
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
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetStageName() {
    assertThat(PreflightCommonUtils.getStageName(fqnToObjectMapMergedYaml, null)).isNull();
    assertThat(PreflightCommonUtils.getStageName(fqnToObjectMapMergedYaml, "qaStage")).isEqualTo("qa stage");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetNotFoundErrorInfo() {
    PreFlightEntityErrorInfo preFlightEntityErrorInfo = PreflightCommonUtils.getNotFoundErrorInfo();
    assertEquals("Connector not found or does not exist", preFlightEntityErrorInfo.summary);
    assertEquals(1, preFlightEntityErrorInfo.causes.size());
    assertEquals("Connector not found or does not exist", preFlightEntityErrorInfo.causes.get(0).cause);
    assertEquals(1, preFlightEntityErrorInfo.resolution.size());
    assertEquals("Create this ConnectorDisconnectHandler", preFlightEntityErrorInfo.resolution.get(0).resolution);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetInvalidConnectorInfo() {
    PreFlightEntityErrorInfo preFlightEntityErrorInfo = PreflightCommonUtils.getInvalidConnectorInfo();
    assertEquals("Connector not valid", preFlightEntityErrorInfo.summary);
    assertEquals(1, preFlightEntityErrorInfo.causes.size());
    assertEquals("The ConnectorDisconnectHandler YAML provided on git is invalid", preFlightEntityErrorInfo.causes.get(0).cause);
    assertEquals(1, preFlightEntityErrorInfo.resolution.size());
    assertEquals("Fix the ConnectorDisconnectHandler yaml", preFlightEntityErrorInfo.resolution.get(0).resolution);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetInternalIssueErrorInfo() {
    PreFlightEntityErrorInfo preFlightEntityErrorInfo =
        PreflightCommonUtils.getInternalIssueErrorInfo(new Exception("Dummy message"));
    assertEquals("Dummy message", preFlightEntityErrorInfo.summary);
    assertEquals(1, preFlightEntityErrorInfo.causes.size());
    assertEquals("Internal Server Error", preFlightEntityErrorInfo.causes.get(0).cause);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetPreFlightStatus() {
    assertEquals(PreFlightStatus.FAILURE, PreflightCommonUtils.getPreFlightStatus(ConnectivityStatus.FAILURE));
    assertEquals(PreFlightStatus.SUCCESS, PreflightCommonUtils.getPreFlightStatus(ConnectivityStatus.SUCCESS));
    assertEquals(PreFlightStatus.IN_PROGRESS, PreflightCommonUtils.getPreFlightStatus(ConnectivityStatus.PARTIAL));
    assertEquals(PreFlightStatus.IN_PROGRESS, PreflightCommonUtils.getPreFlightStatus(ConnectivityStatus.UNKNOWN));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetPipelineInputStatus() {
    List<PipelineInputResponse> pipelineInputResponse = new ArrayList<>();
    pipelineInputResponse.add(PipelineInputResponse.builder().success(true).build());
    assertEquals(PreFlightStatus.SUCCESS, PreflightCommonUtils.getPipelineInputStatus(pipelineInputResponse));
    pipelineInputResponse.add(PipelineInputResponse.builder().success(false).build());
    assertEquals(PreFlightStatus.FAILURE, PreflightCommonUtils.getPipelineInputStatus(pipelineInputResponse));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetConnectorCheckStatus() {
    List<ConnectorCheckResponse> connectorCheckResponses = new ArrayList<>();
    connectorCheckResponses.add(ConnectorCheckResponse.builder().status(PreFlightStatus.SUCCESS).build());
    assertEquals(PreFlightStatus.SUCCESS, PreflightCommonUtils.getConnectorCheckStatus(connectorCheckResponses));
    connectorCheckResponses.add(ConnectorCheckResponse.builder().status(PreFlightStatus.FAILURE).build());
    assertEquals(PreFlightStatus.FAILURE, PreflightCommonUtils.getConnectorCheckStatus(connectorCheckResponses));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetOverallStatus() {
    List<ConnectorCheckResponse> connectorCheckResponses = new ArrayList<>();
    List<PipelineInputResponse> pipelineInputResponse = new ArrayList<>();
    pipelineInputResponse.add(PipelineInputResponse.builder().success(true).build());
    connectorCheckResponses.add(ConnectorCheckResponse.builder().status(PreFlightStatus.SUCCESS).build());
    assertEquals(
        PreFlightStatus.SUCCESS, PreflightCommonUtils.getOverallStatus(connectorCheckResponses, pipelineInputResponse));
    pipelineInputResponse.add(PipelineInputResponse.builder().success(false).build());
    assertEquals(
        PreFlightStatus.FAILURE, PreflightCommonUtils.getOverallStatus(connectorCheckResponses, pipelineInputResponse));
    connectorCheckResponses.add(ConnectorCheckResponse.builder().status(PreFlightStatus.FAILURE).build());
    assertEquals(
        PreFlightStatus.FAILURE, PreflightCommonUtils.getOverallStatus(connectorCheckResponses, pipelineInputResponse));
  }
}
