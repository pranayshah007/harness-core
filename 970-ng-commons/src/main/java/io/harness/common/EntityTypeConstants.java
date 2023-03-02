/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.common;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PL)
public interface EntityTypeConstants {
  String GITOPS_CREATE_PR = "CreatePR";
  String GITOPS_MERGE_PR = "MergePR";
  String GITOPS_UPDATE_RELEASE_REPO = "GitOpsUpdateReleaseRepo";
  String GITOPS_FETCH_LINKED_APPS = "GitOpsFetchLinkedApps";
  String GITOPS_SYNC = "GitOpsSync";
  String PROJECTS = "Projects";
  String PIPELINES = "Pipelines";
  String PIPELINE_STEPS = "PipelineSteps";
  String CONNECTORS = "Connectors";
  String SECRETS = "Secrets";
  String FILES = "Files";
  String SERVICE = "Service";
  String ENVIRONMENT = "Environment";
  String INPUT_SETS = "InputSets";
  String CV_CONFIG = "CvConfig";
  String DELEGATES = "Delegates";
  String DELEGATE_CONFIGURATIONS = "DelegateConfigurations";
  String CV_VERIFICATION_JOB = "CvVerificationJob";
  String INTEGRATION_STAGE = "IntegrationStage";
  String INTEGRATION_STEPS = "IntegrationSteps";
  String CV_KUBERNETES_ACTIVITY_SOURCE = "CvKubernetesActivitySource";
  String DEPLOYMENT_STEPS = "DeploymentSteps";
  String DEPLOYMENT_STAGE = "DeploymentStage";
  String APPROVAL_STAGE = "ApprovalStage";
  String PIPELINE_STAGE = "PipelineStage";
  String CUSTOM_STAGE = "CustomStage";
  String FEATURE_FLAG_STAGE = "FeatureFlagStage";
  String TRIGGERS = "Triggers";
  String MONITORED_SERVICE = "MonitoredService";
  String TEMPLATE = "Template";
  String TEMPLATE_STAGE = "TemplateStage";
  String TEMPLATE_CUSTOM_DEPLOYMENT = "CustomDeployment";
  String FETCH_INSTANCE_SCRIPT = "FetchInstanceScript";
  String GIT_REPOSITORIES = "GitRepositories";
  String FEATURE_FLAGS = "FeatureFlags";
  String HTTP = "Http";
  String EMAIL = "Email";
  String JIRA_CREATE = "JiraCreate";
  String JIRA_UPDATE = "JiraUpdate";
  String SHELL_SCRIPT = "ShellScript";
  String K8S_CANARY_DEPLOY = "K8sCanaryDeploy";
  String K8S_APPLY = "K8sApply";
  String K8S_BLUE_GREEN_DEPLOY = "K8sBlueGreenDeploy";
  String K8S_ROLLING_DEPLOY = "K8sRollingDeploy";
  String K8S_ROLLING_ROLLBACK = "K8sRollingRollback";
  String K8S_SCALE = "K8sScale";
  String K8S_DELETE = "K8sDelete";
  String K8S_BG_SWAP_SERVICES = "K8sBGSwapServices";
  String K8S_CANARY_DELETE = "K8sCanaryDelete";

  String TAS_CANARY_APP_SETUP_STEP = "CanaryAppSetup";
  String TAS_BG_APP_SETUP_STEP = "BGAppSetup";
  String TAS_BASIC_APP_SETUP_STEP = "BasicAppSetup";
  String TAS_APP_RESIZE_STEP = "AppResize";
  String TAS_ROLLBACK_STEP = "AppRollback";
  String TAS_SWAP_ROUTES_STEP = "SwapRoutes";
  String TAS_SWAP_ROLLBACK_STEP = "SwapRollback";
  String TANZU_COMMAND_STEP = "TanzuCommand";

