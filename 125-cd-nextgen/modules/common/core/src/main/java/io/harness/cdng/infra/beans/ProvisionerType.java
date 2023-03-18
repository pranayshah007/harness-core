/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.pipeline.steptype.NGStepType.AZURE_CREATE_ARM_RESOURCE;
import static io.harness.cdng.pipeline.steptype.NGStepType.AZURE_CREATE_BP_RESOURCE;
import static io.harness.cdng.pipeline.steptype.NGStepType.AZURE_ROLLBACK_ARM_RESOURCE;
import static io.harness.cdng.pipeline.steptype.NGStepType.CF_CREATE_STACK;
import static io.harness.cdng.pipeline.steptype.NGStepType.CF_DELETE_STACK;
import static io.harness.cdng.pipeline.steptype.NGStepType.CF_ROLLBACK_STACK;
import static io.harness.cdng.pipeline.steptype.NGStepType.TERRAFORM_APPLY;
import static io.harness.cdng.pipeline.steptype.NGStepType.TERRAFORM_CLOUD_ROLLBACK;
import static io.harness.cdng.pipeline.steptype.NGStepType.TERRAFORM_CLOUD_RUN;
import static io.harness.cdng.pipeline.steptype.NGStepType.TERRAFORM_DESTROY;
import static io.harness.cdng.pipeline.steptype.NGStepType.TERRAFORM_PLAN;
import static io.harness.cdng.pipeline.steptype.NGStepType.TERRAFORM_ROLLBACK;
import static io.harness.cdng.pipeline.steptype.NGStepType.TERRAGRUNT_APPLY;
import static io.harness.cdng.pipeline.steptype.NGStepType.TERRAGRUNT_DESTROY;
import static io.harness.cdng.pipeline.steptype.NGStepType.TERRAGRUNT_PLAN;
import static io.harness.cdng.pipeline.steptype.NGStepType.TERRAGRUNT_ROLLBACK;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.steptype.NGStepType;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.Getter;

@OwnedBy(CDP)
public enum ProvisionerType {
  TERRAFORM("Terraform"),
  CLOUD_FORMATION("CloudFormation"),
  AZURE_ARM("ARM"),
  AZURE_BLUEPRINT("Blueprint"),
  SHELL_SCRIPT_PROVISIONER("Script"),
  TERRAGRUNT("Terragrunt"),
  TERRAFORM_CLOUD("TerraformCloud");

  private static final Set<ProvisionerType> supportedTypes = ImmutableSet.of(
      TERRAFORM, CLOUD_FORMATION, AZURE_ARM, AZURE_BLUEPRINT, SHELL_SCRIPT_PROVISIONER, TERRAGRUNT, TERRAFORM_CLOUD);
  private static final List<NGStepType> supportedSteps =
      Arrays.asList(TERRAFORM_APPLY, TERRAFORM_PLAN, TERRAFORM_DESTROY, TERRAFORM_ROLLBACK, CF_CREATE_STACK,
          CF_DELETE_STACK, CF_ROLLBACK_STACK, AZURE_CREATE_ARM_RESOURCE, AZURE_CREATE_BP_RESOURCE,
          AZURE_ROLLBACK_ARM_RESOURCE, NGStepType.SHELL_SCRIPT_PROVISIONER, TERRAGRUNT_PLAN, TERRAGRUNT_APPLY,
          TERRAGRUNT_DESTROY, TERRAGRUNT_ROLLBACK, TERRAFORM_CLOUD_RUN, TERRAFORM_CLOUD_ROLLBACK);

  @Getter private final String displayName;
  ProvisionerType(String displayName) {
    this.displayName = displayName;
  }

  public static boolean isSupported(ProvisionerType type) {
    return supportedTypes.contains(type);
  }
  public static List<NGStepType> getSupportedSteps() {
    return supportedSteps;
  }
}
