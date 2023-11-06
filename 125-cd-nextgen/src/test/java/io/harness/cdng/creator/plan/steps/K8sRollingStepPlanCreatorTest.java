/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.K8sRollingStepNode;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Scanner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
@OwnedBy(HarnessTeam.CDP)
public class K8sRollingStepPlanCreatorTest extends CategoryTest {
  @Inject @InjectMocks K8sRollingStepPlanCreator stepsPlanCreator;
  @Mock private CDFeatureFlagHelper featureFlagService;
  private final String ACCOUNT_ID = "account_id";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
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

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetExecutionSpecType() throws IOException {
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_K8S_ASYNC_STEP_STRATEGY)).thenReturn(true);
    YamlField stepsYamlField = getYamlFieldFromGivenFileName("cdng/plan/steps/nestedStepGroups.yml");
    YamlField currentNode = stepsYamlField.getNode().getField("execution").getNode().getField("rollbackSteps");
    K8sRollingStepNode k8sRollingStepNode = new K8sRollingStepNode();
    HashMap<String, PlanCreationContextValue> metadata = new HashMap<>();
    metadata.put("metadata", PlanCreationContextValue.newBuilder().setAccountIdentifier(ACCOUNT_ID).build());
    PlanCreationContext ctx = PlanCreationContext.builder().currentField(currentNode).globalContext(metadata).build();
    StepType stepType = stepsPlanCreator.getStepSpecType(ctx, k8sRollingStepNode);
    assertThat(stepType).isEqualTo(StepType.newBuilder()
                                       .setType(ExecutionNodeType.K8S_ROLLING_V2.getName())
                                       .setStepCategory(StepCategory.STEP)
                                       .build());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetExecutionFacilitatorType() throws IOException {
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_K8S_ASYNC_STEP_STRATEGY)).thenReturn(true);
    YamlField stepsYamlField = getYamlFieldFromGivenFileName("cdng/plan/steps/nestedStepGroups.yml");
    YamlField currentNode = stepsYamlField.getNode().getField("execution").getNode().getField("rollbackSteps");
    K8sRollingStepNode k8sRollingStepNode = new K8sRollingStepNode();
    HashMap<String, PlanCreationContextValue> metadata = new HashMap<>();
    metadata.put("metadata", PlanCreationContextValue.newBuilder().setAccountIdentifier(ACCOUNT_ID).build());
    PlanCreationContext ctx = PlanCreationContext.builder().currentField(currentNode).globalContext(metadata).build();
    String facilitatorType = stepsPlanCreator.getFacilitatorType(ctx, k8sRollingStepNode);
    assertThat(facilitatorType).isEqualTo(OrchestrationFacilitatorType.ASYNC_CHAIN);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetExecutionSpecTypeFFOff() throws IOException {
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_K8S_ASYNC_STEP_STRATEGY)).thenReturn(false);
    YamlField stepsYamlField = getYamlFieldFromGivenFileName("cdng/plan/steps/nestedStepGroups.yml");
    YamlField currentNode = stepsYamlField.getNode().getField("execution").getNode().getField("rollbackSteps");
    K8sRollingStepNode k8sRollingStepNode = new K8sRollingStepNode();
    HashMap<String, PlanCreationContextValue> metadata = new HashMap<>();
    metadata.put("metadata", PlanCreationContextValue.newBuilder().setAccountIdentifier(ACCOUNT_ID).build());
    PlanCreationContext ctx = PlanCreationContext.builder().currentField(currentNode).globalContext(metadata).build();
    StepType stepType = stepsPlanCreator.getStepSpecType(ctx, k8sRollingStepNode);
    assertThat(stepType).isEqualTo(StepType.newBuilder()
                                       .setType(ExecutionNodeType.K8S_ROLLING.getYamlType())
                                       .setStepCategory(StepCategory.STEP)
                                       .build());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetExecutionFacilitatorTypeFFOff() throws IOException {
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_K8S_ASYNC_STEP_STRATEGY)).thenReturn(false);
    YamlField stepsYamlField = getYamlFieldFromGivenFileName("cdng/plan/steps/nestedStepGroups.yml");
    YamlField currentNode = stepsYamlField.getNode().getField("execution").getNode().getField("rollbackSteps");
    K8sRollingStepNode k8sRollingStepNode = new K8sRollingStepNode();
    HashMap<String, PlanCreationContextValue> metadata = new HashMap<>();
    metadata.put("metadata", PlanCreationContextValue.newBuilder().setAccountIdentifier(ACCOUNT_ID).build());
    PlanCreationContext ctx = PlanCreationContext.builder().currentField(currentNode).globalContext(metadata).build();
    String facilitatorType = stepsPlanCreator.getFacilitatorType(ctx, k8sRollingStepNode);
    assertThat(facilitatorType).isEqualTo(OrchestrationFacilitatorType.TASK_CHAIN);
  }
}