  String RUN_STEP = "Run";
  String BACKGROUND_STEP = "Background";
  String RUN_TEST = "RunTests";
  String PLUGIN = "Plugin";
  String RESTORE_CACHE_GCS = "RestoreCacheGCS";
  String RESTORE_CACHE_S3 = "RestoreCacheS3";
  String SAVE_CACHE_GCS = "SaveCacheGCS";
  String SAVE_CACHE_S3 = "SaveCacheS3";
  String SECURITY = "Security";
  String AQUA_TRIVY = "AquaTrivy";
  String AWS_ECR = "AWSECR";
  String BANDIT = "Bandit";
  String BLACKDUCK = "BlackDuck";
  String BRAKEMAN = "Brakeman";
  String BURP = "Burp";
  String CHECKMARX = "Checkmarx";
  String CLAIR = "Clair";
  String DATA_THEOREM = "DataTheorem";
  String DOCKER_CONTENT_TRUST = "DockerContentTrust";
  String EXTERNAL = "External";
  String FORTIFY_ON_DEMAND = "FortifyOnDemand";
  String GRYPE = "Grype";
  String JFROG_XRAY = "JfrogXray";
  String MEND = "Mend";
  String METASPLOIT = "Metasploit";
  String NESSUS = "Nessus";
  String NEXUS_IQ = "NexusIQ";
  String NIKTO = "Nikto";
  String NMAP = "Nmap";
  String OPENVAS = "Openvas";
  String OWASP = "Owasp";
  String PRISMA_CLOUD = "PrismaCloud";
  String PROWLER = "Prowler";
  String QUALYS = "Qualys";
  String REAPSAW = "Reapsaw";
  String SHIFT_LEFT = "ShiftLeft";
  String SNIPER = "Sniper";
  String SNYK = "Snyk";
  String SONARQUBE = "Sonarqube";
  String SYSDIG = "Sysdig";
  String TENABLE = "Tenable";
  String VERACODE = "Veracode";
  String ZAP = "Zap";
  String SECURITY_STAGE = "SecurityStage";
  String SECURITY_STEPS = "SecuritySteps";
  String GIT_CLONE = "GitClone";
  String ARTIFACTORY_UPLOAD = "ArtifactoryUpload";
  String GCS_UPLOAD = "GCSUpload";
  String S3_UPLOAD = "S3Upload";

  String BUILD_AND_PUSH_GCR = "BuildAndPushGCR";
  String BUILD_AND_PUSH_ECR = "BuildAndPushECR";
  String BUILD_AND_PUSH_ACR = "BuildAndPushACR";
  String BUILD_AND_PUSH_DOCKER_REGISTRY = "BuildAndPushDockerRegistry";
  String ACTION_STEP = "Action";
  String BITRISE_STEP = "Bitrise";

