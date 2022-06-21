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
  private TerragruntConstants() {
    throw new UnsupportedOperationException();
  }

  public static final String TERRAGRUNT_REFRESH_COMMAND_FORMAT = "terragrunt refresh -input=false %s %s";
  public static final String TERRAGRUNT_APPLY_COMMAND_FORMAT = "terragrunt apply -input=false tfplan";
  public static final String TERRAGRUNT_PLAN_COMMAND_FORMAT = "terragrunt plan -out=tfplan -input=false %s %s";
  public static final String TERRAGRUNT_PLAN_DESTROY_COMMAND_FORMAT =
      "terragrunt plan -destroy -out=tfdestroyplan -input=false %s %s";
  public static final String TERRAGRUNT_DESTROY_COMMAND_FORMAT =
      "terragrunt destroy -force --terragrunt-non-interactive %s %s";
  public static final String TERRAGRUNT_RUN_ALL_APPLY_COMMAND_FORMAT =
      "terragrunt run-all apply -input=false --terragrunt-non-interactive %s %s";
  public static final String TERRAGRUNT_RUN_ALL_PLAN_COMMAND_FORMAT =
      "terragrunt run-all plan -out=tfplan -input=false %s %s";
  public static final String TERRAGRUNT_RUN_ALL_PLAN_DESTROY_COMMAND_FORMAT =
      "terragrunt run-all plan -destroy -out=tfdestroyplan -input=false %s %s";
  public static final String TERRAGRUNT_RUN_ALL_DESTROY_COMMAND_FORMAT =
      "terragrunt run-all destroy -force --terragrunt-non-interactive %s %s";
  public static final String TERRAGRUNT_WORKSPACE_LIST_COMMAND_FORMAT = "terragrunt workspace list";
  public static final String TERRAGRUNT_RUN_ALL_REFRESH_COMMAND_FORMAT =
      "terragrunt run-all refresh -input=false %s %s";

  public static final String TG_BASE_DIR = "./terragrunt-working-dir/${ACCOUNT_ID}/${ENTITY_ID}";
  public static final String TG_SCRIPT_DIR = "terragrunt-script-repository";
  public static final String TERRAGRUNT_INTERNAL_CACHE_FOLDER = ".terragrunt-cache";
  public static final String TERRAGRUNT_LOCK_FILE_NAME = ".terraform.lock.hcl";
  public static final String PATH_TO_MODULE = "pathToModule";
  public static final String PROVISIONER_ID = "provisionerId";
  public static final String WORKSPACE = "workspace";
  public static final String TIMEOUT_MILLIS = "timeoutMillis";

  public static final String FETCH_CONFIG_FILES = "Fetch Config Files";
  public static final String INIT = "Init";
  public static final String SELECT_WORKSPACE = "Select Workspace";
  public static final String PLAN = "Plan";
  public static final String APPLY = "Apply";
  public static final String DESTROY_PLAN = "Destroy Plan";
  public static final String DESTROY = "Destroy";
  public static final String WRAP_UP = "Wrap Up";
  public static final String SKIP_ROLLBACK = "skipRollback";
}
