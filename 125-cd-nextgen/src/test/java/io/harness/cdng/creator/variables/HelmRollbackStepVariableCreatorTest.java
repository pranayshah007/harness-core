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
import io.harness.cdng.helm.HelmRollbackStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class HelmRollbackStepVariableCreatorTest extends CategoryTest {
  private final HelmRollbackStepVariableCreator helmRollbackStepVariableCreator = new HelmRollbackStepVariableCreator();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(helmRollbackStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.HELM_ROLLBACK));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(helmRollbackStepVariableCreator.getFieldClass()).isEqualTo(HelmRollbackStepNode.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2() throws IOException {
    List<String> fqnPropertiesList =
        StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2("cdng/variables/pipelineWithHelmRollbackStep.json",
            helmRollbackStepVariableCreator, HelmRollbackStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.Deployment.spec.execution.steps.helmRollback.description",
            "pipeline.stages.Deployment.spec.execution.steps.helmRollback.spec.skipSteadyStateCheck",
            "pipeline.stages.Deployment.spec.execution.steps.helmRollback.spec.delegateSelectors",
            "pipeline.stages.Deployment.spec.execution.steps.helmRollback.timeout",
            "pipeline.stages.Deployment.spec.execution.steps.helmRollback.name",
            "pipeline.stages.Deployment.spec.execution.steps.helmRollback.when");
  }
}