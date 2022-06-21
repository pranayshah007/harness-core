/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@OwnedBy(HarnessTeam.CI)
public class CIExecutionConstants {
  // Pod labels
  public static final String ACCOUNT_ID_ATTR = "accountID";
  public static final String ORG_ID_ATTR = "orgID";
  public static final String PROJECT_ID_ATTR = "projectID";
  public static final String PIPELINE_ID_ATTR = "pipelineID";
  public static final String PIPELINE_EXECUTION_ID_ATTR = "pipelineExecutionID";
  public static final String STAGE_ID_ATTR = "stageID";
  public static final String STAGE_NAME_ATTR = "stageName";
  public static final String BUILD_NUMBER_ATTR = "buildNumber";
  public static final String LABEL_REGEX = "^[a-z0-9A-Z][a-z0-9A-Z\\-_.]*[a-z0-9A-Z]$";

  // Pipeline constants
  public static final String CI_PIPELINE_CONFIG = "CI_PIPELINE_CONFIG";

  public static final String STEP_VOLUME = "harness";
  public static final String STEP_MOUNT_PATH = "/harness";
  public static final String OSX_STEP_MOUNT_PATH = "/tmp/harness";
  public static final String STEP_WORK_DIR = STEP_MOUNT_PATH;

  public static final int POD_MAX_WAIT_UNTIL_READY_SECS = 8 * 60;

  // Constants for implicit git clone step
  public static final String GIT_CLONE_STEP_ID = "harness-git-clone";
  public static final String GIT_CLONE_STEP_NAME = "Git clone";
  public static final Integer GIT_CLONE_MANUAL_DEPTH = 50;
  public static final String GIT_CLONE_DEPTH_ATTRIBUTE = "depth";
  public static final String PR_CLONE_STRATEGY_ATTRIBUTE = "PR_CLONE_STRATEGY";
  public static final String GIT_SSL_NO_VERIFY = "GIT_SSL_NO_VERIFY";
  public static final String GIT_URL_SUFFIX = ".git";
  public static final String PATH_SEPARATOR = "/";

  // Constant for
  public static final String UNIX_STEP_COMMAND = "/addon/bin/ci-addon";
  public static final String WIN_STEP_COMMAND = "C:\\addon\\bin\\addon.exe";
  public static final Integer STEP_REQUEST_MEMORY_MIB = 10;
  public static final Integer STEP_REQUEST_MILLI_CPU = 10;
  public static final Integer PORT_STARTING_RANGE = 20002;
  public static final Integer LITE_ENGINE_PORT = 20001;
  public static final String PLUGIN_ENV_PREFIX = "PLUGIN_";

  public static final String LOCALHOST_IP = "127.0.0.1";
  public static final String SERVICE_PREFIX = "service-";
  public static final String STEP_PREFIX = "step-";
  public static final String SHARED_VOLUME_PREFIX = "shared-";
  public static final String VOLUME_PREFIX = "volume-";

  // Container constants for setting up addon binary
  public static final String SETUP_ADDON_CONTAINER_NAME = "setup-addon";
  public static final String UNIX_SETUP_ADDON_ARGS =
      "mkdir -p /addon/bin; mkdir -p /addon/tmp; chmod -R 776 /addon/tmp; cp /usr/local/bin/ci-addon-linux-amd64 /addon/bin/ci-addon; chmod +x /addon/bin/ci-addon; cp /usr/local/bin/java-agent.jar /addon/bin/java-agent.jar; chmod +x /addon/bin/java-agent.jar";

  public static final String WIN_SETUP_ADDON_ARGS =
      "mkdir /addon/bin; mkdir /addon/tmp; cp C:/addon.exe /addon/bin/addon.exe";

  public static final String ADDON_VOLUME = "addon";
  public static final String ADDON_VOL_MOUNT_PATH = "/addon";
  // Lite engine container constants
  public static final String LITE_ENGINE_CONTAINER_NAME = "lite-engine";

  public static final Integer LITE_ENGINE_CONTAINER_MEM = 100;
  public static final Integer DEFAULT_CONTAINER_MEM_POV = 3000;
  public static final Integer LITE_ENGINE_CONTAINER_CPU = 100;
  public static final Integer DEFAULT_CONTAINER_CPU_POV = 1000;

  // entry point constants
  public static final String PORT_PREFIX = "--port";
  public static final String TMP_PATH_ARG_PREFIX = "--tmppath";
  public static final String TMP_PATH = "/addon/tmp/";
  public static final String SERVICE_ARG_COMMAND = "service";
  public static final String IMAGE_PREFIX = "--image";
  public static final String ID_PREFIX = "--id";
  public static final String GRPC_SERVICE_PORT_PREFIX = "--svc_ports";

