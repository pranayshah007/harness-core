/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.pms.contracts.plan.ExecutionMode.PIPELINE_ROLLBACK;
import static io.harness.pms.contracts.plan.ExecutionMode.POST_EXECUTION_ROLLBACK;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.advisers.nextstep.NextStepAdviser;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.creator.plan.CDStepsPlanCreator;
import io.harness.plancreator.execution.StepsExecutionConfig;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.common.NGSectionStepParameters;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest({YamlUtils.class})
@OwnedBy(HarnessTeam.CDC)
public class CDStepsPlanCreatorTest extends CDNGTestBase {
  @Inject @InjectMocks CDStepsPlanCreator stepsPlanCreator;

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
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(stepsPlanCreator.getFieldClass()).isEqualTo(StepsExecutionConfig.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = stepsPlanCreator.getSupportedTypes();
    assertThat(supportedTypes.containsKey(YAMLFieldNameConstants.STEPS)).isEqualTo(true);
    assertThat(supportedTypes.get(YAMLFieldNameConstants.STEPS).contains(PlanCreatorUtils.ANY_TYPE)).isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetParentNode() throws IOException {
    List<String> childrenNodeId = Arrays.asList("child1", "child2");

    YamlField stepsYamlField = getYamlFieldFromGivenFileName("cdng/plan/steps/steps.yml");

    NGSectionStepParameters stepParameters =
        NGSectionStepParameters.builder().childNodeId(childrenNodeId.get(0)).build();

    YamlNode dummyYamlNode = getDummyNode();
    MockedStatic<YamlUtils> mockSettings = Mockito.mockStatic(YamlUtils.class);
    when(YamlUtils.getGivenYamlNodeFromParentPath(stepsYamlField.getNode(), "stage")).thenReturn(dummyYamlNode);

    PlanCreationContext ctx = PlanCreationContext.builder().currentField(stepsYamlField).build();
    PlanNode planForParentNode = stepsPlanCreator.createPlanForParentNode(ctx, null, childrenNodeId);
    assertThat(planForParentNode.getUuid()).isEqualTo(ctx.getCurrentField().getNode().getUuid());
    assertThat(planForParentNode.getStepParameters()).isEqualTo(stepParameters);

    Map<ExecutionMode, List<AdviserObtainment>> advisorObtainmentsPerMode =
        planForParentNode.getAdvisorObtainmentsForExecutionMode();
    assertThat(advisorObtainmentsPerMode).hasSize(2);
    assertThat(advisorObtainmentsPerMode).containsKey(POST_EXECUTION_ROLLBACK);
    assertThat(advisorObtainmentsPerMode.get(POST_EXECUTION_ROLLBACK)).hasSize(1);
    AdviserObtainment postExecAdvisor = advisorObtainmentsPerMode.get(POST_EXECUTION_ROLLBACK).get(0);
    assertThat(postExecAdvisor.getType()).isEqualTo(NextStepAdviser.ADVISER_TYPE);
    assertThat(advisorObtainmentsPerMode).containsKey(PIPELINE_ROLLBACK);
    assertThat(advisorObtainmentsPerMode.get(PIPELINE_ROLLBACK)).hasSize(1);
    AdviserObtainment prbAdvisor = advisorObtainmentsPerMode.get(PIPELINE_ROLLBACK).get(0);
    assertThat(prbAdvisor.getType()).isEqualTo(NextStepAdviser.ADVISER_TYPE);
    mockSettings.close();
  }

  private YamlNode getDummyNode() throws IOException {
    String dummyStage = "stage:\n"
        + "  __uuid: uuid2\n"
        + "__uuid: uuid1\n";
    return YamlUtils.readTree(dummyStage).getNode();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testAdviserForStepGroup() throws IOException {
    List<String> childrenNodeId = Arrays.asList("child1", "child2");

    YamlField stepsYamlField = getYamlFieldFromGivenFileName("cdng/plan/steps/step-group.yml")
                                   .getNode()
                                   .getField("stepGroup")
                                   .getNode()
                                   .getField("steps");
    NGSectionStepParameters stepParameters =
        NGSectionStepParameters.builder().childNodeId(childrenNodeId.get(0)).build();

    PlanCreationContext ctx = PlanCreationContext.builder().currentField(stepsYamlField).build();
    PlanNode planForParentNode = stepsPlanCreator.createPlanForParentNode(ctx, null, childrenNodeId);
    assertThat(planForParentNode.getUuid()).isEqualTo(ctx.getCurrentField().getNode().getUuid());
    assertThat(planForParentNode.getStepParameters()).isEqualTo(stepParameters);
    assertThat(planForParentNode.getAdviserObtainments().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes() throws IOException {
    YamlField stepsYamlField = getYamlFieldFromGivenFileName("cdng/plan/steps/steps.yml");
    PlanCreationContext ctx = PlanCreationContext.builder().currentField(stepsYamlField).build();
    List<YamlField> stepsList = ctx.getStepYamlFieldsFromStepsAsCurrentYamlField();
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap =
        stepsPlanCreator.createPlanForChildrenNodes(ctx, null);

    assertThat(planCreationResponseMap.size()).isEqualTo(2);
    List<String> uuidOfChildren = planCreationResponseMap.keySet().stream().collect(Collectors.toList());
    assertThat(uuidOfChildren)
        .containsExactly(stepsList.get(0).getNode().getUuid(), stepsList.get(1).getNode().getUuid());
  }
}
