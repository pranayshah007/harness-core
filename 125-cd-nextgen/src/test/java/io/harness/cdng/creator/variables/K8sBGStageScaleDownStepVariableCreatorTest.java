/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.k8s.K8sBGStageScaleDownStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class K8sBGStageScaleDownStepVariableCreatorTest extends CategoryTest {
  private final K8sBGStageScaleDownStepVariableCreator k8sBGStageScaleDownStepVariableCreator =
      new K8sBGStageScaleDownStepVariableCreator();

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(k8sBGStageScaleDownStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.K8S_BLUE_GREEN_STAGE_SCALE_DOWN));
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(k8sBGStageScaleDownStepVariableCreator.getFieldClass()).isEqualTo(K8sBGStageScaleDownStepNode.class);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWithK8sBGStageScaleDownStep.json", k8sBGStageScaleDownStepVariableCreator,
        K8sBGStageScaleDownStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.K8s.spec.execution.steps.K8s_Blue_Green_Stage_Scale_Down.timeout",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Blue_Green_Stage_Scale_Down.spec.delegateSelectors",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Blue_Green_Stage_Scale_Down.description",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Blue_Green_Stage_Scale_Down.name",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Blue_Green_Stage_Scale_Down.when");
  }
}
