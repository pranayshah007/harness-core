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
import io.harness.cdng.provision.terraform.TerraformPlanStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class TerraformPlanStepVariableCreatorTest extends CategoryTest {
  private final TerraformPlanStepVariableCreator terraformPlanStepVariableCreator =
      new TerraformPlanStepVariableCreator();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(terraformPlanStepVariableCreator.getSupportedStepTypes())
        .isEqualTo(Collections.singleton(StepSpecTypeConstants.TERRAFORM_PLAN));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(terraformPlanStepVariableCreator.getFieldClass()).isEqualTo(TerraformPlanStepNode.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2() throws IOException {
    List<String> fqnPropertiesList = StepVariableCreatorTestUtils.getInfraFqnPropertiesForParentNodeV2(
        "cdng/variables/pipelineWIthInfraTerraformPlanStep.json", terraformPlanStepVariableCreator,
        TerraformPlanStepNode.class);
    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.environmentVariables.env",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.description",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.secretManagerRef",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.delegateSelectors",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.configFiles.store.spec.folderPath",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.targets",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.provisionerIdentifier",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.timeout",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.configFiles.store.spec.branch",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.configFiles.store.spec.connectorRef",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.configFiles.store.spec.paths",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.configFiles.store.spec.repoName",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.backendConfig.spec.content",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.skipRefreshCommand",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.configFiles.store.spec.commitId",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.workspace",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.name",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.varFiles.tfvars.spec.store.spec.repoName",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.varFiles.tfvars.spec.store.spec.folderPath",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.varFiles.tfvars.spec.store.spec.connectorRef",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.varFiles.tfvars.spec.store.spec.commitId",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.varFiles.tfvars.spec.store.spec.branch",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.varFiles.tfvars_inline.spec.content",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.varFiles.tfvars.spec.store.spec.paths",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.configFiles.moduleSource.useConnectorCredentials",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.exportTerraformPlanJson",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.spec.configuration.exportTerraformHumanReadablePlan",
            "pipeline.stages.K8s.spec.infrastructure.infrastructureDefinition.provisioner.steps.Terraform_Plan.when");
  }
}