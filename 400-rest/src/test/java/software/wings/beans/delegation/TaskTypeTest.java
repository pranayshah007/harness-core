/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.delegation;

import static io.harness.annotations.dev.HarnessModule._950_DELEGATE_TASKS_BEANS;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.TaskType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@TargetModule(_950_DELEGATE_TASKS_BEANS)
@OwnedBy(PL)
public class TaskTypeTest extends CategoryTest {
  private Map<Integer, String> taskTypeOrdinalMapping;
  private Map<String, Integer> taskTypeConstantMapping;

  @Before
  public void setUp() {
    taskTypeOrdinalMapping = new HashMap<>();
    taskTypeOrdinalMapping.put(0, "CUSTOM_MANIFEST_VALUES_FETCH_TASK_NG");
    taskTypeOrdinalMapping.put(1, "GITOPS_TASK_NG");
    taskTypeOrdinalMapping.put(2, "BATCH_CAPABILITY_CHECK");
    taskTypeOrdinalMapping.put(3, "CAPABILITY_VALIDATION");
    taskTypeOrdinalMapping.put(4, "COMMAND");
    taskTypeOrdinalMapping.put(5, "SCRIPT");
    taskTypeOrdinalMapping.put(6, "HTTP");
    taskTypeOrdinalMapping.put(7, "GCB");
    taskTypeOrdinalMapping.put(8, "JENKINS");
    taskTypeOrdinalMapping.put(9, "JENKINS_COLLECTION");
    taskTypeOrdinalMapping.put(10, "JENKINS_GET_BUILDS");
    taskTypeOrdinalMapping.put(11, "JENKINS_GET_JOBS");
    taskTypeOrdinalMapping.put(12, "JENKINS_GET_JOB");
    taskTypeOrdinalMapping.put(13, "JENKINS_GET_ARTIFACT_PATHS");
    taskTypeOrdinalMapping.put(14, "JENKINS_LAST_SUCCESSFUL_BUILD");
    taskTypeOrdinalMapping.put(15, "JENKINS_GET_PLANS");
    taskTypeOrdinalMapping.put(16, "JENKINS_VALIDATE_ARTIFACT_SERVER");
    taskTypeOrdinalMapping.put(17, "JENKINS_CONNECTIVITY_TEST_TASK");
    taskTypeOrdinalMapping.put(18, "BAMBOO");
    taskTypeOrdinalMapping.put(19, "BAMBOO_COLLECTION");
    taskTypeOrdinalMapping.put(20, "BAMBOO_GET_BUILDS");
    taskTypeOrdinalMapping.put(21, "BAMBOO_GET_JOBS");
    taskTypeOrdinalMapping.put(22, "BAMBOO_GET_ARTIFACT_PATHS");
    taskTypeOrdinalMapping.put(23, "BAMBOO_LAST_SUCCESSFUL_BUILD");
    taskTypeOrdinalMapping.put(24, "BAMBOO_GET_PLANS");
    taskTypeOrdinalMapping.put(25, "BAMBOO_VALIDATE_ARTIFACT_SERVER");
    taskTypeOrdinalMapping.put(26, "DOCKER_GET_BUILDS");
    taskTypeOrdinalMapping.put(27, "DOCKER_GET_LABELS");
    taskTypeOrdinalMapping.put(28, "DOCKER_VALIDATE_ARTIFACT_SERVER");
    taskTypeOrdinalMapping.put(29, "DOCKER_VALIDATE_ARTIFACT_STREAM");
    taskTypeOrdinalMapping.put(30, "DOCKER_GET_ARTIFACT_META_INFO");
    taskTypeOrdinalMapping.put(31, "ECR_GET_BUILDS");
    taskTypeOrdinalMapping.put(32, "ECR_VALIDATE_ARTIFACT_SERVER");
    taskTypeOrdinalMapping.put(33, "ECR_GET_PLANS");
    taskTypeOrdinalMapping.put(34, "ECR_GET_ARTIFACT_PATHS");
    taskTypeOrdinalMapping.put(35, "ECR_VALIDATE_ARTIFACT_STREAM");
    taskTypeOrdinalMapping.put(36, "ECR_GET_LABELS");
    taskTypeOrdinalMapping.put(37, "GCR_GET_BUILDS");
    taskTypeOrdinalMapping.put(38, "GCR_VALIDATE_ARTIFACT_STREAM");
    taskTypeOrdinalMapping.put(39, "GCR_GET_PLANS");
    taskTypeOrdinalMapping.put(40, "ECR_ARTIFACT_TASK_NG");
    taskTypeOrdinalMapping.put(41, "ACR_GET_REGISTRIES");
    taskTypeOrdinalMapping.put(42, "ACR_GET_REGISTRY_NAMES");
    taskTypeOrdinalMapping.put(43, "ACR_GET_REPOSITORIES");
    taskTypeOrdinalMapping.put(44, "ACR_GET_BUILDS");
    taskTypeOrdinalMapping.put(45, "ACR_VALIDATE_ARTIFACT_STREAM");
    taskTypeOrdinalMapping.put(46, "ACR_GET_PLANS");
    taskTypeOrdinalMapping.put(47, "ACR_GET_ARTIFACT_PATHS");
    taskTypeOrdinalMapping.put(48, "NEXUS_GET_JOBS");
    taskTypeOrdinalMapping.put(49, "NEXUS_GET_PLANS");
    taskTypeOrdinalMapping.put(50, "NEXUS_GET_ARTIFACT_PATHS");
    taskTypeOrdinalMapping.put(51, "NEXUS_GET_GROUP_IDS");
    taskTypeOrdinalMapping.put(52, "NEXUS_GET_BUILDS");
    taskTypeOrdinalMapping.put(53, "NEXUS_LAST_SUCCESSFUL_BUILD");
    taskTypeOrdinalMapping.put(54, "NEXUS_COLLECTION");
    taskTypeOrdinalMapping.put(55, "NEXUS_VALIDATE_ARTIFACT_SERVER");
    taskTypeOrdinalMapping.put(56, "NEXUS_VALIDATE_ARTIFACT_STREAM");
    taskTypeOrdinalMapping.put(57, "GCS_GET_ARTIFACT_PATHS");
    taskTypeOrdinalMapping.put(58, "GCS_GET_BUILDS");
    taskTypeOrdinalMapping.put(59, "GCS_GET_BUCKETS");
    taskTypeOrdinalMapping.put(60, "GCS_GET_PROJECT_ID");
    taskTypeOrdinalMapping.put(61, "GCS_GET_PLANS");
    taskTypeOrdinalMapping.put(62, "SFTP_GET_BUILDS");
    taskTypeOrdinalMapping.put(63, "SFTP_GET_ARTIFACT_PATHS");
    taskTypeOrdinalMapping.put(64, "SFTP_VALIDATE_ARTIFACT_SERVER");
    taskTypeOrdinalMapping.put(65, "SMB_GET_BUILDS");
    taskTypeOrdinalMapping.put(66, "SMB_GET_SMB_PATHS");
    taskTypeOrdinalMapping.put(67, "SMB_VALIDATE_ARTIFACT_SERVER");
    taskTypeOrdinalMapping.put(68, "AMAZON_S3_COLLECTION");
    taskTypeOrdinalMapping.put(69, "AMAZON_S3_GET_ARTIFACT_PATHS");
    taskTypeOrdinalMapping.put(70, "AMAZON_S3_LAST_SUCCESSFUL_BUILD");
    taskTypeOrdinalMapping.put(71, "AMAZON_S3_GET_BUILDS");
    taskTypeOrdinalMapping.put(72, "AMAZON_S3_GET_PLANS");
    taskTypeOrdinalMapping.put(73, "AZURE_ARTIFACTS_VALIDATE_ARTIFACT_SERVER");
    taskTypeOrdinalMapping.put(74, "AZURE_ARTIFACTS_VALIDATE_ARTIFACT_STREAM");
    taskTypeOrdinalMapping.put(75, "AZURE_ARTIFACTS_GET_BUILDS");
    taskTypeOrdinalMapping.put(76, "AZURE_ARTIFACTS_GET_PROJECTS");
    taskTypeOrdinalMapping.put(77, "AZURE_ARTIFACTS_GET_FEEDS");
    taskTypeOrdinalMapping.put(78, "AZURE_ARTIFACTS_GET_PACKAGES");
    taskTypeOrdinalMapping.put(79, "AZURE_ARTIFACTS_COLLECTION");
    taskTypeOrdinalMapping.put(80, "AZURE_ARTIFACTS_CONNECTIVITY_TEST_TASK");
    taskTypeOrdinalMapping.put(81, "AZURE_GET_SUBSCRIPTIONS");
    taskTypeOrdinalMapping.put(82, "AZURE_MACHINE_IMAGE_GET_IMAGE_GALLERIES");
    taskTypeOrdinalMapping.put(83, "AZURE_MACHINE_IMAGE_GET_IMAGE_DEFINITIONS");
    taskTypeOrdinalMapping.put(84, "AZURE_MACHINE_IMAGE_VALIDATE_ARTIFACT_SERVER");
    taskTypeOrdinalMapping.put(85, "AZURE_MACHINE_IMAGE_GET_RESOURCE_GROUPS");
    taskTypeOrdinalMapping.put(86, "AZURE_MACHINE_IMAGE_GET_BUILDS");
    taskTypeOrdinalMapping.put(87, "AZURE_VMSS_COMMAND_TASK");
    taskTypeOrdinalMapping.put(88, "AZURE_APP_SERVICE_TASK");
    taskTypeOrdinalMapping.put(89, "AZURE_ARM_TASK");
    taskTypeOrdinalMapping.put(90, "AZURE_RESOURCE_TASK");
    taskTypeOrdinalMapping.put(91, "LDAP_TEST_CONN_SETTINGS");
    taskTypeOrdinalMapping.put(92, "LDAP_TEST_USER_SETTINGS");
    taskTypeOrdinalMapping.put(93, "LDAP_TEST_GROUP_SETTINGS");
    taskTypeOrdinalMapping.put(94, "LDAP_VALIDATE_SETTINGS");
    taskTypeOrdinalMapping.put(95, "LDAP_AUTHENTICATION");
    taskTypeOrdinalMapping.put(96, "LDAP_SEARCH_GROUPS");
    taskTypeOrdinalMapping.put(97, "LDAP_FETCH_GROUP");
    taskTypeOrdinalMapping.put(98, "NG_LDAP_SEARCH_GROUPS");
    taskTypeOrdinalMapping.put(99, "NG_LDAP_TEST_CONN_SETTINGS");
    taskTypeOrdinalMapping.put(100, "APM_VALIDATE_CONNECTOR_TASK");
    taskTypeOrdinalMapping.put(101, "CUSTOM_LOG_VALIDATE_CONNECTOR_TASK");
    taskTypeOrdinalMapping.put(102, "APM_GET_TASK");
    taskTypeOrdinalMapping.put(103, "APPDYNAMICS_CONFIGURATION_VALIDATE_TASK");
    taskTypeOrdinalMapping.put(104, "CVNG_CONNECTOR_VALIDATE_TASK");
    taskTypeOrdinalMapping.put(105, "GET_DATA_COLLECTION_RESULT");
    taskTypeOrdinalMapping.put(106, "APPDYNAMICS_GET_APP_TASK");
    taskTypeOrdinalMapping.put(107, "APPDYNAMICS_GET_APP_TASK_NG");
    taskTypeOrdinalMapping.put(108, "APPDYNAMICS_GET_TIER_TASK");
    taskTypeOrdinalMapping.put(109, "APPDYNAMICS_GET_TIER_TASK_NG");
    taskTypeOrdinalMapping.put(110, "APPDYNAMICS_GET_TIER_MAP");
    taskTypeOrdinalMapping.put(111, "APPDYNAMICS_COLLECT_METRIC_DATA");
    taskTypeOrdinalMapping.put(112, "APPDYNAMICS_COLLECT_METRIC_DATA_V2");
    taskTypeOrdinalMapping.put(113, "APPDYNAMICS_COLLECT_24_7_METRIC_DATA");
    taskTypeOrdinalMapping.put(114, "APPDYNAMICS_METRIC_DATA_FOR_NODE");
    taskTypeOrdinalMapping.put(115, "INSTANA_GET_INFRA_METRICS");
    taskTypeOrdinalMapping.put(116, "INSTANA_GET_TRACE_METRICS");
    taskTypeOrdinalMapping.put(117, "INSTANA_COLLECT_METRIC_DATA");
    taskTypeOrdinalMapping.put(118, "INSTANA_VALIDATE_CONFIGURATION_TASK");
    taskTypeOrdinalMapping.put(119, "NEWRELIC_VALIDATE_CONFIGURATION_TASK");
    taskTypeOrdinalMapping.put(120, "BUGSNAG_GET_APP_TASK");
    taskTypeOrdinalMapping.put(121, "BUGSNAG_GET_RECORDS");
    taskTypeOrdinalMapping.put(122, "CUSTOM_COLLECT_24_7_LOG_DATA");
    taskTypeOrdinalMapping.put(123, "CUSTOM_APM_COLLECT_METRICS_V2");
    taskTypeOrdinalMapping.put(124, "NEWRELIC_GET_APP_TASK");
    taskTypeOrdinalMapping.put(125, "NEWRELIC_RESOLVE_APP_TASK");
    taskTypeOrdinalMapping.put(126, "NEWRELIC_RESOLVE_APP_ID_TASK");
    taskTypeOrdinalMapping.put(127, "NEWRELIC_GET_APP_INSTANCES_TASK");
    taskTypeOrdinalMapping.put(128, "NEWRELIC_COLLECT_METRIC_DATA");
    taskTypeOrdinalMapping.put(129, "NEWRELIC_COLLECT_METRIC_DATAV2");
    taskTypeOrdinalMapping.put(130, "NEWRELIC_COLLECT_24_7_METRIC_DATA");
    taskTypeOrdinalMapping.put(131, "NEWRELIC_GET_TXNS_WITH_DATA");
    taskTypeOrdinalMapping.put(132, "NEWRELIC_GET_TXNS_WITH_DATA_FOR_NODE");
    taskTypeOrdinalMapping.put(133, "NEWRELIC_POST_DEPLOYMENT_MARKER");
    taskTypeOrdinalMapping.put(134, "STACKDRIVER_COLLECT_METRIC_DATA");
    taskTypeOrdinalMapping.put(135, "STACKDRIVER_METRIC_DATA_FOR_NODE");
    taskTypeOrdinalMapping.put(136, "STACKDRIVER_LOG_DATA_FOR_NODE");
    taskTypeOrdinalMapping.put(137, "STACKDRIVER_LIST_REGIONS");
    taskTypeOrdinalMapping.put(138, "STACKDRIVER_LIST_FORWARDING_RULES");
    taskTypeOrdinalMapping.put(139, "STACKDRIVER_GET_LOG_SAMPLE");
    taskTypeOrdinalMapping.put(140, "STACKDRIVER_COLLECT_24_7_METRIC_DATA");
    taskTypeOrdinalMapping.put(141, "STACKDRIVER_COLLECT_LOG_DATA");
    taskTypeOrdinalMapping.put(142, "STACKDRIVER_COLLECT_24_7_LOG_DATA");
    taskTypeOrdinalMapping.put(143, "SPLUNK");
    taskTypeOrdinalMapping.put(144, "SPLUNK_CONFIGURATION_VALIDATE_TASK");
    taskTypeOrdinalMapping.put(145, "SPLUNK_GET_HOST_RECORDS");
    taskTypeOrdinalMapping.put(146, "SPLUNK_NG_GET_SAVED_SEARCHES");
    taskTypeOrdinalMapping.put(147, "SPLUNK_NG_VALIDATION_RESPONSE_TASK");
    taskTypeOrdinalMapping.put(148, "SPLUNK_COLLECT_LOG_DATAV2");
    taskTypeOrdinalMapping.put(149, "ELK_COLLECT_LOG_DATAV2");
    taskTypeOrdinalMapping.put(150, "DATA_COLLECTION_NEXT_GEN_VALIDATION");
    taskTypeOrdinalMapping.put(151, "SUMO_COLLECT_LOG_DATA");
    taskTypeOrdinalMapping.put(152, "SUMO_VALIDATE_CONFIGURATION_TASK");
    taskTypeOrdinalMapping.put(153, "SUMO_GET_HOST_RECORDS");
    taskTypeOrdinalMapping.put(154, "SUMO_GET_LOG_DATA_BY_HOST");
    taskTypeOrdinalMapping.put(155, "SUMO_COLLECT_24_7_LOG_DATA");
    taskTypeOrdinalMapping.put(156, "ELK_CONFIGURATION_VALIDATE_TASK");
    taskTypeOrdinalMapping.put(157, "ELK_COLLECT_LOG_DATA");
    taskTypeOrdinalMapping.put(158, "ELK_COLLECT_INDICES");
    taskTypeOrdinalMapping.put(159, "ELK_GET_LOG_SAMPLE");
    taskTypeOrdinalMapping.put(160, "ELK_GET_HOST_RECORDS");
    taskTypeOrdinalMapping.put(161, "KIBANA_GET_VERSION");
    taskTypeOrdinalMapping.put(162, "ELK_COLLECT_24_7_LOG_DATA");
    taskTypeOrdinalMapping.put(163, "LOGZ_CONFIGURATION_VALIDATE_TASK");
    taskTypeOrdinalMapping.put(164, "LOGZ_COLLECT_LOG_DATA");
    taskTypeOrdinalMapping.put(165, "LOGZ_GET_LOG_SAMPLE");
    taskTypeOrdinalMapping.put(166, "LOGZ_GET_HOST_RECORDS");
    taskTypeOrdinalMapping.put(167, "ARTIFACTORY_GET_BUILDS");
    taskTypeOrdinalMapping.put(168, "ARTIFACTORY_GET_LABELS");
    taskTypeOrdinalMapping.put(169, "ARTIFACTORY_GET_JOBS");
    taskTypeOrdinalMapping.put(170, "ARTIFACTORY_GET_PLANS");
    taskTypeOrdinalMapping.put(171, "ARTIFACTORY_GET_ARTIFACTORY_PATHS");
    taskTypeOrdinalMapping.put(172, "ARTIFACTORY_GET_GROUP_IDS");
    taskTypeOrdinalMapping.put(173, "ARTIFACTORY_LAST_SUCCSSFUL_BUILD");
    taskTypeOrdinalMapping.put(174, "ARTIFACTORY_COLLECTION");
    taskTypeOrdinalMapping.put(175, "ARTIFACTORY_VALIDATE_ARTIFACT_SERVER");
    taskTypeOrdinalMapping.put(176, "ARTIFACTORY_VALIDATE_ARTIFACT_STREAM");
    taskTypeOrdinalMapping.put(177, "VAULT_GET_CHANGELOG");
    taskTypeOrdinalMapping.put(178, "VAULT_RENEW_TOKEN");
    taskTypeOrdinalMapping.put(179, "VAULT_LIST_ENGINES");
    taskTypeOrdinalMapping.put(180, "VAULT_APPROLE_LOGIN");
    taskTypeOrdinalMapping.put(181, "SSH_SECRET_ENGINE_AUTH");
    taskTypeOrdinalMapping.put(182, "VAULT_SIGN_PUBLIC_KEY_SSH");
    taskTypeOrdinalMapping.put(183, "SECRET_DECRYPT");
    taskTypeOrdinalMapping.put(184, "BATCH_SECRET_DECRYPT");
    taskTypeOrdinalMapping.put(185, "SECRET_DECRYPT_REF");
    taskTypeOrdinalMapping.put(186, "DELETE_SECRET");
    taskTypeOrdinalMapping.put(187, "VALIDATE_SECRET_REFERENCE");
    taskTypeOrdinalMapping.put(188, "UPSERT_SECRET");
    taskTypeOrdinalMapping.put(189, "FETCH_SECRET");
    taskTypeOrdinalMapping.put(190, "ENCRYPT_SECRET");
    taskTypeOrdinalMapping.put(191, "VALIDATE_SECRET_MANAGER_CONFIGURATION");
    taskTypeOrdinalMapping.put(192, "NG_VAULT_RENEW_TOKEN");
    taskTypeOrdinalMapping.put(193, "NG_VAULT_RENEW_APP_ROLE_TOKEN");
    taskTypeOrdinalMapping.put(194, "NG_VAULT_FETCHING_TASK");
    taskTypeOrdinalMapping.put(195, "NG_AZURE_VAULT_FETCH_ENGINES");
    taskTypeOrdinalMapping.put(196, "HOST_VALIDATION");
    taskTypeOrdinalMapping.put(197, "CONTAINER_ACTIVE_SERVICE_COUNTS");
    taskTypeOrdinalMapping.put(198, "CONTAINER_INFO");
    taskTypeOrdinalMapping.put(199, "CONTROLLER_NAMES_WITH_LABELS");
    taskTypeOrdinalMapping.put(200, "AMI_GET_BUILDS");
    taskTypeOrdinalMapping.put(201, "CONTAINER_CE_VALIDATION");
    taskTypeOrdinalMapping.put(202, "CE_DELEGATE_VALIDATION");
    taskTypeOrdinalMapping.put(203, "CONTAINER_CONNECTION_VALIDATION");
    taskTypeOrdinalMapping.put(204, "LIST_CLUSTERS");
    taskTypeOrdinalMapping.put(205, "CONTAINER_VALIDATION");
    taskTypeOrdinalMapping.put(206, "FETCH_MASTER_URL");
    taskTypeOrdinalMapping.put(207, "DYNA_TRACE_VALIDATE_CONFIGURATION_TASK");
    taskTypeOrdinalMapping.put(208, "DYNA_TRACE_METRIC_DATA_COLLECTION_TASK");
    taskTypeOrdinalMapping.put(209, "DYNA_TRACE_GET_TXNS_WITH_DATA_FOR_NODE");
    taskTypeOrdinalMapping.put(210, "DYNA_TRACE_GET_SERVICES");
    taskTypeOrdinalMapping.put(211, "DYNATRACE_COLLECT_24_7_METRIC_DATA");
    taskTypeOrdinalMapping.put(212, "HELM_COMMAND_TASK");
    taskTypeOrdinalMapping.put(213, "HELM_COMMAND_TASK_NG");
    taskTypeOrdinalMapping.put(214, "KUBERNETES_STEADY_STATE_CHECK_TASK");
    taskTypeOrdinalMapping.put(215, "PCF_COMMAND_TASK");
    taskTypeOrdinalMapping.put(216, "SPOTINST_COMMAND_TASK");
    taskTypeOrdinalMapping.put(217, "ECS_COMMAND_TASK");
    taskTypeOrdinalMapping.put(218, "COLLABORATION_PROVIDER_TASK");
    taskTypeOrdinalMapping.put(219, "PROMETHEUS_METRIC_DATA_PER_HOST");
    taskTypeOrdinalMapping.put(220, "CLOUD_WATCH_COLLECT_METRIC_DATA");
    taskTypeOrdinalMapping.put(221, "CLOUD_WATCH_METRIC_DATA_FOR_NODE");
    taskTypeOrdinalMapping.put(222, "CLOUD_WATCH_GENERIC_METRIC_STATISTICS");
    taskTypeOrdinalMapping.put(223, "CLOUD_WATCH_GENERIC_METRIC_DATA");
    taskTypeOrdinalMapping.put(224, "CLOUD_WATCH_COLLECT_24_7_METRIC_DATA");
    taskTypeOrdinalMapping.put(225, "APM_METRIC_DATA_COLLECTION_TASK");
    taskTypeOrdinalMapping.put(226, "APM_24_7_METRIC_DATA_COLLECTION_TASK");
    taskTypeOrdinalMapping.put(227, "CUSTOM_LOG_COLLECTION_TASK");
    taskTypeOrdinalMapping.put(228, "CLOUD_FORMATION_TASK");
    taskTypeOrdinalMapping.put(229, "FETCH_S3_FILE_TASK");
    taskTypeOrdinalMapping.put(230, "TERRAFORM_PROVISION_TASK");
    taskTypeOrdinalMapping.put(231, "TERRAFORM_INPUT_VARIABLES_OBTAIN_TASK");
    taskTypeOrdinalMapping.put(232, "TERRAFORM_FETCH_TARGETS_TASK");
    taskTypeOrdinalMapping.put(233, "TERRAGRUNT_PROVISION_TASK");
    taskTypeOrdinalMapping.put(234, "KUBERNETES_SWAP_SERVICE_SELECTORS_TASK");
    taskTypeOrdinalMapping.put(235, "ECS_STEADY_STATE_CHECK_TASK");
    taskTypeOrdinalMapping.put(236, "AWS_ECR_TASK");
    taskTypeOrdinalMapping.put(237, "AWS_ELB_TASK");
    taskTypeOrdinalMapping.put(238, "AWS_ECS_TASK");
    taskTypeOrdinalMapping.put(239, "AWS_IAM_TASK");
    taskTypeOrdinalMapping.put(240, "AWS_EC2_TASK");
    taskTypeOrdinalMapping.put(241, "AWS_ASG_TASK");
    taskTypeOrdinalMapping.put(242, "AWS_CODE_DEPLOY_TASK");
    taskTypeOrdinalMapping.put(243, "AWS_LAMBDA_TASK");
    taskTypeOrdinalMapping.put(244, "AWS_AMI_ASYNC_TASK");
    taskTypeOrdinalMapping.put(245, "AWS_CF_TASK");
    taskTypeOrdinalMapping.put(246, "K8S_COMMAND_TASK");
    taskTypeOrdinalMapping.put(247, "K8S_COMMAND_TASK_NG");
    taskTypeOrdinalMapping.put(248, "K8S_WATCH_TASK");
    taskTypeOrdinalMapping.put(249, "TRIGGER_TASK");
    taskTypeOrdinalMapping.put(250, "WEBHOOK_TRIGGER_TASK");
    taskTypeOrdinalMapping.put(251, "JIRA");
    taskTypeOrdinalMapping.put(252, "CONNECTIVITY_VALIDATION");
    taskTypeOrdinalMapping.put(253, "GIT_COMMAND");
    taskTypeOrdinalMapping.put(254, "GIT_FETCH_FILES_TASK");
    taskTypeOrdinalMapping.put(255, "GIT_FETCH_NEXT_GEN_TASK");
    taskTypeOrdinalMapping.put(256, "BUILD_SOURCE_TASK");
    taskTypeOrdinalMapping.put(257, "DOCKER_ARTIFACT_TASK_NG");
    taskTypeOrdinalMapping.put(258, "GOOGLE_ARTIFACT_REGISTRY_TASK_NG");
    taskTypeOrdinalMapping.put(259, "JENKINS_ARTIFACT_TASK_NG");
    taskTypeOrdinalMapping.put(260, "GCR_ARTIFACT_TASK_NG");
    taskTypeOrdinalMapping.put(261, "NEXUS_ARTIFACT_TASK_NG");
    taskTypeOrdinalMapping.put(262, "ARTIFACTORY_ARTIFACT_TASK_NG");
    taskTypeOrdinalMapping.put(263, "AMAZON_S3_ARTIFACT_TASK_NG");
    taskTypeOrdinalMapping.put(264, "GITHUB_PACKAGES_TASK_NG");
    taskTypeOrdinalMapping.put(265, "AZURE_ARTIFACT_TASK_NG");
    taskTypeOrdinalMapping.put(266, "AMI_ARTIFACT_TASK_NG");
    taskTypeOrdinalMapping.put(267, "AWS_ROUTE53_TASK");
    taskTypeOrdinalMapping.put(268, "SHELL_SCRIPT_APPROVAL");
    taskTypeOrdinalMapping.put(269, "CUSTOM_GET_BUILDS");
    taskTypeOrdinalMapping.put(270, "CUSTOM_VALIDATE_ARTIFACT_STREAM");
    taskTypeOrdinalMapping.put(271, "CUSTOM_ARTIFACT_NG");
    taskTypeOrdinalMapping.put(272, "SHELL_SCRIPT_PROVISION_TASK");
    taskTypeOrdinalMapping.put(273, "SERVICENOW_ASYNC");
    taskTypeOrdinalMapping.put(274, "SERVICENOW_SYNC");
    taskTypeOrdinalMapping.put(275, "SERVICENOW_VALIDATION");
    taskTypeOrdinalMapping.put(276, "HELM_REPO_CONFIG_VALIDATION");
    taskTypeOrdinalMapping.put(277, "HELM_VALUES_FETCH");
    taskTypeOrdinalMapping.put(278, "HELM_VALUES_FETCH_NG");
    taskTypeOrdinalMapping.put(279, "HELM_COLLECT_CHART");
    taskTypeOrdinalMapping.put(280, "SLACK");
    taskTypeOrdinalMapping.put(281, "INITIALIZATION_PHASE");
    taskTypeOrdinalMapping.put(282, "CI_LE_STATUS");
    taskTypeOrdinalMapping.put(283, "EXECUTE_COMMAND");
    taskTypeOrdinalMapping.put(284, "CI_CLEANUP");
    taskTypeOrdinalMapping.put(285, "CI_EXECUTE_STEP");
    taskTypeOrdinalMapping.put(286, "AWS_S3_TASK");
    taskTypeOrdinalMapping.put(287, "CUSTOM_MANIFEST_VALUES_FETCH_TASK");
    taskTypeOrdinalMapping.put(288, "CUSTOM_MANIFEST_FETCH_TASK");
    taskTypeOrdinalMapping.put(289, "GCP_TASK");
    taskTypeOrdinalMapping.put(290, "VALIDATE_KUBERNETES_CONFIG");
    taskTypeOrdinalMapping.put(291, "NG_GIT_COMMAND");
    taskTypeOrdinalMapping.put(292, "NG_SSH_VALIDATION");
    taskTypeOrdinalMapping.put(293, "NG_WINRM_VALIDATION");
    taskTypeOrdinalMapping.put(294, "NG_HOST_CONNECTIVITY_TASK");
    taskTypeOrdinalMapping.put(295, "DOCKER_CONNECTIVITY_TEST_TASK");
    taskTypeOrdinalMapping.put(296, "NG_AWS_TASK");
    taskTypeOrdinalMapping.put(297, "JIRA_TASK_NG");
    taskTypeOrdinalMapping.put(298, "BUILD_STATUS");
    taskTypeOrdinalMapping.put(299, "GIT_API_TASK");
    taskTypeOrdinalMapping.put(300, "AWS_CODECOMMIT_API_TASK");
    taskTypeOrdinalMapping.put(301, "JIRA_CONNECTIVITY_TASK_NG");
    taskTypeOrdinalMapping.put(302, "K8_FETCH_NAMESPACES");
    taskTypeOrdinalMapping.put(303, "K8_FETCH_WORKLOADS");
    taskTypeOrdinalMapping.put(304, "K8_FETCH_EVENTS");
    taskTypeOrdinalMapping.put(305, "NOTIFY_SLACK");
    taskTypeOrdinalMapping.put(306, "NOTIFY_PAGERDUTY");
    taskTypeOrdinalMapping.put(307, "NOTIFY_MAIL");
    taskTypeOrdinalMapping.put(308, "NOTIFY_MICROSOFTTEAMS");
    taskTypeOrdinalMapping.put(309, "HTTP_TASK_NG");
    taskTypeOrdinalMapping.put(310, "SHELL_SCRIPT_TASK_NG");
    taskTypeOrdinalMapping.put(311, "NG_NEXUS_TASK");
    taskTypeOrdinalMapping.put(312, "NG_ARTIFACTORY_TASK");
    taskTypeOrdinalMapping.put(313, "CE_VALIDATE_KUBERNETES_CONFIG");
    taskTypeOrdinalMapping.put(314, "K8S_SERVICE_ACCOUNT_INFO");
    taskTypeOrdinalMapping.put(315, "NG_AWS_CODE_COMMIT_TASK");
    taskTypeOrdinalMapping.put(316, "HTTP_HELM_CONNECTIVITY_TASK");
    taskTypeOrdinalMapping.put(317, "NG_DECRYT_GIT_API_ACCESS_TASK");
    taskTypeOrdinalMapping.put(318, "TERRAFORM_TASK_NG");
    taskTypeOrdinalMapping.put(319, "SCM_PUSH_TASK");
    taskTypeOrdinalMapping.put(320, "SCM_PATH_FILTER_EVALUATION_TASK");
    taskTypeOrdinalMapping.put(321, "SCM_GIT_REF_TASK");
    taskTypeOrdinalMapping.put(322, "SCM_GIT_FILE_TASK");
    taskTypeOrdinalMapping.put(323, "SCM_PULL_REQUEST_TASK");
    taskTypeOrdinalMapping.put(324, "SCM_GIT_WEBHOOK_TASK");
    taskTypeOrdinalMapping.put(325, "SERVICENOW_CONNECTIVITY_TASK_NG");
    taskTypeOrdinalMapping.put(326, "SERVICENOW_TASK_NG");
    taskTypeOrdinalMapping.put(327, "RANCHER_RESOLVE_CLUSTERS");
    taskTypeOrdinalMapping.put(328, "NG_AZURE_TASK");
    taskTypeOrdinalMapping.put(329, "CLOUDFORMATION_TASK_NG");
    taskTypeOrdinalMapping.put(330, "ACR_ARTIFACT_TASK_NG");
    taskTypeOrdinalMapping.put(331, "SERVERLESS_GIT_FETCH_TASK_NG");
    taskTypeOrdinalMapping.put(332, "SERVERLESS_COMMAND_TASK");
    taskTypeOrdinalMapping.put(333, "FETCH_S3_FILE_TASK_NG");
    taskTypeOrdinalMapping.put(334, "OCI_HELM_CONNECTIVITY_TASK");
    taskTypeOrdinalMapping.put(335, "AZURE_WEB_APP_TASK_NG");
    taskTypeOrdinalMapping.put(336, "COMMAND_TASK_NG");
    taskTypeOrdinalMapping.put(337, "VALIDATE_CUSTOM_SECRET_MANAGER_SECRET_REFERENCE");
    taskTypeOrdinalMapping.put(338, "FETCH_CUSTOM_SECRET");
    taskTypeOrdinalMapping.put(339, "RESOLVE_CUSTOM_SM_CONFIG");
    taskTypeOrdinalMapping.put(340, "NG_LDAP_TEST_USER_SETTINGS");
    taskTypeOrdinalMapping.put(341, "NG_LDAP_TEST_GROUP_SETTINGS");
    taskTypeOrdinalMapping.put(342, "DLITE_CI_VM_INITIALIZE_TASK");
    taskTypeOrdinalMapping.put(343, "DLITE_CI_VM_EXECUTE_TASK");
    taskTypeOrdinalMapping.put(344, "DLITE_CI_VM_CLEANUP_TASK");
    taskTypeOrdinalMapping.put(345, "NG_LDAP_GROUPS_SYNC");
    taskTypeOrdinalMapping.put(346, "AZURE_NG_ARM");
    taskTypeOrdinalMapping.put(347, "NG_LDAP_TEST_AUTHENTICATION");
    taskTypeOrdinalMapping.put(348, "ECS_GIT_FETCH_TASK_NG");
    taskTypeOrdinalMapping.put(349, "ECS_COMMAND_TASK_NG");
    taskTypeOrdinalMapping.put(350, "WIN_RM_SHELL_SCRIPT_TASK_NG");
    taskTypeOrdinalMapping.put(351, "SHELL_SCRIPT_PROVISION");
    taskTypeOrdinalMapping.put(352, "ECS_GIT_FETCH_RUN_TASK_NG");
    taskTypeOrdinalMapping.put(353, "TRIGGER_AUTHENTICATION_TASK");
    taskTypeOrdinalMapping.put(354, "SPOT_TASK_NG");
    taskTypeOrdinalMapping.put(355, "FETCH_INSTANCE_SCRIPT_TASK_NG");
    taskTypeOrdinalMapping.put(356, "AZURE_WEB_APP_TASK_NG_V2");
    taskTypeOrdinalMapping.put(357, "HELM_FETCH_CHART_VERSIONS_TASK_NG");
    taskTypeOrdinalMapping.put(358, "TERRAFORM_TASK_NG_V2");
    taskTypeOrdinalMapping.put(359, "ELASTIGROUP_SETUP_COMMAND_TASK_NG");
    taskTypeOrdinalMapping.put(360, "ELASTIGROUP_STARTUP_SCRIPT_FETCH_RUN_TASK_NG");
    taskTypeOrdinalMapping.put(361, "TERRAFORM_SECRET_CLEANUP_TASK_NG");
    taskTypeOrdinalMapping.put(362, "TERRAGRUNT_PLAN_TASK_NG");
    taskTypeOrdinalMapping.put(363, "TERRAGRUNT_APPLY_TASK_NG");
    taskTypeOrdinalMapping.put(364, "TERRAGRUNT_DESTROY_TASK_NG");
    taskTypeOrdinalMapping.put(365, "TERRAGRUNT_ROLLBACK_TASK_NG");
    taskTypeOrdinalMapping.put(366, "GITOPS_FETCH_APP_TASK");
    taskTypeOrdinalMapping.put(367, "VAULT_TOKEN_LOOKUP");
    taskTypeOrdinalMapping.put(368, "NG_VAULT_TOKEN_LOOKUP");
    taskTypeOrdinalMapping.put(369, "VALIDATE_TAS_CONNECTOR_TASK_NG");
    taskTypeOrdinalMapping.put(370, "ECS_S3_FETCH_TASK_NG");
    taskTypeOrdinalMapping.put(371, "SERVERLESS_S3_FETCH_TASK_NG");
    taskTypeOrdinalMapping.put(372, "CONTAINER_INITIALIZATION");
    taskTypeOrdinalMapping.put(373, "AWS_ASG_CANARY_DEPLOY_TASK_NG");
    taskTypeOrdinalMapping.put(374, "ELASTIGROUP_DEPLOY");
    taskTypeOrdinalMapping.put(375, "ELASTIGROUP_PARAMETERS_FETCH_RUN_TASK_NG");
    taskTypeOrdinalMapping.put(376, "ELASTIGROUP_BG_STAGE_SETUP_COMMAND_TASK_NG");
    taskTypeOrdinalMapping.put(377, "ELASTIGROUP_SWAP_ROUTE_COMMAND_TASK_NG");
    taskTypeOrdinalMapping.put(378, "ELASTIGROUP_ROLLBACK");
    taskTypeOrdinalMapping.put(379, "ELASTIGROUP_PRE_FETCH_TASK_NG");
    taskTypeOrdinalMapping.put(380, "CONTAINER_LE_STATUS");
    taskTypeOrdinalMapping.put(381, "CONTAINER_CLEANUP");
    taskTypeOrdinalMapping.put(382, "CONTAINER_EXECUTE_STEP");
    taskTypeOrdinalMapping.put(383, "AWS_ASG_CANARY_DELETE_TASK_NG");
    taskTypeOrdinalMapping.put(384, "TAS_APP_RESIZE");
    taskTypeOrdinalMapping.put(385, "TAS_ROLLBACK");
    taskTypeOrdinalMapping.put(386, "TAS_SWAP_ROUTES");
    taskTypeOrdinalMapping.put(387, "TANZU_COMMAND");
    taskTypeOrdinalMapping.put(388, "TAS_BASIC_SETUP");
    taskTypeOrdinalMapping.put(389, "TAS_BG_SETUP");
    taskTypeOrdinalMapping.put(390, "TAS_SWAP_ROLLBACK");
    taskTypeOrdinalMapping.put(391, "TAS_DATA_FETCH");
    taskTypeOrdinalMapping.put(392, "ECS_RUN_TASK_ARN");
    taskTypeOrdinalMapping.put(393, "AWS_ASG_ROLLING_DEPLOY_TASK_NG");
    taskTypeOrdinalMapping.put(394, "AWS_ASG_PREPARE_ROLLBACK_DATA_TASK_NG");
    taskTypeOrdinalMapping.put(395, "AWS_ASG_ROLLING_ROLLBACK_TASK_NG");
    taskTypeOrdinalMapping.put(396, "TERRAFORM_PROVISION_TASK_V2");
    taskTypeOrdinalMapping.put(397, "TERRAFORM_INPUT_VARIABLES_OBTAIN_TASK_V2");
    taskTypeOrdinalMapping.put(398, "TERRAFORM_FETCH_TARGETS_TASK_V2");
    taskTypeOrdinalMapping.put(399, "TAS_ROLLING_DEPLOY");
    taskTypeOrdinalMapping.put(400, "TAS_ROLLING_ROLLBACK");
    taskTypeOrdinalMapping.put(401, "K8S_DRY_RUN_MANIFEST_TASK_NG");
    taskTypeOrdinalMapping.put(402, "COMMAND_TASK_NG_WITH_AZURE_ARTIFACT");
    taskTypeOrdinalMapping.put(403, "AWS_ASG_BLUE_GREEN_SWAP_SERVICE_TASK_NG");
    taskTypeOrdinalMapping.put(404, "AWS_ASG_BLUE_GREEN_PREPARE_ROLLBACK_DATA_TASK_NG");
    taskTypeOrdinalMapping.put(405, "AWS_ASG_BLUE_GREEN_DEPLOY_TASK_NG");
    taskTypeOrdinalMapping.put(406, "AWS_ASG_BLUE_GREEN_ROLLBACK_TASK_NG");
    taskTypeOrdinalMapping.put(407, "SCM_BATCH_GET_FILE_TASK");
    taskTypeOrdinalMapping.put(408, "TERRAFORM_CLOUD_TASK_NG");
    taskTypeOrdinalMapping.put(409, "GIT_TASK_NG");
    taskTypeOrdinalMapping.put(410, "GOOGLE_CLOUD_STORAGE_ARTIFACT_TASK_NG");
    taskTypeOrdinalMapping.put(411, "GOOGLE_FUNCTION_COMMAND_TASK");
    taskTypeOrdinalMapping.put(412, "GOOGLE_CLOUD_SOURCE_ARTIFACT_TASK_NG");
    taskTypeOrdinalMapping.put(413, "GCP_PROJECTS_TASK_NG");
    taskTypeOrdinalMapping.put(414, "GCS_BUCKETS_TASK_NG");
    taskTypeOrdinalMapping.put(415, "AWS_LAMBDA_DEPLOY_COMMAND_TASK_NG");
    taskTypeOrdinalMapping.put(416, "GOOGLE_FUNCTION_DEPLOY_TASK");
    taskTypeOrdinalMapping.put(417, "GOOGLE_FUNCTION_ROLLBACK_TASK");
    taskTypeOrdinalMapping.put(418, "GOOGLE_FUNCTION_PREPARE_ROLLBACK_TASK");
    taskTypeOrdinalMapping.put(419, "GOOGLE_FUNCTION_DEPLOY_WITHOUT_TRAFFIC_TASK");
    taskTypeOrdinalMapping.put(420, "GOOGLE_FUNCTION_TRAFFIC_SHIFT_TASK");
    taskTypeOrdinalMapping.put(421, "ECS_TASK_ARN_ROLLING_DEPLOY_NG");
    taskTypeOrdinalMapping.put(422, "ECS_TASK_ARN_CANARY_DEPLOY_NG");
    taskTypeOrdinalMapping.put(423, "ECS_TASK_ARN_BLUE_GREEN_CREATE_SERVICE_NG");
    taskTypeOrdinalMapping.put(424, "BAMBOO_CONNECTIVITY_TEST_TASK");
    taskTypeOrdinalMapping.put(425, "BAMBOO_ARTIFACT_TASK_NG");
    taskTypeOrdinalMapping.put(426, "AWS_LAMBDA_PREPARE_ROLLBACK_COMMAND_TASK_NG");
    taskTypeOrdinalMapping.put(427, "AWS_LAMBDA_ROLLBACK_COMMAND_TASK_NG");
    taskTypeOrdinalMapping.put(428, "TERRAFORM_TASK_NG_V3");
    taskTypeOrdinalMapping.put(429, "BAMBOO_TRIGGER_JOB");
    taskTypeOrdinalMapping.put(430, "TERRAFORM_TASK_NG_V4");
    taskTypeOrdinalMapping.put(431, "TERRAFORM_CLOUD_CLEANUP_TASK_NG");
    taskTypeOrdinalMapping.put(432, "OCI_HELM_DOCKER_API_LIST_TAGS_TASK_NG");
    taskTypeOrdinalMapping.put(433, "TAS_ROUTE_MAPPING");
    taskTypeConstantMapping =
        taskTypeOrdinalMapping.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testMappingExistsForAllEnumConstants() {
    Arrays.stream(TaskType.values()).forEach(taskType -> {
      if (!taskType.name().equals(taskTypeOrdinalMapping.get(taskType.ordinal()))) {
        Assertions.fail(String.format("Not all constants from enum [%s] mapped in test [%s].",
            TaskType.class.getCanonicalName(), this.getClass().getCanonicalName()));
      }
    });
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testEnumConstantAddedAtTheEndWithoutMapping() {
    if (TaskType.values().length > taskTypeOrdinalMapping.size()) {
      Arrays.stream(TaskType.values()).forEach(taskType -> {
        if (!taskType.name().equals(taskTypeOrdinalMapping.get(taskType.ordinal()))
            && !taskTypeConstantMapping.containsKey(taskType.name())
            && taskType.ordinal() >= taskTypeOrdinalMapping.size()) {
          Assertions.fail(String.format(
              "New constant added at the end of Enum [%s] at ordinal [%s] with name [%s]. This is expected for Kryo serialization/deserialization to work in the backward compatible manner. Please add this new enum constant mapping in test [%s].",
              TaskType.class.getCanonicalName(), taskType.ordinal(), taskType.name(),
              this.getClass().getCanonicalName()));
        }
      });
    }
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testEnumConstantNotAddedInBetween() {
    if (taskTypeOrdinalMapping.size() < TaskType.values().length) {
      Arrays.stream(TaskType.values()).forEach(taskType -> {
        if (!taskType.name().equals(taskTypeOrdinalMapping.get(taskType.ordinal()))
            && !taskTypeConstantMapping.containsKey(taskType.name())
            && taskType.ordinal() < taskTypeOrdinalMapping.size()) {
          Assertions.fail(String.format(
              "New constant added in Enum [%s] at ordinal [%s] with name [%s]. You have to add constant at the end for Kryo serialization/deserialization to work in the backward compatible manner.",
              TaskType.class.getCanonicalName(), taskType.ordinal(), taskType.name()));
        }
      });
    }
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testEnumConstantNotDeleted() {
    if (taskTypeOrdinalMapping.size() > TaskType.values().length) {
      Arrays.stream(TaskType.values()).forEach(taskType -> {
        if (!taskType.name().equals(taskTypeOrdinalMapping.get(taskType.ordinal()))
            && taskTypeConstantMapping.containsKey(taskType.name())) {
          Assertions.fail(String.format(
              "Constant deleted from Enum [%s] at ordinal [%s] with name [%s]. You should not delete constant for Kryo serialization/deserialization to work in the backward compatible manner.",
              TaskType.class.getCanonicalName(), taskType.ordinal(), taskType.name()));
        }
      });
    }
  }
}
