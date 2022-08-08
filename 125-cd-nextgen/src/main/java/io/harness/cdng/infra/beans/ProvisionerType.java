/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.pipeline.NGStepType.CF_CREATE_STACK;
import static io.harness.cdng.pipeline.NGStepType.CF_DELETE_STACK;
import static io.harness.cdng.pipeline.NGStepType.CF_ROLLBACK_STACK;
import static io.harness.cdng.pipeline.NGStepType.TERRAFORM_APPLY;
import static io.harness.cdng.pipeline.NGStepType.TERRAFORM_DESTROY;
import static io.harness.cdng.pipeline.NGStepType.TERRAFORM_PLAN;
import static io.harness.cdng.pipeline.NGStepType.TERRAFORM_ROLLBACK;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.NGStepType;

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
  SHELL_SCRIPT_PROVISIONER("Script");

  private static final Set<ProvisionerType> supportedTypes = ImmutableSet.of(TERRAFORM, CLOUD_FORMATION);
  private static final List<NGStepType> supportedSteps = Arrays.asList(TERRAFORM_APPLY, TERRAFORM_PLAN,
      TERRAFORM_DESTROY, TERRAFORM_ROLLBACK, CF_CREATE_STACK, CF_DELETE_STACK, CF_ROLLBACK_STACK);

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
