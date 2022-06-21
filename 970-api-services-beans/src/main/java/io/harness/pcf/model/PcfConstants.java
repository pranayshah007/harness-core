/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public interface PcfConstants {
  char PATH_DELIMITER = '/';
  String DELIMITER = "__";
  String REPOSITORY_DIR_PATH = "./repository";
  String PCF_ARTIFACT_DOWNLOAD_DIR_PATH = "./repository/pcfartifacts";
  String PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX = "PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX: ";
  String PIVOTAL_CLOUD_FOUNDRY_CLIENT_EXCEPTION = "Pivotal Client Exception: ";
  String CUSTOM_SOURCE_MANIFESTS = "/extractedCustomSourceManifests-";
  String CF_HOME = "CF_HOME";
  String CF_USERNAME = "CF_USERNAME";
  String CF_PASSWORD = "CF_PASSWORD";
  String CF_DOCKER_CREDENTIALS = "CF_DOCKER_PASSWORD";
  String CF_PLUGIN_HOME = "CF_PLUGIN_HOME";
  String SYS_VAR_CF_PLUGIN_HOME = "harness.pcf.plugin.home";
  String BIN_BASH = "/bin/bash";
  String AUTOSCALING_APPS_PLUGIN_NAME = "autoscaling-apps";
  String APP_TOKEN = "<APP_NAME>";
  String ENABLE_AUTOSCALING = "enable-autoscaling";
  String DISABLE_AUTOSCALING = "disable-autoscaling";
  String CONFIGURE_AUTOSCALING = "cf configure-autoscaling";
  String DEFAULT_CF_CLI_INSTALLATION_PATH = "cf";
  String PATH_SYSTEM_VARIABLE_STR = "PATH";

  String MANIFEST_YML = "manifest.yml";
  String VARS_YML = "vars.yml";

  String APPLICATION_YML_ELEMENT = "applications";
  String NAME_MANIFEST_YML_ELEMENT = "name";
  String MEMORY_MANIFEST_YML_ELEMENT = "memory";
  String INSTANCE_MANIFEST_YML_ELEMENT = "instances";
  String METADATA_MANIFEST_YML_ELEMENT = "metadata";
  String CREATE_SERVICE_MANIFEST_ELEMENT = "create-services";
  String PATH_MANIFEST_YML_ELEMENT = "path";
  String ROUTES_MANIFEST_YML_ELEMENT = "routes";
  String SIDE_CARS_MANIFEST_YML_ELEMENT = "sidecars";
  String ROUTE_MANIFEST_YML_ELEMENT = "route";
  String NO_ROUTE_MANIFEST_YML_ELEMENT = "no-route";
  String ROUTE_PLACEHOLDER_TOKEN_DEPRECATED = "${ROUTE_MAP}";
  String INSTANCE_PLACEHOLDER_TOKEN_DEPRECATED = "${INSTANCE_COUNT}";
  String RANDOM_ROUTE_MANIFEST_YML_ELEMENT = "random-route";
  String HOST_MANIFEST_YML_ELEMENT = "host";

  String BUILDPACK_MANIFEST_YML_ELEMENT = "buildpack";
  String BUILDPACKS_MANIFEST_YML_ELEMENT = "buildpacks";
  String PROCESSES_MANIFEST_YML_ELEMENT = "processes";
  String COMMAND_MANIFEST_YML_ELEMENT = "command";
  String DISK_QUOTA_MANIFEST_YML_ELEMENT = "disk_quota";
  String DOCKER_MANIFEST_YML_ELEMENT = "docker";
  String IMAGE_MANIFEST_YML_ELEMENT = "image";
  String USERNAME_MANIFEST_YML_ELEMENT = "username";
  String DOMAINS_MANIFEST_YML_ELEMENT = "domains";
  String DOMAIN_MANIFEST_YML_ELEMENT = "domain";
  String ENV_MANIFEST_YML_ELEMENT = "env";
  String HEALTH_CHECK_HTTP_ENDPOINT_MANIFEST_YML_ELEMENT = "health-check-http-endpoint";
  String HEALTH_CHECK_INVOCATION_TIMEOUT_MANIFEST_YML_ELEMENT = "health-check-invocation-timeout";
  String HEALTH_CHECK_TYPE_MANIFEST_YML_ELEMENT = "health-check-type";
  String HOSTS_MANIFEST_YML_ELEMENT = "hosts";
  String NO_HOSTNAME_MANIFEST_YML_ELEMENT = "no-hostname";
  String SERVICES_MANIFEST_YML_ELEMENT = "services";
  String STACK_MANIFEST_YML_ELEMENT = "stack";
  String TIMEOUT_MANIFEST_YML_ELEMENT = "timeout";
  String ROUTE_PATH_MANIFEST_YML_ELEMENT = "route-path";
  String INFRA_ROUTE = "${infra.route}";
  String PCF_INFRA_ROUTE = "${infra.pcf.route}";

  String LEGACY_NAME_PCF_MANIFEST = "${APPLICATION_NAME}";

  String CONTEXT_NEW_APP_NAME_EXPR = "pcf.newAppName";
  String CONTEXT_NEW_APP_GUID_EXPR = "pcf.newAppGuid";
  String CONTEXT_NEW_APP_ROUTES_EXPR = "pcf.newAppRoutes";

  String CONTEXT_OLD_APP_NAME_EXPR = "pcf.oldAppName";
  String CONTEXT_OLD_APP_GUID_EXPR = "pcf.oldAppGuid";
  String CONTEXT_OLD_APP_ROUTES_EXPR = "pcf.oldAppRoutes";

  String CONTEXT_ACTIVE_APP_NAME_EXPR = "pcf.activeAppName";
  String CONTEXT_INACTIVE_APP_NAME_EXPR = "pcf.inActiveAppName";

  String CONTEXT_APP_FINAL_ROUTES_EXPR = "pcf.finalRoutes";
  String CONTEXT_APP_TEMP_ROUTES_EXPR = "pcf.tempRoutes";
  String PCF_CONFIG_FILE_EXTENSION = ".yml";
  String PCF_ROUTE_PATH_SEPARATOR = "/";
  char PCF_ROUTE_PORT_SEPARATOR = ':';
  char PCF_ROUTE_PATH_SEPARATOR_CHAR = '/';

  String FILE_START_SERVICE_MANIFEST_REGEX = "\\$\\{service\\.manifest}";
  String FILE_START_REPO_ROOT_REGEX = "\\$\\{service\\.manifest\\.repoRoot}";

  String PCF_AUTOSCALAR_MANIFEST_INSTANCE_LIMITS_ELE = "instance_limits";
  String PCF_AUTOSCALAR_MANIFEST_RULES_ELE = "rules";
  long THREAD_SLEEP_INTERVAL_FOR_STEADY_STATE_CHECK = 20000l;

  int MANIFEST_INSTANCE_COUNT_DEFAULT = 2;
  int DEFAULT_PCF_TASK_TIMEOUT_MIN = 30;
  // not removing this variable for backward compatibility
  String HARNESS__STATUS__INDENTIFIER = "HARNESS__STATUS__INDENTIFIER";
  String HARNESS__STATUS__IDENTIFIER = "HARNESS__STATUS__IDENTIFIER";
  String HARNESS__ACTIVE__IDENTIFIER = "ACTIVE";
  String HARNESS__STAGE__IDENTIFIER = "STAGE";

  String HARNESS__INACTIVE__IDENTIFIER = "INACTIVE";

  String PCF_CONNECTIVITY_SUCCESS = "SUCCESS";
  String INTERIM = "interim";
  String INTERIM_APP_NAME_SUFFIX = DELIMITER + INTERIM;
  String INACTIVE_APP_NAME_SUFFIX = DELIMITER + HARNESS__INACTIVE__IDENTIFIER;

  String INVALID_MANIFEST_MESSAGE = "Application Manifest cannot be blank or contain invalid characters";
  String MULTIPLE_APPLICATION_MANIFEST_MESSAGE = "Multiple application manifest files found at %s level";
  String MULTIPLE_AUTOSCALAR_MANIFEST_MESSAGE = "Multiple autoscalar manifest files found at %s level";

  static boolean isInterimApp(String applicationName) {
    return isNotEmpty(applicationName) && applicationName.endsWith(INTERIM_APP_NAME_SUFFIX);
  }

  static String generateInterimAppName(String appNamePrefix) {
    return appNamePrefix + INTERIM_APP_NAME_SUFFIX;
  }
}
