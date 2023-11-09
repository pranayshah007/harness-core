/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.visitor.v1;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.manifest.ManifestType;
import io.harness.executions.steps.v1.StepSpecTypeConstantsV1;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.utilities.VisitorParentPathUtils;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDC)
public final class YamlTypesV1 {
  public static final String GITOPS_MERGE_PR = StepSpecTypeConstantsV1.GITOPS_MERGE_PR;
  public static final String UPDATE_RELEASE_REPO = StepSpecTypeConstantsV1.GITOPS_UPDATE_RELEASE_REPO;
  public static final String GITOPS_SYNC = StepSpecTypeConstantsV1.GITOPS_SYNC;
  public static final String PRIMARY_ARTIFACT = "primary";
  public static final String PRIMARY_ARTIFACT_REF = "primaryArtifactRef";
  public static final String ARTIFACT_SOURCES = "sources";
  public static final String IDENTIFIER = "identifier";
  public static final String ARTIFACT_LIST_CONFIG = "artifacts";
  public static final String SIDECAR_ARTIFACT_CONFIG = "sidecar";
  public static final String SIDECARS_ARTIFACT_CONFIG = "sidecars";
  public static final String ENVIRONMENT_YAML = "environment";
  public static final String ENVIRONMENT_GROUP_YAML = "environmentGroup";
  public static final String GITOPS_CLUSTERS = "gitopsClusters";
  public static final String ENVIRONMENT_REF = "environmentRef";
  public static final String ENVIRONMENT_GROUP_REF = "envGroupRef";
  public static final String INFRASTRUCTURE_DEF = "infrastructureDefinition";
  public static final String INFRASTRUCTURE_DEFS = "infrastructureDefinitions";
  public static final String INFRASTRUCTURE_STEP_PARAMETERS = "infrastructureStepParameters";
  public static final String ENVIRONMENT_NODE_ID = "environmentNodeId";
  public static final String DEPLOYMENT_STAGE = "deployment";

  public static final String K8S_ROLLING_ROLLBACK = StepSpecTypeConstantsV1.K8S_ROLLING_ROLLBACK;
  public static final String K8S_ROLLING_DEPLOY = StepSpecTypeConstantsV1.K8S_ROLLING_DEPLOY;
  public static final String K8S_BLUE_GREEN_DEPLOY = StepSpecTypeConstantsV1.K8S_BLUE_GREEN_DEPLOY;
  public static final String K8S_APPLY = StepSpecTypeConstantsV1.K8S_APPLY;
  public static final String K8S_SCALE = StepSpecTypeConstantsV1.K8S_SCALE;
  public static final String K8S_CANARY_DEPLOY = StepSpecTypeConstantsV1.K8S_CANARY_DEPLOY;
  public static final String K8S_BG_SWAP_SERVICES = StepSpecTypeConstantsV1.K8S_BG_SWAP_SERVICES;
  public static final String K8S_DELETE = StepSpecTypeConstantsV1.K8S_DELETE;
  public static final String K8S_CANARY_DELETE = StepSpecTypeConstantsV1.K8S_CANARY_DELETE;
  public static final String K8S_BLUE_GREEN_STAGE_SCALE_DOWN = StepSpecTypeConstantsV1.K8S_BLUE_GREEN_STAGE_SCALE_DOWN;

  public static final String HELM_DEPLOY = StepSpecTypeConstantsV1.HELM_DEPLOY;
  public static final String HELM_ROLLBACK = StepSpecTypeConstantsV1.HELM_ROLLBACK;

  public static final String SERVERLESS_AWS_LAMBDA_DEPLOY = StepSpecTypeConstantsV1.SERVERLESS_AWS_LAMBDA_DEPLOY;
  public static final String SERVERLESS_AWS_LAMBDA_ROLLBACK = StepSpecTypeConstantsV1.SERVERLESS_AWS_LAMBDA_ROLLBACK;
  public static final String COMMAND = StepSpecTypeConstantsV1.COMMAND;

  public static final String ELASTIGROUP_DEPLOY = StepSpecTypeConstantsV1.ELASTIGROUP_DEPLOY;
  public static final String ELASTIGROUP_ROLLBACK = StepSpecTypeConstantsV1.ELASTIGROUP_ROLLBACK;

