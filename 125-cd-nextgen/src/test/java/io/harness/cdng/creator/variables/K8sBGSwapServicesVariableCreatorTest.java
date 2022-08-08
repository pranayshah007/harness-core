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
import io.harness.cdng.k8s.K8sBGSwapServicesStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class K8sBGSwapServicesVariableCreatorTest extends CategoryTest {
  private final K8sBGSwapServicesVariableCreator k8sBGSwapServicesVariableCreator =
      new K8sBGSwapServicesVariableCreator();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(k8sBGSwapServicesVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.K8S_BG_SWAP_SERVICES));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(k8sBGSwapServicesVariableCreator.getFieldClass()).isEqualTo(K8sBGSwapServicesStepNode.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWIthK8sBGSwapServicesStep.json", k8sBGSwapServicesVariableCreator,
        K8sBGSwapServicesStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.K8s.spec.execution.steps.K8s_BG_Swap_Services.description",
            "pipeline.stages.K8s.spec.execution.steps.K8s_BG_Swap_Services.spec.skipDryRun",
            "pipeline.stages.K8s.spec.execution.steps.K8s_BG_Swap_Services.name",
            "pipeline.stages.K8s.spec.execution.steps.K8s_BG_Swap_Services.spec.delegateSelectors",
            "pipeline.stages.K8s.spec.execution.steps.K8s_BG_Swap_Services.timeout");
  }
}