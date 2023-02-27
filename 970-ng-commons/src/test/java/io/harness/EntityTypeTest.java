/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import io.harness.agent.sdk.HarnessAlwaysRun;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class EntityTypeTest extends CategoryTest {
  private Map<Integer, String> entityTypeOrdinalMapping;
  private Map<String, Integer> entityTypeConstantMapping;

  @Before
  public void setUp() {
    entityTypeOrdinalMapping = new HashMap<>();
    entityTypeOrdinalMapping.put(0, "GITOPS_CREATE_PR");
    entityTypeOrdinalMapping.put(1, "GITOPS_MERGE_PR");
    entityTypeOrdinalMapping.put(2, "PROJECTS");
    entityTypeOrdinalMapping.put(3, "PIPELINES");
    entityTypeOrdinalMapping.put(4, "PIPELINE_STEPS");
    entityTypeOrdinalMapping.put(5, "HTTP_STEP");
    entityTypeOrdinalMapping.put(6, "EMAIL_STEP");
    entityTypeOrdinalMapping.put(7, "JIRA_CREATE_STEP");
    entityTypeOrdinalMapping.put(8, "JIRA_UPDATE_STEP");
    entityTypeOrdinalMapping.put(9, "JIRA_APPROVAL_STEP");
    entityTypeOrdinalMapping.put(10, "HARNESS_APPROVAL_STEP");
    entityTypeOrdinalMapping.put(11, "CUSTOM_APPROVAL_STEP");
    entityTypeOrdinalMapping.put(12, "BARRIER_STEP");
    entityTypeOrdinalMapping.put(13, "QUEUE_STEP");
    entityTypeOrdinalMapping.put(14, "FLAG_CONFIGURATION");
    entityTypeOrdinalMapping.put(15, "SHELL_SCRIPT_STEP");
    entityTypeOrdinalMapping.put(16, "K8S_CANARY_DEPLOY_STEP");
    entityTypeOrdinalMapping.put(17, "K8S_APPLY_STEP");
    entityTypeOrdinalMapping.put(18, "K8S_BLUE_GREEN_DEPLOY_STEP");
    entityTypeOrdinalMapping.put(19, "K8S_ROLLING_DEPLOY_STEP");
    entityTypeOrdinalMapping.put(20, "K8S_ROLLING_ROLLBACK_STEP");
    entityTypeOrdinalMapping.put(21, "K8S_SCALE_STEP");
    entityTypeOrdinalMapping.put(22, "K8S_DELETE_STEP");
    entityTypeOrdinalMapping.put(23, "K8S_BG_SWAP_SERVICES_STEP");
    entityTypeOrdinalMapping.put(24, "K8S_CANARY_DELETE_STEP");
    entityTypeOrdinalMapping.put(25, "TERRAFORM_APPLY_STEP");
    entityTypeOrdinalMapping.put(26, "TERRAFORM_PLAN_STEP");
    entityTypeOrdinalMapping.put(27, "TERRAFORM_DESTROY_STEP");
    entityTypeOrdinalMapping.put(28, "TERRAFORM_ROLLBACK_STEP");
    entityTypeOrdinalMapping.put(29, "HELM_DEPLOY_STEP");
    entityTypeOrdinalMapping.put(30, "HELM_ROLLBACK_STEP");
    entityTypeOrdinalMapping.put(31, "CONNECTORS");
    entityTypeOrdinalMapping.put(32, "SECRETS");
    entityTypeOrdinalMapping.put(33, "FILES");
    entityTypeOrdinalMapping.put(34, "SERVICE");
    entityTypeOrdinalMapping.put(35, "ENVIRONMENT");
    entityTypeOrdinalMapping.put(36, "ENVIRONMENT_GROUP");
    entityTypeOrdinalMapping.put(37, "INPUT_SETS");
    entityTypeOrdinalMapping.put(38, "CV_CONFIG");
    entityTypeOrdinalMapping.put(39, "VERIFY_STEP");
    entityTypeOrdinalMapping.put(40, "DELEGATES");
    entityTypeOrdinalMapping.put(41, "DELEGATE_CONFIGURATIONS");
    entityTypeOrdinalMapping.put(42, "CV_VERIFICATION_JOB");
    entityTypeOrdinalMapping.put(43, "INTEGRATION_STAGE");
    entityTypeOrdinalMapping.put(44, "INTEGRATION_STEPS");
    entityTypeOrdinalMapping.put(45, "SECURITY_STAGE");
    entityTypeOrdinalMapping.put(46, "SECURITY_STEPS");
    entityTypeOrdinalMapping.put(47, "CV_KUBERNETES_ACTIVITY_SOURCE");
    entityTypeOrdinalMapping.put(48, "DEPLOYMENT_STEPS");
    entityTypeOrdinalMapping.put(49, "DEPLOYMENT_STAGE");
    entityTypeOrdinalMapping.put(50, "APPROVAL_STAGE");
    entityTypeOrdinalMapping.put(51, "PIPELINE_STAGE");
    entityTypeOrdinalMapping.put(52, "FEATURE_FLAG_STAGE");
    entityTypeOrdinalMapping.put(53, "TEMPLATE");
    entityTypeOrdinalMapping.put(54, "TEMPLATE_STAGE");
    entityTypeOrdinalMapping.put(55, "TEMPLATE_CUSTOM_DEPLOYMENT");
    entityTypeOrdinalMapping.put(56, "TRIGGERS");
    entityTypeOrdinalMapping.put(57, "MONITORED_SERVICE");
    entityTypeOrdinalMapping.put(58, "GIT_REPOSITORIES");
    entityTypeOrdinalMapping.put(59, "FEATURE_FLAGS");
    entityTypeOrdinalMapping.put(60, "SERVICENOW_APPROVAL_STEP");
    entityTypeOrdinalMapping.put(61, "SERVICENOW_CREATE_STEP");
    entityTypeOrdinalMapping.put(62, "SERVICENOW_UPDATE_STEP");
    entityTypeOrdinalMapping.put(63, "SERVICENOW_IMPORT_SET_STEP");
    entityTypeOrdinalMapping.put(64, "OPAPOLICIES");
    entityTypeOrdinalMapping.put(65, "POLICY_STEP");
    entityTypeOrdinalMapping.put(66, "RUN_STEP");
    entityTypeOrdinalMapping.put(67, "RUN_TEST");
    entityTypeOrdinalMapping.put(68, "PLUGIN");
    entityTypeOrdinalMapping.put(69, "RESTORE_CACHE_GCS");
    entityTypeOrdinalMapping.put(70, "RESTORE_CACHE_S3");
    entityTypeOrdinalMapping.put(71, "SAVE_CACHE_GCS");
    entityTypeOrdinalMapping.put(72, "SAVE_CACHE_S3");
    entityTypeOrdinalMapping.put(73, "SECURITY");
    entityTypeOrdinalMapping.put(74, "AQUA_TRIVY");
    entityTypeOrdinalMapping.put(75, "AWS_ECR");
    entityTypeOrdinalMapping.put(76, "BANDIT");
    entityTypeOrdinalMapping.put(77, "BLACKDUCK");
    entityTypeOrdinalMapping.put(78, "BRAKEMAN");
    entityTypeOrdinalMapping.put(79, "BURP");
    entityTypeOrdinalMapping.put(80, "CHECKMARX");
    entityTypeOrdinalMapping.put(81, "CLAIR");
    entityTypeOrdinalMapping.put(82, "DATA_THEOREM");
    entityTypeOrdinalMapping.put(83, "DOCKER_CONTENT_TRUST");
    entityTypeOrdinalMapping.put(84, "EXTERNAL");
    entityTypeOrdinalMapping.put(85, "FORTIFY_ON_DEMAND");
    entityTypeOrdinalMapping.put(86, "GRYPE");
    entityTypeOrdinalMapping.put(87, "JFROG_XRAY");
    entityTypeOrdinalMapping.put(88, "MEND");
    entityTypeOrdinalMapping.put(89, "METASPLOIT");
    entityTypeOrdinalMapping.put(90, "NESSUS");
    entityTypeOrdinalMapping.put(91, "NEXUS_IQ");
    entityTypeOrdinalMapping.put(92, "NIKTO");
    entityTypeOrdinalMapping.put(93, "NMAP");
    entityTypeOrdinalMapping.put(94, "OPENVAS");
    entityTypeOrdinalMapping.put(95, "OWASP");
    entityTypeOrdinalMapping.put(96, "PRISMA_CLOUD");
    entityTypeOrdinalMapping.put(97, "PROWLER");
    entityTypeOrdinalMapping.put(98, "QUALYS");
    entityTypeOrdinalMapping.put(99, "REAPSAW");
    entityTypeOrdinalMapping.put(100, "SHIFT_LEFT");
    entityTypeOrdinalMapping.put(101, "SNIPER");
    entityTypeOrdinalMapping.put(102, "SNYK");
    entityTypeOrdinalMapping.put(103, "SONARQUBE");
    entityTypeOrdinalMapping.put(104, "SYSDIG");
    entityTypeOrdinalMapping.put(105, "TENABLE");
    entityTypeOrdinalMapping.put(106, "VERACODE");
    entityTypeOrdinalMapping.put(107, "ZAP");
    entityTypeOrdinalMapping.put(108, "GIT_CLONE");
    entityTypeOrdinalMapping.put(109, "ARTIFACTORY_UPLOAD");
    entityTypeOrdinalMapping.put(110, "GCS_UPLOAD");
    entityTypeOrdinalMapping.put(111, "S3_UPLOAD");
    entityTypeOrdinalMapping.put(112, "BUILD_AND_PUSH_GCR");
    entityTypeOrdinalMapping.put(113, "BUILD_AND_PUSH_ECR");
    entityTypeOrdinalMapping.put(114, "BUILD_AND_PUSH_DOCKER_REGISTRY");
    entityTypeOrdinalMapping.put(115, "CLOUDFORMATION_CREATE_STACK_STEP");
    entityTypeOrdinalMapping.put(116, "CLOUDFORMATION_DELETE_STACK_STEP");
    entityTypeOrdinalMapping.put(117, "SERVERLESS_AWS_LAMBDA_DEPLOY_STEP");
    entityTypeOrdinalMapping.put(118, "SERVERLESS_AWS_LAMBDA_ROLLBACK_STEP");
    entityTypeOrdinalMapping.put(119, "CUSTOM_STAGE");
    entityTypeOrdinalMapping.put(120, "CLOUDFORMATION_ROLLBACK_STACK_STEP");
    entityTypeOrdinalMapping.put(121, "INFRASTRUCTURE");
    entityTypeOrdinalMapping.put(122, "COMMAND_STEP");
    entityTypeOrdinalMapping.put(123, "STRATEGY_NODE");
    entityTypeOrdinalMapping.put(124, "AZURE_SLOT_DEPLOYMENT_STEP");
    entityTypeOrdinalMapping.put(125, "AZURE_TRAFFIC_SHIFT_STEP");
    entityTypeOrdinalMapping.put(126, "FETCH_INSTANCE_SCRIPT_STEP");
    entityTypeOrdinalMapping.put(127, "AZURE_SWAP_SLOT_STEP");
    entityTypeOrdinalMapping.put(128, "AZURE_WEBAPP_ROLLBACK_STEP");
    entityTypeOrdinalMapping.put(129, "JENKINS_BUILD");
    entityTypeOrdinalMapping.put(130, "ECS_ROLLING_DEPLOY_STEP");
    entityTypeOrdinalMapping.put(131, "ECS_ROLLING_ROLLBACK_STEP");
    entityTypeOrdinalMapping.put(132, "ECS_CANARY_DEPLOY_STEP");
    entityTypeOrdinalMapping.put(133, "ECS_CANARY_DELETE_STEP");
    entityTypeOrdinalMapping.put(134, "AZURE_CREATE_ARM_RESOURCE_STEP");
    entityTypeOrdinalMapping.put(135, "BUILD_AND_PUSH_ACR");
    entityTypeOrdinalMapping.put(136, "AZURE_CREATE_BP_RESOURCE_STEP");
    entityTypeOrdinalMapping.put(137, "AZURE_ROLLBACK_ARM_RESOURCE_STEP");
    entityTypeOrdinalMapping.put(138, "BACKGROUND_STEP");
    entityTypeOrdinalMapping.put(139, "WAIT_STEP");
    entityTypeOrdinalMapping.put(140, "ARTIFACT_SOURCE_TEMPLATE");
    entityTypeOrdinalMapping.put(141, "ECS_BLUE_GREEN_CREATE_SERVICE_STEP");
    entityTypeOrdinalMapping.put(142, "ECS_BLUE_GREEN_SWAP_TARGET_GROUPS_STEP");
    entityTypeOrdinalMapping.put(143, "ECS_BLUE_GREEN_ROLLBACK_STEP");
    entityTypeOrdinalMapping.put(144, "SHELL_SCRIPT_PROVISION_STEP");
    entityTypeOrdinalMapping.put(145, "FREEZE");
    entityTypeOrdinalMapping.put(146, "GITOPS_UPDATE_RELEASE_REPO");
    entityTypeOrdinalMapping.put(147, "GITOPS_FETCH_LINKED_APPS");
    entityTypeOrdinalMapping.put(148, "ECS_RUN_TASK_STEP");
    entityTypeOrdinalMapping.put(149, "CHAOS_STEP");
    entityTypeOrdinalMapping.put(150, "ELASTIGROUP_DEPLOY_STEP");
    entityTypeOrdinalMapping.put(151, "ELASTIGROUP_ROLLBACK_STEP");
    entityTypeOrdinalMapping.put(152, "ACTION_STEP");
    entityTypeOrdinalMapping.put(153, "ELASTIGROUP_SETUP_STEP");
    entityTypeOrdinalMapping.put(154, "BITRISE_STEP");
    entityTypeOrdinalMapping.put(155, "TERRAGRUNT_PLAN_STEP");
    entityTypeOrdinalMapping.put(156, "TERRAGRUNT_APPLY_STEP");
    entityTypeOrdinalMapping.put(157, "TERRAGRUNT_DESTROY_STEP");
    entityTypeOrdinalMapping.put(158, "TERRAGRUNT_ROLLBACK_STEP");
    entityTypeOrdinalMapping.put(159, "IACM_STAGE");
    entityTypeOrdinalMapping.put(160, "IACM_STEPS");
    entityTypeOrdinalMapping.put(161, "IACM");
    entityTypeOrdinalMapping.put(162, "CONTAINER_STEP");
    entityTypeOrdinalMapping.put(163, "IACM_TERRAFORM_PLAN");
    entityTypeOrdinalMapping.put(164, "IACM_TEMPLATE");
    entityTypeOrdinalMapping.put(165, "ELASTIGROUP_BG_STAGE_SETUP_STEP");
    entityTypeOrdinalMapping.put(166, "ELASTIGROUP_SWAP_ROUTE_STEP");
    entityTypeOrdinalMapping.put(167, "ASG_CANARY_DEPLOY_STEP");
    entityTypeOrdinalMapping.put(168, "ASG_CANARY_DELETE_STEP");
    entityTypeOrdinalMapping.put(169, "TAS_SWAP_ROUTES_STEP");
    entityTypeOrdinalMapping.put(170, "TAS_SWAP_ROLLBACK_STEP");
    entityTypeOrdinalMapping.put(171, "TAS_APP_RESIZE_STEP");
    entityTypeOrdinalMapping.put(172, "TAS_ROLLBACK_STEP");
    entityTypeOrdinalMapping.put(173, "TAS_CANARY_APP_SETUP_STEP");
    entityTypeOrdinalMapping.put(174, "TAS_BG_APP_SETUP_STEP");
    entityTypeOrdinalMapping.put(175, "TAS_BASIC_APP_SETUP_STEP");
    entityTypeOrdinalMapping.put(176, "TANZU_COMMAND_STEP");
    entityTypeOrdinalMapping.put(177, "ASG_ROLLING_DEPLOY_STEP");
    entityTypeOrdinalMapping.put(178, "ASG_ROLLING_ROLLBACK_STEP");
    entityTypeOrdinalMapping.put(179, "CCM_GOVERNANCE_RULE_AWS");
    entityTypeOrdinalMapping.put(180, "TAS_ROLLING_DEPLOY");
    entityTypeOrdinalMapping.put(181, "TAS_ROLLING_ROLLBACK");
    entityTypeOrdinalMapping.put(182, "K8S_DRY_RUN_MANIFEST_STEP");
    entityTypeOrdinalMapping.put(183, "ASG_BLUE_GREEN_SWAP_SERVICE_STEP");
    entityTypeOrdinalMapping.put(184, "ASG_BLUE_GREEN_DEPLOY_STEP");
    entityTypeOrdinalMapping.put(185, "ASG_BLUE_GREEN_ROLLBACK_STEP");
    entityTypeOrdinalMapping.put(186, "TERRAFORM_CLOUD_RUN");
    entityTypeOrdinalMapping.put(187, "TERRAFORM_CLOUD_ROLLBACK");
    entityTypeOrdinalMapping.put(188, "GOOGLE_CLOUD_FUNCTIONS_DEPLOY");
    entityTypeOrdinalMapping.put(189, "GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC");
    entityTypeOrdinalMapping.put(190, "GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT");
    entityTypeOrdinalMapping.put(191, "GOOGLE_CLOUD_FUNCTIONS_ROLLBACK");
    entityTypeOrdinalMapping.put(192, "AWS_LAMBDA_DEPLOY");
    entityTypeOrdinalMapping.put(193, "AWS_SAM_DEPLOY");
    entityTypeOrdinalMapping.put(194, "AWS_SAM_ROLLBACK");
    entityTypeOrdinalMapping.put(195, "SSCA_ORCHESTRATION");
    entityTypeOrdinalMapping.put(196, "AWS_LAMBDA_ROLLBACK");
    entityTypeConstantMapping =
        entityTypeOrdinalMapping.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void testMappingExistsForAllEnumConstants() {
    Arrays.stream(EntityType.values()).forEach(entityType -> {
      if (!entityType.name().equals(entityTypeOrdinalMapping.get(entityType.ordinal()))) {
        Assertions.fail(String.format("Not all constants from enum [%s] mapped in test [%s].",
            EntityType.class.getCanonicalName(), this.getClass().getCanonicalName()));
      }
    });
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void testEnumConstantAddedAtTheEndWithoutMapping() {
    if (EntityType.values().length > entityTypeOrdinalMapping.size()) {
      Arrays.stream(EntityType.values()).forEach(entityType -> {
        if (!entityType.name().equals(entityTypeOrdinalMapping.get(entityType.ordinal()))
            && !entityTypeConstantMapping.containsKey(entityType.name())
            && entityType.ordinal() >= entityTypeOrdinalMapping.size()) {
          Assertions.fail(String.format(
              "New constant added at the end of Enum [%s] at ordinal [%s] with name [%s]. This is expected for Kryo serialization/deserialization to work in the backward compatible manner. Please add this new enum constant mapping in test [%s].",
              EntityType.class.getCanonicalName(), entityType.ordinal(), entityType.name(),
              this.getClass().getCanonicalName()));
        }
      });
    }
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void testEnumConstantNotAddedInBetween() {
    if (entityTypeOrdinalMapping.size() < EntityType.values().length) {
      Arrays.stream(EntityType.values()).forEach(entityType -> {
        if (!entityType.name().equals(entityTypeOrdinalMapping.get(entityType.ordinal()))
            && !entityTypeConstantMapping.containsKey(entityType.name())
            && entityType.ordinal() < entityTypeOrdinalMapping.size()) {
          Assertions.fail(String.format(
              "New constant added in Enum [%s] at ordinal [%s] with name [%s]. You have to add constant at the end for Kryo serialization/deserialization to work in the backward compatible manner.",
              EntityType.class.getCanonicalName(), entityType.ordinal(), entityType.name()));
        }
      });
    }
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void testEnumConstantNotDeleted() {
    if (entityTypeOrdinalMapping.size() > EntityType.values().length) {
      Arrays.stream(EntityType.values()).forEach(entityType -> {
        if (!entityType.name().equals(entityTypeOrdinalMapping.get(entityType.ordinal()))
            && entityTypeConstantMapping.containsKey(entityType.name())) {
          Assertions.fail(String.format(
              "Constant deleted from Enum [%s] at ordinal [%s] with name [%s]. You should not delete constant for Kryo serialization/deserialization to work in the backward compatible manner.",
              EntityType.class.getCanonicalName(), entityType.ordinal(), entityType.name()));
        }
      });
    }
  }
}
