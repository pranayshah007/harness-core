/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.commonconstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ContainerExecutionConstants {
  public static final String ACCOUNT_ID_ATTR = "accountID";
  public static final String ORG_ID_ATTR = "orgID";
  public static final String PROJECT_ID_ATTR = "projectID";
  public static final String PIPELINE_ID_ATTR = "pipelineID";
  public static final String PIPELINE_EXECUTION_ID_ATTR = "pipelineExecutionID";
  public static final String STEP_VOLUME = "harness";
  public static final String STEP_MOUNT_PATH = "/harness";
  public static final String OSX_STEP_MOUNT_PATH = "/tmp/harness";
  public static final String OSX_ADDON_MOUNT_PATH = "/tmp/addon";
  public static final String STEP_WORK_DIR = STEP_MOUNT_PATH;
  public static final int POD_MAX_WAIT_UNTIL_READY_SECS = 8 * 60;
  public static final Integer STEP_REQUEST_MEMORY_MIB = 10;
  public static final Integer STEP_REQUEST_MILLI_CPU = 10;
  public static final Integer PORT_STARTING_RANGE = 20002;
  public static final Integer LITE_ENGINE_PORT = 20001;
  public static final String PLUGIN_ENV_PREFIX = "PLUGIN_";
  public static final String PLUGIN_PIPELINE = "PLUGIN_PIPELINE";
  public static final String SERVICE_PREFIX = "service-";
  public static final String STEP_PREFIX = "step-";
  public static final String SHARED_VOLUME_PREFIX = "shared-";
  public static final String VOLUME_PREFIX = "volume-";

  // Container constants for setting up addon binary
  public static final String SETUP_ADDON_CONTAINER_NAME = "setup-addon";
  public static final String UNIX_SETUP_ADDON_ARGS =
      "mkdir -p /addon/bin; mkdir -p /addon/tmp; chmod -R 776 /addon/tmp; if [ -e /usr/local/bin/ci-addon-linux-amd64 ];then cp /usr/local/bin/ci-addon-linux-amd64 /addon/bin/ci-addon;else cp /usr/local/bin/ci-addon-linux /addon/bin/ci-addon;fi; chmod +x /addon/bin/ci-addon; cp /usr/local/bin/tmate /addon/bin/tmate; chmod +x /addon/bin/tmate; cp /usr/local/bin/java-agent.jar /addon/bin/java-agent.jar; chmod +x /addon/bin/java-agent.jar; if [ -e /usr/local/bin/split_tests ];then cp /usr/local/bin/split_tests /addon/bin/split_tests; chmod +x /addon/bin/split_tests; export PATH=$PATH:/addon/bin; fi;";

  public static final String WIN_SETUP_ADDON_ARGS =
      "mkdir /addon/bin; mkdir /addon/tmp; cp C:/addon.exe /addon/bin/addon.exe; If (Test-Path -Path C:/split_tests.exe ) {cp C:/split_tests.exe /addon/bin/split_tests.exe}";

  public static final String ADDON_VOLUME = "addon";
  public static final String ADDON_VOL_MOUNT_PATH = "/addon";

  public static final Integer LITE_ENGINE_CONTAINER_MEM = 100;
  public static final Integer DEFAULT_CONTAINER_MEM_POV = 3000;
  public static final Integer DEFAULT_CONTAINER_CPU_POV = 1000;
  public static final Integer LITE_ENGINE_CONTAINER_CPU = 100;
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

  // These are environment variables to be set on the pod for talking to the log service.
  public static final String LOG_SERVICE_TOKEN_VARIABLE = "HARNESS_LOG_SERVICE_TOKEN";
  public static final String LOG_SERVICE_ENDPOINT_VARIABLE = "HARNESS_LOG_SERVICE_ENDPOINT";
  public static final String LABEL_REGEX = "^[a-z0-9A-Z][a-z0-9A-Z\\-_.]*[a-z0-9A-Z]$";
  // Deprecated
  public static final List<String> SH_COMMAND = Collections.unmodifiableList(Arrays.asList("sh", "-c", "--"));
  public static final List<String> PWSH_COMMAND = Collections.unmodifiableList(Arrays.asList("pwsh", "-Command"));

  // All FFs go here
  public static final String HARNESS_CI_INDIRECT_LOG_UPLOAD_FF = "HARNESS_CI_INDIRECT_LOG_UPLOAD_FF";

  public static final String HARNESS_LE_STATUS_REST_ENABLED = "HARNESS_LE_STATUS_REST_ENABLED";
  public static final String IMAGE_PATH_SPLIT_REGEX = ":";
  public static final String PORT_PREFIX = "--port";
  public static final String UNIX_STEP_COMMAND = "/addon/bin/ci-addon";
  public static final String WIN_STEP_COMMAND = "C:\\addon\\bin\\addon.exe";
  public static final String TMP_PATH = "/addon/tmp/";

  public static final String GOLANG_CACHE_ENV_NAME = "GOCACHE";
  public static final String GOLANG_CACHE_DIR = "/harness/.go/";
  public static final String GRADLE_CACHE_ENV_NAME = "GRADLE_USER_HOME";
  public static final String GRADLE_CACHE_DIR = "/harness/.gradle/";

  public static final String MEMORY = "MEMORY";
  public static final String CPU = "CPU";
}
