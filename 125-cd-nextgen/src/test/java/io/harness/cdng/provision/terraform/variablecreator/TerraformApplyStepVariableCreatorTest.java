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
import io.harness.cdng.provision.terraform.TerraformApplyStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class TerraformApplyStepVariableCreatorTest extends CategoryTest {
  private final TerraformApplyStepVariableCreator terraformApplyStepVariableCreator =
      new TerraformApplyStepVariableCreator();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(terraformApplyStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.TERRAFORM_APPLY));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(terraformApplyStepVariableCreator.getFieldClass()).isEqualTo(TerraformApplyStepNode.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2Inherit() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getInfraFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWithInfraTerraformApplyInheritStep.json", terraformApplyStepVariableCreator,
        TerraformApplyStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply_Inherit.spec.delegateSelectors",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply_Inherit.description",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply_Inherit.timeout",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply_Inherit.spec.provisionerIdentifier",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply_Inherit.name",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply_Inherit.spec.configuration.skipRefreshCommand",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply_Inherit.when");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getInfraFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWithInfraTerraformApplyStep.json", terraformApplyStepVariableCreator,
        TerraformApplyStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.configuration.spec.configFiles.store.spec.repositoryName",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.provisionerIdentifier",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.configuration.spec.configFiles.store.spec.artifactPaths",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.configuration.spec.varFiles.tfvar_arifactory.spec.store.spec.connectorRef",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.configuration.spec.varFiles.tvfar_inline.spec.content",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.configuration.spec.environmentVariables.GIT_SSH_COMMAND",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.name",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.delegateSelectors",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.configuration.spec.configFiles.store.spec.connectorRef",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.configuration.spec.workspace",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.description",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.configuration.spec.varFiles.tfvar_arifactory.spec.store.spec.repositoryName",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.configuration.spec.varFiles.tfvar_arifactory.spec.store.spec.artifactPaths",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.timeout",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.configuration.spec.backendConfig.spec.content",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.configuration.spec.targets",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.configuration.spec.environmentVariables.ANSWER",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.configuration.spec.configFiles.moduleSource.useConnectorCredentials",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.spec.configuration.skipRefreshCommand",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Apply.when");
  }
}