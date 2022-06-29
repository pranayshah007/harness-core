/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.pipeline.CdAbstractStepNode;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Scanner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class CDPMSStepPlanCreatorV2Test extends CategoryTest {
  @Spy private CDPMSStepPlanCreatorV2<CdAbstractStepNode> cdPMSStepPlanCreator;
  private static final String SHELL_SCRIPT_TYPE = "ShellScript";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFindStepsBeforeCurrentStep() throws IOException {
    YamlField pipeline = getYamlFieldFromGivenFileName("cdng/plan/pipeline_multistage.yaml");
    YamlField currentStep = pipeline.fromYamlPath("pipeline/stages/[2]/stage/spec/execution/steps/[1]/step");
    List<YamlNode> steps = cdPMSStepPlanCreator.findStepsBeforeCurrentStep(
        currentStep, yamlNode -> SHELL_SCRIPT_TYPE.equals(yamlNode.getType()));
    assertThat(steps).hasSize(9);
    assertThat(steps.stream().map(YamlUtils::getFullyQualifiedName))
        .containsOnly("pipeline.stages.Stage1.spec.execution.steps.Execution_Step1",
            "pipeline.stages.Stage1.spec.execution.steps.Execution_Step2",
            "pipeline.stages.Stage1.spec.infrastructure.infrastructureDefinition.provisioner.steps.Provisioner_Step1",
            "pipeline.stages.Stage1.spec.infrastructure.infrastructureDefinition.provisioner.steps.Provisioner_Step2",
            "pipeline.stages.Stage2.spec.execution.steps.Execution_Step1",
            "pipeline.stages.Stage2.spec.execution.steps.Execution_Step2",
            "pipeline.stages.Stage2.spec.execution.steps.Execution_Script3",
            "pipeline.stages.Stage3.spec.execution.steps.Execution_Script1",
            "pipeline.stages.Stage3.spec.execution.steps.Execution_Step2");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFindStepsBeforeCurrentStepMiddleStage() throws IOException {
    YamlField pipeline = getYamlFieldFromGivenFileName("cdng/plan/pipeline_multistage.yaml");
    YamlField currentStep = pipeline.fromYamlPath("pipeline/stages/[1]/stage/spec/execution/steps/[2]/step");
    List<YamlNode> steps = cdPMSStepPlanCreator.findStepsBeforeCurrentStep(
        currentStep, yamlNode -> SHELL_SCRIPT_TYPE.equals(yamlNode.getType()));
    assertThat(steps).hasSize(7);
    assertThat(steps.stream().map(YamlUtils::getFullyQualifiedName))
        .containsOnly("pipeline.stages.Stage1.spec.execution.steps.Execution_Step1",
            "pipeline.stages.Stage1.spec.execution.steps.Execution_Step2",
            "pipeline.stages.Stage1.spec.infrastructure.infrastructureDefinition.provisioner.steps.Provisioner_Step1",
            "pipeline.stages.Stage1.spec.infrastructure.infrastructureDefinition.provisioner.steps.Provisioner_Step2",
            "pipeline.stages.Stage2.spec.execution.steps.Execution_Step1",
            "pipeline.stages.Stage2.spec.execution.steps.Execution_Step2",
            "pipeline.stages.Stage2.spec.execution.steps.Execution_Script3");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFindStepsBeforeCurrentStepSameStageProvisioner() throws IOException {
    YamlField pipeline = getYamlFieldFromGivenFileName("cdng/plan/pipeline_multistage.yaml");
    YamlField currentStep = pipeline.fromYamlPath(
        "pipeline/stages/[0]/stage/spec/infrastructure/infrastructureDefinition/provisioner/steps/[1]/step");
    List<YamlNode> steps = cdPMSStepPlanCreator.findStepsBeforeCurrentStep(
        currentStep, yamlNode -> SHELL_SCRIPT_TYPE.equals(yamlNode.getType()));
    assertThat(steps).hasSize(4);
    assertThat(steps.stream().map(YamlUtils::getFullyQualifiedName))
        .containsOnly("pipeline.stages.Stage1.spec.execution.steps.Execution_Step1",
            "pipeline.stages.Stage1.spec.execution.steps.Execution_Step2",
            "pipeline.stages.Stage1.spec.infrastructure.infrastructureDefinition.provisioner.steps.Provisioner_Step1",
            "pipeline.stages.Stage1.spec.infrastructure.infrastructureDefinition.provisioner.steps.Provisioner_Step2");
  }

  private YamlField getYamlFieldFromGivenFileName(String file) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(file);
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField yamlField = YamlUtils.readTree(yaml);
    return yamlField;
  }
}