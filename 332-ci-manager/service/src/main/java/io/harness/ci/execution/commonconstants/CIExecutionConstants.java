/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.commonconstants;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CI)
public class CIExecutionConstants extends ContainerExecutionConstants {
  // Pod labels
  public static final String STAGE_ID_ATTR = "stageID";
  public static final String STAGE_RUNTIME_ID_ATTR = "stageRuntimeID";

  public static final String STAGE_NAME_ATTR = "stageName";
  public static final String BUILD_NUMBER_ATTR = "buildNumber";
  public static final String ETC_DIR = "/etc";

  // Constants for implicit git clone step
  public static final String GIT_CLONE_STEP_ID = "harness-git-clone";
  public static final String GIT_CLONE_STEP_NAME = "Clone codebase";
  public static final Integer GIT_CLONE_MANUAL_DEPTH = 50;
  public static final String GIT_CLONE_DEPTH_ATTRIBUTE = "depth";
  public static final String PR_CLONE_STRATEGY_ATTRIBUTE = "PR_CLONE_STRATEGY";
  public static final String GIT_SSL_NO_VERIFY = "GIT_SSL_NO_VERIFY";
  public static final String GIT_URL_SUFFIX = ".git";
  public static final String PATH_SEPARATOR = "/";
  public static final String UNDERSCORE_SEPARATOR = "_";

  // Constants for implict cache steps
  public static final String RESTORE_CACHE_STEP_ID = "restore-cache-harness";
  public static final String SAVE_CACHE_STEP_ID = "save-cache-harness";
  public static final String RESTORE_CACHE_STEP_NAME = "Restore Cache From Harness";
  public static final String SAVE_CACHE_STEP_NAME = "Save Cache to Harness";
  public static final String CACHE_ARCHIVE_TYPE_TAR = "tar";
  public static final String CACHE_GCS_BACKEND = "gcs";
  public static final String CACHE_S3_BACKEND = "s3";

  // Constant for

  public static final String LOCALHOST_IP = "127.0.0.1";
  public static final Integer MAXIMUM_EXPANSION_LIMIT = 5000;
  public static final Integer MAXIMUM_EXPANSION_LIMIT_FREE_ACCOUNT = 5;

  // entry point constants
  public static final String TMP_PATH_ARG_PREFIX = "--tmppath";
  public static final String SERVICE_ARG_COMMAND = "service";
  public static final String IMAGE_PREFIX = "--image";
  public static final String ID_PREFIX = "--id";
  public static final String GRPC_SERVICE_PORT_PREFIX = "--svc_ports";

  public static final String DEBUG_PREFIX = "--debug";

  public static final String ACCESS_KEY_MINIO_VARIABLE = "ACCESS_KEY_MINIO";
  public static final String SECRET_KEY_MINIO_VARIABLE = "SECRET_KEY_MINIO";

  // These are environment variables to be set on the pod for talking to the TI service.
  public static final String TI_SERVICE_ENDPOINT_VARIABLE = "HARNESS_TI_SERVICE_ENDPOINT";
  public static final String TI_SERVICE_TOKEN_VARIABLE = "HARNESS_TI_SERVICE_TOKEN";
  public static final String DRONE_WORKSPACE = "DRONE_WORKSPACE";

  public static final String PLUGIN_USERNAME = "PLUGIN_USERNAME";
  public static final String PLUGIN_PASSW = "PLUGIN_PASSWORD";
  public static final String PLUGIN_REGISTRY = "PLUGIN_REGISTRY";
  public static final String PLUGIN_ACCESS_KEY = "PLUGIN_ACCESS_KEY";
  public static final String PLUGIN_SECRET_KEY = "PLUGIN_SECRET_KEY";

  public static final String CLIENT_SECRET = "CLIENT_SECRET";
  public static final String TENANT_ID = "TENANT_ID";
  public static final String CLIENT_CERTIFICATE = "CLIENT_CERTIFICATE";
  public static final String CLIENT_ID = "CLIENT_ID";
  public static final String PLUGIN_ASSUME_ROLE = "PLUGIN_ASSUME_ROLE";
  public static final String PLUGIN_EXTERNAL_ID = "PLUGIN_EXTERNAL_ID";

  public static final String AWS_ROLE_ARN = "AWS_ROLE_ARN";
  public static final String PLUGIN_JSON_KEY = "PLUGIN_JSON_KEY";
  public static final String PLUGIN_URL = "PLUGIN_URL";

  public static final String PVC_DEFAULT_STORAGE_CLASS = "faster";
  public static final String AWS_CODE_COMMIT_URL_REGEX =
      "^https://git-codecommit\\.([^/.]*)\\.amazonaws\\.com/v1/repos(?:/?|/[^/.]*)$";
  public static final String PLUGIN_ARTIFACT_FILE_VALUE = "/addon/tmp/.plugin/artifact";
  public static final String AZURE_REPO_BASE_URL = "azure.com";
  public static final Double MACOS_BUILD_MULTIPLIER = 10.0;
  public static final Double WINDOWS_BUILD_MULTIPLIER = 2.0;
  public static final Double DEFAULT_BUILD_MULTIPLIER = 1.0;

  public static final String NULL_STR = "null";

  public static final String BASE_AZURE_HOSTNAME = "azurecr.io";
  public static final String BASE_GCR_HOSTNAME = "gcr.io";
  public static final String BASE_ECR_HOSTNAME = "amazonaws.com";
  public static final String HTTPS_URL = "https://";

  public static final String DOCKER_REGISTRY_V2 = "https://index.docker.io/v2/";
  public static final String DOCKER_REGISTRY_V1 = "https://index.docker.io/v1/";
  public static final String STACK_ID = "STACK_ID";
  public static final String WORKFLOW = "WORKFLOW";
  public static final String INITIALISE = "initialise";
  public static final String EVALUATE = "evaluate";
  public static final String EXECUTE = "execute";
  public static final String TEARDOWN = "teardown";
}
