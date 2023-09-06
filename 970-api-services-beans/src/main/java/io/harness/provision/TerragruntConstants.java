/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public final class TerragruntConstants {
  public static final String TERRAGRUNT_INIT_COMMAND_FORMAT = "terragrunt init%s %s";
  public static final String TERRAGRUNT_RUN_ALL_INIT_COMMAND_FORMAT = "terragrunt run-all init%s %s";
  public static final String TERRAGRUNT_REFRESH_COMMAND_FORMAT = "terragrunt refresh -input=false %s %s";
  public static final String TERRAGRUNT_APPLY_COMMAND_FORMAT = "terragrunt apply -input=false tfplan";
  public static final String TERRAGRUNT_APPLY_COMMAND_FORMAT_WITH_PLAN_INPUT = "terragrunt apply -input=false %s %s";
  public static final String TERRAGRUNT_PLAN_COMMAND_FORMAT = "terragrunt plan -out=tfplan -input=false %s %s";
  public static final String TERRAGRUNT_PLAN_DESTROY_COMMAND_FORMAT =
      "terragrunt plan -destroy -out=tfdestroyplan -input=false %s %s";
  public static final String TERRAGRUNT_DESTROY_COMMAND_FORMAT =
      "terragrunt destroy %s --terragrunt-non-interactive %s %s";
  public static final String TERRAGRUNT_SHOW_COMMAND_FORMAT = "terragrunt show %s";
  public static final String TERRAGRUNT_RUN_ALL_APPLY_COMMAND_FORMAT =
      "terragrunt run-all apply -input=false --terragrunt-non-interactive %s %s";
  public static final String TERRAGRUNT_RUN_ALL_PLAN_COMMAND_FORMAT =
      "terragrunt run-all plan -out=tfplan -input=false %s %s";
  public static final String TERRAGRUNT_RUN_ALL_PLAN_DESTROY_COMMAND_FORMAT =
      "terragrunt run-all plan -destroy -out=tfdestroyplan -input=false %s %s";
  public static final String TERRAGRUNT_RUN_ALL_DESTROY_COMMAND_FORMAT =
      "terragrunt run-all destroy %s --terragrunt-non-interactive %s %s";
  public static final String TERRAGRUNT_WORKSPACE_RUN_ALL_LIST_COMMAND_FORMAT = "terragrunt run-all workspace list";
  public static final String TERRAGRUNT_WORKSPACE_LIST_COMMAND_FORMAT = "terragrunt workspace list";
  public static final String TERRAGRUNT_WORKSPACE_NEW_COMMAND_FORMAT = "terragrunt workspace new %s %s";
  public static final String TERRAGRUNT_WORKSPACE_RUN_ALL_NEW_COMMAND_FORMAT = "terragrunt run-all workspace new %s %s";
  public static final String TERRAGRUNT_WORKSPACE_SELECT_COMMAND_FORMAT = "terragrunt workspace select %s";
  public static final String TERRAGRUNT_WORKSPACE_RUN_ALL_SELECT_COMMAND_FORMAT =
      "terragrunt run-all workspace select %s";
  public static final String TERRAGRUNT_RUN_ALL_REFRESH_COMMAND_FORMAT =
      "terragrunt run-all refresh -input=false %s %s";
  public static final String TERRAGRUNT_RUN_ALL_SHOW_COMMAND_FORMAT = "terragrunt run-all show %s";
  public static final String TERRAGRUNT_INFO_COMMAND = "terragrunt terragrunt-info";
  public static final String TERRAGRUNT_VERSION_COMMAND = "terragrunt --version";

  public static final String TG_BASE_DIR = "./terragrunt-working-dir/${ACCOUNT_ID}/${ENTITY_ID}";
  public static final String TG_SCRIPT_DIR = "terragrunt-script-repository";
  public static final String TERRAGRUNT_INTERNAL_CACHE_FOLDER = ".terragrunt-cache";
  public static final String TERRAGRUNT_LOCK_FILE_NAME = ".terraform.lock.hcl";
  public static final String PATH_TO_MODULE = "pathToModule";
  public static final String PROVISIONER_ID = "provisionerId";
  public static final String WORKSPACE = "workspace";
  public static final String TIMEOUT_MILLIS = "timeoutMillis";
  public static final String VAR_FILE_FORMAT = " -var-file=\"%s\" ";
  public static final String TARGET_FORMAT = " -target=\"%s\" ";

  public static final String FETCH_CONFIG_FILES = "Fetch Config Files";
  public static final String INIT = "Init";
  public static final String SELECT_WORKSPACE = "Select Workspace";
  public static final String PLAN = "Plan";
  public static final String APPLY = "Apply";
  public static final String DESTROY_PLAN = "Destroy Plan";
  public static final String DESTROY = "Destroy";
  public static final String WRAP_UP = "Wrap Up";
  public static final String SKIP_ROLLBACK = "skipRollback";

  public static final String FORCE_FLAG = "-force";
  public static final String TF_DEFAULT_BINARY_PATH = "terraform";

  public static final String TERRAGRUNT_INFO_TF_BINARY_JSON_PATH = "TerraformBinary";
  public static final String TERRAGRUNT_OUTPUT_COMMAND_FORMAT = "terragrunt output -json > %s";
  public static final String TERRAGRUNT_RUN_ALL_OUTPUT_COMMAND_FORMAT = "terragrunt run-all output --json > %s";

  private TerragruntConstants() {
    throw new UnsupportedOperationException();
  }
}
