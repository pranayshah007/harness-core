/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.provision;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public final class TerraformConstants {
  private TerraformConstants() {}

  public static final String RUN_PLAN_ONLY_KEY = "runPlanOnly";
  public static final String INHERIT_APPROVED_PLAN = "inheritApprovedPlan";

  public static final String VARIABLES_KEY = "variables";
  public static final String BACKEND_CONFIGS_KEY = "backend_configs";
  public static final String BACKEND_CONFIG_KEY = "backend_config";

  public static final String ENVIRONMENT_VARS_KEY = "environment_variables";
  public static final String TARGETS_KEY = "targets";
  public static final String TF_VAR_FILES_KEY = "tf_var_files";
  public static final String TF_VAR_FILES_GIT_CONNECTOR_ID_KEY = "tf_var_files_git_connector_id";
  public static final String TF_VAR_FILES_GIT_COMMIT_ID_KEY = "tf_var_files_git_commit_id";
  public static final String TF_VAR_FILES_GIT_BRANCH_KEY = "tf_var_files_git_branch";
  public static final String TF_VAR_FILES_GIT_FILE_PATH_KEY = "tf_var_files_git_file_path";
  public static final String TF_VAR_FILES_GIT_REPO_NAME_KEY = "tf_var_files_git_repo_name";
  public static final String TF_VAR_FILES_GIT_USE_BRANCH_KEY = "tf_var_files_git_use_branch";

  public static final String REMOTE_BE_CONFIG_GIT_BRANCH_KEY = "remote_be_config_git_branch";
  public static final String REMOTE_BE_CONFIG_GIT_CONNECTOR_ID_KEY = "remote_be_config_git_connector_id";
  public static final String REMOTE_BE_CONFIG_GIT_COMMIT_ID_KEY = "remote_be_config_git_commit_id";
  public static final String REMOTE_BE_CONFIG_GIT_FILE_PATH_KEY = "remote_be_config_git_file_path";
  public static final String REMOTE_BE_CONFIG_GIT_REPO_NAME_KEY = "remote_be_config_git_repo_name";
  public static final String REMOTE_BE_CONFIG_GIT_USE_BRANCH_KEY = "remote_be_config_git_use_branch";

  public static final String REMOTE_BE_CONFIG_S3_CONFIG_ID_KEY = "remote_be_config_s3_config_id";

  public static final String REMOTE_BE_CONFIG_S3_URI_LIST_KEY = "remote_be_config_s3_uri_list";
  public static final String REMOTE_BE_CONFIG_S3_URI_KEY = "remote_be_config_s3_uri";
  public static final String TF_VAR_FILES_S3_CONFIG_ID_KEY = "remote_tf_var_files_s3_config_id";
  public static final String TF_VAR_FILES_S3_URI_KEY = "remote_tf_var_files_s3_uri";

  public static final String TF_VAR_FILES_S3_URI_LIST_KEY = "remote_tf_var_files_s3_uri_list";

  public static final String WORKSPACE_KEY = "tf_workspace";
  public static final String ENCRYPTED_VARIABLES_KEY = "encrypted_variables";
  public static final String ENCRYPTED_BACKEND_CONFIGS_KEY = "encrypted_backend_configs";
  public static final String ENCRYPTED_BACKEND_CONFIG_KEY = "encrypted_backend_config";
  public static final String ENCRYPTED_ENVIRONMENT_VARS_KEY = "encrypted_environment_variables";
  public static final String TF_NAME_PREFIX = "tfPlan_%s";
  public static final String TF_DESTROY_NAME_PREFIX = "tfDestroyPlan_%s";
  public static final String TF_APPLY_VAR_NAME = "terraformApply";
  public static final String TF_DESTROY_VAR_NAME = "terraformDestroy";
  public static final String QUALIFIER_APPLY = "apply";

  public static final String USER_DIR_KEY = "user.dir";
  public static final String TERRAFORM_STATE_FILE_NAME = "terraform.tfstate";
  public static final String WORKSPACE_DIR_BASE = "terraform.tfstate.d";
  public static final String WORKSPACE_STATE_FILE_PATH_FORMAT = WORKSPACE_DIR_BASE + "/%s/terraform.tfstate";
  public static final String WORKSPACE_PLAN_FILE_PATH_FORMAT = WORKSPACE_DIR_BASE + "/$WORKSPACE_NAME/tfplan";
  public static final String WORKSPACE_DESTROY_PLAN_FILE_PATH_FORMAT =
      WORKSPACE_DIR_BASE + "/$WORKSPACE_NAME/tfdestroyplan";
  public static final String TERRAFORM_PLAN_FILE_OUTPUT_NAME = "tfplan";
  public static final String TERRAFORM_HUMAN_READABLE_PLAN_FILE_OUTPUT_NAME = "tfHumanReadablePlan";
  public static final String TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME = "tfdestroyplan";
  public static final String TERRAFORM_DESTROY_HUMAN_READABLE_PLAN_FILE_OUTPUT_NAME = "tfDestroyHumanReadablePlan";
  public static final String TERRAFORM_APPLY_PLAN_FILE_VAR_NAME = "${terraformApply.tfplan}";
  public static final String TERRAFORM_HUMAN_READABLE_PLAN_FILE_VAR_NAME = "${terraformApply.tfHumanReadablePlan}";
  public static final String TERRAFORM_DESTROY_HUMAN_READABLE_PLAN_FILE_VAR_NAME =
      "${terraformDestroy.tfHumanReadablePlan}";
  public static final String PLAN_HUMAN_READABLE_FILE_PREFIX = "harnessTfHumanReadablePlan";
  public static final String PLAN_HUMAN_READABLE_FILE_NAME = "terraform.harnessTfHumanReadablePlan";
  public static final String PLAN_HUMAN_READABLE_TXT_FILE_NAME = "%s.txt";
  public static final String TERRAFORM_DESTROY_PLAN_FILE_VAR_NAME = "${terraformDestroy.tfplan}";
  public static final String TERRAFORM_PLAN_FILE_NAME = "terraform.tfplan";
  public static final String TERRAFORM_PLAN_JSON_FILE_NAME = "%s.json";
  public static final String TERRAFORM_VARIABLES_FILE_NAME = "terraform-%s.tfvars";
  public static final String TERRAFORM_CLOUD_VARIABLES_FILE_NAME = "terraform-%s.auto.tfvars";
  public static final String TERRAFORM_BACKEND_CONFIGS_FILE_NAME = "backend_configs-%s";
  public static final String TERRAFORM_INTERNAL_FOLDER = ".terraform";
  public static final long RESOURCE_READY_WAIT_TIME_SECONDS = 15;
  public static final String VAR_FILE_FORMAT = " -var-file=\"%s\" ";
  public static final String TF_BASE_DIR = "./terraform-working-dir/${ACCOUNT_ID}/${ENTITY_ID}";
  public static final String ACTIVITY_ID_BASED_TF_BASE_DIR =
      "./terraform-working-dir-v2/${ACCOUNT_ID}/${ENTITY_ID}/${ACTIVITY_ID}";
  public static final String TF_WORKING_DIR = "./terraform-working-dir/";
  public static final String TF_VAR_FILES_DIR = "tf-var-files";
  public static final String TF_SCRIPT_DIR = "script-repository";

  public static final String TF_BACKEND_CONFIG_DIR = "tf-be-config-files";

  public static final String COMMAND_UNIT = "Execute Terraform";
  public static final String DEFAULT_TIMEOUT = "10m";

  public static final String LOCAL_STORE_TYPE = "Local";
  public static final String REMOTE_STORE_TYPE = "Remote";

  public static final String S3_STORE_TYPE = "S3";

  public static final String TF_PLAN_RESOURCES_ADD = "TF_PLAN_RESOURCES_ADD";
  public static final String TF_PLAN_RESOURCES_CHANGE = "TF_PLAN_RESOURCES_CHANGE";
  public static final String TF_PLAN_RESOURCES_DESTROY = "TF_PLAN_RESOURCES_DESTROY";

  public static final String TF_S3_FILE_DIR_FOR_VARIABLES_AND_TARGETS =
      "./s3_bucket/${REPO_TYPE}/${ACCOUNT_ID}/${BUCKET_NAME}";

  public static final long SECONDS_TO_WAIT_FOR_GRACEFUL_SHUTDOWN = 30;
}
