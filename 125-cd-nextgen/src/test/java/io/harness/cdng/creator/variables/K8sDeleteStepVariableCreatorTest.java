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
import io.harness.cdng.k8s.K8sDeleteStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class K8sDeleteStepVariableCreatorTest extends CategoryTest {
  private final K8sDeleteStepVariableCreator k8sDeleteStepVariableCreator = new K8sDeleteStepVariableCreator();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(k8sDeleteStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.K8S_DELETE));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(k8sDeleteStepVariableCreator.getFieldClass()).isEqualTo(K8sDeleteStepNode.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2ResourceName() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWithK8sDeleteSteps.json", k8sDeleteStepVariableCreator, K8sDeleteStepNode.class, 0);
    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Resource_Name.spec.deleteResources.spec.resourceNames",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Resource_Name.timeout",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Resource_Name.name",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Resource_Name.spec.skipDryRun",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Resource_Name.spec.delegateSelectors",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Resource_Name.description");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2DeleteManifestPath() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWithK8sDeleteSteps.json", k8sDeleteStepVariableCreator, K8sDeleteStepNode.class, 1);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Manifest_Path.spec.delegateSelectors",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Manifest_Path.name",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Manifest_Path.spec.skipDryRun",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Manifest_Path.description",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Manifest_Path.timeout",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Manifest_Path.spec.deleteResources.spec.manifestPaths",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Manifest_Path.spec.deleteResources.spec.allManifestPaths");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2ReleaseName() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWithK8sDeleteSteps.json", k8sDeleteStepVariableCreator, K8sDeleteStepNode.class, 2);
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Release_Name.spec.delegateSelectors",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Release_Name.spec.skipDryRun",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Release_Name.spec.deleteResources.spec.deleteNamespace",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Release_Name.timeout",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Release_Name.description",
            "pipeline.stages.K8s.spec.execution.steps.K8s_Delete_Release_Name.name");
  }
}