  public static final String TAS_CANARY_APP_SETUP = StepSpecTypeConstantsV1.TAS_CANARY_APP_SETUP;
  public static final String TAS_BG_APP_SETUP = StepSpecTypeConstantsV1.TAS_BG_APP_SETUP;
  public static final String TAS_BASIC_APP_SETUP = StepSpecTypeConstantsV1.TAS_BASIC_APP_SETUP;
  public static final String TAS_APP_RESIZE = StepSpecTypeConstantsV1.TAS_APP_RESIZE;
  public static final String TAS_SWAP_ROUTES = StepSpecTypeConstantsV1.TAS_SWAP_ROUTES;
  public static final String TAS_ROLLBACK = StepSpecTypeConstantsV1.TAS_ROLLBACK;
  public static final String SWAP_ROLLBACK = StepSpecTypeConstantsV1.TAS_SWAP_ROLLBACK;
  public static final String TANZU_COMMAND = StepSpecTypeConstantsV1.TANZU_COMMAND;

  public static final String ECS_ROLLING_DEPLOY = StepSpecTypeConstantsV1.ECS_ROLLING_DEPLOY;
  public static final String ECS_ROLLING_ROLLBACK = StepSpecTypeConstantsV1.ECS_ROLLING_ROLLBACK;
  public static final String ECS_CANARY_DEPLOY = StepSpecTypeConstantsV1.ECS_CANARY_DEPLOY;
  public static final String ECS_CANARY_DELETE = StepSpecTypeConstantsV1.ECS_CANARY_DELETE;
  public static final String ECS_RUN_TASK = StepSpecTypeConstantsV1.ECS_RUN_TASK;
  public static final String ECS_BLUE_GREEN_CREATE_SERVICE = StepSpecTypeConstantsV1.ECS_BLUE_GREEN_CREATE_SERVICE;
  public static final String ECS_BLUE_GREEN_SWAP_TARGET_GROUPS =
      StepSpecTypeConstantsV1.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS;
  public static final String ECS_BLUE_GREEN_ROLLBACK = StepSpecTypeConstantsV1.ECS_BLUE_GREEN_ROLLBACK;
  public static final String ECS_SERVICE_SETUP = StepSpecTypeConstantsV1.ECS_SERVICE_SETUP;
  public static final String ECS_UPGRADE_CONTAINER = StepSpecTypeConstantsV1.ECS_UPGRADE_CONTAINER;
  public static final String ECS_BASIC_ROLLBACK = StepSpecTypeConstantsV1.ECS_BASIC_ROLLBACK;

  public static final String AZURE_SLOT_DEPLOYMENT = StepSpecTypeConstantsV1.AZURE_SLOT_DEPLOYMENT;
  public static final String AZURE_TRAFFIC_SHIFT = StepSpecTypeConstantsV1.AZURE_TRAFFIC_SHIFT;
  public static final String AZURE_SWAP_SLOT = StepSpecTypeConstantsV1.AZURE_SWAP_SLOT;
  public static final String AZURE_WEBAPP_ROLLBACK = StepSpecTypeConstantsV1.AZURE_WEBAPP_ROLLBACK;
  public static final String FETCH_INSTANCE_SCRIPT = StepSpecTypeConstantsV1.CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT;