  String TERRAFORM_APPLY = "TerraformApply";
  String TERRAFORM_PLAN = "TerraformPlan";
  String TERRAFORM_DESTROY = "TerraformDestroy";
  String TERRAFORM_ROLLBACK = "TerraformRollback";
  String HELM_DEPLOY = "HelmDeploy";
  String HELM_ROLLBACK = "HelmRollback";
  String SERVICENOW_APPROVAL = "ServiceNowApproval";
  String JIRA_APPROVAL = "JiraApproval";
  String HARNESS_APPROVAL = "HarnessApproval";
  String CUSTOM_APPROVAL = "CustomApproval";
  String BARRIER = "Barrier";
  String Verify = "Verify";
  String FlagConfiguration = "FlagConfiguration";
  String OPAPOLICIES = "GovernancePolicies";
  String POLICY_STEP = "Policy";
  String SERVICENOW_CREATE = "ServiceNowCreate";
  String SERVICENOW_UPDATE = "ServiceNowUpdate";
  String SERVICENOW_IMPORT_SET = "ServiceNowImportSet";
  String ENVIRONMENT_GROUP = "EnvironmentGroup";
  String NG_FILE = "NgFile";
  String CLOUDFORMATION_CREATE_STACK_STEP = "CreateStack";
  String CLOUDFORMATION_DELETE_STACK_STEP = "DeleteStack";
  String SERVERLESS_AWS_LAMBDA_DEPLOY = "ServerlessAwsLambdaDeploy";
  String SERVERLESS_AWS_LAMBDA_ROLLBACK = "ServerlessAwsLambdaRollback";
  String CLOUDFORMATION_ROLLBACK_STACK_STEP = "RollbackStack";
  String INFRASTRUCTURE = "Infrastructure";
  String COMMAND = "Command";
  String STRATEGY_NODE = "StrategyNode";
  String AZURE_SLOT_DEPLOYMENT = "AzureSlotDeployment";
  String AZURE_TRAFFIC_SHIFT = "AzureTrafficShift";
  String AZURE_SWAP_SLOT = "AzureSwapSlot";
  String AZURE_WEBAPP_ROLLBACK = "AzureWebAppRollback";
  String QUEUE = "Queue";
  String JENKINS_BUILD = "JenkinsBuild";
  String ECS_ROLLING_DEPLOY = "EcsRollingDeploy";
  String ECS_ROLLING_ROLLBACK = "EcsRollingRollback";
  String ECS_CANARY_DEPLOY = "EcsCanaryDeploy";
  String ECS_CANARY_DELETE = "EcsCanaryDelete";
  String AZURE_CREATE_ARM_RESOURCE_STEP = "AzureCreateARMResource";
  String AZURE_CREATE_BP_RESOURCE_STEP = "AzureCreateBPResource";
  String AZURE_ROLLBACK_ARM_RESOURCE_STEP = "AzureARMRollback";
  String ECS_RUN_TASK = "EcsRunTask";
  String ECS_BLUE_GREEN_CREATE_SERVICE = "EcsBlueGreenCreateService";
  String ECS_BLUE_GREEN_SWAP_TARGET_GROUPS = "EcsBlueGreenSwapTargetGroups";
  String ECS_BLUE_GREEN_ROLLBACK = "EcsBlueGreenRollback";
  String WAIT_STEP = "Wait";
  String ARTIFACT_SOURCE_TEMPLATE = "ArtifactSource";
  String SHELL_SCRIPT_PROVISION_STEP = "ShellScriptProvision";
  String FREEZE = "Freeze";
  String CHAOS_STEP = "Chaos";
  String ELASTIGROUP_DEPLOY_STEP = "ElastigroupDeploy";
  String ELASTIGROUP_ROLLBACK_STEP = "ElastigroupRollback";
  String IACM_STAGE = "IACMStage";
  String IACM_STEPS = "IACMStep";
  String IACM = "IACM";
  String ELASTIGROUP_SETUP = "ElastigroupSetup";
  String TERRAGRUNT_PLAN = "TerragruntPlan";
  String TERRAGRUNT_APPLY = "TerragruntApply";
  String TERRAGRUNT_DESTROY = "TerragruntDestroy";
  String TERRAGRUNT_ROLLBACK = "TerragruntRollback";
  String CONTAINER_STEP = "Container";
  String IACM_TERRAFORM_PLAN = "IACMTerraformPlan";
  String IACM_TEMPLATE = "IACMTemplate";

  String ELASTIGROUP_BG_STAGE_SETUP = "ElastigroupBGStageSetup";
  String ELASTIGROUP_SWAP_ROUTE = "ElastigroupSwapRoute";
  String ASG_CANARY_DEPLOY = "AsgCanaryDeploy";
  String ASG_CANARY_DELETE = "AsgCanaryDelete";
  String ASG_ROLLING_DEPLOY = "AsgRollingDeploy";
  String ASG_ROLLING_ROLLBACK = "AsgRollingRollback";
  String ASG_BLUE_GREEN_DEPLOY = "AsgBlueGreenDeploy";
  String ASG_BLUE_GREEN_ROLLBACK = "AsgBlueGreenRollback";
  String CCM_GOVERNANCE_RULE_AWS = "GovernanceRuleAWS";

  String GOOGLE_CLOUD_FUNCTIONS_DEPLOY = "DeployCloudFunction";
  String GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC = "DeployCloudFunctionWithNoTraffic";
  String GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT = "CloudFunctionTrafficShift";
  String GOOGLE_CLOUD_FUNCTIONS_ROLLBACK = "CloudFunctionRollback";

  String TAS_ROLLING_DEPLOY = "TasRollingDeploy";
  String TAS_ROLLING_ROLLBACK = "TasRollingRollback";
  String K8S_DRY_RUN_MANIFEST = "K8sDryRun";
  String ASG_BLUE_GREEN_SWAP_SERVICE_STEP = "AsgBlueGreenSwapService";

  String TERRAFORM_CLOUD_RUN = "TerraformCloudRun";
  String TERRAFORM_CLOUD_ROLLBACK = "TerraformCloudRollback";

  String AWS_LAMBDA_DEPLOY = "AwsLambdaDeploy";

  // AWS SAM
  String AWS_SAM_DEPLOY = "AwsSamDeploy";
  String AWS_SAM_ROLLBACK = "AwsSamRollback";
  String SSCA_ORCHESTRATION = "SscaOrchestration";

  String AWS_LAMBDA_ROLLBACK = "AwsLambdaRollback";
}
