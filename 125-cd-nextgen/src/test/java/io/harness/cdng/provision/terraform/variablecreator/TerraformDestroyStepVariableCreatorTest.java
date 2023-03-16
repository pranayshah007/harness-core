/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform.variablecreator;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.variables.StepVariableCreatorTestUtils;
import io.harness.cdng.provision.terraform.TerraformDestroyStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class TerraformDestroyStepVariableCreatorTest extends CategoryTest {
  private final TerraformDestroyStepVariableCreator terraformDestroyStepVariableCreator =
      new TerraformDestroyStepVariableCreator();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(terraformDestroyStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.TERRAFORM_DESTROY));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(terraformDestroyStepVariableCreator.getFieldClass()).isEqualTo(TerraformDestroyStepNode.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getInfraFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWIthInfraTerraformDestroyStep.json", terraformDestroyStepVariableCreator,
        TerraformDestroyStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Destroy.spec.provisionerIdentifier",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Destroy.name",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Destroy.timeout",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Destroy.spec.delegateSelectors",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Destroy.description",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Destroy.spec.configuration.skipRefreshCommand",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Destroy.when");
  }
}