  public static final String ASG_CANARY_DEPLOY = StepSpecTypeConstantsV1.ASG_CANARY_DEPLOY;
  public static final String ASG_CANARY_DELETE = StepSpecTypeConstantsV1.ASG_CANARY_DELETE;
  public static final String ASG_ROLLING_DEPLOY = StepSpecTypeConstantsV1.ASG_ROLLING_DEPLOY;
  public static final String ASG_ROLLING_ROLLBACK = StepSpecTypeConstantsV1.ASG_ROLLING_ROLLBACK;
  public static final String ASG_BLUE_GREEN_DEPLOY = StepSpecTypeConstantsV1.ASG_BLUE_GREEN_DEPLOY;
  public static final String ASG_BLUE_GREEN_ROLLBACK = StepSpecTypeConstantsV1.ASG_BLUE_GREEN_ROLLBACK;
  public static final String ASG_BLUE_GREEN_SWAP_SERVICE = StepSpecTypeConstantsV1.ASG_BLUE_GREEN_SWAP_SERVICE;
  public static final String GOOGLE_CLOUD_FUNCTIONS_DEPLOY = StepSpecTypeConstantsV1.GOOGLE_CLOUD_FUNCTIONS_DEPLOY;
  public static final String GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC =
      StepSpecTypeConstantsV1.GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC;
  public static final String GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT =
      StepSpecTypeConstantsV1.GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT;
  public static final String GOOGLE_CLOUD_FUNCTIONS_ROLLBACK = StepSpecTypeConstantsV1.GOOGLE_CLOUD_FUNCTIONS_ROLLBACK;
  public static final String GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_DEPLOY =
      StepSpecTypeConstantsV1.GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_DEPLOY;
  public static final String GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_ROLLBACK =
      StepSpecTypeConstantsV1.GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_ROLLBACK;
  public static final String TERRAFORM_CLOUD_RUN = StepSpecTypeConstantsV1.TERRAFORM_CLOUD_RUN;
  public static final String TERRAFORM_CLOUD_ROLLBACK = StepSpecTypeConstantsV1.TERRAFORM_CLOUD_ROLLBACK;
  public static final String MANIFEST_LIST_CONFIG = "manifests";
  public static final String MANIFEST_CONFIG = "manifest";
  public static final String K8S_MANIFEST = ManifestType.K8Manifest;
  public static final String HELM_CHART_MANIFEST = ManifestType.HelmChart;
  public static final String KUSTOMIZE_MANIFEST = ManifestType.Kustomize;
  public static final String OPENSHIFT_MANIFEST = ManifestType.OpenshiftTemplate;
  public static final String SPEC = "spec";
  public static final String PIPELINE_INFRASTRUCTURE = "infrastructure";
  public static final String SERVICE_CONFIG = "serviceConfig";
  public static final String SERVICE_SECTION = "serviceSection";
  public static final String SERVICE_ENTITY = "service";
  public static final String SERVICE_REF = "serviceRef";
  public static final String USE_FROM_STAGE = "useFromStage";
  public static final String SERVICE_DEFINITION = "serviceDefinition";
  public static final String SERVICE_SPEC = "spec";
  public static final String SERVICE_OVERRIDE = "serviceOverrides";
  public static final String OVERRIDE = "overrides";
  public static final String VARIABLES = "variables";
  public static final String SERVICE_INPUTS = "serviceInputs";
  public static final String STAGE_OVERRIDES_CONFIG = "stageOverrides";
  public static final String PATH_CONNECTOR = VisitorParentPathUtils.PATH_CONNECTOR;
  public static final String CONNECTOR_REF = "connectorRef";
  public static final String CONFIGURATION = "configuration";
  public static final String TAG = "tag";
  public static final String TAG_REGEX = "tagRegex";
  public static final String IMAGE_PATH = "imagePath";
  public static final String BRANCH = "branch";
  public static final String COMMIT_ID = "commitId";
  public static final String NAMESPACE = "namespace";
  public static final String RELEASE_NAME = "releaseName";
  public static final String CLUSTER = "cluster";
  public static final String STORE_CONFIG_WRAPPER = "store";
  public static final String CONFIG_FILES = "configFiles";
  public static final String CONFIG_FILE = "configFile";

  public static final String SKIP_DRY_RUN = "skipDryRun";
  public static final String OUTPUT = "output";
  public static final String TIMEOUT = "timeout";
  public static final String UUID = YamlNode.UUID_FIELD_NAME;
  public static final String HEADERS = "headers";
  public static final String DELEGATE_SELECTORS = "delegateSelectors";

  public static final String COMMAND_FLAGS_WRAPPER = "commandFlags";
  public static final String REGION = "region";
  public static final String PROJECT = "project";
  public static final String STAGE = "stage";
  public static final String TEMPLATE = "template";
  public static final String CREDENTIALS_REF = "credentialsRef";
  public static final String HOSTS = "hosts";
  public static final String HOST_FILTER = "hostFilter";
  public static final String SIDECARS = "sidecars";
  public static final String SIDECAR = "sidecar";
  public static final String ARTIFACTS = "artifacts";
  public static final String ROLLBACK_STEPS = "rollbackSteps";
  public static final String STEPS = "steps";
  public static final String STRATEGY = "strategy";
  public static final String STEP_GROUP = "stepGroup";
  public static final String PRIMARY = "primary";

  public static final String SUBSCRIPTION = "subscription";
  public static final String RESOURCE_GROUP = "resourceGroup";

  // METADATA for Service and Environment Plan Creator
  public static final String SERVICE_SPEC_UUID = "service_spec_uuid";
  public static final String POST_SERVICE_SPEC_UUID = "service_spec_uuid";
  public static final String INFRA_SECTION_UUID = "infra_section_uuid";
  public static final String NEXT_UUID = "nextUuid";

  public static final String CLOUD_PROVIDER = "cloudProvider";
  public static final String LOAD_BALANCER = "loadBalancer";
  public static final String HOST_NAME_CONVENTION = "hostNameConvention";

  public static final String APP_SERVICE = "appService";
  public static final String DEPLOYMENT_SLOT = "deploymentSlot";

  public static final String ENVIRONMENT_INPUTS = "environmentInputs";
  public static final String SERVICE_OVERRIDE_INPUTS = "serviceOverrideInputs";
  public static final String INPUTS = "inputs";
  public static final String REF = "ref";
  public static final String JENKINS_BUILD = StepSpecTypeConstantsV1.JENKINS_BUILD;
  public static final String BAMBOO_BUILD = StepSpecTypeConstantsV1.BAMBOO_BUILD;
  public static final String STARTUP_COMMAND = "startupCommand";

