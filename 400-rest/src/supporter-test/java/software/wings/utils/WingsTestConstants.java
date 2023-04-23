/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.checker.StaticLimitCheckerWithDecrement;
import io.harness.limits.lib.StaticLimit;

import lombok.AllArgsConstructor;
import lombok.Value;

@Deprecated // Do not create generic constant buckets
public interface WingsTestConstants {
  String APP_ID = "APP_ID";

  String PROJECT_ID = "PROJECT_ID";

  String IMAGE_NAME = "IMAGE_NAME";

  String REGISTRY_HOST = "REGISTRY_HOST";

  String REPO_NAME = "REPO_NAME";

  String S3_URI = "s3://cdng-terraform-state/env/";

  String REPOSITORY_FORMAT = "REPOSITORY_FORMAT";

  String REPOSITORY_NAME = "REPOSITORY_NAME";

  String TARGET_APP_ID = "TARGET_APP_ID";

  String APP_NAME = "APP_NAME";

  String APPLICATION = "APPLICATION";

  String APPLICATION_URL = "APPLICATION_URL";

  String SERVICE = "SERVICE";

  String SERVICE_ID = "SERVICE_ID";

  String SERVICE1_ID = "SERVICE1_ID";

  String SERVICE2_ID = "SERVICE2_ID";

  String SERVICE3_ID = "SERVICE3_ID";

  String SERVICE4_ID = "SERVICE4_ID";

  String SERVICE5_ID = "SERVICE5_ID";

  String SERVICE6_ID = "SERVICE6_ID";

  String SERVICE7_ID = "SERVICE7_ID";

  String SERVICE_TEMPLATE_ID = "SERVICE_TEMPLATE_ID";

  String PROVISIONER_ID = "PROVISIONER_ID";

  String SERVICE_COMMAND_ID = "SERVICE_COMMAND_ID";

  String TARGET_SERVICE_ID = "TARGET_SERVICE_ID";

  String SERVICE_ID_CHANGED = "SERVICE_ID_CHANGED";

  String SERVICE_NAME = "SERVICE_NAME";

  String SERVICE_NAME_PREFIX = "SERVICE_NAME__";

  String SERVICE_URL = "SERVICE_URL";

  String INFRA_NAME = "INFRA_NAME";

  String PROVISIONER_NAME = "PROVISIONER_NAME";

  String ENV_ID = "ENV_ID";

  String ENV_ID_CHANGED = "ENV_ID_CHANGED";

  String ENV_NAME = "ENV_NAME";

  String ENV_PROD_FIELD = "PROD";

  String ENVIRONMENT = "ENVIRONMENT";

  String ENVIRONMENT_URL = "ENVIRONMENT_URL";

  String ENV_DESCRIPTION = "ENV_DESCRIPTION";

  String ARTIFACT_ID = "ARTIFACT_ID";

  String ARTIFACT_NAME = "ARTIFACT_DISPLAY_NAME";

  String ARTIFACT_STREAM_ID = "ARTIFACT_STREAM_ID";

  String ARTIFACT_STREAM_NAME = "ARTIFACT_STREAM_NAME";

  String ARTIFACT_SOURCE_NAME = "ARTIFACT_SOURCE_NAME";

  String BUILD_JOB_NAME = "BUILD_JOB_NAME";

  String BUILD_NO = "BUILD_NO";

  String ARTIFACT_GROUP_ID = "ARTIFACT_GROUP_ID";

  String STATE_EXECUTION_ID = "STATE_EXECUTION_ID";

  String SERVICE_INSTANCE_ID = "SERVICE_INSTANCE_ID";

  String HOST_ID = "HOST_ID";

  String HOST_NAME = "HOST_NAME";

  String TEMPLATE_ID = "TEMPLATE_ID";

  String TEMPLATE_FILE_PATH = "TEMPLATE_FILE_PATH";

  String TEMPLATE_BODY = "TEMPLATE_BODY";

  String TEMPLATE_NAME = "TEMPLATE_NAME";

  String TEMPLATE_DESCRIPTION = "TEMPLATE_DESCRIPTION";

  String TEMPLATE_VERSION = "LATEST";

  String FILE_NAME = "FILE_NAME";

  String FILE_PATH = "FILE_PATH";