  public static final String DEBUG_PREFIX = "--debug";

  public static final String ACCESS_KEY_MINIO_VARIABLE = "ACCESS_KEY_MINIO";
  public static final String SECRET_KEY_MINIO_VARIABLE = "SECRET_KEY_MINIO";

  // These are environment variables to be set on the pod for talking to the log service.
  public static final String LOG_SERVICE_TOKEN_VARIABLE = "HARNESS_LOG_SERVICE_TOKEN";
  public static final String LOG_SERVICE_ENDPOINT_VARIABLE = "HARNESS_LOG_SERVICE_ENDPOINT";

  // These are environment variables to be set on the pod for talking to the TI service.
  public static final String TI_SERVICE_ENDPOINT_VARIABLE = "HARNESS_TI_SERVICE_ENDPOINT";
  public static final String TI_SERVICE_TOKEN_VARIABLE = "HARNESS_TI_SERVICE_TOKEN";

  public static final String DELEGATE_SERVICE_ENDPOINT_VARIABLE = "DELEGATE_SERVICE_ENDPOINT";
  public static final String DELEGATE_SERVICE_ID_VARIABLE = "DELEGATE_SERVICE_ID";
  public static final String DELEGATE_SERVICE_ID_VARIABLE_VALUE = "delegate-grpc-service";

  public static final String HARNESS_ACCOUNT_ID_VARIABLE = "HARNESS_ACCOUNT_ID";
  public static final String HARNESS_PROJECT_ID_VARIABLE = "HARNESS_PROJECT_ID";
  public static final String HARNESS_ORG_ID_VARIABLE = "HARNESS_ORG_ID";
  public static final String HARNESS_BUILD_ID_VARIABLE = "HARNESS_BUILD_ID";
  public static final String HARNESS_STAGE_ID_VARIABLE = "HARNESS_STAGE_ID";
  public static final String HARNESS_EXECUTION_ID_VARIABLE = "HARNESS_EXECUTION_ID";
  public static final String HARNESS_LOG_PREFIX_VARIABLE = "HARNESS_LOG_PREFIX";
  public static final String HARNESS_SERVICE_LOG_KEY_VARIABLE = "HARNESS_SERVICE_LOG_KEY";
  public static final String HARNESS_PIPELINE_ID_VARIABLE = "HARNESS_PIPELINE_ID";

  public static final String HARNESS_SERVICE_ENTRYPOINT = "HARNESS_SERVICE_ENTRYPOINT";
  public static final String HARNESS_SERVICE_ARGS = "HARNESS_SERVICE_ARGS";

  public static final String HARNESS_WORKSPACE = "HARNESS_WORKSPACE";

  public static final String PLUGIN_USERNAME = "PLUGIN_USERNAME";
  public static final String PLUGIN_PASSW = "PLUGIN_PASSWORD";
  public static final String PLUGIN_REGISTRY = "PLUGIN_REGISTRY";
  public static final String PLUGIN_ACCESS_KEY = "PLUGIN_ACCESS_KEY";
  public static final String PLUGIN_SECRET_KEY = "PLUGIN_SECRET_KEY";
  public static final String PLUGIN_ASSUME_ROLE = "PLUGIN_ASSUME_ROLE";
  public static final String AWS_ROLE_ARN = "AWS_ROLE_ARN";
  public static final String PLUGIN_JSON_KEY = "PLUGIN_JSON_KEY";
  public static final String PLUGIN_URL = "PLUGIN_URL";

  // All FFs go here
  public static final String HARNESS_CI_INDIRECT_LOG_UPLOAD_FF = "HARNESS_CI_INDIRECT_LOG_UPLOAD_FF";

  // Deprecated
  public static final List<String> SH_COMMAND = Collections.unmodifiableList(Arrays.asList("sh", "-c", "--"));
  public static final List<String> PWSH_COMMAND = Collections.unmodifiableList(Arrays.asList("pwsh", "-Command"));

  public static final String IMAGE_PATH_SPLIT_REGEX = ":";
  public static final String PVC_DEFAULT_STORAGE_CLASS = "faster";

  public static final String AWS_CODE_COMMIT_URL_REGEX =
      "^https://git-codecommit\\.([^/.]*)\\.amazonaws\\.com/v1/repos(?:/?|/[^/.]*)$";

  public static final String PLUGIN_ARTIFACT_FILE_VALUE = "/addon/tmp/.plugin/artifact";

  public static final String AZURE_REPO_GIT_LABEL = "/_git/";
  public static final String AZURE_REPO_BASE_URL = "azure.com";
}
