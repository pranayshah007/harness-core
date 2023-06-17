/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.InputSetValidatorType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceUseFromStageV2;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.validation.InputSetValidator;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import java.io.IOException;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServiceAllInOnePlanCreatorUtilsTest extends CategoryTest {
  private AutoCloseable mocks;

  @Mock private KryoSerializer kryoSerializer;

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {0});
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void addServiceNode() throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/pipeline.yaml");
    YamlField pipeline = new YamlField("pipeline", YamlNode.fromYamlPath(pipelineYaml, ""));
    Map<String, PlanCreationResponse> planCreationResponse = ServiceAllInOnePlanCreatorUtils.addServiceNode(pipeline,
        kryoSerializer, ServiceYamlV2.builder().serviceRef(ParameterField.createValueField("my_service")).build(),
        EnvironmentYamlV2.builder().build(), "serviceNodeId", "mextNodeId", ServiceDefinitionType.ECS, null);
    assertThat(planCreationResponse).hasSize(5);
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void addServiceNodeContainBothUseFromStageAndService() throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/pipeline.yaml");
    YamlField pipeline = new YamlField("pipeline", YamlNode.fromYamlPath(pipelineYaml, ""));
    assertThatThrownBy(
        ()
            -> ServiceAllInOnePlanCreatorUtils.addServiceNode(pipeline, kryoSerializer,
                ServiceYamlV2.builder()
                    .serviceRef(ParameterField.createValueField("my_service"))
                    .useFromStage(ServiceUseFromStageV2.builder().stage("stage1").build())
                    .build(),
                EnvironmentYamlV2.builder().build(), "serviceNodeId", "mextNodeId", ServiceDefinitionType.ECS, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Only one of serviceRef and useFromStage fields are allowed.");
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void addServiceNodeContainServiceRefAsExpression() throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/pipeline.yaml");
    YamlField pipeline = new YamlField("pipeline", YamlNode.fromYamlPath(pipelineYaml, ""));
    InputSetValidator validator = new InputSetValidator(InputSetValidatorType.REGEX, "");

    Map<String, PlanCreationResponse> planCreationResponse =
        ServiceAllInOnePlanCreatorUtils.addServiceNode(pipeline, kryoSerializer,
            ServiceYamlV2.builder()
                .serviceRef(ParameterField.createExpressionField(true, "<+pipeline.name>", validator, true))
                .build(),
            EnvironmentYamlV2.builder().build(), "serviceNodeId", "nextNodeId", ServiceDefinitionType.ECS, null);
    assertThat(planCreationResponse).hasSize(5);
  }
  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void addServiceNodeNoServiceNodeChild() throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/pipeline.yaml");
    YamlField pipeline = new YamlField("pipeline", YamlNode.fromYamlPath(pipelineYaml, ""));
    assertThatThrownBy(
        ()
            -> ServiceAllInOnePlanCreatorUtils.addServiceNode(pipeline, kryoSerializer, ServiceYamlV2.builder().build(),
                EnvironmentYamlV2.builder().build(), "serviceNodeId", "mextNodeId", ServiceDefinitionType.ECS, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("At least one of serviceRef and useFromStage fields is required.");
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void addServiceNodeStageDeploymentTypeMismatch() throws IOException {
    String pipelineYaml = readFileIntoUTF8String(
        "cdng/creator/servicePlanCreator/pipeline-with-stages-of-different-deployment-type.yaml");
    YamlField yamlField = new YamlField("", YamlNode.fromYamlPath(pipelineYaml, ""));
    YamlField specField = new YamlField("spec", getStageNodeAtIndex(yamlField, 1));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> ServiceAllInOnePlanCreatorUtils.addServiceNode(specField, kryoSerializer,
                            ServiceYamlV2.builder()
                                .useFromStage(ServiceUseFromStageV2.builder().stage("stage0").build())
                                .build(),
                            EnvironmentYamlV2.builder().build(), "serviceNodeId", "nextNodeId",
                            ServiceDefinitionType.ECS, null))
        .withMessage(
            "Deployment type: [Kubernetes] of stage: [stage1] does not match with deployment type: [NativeHelm] of stage: [stage0] from which service propagation is configured");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void addServiceNodeUseFromStageError_0() throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/pipeline.yaml");
    YamlField pipeline = new YamlField("pipeline", YamlNode.fromYamlPath(pipelineYaml, ""));
    YamlField specField = new YamlField("spec", getStageNodeAtIndex(pipeline, 2));
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(()
                        -> ServiceAllInOnePlanCreatorUtils.addServiceNode(specField, kryoSerializer,
                            ServiceYamlV2.builder()
                                .useFromStage(ServiceUseFromStageV2.builder().stage("stage1").build())
                                .build(),
                            EnvironmentYamlV2.builder().build(), "serviceNodeId", "mextNodeId",
                            ServiceDefinitionType.ECS, null))
        .withMessage(
            "Invalid identifier [stage1] given in useFromStage. Cannot reference a stage which also has useFromStage parameter");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void addServiceNodeUseFromStageError_1() throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/pipeline.yaml");
    YamlField yamlField = new YamlField("", YamlNode.fromYamlPath(pipelineYaml, ""));
    YamlField specField = new YamlField("spec", getStageNodeAtIndex(yamlField, 2));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> ServiceAllInOnePlanCreatorUtils.addServiceNode(specField, kryoSerializer,
                            ServiceYamlV2.builder()
                                .useFromStage(ServiceUseFromStageV2.builder().stage("stage0").build())
                                .build(),
                            EnvironmentYamlV2.builder().build(), "serviceNodeId", "mextNodeId",
                            ServiceDefinitionType.ECS, null))
        .withMessage(
            "Propagate from stage is not supported with multi service deployments, hence not possible to propagate service from that stage");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void addServiceNodeUseFromStageError_2() throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/pipeline.yaml");
    YamlField yamlField = new YamlField("", YamlNode.fromYamlPath(pipelineYaml, ""));
    YamlField specField = new YamlField("spec", getStageNodeAtIndex(yamlField, 2));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> ServiceAllInOnePlanCreatorUtils.addServiceNode(specField, kryoSerializer,
                            ServiceYamlV2.builder()
                                .useFromStage(ServiceUseFromStageV2.builder().stage("adhoc").build())
                                .build(),
                            EnvironmentYamlV2.builder().build(), "serviceNodeId", "mextNodeId",
                            ServiceDefinitionType.ECS, null))
        .withMessage(
            "Could not find service in stage [adhoc], hence not possible to propagate service from that stage");
  }

  private static YamlNode getStageNodeAtIndex(YamlField pipeline, int idx) {
    return pipeline.getNode()
        .getField("pipeline")
        .getNode()
        .getField("stages")
        .getNode()
        .asArray()
        .get(idx)
        .getField("stage")
        .getNode()
        .getField(YAMLFieldNameConstants.SPEC)
        .getNode();
  }
}