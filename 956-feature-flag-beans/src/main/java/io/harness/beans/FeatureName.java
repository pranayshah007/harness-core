/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.annotations.dev.HarnessTeam.SPG;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureFlag.Scope;

import lombok.Getter;
/**
 * Add your feature name here. When the feature is fully launched and no longer needs to be flagged,
 * delete the feature name.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVERLESS, HarnessModuleComponent.CDS_AMI_ASG,
        HarnessModuleComponent.CDS_INFRA_PROVISIONERS, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY,
        HarnessModuleComponent.CDS_K8S, HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_COMMON_STEPS,
        HarnessModuleComponent.CDS_APPROVALS, HarnessModuleComponent.CDS_FIRST_GEN, HarnessModuleComponent.CDS_TRIGGERS,
        HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_GITOPS,
        HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(HarnessTeam.PL)
public enum FeatureName {
  // Sorted using https://github.com/google/keep-sorted/blob/main/README.md
  // keep-sorted start
  ACCOUNT_BASIC_ROLE,
  ACTIVE_MIGRATION_FROM_LOCAL_TO_GCP_KMS,
  ACTIVITY_ID_BASED_TF_BASE_DIR,
  ADD_MANIFEST_COLLECTION_STEP,
  ALLOW_USER_TYPE_FIELDS_JIRA("used to hide jira userfields input in ui in both cg and ng", HarnessTeam.SPG),
  AMAZON_ECR_AUTH_REFACTOR,
  AMI_ASG_CONFIG_COPY,
  AMI_IN_SERVICE_HEALTHY_WAIT,
  ANALYSE_TF_PLAN_SUMMARY(
      "Enables parsing of the Terraform plan/apply/destroy summary [add/change/destroy] and exposing them as expressions",
      HarnessTeam.CDP),
  APPLICATION_DROPDOWN_MULTISELECT,
  ARTIFACT_COLLECTION_CONFIGURABLE,
  ARTIFACT_PERPETUAL_TASK,
  ARTIFACT_PERPETUAL_TASK_MIGRATION,
  ARTIFACT_STREAM_DELEGATE_SCOPING,
  ARTIFACT_STREAM_DELEGATE_TIMEOUT,
  ARTIFACT_STREAM_METADATA_ONLY,
  ATTRIBUTE_TYPE_ACL_ENABLED("Enable attribute filter on NG UI for ACL", HarnessTeam.PL),
  AUTO_ACCEPT_SAML_ACCOUNT_INVITES,
  AUTO_FREE_MODULE_LICENSE,
  AUTO_REJECT_PREVIOUS_APPROVALS,
  AWS_OVERRIDE_REGION,
  AZURE_BLOB_SM,
  AZURE_WEBAPP,
  BIND_CUSTOM_VALUE_AND_MANIFEST_FETCH_TASK,
  BIND_FETCH_FILES_TASK_TO_DELEGATE,
  BOOKING_RECOMMENDATIONS("Feature flag for booking.com recommendations", HarnessTeam.CE),
  BUILD_CREDITS_VIEW(
      "Enable build credit dashboard in UI, FF will be removed once the Free credits allocation for all users are introduced",
      HarnessTeam.GTM),
  BYPASS_HELM_FETCH,
  CCM_AS_DRY_RUN("Dry Run functionality of the AutoStopping Rules", HarnessTeam.CE),
  CCM_BUDGET_CASCADES("Enable to allow nested budgets for Financial Management", HarnessTeam.CE),
  CCM_CLUSTER_ORCH("Show/ Hide navigation link for cluster orchestrator page", HarnessTeam.CE),
  CCM_COMMORCH("Commitment Orchestration", HarnessTeam.CE),
  CCM_COMM_SETUP("It is used for enabling the setup flow of commitment orchestrator in CCM.", HarnessTeam.CE),
  CCM_CURRENCY_PREFERENCES("Currency Preferences", HarnessTeam.CE),
  CCM_DEV_TEST("", HarnessTeam.CE),
  CCM_ENABLE_AZURE_CLOUD_ASSET_GOVERNANCE_UI("Enable Azure Cloud Asset Governance UI", HarnessTeam.CE),
  CCM_ENABLE_CLOUD_ASSET_GOVERNANCE_UI("Enable Cloud Asset governance UI", HarnessTeam.CE),
  CCM_GOVERNANCE_GENAI_ENABLE("Genai feature for cloud asset governance", HarnessTeam.CE),
  CCM_INSTANCE_DATA_CLUSTERID_FILTER("Query from instanceData collection based on clusterId", HarnessTeam.CE),
  CCM_LABELS_FLATTENING("Use flattened label's columns in BigQuery", HarnessTeam.CE),
  CCM_MICRO_FRONTEND("Micro front for CCM", HarnessTeam.CE),
  CCM_MONTHLY_BUDGET_BREAKDOWN("Use monthly breakdown feature in Yearly Period Budget", HarnessTeam.CE),
  CCM_MSP("To enable margin obfuscation for CCM MSP accounts", HarnessTeam.CE),
  CCM_SUNSETTING_CG("Sunsetting CCM CG Features", HarnessTeam.CE),
  CCM_SUSTAINABILITY("Sustainability Feature in CCM Module", HarnessTeam.CE),
  CCM_WORKLOAD_LABELS_OPTIMISATION("Use workload labels from instance data instead of k8sworkload", HarnessTeam.CE),
  CCM_COST_CATEGORIES_DASHBOARD(
      "Cost Categories will reflect in bigQuery and eventually in Looker Dashboards", HarnessTeam.CE),
  CDB_MFE_ENABLED("Feature flag is needed to test/control the microfrontend architecture for CDB UI", HarnessTeam.CDB),
  CDC_SERVICE_DASHBOARD_REVAMP_NG("Service Dashboard Revamp is behind this FF", HarnessTeam.CDC),
  CDP_AWS_SAM("FF for enabling AWS SAM deployments", HarnessTeam.CDP),
  CDP_SKIP_DEFAULT_VALUES_YAML_CG(
      "Skip adding the default values file as an override if it doesn't contain any expressions in case of helm chart manifest for CG",
      HarnessTeam.CDP),
  CDP_SKIP_DEFAULT_VALUES_YAML_NG(
      "Skip adding the default values file as an override if it doesn't contain any expressions in case of helm chart manifest for NG",
      HarnessTeam.CDP),
  CDP_UPDATE_INSTANCE_DETAILS_WITH_IMAGE_SUFFIX("Update instance details if image suffix matches", HarnessTeam.CDP),
  CDP_USE_K8S_DECLARATIVE_ROLLBACK(
      "CG: Enable declarative rollback instead of imperative rollback for K8s, along with a new release history implementation. Release history is stored in individual secrets, instead of being consolidated and stored in a single configmap/secret. Old manifests are re-applied using `kubectl apply` (declarative rollback) instead of performing `kubectl rollout undo` (imperative rollback). See Jira ticket for more details: https://harness.atlassian.net/browse/CDS-2993",
      HarnessTeam.CDP),
  CDS_ARTIFACTORY_REPOSITORY_URL_MANDATORY(
      "FF to make the Artifactory Repository Url as mandatory in case of docker repositoryFormat", HarnessTeam.CDC),
  CDS_ARTIFACTS_PRIMARY_IDENTIFIER("To change the expression value for primary artifact identifier", HarnessTeam.CDC),
  CDS_AUTO_APPROVAL("This FF is for allowing scheduled approval in Harness approval step", HarnessTeam.CDP),
  CDS_AWS_BACKOFF_STRATEGY("Enable AWS SDK Client Backoff Strategy", HarnessTeam.CDP),
  CDS_AWS_CDK("Enable AWS CDK integration", HarnessTeam.CDP),
  CDS_AZURE_WEBAPP_NG_LISTING_APP_NAMES_AND_SLOTS(
      "Support for listing Azure Web App names and slots on Slot Deployment and Swap Slot steps", HarnessTeam.CDP),
  CDS_BG_STAGE_SCALE_DOWN_STEP_NG(
      "Enables Blue Green Scale Down Stage Scale Down Step. This will help bring down the stage environment in Blue Green Deployment. Epic: https://harness.atlassian.net/browse/CDS-55822",
      HarnessTeam.CDP),
  CDS_CONTAINER_STEP_GROUP("Support for container step group in CD", HarnessTeam.CDP),
  CDS_CUSTOM_STAGE_EXECUTION_DATA_SYNC(
      "This flag controls if you want Custom Stage execution data saved in mongodb and eventually synced to timescale",
      HarnessTeam.CDP),
  CDS_DEBEZIUM_ENABLED_CG("This flag is enable sync using debezium in cg", HarnessTeam.CDC, Scope.GLOBAL),
  CDS_DISABLE_FABRIC8_CG(
      "Enabling this FF switches the user to kubernetes java client instead of deprecated fabric8 client in CG.",
      HarnessTeam.CDP),
  CDS_DISABLE_FABRIC8_NG(
      "Enabling this FF switches the user to kubernetes java client instead of deprecated fabric8 client in NG.",
      HarnessTeam.CDP),
  CDS_ENABLE_TRIGGER_YAML_VALIDATION("Enables trigger yaml validation", HarnessTeam.SPG),
  CDS_ENCRYPT_TERRAFORM_APPLY_JSON_OUTPUT(
      "FF for providing the terraform apply json output as a secret", HarnessTeam.CDP),
  CDS_ENABLE_NEW_PARAMETER_FIELD_PROCESSOR(
      "FF for turning on new parameterField processor which handles all expression resolution for ParameterField",
      HarnessTeam.CDC),
  CDS_ENV_PROPAGATION("FF for enabling env infra propagation for CD", HarnessTeam.CDC),
  CDS_GIT_CONFIG_FILES("Enable config files from GIT repositories", HarnessTeam.CDP),
  CDS_HELM_FETCH_CHART_METADATA_NG(
      "Enables option to fetch helm chart details from the Chart.yaml and expose this as expressions. Epic: https://harness.atlassian.net/browse/CDS-58036",
      HarnessTeam.CDP),
  CDS_HELM_MULTIPLE_MANIFEST_SUPPORT_NG(
      "Enables multiple manifest support. We will be able to define multiple manifest and set only one as a primary. Epic: https://harness.atlassian.net/browse/CDS-58036",
      HarnessTeam.CDP),
  CDS_HELM_STEADY_STATE_CHECK_1_16_V2_CG(
      "This FF will use helm get manifest instead of helm template output to find managed workloads for Native Helm steady state check for CG",
      HarnessTeam.CDP),
  CDS_HELM_STEADY_STATE_CHECK_1_16_V2_NG(
      "This FF will use helm get manifest instead of helm template output to find managed workloads for Native Helm steady state check for NG",
      HarnessTeam.CDP),
  CDS_HTTP_STEP_NG_CERTIFICATE("Allow enforce SSL/TLS certificate in NG HTTP step", HarnessTeam.CDC),
  CDS_K8S_HELM_INSTANCE_SYNC_V2_NG(
      "FF for enabling Instance Sync V2 for K8s and Native Helm swimlanes in NG", HarnessTeam.CDP),
  CDS_K8S_SERVICE_HOOKS_NG("Enables Service hooks support for K8s/Native Helm Services", HarnessTeam.CDP),
  CDS_K8S_SOCKET_CAPABILITY_CHECK_NG(
      "Replace HTTP capability check for Kubernetes connector with Socket Capability", HarnessTeam.CDP),
  CDS_MULTI_SERVICE_PROPAGATION("FF for enabling multi service propagation for CD", HarnessTeam.CDP),
  CDS_NG_TRIGGER_EXECUTION_REFACTOR(
      "Refactor trigger execution to use same logic used in manual execution", HarnessTeam.SPG),
  CDS_NG_SERVICE_PRINCIPAL_FOR_CUSTOM_WEBHOOK(
      "Makes authenticated custom webhook calls use service principal instead of the principal inherited from the API key",
      HarnessTeam.CDC),
  CDS_NG_TRIGGER_MULTI_ARTIFACTS("Allows creation of multi-region artifact triggers", HarnessTeam.CDC),
  CDS_NOT_ALLOW_READ_ONLY_SECRET_MANAGER_TERRAFORM_TERRAGRUNT_PLAN(
      "Enable the check if Vault secret manager is read only", HarnessTeam.CDP),
  CDS_OPA_TEMPLATE_GOVERNANCE("Added OPA support for template service ", HarnessTeam.CDC),
  CDS_OrgAccountLevelServiceEnvEnvGroup(
      "Support Creation and Use of Org and Account level Services and Environments", HarnessTeam.CDC),
  CDS_PAUSE_JIRA_APPROVAL_CG("Pause CG Jira Approval", HarnessTeam.CDC),
  CDS_PIPELINE_STUDIO_UPGRADES("Enables upgraded one canvas pipeline studio with usability fixes", HarnessTeam.CDP),
  CDS_PROJECT_SCOPED_RESOURCE_CONSTRAINT_QUEUE(
      "With enabling this FF with serviceV2 setup, pipeline in different projects but having the same infra key can be executed parallely",
      HarnessTeam.CDC),
  CDS_QUERY_OPTIMIZATION("Feature flag to optimize CG Queries", HarnessTeam.CDC),
  CDS_RANCHER_SUPPORT_NG("Enable Rancher support in NG.", HarnessTeam.CDP),
  CDS_RECONFIGURE_JIRA_APPROVAL_TIMEOUT("Reconfigure Jira Approval Timeout to 1 Minute", HarnessTeam.CDC),
  CDS_REMOVE_COMMENTS_FROM_VALUES_YAML("Remove comments from values.yaml files", HarnessTeam.CDP),
  CDS_RENAME_HARNESS_RELEASE_HISTORY_RESOURCE_NATIVE_HELM_NG(
      "Use a prefix for internal harness release history for native helm deployment. Feature Flag will be removed as part of the epic: https://harness.atlassian.net/browse/CDS-46915",
      HarnessTeam.CDP),
  CDS_RESOLVE_OBJECTS_VIA_JSON_SELECT(
      "Support resolution of Objects via JSON Select Command in HTTP Step", HarnessTeam.CDC),
  CDS_SERVERLESS_V2("FF for enabling Serverless 2.0 deployments", HarnessTeam.CDP),
  CDS_SERVICENOW_REFRESH_TOKEN_AUTH("Refresh Token auth support for servicenow connector", HarnessTeam.CDC),
  CDS_SERVICENOW_USE_METADATA_V2(
      "Using METADATA_V2 in /metadata (create update) and /createMetadata (approval) API in Servicenow Steps NG; and changing /metadata parsing to manager",
      HarnessTeam.CDC),
  CDS_SERVICE_CONFIG_LAST_STEP(
      "Allows landing users on the last step of manifest/artifact/config files, if these configs are in edit mode",
      HarnessTeam.CDP),
  CDS_SERVICE_OVERRIDES_2_0("Revamped experience service and environment overrides in NG", HarnessTeam.CDC),
  CDS_SSH_CLIENT("Enable SSH new implementation via SSH Client", HarnessTeam.CDP),
  CDS_SSH_SSHJ("Enable SSH for Vault flow via SSHJ", HarnessTeam.CDP),
  CDS_STAGE_EXECUTION_DATA_SYNC(
      "This flag controls if you want CD Stage execution data saved in cd service and eventually synced to timescale",
      HarnessTeam.CDC),
  CDS_SUPPORT_EXPRESSION_REMOTE_TERRAFORM_VAR_FILES_NG(
      "FF to support expressions in remote terraform var files", HarnessTeam.CDP),
  CDS_SUPPORT_HPA_AND_PDB_NG(
      "Enabling support for HPA and PDB kind resources in k8s deployments in NG", HarnessTeam.CDP),
  CDS_SUPPORT_SKIPPING_BG_DEPLOYMENT_NG(
      "Enabling support for skipping BG deployment if the manifest previously deployed is same as current manifest in NG",
      HarnessTeam.CDP),
  CDS_SUPPORT_TICKET_DEFLECTION("Enable api to create zendesk ticket and for generating coveo token", HarnessTeam.CDP),
  CDS_TERRAFORM_CONFIG_INSPECT_V1_2(
      "Enables usage of terraform-config-inspect v1.2 built from commit 7c9946b1df498f1b0634c7b33257790f01c819f3 of https://github.com/hashicorp/terraform-config-inspect and GO 1.19.6",
      HarnessTeam.CDP),
  CDS_TERRAFORM_CONFIG_INSPECT_V1_3(
      "Enables usage of terraform-config-inspect v1.2 built from commit f32df32a01cd687715ed165ac5d202009eba9b2f of https://github.com/hashicorp/terraform-config-inspect and GO 1.19.6",
      HarnessTeam.CDP),
  CDS_TERRAFORM_S3_SUPPORT(
      "Enable support for AWS S3 bucket and URIs for Terraform Source, tfVars and Backend Config", HarnessTeam.CDP),
  CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_CG(
      "To encrypt and decrypt terraform and terragrunt plan on manager side instead of delegate side for CG",
      HarnessTeam.CDP),
  CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_NG(
      "To encrypt and decrypt terraform and terragrunt plan on manager side instead of delegate side for NG",
      HarnessTeam.CDP),
  CDS_TRIGGER_ACTIVITY_PAGE("NG Triggers Activity page", HarnessTeam.CDC),
  CDS_USE_HTTP_CHECK_IGNORE_RESPONSE_INSTEAD_OF_SOCKET_NG(
      "This is to diable checking for the HTTP status code and instead just check for a valid response",
      HarnessTeam.CDP),
  CDS_USE_OLD_SERVICE_V1("Feature flag to use service v1. NG_SVC_ENV_REDESIGN will be deprecated", HarnessTeam.CDC),
  CDS_GITHUB_APP_AUTHENTICATION("This flag enables the github app authentication fo github connector", HarnessTeam.CDP),
  CDS_V1_EOL_BANNER("Display EOL banner for v1 CD entities", HarnessTeam.CDC),
  CD_AI_ENHANCED_REMEDIATIONS(
      "Enables use of generative AI to provide remediation information in CD step logs", HarnessTeam.CDP),
  CD_GIT_WEBHOOK_POLLING("Used to poll git webhook recent delivery events", HarnessTeam.CDP),
  CD_NG_DOCKER_ARTIFACT_DIGEST(
      "Use SHA256 digest as optional parameter for docker image artifact config", HarnessTeam.SPG),
  CD_NG_DYNAMIC_PROVISIONING_ENV_V2(
      "Enable dynamic provisioning support in v2 environment. Epic: CDS-39606", HarnessTeam.CDC),
  CD_ONBOARDING_HELP_ENABLED("Enables help panel for CD onboarding ", HarnessTeam.CDP),
  CD_TRIGGERS_REFACTOR("Enable NG Triggers UI refactoring", HarnessTeam.CDP),
  CD_TRIGGER_CATALOG("Enables UI for Trigger catalog for Nexus ", HarnessTeam.CDC),
  CD_TRIGGER_CATALOG_API_ENABLED("Enable support for pipeline/api/triggers/catalog API in UI", HarnessTeam.CDC),
  CD_TRIGGER_V2("Enable support for nexus3, nexus2, azure, ami trigger", HarnessTeam.CDC),
  CET_ENABLED("Enable Continuous Error Tracking module in UI", HarnessTeam.CET),
  CET_EVENTS_CHART("Enable events chart in UI of Continuous Error Tracking module", HarnessTeam.CET),
  CET_CD_INTEGRATION("Enable Continuous Error Tracking events list in CD pipeline execution tab", HarnessTeam.CET),
  CET_PLATFORM_MONITORED_SERVICE(
      "Enable Continuous Error Tracking agent column and settings in platform Monitored Services", HarnessTeam.CET),
  CET_AGENT_WIZARD("Enable agent configuration wizard in UI of Continuous Error Tracking module", HarnessTeam.CET),
  CE_GCP_CUSTOM_PRICING("Use custom pricing data for k8s gcp from billing export", HarnessTeam.CE),
  CE_HARNESS_ENTITY_MAPPING("Internal FF to decide if harness entities mapping is needed", HarnessTeam.CE),
  CE_HARNESS_INSTANCE_QUERY("Internal FF to decide which table to use for querying mapping data", HarnessTeam.CE),
  CE_NET_AMORTISED_COST_ENABLED("Enable cost calculation through Net Amortised cost", HarnessTeam.CE),
  CE_RERUN_HOURLY_JOBS("Rerunning Hourly billing jobs", HarnessTeam.CE),
  CE_SAMPLE_DATA_GENERATION("Used to show sample data in CCM CG", HarnessTeam.CE),
  CFNG_ENABLED,
  CF_ALLOW_SPECIAL_CHARACTERS,
  CF_APP_NON_VERSIONING_INACTIVE_ROLLBACK,
  CF_CLI7,
  CF_CUSTOM_EXTRACTION,
  CF_ROLLBACK_CONFIG_FILTER,
  CF_ROLLBACK_CUSTOM_STACK_NAME(
      "Use custom stack name and region to find lates successful couldformation rollback data", HarnessTeam.CDP),
  CG_GIT_POLLING("Poll git based on account config for git sync in CG.", HarnessTeam.SPG),
  CG_SPG_JIRA_APPROVAL_POLLING_ASYNC("Enable jira approval polling to async", HarnessTeam.SPG),
  CG_K8S_MANIFEST_COMMIT_VAR(
      "Enables users to pass commit id as sweeping output and use later in subsequent steps. PM Rohan",
      HarnessTeam.CDP),
  CG_LICENSE_USAGE,
  CG_SECRET_MANAGER_DELEGATE_SELECTORS,
  CHANGE_INSTANCE_QUERY_OPERATOR_TO_NE("Change instance service query operator from $exists to $ne", HarnessTeam.SPG),
  CHAOS_DASHBOARD_ENABLED("Enables chaos dashboards in CHAOS module", HarnessTeam.CHAOS),
  CHAOS_IMAGE_REGISTRY_DEV("Enable image registry configuration in CHAOS", HarnessTeam.CHAOS),
  CHAOS_LINUX_ENABLED("Enable linux experiment and infrastructure integration in CHAOS", HarnessTeam.CHAOS),
  CHAOS_PROBE_ENABLED("Enable new probe ui and flow in CHAOS", HarnessTeam.CHAOS),
  CHAOS_SECURITY_GOVERNANCE("Enable security governance routes, validation and navigation in CHAOS", HarnessTeam.CHAOS),
  CIE_ENABLED_RBAC("Enable rbac validationa at CI level", HarnessTeam.CI),
  CIE_HOSTED_VMS(
      "Enables hosted VMs in favor of hosted K8s for CIE. This flag will be deprecated once all the feature work has been checked in",
      HarnessTeam.CI),
  CIE_HOSTED_VMS_MAC("FF for enabling hosted builds for mac os", HarnessTeam.CI),
  CIE_HOSTED_VMS_WINDOWS("FF for enabling hosted builds for windows amd64", HarnessTeam.CI),
  CIE_USE_DOCKER_BUILDX("Enable docker build and push step to use buildx", HarnessTeam.CI),
  CI_AI_ENHANCED_REMEDIATIONS(
      "Enables use of generative AI to provide remediation information in CI step logs", HarnessTeam.CI),
  CI_BITBUCKET_STATUS_KEY_HASH("Hash and abbreviate the stage ID for Bitbucket SaaS", HarnessTeam.CI),
  CI_CACHE_INTELLIGENCE("Feature flag for cache intelligence feature", HarnessTeam.CI),
  CI_DISABLE_RESOURCE_OPTIMIZATION(
      "Used for disabling the resource optimization, AXA had asked this flag", HarnessTeam.CI),
  CI_DOCKER_INFRASTRUCTURE,
  CI_ENABLE_BARE_METAL("To enable bare metal cloud for infra", HarnessTeam.CI),
  CI_ENABLE_BARE_METAL_FREE_ACCOUNT("To enable bare metal cloud for infra for free account", HarnessTeam.CI),
  CI_ENABLE_DLC("Enable docker layer caching", HarnessTeam.CI),
  CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED(
      "If enabled, OOTB steps will run directly on host in cloud infra", HarnessTeam.CI),
  CI_INCREASE_DEFAULT_RESOURCES,
  CI_INDIRECT_LOG_UPLOAD,
  CI_LE_STATUS_REST_ENABLED(
      "Used for sending step status for CI via REST APIs instead of gRPC from Lite Engine to manager", HarnessTeam.CI),
  CI_MFE_ENABLED("Feature flag is needed to test/control the microfrontend architecture for CI UI", HarnessTeam.CI),
  CI_OUTPUT_VARIABLES_AS_ENV("For enabling output variables as env variables in CI stages", HarnessTeam.CI),
  CI_PIPELINE_VARIABLES_IN_STEPS("For enabling pipeline variables as env variables in CI steps", HarnessTeam.CI),
  CI_PYTHON_TI("Enable Test Intelligence for Python", HarnessTeam.CI),
  CI_REMOTE_DEBUG("Enable the option for remote debug for CI users.", HarnessTeam.CI),
  CI_TESTTAB_NAVIGATION,
  CI_TI_DASHBOARDS_ENABLED,
  CI_USE_S3_FOR_CACHE("Use S3 bucket for cache intelligence instead of GCP", HarnessTeam.CI),
  CI_USE_S3_FOR_DLC("Use S3 bucket for DLC cache", HarnessTeam.CI),
  CI_YAML_VERSIONING("Feature flag for yaml simplification", HarnessTeam.CI),
  CI_NEW_CODEBASE_INPUTSET("Feature flag for new codebase input set", HarnessTeam.CI),
  CLEAN_UP_OLD_MANAGER_VERSIONS(Scope.PER_ACCOUNT),
  CLOUDFORMATION_CHANGE_SET,
  CLOUDFORMATION_SKIP_WAIT_FOR_RESOURCES,
  CODE_ENABLED("Enable Harness Code", HarnessTeam.CODE),
  CONSIDER_ORIGINAL_STATE_VERSION,
  CREATE_DEFAULT_PROJECT("Enables auto create default project after user signup", HarnessTeam.GTM),
  CUSTOM_DASHBOARD,
  CUSTOM_DASHBOARD_DEPLOYMENT_FETCH_LONGER_RETENTION_DATA,
  CUSTOM_DASHBOARD_ENABLE_CRON_DEPLOYMENT_DATA_MIGRATION,
  CUSTOM_DASHBOARD_ENABLE_CRON_INSTANCE_DATA_MIGRATION,
  CUSTOM_DASHBOARD_ENABLE_REALTIME_DEPLOYMENT_MIGRATION,
  CUSTOM_DASHBOARD_ENABLE_REALTIME_INSTANCE_AGGREGATION,
  CUSTOM_DASHBOARD_INSTANCE_FETCH_LONGER_RETENTION_DATA,
  CUSTOM_DASHBOARD_V2, // To be used only by ui to control flow from cg dashbaords to ng
  CUSTOM_DEPLOYMENT_ARTIFACT_FROM_INSTANCE_JSON,
  CUSTOM_MANIFEST,
  CUSTOM_MAX_PAGE_SIZE,
  CVNG_ENABLED,
  CVNG_LICENSE_ENFORCEMENT,
  CVNG_MONITORED_SERVICE_DEMO,
  CVNG_SPLUNK_METRICS,
  CVNG_TEMPLATE_MONITORED_SERVICE,
  CVNG_TEMPLATE_VERIFY_STEP,
  CVNG_VERIFY_STEP_DEMO,
  CV_AWS_PROMETHEUS("Enable AWS Prometheus for CV State", HarnessTeam.CV),
  CV_UI_DISPLAY_NODE_REGEX_FILTER(
      "Displays the control node and test node reg filter option in Verify step", HarnessTeam.CV),
  CV_UI_DISPLAY_SHOULD_USE_NODES_FROM_CD_CHECKBOX(
      "Displays the should use nodes from CD checkbox in Verify step", HarnessTeam.CV),
  CV_USE_SEPARATE_LE_TASK_TYPE_FOR_LOG_CLUSTERING(
      "When enabled, CV log clustering task type will be CV_LOG_CLUSTER, else it will be LOG_CLUSTER", HarnessTeam.CV),
  CV_DEMO,
  CV_FAIL_ON_EMPTY_NODES,
  CV_HOST_SAMPLING,
  CV_SUCCEED_FOR_ANOMALY,
  DEBEZIUM_ENABLED,
  DEFAULT_ARTIFACT,
  DELEGATE_TASK_CAPACITY_CHECK("Enable delegate task capacity check", HarnessTeam.DEL),
  DELEGATE_TASK_LOAD_DISTRIBUTION("Delegate task load distribution among delegates", HarnessTeam.DEL),
  DEL_FETCH_TASK_LOG_API("FF to enable fetch delegate task logs from stackdriver", HarnessTeam.DEL),
  DEL_SELECTION_LOGS_READ_FROM_GOOGLE_DATA_STORE(
      "Enables the fetching of delegate selection records from google data store instead of mongo", HarnessTeam.DEL),
  DEPLOYMENT_RECONCILIATION_LOGIC_QUERY_OPTIMIZATIONS(
      "Used to modify logic for reconciliation queries", HarnessTeam.CDC),
  DEPLOYMENT_SUBFORMIK_APPLICATION_DROPDOWN,
  DEPLOYMENT_SUBFORMIK_PIPELINE_DROPDOWN,
  DEPLOYMENT_SUBFORMIK_WORKFLOW_DROPDOWN,
  DEPLOY_TO_INLINE_HOSTS,
  DEPLOY_TO_SPECIFIC_HOSTS,
  DEPRECATE_K8S_STEADY_STATE_CHECK_STEP,
  DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION(
      "Used to disable Schema validation for Service Env for new service env redesign", HarnessTeam.CDC),
  DISABLE_CI_STAGE_DEL_SELECTOR,
  DISABLE_DEPLOYMENTS_SEARCH_AND_LIMIT_DEPLOYMENT_STATS,
  DISABLE_HARNESS_SM,
  DISABLE_HELM_REPO_YAML_CACHE(
      "Enable to create a temporary folder (based on execution id) to store repository.yaml file", HarnessTeam.CDP),
  DISABLE_INSTANCE_STATS_JOB_CG("Disable Instance Stats Job from CG", HarnessTeam.CDP),
  DISABLE_LOCAL_LOGIN,
  DISABLE_LOGML_NEURAL_NET,
  DISABLE_METRIC_NAME_CURLY_BRACE_CHECK,
  DISABLE_PIPELINE_SCHEMA_VALIDATION(
      "Used to disable pipeline yaml schema as We saw some intermittent issue in Schema Validation due to invalid schema generation. Will keep this FF until root cause is found and fixed.",
      HarnessTeam.PIPELINE),
  DISABLE_SERVICEGUARD_LOG_ALERTS,
  DISABLE_TEMPLATE_SCHEMA_VALIDATION,
  DISABLE_WINRM_COMMAND_ENCODING(
      "To disable Base64 encoding done to WinRM command script which is sent to remote server for execution",
      HarnessTeam.CDP),
  DISABLE_WINRM_COMMAND_ENCODING_NG(
      "To disable Base64 encoding done to WinRM command script which is sent to remote server for execution",
      HarnessTeam.CDP),
  DONT_RESTRICT_PARALLEL_STAGE_COUNT,
  DO_NOT_RENEW_APPROLE_TOKEN(
      "CAUTION: USE THIS ONLY WHEN THE CUSTOMER DELEGATE IS IN VERSION HIGHER OR EQUAL TO 764xx. Used for disabling appRole token renewal and fetching token on the fly before CRUD",
      HarnessTeam.PL),
  DYNATRACE_MULTI_SERVICE,
  ECS_AUTOSCALAR_REDESIGN,
  ECS_BG_DOWNSIZE,
  ECS_MULTI_LBS,
  ECS_REGISTER_TASK_DEFINITION_TAGS,
  ECS_ROLLBACK_MAX_DESIRED_COUNT("Changes ECS Rollback Desired Count to Max(OldService, NewService)", HarnessTeam.CDP),
  ENABLE_ADDING_SERVICE_VARS_TO_ECS_SPEC,
  ENABLE_CERT_VALIDATION,
  ENABLE_CHECK_STATE_EXECUTION_STARTING(
      "Used to allow create retry state execution when event is status equals to STARTING", HarnessTeam.SPG),
  ENABLE_CVNG_INTEGRATION,
  ENABLE_DEFAULT_NG_EXPERIENCE_FOR_ONPREM,
  ENABLE_DEFAULT_TIMEFRAME_IN_DEPLOYMENTS,
  ENABLE_EXPERIMENTAL_STEP_FAILURE_STRATEGIES(
      "Used to enable rollback workflow strategy on step failure", HarnessTeam.SPG),
  ENABLE_K8_BUILDS("FF for enabling kubernetes builds as an option", HarnessTeam.CI),
  ENABLE_LOGIN_AUDITS,
  ENABLE_WINRM_ENV_VARIABLES,
  ENHANCED_GCR_CONNECTIVITY_CHECK,
  ENTITY_AUDIT_RECORD,
  EXPORT_TF_PLAN,
  EXTERNAL_USERID_BASED_LOGIN,
  EXTRA_LARGE_PAGE_SIZE,
  FAIL_WORKFLOW_IF_SECRET_DECRYPTION_FAILS,
  FEATURE_ENFORCEMENT_ENABLED,
  FETCH_PIPELINE_HEALTH_FROM_NEW_TABLE(
      "We will fetch pipeline health and execution data from the new timescale table if this FF is on",
      HarnessTeam.PIPELINE),
  FFM_1859,
  FFM_2134_FF_PIPELINES_TRIGGER,
  FFM_3938_STALE_FLAGS_ACTIVE_CARD_HIDE_SHOW,
  FFM_3959_FF_MFE_Environment_Detail("Enable Feature Flag MFE Environment page", HarnessTeam.CF),
  FFM_3961_ENHANCED_ONBOARDING("Enable new onboarding experience for FeatureFlags", HarnessTeam.CF),
  FFM_4117_INTEGRATE_SRM("Enable Feature Flags to send events to the SRM module", HarnessTeam.CF),
  FFM_4737_JIRA_INTEGRATION("Enable the Jira Integration feature", HarnessTeam.CF),
  FFM_5256_FF_MFE_Environment_Listing("Enable Feature Flag MFE Environment listing page", HarnessTeam.CF),
  FFM_5939_MFE_TARGET_GROUPS_LISTING("Enable Feature Flag MFE Target Groups listing page", HarnessTeam.CF),
  FFM_5951_FF_MFE_Targets_Listing("Enable Feature Flag MFE Targets listing page", HarnessTeam.CF),
  FFM_6610_ENABLE_METRICS_ENDPOINT("Enable fetching feature flag metrics from new metrics endpoint", HarnessTeam.CF),
  FFM_6665_FF_MFE_Target_Detail("Enable Feature Flag MFE Target Detail page", HarnessTeam.CF),
  FFM_6666_FF_MFE_Target_Group_Detail("Enable Feature Flag MFE Target Group Detail page", HarnessTeam.CF),
  FFM_6683_ALL_ENVIRONMENTS_FLAGS,
  FFM_6800_FF_MFE_ONBOARDING("Enable Feature Flag MFE Onboarding page", HarnessTeam.CF),
  FFM_7127_FF_MFE_ONBOARDING_DETAIL("Enable Feature Flag MFE Onboarding Detail page", HarnessTeam.CF),
  FFM_7921_ARCHIVING_FEATURE_FLAGS("Enable archiving feature flags instead of permanent deletion", HarnessTeam.CF),
  FFM_7258_INTERCOM_VIDEO_LINKS("Enable links to launch Intercom window containing video tutorials", HarnessTeam.CF),
  FFM_8261_EXPRESSIONS_IN_PIPELINE_STEP("Enable expressions support in the FF pipeline step", HarnessTeam.CF),
  FFM_8344_FLAG_CLEANUP("Enable Stale Flag Cleanup in Feature Flags filtered listing page", HarnessTeam.CF),
  FF_ALLOW_OPTIONAL_VARIABLE("Allow Optional Variable from UI in NG.", HarnessTeam.PIPELINE),
  FF_FLAG_SYNC_THROUGH_GITEX_ENABLED,
  FF_GITSYNC,
  FF_PIPELINE,
  FIXED_INSTANCE_ZERO_ALLOW("To allow user to set the fixed instance count to 0 for ECS Deployments", HarnessTeam.CDP),
  FREEZE_DURING_MIGRATION,
  FREE_PLAN_ENFORCEMENT_ENABLED,
  GCB_CI_SYSTEM,
  GCP_WORKLOAD_IDENTITY,
  GITHUB_WEBHOOK_AUTHENTICATION,
  SPG_CG_FIXING_PIPELINE_RESUME("This fixes pipeline resume when a loop state is skipped", HarnessTeam.SPG),
  GITOPS_IAM("Support for connecting via IAM role in GitOps Clusters", HarnessTeam.GITOPS),
  GITOPS_ORG_LEVEL("Support GitOps at Org level", HarnessTeam.GITOPS),
  GITOPS_REVERT_PR("Support for Revert PR step in GitOps pipeline flow", HarnessTeam.GITOPS),
  GIT_HOST_CONNECTIVITY,
  GLOBAL_COMMAND_LIBRARY,
  GLOBAL_DISABLE_HEALTH_CHECK(Scope.GLOBAL),
  GRAPHQL_DEV,
  GRAPHQL_WORKFLOW_EXECUTION_OPTIMIZATION(
      "Making multiple optimizations for workflow execution graphql in CG", HarnessTeam.SPG),
  HARNESS_TAGS,
  HELM_CHART_AS_ARTIFACT,
  HELM_CHART_NAME_SPLIT,
  HELM_MERGE_CAPABILITIES("Add helm merge capabilities", HarnessTeam.CDP),
  HELM_STEADY_STATE_CHECK_1_16,
  HELM_VERSION_3_8_0,
  HELP_PANEL,
  HIDE_ABORT,
  HONOR_DELEGATE_SCOPING,
  HOSTED_BUILDS("Used to enabled Hosted builds in paid accounts", HarnessTeam.CI),
  HTTP_HEADERS_CAPABILITY_CHECK,
  IACM_ENABLED("Enable support for IACM micro front end capabilities", HarnessTeam.IACM),
  IDP_DYNAMIC_SECRET_RESOLUTION("Enable dynamic resolution of secrets", HarnessTeam.IDP),
  IDP_ENABLED("This for enabling IDP on UI", HarnessTeam.IDP),
  IDP_ENABLE_EDIT_HARNESS_CI_CD_PLUGIN(
      "This FF is for allowing user to edit config for harness-ci-cd plugin from UI", HarnessTeam.IDP),
  INFRA_MAPPING_BASED_ROLLBACK_ARTIFACT,
  INLINE_SSH_COMMAND,
  INSTANCE_SYNC_V2_CG("Enable Instance Sync V2 framework in CG for direct K8s cloud provider", HarnessTeam.CDP),
  INSTANT_DELEGATE_DOWN_ALERT("FF to instantly alert when delegates are down", HarnessTeam.SPG),
  JDK11_UPGRADE_BANNER,
  KUSTOMIZE_PATCHES_CG,
  LANDING_OVERVIEW_PAGE_V2("Supports new entities for landing overview page", HarnessTeam.SPG),
  LDAP_SYNC_WITH_USERID,
  LDAP_USER_ID_SYNC,
  LIMITED_ACCESS_FOR_HARNESS_USER_GROUP,
  LIMIT_PCF_THREADS,
  LOCAL_DELEGATE_CONFIG_OVERRIDE,
  LOGS_V2_247,
  LOG_APP_DEFAULTS,
  MANIFEST_INHERIT_FROM_CANARY_TO_PRIMARY_PHASE,
  MERGE_RUNTIME_VARIABLES_IN_RESUME(
      "merge context elements workflow variables while resuming pipeline from a stage", HarnessTeam.SPG),
  MOVE_AWS_AMI_INSTANCE_SYNC_TO_PERPETUAL_TASK,
  MOVE_AWS_AMI_SPOT_INST_INSTANCE_SYNC_TO_PERPETUAL_TASK,
  MOVE_AWS_CODE_DEPLOY_INSTANCE_SYNC_TO_PERPETUAL_TASK,
  MOVE_AWS_LAMBDA_INSTANCE_SYNC_TO_PERPETUAL_TASK,
  MOVE_AWS_SSH_INSTANCE_SYNC_TO_PERPETUAL_TASK,
  MOVE_CONTAINER_INSTANCE_SYNC_TO_PERPETUAL_TASK,
  MOVE_PCF_INSTANCE_SYNC_TO_PERPETUAL_TASK,
  NEW_DEPLOYMENT_FREEZE,
  NEW_EXECUTION_LIST_VIEW(
      "Enables the new UX for Executions list view for Pipelines and Projects", HarnessTeam.PIPELINE),
  NEW_KUBECTL_VERSION,
  NEW_KUSTOMIZE_BINARY,
  NEW_LEFT_NAVBAR_SETTINGS("Used for new left navbar configuration", HarnessTeam.PL),
  NG_ARTIFACT_SOURCES("Flag to support multi artifact sources for service V2", HarnessTeam.CDC),
  NG_DASHBOARDS("", HarnessTeam.CE),
  NG_EXECUTION_INPUT,
  NG_EXPRESSIONS_NEW_INPUT_ELEMENT(
      "FF to enable the new input element for the Expressions on UI when UI suggests the probable expressions to the User based on text input so far",
      PIPELINE),
  NG_GIT_EXPERIENCE,
  NG_INLINE_MANIFEST,
  NG_LICENSES_ENABLED,
  NG_SVC_ENV_REDESIGN,
  NODE_RECOMMENDATION_AGGREGATE("K8S Node recommendation Feature in CCM", HarnessTeam.CE),
  NOTIFY_GIT_SYNC_ERRORS_PER_APP(
      "Notifies git sync errors per application to the user, general behaviour is at the account level",
      HarnessTeam.SPG),
  ON_DEMAND_ROLLBACK_WITH_DIFFERENT_ARTIFACT(
      "Used to do on demand rollback to previously deployed different artifact on same inframapping", HarnessTeam.CDC),
  ON_NEW_ARTIFACT_TRIGGER_WITH_LAST_COLLECTED_FILTER,
  OPA_FF_GOVERNANCE,
  OPA_GIT_GOVERNANCE,
  OPA_PIPELINE_GOVERNANCE,
  OPTIMIZED_GIT_FETCH_FILES,
  OPTIMIZED_TF_PLAN,
  OUTAGE_CV_DISABLE,
  OUTCOME_GRAPHQL_WITH_INFRA_DEF,
  OVERRIDE_VALUES_YAML_FROM_HELM_CHART,
  PCF_OLD_APP_RESIZE,
  PDC_PERPETUAL_TASK,
  PERSIST_MONITORED_SERVICE_TEMPLATE_STEP(
      "Enables saving of monitored service created during template verify step", HarnessTeam.CV),
  PIE_ASYNC_VALIDATION("Validate Pipelines asynchronously on Get calls in Pipeline Studio", HarnessTeam.PIPELINE),
  PIE_DEPRECATE_PAUSE_INTERRUPT_NG("Deprecate Pause and Resume interrupts in NG", HarnessTeam.PIPELINE),
  PIE_EXPRESSION_CONCATENATION(
      "Support for new string concatenation support in expression engine", HarnessTeam.PIPELINE),
  PIE_EXPRESSION_DISABLE_COMPLEX_JSON_SUPPORT(
      "Support for disabling returning complex objects as json", HarnessTeam.PIPELINE),
  PIE_GET_FILE_CONTENT_ONLY(
      "FF to optimise the execution flow to fetch only file content for remote entities", HarnessTeam.PIPELINE),
  PIE_GITX_OAUTH("Use users' oauth creds to fetch and commit in git", HarnessTeam.PIPELINE),
  PIE_GIT_DEFAULT_BRANCH_CACHE("FF to fetch the default branch from the git default branch cache", PIPELINE),
  PIE_MULTISELECT_AND_COMMA_IN_ALLOWED_VALUES(
      "Will allow comma and multi-selection in runtime input allowed values", PIPELINE),
  PIE_NG_BATCH_GET_TEMPLATES(
      "FF to enable batching of templates to improve loading time of pipeline and template studio",
      HarnessTeam.PIPELINE),
  PIE_PIPELINE_SETTINGS_ENFORCEMENT_LIMIT(
      "To enable pipeline-settings and limits in Project Default Settings in UI", HarnessTeam.PIPELINE),
  PIE_RETRY_STEP_GROUP(
      "To enable Retry Step Group Failure Strategy, under which if a step fails in a step group, the whole group is retried",
      PIPELINE),
  PIE_STATIC_YAML_SCHEMA("Enable support for static schema", PIPELINE),
  PIE_EXPRESSION_PLAYGROUND("Enable support for dynamic expression evaluation", PIPELINE),
  PIE_STORE_USED_EXPRESSIONS(
      "FF to use store the expressions used during the pipeline executions", HarnessTeam.PIPELINE),
  PIE_USE_SECRET_FUNCTOR_WITH_RBAC("Perform Rbac on secrets when used in pipeline execution", HarnessTeam.PIPELINE),
  PIE_WEBHOOK_NOTIFICATION("Enable the webhook notifications for the pipeline execution events", PIPELINE),
  PIPELINE_GOVERNANCE,
  PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION,
  PIPELINE_ROLLBACK("Flag to enable/disable Pipeline Rollback", HarnessTeam.PIPELINE),
  PLG_ENABLE_CROSS_GENERATION_ACCESS("Enables cross generation access", GTM),
  PL_ADD_ACL_CHECKS_NG_SCIM_API("Enable access control checks on token for NG SCIM API calls", HarnessTeam.PL),
  PL_AUDIT_LOG_STREAMING_ENABLED("Enables AuditLogStreaming tab on AuditTrails page in account scope", HarnessTeam.PL),
  PL_CG_SHOW_MEMBER_ID_COUNT(
      "Shows memberId count instead of member names on CG UserGroupListing page", HarnessTeam.PL),
  PL_CONNECTOR_ENCRYPTION_PRIVILEGED_CALL("make the encryption/decryption call as pirvileged call", HarnessTeam.PL),
  PL_DISCOVERY_ENABLE(
      "To control visibility of Discovery navlink in sidebar under project settings", HarnessTeam.CHAOS),
  PL_DO_NOT_MIGRATE_NON_ADMIN_CG_USERS_TO_NG("FF to disable CG to NG user migration except Admins", HarnessTeam.PL),
  PL_ENABLE_BASIC_ROLE_FOR_PROJECTS_ORGS(
      "Enable Basic Role assignment  Default User Group in Orgs and Projects", HarnessTeam.PL),
  PL_ENABLE_JIT_USER_PROVISION("Enable support for Just in time user provision", HarnessTeam.PL),
  PL_ENABLE_MULTIPLE_IDP_SUPPORT("Enable support for multiple SSO IDP in an account", HarnessTeam.PL),
  PL_FAVORITES("To enable favorites marking support on entities", HarnessTeam.PL),
  PL_FIX_INCONSISTENT_USER_DATA(
      "This FF process all users of this account and fixes their inconsistent data between CG Manager, NG manager and Access Control ",
      HarnessTeam.PL),
  PL_HELM2_DELEGATE_BANNER("FF for adding banner on delegate to mention deprecation of helm 2", HarnessTeam.PL),
  PL_HIDE_LAUNCH_NEXTGEN("FF to hide Launch NextGen button", HarnessTeam.PL),
  PL_HIDE_ORGANIZATION_LEVEL_MANAGED_ROLE("FF to hide organization level managed roles", HarnessTeam.PL),
  PL_HIDE_PROJECT_LEVEL_MANAGED_ROLE("FF to hide project level managed roles", HarnessTeam.PL),
  PL_IP_ALLOWLIST_NG("Enables IP Allowlist feature in NG.", HarnessTeam.PL),
  PL_LDAP_PARALLEL_GROUP_SYNC(
      "Enables User Group sync operation to fetch data from Ldap Server in Parallel. Enable only if Ldap Server can take the load",
      HarnessTeam.PL),
  PL_NEW_PAGE_SIZE(
      "Enables new default page size for several listing pages like pipelines, access control, executions, connectors, etc.",
      HarnessTeam.PL),
  PL_NEW_SCIM_STANDARDS("Changes required for being SCIM 2 compliant API calls", HarnessTeam.PL),
  PL_NO_EMAIL_FOR_SAML_ACCOUNT_INVITES("No email for users in account where SAML auth is enabled", HarnessTeam.PL),
  PL_REGENERATE_ACL_FOR_DEFAULT_VIEWER_ROLE(
      "This is to regenerate acls for default account viewer roles", HarnessTeam.PL),
  PL_REMOVE_USER_VIEWER_ROLE_ASSIGNMENTS("Enable removal of user level viewer role assignments", HarnessTeam.PL),
  PL_SUPPORT_JWT_TOKEN_SCIM_API("Enable support for external OAuth JWT token for SCIM API calls", HarnessTeam.PL),
  PL_UPDATE_CONNECTOR_HEARTBEAT_PPT("To update the perpetualTask", HarnessTeam.PL),
  PL_USER_ACCOUNT_LEVEL_DATA_FLOW(
      "Enables the new flow for all User CRUD where User's Account level Data is also considered.", HarnessTeam.PL),
  PL_USER_ACCOUNT_LEVEL_DATA_MIGRATION(
      "Enables Migration to create user account level data map for this account", HarnessTeam.PL),
  PL_USER_DELETION_V2("Modularising user deletion flows separately for CG and NG ", HarnessTeam.PL),
  PL_USE_CREDENTIALS_FROM_DELEGATE_FOR_GCP_SM(
      "Enables the use of credentials from Delegate in GCP Secret Manager", HarnessTeam.PL),
  POST_PROD_ROLLBACK("Flag to enable/disable PostProd Rollback", HarnessTeam.PIPELINE),
  PRUNE_KUBERNETES_RESOURCES,
  PURGE_DANGLING_APP_ENV_REFS("Explicitly purge dangling references of app/env", HarnessTeam.SPG),
  QUEUED_COUNT_FOR_QUEUEKEY("Used to display the count of the queue in CG git sync", HarnessTeam.SPG),
  QUEUE_CI_EXECUTIONS("FF to enable queueing in CI builds", HarnessTeam.CI),
  QUEUE_CI_EXECUTIONS_CONCURRENCY("FF to enable queueing in CI builds", HarnessTeam.CI),
  QUEUE_DELEGATE_TASK("Delegate task queueing with queue service(HQS)", HarnessTeam.DEL),
  RANCHER_SUPPORT,
  RATE_LIMITED_TOTP,
  READ_ENCRYPTED_DELEGATE_TOKEN("Read encrypted delegate token value", HarnessTeam.DEL),
  RECOMMENDATION_EFFICIENCY_VIEW_UI("Enable efficiency view instead cost view in Recommendation", HarnessTeam.CE),
  REDUCE_DELEGATE_MEMORY_SIZE("Reduce CG delegate memory to 4GB", HarnessTeam.DEL),
  REFACTOR_ARTIFACT_SELECTION,
  REFACTOR_STATEMACHINEXECUTOR,
  REJECT_TRIGGER_IF_ARTIFACTS_NOT_MATCH,
  REMOVE_STENCIL_MANUAL_INTERVENTION,
  REMOVE_USERGROUP_CHECK(
      "Customers started facing NPE due to migration of usergroup reference, removed null check behind FF - ticket ID - CDS-39770, CG",
      HarnessTeam.SPG),
  RESOLVE_DEPLOYMENT_TAGS_BEFORE_EXECUTION,
  RESOURCE_CONSTRAINT_MAX_QUEUE,
  ROLLBACK_NONE_ARTIFACT,
  ROLLBACK_PROVISIONER_AFTER_PHASES,
  SAVE_ARTIFACT_TO_DB("Saves artifact to db and proceed in artifact collection step if not found", HarnessTeam.CDC),
  SAVE_SHELL_SCRIPT_PROVISION_OUTPUTS_TO_SWEEPING_OUTPUT,
  SAVE_TERRAFORM_APPLY_SWEEPING_OUTPUT_TO_WORKFLOW,
  SAVE_TERRAFORM_OUTPUTS_TO_SWEEPING_OUTPUT,
  SEARCH_REQUEST,
  SEARCH_USERGROUP_BY_APPLICATION("Search in usergroup by application in CG", HarnessTeam.SPG),
  SELF_SERVICE_ENABLED,
  SEND_SLACK_NOTIFICATION_FROM_DELEGATE,
  SERVICE_DASHBOARD_V2,
  SERVICE_ID_FILTER_FOR_TRIGGERS(
      "Filter last deployed artifacts for triggers using serviceId as well", HarnessTeam.SPG),
  SETTINGS_OPTIMIZATION,
  SINGLE_MANIFEST_SUPPORT,
  SKIP_ADDING_TRACK_LABEL_SELECTOR_IN_ROLLING,
  SKIP_BASED_ON_STACK_STATUSES,
  SKIP_SWITCH_ACCOUNT_REAUTHENTICATION,
  SLACK_APPROVALS,
  SOCKET_HTTP_STATE_TIMEOUT,
  SORT_ARTIFACTS_IN_UPDATED_ORDER("Sort the collected artifacts by lastUpdatedAt", HarnessTeam.SPG),
  SPG_2K_DEFAULT_PAGE_SIZE("Increase the default page size to 2000 elements in CG", HarnessTeam.SPG),
  SPG_ADOPT_DELEGATE_DECRYPTION_ON_SERVICE_VARIABLES(
      "Enable to resolve expression using nested values from secret.getValue from service variables", HarnessTeam.SPG),
  SPG_ALLOW_DISABLE_TRIGGERS("Allow disabling triggers at application level for CG", HarnessTeam.SPG),
  SPG_ALLOW_DISABLE_USER_GITCONFIG(
      "Allow disabling local delegate user's .gitconfig when running git commands", HarnessTeam.SPG),
  SPG_ALLOW_FILTER_BY_PATHS_GCS("Enables filtering by path on database GCS-type stream artifacts.", HarnessTeam.SPG),
  SPG_ALLOW_GET_BUILD_SYNC("Allow get builds sync from gcs", HarnessTeam.SPG),
  SPG_ALLOW_REFRESH_PIPELINE_EXECUTION_BEFORE_CONTINUE_PIPELINE("Enables refresh pipeline when trigger "
          + "continue pipeline execution",
      HarnessTeam.SPG),
  SPG_ALLOW_TEMPLATE_ON_NEXUS_ARTIFACT(
      "Enables UI to use artifactID and groupID as template or static value", HarnessTeam.SPG),
  SPG_ALLOW_UI_JIRA_CUSTOM_DATETIME_FIELD("Enables backend parse custom field time of jira as the UI", HarnessTeam.SPG),
  SPG_ALLOW_WFLOW_VARIABLES_TO_CONDITION_SKIP_PIPELINE_STAGE("Enables the use of workflow variables to skip"
          + " pipeline stage",
      HarnessTeam.SPG),
  SPG_CD_RUN_STEP("CD run step in NG", HarnessTeam.SPG),
  SPG_CG_END_OF_LIFE_BANNER(
      "Shows the user a banner notifying about the End of Life of CG CD new features", HarnessTeam.SPG),
  SPG_CG_LIST_RESUMED_PIPELINES(
      "Allows resumed workflow/pipelines to be listed on the deployment history page", HarnessTeam.SPG),
  SPG_CG_REJECT_PRIORITY_WHEN_FORK_STATE(
      "Set the reject status to have higher priority over other failed statuses when handling responses inside a fork state",
      HarnessTeam.SPG),
  SPG_CG_SEGMENT_EVENT_FIRST_DEPLOYMENT(
      "Disable evaluation of first deployment condition to avoid unoptimized query execution on each completed deployment",
      HarnessTeam.SPG),
  SPG_CG_STATS_INSTANCE_CONSUMER("Optimize stats collector for instance collection", HarnessTeam.SPG),
  SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW("Enable timeout failure strategy at workflow level", HarnessTeam.SPG),
  SPG_CG_K8S_SECRET_MANAGER_CAPABILITIES("Enable to enforce secret manager capabilities on k8s tasks", HarnessTeam.SPG),
  SPG_CG_WFE_PROJECTION_FIELDS(
      "Enable to optimize workflow execution queries to retrieve only required fields using projection",
      HarnessTeam.SPG),
  SPG_CHANGE_SECRET_VAULT_PATTERN_ON_YAML("Change the format of secret in yaml when use vault", HarnessTeam.SPG),
  SPG_DASHBOARD_STATS_OPTIMIZE_ACTIVE_SERVICES(
      "Active services slow api call optimization. Also solves MOM issue in CG.", HarnessTeam.SPG),
  SPG_DASHBOARD_STATS_OPTIMIZE_DEPLOYMENTS(
      "Dashboard stats slow api call optimization. Also solves MOM issue in CG.", HarnessTeam.SPG),
  SPG_DELETE_ENVIRONMENTS_ON_SERVICE_RENAME_GIT_SYNC(
      "On service rename delete stale folders inside environments folders.", HarnessTeam.SPG),
  SPG_DISABLE_CUSTOM_WEBHOOK_V3_URL("This is used to disable customer webhook authentication.", SPG),
  SPG_DISABLE_EXPIRING_TO_MANUAL_INTERVENTION_CANDIDATE(
      "Disable the expiration of stateExecutionInstances candidates of manual intervention", HarnessTeam.SPG),
  SPG_DISABLE_SEARCH_DEPLOYMENTS_PAGE("Disable search on deployment page in CG.", HarnessTeam.SPG),
  SPG_DISABLE_SECRET_DETAILS("Disable secret management logs show in CG", HarnessTeam.SPG),
  SPG_ENABLE_EMAIL_VALIDATION("Enable email validation in GraphQL approveOrRejectApprovals mutation", HarnessTeam.SPG),
  SPG_ENABLE_GIT_SYNC_YAML_VALIDATE("Enable yaml validate in git sync", HarnessTeam.SPG),
  SPG_ENABLE_NOTIFICATION_RULES("Enables notification rules and approvals notifications by usergroup", HarnessTeam.SPG),
  SPG_ENABLE_POPULATE_USING_ARTIFACT_VARIABLE("Enable to populate artifact using artifact variables", HarnessTeam.SPG),
  SPG_ENABLE_SHARING_FILTERS("Enables account admin share deployments filter using usergroups", HarnessTeam.SPG),
  SPG_ENABLE_STATUS_OF_DEPLOYMENTS("Enables a way to see deployments status by env", HarnessTeam.SPG),
  SPG_ENABLE_VALIDATION_WORKFLOW_PIPELINE_STAGE(
      "Enables validation of dot on pipeline and workflow name", HarnessTeam.SPG),
  SPG_ENFORCE_TIME_RANGE_DEPLOYMENTS_WITHOUT_APP_ID(
      "This feature flag enforces maximum time range for workflow execution queries without appId", HarnessTeam.SPG),
  SPG_ENVIRONMENT_QUERY_LOGS(
      "This is a debug FF, no behaviour will be changed. We are adding logs to help find a root cause", SPG),
  SPG_FETCH_ARTIFACT_FROM_DB("Fetch artifact from database if available in artifact collection step", HarnessTeam.SPG),
  SPG_FIX_APPROVAL_WAITING_FOR_INPUTS(
      "Fixes a bug where approval step is going to waiting for inputs state", HarnessTeam.SPG),
  SPG_GENERATE_CURL_WITHOUT_ARTIFACT(
      "Enable curl generation to trigger when last collected without artifacts.", HarnessTeam.SPG),
  SPG_GRAPHQL_VERIFY_APPLICATION_FROM_USER_GROUP(
      "Verify if application references from a user group still exist", HarnessTeam.SPG),
  SPG_HTTP_STEP_CERTIFICATE("Allow enforce SSL/TLS certificate in HTTP step", HarnessTeam.SPG),
  SPG_LIVE_DASHBOARD_STATS_DEBUGGING("Live debugging for dashboard stats in CG", HarnessTeam.SPG),
  SPG_LOG_SERVICE_ENABLE_DOWNLOAD_LOGS("Enable download logs in ng. Only used by ui.", HarnessTeam.SPG),
  SPG_MODULE_VERSION_INFO("Enable version information on module level", HarnessTeam.SPG),
  SPG_NEW_DEPLOYMENT_FREEZE_EXCLUSIONS(
      "Flag to support deployment freeze exclusions. Depends on NEW_DEPLOYMENT_FREEZE", HarnessTeam.SPG),
  SPG_OPTIMIZE_PIPELINE_QUERY_ON_AUTH("Optimizes auth on pipelines making the query more efficient.", HarnessTeam.SPG),
  SPG_PIPELINE_ROLLBACK("Enables pipeline rollback on failure option", HarnessTeam.SPG),
  SPG_REMOVE_REDUNDANT_UPDATE_IN_AUDIT("It removes a redudant update on the audit", HarnessTeam.SPG),
  SPG_REMOVE_RESTRICTION_APPS_UNNECESSARY_CALLS(
      "Unnecessary restriction apps calls are not called from UI", HarnessTeam.SPG),
  SPG_SAVE_REJECTED_BY_FREEZE_WINDOWS(
      "Flag that enables populating WorkflowExecution with ids of freeze windows that rejected the execution",
      HarnessTeam.SPG),
  SPG_SEND_TRIGGER_PIPELINE_FOR_WEBHOOKS_ASYNC(
      "Will fire the all the triggers to be fired by a single webhook event asyncrounouly", HarnessTeam.SPG),
  SPG_SERVICES_OVERVIEW_RBAC(
      "Applies RBAC on services overview page, only displays services which the user has access to read",
      HarnessTeam.SPG),
  SPG_SETTINGS_INFINITY_SCROLL_FIX("Fixes infinte scroll used with infinite scroll.", HarnessTeam.SPG),
  SPG_SIDENAV_COLLAPSE("FF for enabling collapse and expand of side nav", HarnessTeam.SPG),
  SPG_TRIGGER_FOR_ALL_ARTIFACTS_NG(
      "Will fire the artifact and manifest triggers for all the versions in the polling response instead of the latest only",
      HarnessTeam.SPG),
  SPG_UI_ALLOW_ENCODING_FOR_JENKINS_ARTIFACT("Enables correct encoding for jenkins artifact", HarnessTeam.SPG),
  SPG_GC_ALLOW_GRAPHQL_GET_EXECUTIONS_RUNNING("Enables running rollout deployment in graphql", HarnessTeam.SPG),
  SPG_USE_NEW_METADATA("To use new metadata endpoint for jira server version greater than 9.0", HarnessTeam.SPG),
  SPG_WFE_PROJECTIONS_DEPLOYMENTS_PAGE("Enable projection on deployments page and executions", HarnessTeam.SPG),
  SPG_WFE_PROJECTIONS_GRAPHQL_DEPLOYMENTS_PAGE(
      "Enable projection on deployments page and graphql executions", HarnessTeam.SPG),
  SPG_WORKFLOW_RBAC_ON_TRIGGER_RESOURCE(
      "Create a binding with Workflow/Pipeline RBAC on triggers resource", HarnessTeam.SPG),
  SRM_CODE_ERROR_NOTIFICATIONS("Feature flag for Code Error notification condition", HarnessTeam.CV),
  SRM_COMMON_MONITORED_SERVICE(
      "Flag to be used in UI for controlling common monitored service listing", HarnessTeam.CV),
  SRM_CUSTOM_CHANGE_SOURCE("UI FF to enable Custom Change Source", HarnessTeam.CV),
  SRM_DATADOG_METRICS_FORMULA_SUPPORT("Support datadog metric formulas in the query of health source", HarnessTeam.CV),
  SRM_DOWNTIME("Flag to start creating downtime", HarnessTeam.CV),
  SRM_ENABLE_AGGREGATION_USING_BY_IN_PROMETHEUS(
      "This is used make prometheus Health source run with promQL by clause", HarnessTeam.CV),
  SRM_ENABLE_BASELINE_BASED_VERIFICATION("This is used to enable baseline based verification.", HarnessTeam.CV),
  SRM_ENABLE_GRAFANA_LOKI_LOGS("This is used to enable Grafana Loki logs health source.", HarnessTeam.CV),
  SRM_ENABLE_AZURE_LOGS("Will enable Azure logs health source.", HarnessTeam.CV),
  SRM_ENABLE_JIRA_INTEGRATION("Enable Jira integration in CVNG Verify step UI", HarnessTeam.CV),
  SRM_ENABLE_REQUEST_SLO("Control Request Based SLO feature on UI", HarnessTeam.CV),
  SRM_ENABLE_SIMPLE_VERIFICATION("This is used to enable simple verification.", HarnessTeam.CV),
  SRM_ENABLE_SLI_BUCKET("This is used to enable sli bucket reads", HarnessTeam.CV),
  SRM_ET_CRITICAL_EVENTS("Enable code errors critical events configuration", HarnessTeam.CV),
  SRM_ET_EXPERIMENTAL("Feature flag for SRM only Error Tracking development", HarnessTeam.CV),
  SRM_ET_JIRA_INTEGRATION("Enable code errors JIRA integration", HarnessTeam.CV),
  SRM_ET_RESOLVED_EVENTS("Feature flag for Error Tracking resolved events", HarnessTeam.CV),
  SRM_INTERNAL_CHANGE_SOURCE_CE("Control Chaos Engineering Internal Change Source from backend", HarnessTeam.CV),
  SRM_LICENSE_ENABLED,
  SRM_LOG_FEEDBACK_ENABLE_UI("Enable FE for Log feedback", HarnessTeam.CV),
  SRM_MICRO_FRONTEND("This FF is used to enable the micro-frontend for SRM", CV),
  SRM_MONITORED_SERVICE_VALIDATION(
      "Will Enable Service, Environment and Connector Ref Validation for Monitored Service Create/Update",
      HarnessTeam.CV),
  SRM_SLO_ANNOTATIONS("Flag to start creating SLO annotations", HarnessTeam.CV),
  SRM_SLO_TOGGLE,
  SRM_SPLUNK_SIGNALFX("Will enable SignalFX metric health source in SRM", HarnessTeam.CV),
  SRM_TELEMETRY("Will enable telemetry for verify step result", HarnessTeam.CV),
  SRM_ENABLE_ANALYZE_DEPLOYMENT_STEP("This is used to enable analyze deployment step in the pipeline", HarnessTeam.CV),
  SSCA_ENABLED("FF to enable SSCA on Harness", HarnessTeam.SSCA),
  SSH_JSCH_LOGS,
  STALE_FLAGS_FFM_1510,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_AMI_DEPLOYMENTS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_AMI_SPOT_INST_DEPLOYMENTS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_CODE_DEPLOY_DEPLOYMENTS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_LAMBDA_DEPLOYMENTS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_SSH_DEPLOYMENTS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AZURE_INFRA_DEPLOYMENTS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_CONTAINER_DEPLOYMENTS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_PCF_DEPLOYMENTS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_PDC_DEPLOYMENTS,
  STOP_SHOWING_RUNNING_EXECUTIONS,
  STO_AI_ENHANCED_REMEDIATIONS(
      "Enable STO to enhance security issues with remediation information using generative AI", HarnessTeam.STO),
  STO_BASELINE_REGEX("Enable selection of baselines by RegEx from Test Targets page", HarnessTeam.STO),
  STO_JIRA_INTEGRATION("Enable Jira integration for STO", HarnessTeam.STO),
  STO_STEPS_TEST_MODE(
      "Enable the rest of STO Steps Q3 2023 and beyond, NOT READY for use in PRODUCTION", HarnessTeam.STO),
  STO_STEP_PALETTE_BURP_ENTERPRISE("Enable Burp Enterpise step for STO", HarnessTeam.STO),
  STO_STEP_PALETTE_CODEQL("Enable CodeQL step for STO", HarnessTeam.STO),
  STO_STEP_PALETTE_COVERITY("Enable Coverity step for STO", HarnessTeam.STO),
  STO_STEP_PALETTE_FOSSA("Enable Fossa step for STO", HarnessTeam.STO),
  STO_STEP_PALETTE_GIT_LEAKS("Enable Gitleaks step for STO", HarnessTeam.STO),
  STO_STEP_PALETTE_SEMGREP("Enable Semgrep step for STO", HarnessTeam.STO),
  STO_STEP_PALETTE_SYSDIG("Enable Sysdig step for STO", HarnessTeam.STO),
  SUPERVISED_TS_THRESHOLD,
  SYNC_GIT_CLONE_AND_COPY_TO_DEST_DIR(
      "This feature flag helps in synchronizing the git clone of repo and copying the files then to destination directory",
      HarnessTeam.CDP),
  TERRAFORM_AWS_CP_AUTHENTICATION,
  TERRAFORM_CONFIG_INSPECT_VERSION_SELECTOR,
  TERRAFORM_REMOTE_BACKEND_CONFIG("Enables storing Terraform backend configuration in a remote repo", HarnessTeam.CDP),
  TG_USE_AUTO_APPROVE_FLAG,
  THREE_PHASE_SECRET_DECRYPTION,
  TIMEOUT_FAILURE_SUPPORT,
  TIME_RANGE_FREEZE_GOVERNANCE,
  TIME_SCALE_CG_SYNC,
  TI_DOTNET,
  TI_MFE_ENABLED("Migrating TI UI to Microfrontend. This feature flag is needed to test/control the new architecture",
      HarnessTeam.CI),
  TRIGGERS_PAGE_PAGINATION,
  TRIGGER_FOR_ALL_ARTIFACTS,
  TRIGGER_YAML,
  UPDATE_EMAILS_VIA_SCIM("Will enable updating emails in Harness via SCIM", HarnessTeam.PL),
  USAGE_SCOPE_RBAC,
  USE_ANALYTIC_MONGO_FOR_GRAPHQL_QUERY,
  USE_CDC_FOR_PIPELINE_HANDLER,
  USE_GET_FILE_V2_GIT_CALL(
      "FF for customers on updated delegate to use GetFileV2 call which is more performance efficient",
      HarnessTeam.PIPELINE),
  USE_IMMUTABLE_DELEGATE("Use immutable delegate on download delegate from UI", HarnessTeam.DEL),
  USE_K8S_API_FOR_STEADY_STATE_CHECK(
      "Used to enable API based steady state check for K8s deployments, instead of using the kubectl binary present in delegate.",
      HarnessTeam.CDP),
  USE_LATEST_CHARTMUSEUM_VERSION,
  USE_NEXUS3_PRIVATE_APIS,
  USE_OLD_GIT_SYNC("Used for enabling old Git Experience on projects", HarnessTeam.PL),
  USE_PAGINATED_ENCRYPT_SERVICE, // To be only used by UI for safeguarding encrypt component changes in CG
  USE_TEXT_SEARCH_FOR_EXECUTION(
      "With this instead of using regex search we will use text search for CD page in CG", HarnessTeam.SPG),
  USE_TF_CLIENT,
  VALIDATE_PHASES_AND_ROLLBACK("Validate that each phase has your own rollback phase", HarnessTeam.SPG),
  VALIDATE_PROVISIONER_EXPRESSION,
  VALIDATE_SERVICE_NAME_IN_FILE_PATH("Validate the service name in yaml file path", HarnessTeam.SPG),
  VIEW_USAGE_ENABLED,
  WEBHOOK_TRIGGER_AUTHORIZATION,
  WF_VAR_MULTI_SELECT_ALLOWED_VALUES,
  WHITELIST_GRAPHQL,
  WHITELIST_PUBLIC_API,
  SPG_TRIPLE_TIMEOUT_FOR_ZIP_YAML_UPSERT("This makes timeout for upsert yaml entities zip file to be 90 seconds", SPG),
  WINRM_ASG_ROLLBACK("Used for Collect remaining instances rollback step", HarnessTeam.CDP),
  WINRM_COPY_CONFIG_OPTIMIZE,
  WINRM_KERBEROS_CACHE_UNIQUE_FILE,
  WINRM_SCRIPT_COMMAND_SPLIT(
      "Enables the new way of how to copy powershell/winrm script commands content to file on remote. (Copy is done in chunks of 4KB) ",
      HarnessTeam.CDP),
  WINRM_SCRIPT_COMMAND_SPLIT_NG(
      "Enables the new way of how to copy powershell/winrm script commands content to file on remote. (Copy is done in chunks of 6KB) ",
      HarnessTeam.CDP),
  PLG_CD_CLI_WIZARD_ENABLED("Enables new cd onboarding wizard with harness-cli", HarnessTeam.GTM),
  WORKFLOW_DATA_COLLECTION_ITERATOR,
  WORKFLOW_EXECUTION_REFRESH_STATUS,
  WORKFLOW_EXECUTION_ZOMBIE_MONITOR,
  WORKFLOW_PIPELINE_PERMISSION_BY_ENTITY,
  YAML_APIS_GRANULAR_PERMISSION,
  YAML_GIT_CONNECTOR_NAME,
  CDS_STEP_EXECUTION_DATA_SYNC(
      "This flag controls if you want Step execution data saved in mongodb and eventually synced to timescale",
      HarnessTeam.CDP),
  PIE_ERROR_ENHANCEMENTS("Enhance error framework for pipeline exceptions", PIPELINE),
  CDS_ENABLE_LOAD_FROM_CACHE_FOR_RETRY_FORM("Used for enabling load from cache on retry form", PIPELINE),
  CDS_GITHUB_PACKAGES("Used for SSH/WinRm copy and download Github packages artifacts", HarnessTeam.CDP),
  CDS_HELM_SEND_TASK_PROGRESS_NG("For Helm tasks we will be "
          + "sending task progress event via log streaming client which will be received and handled by handleProgress handler in the ng-manager step class",
      HarnessTeam.CDP),
  CDS_ENABLE_SHELL_SCRITPT_FILE_REFERENCE(
      "Used for ShellScript step in order to support scripts from Harness File Store", PIPELINE),
  CDS_QUEUE_SERVICE_FOR_TRIGGERS(
      "When this flag is enabled, triggers will be processed using queue service instead of mongo iterators", PIPELINE),
  CDS_MERGED_RUN_AND_RETRY_PIPELINE_COMPONENT(
      "When enabled RunPipelineForm component will be used for Retry From (Failed) Stage", HarnessTeam.CDP),
  CDS_SUPPORT_SERVICE_INPUTS_AS_EXECUTION_INPUTS(
      "Service Inputs will be marked as execution inputs if service is an expression", HarnessTeam.CDC),
  CDS_STORE_INSTANCE_STATS_ITERATOR_RUN_TIME(
      "FF for storing when the instance stats iterator ran for a particular service", HarnessTeam.CDP),
  CDS_NG_FETCH_FILES_REFACTOR(
      "Refactoring Fetch logic for K8s and helm step on manager side. PM Rohan", HarnessTeam.CDP);
  // keep-sorted end

  @Deprecated
  FeatureName() {
    scope = Scope.PER_ACCOUNT;
  }

  @Deprecated
  FeatureName(Scope scope) {
    this.scope = scope;
  }

  @Getter private FeatureFlag.Scope scope;

  FeatureName(String description, HarnessTeam owner) {
    this.description = description;
    this.owner = owner;
    this.scope = Scope.PER_ACCOUNT;
  }

  FeatureName(String description, HarnessTeam owner, FeatureFlag.Scope scope) {
    this.description = description;
    this.owner = owner;
    this.scope = scope;
  }

  @Getter private String description;
  private HarnessTeam owner;

  public String getOwner() {
    return owner.name();
  }
}
