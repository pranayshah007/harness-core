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
import io.harness.cdng.k8s.K8sCanaryDeleteStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class K8sCanaryDeleteStepVariableCreatorTest extends CategoryTest {
  private final K8sCanaryDeleteStepVariableCreator k8sCanaryDeleteStepVariableCreator =
      new K8sCanaryDeleteStepVariableCreator();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(k8sCanaryDeleteStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.K8S_CANARY_DELETE));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(k8sCanaryDeleteStepVariableCreator.getFieldClass()).isEqualTo(K8sCanaryDeleteStepNode.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWithK8sCanaryDeleteStep.json", k8sCanaryDeleteStepVariableCreator,
        K8sCanaryDeleteStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.K8s.spec.execution.steps.K8s_Canary_Delete.description",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Canary_Delete.name",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Canary_Delete.spec.delegateSelectors",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Canary_Delete.timeout",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Canary_Delete.spec.skipDryRun");
  }
}