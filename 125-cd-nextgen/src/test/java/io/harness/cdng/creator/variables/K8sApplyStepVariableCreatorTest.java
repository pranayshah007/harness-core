/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.k8s.K8sApplyStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class K8sApplyStepVariableCreatorTest extends CategoryTest {
  private final K8sApplyStepVariableCreator k8sApplyStepVariableCreator = new K8sApplyStepVariableCreator();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(k8sApplyStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.K8S_APPLY));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(k8sApplyStepVariableCreator.getFieldClass()).isEqualTo(K8sApplyStepNode.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWithK8sApplyStep.json", k8sApplyStepVariableCreator, K8sApplyStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.K8s.spec.execution.steps.K8s_Apply_Step.timeout",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Apply_Step.name",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Apply_Step.description",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Apply_Step.spec.filePaths",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Apply_Step.spec.skipDryRun",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Apply_Step.spec.delegateSelectors",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Apply_Step.spec.skipSteadyStateCheck",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Apply_Step.spec.skipRendering");
  }
}