/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.features;

import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class FeatureNameTest extends WingsBaseTest {
  private Map<Integer, String> featureNameOrdinalMapping;
  private Map<String, Integer> featureNameConstantMapping;

  @Before
  public void setUp() {
    featureNameOrdinalMapping = new HashMap<>();
    featureNameOrdinalMapping.put(0, "SPG_UI_ALLOW_ENCODING_FOR_JENKINS_ARTIFACT");
    featureNameOrdinalMapping.put(1, "SPG_NG_GITHUB_WEBHOOK_AUTHENTICATION");
    featureNameOrdinalMapping.put(2, "SPG_ALLOW_DISABLE_TRIGGERS");
    featureNameOrdinalMapping.put(3, "SPG_ALLOW_DISABLE_USER_GITCONFIG");
    featureNameOrdinalMapping.put(4, "SPG_NEW_DEPLOYMENT_FREEZE_EXCLUSIONS");
    featureNameOrdinalMapping.put(5, "SPG_ENABLE_EMAIL_VALIDATION");
    featureNameOrdinalMapping.put(6, "DISABLE_HELM_REPO_YAML_CACHE");
    featureNameOrdinalMapping.put(7, "DEPRECATE_K8S_STEADY_STATE_CHECK_STEP");
    featureNameOrdinalMapping.put(8, "NG_GITOPS");
    featureNameOrdinalMapping.put(9, "ARGO_PHASE1");
    featureNameOrdinalMapping.put(10, "ARGO_PHASE2_MANAGED");
    featureNameOrdinalMapping.put(11, "ARTIFACT_PERPETUAL_TASK");
    featureNameOrdinalMapping.put(12, "ARTIFACT_PERPETUAL_TASK_MIGRATION");
    featureNameOrdinalMapping.put(13, "ARTIFACT_STREAM_DELEGATE_SCOPING");
    featureNameOrdinalMapping.put(14, "ARTIFACT_STREAM_DELEGATE_TIMEOUT");
    featureNameOrdinalMapping.put(15, "AUTO_ACCEPT_SAML_ACCOUNT_INVITES");
    featureNameOrdinalMapping.put(16, "AZURE_WEBAPP");
    featureNameOrdinalMapping.put(17, "BIND_FETCH_FILES_TASK_TO_DELEGATE");
    featureNameOrdinalMapping.put(18, "CCM_SUSTAINABILITY");
    featureNameOrdinalMapping.put(19, "CDNG_ENABLED");
    featureNameOrdinalMapping.put(20, "CENG_ENABLED");
    featureNameOrdinalMapping.put(21, "CE_SAMPLE_DATA_GENERATION");
    featureNameOrdinalMapping.put(22, "CE_HARNESS_ENTITY_MAPPING");
    featureNameOrdinalMapping.put(23, "CE_HARNESS_INSTANCE_QUERY");
    featureNameOrdinalMapping.put(24, "CE_GCP_CUSTOM_PRICING");
    featureNameOrdinalMapping.put(25, "CFNG_ENABLED");
    featureNameOrdinalMapping.put(26, "CF_CUSTOM_EXTRACTION");
    featureNameOrdinalMapping.put(27, "CF_ROLLBACK_CONFIG_FILTER");
    featureNameOrdinalMapping.put(28, "CING_ENABLED");
    featureNameOrdinalMapping.put(29, "CI_INDIRECT_LOG_UPLOAD");
    featureNameOrdinalMapping.put(30, "CI_LE_STATUS_REST_ENABLED");
    featureNameOrdinalMapping.put(31, "CUSTOM_DASHBOARD");
    featureNameOrdinalMapping.put(32, "CUSTOM_DEPLOYMENT_ARTIFACT_FROM_INSTANCE_JSON");
    featureNameOrdinalMapping.put(33, "CUSTOM_MAX_PAGE_SIZE");
    featureNameOrdinalMapping.put(34, "EXTRA_LARGE_PAGE_SIZE");
    featureNameOrdinalMapping.put(35, "CVNG_ENABLED");
    featureNameOrdinalMapping.put(36, "CV_DEMO");
    featureNameOrdinalMapping.put(37, "CV_HOST_SAMPLING");
    featureNameOrdinalMapping.put(38, "CV_SUCCEED_FOR_ANOMALY");
    featureNameOrdinalMapping.put(39, "DEFAULT_ARTIFACT");
    featureNameOrdinalMapping.put(40, "DEPLOY_TO_SPECIFIC_HOSTS");
    featureNameOrdinalMapping.put(41, "ENABLE_ADDING_SERVICE_VARS_TO_ECS_SPEC");
    featureNameOrdinalMapping.put(42, "DISABLE_LOGML_NEURAL_NET");
    featureNameOrdinalMapping.put(43, "DISABLE_METRIC_NAME_CURLY_BRACE_CHECK");
    featureNameOrdinalMapping.put(44, "DISABLE_SERVICEGUARD_LOG_ALERTS");
    featureNameOrdinalMapping.put(45, "DISABLE_WINRM_COMMAND_ENCODING");
    featureNameOrdinalMapping.put(46, "ENABLE_WINRM_ENV_VARIABLES");
    featureNameOrdinalMapping.put(47, "FF_PIPELINE");
    featureNameOrdinalMapping.put(48, "FF_GITSYNC");
    featureNameOrdinalMapping.put(49, "NG_TEMPLATE_GITX");
    featureNameOrdinalMapping.put(50, "FFM_1513");
    featureNameOrdinalMapping.put(51, "FFM_1512");
    featureNameOrdinalMapping.put(52, "FFM_1827");
    featureNameOrdinalMapping.put(53, "FFM_1859");
    featureNameOrdinalMapping.put(54, "FFM_2134_FF_PIPELINES_TRIGGER");
    featureNameOrdinalMapping.put(55, "FFM_3938_STALE_FLAGS_ACTIVE_CARD_HIDE_SHOW");
    featureNameOrdinalMapping.put(56, "FFM_4117_INTEGRATE_SRM");
    featureNameOrdinalMapping.put(57, "FFM_3959_FF_MFE_Environment_Detail");
    featureNameOrdinalMapping.put(58, "FFM_3961_ENHANCED_ONBOARDING");
    featureNameOrdinalMapping.put(59, "WINRM_COPY_CONFIG_OPTIMIZE");
    featureNameOrdinalMapping.put(60, "ECS_MULTI_LBS");
    featureNameOrdinalMapping.put(61, "ENTITY_AUDIT_RECORD");
    featureNameOrdinalMapping.put(62, "EXPORT_TF_PLAN");
    featureNameOrdinalMapping.put(63, "GCB_CI_SYSTEM");
    featureNameOrdinalMapping.put(64, "GCP_WORKLOAD_IDENTITY");
    featureNameOrdinalMapping.put(65, "GIT_HOST_CONNECTIVITY");
    featureNameOrdinalMapping.put(66, "GLOBAL_COMMAND_LIBRARY");
    featureNameOrdinalMapping.put(67, "GLOBAL_DISABLE_HEALTH_CHECK");
    featureNameOrdinalMapping.put(68, "GRAPHQL_DEV");
    featureNameOrdinalMapping.put(69, "HARNESS_TAGS");
    featureNameOrdinalMapping.put(70, "HELM_CHART_AS_ARTIFACT");
    featureNameOrdinalMapping.put(71, "HELM_CHART_NAME_SPLIT");
    featureNameOrdinalMapping.put(72, "HELM_MERGE_CAPABILITIES");
    featureNameOrdinalMapping.put(73, "INLINE_SSH_COMMAND");
    featureNameOrdinalMapping.put(74, "LIMIT_PCF_THREADS");
    featureNameOrdinalMapping.put(75, "OPA_FF_GOVERNANCE");
    featureNameOrdinalMapping.put(76, "OPA_GIT_GOVERNANCE");
    featureNameOrdinalMapping.put(77, "OPA_PIPELINE_GOVERNANCE");
    featureNameOrdinalMapping.put(78, "PCF_OLD_APP_RESIZE");
    featureNameOrdinalMapping.put(79, "LOCAL_DELEGATE_CONFIG_OVERRIDE");
    featureNameOrdinalMapping.put(80, "LOGS_V2_247");
    featureNameOrdinalMapping.put(81, "MOVE_AWS_AMI_INSTANCE_SYNC_TO_PERPETUAL_TASK");
    featureNameOrdinalMapping.put(82, "MOVE_AWS_AMI_SPOT_INST_INSTANCE_SYNC_TO_PERPETUAL_TASK");
    featureNameOrdinalMapping.put(83, "MOVE_AWS_CODE_DEPLOY_INSTANCE_SYNC_TO_PERPETUAL_TASK");
    featureNameOrdinalMapping.put(84, "MOVE_AWS_LAMBDA_INSTANCE_SYNC_TO_PERPETUAL_TASK");
    featureNameOrdinalMapping.put(85, "MOVE_AWS_SSH_INSTANCE_SYNC_TO_PERPETUAL_TASK");
    featureNameOrdinalMapping.put(86, "MOVE_CONTAINER_INSTANCE_SYNC_TO_PERPETUAL_TASK");
    featureNameOrdinalMapping.put(87, "MOVE_PCF_INSTANCE_SYNC_TO_PERPETUAL_TASK");
    featureNameOrdinalMapping.put(88, "PDC_PERPETUAL_TASK");
    featureNameOrdinalMapping.put(89, "NG_DASHBOARDS");
    featureNameOrdinalMapping.put(90, "NODE_RECOMMENDATION_AGGREGATE");
    featureNameOrdinalMapping.put(91, "ON_NEW_ARTIFACT_TRIGGER_WITH_LAST_COLLECTED_FILTER");
    featureNameOrdinalMapping.put(92, "OUTAGE_CV_DISABLE");
    featureNameOrdinalMapping.put(93, "OVERRIDE_VALUES_YAML_FROM_HELM_CHART");
    featureNameOrdinalMapping.put(94, "PIPELINE_GOVERNANCE");
    featureNameOrdinalMapping.put(95, "PRUNE_KUBERNETES_RESOURCES");
    featureNameOrdinalMapping.put(96, "REJECT_TRIGGER_IF_ARTIFACTS_NOT_MATCH");
    featureNameOrdinalMapping.put(97, "ROLLBACK_NONE_ARTIFACT");
    featureNameOrdinalMapping.put(98, "SEARCH_REQUEST");
    featureNameOrdinalMapping.put(99, "SEND_SLACK_NOTIFICATION_FROM_DELEGATE");
    featureNameOrdinalMapping.put(100, "SIDE_NAVIGATION");
    featureNameOrdinalMapping.put(101, "SKIP_SWITCH_ACCOUNT_REAUTHENTICATION");
    featureNameOrdinalMapping.put(102, "SLACK_APPROVALS");
    featureNameOrdinalMapping.put(103, "STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_AMI_DEPLOYMENTS");
    featureNameOrdinalMapping.put(104, "STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_AMI_SPOT_INST_DEPLOYMENTS");
    featureNameOrdinalMapping.put(105, "STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_CODE_DEPLOY_DEPLOYMENTS");
    featureNameOrdinalMapping.put(106, "STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_LAMBDA_DEPLOYMENTS");
    featureNameOrdinalMapping.put(107, "STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_SSH_DEPLOYMENTS");
    featureNameOrdinalMapping.put(108, "STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_PDC_DEPLOYMENTS");
    featureNameOrdinalMapping.put(109, "STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AZURE_INFRA_DEPLOYMENTS");
    featureNameOrdinalMapping.put(110, "STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_CONTAINER_DEPLOYMENTS");
    featureNameOrdinalMapping.put(111, "STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_PCF_DEPLOYMENTS");
    featureNameOrdinalMapping.put(112, "SUPERVISED_TS_THRESHOLD");
    featureNameOrdinalMapping.put(113, "THREE_PHASE_SECRET_DECRYPTION");
    featureNameOrdinalMapping.put(114, "TIME_RANGE_FREEZE_GOVERNANCE");
    featureNameOrdinalMapping.put(115, "TRIGGER_FOR_ALL_ARTIFACTS");
    featureNameOrdinalMapping.put(116, "TRIGGER_YAML");
    featureNameOrdinalMapping.put(117, "USE_NEXUS3_PRIVATE_APIS");
    featureNameOrdinalMapping.put(118, "ENABLE_CVNG_INTEGRATION");
    featureNameOrdinalMapping.put(119, "DYNATRACE_MULTI_SERVICE");
    featureNameOrdinalMapping.put(120, "REFACTOR_STATEMACHINEXECUTOR");
    featureNameOrdinalMapping.put(121, "WORKFLOW_DATA_COLLECTION_ITERATOR");
    featureNameOrdinalMapping.put(122, "ENABLE_CERT_VALIDATION");
    featureNameOrdinalMapping.put(123, "RESOURCE_CONSTRAINT_MAX_QUEUE");
    featureNameOrdinalMapping.put(124, "AWS_OVERRIDE_REGION");
    featureNameOrdinalMapping.put(125, "CLEAN_UP_OLD_MANAGER_VERSIONS");
    featureNameOrdinalMapping.put(126, "ECS_AUTOSCALAR_REDESIGN");
    featureNameOrdinalMapping.put(127, "SAVE_SHELL_SCRIPT_PROVISION_OUTPUTS_TO_SWEEPING_OUTPUT");
    featureNameOrdinalMapping.put(128, "SAVE_TERRAFORM_OUTPUTS_TO_SWEEPING_OUTPUT");
    featureNameOrdinalMapping.put(129, "SAVE_TERRAFORM_APPLY_SWEEPING_OUTPUT_TO_WORKFLOW");
    featureNameOrdinalMapping.put(130, "NEW_DEPLOYMENT_FREEZE");
    featureNameOrdinalMapping.put(131, "ECS_REGISTER_TASK_DEFINITION_TAGS");
    featureNameOrdinalMapping.put(132, "CUSTOM_DASHBOARD_INSTANCE_FETCH_LONGER_RETENTION_DATA");
    featureNameOrdinalMapping.put(133, "CUSTOM_DASHBOARD_DEPLOYMENT_FETCH_LONGER_RETENTION_DATA");
    featureNameOrdinalMapping.put(134, "CUSTOM_DASHBOARD_ENABLE_REALTIME_INSTANCE_AGGREGATION");
    featureNameOrdinalMapping.put(135, "CUSTOM_DASHBOARD_ENABLE_REALTIME_DEPLOYMENT_MIGRATION");
    featureNameOrdinalMapping.put(136, "CUSTOM_DASHBOARD_ENABLE_CRON_INSTANCE_DATA_MIGRATION");
    featureNameOrdinalMapping.put(137, "CUSTOM_DASHBOARD_ENABLE_CRON_DEPLOYMENT_DATA_MIGRATION");
    featureNameOrdinalMapping.put(138, "SSH_SECRET_ENGINE");
    featureNameOrdinalMapping.put(139, "WHITELIST_PUBLIC_API");
    featureNameOrdinalMapping.put(140, "WHITELIST_GRAPHQL");
    featureNameOrdinalMapping.put(141, "TIMEOUT_FAILURE_SUPPORT");
    featureNameOrdinalMapping.put(142, "LOG_APP_DEFAULTS");
    featureNameOrdinalMapping.put(143, "ENABLE_LOGIN_AUDITS");
    featureNameOrdinalMapping.put(144, "CUSTOM_MANIFEST");
    featureNameOrdinalMapping.put(145, "WEBHOOK_TRIGGER_AUTHORIZATION");
    featureNameOrdinalMapping.put(146, "ENHANCED_GCR_CONNECTIVITY_CHECK");
    featureNameOrdinalMapping.put(147, "USE_TF_CLIENT");
    featureNameOrdinalMapping.put(148, "GITHUB_WEBHOOK_AUTHENTICATION");
    featureNameOrdinalMapping.put(149, "NG_LICENSES_ENABLED");
    featureNameOrdinalMapping.put(150, "ECS_BG_DOWNSIZE");
    featureNameOrdinalMapping.put(151, "LIMITED_ACCESS_FOR_HARNESS_USER_GROUP");
    featureNameOrdinalMapping.put(152, "REMOVE_STENCIL_MANUAL_INTERVENTION");
    featureNameOrdinalMapping.put(153, "CI_OVERVIEW_PAGE");
    featureNameOrdinalMapping.put(154, "SKIP_BASED_ON_STACK_STATUSES");
    featureNameOrdinalMapping.put(155, "WF_VAR_MULTI_SELECT_ALLOWED_VALUES");
    featureNameOrdinalMapping.put(156, "CF_CLI7");
    featureNameOrdinalMapping.put(157, "CF_APP_NON_VERSIONING_INACTIVE_ROLLBACK");
    featureNameOrdinalMapping.put(158, "CF_ALLOW_SPECIAL_CHARACTERS");
    featureNameOrdinalMapping.put(159, "HTTP_HEADERS_CAPABILITY_CHECK");
    featureNameOrdinalMapping.put(160, "AMI_IN_SERVICE_HEALTHY_WAIT");
    featureNameOrdinalMapping.put(161, "SETTINGS_OPTIMIZATION");
    featureNameOrdinalMapping.put(162, "CG_SECRET_MANAGER_DELEGATE_SELECTORS");
    featureNameOrdinalMapping.put(163, "ARTIFACT_COLLECTION_CONFIGURABLE");
    featureNameOrdinalMapping.put(164, "ROLLBACK_PROVISIONER_AFTER_PHASES");
    featureNameOrdinalMapping.put(165, "FEATURE_ENFORCEMENT_ENABLED");
    featureNameOrdinalMapping.put(166, "FREE_PLAN_ENFORCEMENT_ENABLED");
    featureNameOrdinalMapping.put(167, "VIEW_USAGE_ENABLED");
    featureNameOrdinalMapping.put(168, "SOCKET_HTTP_STATE_TIMEOUT");
    featureNameOrdinalMapping.put(169, "TERRAFORM_CONFIG_INSPECT_VERSION_SELECTOR");
    featureNameOrdinalMapping.put(170, "VALIDATE_PROVISIONER_EXPRESSION");
    featureNameOrdinalMapping.put(171, "WORKFLOW_PIPELINE_PERMISSION_BY_ENTITY");
    featureNameOrdinalMapping.put(172, "AMAZON_ECR_AUTH_REFACTOR");
    featureNameOrdinalMapping.put(173, "AMI_ASG_CONFIG_COPY");
    featureNameOrdinalMapping.put(174, "OPTIMIZED_GIT_FETCH_FILES");
    featureNameOrdinalMapping.put(175, "CVNG_VERIFY_STEP_DEMO");
    featureNameOrdinalMapping.put(176, "CVNG_MONITORED_SERVICE_DEMO");
    featureNameOrdinalMapping.put(177, "MANIFEST_INHERIT_FROM_CANARY_TO_PRIMARY_PHASE");
    featureNameOrdinalMapping.put(178, "USE_LATEST_CHARTMUSEUM_VERSION");
    featureNameOrdinalMapping.put(179, "NEW_KUSTOMIZE_BINARY");
    featureNameOrdinalMapping.put(180, "KUSTOMIZE_PATCHES_CG");
    featureNameOrdinalMapping.put(181, "SSH_JSCH_LOGS");
    featureNameOrdinalMapping.put(182, "RESOLVE_DEPLOYMENT_TAGS_BEFORE_EXECUTION");
    featureNameOrdinalMapping.put(183, "LDAP_USER_ID_SYNC");
    featureNameOrdinalMapping.put(184, "NEW_KUBECTL_VERSION");
    featureNameOrdinalMapping.put(185, "CUSTOM_DASHBOARD_V2");
    featureNameOrdinalMapping.put(186, "TIME_SCALE_CG_SYNC");
    featureNameOrdinalMapping.put(187, "CI_INCREASE_DEFAULT_RESOURCES");
    featureNameOrdinalMapping.put(188, "DISABLE_DEPLOYMENTS_SEARCH_AND_LIMIT_DEPLOYMENT_STATS");
    featureNameOrdinalMapping.put(189, "RATE_LIMITED_TOTP");
    featureNameOrdinalMapping.put(190, "CLOSE_TIME_SCALE_SYNC_PROCESSING_ON_FAILURE");
    featureNameOrdinalMapping.put(191, "RESOURCE_CENTER_ENABLED");
    featureNameOrdinalMapping.put(192, "USE_IMMUTABLE_DELEGATE");
    featureNameOrdinalMapping.put(193, "ACTIVE_MIGRATION_FROM_LOCAL_TO_GCP_KMS");
    featureNameOrdinalMapping.put(194, "TERRAFORM_AWS_CP_AUTHENTICATION");
    featureNameOrdinalMapping.put(195, "CI_DOCKER_INFRASTRUCTURE");
    featureNameOrdinalMapping.put(196, "CI_TESTTAB_NAVIGATION");
    featureNameOrdinalMapping.put(197, "CI_DISABLE_GIT_SAFEDIR");
    featureNameOrdinalMapping.put(198, "OPTIMIZED_TF_PLAN");
    featureNameOrdinalMapping.put(199, "SELF_SERVICE_ENABLED");
    featureNameOrdinalMapping.put(200, "CLOUDFORMATION_SKIP_WAIT_FOR_RESOURCES");
    featureNameOrdinalMapping.put(201, "CLOUDFORMATION_CHANGE_SET");
    featureNameOrdinalMapping.put(202, "FAIL_WORKFLOW_IF_SECRET_DECRYPTION_FAILS");
    featureNameOrdinalMapping.put(203, "DEPLOY_TO_INLINE_HOSTS");
    featureNameOrdinalMapping.put(204, "HONOR_DELEGATE_SCOPING");
    featureNameOrdinalMapping.put(205, "CG_LICENSE_USAGE");
    featureNameOrdinalMapping.put(206, "RANCHER_SUPPORT");
    featureNameOrdinalMapping.put(207, "BYPASS_HELM_FETCH");
    featureNameOrdinalMapping.put(208, "FREEZE_DURING_MIGRATION");
    featureNameOrdinalMapping.put(209, "USE_ANALYTIC_MONGO_FOR_GRAPHQL_QUERY");
    featureNameOrdinalMapping.put(210, "CCM_AS_DRY_RUN");
    featureNameOrdinalMapping.put(211, "CCM_COMMORCH");
    featureNameOrdinalMapping.put(212, "CCM_SUNSETTING_CG");
    featureNameOrdinalMapping.put(213, "RECOMMENDATION_EFFICIENCY_VIEW_UI");
    featureNameOrdinalMapping.put(214, "CLOUD_COST_GOVERNANCE_UI");
    featureNameOrdinalMapping.put(215, "DONT_RESTRICT_PARALLEL_STAGE_COUNT");
    featureNameOrdinalMapping.put(216, "NG_EXECUTION_INPUT");
    featureNameOrdinalMapping.put(217, "SKIP_ADDING_TRACK_LABEL_SELECTOR_IN_ROLLING");
    featureNameOrdinalMapping.put(218, "EXTERNAL_USERID_BASED_LOGIN");
    featureNameOrdinalMapping.put(219, "LDAP_SYNC_WITH_USERID");
    featureNameOrdinalMapping.put(220, "DISABLE_HARNESS_SM");
    featureNameOrdinalMapping.put(221, "SECURITY");
    featureNameOrdinalMapping.put(222, "SECURITY_STAGE");
    featureNameOrdinalMapping.put(223, "STO_CI_PIPELINE_SECURITY");
    featureNameOrdinalMapping.put(224, "STO_CD_PIPELINE_SECURITY");
    featureNameOrdinalMapping.put(225, "STO_API_V2");
    featureNameOrdinalMapping.put(226, "REFACTOR_ARTIFACT_SELECTION");
    featureNameOrdinalMapping.put(227, "CCM_DEV_TEST");
    featureNameOrdinalMapping.put(228, "CV_FAIL_ON_EMPTY_NODES");
    featureNameOrdinalMapping.put(229, "SHOW_REFINER_FEEDBACK");
    featureNameOrdinalMapping.put(230, "SHOW_NG_REFINER_FEEDBACK");
    featureNameOrdinalMapping.put(231, "NG_NEXUS_ARTIFACTORY");
    featureNameOrdinalMapping.put(232, "HELM_VERSION_3_8_0");
    featureNameOrdinalMapping.put(233, "DELEGATE_ENABLE_DYNAMIC_HANDLING_OF_REQUEST");
    featureNameOrdinalMapping.put(234, "YAML_GIT_CONNECTOR_NAME");
    featureNameOrdinalMapping.put(235, "STOP_SHOWING_RUNNING_EXECUTIONS");
    featureNameOrdinalMapping.put(236, "SSH_NG");
    featureNameOrdinalMapping.put(237, "ARTIFACT_STREAM_METADATA_ONLY");
    featureNameOrdinalMapping.put(238, "OUTCOME_GRAPHQL_WITH_INFRA_DEF");
    featureNameOrdinalMapping.put(239, "AUTO_REJECT_PREVIOUS_APPROVALS");
    featureNameOrdinalMapping.put(240, "BIND_CUSTOM_VALUE_AND_MANIFEST_FETCH_TASK");
    featureNameOrdinalMapping.put(241, "AZURE_BLOB_SM");
    featureNameOrdinalMapping.put(242, "CONSIDER_ORIGINAL_STATE_VERSION");
    featureNameOrdinalMapping.put(243, "SINGLE_MANIFEST_SUPPORT");
    featureNameOrdinalMapping.put(244, "ENV_GROUP");
    featureNameOrdinalMapping.put(245, "REDUCE_DELEGATE_MEMORY_SIZE");
    featureNameOrdinalMapping.put(246, "PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION");
    featureNameOrdinalMapping.put(247, "DISABLE_LOCAL_LOGIN");
    featureNameOrdinalMapping.put(248, "WINRM_KERBEROS_CACHE_UNIQUE_FILE");
    featureNameOrdinalMapping.put(249, "HIDE_ABORT");
    featureNameOrdinalMapping.put(250, "CUSTOM_ARTIFACT_NG");
    featureNameOrdinalMapping.put(251, "APPLICATION_DROPDOWN_MULTISELECT");
    featureNameOrdinalMapping.put(252, "NG_GIT_EXPERIENCE");
    featureNameOrdinalMapping.put(253, "LDAP_SECRET_AUTH");
    featureNameOrdinalMapping.put(254, "WORKFLOW_EXECUTION_REFRESH_STATUS");
    featureNameOrdinalMapping.put(255, "TRIGGERS_PAGE_PAGINATION");
    featureNameOrdinalMapping.put(256, "STALE_FLAGS_FFM_1510");
    featureNameOrdinalMapping.put(257, "NG_SVC_ENV_REDESIGN");
    featureNameOrdinalMapping.put(258, "NEW_PIPELINE_STUDIO");
    featureNameOrdinalMapping.put(259, "EARLY_ACCESS_ENABLED");
    featureNameOrdinalMapping.put(260, "HELP_PANEL");
    featureNameOrdinalMapping.put(261, "CHAOS_ENABLED");
    featureNameOrdinalMapping.put(262, "DEPLOYMENT_SUBFORMIK_APPLICATION_DROPDOWN");
    featureNameOrdinalMapping.put(263, "USAGE_SCOPE_RBAC");
    featureNameOrdinalMapping.put(264, "ALLOW_USER_TYPE_FIELDS_JIRA");
    featureNameOrdinalMapping.put(265, "ACTIVITY_ID_BASED_TF_BASE_DIR");
    featureNameOrdinalMapping.put(266, "JDK11_UPGRADE_BANNER");
    featureNameOrdinalMapping.put(267, "DISABLE_CI_STAGE_DEL_SELECTOR");
    featureNameOrdinalMapping.put(268, "JENKINS_ARTIFACT");
    featureNameOrdinalMapping.put(269, "ENABLE_DEFAULT_TIMEFRAME_IN_DEPLOYMENTS");
    featureNameOrdinalMapping.put(270, "ADD_MANIFEST_COLLECTION_STEP");
    featureNameOrdinalMapping.put(271, "ACCOUNT_BASIC_ROLE");
    featureNameOrdinalMapping.put(272, "CVNG_TEMPLATE_MONITORED_SERVICE");
    featureNameOrdinalMapping.put(273, "CVNG_TEMPLATE_VERIFY_STEP");
    featureNameOrdinalMapping.put(274, "CVNG_METRIC_THRESHOLD");
    featureNameOrdinalMapping.put(275, "WORKFLOW_EXECUTION_ZOMBIE_MONITOR");
    featureNameOrdinalMapping.put(276, "USE_PAGINATED_ENCRYPT_SERVICE");
    featureNameOrdinalMapping.put(277, "INFRA_MAPPING_BASED_ROLLBACK_ARTIFACT");
    featureNameOrdinalMapping.put(278, "DEPLOYMENT_SUBFORMIK_PIPELINE_DROPDOWN");
    featureNameOrdinalMapping.put(279, "DEPLOYMENT_SUBFORMIK_WORKFLOW_DROPDOWN");
    featureNameOrdinalMapping.put(280, "TI_DOTNET");
    featureNameOrdinalMapping.put(281, "TG_USE_AUTO_APPROVE_FLAG");
    featureNameOrdinalMapping.put(282, "CVNG_SPLUNK_METRICS");
    featureNameOrdinalMapping.put(283, "AUTO_FREE_MODULE_LICENSE");
    featureNameOrdinalMapping.put(284, "SRM_LICENSE_ENABLED");
    featureNameOrdinalMapping.put(285, "ACCOUNT_BASIC_ROLE_ONLY");
    featureNameOrdinalMapping.put(286, "SEARCH_USERGROUP_BY_APPLICATION");
    featureNameOrdinalMapping.put(287, "GITOPS_BYO_ARGO");
    featureNameOrdinalMapping.put(288, "CCM_MICRO_FRONTEND");
    featureNameOrdinalMapping.put(289, "CVNG_LICENSE_ENFORCEMENT");
    featureNameOrdinalMapping.put(290, "CVNG_SLO_DISABLE_ENABLE");
    featureNameOrdinalMapping.put(291, "SERVICE_DASHBOARD_V2");
    featureNameOrdinalMapping.put(292, "DEBEZIUM_ENABLED");
    featureNameOrdinalMapping.put(293, "TEMPLATE_SCHEMA_VALIDATION");
    featureNameOrdinalMapping.put(294, "YAML_APIS_GRANULAR_PERMISSION");
    featureNameOrdinalMapping.put(295, "JENKINS_BUILD");
    featureNameOrdinalMapping.put(296, "AZURE_ARTIFACTS_NG");
    featureNameOrdinalMapping.put(297, "CD_AMI_ARTIFACTS_NG");
    featureNameOrdinalMapping.put(298, "GITHUB_PACKAGES");
    featureNameOrdinalMapping.put(299, "DO_NOT_RENEW_APPROLE_TOKEN");
    featureNameOrdinalMapping.put(300, "ENABLE_DEFAULT_NG_EXPERIENCE_FOR_ONPREM");
    featureNameOrdinalMapping.put(301, "NG_SETTINGS");
    featureNameOrdinalMapping.put(302, "QUEUED_COUNT_FOR_QUEUEKEY");
    featureNameOrdinalMapping.put(303, "NG_EMAIL_STEP");
    featureNameOrdinalMapping.put(304, "NG_GOOGLE_ARTIFACT_REGISTRY");
    featureNameOrdinalMapping.put(305, "USE_OLD_GIT_SYNC");
    featureNameOrdinalMapping.put(306, "DISABLE_PIPELINE_SCHEMA_VALIDATION");
    featureNameOrdinalMapping.put(307, "USE_K8S_API_FOR_STEADY_STATE_CHECK");
    featureNameOrdinalMapping.put(308, "WINRM_ASG_ROLLBACK");
    featureNameOrdinalMapping.put(309, "NEW_LEFT_NAVBAR_SETTINGS");
    featureNameOrdinalMapping.put(310, "SAVE_ARTIFACT_TO_DB");
    featureNameOrdinalMapping.put(311, "NG_INLINE_MANIFEST");
    featureNameOrdinalMapping.put(312, "CI_DISABLE_RESOURCE_OPTIMIZATION");
    featureNameOrdinalMapping.put(313, "ENABLE_EXPERIMENTAL_STEP_FAILURE_STRATEGIES");
    featureNameOrdinalMapping.put(314, "REMOVE_USERGROUP_CHECK");
    featureNameOrdinalMapping.put(315, "STO_STEP_PALETTE_V1");
    featureNameOrdinalMapping.put(316, "STO_STEP_PALETTE_V2");
    featureNameOrdinalMapping.put(317, "HOSTED_BUILDS");
    featureNameOrdinalMapping.put(318, "CD_ONBOARDING_ENABLED");
    featureNameOrdinalMapping.put(319, "SPOT_ELASTIGROUP_NG");
    featureNameOrdinalMapping.put(320, "ATTRIBUTE_TYPE_ACL_ENABLED");
    featureNameOrdinalMapping.put(321, "CREATE_DEFAULT_PROJECT");
    featureNameOrdinalMapping.put(322, "ANALYSE_TF_PLAN_SUMMARY");
    featureNameOrdinalMapping.put(323, "TERRAFORM_REMOTE_BACKEND_CONFIG");
    featureNameOrdinalMapping.put(324, "REMOVE_HINT_YAML_GIT_COMMITS");
    featureNameOrdinalMapping.put(325, "FIXED_INSTANCE_ZERO_ALLOW");
    featureNameOrdinalMapping.put(326, "USE_PAGINATED_ENCRYPT_FOR_VARIABLE_OVERRIDES");
    featureNameOrdinalMapping.put(327, "ON_DEMAND_ROLLBACK_WITH_DIFFERENT_ARTIFACT");
    featureNameOrdinalMapping.put(328, "CG_GIT_POLLING");
    featureNameOrdinalMapping.put(329, "GRAPHQL_WORKFLOW_EXECUTION_OPTIMIZATION");
    featureNameOrdinalMapping.put(330, "NG_ENABLE_LDAP_CHECK");
    featureNameOrdinalMapping.put(331, "CUSTOM_SECRET_MANAGER_NG");
    featureNameOrdinalMapping.put(332, "AZURE_ARM_BP_NG");
    featureNameOrdinalMapping.put(333, "CV_AWS_PROMETHEUS");
    featureNameOrdinalMapping.put(334, "CD_GIT_WEBHOOK_POLLING");
    featureNameOrdinalMapping.put(335, "MULTI_SERVICE_INFRA");
    featureNameOrdinalMapping.put(336, "CD_TRIGGERS_REFACTOR");
    featureNameOrdinalMapping.put(337, "SORT_ARTIFACTS_IN_UPDATED_ORDER");
    featureNameOrdinalMapping.put(338, "ENABLE_CHECK_STATE_EXECUTION_STARTING");
    featureNameOrdinalMapping.put(339, "CI_TI_DASHBOARDS_ENABLED");
    featureNameOrdinalMapping.put(340, "SERVICE_ID_FILTER_FOR_TRIGGERS");
    featureNameOrdinalMapping.put(341, "PERSIST_MONITORED_SERVICE_TEMPLATE_STEP");
    featureNameOrdinalMapping.put(342, "VALIDATE_PHASES_AND_ROLLBACK");
    featureNameOrdinalMapping.put(343, "OPTIMIZED_TF_PLAN_NG");
    featureNameOrdinalMapping.put(344, "CIE_HOSTED_VMS");
    featureNameOrdinalMapping.put(345, "CHANGE_INSTANCE_QUERY_OPERATOR_TO_NE");
    featureNameOrdinalMapping.put(346, "NEXUS3_RAW_REPOSITORY");
    featureNameOrdinalMapping.put(347, "NG_ARTIFACT_SOURCES");
    featureNameOrdinalMapping.put(348, "UPDATE_EMAILS_VIA_SCIM");
    featureNameOrdinalMapping.put(349, "ELK_HEALTH_SOURCE");
    featureNameOrdinalMapping.put(350, "SRM_COMPOSITE_SLO");
    featureNameOrdinalMapping.put(351, "PIPELINE_CHAINING");
    featureNameOrdinalMapping.put(352, "PIPELINE_ROLLBACK");
    featureNameOrdinalMapping.put(353, "MERGE_RUNTIME_VARIABLES_IN_RESUME");
    featureNameOrdinalMapping.put(354, "USE_TEXT_SEARCH_FOR_EXECUTION");
    featureNameOrdinalMapping.put(355, "AZURE_WEBAPP_NG_JENKINS_ARTIFACTS");
    featureNameOrdinalMapping.put(356, "AZURE_WEBAPP_NG_AZURE_DEVOPS_ARTIFACTS");
    featureNameOrdinalMapping.put(357, "DEL_EVALUATE_SECRET_EXPRESSION_SYNC");
    featureNameOrdinalMapping.put(358, "SRM_ENABLE_HEALTHSOURCE_CLOUDWATCH_METRICS");
    featureNameOrdinalMapping.put(359, "SRM_ENABLE_VERIFY_STEP_LONG_DURATION");
    featureNameOrdinalMapping.put(360, "SETTING_ATTRIBUTES_SERVICE_ACCOUNT_TOKEN_MIGRATION");
    featureNameOrdinalMapping.put(361, "ARTIFACT_SOURCE_TEMPLATE");
    featureNameOrdinalMapping.put(362, "LOOKER_ENTITY_RECONCILIATION");
    featureNameOrdinalMapping.put(363, "STAGE_AND_STEP_LEVEL_DEPLOYMENT_DETAILS");
    featureNameOrdinalMapping.put(364, "NG_DEPLOYMENT_FREEZE");
    featureNameOrdinalMapping.put(365, "NG_DEPLOYMENT_FREEZE_OVERRIDE");
    featureNameOrdinalMapping.put(366, "PL_ENABLE_SWITCH_ACCOUNT_PAGINATION");
    featureNameOrdinalMapping.put(367, "SHELL_SCRIPT_PROVISION_NG");
    featureNameOrdinalMapping.put(368, "NEW_EXECUTION_LIST_VIEW");
    featureNameOrdinalMapping.put(369, "PL_ACCESS_SECRET_DYNAMICALLY_BY_PATH");
    featureNameOrdinalMapping.put(370, "PL_NO_EMAIL_FOR_SAML_ACCOUNT_INVITES");
    featureNameOrdinalMapping.put(371, "PL_ENABLE_GOOGLE_SECRET_MANAGER_IN_NG");
    featureNameOrdinalMapping.put(372, "SPG_2K_DEFAULT_PAGE_SIZE");
    featureNameOrdinalMapping.put(373, "SPG_DISABLE_SEARCH_DEPLOYMENTS_PAGE");
    featureNameOrdinalMapping.put(374, "WINRM_SCRIPT_COMMAND_SPLIT");
    featureNameOrdinalMapping.put(375, "SPG_USE_NEW_METADATA");
    featureNameOrdinalMapping.put(376, "SPG_OPTIMIZE_WORKFLOW_EXECUTIONS_LISTING");
    featureNameOrdinalMapping.put(377, "SPG_OPTIMIZE_CONCILIATION_QUERY");
    featureNameOrdinalMapping.put(378, "CD_SERVICE_ENV_RECONCILIATION");
    featureNameOrdinalMapping.put(379, "CD_TRIGGER_CATALOG");
    featureNameOrdinalMapping.put(380, "SRM_HOST_SAMPLING_ENABLE");
    featureNameOrdinalMapping.put(381, "CDS_SHOW_CREATE_PR");
    featureNameOrdinalMapping.put(382, "SPG_PIPELINE_ROLLBACK");
    featureNameOrdinalMapping.put(383, "PL_FORCE_DELETE_CONNECTOR_SECRET");
    featureNameOrdinalMapping.put(384, "PL_CONNECTOR_ENCRYPTION_PRIVILEGED_CALL");
    featureNameOrdinalMapping.put(385, "SPG_DASHBOARD_STATS_OPTIMIZE_DEPLOYMENTS");
    featureNameOrdinalMapping.put(386, "SPG_DASHBOARD_STATS_OPTIMIZE_ACTIVE_SERVICES");
    featureNameOrdinalMapping.put(387, "SPG_LIVE_DASHBOARD_STATS_DEBUGGING");
    featureNameOrdinalMapping.put(388, "TI_MFE_ENABLED");
    featureNameOrdinalMapping.put(389, "CI_CACHE_INTELLIGENCE");
    featureNameOrdinalMapping.put(390, "SPG_ENFORCE_TIME_RANGE_DEPLOYMENTS_WITHOUT_APP_ID");
    featureNameOrdinalMapping.put(391, "SPG_REDUCE_KEYWORDS_PERSISTENCE_ON_EXECUTIONS");
    featureNameOrdinalMapping.put(392, "SYNC_GIT_CLONE_AND_COPY_TO_DEST_DIR");
    featureNameOrdinalMapping.put(393, "ECS_ROLLBACK_MAX_DESIRED_COUNT");
    featureNameOrdinalMapping.put(394, "CI_YAML_VERSIONING");
    featureNameOrdinalMapping.put(395, "SRM_ET_EXPERIMENTAL");
    featureNameOrdinalMapping.put(396, "SRM_CODE_ERROR_NOTIFICATIONS");
    featureNameOrdinalMapping.put(397, "SRM_ENABLE_HEALTHSOURCE_AWS_PROMETHEUS");
    featureNameOrdinalMapping.put(398, "DEL_SECRET_EVALUATION_VERBOSE_LOGGING");
    featureNameOrdinalMapping.put(399, "CI_MFE_ENABLED");
    featureNameOrdinalMapping.put(400, "INSTANCE_SYNC_V2_CG");
    featureNameOrdinalMapping.put(401, "CF_ROLLBACK_CUSTOM_STACK_NAME");
    featureNameOrdinalMapping.put(402, "IACM_MICRO_FE");
    featureNameOrdinalMapping.put(403, "AZURE_WEB_APP_NG_NEXUS_PACKAGE");
    featureNameOrdinalMapping.put(404, "BOOKING_RECOMMENDATIONS");
    featureNameOrdinalMapping.put(405, "USE_GET_FILE_V2_GIT_CALL");
    featureNameOrdinalMapping.put(406, "SPG_CD_RUN_STEP");
    featureNameOrdinalMapping.put(407, "GITOPS_ONPREM_ENABLED");
    featureNameOrdinalMapping.put(408, "CIE_HOSTED_VMS_MAC");
    featureNameOrdinalMapping.put(409, "SPG_DELETE_ENVIRONMENTS_ON_SERVICE_RENAME_GIT_SYNC");
    featureNameOrdinalMapping.put(410, "GITOPS_API_PARAMS_MERGE_PR");
    featureNameOrdinalMapping.put(411, "PL_HIDE_LAUNCH_NEXTGEN");
    featureNameOrdinalMapping.put(412, "PL_LDAP_PARALLEL_GROUP_SYNC");
    featureNameOrdinalMapping.put(413, "CDS_OrgAccountLevelServiceEnvEnvGroup");
    featureNameOrdinalMapping.put(414, "CE_NET_AMORTISED_COST_ENABLED");
    featureNameOrdinalMapping.put(415, "GITOPS_DR_ENABLED");
    featureNameOrdinalMapping.put(416, "GITOPS_RECONCILER_ENABLED");
    featureNameOrdinalMapping.put(417, "CE_RERUN_HOURLY_JOBS");
    featureNameOrdinalMapping.put(418, "SPG_WFE_OPTIMIZE_WORKFLOW_LISTING");
    featureNameOrdinalMapping.put(419, "SPG_OPTIMIZE_PIPELINE_QUERY_ON_AUTH");
    featureNameOrdinalMapping.put(420, "SPG_NG_CUSTOM_WEBHOOK_AUTHORIZATION");

    featureNameConstantMapping =
        featureNameOrdinalMapping.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testMappingExistsForAllEnumConstants() {
    Arrays.stream(FeatureName.values()).forEach(taskType -> {
      if (!taskType.name().equals(featureNameOrdinalMapping.get(taskType.ordinal()))) {
        Assertions.fail(String.format("Not all constants from enum [%s] mapped in test [%s].",
            FeatureName.class.getCanonicalName(), this.getClass().getCanonicalName()));
      }
    });
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testEnumConstantAddedAtTheEndWithoutMapping() {
    if (FeatureName.values().length > featureNameOrdinalMapping.size()) {
      Arrays.stream(FeatureName.values()).forEach(taskType -> {
        if (!taskType.name().equals(featureNameOrdinalMapping.get(taskType.ordinal()))
            && !featureNameConstantMapping.containsKey(taskType.name())
            && taskType.ordinal() >= featureNameOrdinalMapping.size()) {
          Assertions.fail(String.format(
              "New constant added at the end of Enum [%s] at ordinal [%s] with name [%s]. This is expected for Kryo serialization/deserialization to work in the backward compatible manner. Please add this new enum constant mapping in test [%s].",
              FeatureName.class.getCanonicalName(), taskType.ordinal(), taskType.name(),
              this.getClass().getCanonicalName()));
        }
      });
    }
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testEnumConstantNotAddedInBetween() {
    if (featureNameOrdinalMapping.size() < FeatureName.values().length) {
      Arrays.stream(FeatureName.values()).forEach(taskType -> {
        if (!taskType.name().equals(featureNameOrdinalMapping.get(taskType.ordinal()))
            && !featureNameConstantMapping.containsKey(taskType.name())
            && taskType.ordinal() < featureNameOrdinalMapping.size()) {
          Assertions.fail(String.format(
              "New constant added in Enum [%s] at ordinal [%s] with name [%s]. You have to add constant at the end for Kryo serialization/deserialization to work in the backward compatible manner.",
              FeatureName.class.getCanonicalName(), taskType.ordinal(), taskType.name()));
        }
      });
    }
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testEnumConstantNotDeleted() {
    if (featureNameOrdinalMapping.size() > FeatureName.values().length) {
      Arrays.stream(FeatureName.values()).forEach(taskType -> {
        if (!taskType.name().equals(featureNameOrdinalMapping.get(taskType.ordinal()))
            && featureNameConstantMapping.containsKey(taskType.name())) {
          Assertions.fail(String.format(
              "Constant deleted from Enum [%s] at ordinal [%s] with name [%s]. You should not delete constant for Kryo serialization/deserialization to work in the backward compatible manner.",
              FeatureName.class.getCanonicalName(), taskType.ordinal(), taskType.name()));
        }
      });
    }
  }
}
