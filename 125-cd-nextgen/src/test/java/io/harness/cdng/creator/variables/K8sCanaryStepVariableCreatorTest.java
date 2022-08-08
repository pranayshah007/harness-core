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
import io.harness.cdng.k8s.K8sCanaryStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class K8sCanaryStepVariableCreatorTest extends CategoryTest {
  private final K8sCanaryStepVariableCreator k8sCanaryStepVariableCreator = new K8sCanaryStepVariableCreator();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(k8sCanaryStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.K8S_CANARY_DEPLOY));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(k8sCanaryStepVariableCreator.getFieldClass()).isEqualTo(K8sCanaryStepNode.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWithK8sCanaryStep.json", k8sCanaryStepVariableCreator, K8sCanaryStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.K8s.spec.execution.steps.K8s_Canary.spec.instanceSelection.spec.count",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Canary.timeout",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Canary.description",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Canary.spec.delegateSelectors",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Canary.spec.skipDryRun",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Canary.name");
  }
}