  String FILE_ID = "FILE_ID";

  String SSH_USER_NAME = "SSH_USER_NAME";

  char[] SSH_USER_PASSWORD = "SSH_USER_PASSWORD".toCharArray();

  char[] SSH_KEY = "SSH_KEY".toCharArray();

  String DELEGATE_ID = "DELEGATE_ID";

  String ACTIVITY_ID = "ACTIVITY_ID";

  String STATE_MACHINE_ID = "STATE_MACHINE_ID";

  String WORKFLOW_ID = "WORKFLOW_ID";

  String WORKFLOW_NAME = "WORKFLOW_NAME";

  String WORKFLOW_URL = "WORKFLOW_URL";

  Integer DEFAULT_VERSION = 1000;

  String PIPELINE = "PIPELINE";

  String PIPELINE_ID = "PIPELINE_ID";

  String CLONED_PIPELINE_ID = "CLONED_PIPELINE_ID";

  String ORIGINAL_EXECUTION_ID = "ORIGINAL_EXECUTION_ID";

  String PIPELINE_NAME = "PIPELINE_NAME";

  String PIPELINE_URL = "PIPELINE_URL";

  String PIPELINE_STAGE_ELEMENT_ID = "PIPELINE_STAGE_ELEMENT_ID";

  String PIPELINE_EXECUTION_ID = "PIPELINE_EXECUTION_ID";

  String WORKFLOW_EXECUTION_ID = "WORKFLOW_EXECUTION_ID";

  String PIPELINE_WORKFLOW_EXECUTION_ID = "PIPELINE_WORKFLOW_EXECUTION_ID";

  String JENKINS_URL = "JENKINS_URL";

  String AZURE_DEVOPS_URL = "https://dev.azure.com/ORG";

  String JOB_NAME = "JOB_NAME";

  String ARTIFACT_PATH = "ARTIFACT_PATH";

  String ARTIFACTS = "ARTIFACTS";

  String ARTIFACTS_NAME = "ARTIFACTS_NAME";

  String ARTIFACTS_URL = "ARTIFACTS_URL";

  String USER_NAME = "USER_NAME";

  String USER_NAME_2 = "USER_NAME_2";

  String USER1_NAME = "USER_NAME1";

  char[] USER_NAME_DECRYPTED = USER_NAME.toCharArray();

  String DOMAIN = "DOMAIN";

  String USER_EMAIL = "user@wings.software";

  String USER1_EMAIL = "user1@wings.software";

  String TEMPORARY_EMAIL = "user@mailinator.com";

  String INVALID_USER_EMAIL = "user@@non-existent.com";

  String COMPANY_NAME = "COMPANY_NAME";

  String ACCOUNT_NAME = "ACCOUNT_NAME";

  String ILLEGAL_ACCOUNT_NAME = "/@[]{} Inc.";

  char[] PASSWORD = "PASSWORD".toCharArray();

  char[] USER_PASSWORD = "USER_PASSWORD".toCharArray();

  String COMMAND_NAME = "COMMAND_NAME";

  String COMMAND_UNIT_NAME = "COMMAND_UNIT_NAME";

  String COMMAND_UNIT_TYPE = "COMMAND_UNIT_TYPE";

  String SETTING_ID = "SETTING_ID";

  String SPOTINST_SETTING_ID = "SPOTINST_SETTING_ID";

  String SETTING_NAME = "SETTING_NAME";

  String RUNTIME_PATH = "RUNTIME_PATH";

  String LOG_ID = "LOG_ID";

  String USER_ID = "USER_ID";

  String USER_ID_2 = "USER_ID_2";

  String USER1_ID = "USER1_ID";

  String DASHBOARD1_ID = "DASHBOARD1_ID";

  String NEW_USER_EMAIL = "user1@harness.io";

  String NEW_USER_NAME = "user1";

  String NOTE = "sample note";

  String USER_INVITE_ID = "USER_INVITE_ID";

  String PORTAL_URL = "PORTAL_URL";

  String SUPPORT_EMAIL = "support@harness.io";

  String VERIFICATION_PATH = "VERIFICATION_PATH";

  String FREEMIUM_ENV_PATH = "GRATIS";

  String NOTIFICATION_ID = "NOTIFICATION_ID";

