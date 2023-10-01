/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.terragrunt.v2;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public class TerragruntV2Contants {
  static final String INIT = "INIT";
  static final String WORKSPACE = "WORKSPACE";
  static final String PLAN = "PLAN";
  static final String APPLY = "APPLY";
  static final String DESTROY = "DESTROY";
  static final String OUTPUT = "OUTPUT";

  public static final String TERRAGRUNT_PLAN_COMMAND_FORMAT = "terragrunt plan -out=tfplan -input=false %s %s %s";
  public static final String TERRAGRUNT_PLAN_DESTROY_COMMAND_FORMAT =
      "terragrunt plan -destroy -out=tfdestroyplan -input=false %s %s %s";
  public static final String TERRAGRUNT_RUN_ALL_PLAN_COMMAND_FORMAT =
      "terragrunt run-all plan -out=tfplan -input=false --terragrunt-non-interactive %s %s %s";
  public static final String TERRAGRUNT_RUN_ALL_PLAN_DESTROY_COMMAND_FORMAT =
      "terragrunt run-all plan -destroy -out=tfdestroyplan -input=false --terragrunt-non-interactive %s %s %s";
  public static final String TERRAGRUNT_DESTROY_COMMAND_FORMAT =
      "terragrunt destroy %s --terragrunt-non-interactive %s %s %s";
  public static final String TERRAGRUNT_RUN_ALL_DESTROY_COMMAND_FORMAT =
      "terragrunt run-all destroy %s --terragrunt-non-interactive %s %s %s";
  public static final String TERRAGRUNT_RUN_ALL_APPLY_COMMAND_FORMAT =
      "terragrunt run-all apply -input=false --terragrunt-non-interactive %s %s %s";
  public static final String TERRAGRUNT_OUTPUT_COMMAND_FORMAT = "terragrunt output %s -json > %s";
  public static final String TERRAGRUNT_RUN_ALL_OUTPUT_COMMAND_FORMAT =
      "terragrunt run-all output --terragrunt-non-interactive %s --json > %s";
}