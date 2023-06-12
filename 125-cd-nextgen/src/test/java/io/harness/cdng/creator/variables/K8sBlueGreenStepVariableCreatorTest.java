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
import io.harness.cdng.k8s.K8sBlueGreenStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class K8sBlueGreenStepVariableCreatorTest extends CategoryTest {
  private final K8sBlueGreenStepVariableCreator k8sBlueGreenStepVariableCreator = new K8sBlueGreenStepVariableCreator();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(k8sBlueGreenStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.K8S_BLUE_GREEN_DEPLOY));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(k8sBlueGreenStepVariableCreator.getFieldClass()).isEqualTo(K8sBlueGreenStepNode.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2() throws IOException {
    List<String> fqnPropertiesList =
        StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2("cdng/variables/pipelineWithK8sBlueGreenStep.json",
            k8sBlueGreenStepVariableCreator, K8sBlueGreenStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.K8s.spec.execution.steps.Stage_Deployment.timeout",
            "pipeline.stages.K8s.spec.execution.steps.Stage_Deployment.spec.delegateSelectors",
            "pipeline.stages.K8s.spec.execution.steps.Stage_Deployment.description",
            "pipeline.stages.K8s.spec.execution.steps.Stage_Deployment.spec.skipDryRun",
            "pipeline.stages.K8s.spec.execution.steps.Stage_Deployment.spec.pruningEnabled",
            "pipeline.stages.K8s.spec.execution.steps.Stage_Deployment.name",
            "pipeline.stages.K8s.spec.execution.steps.Stage_Deployment.when",
            "pipeline.stages.K8s.spec.execution.steps.Stage_Deployment.spec.skipDeploymentIfSameManifest");
  }
}