  String NOTIFICATION_GROUP_ID = "NOTIFICATION_GROUP_ID";

  String ROLE_NAME = "ROLE_NAME";

  String ROLE_ID = "ROLE_ID";

  String SERVICE_VARIABLE_ID = "SERVICE_VARIABLE_ID";

  String SERVICE_VARIABLE_NAME = "SERVICE_VARIABLE_NAME";

  String HOST_CONN_ATTR_ID = "HOST_CONN_ATTR_ID";

  String BASTION_CONN_ATTR_ID = "BASTION_CONN_ATTR_ID";

  String HOST_CONN_ATTR_KEY_ID = "HOST_CONN_ATTR_KEY_ID";

  String ACCOUNT_ID = "ACCOUNT_ID";

  String ACCOUNT1_ID = "ACCOUNT1_ID";

  String ACCOUNT_SERVICE = "ACCOUNT_SERVICE";

  String ACCOUNT_KEY = "ACCOUNT_KEY_ACCOUNT_KEY_ACCOUNT_"; // Account key must be 32 characters

  String KMS_ID = "KMS_ID";

  String ACCESS_KEY = "ACCESS_KEY";

  char[] SECRET_KEY = "SECRET_KEY".toCharArray();

  String ASSERTION = "ASSERTION";

  String NAMESPACE = "AWS/EC2";

  String METRIC_NAME = "CPUUtilization";

  String METRIC_DIMENSION = "METRIC_DIMENSION";

  String CLUSTER_NAME = "CLUSTER_NAME";

  String SERVICE_DEFINITION = "SERVICE_DEFINITION";

  String LAUNCHER_TEMPLATE_NAME = "LAUNCHER_TEMPLATE_NAME";

  String AUTO_SCALING_GROUP_NAME = "AUTO_SCALING_GROUP_NAME";

  String INFRA_MAPPING_ID = "INFRA_MAPPING_ID";

  String INFRA_DEFINITION_ID = "INFRA_DEFINITION_ID";

  String INFRA_DEFINITION_ID_CHANGED = "INFRA_DEFINITION_ID_CHANGED";

  String INFRA_MAPPING_ID_CHANGED = "INFRA_MAPPING_ID_CHANGED";

  String COMPUTE_PROVIDER_ID = "COMPUTE_PROVIDER_ID";

  String COMPUTE_PROVIDER_ID_CHANGED = "COMPUTE_PROVIDER_ID_CHANGED";

  String STATE_NAME = "STATE_NAME";

  String TASK_FAMILY = "TASK_FAMILY";

  Integer TASK_REVISION = 100;

  String ECS_SERVICE_NAME = "ECS_SERVICE_NAME";

  String PCF_SERVICE_NAME = "PCF_SERVICE_NAME";

  String PHASE_STEP = "PHASE_STEP";

  String PHASE_ID = "PHASE_ID";

  String TRIGGER_ID = "TRIGGER_ID";

  String TRIGGER_NAME = "TRIGGER_NAME";

  String TRIGGER_URL = "TRIGGER_URL";

  String RELEASE_NAME = "RELEASE_NAME";

  int TIMEOUT_INTERVAL = 1;

  long LONG_TIMEOUT_INTERVAL = 60 * 1000L;

  String DEPLOYMENT_TRIGGER_NAME = "DEPLOYMENT_TRIGGER_NAME";

  String TRIGGER_DESCRIPTION = "TRIGGER_DESCRIPTION";

  String ARTIFACT_FILTER = "ARTIFACT_FILTER";

  String ENTITY_TYPE_APP_DEFAULTS = "APP_DEFAULTS";

  String ENTITY_ID = "ENTITY_ID";

  String VARIABLE_NAME = "VARIABLE_NAME";

  String VARIABLE_VALUE = "VARIABLE_VALUE";

  String VERB = "VERB";

  String USER_GROUP_ID = "USER_GROUP_ID";

  String MANIFEST_ID = "MANIFEST_ID";

  String PERPETUAL_TASK_ID = "PERPETUAL_TASK_ID";

  int INTEGER_DEFAULT_VALUE = Integer.MAX_VALUE;

  long LONG_DEFAULT_VALUE = Long.MAX_VALUE;

  float FLOAT_DEFAULT_VALUE = Float.MAX_VALUE;