  public static final String ELASTIGROUP_SERVICE_SETTINGS_STEP = "Elastigroup_Service_Settings";
  public static final String AZURE_SERVICE_SETTINGS_STEP = "Azure_Service_Settings";
  public static final String ASG_SERVICE_SETTINGS_STEP = "Asg_Service_Settings";

  public static final String APPLICATION_SETTINGS = "applicationSettings";
  public static final String CONNECTION_STRINGS = "connectionStrings";
  public static final String DEPLOYMENT_TYPE = "deployment";

  public static final String ENV_PRODUCTION = "Production";
  public static final String ENV_PRE_PRODUCTION = "PreProduction";

  public static final String SERVICE_ENTITIES = "services";

  public static final String PIPELINE = "pipeline";
  public static final String PARALLEL_STAGE = "parallel";
  public static final String STAGES = "stages";
  public static final String ELASTIGROUP_SETUP = StepSpecTypeConstantsV1.ELASTIGROUP_SETUP;

  public static final String ORG = "organization";
  public static final String SPACE = "space";

  public static final String SERVICE_HOOKS = "hooks";
  public static final String PRE_HOOK = "preHook";
  public static final String POST_HOOK = "postHook";
  public static final String ELASTIGROUP_BG_STAGE_SETUP = StepSpecTypeConstantsV1.ELASTIGROUP_BG_STAGE_SETUP;
  public static final String ELASTIGROUP_SWAP_ROUTE = StepSpecTypeConstantsV1.ELASTIGROUP_SWAP_ROUTE;

  public static final String TAS_ROLLING_DEPLOY = StepSpecTypeConstantsV1.TAS_ROLLING_DEPLOY;
  public static final String TAS_ROLLING_ROLLBACK = StepSpecTypeConstantsV1.TAS_ROLLING_ROLLBACK;

  public static final String K8S_DRY_RUN_MANIFEST = StepSpecTypeConstantsV1.K8S_DRY_RUN_MANIFEST;

  public static final String AWS_LAMBDA_DEPLOY = StepSpecTypeConstantsV1.AWS_LAMBDA_DEPLOY;

  // AWS SAM
  public static final String AWS_SAM_DEPLOY = StepSpecTypeConstantsV1.AWS_SAM_DEPLOY;
  public static final String AWS_SAM_BUILD = StepSpecTypeConstantsV1.AWS_SAM_BUILD;
  public static final String AWS_SAM_ROLLBACK = StepSpecTypeConstantsV1.AWS_SAM_ROLLBACK;
  public static final String DOWNLOAD_MANIFESTS = StepSpecTypeConstantsV1.DOWNLOAD_MANIFESTS;

  public static final String DOWNLOAD_SERVERLESS_MANIFESTS = StepSpecTypeConstantsV1.DOWNLOAD_SERVERLESS_MANIFESTS;

  public static final String AWS_LAMBDA_ROLLBACK = StepSpecTypeConstantsV1.AWS_LAMBDA_ROLLBACK;
  public static final String TAS_ROUTE_MAPPING = StepSpecTypeConstantsV1.TAS_ROUTE_MAPPING;

  // SERVERLESS CONTAINER STEPS
  public static final String SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_V2 =
      StepSpecTypeConstantsV1.SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_V2;
  public static final String SERVERLESS_AWS_LAMBDA_ROLLBACK_V2 =
      StepSpecTypeConstantsV1.SERVERLESS_AWS_LAMBDA_ROLLBACK_V2;
  public static final String SERVERLESS_AWS_LAMBDA_DEPLOY_V2 = StepSpecTypeConstantsV1.SERVERLESS_AWS_LAMBDA_DEPLOY_V2;
  public static final String SERVERLESS_AWS_LAMBDA_PACKAGE_V2 =
      StepSpecTypeConstantsV1.SERVERLESS_AWS_LAMBDA_PACKAGE_V2;
  public static final String AWS_CDK_BOOTSTRAP = StepSpecTypeConstantsV1.AWS_CDK_BOOTSTRAP;
  public static final String AWS_CDK_SYNTH = StepSpecTypeConstantsV1.AWS_CDK_SYNTH;
  public static final String AWS_CDK_DIFF = StepSpecTypeConstantsV1.AWS_CDK_DIFF;
  public static final String AWS_CDK_DEPLOY = StepSpecTypeConstantsV1.AWS_CDK_DEPLOY;
  public static final String AWS_CDK_DESTROY = StepSpecTypeConstantsV1.AWS_CDK_DESTROY;
  public static final String AWS_CDK_ROLLBACK = StepSpecTypeConstantsV1.AWS_CDK_ROLLBACK;

  private YamlTypesV1() {}
}
