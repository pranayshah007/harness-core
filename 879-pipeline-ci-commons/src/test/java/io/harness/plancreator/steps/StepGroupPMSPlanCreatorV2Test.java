/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.ContainerTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.PlanExecutionContext;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.common.steps.stepgroup.StepGroupStep;
import io.harness.steps.common.steps.stepgroup.StepGroupStepParameters;
import io.harness.steps.plugin.ContainerCommandUnitConstants;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@Slf4j
@OwnedBy(CDP)
public class StepGroupPMSPlanCreatorV2Test extends ContainerTestBase {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepGroupPMSPlanCreator stepGroupPMSPlanCreator;
  @Mock private StepGroupHandlerFactory stepGroupHandlerFactory;

  @Inject @InjectMocks private StepGroupPMSPlanCreatorV2 stepGroupPMSPlanCreatorV2;

  YamlField stepGroupYamlField;
  StepGroupElementConfig stepElementConfig;
  PlanCreationContext context;

  @Before
  public void setUp() throws IOException {
    stepGroupYamlField = getContainerStepGroupYamlField("pipeline-container-step-group.yaml");
    assertThat(stepGroupYamlField).isNotNull();
    stepElementConfig = YamlUtils.read(stepGroupYamlField.getNode().toString(), StepGroupElementConfig.class);
    context = PlanCreationContext.builder()
                  .currentField(stepGroupYamlField)
                  .globalContext("metadata",
                      PlanCreationContextValue.newBuilder()
                          .setExecutionContext(PlanExecutionContext.newBuilder().build())
                          .build())
                  .build();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(stepGroupPMSPlanCreatorV2.getSupportedTypes())
        .isEqualTo(Collections.singletonMap(
            YAMLFieldNameConstants.STEP_GROUP, Collections.singleton(PlanCreatorUtils.ANY_TYPE)));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode() {
    PlanNode planForParentNode = stepGroupPMSPlanCreatorV2.createPlanForParentNode(context, stepElementConfig, null);
    assertThat(planForParentNode.getIdentifier()).isEqualTo(stepElementConfig.getIdentifier());
    assertThat(planForParentNode.getStepType()).isEqualTo(StepGroupStep.STEP_TYPE);
    assertThat(planForParentNode.getFacilitatorObtainments()).hasSize(1);
    assertThat(planForParentNode.getFacilitatorObtainments().get(0).getType().getType()).isEqualTo("CHILD");
    assertThat(planForParentNode.isSkipExpressionChain()).isFalse();

    StepGroupStepParameters stepParameters = (StepGroupStepParameters) planForParentNode.getStepParameters();
    assertThat(stepParameters.getName()).isEqualTo(stepElementConfig.getName());
    assertThat(stepParameters.getIdentifier()).isEqualTo(stepElementConfig.getIdentifier());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes() {
    LinkedHashMap<String, PlanCreationResponse> planForChildrenNodes =
        stepGroupPMSPlanCreatorV2.createPlanForChildrenNodes(context, stepElementConfig);
    PlanCreationResponse initPlanCreationResponse = null;
    for (PlanCreationResponse planCreationResponse : planForChildrenNodes.values()) {
      if (planCreationResponse.getPlanNode() != null
          && ContainerCommandUnitConstants.InitContainer.equals(planCreationResponse.getPlanNode().getName())) {
        initPlanCreationResponse = planCreationResponse;
      }
    }

    assertThat(initPlanCreationResponse).isNotNull();
    assertThat(initPlanCreationResponse.getPlanNode()).isNotNull();
    assertThat(initPlanCreationResponse.getPlanNode().getName()).isEqualTo(ContainerCommandUnitConstants.InitContainer);
    assertThat(initPlanCreationResponse.getPlanNode().getIdentifier())
        .isEqualTo(ContainerCommandUnitConstants.InitContainer);
    assertThat(initPlanCreationResponse.getPlanNode().getStepType().getType()).isEqualTo("InitializeContainer");

    assertThat(initPlanCreationResponse.getPlanNode().getStepParameters()).isInstanceOf(InitContainerV2StepInfo.class);
    InitContainerV2StepInfo initContainerV2StepInfo =
        (InitContainerV2StepInfo) initPlanCreationResponse.getPlanNode().getStepParameters();
    assertThat(initContainerV2StepInfo.getIdentifier()).isEqualTo(stepElementConfig.getIdentifier());
    assertThat(initContainerV2StepInfo.getName()).isEqualTo(stepElementConfig.getName());
    assertThat(initContainerV2StepInfo.getSharedPaths().get("0")).isEqualTo("share/path/1");

    ContainerK8sInfra infrastructure = (ContainerK8sInfra) initContainerV2StepInfo.getInfrastructure();
    assertThat(infrastructure.getSpec().getConnectorRef().getValue()).isEqualTo("connectorRef");
    assertThat(infrastructure.getSpec().getNamespace().getValue()).isEqualTo("tmp-namespace");
    assertThat(infrastructure.getSpec().getContainerSecurityContext().getValue().getRunAsUser().getValue())
        .isEqualTo(1);

    assertThat(initContainerV2StepInfo.getStepsExecutionConfig().getSteps().size()).isEqualTo(1);
  }

  private YamlField getContainerStepGroupYamlField(String pipelineYamlName) throws IOException {
    final URL pipelineYamlFile = this.getClass().getClassLoader().getResource(pipelineYamlName);
    assertThat(pipelineYamlFile).isNotNull();
    String pipelineYaml = Resources.toString(pipelineYamlFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();

    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();

    List<YamlNode> stagesNodes = stagesYamlField.getNode().asArray();
    YamlField approvalStageField = stagesNodes.get(0).getField("stage");
    YamlField stageSpec = Objects.requireNonNull(approvalStageField).getNode().getField("spec");
    YamlField executionField = Objects.requireNonNull(stageSpec).getNode().getField("execution");

    YamlField stepsField = executionField.getNode().getField("steps");
    YamlNode stepGroupNode = stepsField.getNode().asArray().get(1);

    return stepGroupNode.getField("stepGroup");
  }
}
