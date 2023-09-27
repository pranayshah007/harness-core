/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.customstage;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.SOUMYAJIT;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class CustomStagePlanCreatorTest extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks CustomStagePlanCreator customStagePlanCreator;

  private String SOURCE_PIPELINE_YAML;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    String pipeline_yaml_filename = "customStage.yaml";
    SOURCE_PIPELINE_YAML = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(pipeline_yaml_filename)), StandardCharsets.UTF_8);
  }
  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateSupportedStageTypes() {
    assertThat(customStagePlanCreator.getSupportedStageTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.CUSTOM_STAGE));
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateGetStepType() {
    assertThat(customStagePlanCreator.getStepType(null)).isEqualTo(CustomStageStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateSpecParameters() {
    String childNodeID = "temp";
    CustomStageSpecParams customStageSpecParams = new CustomStageSpecParams(childNodeID);
    assertThat(customStagePlanCreator.getSpecParameters(childNodeID, null, null)).isEqualTo(customStageSpecParams);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateCustomStagePlanFieldClass() {
    assertThat(customStagePlanCreator.getFieldClass()).isInstanceOf(java.lang.Class.class);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateCustomStagePlanForChildrenNodes() throws IOException {
    String yamlWithUuid = YamlUtils.injectUuid(SOURCE_PIPELINE_YAML);
    YamlField fullYamlFieldWithUuiD = YamlUtils.injectUuidInYamlField(yamlWithUuid);

    PlanCreationContext ctx = PlanCreationContext.builder().yaml(yamlWithUuid).build();
    ctx.setCurrentField(fullYamlFieldWithUuiD);

    CustomStageNode customStageNode = new CustomStageNode();
    customStageNode.setUuid("tempid");
    doReturn("temp".getBytes()).when(kryoSerializer).asDeflatedBytes(any());

    MockedStatic<YamlUtils> mockSettings = Mockito.mockStatic(YamlUtils.class, CALLS_REAL_METHODS);
    when(YamlUtils.getGivenYamlNodeFromParentPath(any(), any())).thenReturn(fullYamlFieldWithUuiD.getNode());
    assertThat(customStagePlanCreator.createPlanForChildrenNodes(ctx, customStageNode)).isNotNull();
    mockSettings.close();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testPopulateParentInfo() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String pipelineYamlFilename = "customStageWithStrategy.yaml";
    String pipelineYaml = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(pipelineYamlFilename)), StandardCharsets.UTF_8);
    String yamlWithUuid = YamlUtils.injectUuid(pipelineYaml);
    YamlField fullYamlFieldWithUuiD = YamlUtils.injectUuidInYamlField(yamlWithUuid);

    PlanCreationContext ctx = PlanCreationContext.builder().yaml(yamlWithUuid).build();
    ctx.setCurrentField(fullYamlFieldWithUuiD);

    CustomStageNode customStageNode = new CustomStageNode();
    customStageNode.setUuid("tempid");
    doReturn("temp".getBytes()).when(kryoSerializer).asDeflatedBytes(any());

    MockedStatic<YamlUtils> yamlUtilsMockSettings = Mockito.mockStatic(YamlUtils.class, CALLS_REAL_METHODS);
    MockedStatic<StrategyUtils> strategyUtilsMockSettings = Mockito.mockStatic(StrategyUtils.class, RETURNS_MOCKS);
    strategyUtilsMockSettings.when(() -> StrategyUtils.isWrappedUnderStrategy(any(YamlField.class))).thenReturn(true);
    YamlField strategyField = fullYamlFieldWithUuiD.getNode().getField("strategy");
    strategyUtilsMockSettings.when(() -> StrategyUtils.getSwappedPlanNodeId(any(), any()))
        .thenReturn(strategyField.getUuid());
    when(YamlUtils.getGivenYamlNodeFromParentPath(any(), any())).thenReturn(fullYamlFieldWithUuiD.getNode());
    Map<String, PlanCreationResponse> childrenResponses =
        customStagePlanCreator.createPlanForChildrenNodes(ctx, customStageNode);

    YamlField executionField = fullYamlFieldWithUuiD.getNode().getField("spec").getNode().getField("execution");
    PlanCreationResponse stepsResponse = childrenResponses.get(executionField.getUuid());
    Map<String, HarnessValue> parentInfo = stepsResponse.getDependencies()
                                               .getDependencyMetadataMap()
                                               .get(executionField.getUuid())
                                               .getParentInfo()
                                               .getDataMap();
    assertThat(parentInfo.get("stageId").getStringValue()).isEqualTo(strategyField.getUuid());
    assertThat(parentInfo.get("strategyId").getStringValue()).isEqualTo(customStageNode.getUuid());
    yamlUtilsMockSettings.close();
    strategyUtilsMockSettings.close();
  }
}