  double DOUBLE_DEFAULT_VALUE = Double.MAX_VALUE;

  String INVALID_NAME = "aba$$%55";

  String HARNESS_NEXUS = "Harness Nexus";
  String HARNESS_JENKINS = "Harness Jenkins";
  String HARNESS_NEXUS_THREE = "Harness Nexus 3";
  String HARNESS_ARTIFACTORY = "Harness Artifactory";
  String HARNESS_BAMBOO = "Harness Bamboo";
  String HARNESS_DOCKER_REGISTRY = "Harness Docker Registry";

  String BUCKET_NAME = "BUCKET_NAME";
  String ARTIFACT_FILE_NAME = "ARTIFACT_FILE_NAME";
  String S3_URL = "S3_URL";
  String DESTINATION_DIR_PATH = "DESTINATION_DIR_PATH";
  Long ARTIFACT_FILE_SIZE = Long.MAX_VALUE;

  String PUBLIC_DNS = "PUBLIC_DNS";
  String ARTIFACTORY_URL = "ARTIFACTORY_URL";
  String ARTIFACT_STREAM_ID_ARTIFACTORY = "ARTIFACT_STREAM_ID_ARTIFACTORY";

  String INTEGRATION_TEST_ACCOUNT_ID = "kmpySmUISimoRrJL6NL73w";

  String DELEGATE_NAME = "delegatename";
  String DELEGATE_PROFILE_ID = "delegateprofileid";
  String DELEGATE_GROUP_NAME = "delegateGroupName";

  String CV_CONFIG_ID = "CV_CONFIG_ID";
  String WHITELIST_ID = "WHITELIST_ID";

  String JIRA_CONNECTOR_ID = "JIRA_CONNECTOR_ID";
  String JIRA_ISSUE_ID = "JIRA_ISSUE_ID";
  String APPROVAL_EXECUTION_ID = "APPROVAL_EXECUTION_ID";

  String PHASE_VALIDATION_MESSAGE = "Some steps %s are found to be invalid/incomplete.";

  String PARENT = "parent";

  String REFERENCED_WORKFLOW = "Referenced Workflow";

  String BRANCH_NAME = "branchName";

  String BUILD_ID = "buildId";

  String INLINE_SPEC = "{ \"steps\": [ { \"name\": \"test\", \"args\": [ \"test\", \"test\" ] } ] }";

  String TAG_NAME = "tag";

  String COMMIT_SHA = "commitSha";

  String SOURCE_REPO_SETTINGS_ID = "SOURCE_REPO_SETTINGS_ID";
  String COMMIT_REFERENCE = "COMMIT_REFERENCE";
  String WORKSPACE = "WORKSPACE";
  String TERRAFORM_STATE_FILE_ID = "TERRAFORM_STATE_FILE_ID";
  String UUID = "UUID";
  String UUID1 = "UUID1";

  String HELM_CHART_ID = "HELM_CHART_ID";
  String FREEZE_WINDOW_ID = "FREEZE_WINDOW_ID";
  String APP_MANIFEST_NAME = "APP_MANIFEST_NAME";

  /**
   * The constant URL.
   */
  String URL = "url";
  String PATH = "path";
  String INFRA_TEMP_ROUTE = "infra.tempRoute";

  static StaticLimitCheckerWithDecrement mockChecker() {
    return new StaticLimitCheckerWithDecrement() {
      @Override
      public boolean checkAndConsume() {
        return true;
      }

      @Override
      public StaticLimit getLimit() {
        return new io.harness.limits.impl.model.StaticLimit(1000);
      }

      @Override
      public boolean decrement() {
        return true;
      }

      @Override
      public Action getAction() {
        return new Action("invalid-account", ActionType.CREATE_APPLICATION);
      }
    };
  }

  @Value
  @AllArgsConstructor
  class MockChecker implements StaticLimitCheckerWithDecrement {
    private final boolean allowRequest;
    private final ActionType actionType;
    private final StaticLimit limit = new io.harness.limits.impl.model.StaticLimit(1000);

    @Override
    public boolean checkAndConsume() {
      return allowRequest;
    }

    @Override
    public boolean decrement() {
      return true;
    }

    @Override
    public Action getAction() {
      return new Action("invalid-account", actionType);
    }
  }
}
