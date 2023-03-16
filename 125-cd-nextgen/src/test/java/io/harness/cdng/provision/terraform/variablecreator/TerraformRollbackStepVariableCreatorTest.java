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
import io.harness.cdng.provision.terraform.TerraformRollbackStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class TerraformRollbackStepVariableCreatorTest extends CategoryTest {
  private final TerraformRollbackStepVariableCreator terraformRollbackStepVariableCreator =
      new TerraformRollbackStepVariableCreator();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(terraformRollbackStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.TERRAFORM_ROLLBACK));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(terraformRollbackStepVariableCreator.getFieldClass()).isEqualTo(TerraformRollbackStepNode.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getInfraFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWIthInfraTerraformRollbackStep.json", terraformRollbackStepVariableCreator,
        TerraformRollbackStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Rollback.timeout",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Rollback.spec.delegateSelectors",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Rollback.name",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Rollback.spec.provisionerIdentifier",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Rollback.description",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Rollback.spec.skipRefreshCommand",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Rollback.when");
  }
}