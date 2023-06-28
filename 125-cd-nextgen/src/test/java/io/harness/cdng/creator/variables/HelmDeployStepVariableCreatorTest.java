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
import io.harness.cdng.helm.HelmDeployStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class HelmDeployStepVariableCreatorTest extends CategoryTest {
  private final HelmDeployStepVariableCreator helmDeployStepVariableCreator = new HelmDeployStepVariableCreator();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(helmDeployStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.HELM_DEPLOY));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(helmDeployStepVariableCreator.getFieldClass()).isEqualTo(HelmDeployStepNode.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWithHelmDeployStep.json", helmDeployStepVariableCreator, HelmDeployStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.Deployment.spec.execution.steps.helmDeployment.spec.delegateSelectors",
            "pipeline.stages.Deployment.spec.execution.steps.helmDeployment.spec.ignoreReleaseHistFailStatus",
            "pipeline.stages.Deployment.spec.execution.steps.helmDeployment.spec.skipSteadyStateCheck",
            "pipeline.stages.Deployment.spec.execution.steps.helmDeployment.description",
            "pipeline.stages.Deployment.spec.execution.steps.helmDeployment.timeout",
            "pipeline.stages.Deployment.spec.execution.steps.helmDeployment.name",
            "pipeline.stages.Deployment.spec.execution.steps.helmDeployment.when");
  }
}