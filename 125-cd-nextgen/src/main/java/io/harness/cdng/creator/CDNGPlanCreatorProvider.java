/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator;

import static io.harness.cdng.manifest.ManifestType.EcsScalingPolicyDefinition;
import static io.harness.cdng.manifest.ManifestType.EcsTaskDefinition;
import static io.harness.cdng.manifest.ManifestType.ServerlessAwsLambda;
import static io.harness.cdng.visitor.YamlTypes.APPLICATION_SETTINGS;
import static io.harness.cdng.visitor.YamlTypes.ARTIFACTS;
import static io.harness.cdng.visitor.YamlTypes.CONFIG_FILE;
import static io.harness.cdng.visitor.YamlTypes.CONFIG_FILES;
import static io.harness.cdng.visitor.YamlTypes.CONNECTION_STRINGS;
import static io.harness.cdng.visitor.YamlTypes.ENVIRONMENT_GROUP_YAML;
import static io.harness.cdng.visitor.YamlTypes.ENVIRONMENT_YAML;
import static io.harness.cdng.visitor.YamlTypes.K8S_MANIFEST;
import static io.harness.cdng.visitor.YamlTypes.MANIFEST_CONFIG;
import static io.harness.cdng.visitor.YamlTypes.MANIFEST_LIST_CONFIG;
import static io.harness.cdng.visitor.YamlTypes.POST_HOOK;
import static io.harness.cdng.visitor.YamlTypes.PRE_HOOK;
import static io.harness.cdng.visitor.YamlTypes.PRIMARY;
import static io.harness.cdng.visitor.YamlTypes.ROLLBACK_STEPS;
import static io.harness.cdng.visitor.YamlTypes.SERVICE_CONFIG;
import static io.harness.cdng.visitor.YamlTypes.SERVICE_DEFINITION;
import static io.harness.cdng.visitor.YamlTypes.SERVICE_ENTITY;
import static io.harness.cdng.visitor.YamlTypes.SERVICE_HOOKS;
import static io.harness.cdng.visitor.YamlTypes.SIDECAR;
import static io.harness.cdng.visitor.YamlTypes.SIDECARS;
import static io.harness.cdng.visitor.YamlTypes.STARTUP_COMMAND;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ACR_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.AMAZON_S3_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.AMI_ARTIFACTS_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ARTIFACTORY_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.AZURE_ARTIFACTS_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.CUSTOM_ARTIFACT_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.DOCKER_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ECR_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.GCR_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.GITHUB_PACKAGES_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.GOOGLE_ARTIFACT_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.JENKINS_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.NEXUS2_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.NEXUS3_REGISTRY_NAME;
import static io.harness.ng.core.k8s.ServiceSpecType.AZURE_WEBAPP;
import static io.harness.ng.core.k8s.ServiceSpecType.CUSTOM_DEPLOYMENT;
import static io.harness.ng.core.k8s.ServiceSpecType.ECS;
import static io.harness.ng.core.k8s.ServiceSpecType.GOOGLE_CLOUD_FUNCTIONS;
import static io.harness.ng.core.k8s.ServiceSpecType.KUBERNETES;
import static io.harness.ng.core.k8s.ServiceSpecType.SERVERLESS_AWS_LAMBDA;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.azure.webapp.variablecreator.AzureWebAppRollbackStepVariableCreator;
import io.harness.cdng.azure.webapp.variablecreator.AzureWebAppSlotDeploymentStepVariableCreator;
import io.harness.cdng.azure.webapp.variablecreator.AzureWebAppSwapSlotStepVariableCreator;
import io.harness.cdng.azure.webapp.variablecreator.AzureWebAppTrafficShiftStepVariableCreator;
import io.harness.cdng.bamboo.BambooBuildStepVariableCreator;
import io.harness.cdng.bamboo.BambooCreateStepPlanCreator;
import io.harness.cdng.chaos.ChaosStepFilterJsonCreator;
import io.harness.cdng.chaos.ChaosStepPlanCreator;
import io.harness.cdng.chaos.ChaosStepVariableCreator;
import io.harness.cdng.creator.filters.DeploymentStageFilterJsonCreatorV2;
import io.harness.cdng.creator.plan.CDStepsPlanCreator;
import io.harness.cdng.creator.plan.artifact.ArtifactsPlanCreator;
import io.harness.cdng.creator.plan.artifact.PrimaryArtifactPlanCreator;
import io.harness.cdng.creator.plan.artifact.SideCarArtifactPlanCreator;
import io.harness.cdng.creator.plan.artifact.SideCarListPlanCreator;
import io.harness.cdng.creator.plan.azure.webapps.ApplicationSettingsPlanCreator;
import io.harness.cdng.creator.plan.azure.webapps.ConnectionStringsPlanCreator;
import io.harness.cdng.creator.plan.azure.webapps.StartupCommandPlanCreator;
import io.harness.cdng.creator.plan.configfile.ConfigFilesPlanCreator;
import io.harness.cdng.creator.plan.configfile.IndividualConfigFilePlanCreator;
import io.harness.cdng.creator.plan.execution.CDExecutionPMSPlanCreator;
import io.harness.cdng.creator.plan.manifest.IndividualManifestPlanCreator;
import io.harness.cdng.creator.plan.manifest.ManifestsPlanCreator;
import io.harness.cdng.creator.plan.rollback.ExecutionStepsRollbackPMSPlanCreator;
import io.harness.cdng.creator.plan.service.ServiceDefinitionPlanCreator;
import io.harness.cdng.creator.plan.service.ServicePlanCreator;
import io.harness.cdng.creator.plan.stage.DeploymentStagePMSPlanCreatorV2;
import io.harness.cdng.creator.plan.steps.AzureARMRollbackResourceStepPlanCreator;
import io.harness.cdng.creator.plan.steps.AzureCreateARMResourceStepPlanCreator;
import io.harness.cdng.creator.plan.steps.AzureCreateBPResourceStepPlanCreator;
import io.harness.cdng.creator.plan.steps.CDPMSCommandStepFilterJsonCreator;
import io.harness.cdng.creator.plan.steps.CDPMSStepFilterJsonCreator;
import io.harness.cdng.creator.plan.steps.CDPMSStepFilterJsonCreatorV2;
import io.harness.cdng.creator.plan.steps.CloudformationCreateStackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.CloudformationDeleteStackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.CloudformationRollbackStackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.CommandStepPlanCreator;
import io.harness.cdng.creator.plan.steps.ElastigroupDeployStepPlanCreator;
import io.harness.cdng.creator.plan.steps.ElastigroupRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.FetchInstanceScriptStepPlanCreator;
import io.harness.cdng.creator.plan.steps.GitOpsCreatePRStepPlanCreatorV2;
import io.harness.cdng.creator.plan.steps.GitOpsFetchLinkedAppsStepPlanCreatorV2;
import io.harness.cdng.creator.plan.steps.GitOpsMergePRStepPlanCreatorV2;
import io.harness.cdng.creator.plan.steps.GitOpsSyncStepPlanCreator;
import io.harness.cdng.creator.plan.steps.GitOpsUpdateReleaseRepoStepPlanCreator;
import io.harness.cdng.creator.plan.steps.HelmDeployStepPlanCreatorV2;
import io.harness.cdng.creator.plan.steps.HelmRollbackStepPlanCreatorV2;
import io.harness.cdng.creator.plan.steps.K8sApplyStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sBGStageScaleDownStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sBGSwapServicesStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sBlueGreenStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sCanaryDeleteStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sCanaryStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sDeleteStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sDryRunManifestStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sRollingRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sRollingStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sScaleStepPlanCreator;
import io.harness.cdng.creator.plan.steps.ShellScriptProvisionStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TasAppResizeStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TasBGAppSetupStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TasBasicAppSetupStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TasCanaryAppSetupStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TasCommandStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TasRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TasRollingDeployStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TasRollingRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TasRouteMappingStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TasSwapRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TasSwapRoutesStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TerraformApplyStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TerraformDestroyStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TerraformPlanStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TerraformRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.aws.asg.AsgBlueGreenDeployStepPlanCreator;
import io.harness.cdng.creator.plan.steps.aws.asg.AsgBlueGreenRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.aws.asg.AsgBlueGreenSwapServiceStepPlanCreator;
import io.harness.cdng.creator.plan.steps.aws.asg.AsgCanaryDeleteStepPlanCreator;
import io.harness.cdng.creator.plan.steps.aws.asg.AsgCanaryDeployStepPlanCreator;
import io.harness.cdng.creator.plan.steps.aws.asg.AsgRollingDeployStepPlanCreator;
import io.harness.cdng.creator.plan.steps.aws.asg.AsgRollingRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.aws.lambda.AwsLambdaDeployStepPlanCreator;
import io.harness.cdng.creator.plan.steps.aws.lambda.AwsLambdaRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.aws.sam.AwsSamBuildStepPlanCreator;
import io.harness.cdng.creator.plan.steps.aws.sam.AwsSamDeployStepPlanCreator;
import io.harness.cdng.creator.plan.steps.aws.sam.AwsSamRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.azure.webapp.AzureWebAppRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.azure.webapp.AzureWebAppSlotDeploymentStepPlanCreator;
import io.harness.cdng.creator.plan.steps.azure.webapp.AzureWebAppSlotSwapSlotPlanCreator;
import io.harness.cdng.creator.plan.steps.azure.webapp.AzureWebAppTrafficShiftStepPlanCreator;
import io.harness.cdng.creator.plan.steps.ecs.EcsBlueGreenCreateServiceStepPlanCreator;
import io.harness.cdng.creator.plan.steps.ecs.EcsBlueGreenRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.ecs.EcsBlueGreenSwapTargetGroupsStepPlanCreator;
import io.harness.cdng.creator.plan.steps.ecs.EcsCanaryDeleteStepPlanCreator;
import io.harness.cdng.creator.plan.steps.ecs.EcsCanaryDeployStepPlanCreator;
import io.harness.cdng.creator.plan.steps.ecs.EcsRollingDeployStepPlanCreator;
import io.harness.cdng.creator.plan.steps.ecs.EcsRollingRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.ecs.EcsRunTaskStepPlanCreator;
import io.harness.cdng.creator.plan.steps.elastigroup.ElastigroupBGStageSetupStepPlanCreator;
import io.harness.cdng.creator.plan.steps.elastigroup.ElastigroupSetupStepPlanCreator;
import io.harness.cdng.creator.plan.steps.elastigroup.ElastigroupSwapRouteStepPlanCreator;
import io.harness.cdng.creator.plan.steps.googlefunctions.GoogleFunctionsDeployStepPlanCreator;
import io.harness.cdng.creator.plan.steps.googlefunctions.GoogleFunctionsDeployWithoutTrafficStepPlanCreator;
import io.harness.cdng.creator.plan.steps.googlefunctions.GoogleFunctionsGenOneDeployStepPlanCreator;
import io.harness.cdng.creator.plan.steps.googlefunctions.GoogleFunctionsGenOneRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.googlefunctions.GoogleFunctionsRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.googlefunctions.GoogleFunctionsTrafficShiftStepPlanCreator;
import io.harness.cdng.creator.plan.steps.serverless.ServerlessAwsLambdaDeployStepPlanCreator;
import io.harness.cdng.creator.plan.steps.serverless.ServerlessAwsLambdaRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.terraformcloud.TerraformCloudRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.terraformcloud.TerraformCloudRunStepPlanCreator;
import io.harness.cdng.creator.plan.steps.terragrunt.TerragruntApplyStepPlanCreator;
import io.harness.cdng.creator.plan.steps.terragrunt.TerragruntDestroyStepPlanCreator;
import io.harness.cdng.creator.plan.steps.terragrunt.TerragruntPlanStepPlanCreator;
import io.harness.cdng.creator.plan.steps.terragrunt.TerragruntRollbackStepPlanCreator;
import io.harness.cdng.creator.variables.AsgBlueGreenDeployStepVariableCreator;
import io.harness.cdng.creator.variables.AsgBlueGreenRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.AsgBlueGreenSwapServiceStepVariableCreator;
import io.harness.cdng.creator.variables.AsgCanaryDeleteStepVariableCreator;
import io.harness.cdng.creator.variables.AsgCanaryDeployStepVariableCreator;
import io.harness.cdng.creator.variables.AsgRollingDeployStepVariableCreator;
import io.harness.cdng.creator.variables.AsgRollingRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.CommandStepVariableCreator;
import io.harness.cdng.creator.variables.DeploymentStageVariableCreator;
import io.harness.cdng.creator.variables.EcsBlueGreenCreateServiceStepVariableCreator;
import io.harness.cdng.creator.variables.EcsBlueGreenRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.EcsBlueGreenSwapTargetGroupsStepVariableCreator;
import io.harness.cdng.creator.variables.EcsCanaryDeleteStepVariableCreator;
import io.harness.cdng.creator.variables.EcsCanaryDeployStepVariableCreator;
import io.harness.cdng.creator.variables.EcsRollingDeployStepVariableCreator;
import io.harness.cdng.creator.variables.EcsRollingRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.EcsRunTaskStepVariableCreator;
import io.harness.cdng.creator.variables.ElastigroupBGStageSetupStepVariableCreator;
import io.harness.cdng.creator.variables.ElastigroupDeployStepVariableCreator;
import io.harness.cdng.creator.variables.ElastigroupRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.ElastigroupSetupStepVariableCreator;
import io.harness.cdng.creator.variables.ElastigroupSwapRouteStepVariableCreator;
import io.harness.cdng.creator.variables.GitOpsCreatePRStepVariableCreator;
import io.harness.cdng.creator.variables.GitOpsFetchLinkedAppsStepVariableCreator;
import io.harness.cdng.creator.variables.GitOpsMergePRStepVariableCreator;
import io.harness.cdng.creator.variables.GitOpsSyncStepVariableCreator;
import io.harness.cdng.creator.variables.GitOpsUpdateReleaseRepoStepVariableCreator;
import io.harness.cdng.creator.variables.HelmDeployStepVariableCreator;
import io.harness.cdng.creator.variables.HelmRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.K8sApplyStepVariableCreator;
import io.harness.cdng.creator.variables.K8sBGStageScaleDownStepVariableCreator;
import io.harness.cdng.creator.variables.K8sBGSwapServicesVariableCreator;
import io.harness.cdng.creator.variables.K8sBlueGreenStepVariableCreator;
import io.harness.cdng.creator.variables.K8sCanaryDeleteStepVariableCreator;
import io.harness.cdng.creator.variables.K8sCanaryStepVariableCreator;
import io.harness.cdng.creator.variables.K8sDeleteStepVariableCreator;
import io.harness.cdng.creator.variables.K8sDryRunManifestStepVariableCreator;
import io.harness.cdng.creator.variables.K8sRollingRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.K8sRollingStepVariableCreator;
import io.harness.cdng.creator.variables.K8sScaleStepVariableCreator;
import io.harness.cdng.creator.variables.ServerlessAwsLambdaDeployStepVariableCreator;
import io.harness.cdng.creator.variables.ServerlessAwsLambdaRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.StepGroupVariableCreator;
import io.harness.cdng.creator.variables.TasAppResizeStepVariableCreator;
import io.harness.cdng.creator.variables.TasBGAppSetupStepVariableCreator;
import io.harness.cdng.creator.variables.TasBasicAppSetupStepVariableCreator;
import io.harness.cdng.creator.variables.TasCanaryAppSetupStepVariableCreator;
import io.harness.cdng.creator.variables.TasCommandStepVariableCreator;
import io.harness.cdng.creator.variables.TasRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.TasRollingDeployStepVariableCreator;
import io.harness.cdng.creator.variables.TasRollingRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.TasRouteMappingStepVariableCreator;
import io.harness.cdng.creator.variables.TasSwapRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.TasSwapRoutesStepVariableCreator;
import io.harness.cdng.creator.variables.aws.AwsLambdaDeployStepVariableCreator;
import io.harness.cdng.creator.variables.aws.AwsLambdaRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.aws.sam.AwsSamBuildStepVariableCreator;
import io.harness.cdng.creator.variables.aws.sam.AwsSamDeployStepVariableCreator;
import io.harness.cdng.creator.variables.aws.sam.AwsSamRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.googlefunctions.GoogleFunctionsDeployStepVariableCreator;
import io.harness.cdng.creator.variables.googlefunctions.GoogleFunctionsDeployWithoutTrafficStepVariableCreator;
import io.harness.cdng.creator.variables.googlefunctions.GoogleFunctionsGenOneDeployStepVariableCreator;
import io.harness.cdng.creator.variables.googlefunctions.GoogleFunctionsGenOneRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.googlefunctions.GoogleFunctionsRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.googlefunctions.GoogleFunctionsTrafficShiftStepVariableCreator;
import io.harness.cdng.customDeployment.constants.CustomDeploymentConstants;
import io.harness.cdng.customDeployment.variablecreator.FetchInstanceScriptStepVariableCreator;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildStepVariableCreator;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsCreateStepPlanCreator;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.provision.azure.variablecreator.AzureARMRollbackStepVariableCreator;
import io.harness.cdng.provision.azure.variablecreator.AzureCreateARMResourceStepVariableCreator;
import io.harness.cdng.provision.azure.variablecreator.AzureCreateBPStepVariableCreator;
import io.harness.cdng.provision.cloudformation.variablecreator.CloudformationCreateStepVariableCreator;
import io.harness.cdng.provision.cloudformation.variablecreator.CloudformationDeleteStepVariableCreator;
import io.harness.cdng.provision.cloudformation.variablecreator.CloudformationRollbackStepVariableCreator;
import io.harness.cdng.provision.shellscript.ShellScriptProvisionStepVariableCreator;
import io.harness.cdng.provision.terraform.variablecreator.TerraformApplyStepVariableCreator;
import io.harness.cdng.provision.terraform.variablecreator.TerraformDestroyStepVariableCreator;
import io.harness.cdng.provision.terraform.variablecreator.TerraformPlanStepVariableCreator;
import io.harness.cdng.provision.terraform.variablecreator.TerraformRollbackStepVariableCreator;
import io.harness.cdng.provision.terraformcloud.variablecreator.TerraformCloudRollbackStepVariableCreator;
import io.harness.cdng.provision.terraformcloud.variablecreator.TerraformCloudRunStepVariableCreator;
import io.harness.cdng.provision.terragrunt.variablecreator.TerragruntApplyStepVariableCreator;
import io.harness.cdng.provision.terragrunt.variablecreator.TerragruntDestroyStepVariableCreator;
import io.harness.cdng.provision.terragrunt.variablecreator.TerragruntPlanStepVariableCreator;
import io.harness.cdng.provision.terragrunt.variablecreator.TerragruntRollbackStepVariableCreator;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.delegate.beans.DelegateType;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.filters.EmptyAnyFilterJsonCreator;
import io.harness.filters.EmptyFilterJsonCreator;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.plancreator.steps.StepGroupPMSPlanCreatorV2;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.variables.EmptyAnyVariableCreator;
import io.harness.pms.sdk.core.variables.EmptyVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreator;
import io.harness.pms.utils.InjectorUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public class CDNGPlanCreatorProvider implements PipelineServiceInfoProvider {
  private static final String TERRAFORM_STEP_METADATA = "Terraform";
  private static final String TERRAGRUNT_STEP_METADATA = "Terragrunt";
  private static final String TERRAFORM_CLOUD_STEP_METADATA = "Terraform Cloud";

  private static final String CLOUDFORMATION_STEP_METADATA = "Cloudformation";
  private static final String AZURE = "Azure";
  private static final String HELM = "Helm";
  private static final String PROVISIONER = "Provisioner";
  private static final String SHELL_SCRIPT_PROVISIONER_STEM_METADATA = "Shell Script Provisioner";

  private static final String CUSTOM = "Custom Deployment";
  private static final String COMMANDS = "Commands";
  private static final String ELASTIGROUP = "Elastigroup";
  private static final String TAS = "TAS";
  private static final String ASG = "AWS Auto Scaling Group";
  private static final String AWS_LAMBDA = "AwsLambda";

  private static final List<String> CUSTOM_DEPLOYMENT_CATEGORY = Arrays.asList(COMMANDS, CUSTOM_DEPLOYMENT);
  private static final List<String> CLOUDFORMATION_CATEGORY = Arrays.asList(KUBERNETES, PROVISIONER,
      CLOUDFORMATION_STEP_METADATA, HELM, ECS, COMMANDS, SERVERLESS_AWS_LAMBDA, ASG, ServiceSpecType.AWS_LAMBDA);
  private static final List<String> TERRAFORM_CATEGORY = Arrays.asList(KUBERNETES, PROVISIONER, HELM, ECS, COMMANDS,
      SERVERLESS_AWS_LAMBDA, ASG, GOOGLE_CLOUD_FUNCTIONS, ServiceSpecType.AWS_LAMBDA);
  private static final List<String> TERRAGRUNT_CATEGORY = Arrays.asList(KUBERNETES, PROVISIONER, HELM, ECS, COMMANDS,
      SERVERLESS_AWS_LAMBDA, ASG, ServiceSpecType.AWS_LAMBDA, GOOGLE_CLOUD_FUNCTIONS);
  private static final List<String> TERRAFORM_CLOUD_CATEGORY = Arrays.asList(KUBERNETES, PROVISIONER, HELM, ECS,
      COMMANDS, SERVERLESS_AWS_LAMBDA, ASG, GOOGLE_CLOUD_FUNCTIONS, ServiceSpecType.AWS_LAMBDA);
  private static final String BUILD_STEP = "Builds";

  private static final List<String> AZURE_RESOURCE_CATEGORY =
      Arrays.asList(KUBERNETES, PROVISIONER, AZURE, HELM, AZURE_WEBAPP, COMMANDS);
  private static final String AZURE_RESOURCE_STEP_METADATA = "Azure Provisioner";

  private static final List<String> SHELL_SCRIPT_PROVISIONER_CATEGORY = Arrays.asList(KUBERNETES, PROVISIONER, HELM,
      AZURE_WEBAPP, ECS, COMMANDS, ASG, ServiceSpecType.AWS_LAMBDA, GOOGLE_CLOUD_FUNCTIONS);

  private static final List<String> ASG_CATEGORY = Arrays.asList(ASG);

  private static final Set<String> EMPTY_FILTER_IDENTIFIERS = Sets.newHashSet(SIDECARS, SERVICE_CONFIG, CONFIG_FILE,
      STARTUP_COMMAND, APPLICATION_SETTINGS, ARTIFACTS, ROLLBACK_STEPS, CONNECTION_STRINGS, CONFIG_FILES,
      ENVIRONMENT_GROUP_YAML, SERVICE_ENTITY, MANIFEST_LIST_CONFIG, SERVICE_HOOKS, PRE_HOOK, POST_HOOK);
  private static final Set<String> EMPTY_VARIABLE_IDENTIFIERS = Sets.newHashSet(SIDECARS, SERVICE_CONFIG, CONFIG_FILE,
      STARTUP_COMMAND, APPLICATION_SETTINGS, ARTIFACTS, ROLLBACK_STEPS, CONNECTION_STRINGS, CONFIG_FILES,
      ENVIRONMENT_GROUP_YAML, SERVICE_ENTITY, MANIFEST_LIST_CONFIG, SERVICE_HOOKS, PRE_HOOK, POST_HOOK);
  private static final Set<String> EMPTY_SIDECAR_TYPES =
      Sets.newHashSet(CUSTOM_ARTIFACT_NAME, JENKINS_NAME, DOCKER_REGISTRY_NAME, ACR_NAME, AMAZON_S3_NAME,
          ARTIFACTORY_REGISTRY_NAME, ECR_NAME, GOOGLE_ARTIFACT_REGISTRY_NAME, GCR_NAME, NEXUS3_REGISTRY_NAME,
          GITHUB_PACKAGES_NAME, AZURE_ARTIFACTS_NAME, AMI_ARTIFACTS_NAME);
  private static final Set<String> EMPTY_MANIFEST_TYPES = Sets.newHashSet(EcsTaskDefinition, ServerlessAwsLambda,
      EcsScalingPolicyDefinition, K8S_MANIFEST, ManifestType.VALUES, ManifestType.KustomizePatches,
      ManifestType.EcsScalableTargetDefinition, ManifestType.Kustomize, ManifestType.EcsServiceDefinition, CONFIG_FILES,
      ManifestType.HelmChart, ManifestType.ReleaseRepo, ManifestType.DeploymentRepo, ManifestType.OpenshiftTemplate,
      ManifestType.OpenshiftParam, ManifestType.TAS_MANIFEST, ManifestType.TAS_VARS, ManifestType.TAS_AUTOSCALER,
      ManifestType.AsgLaunchTemplate, ManifestType.AsgConfiguration, ManifestType.AsgScalingPolicy,
      ManifestType.AsgScheduledUpdateGroupAction, ManifestType.GoogleCloudFunctionDefinition,
      ManifestType.AwsLambdaFunctionDefinition, ManifestType.AwsLambdaFunctionAliasDefinition,
      ManifestType.AwsSamDirectory, ManifestType.GoogleCloudFunctionGenOneDefinition);
  private static final Set<String> EMPTY_ENVIRONMENT_TYPES =
      Sets.newHashSet(YamlTypes.ENV_PRODUCTION, YamlTypes.ENV_PRE_PRODUCTION);
  private static final Set<String> EMPTY_PRIMARY_TYPES =
      Sets.newHashSet(CUSTOM_ARTIFACT_NAME, JENKINS_NAME, DOCKER_REGISTRY_NAME, ACR_NAME, AMAZON_S3_NAME,
          ARTIFACTORY_REGISTRY_NAME, ECR_NAME, GOOGLE_ARTIFACT_REGISTRY_NAME, GCR_NAME, NEXUS3_REGISTRY_NAME,
          NEXUS2_REGISTRY_NAME, GITHUB_PACKAGES_NAME, AZURE_ARTIFACTS_NAME, AMI_ARTIFACTS_NAME);
  private static final Set<String> EMPTY_SERVICE_DEFINITION_TYPES = Sets.newHashSet(ManifestType.ServerlessAwsLambda,
      DelegateType.ECS, ServiceSpecType.NATIVE_HELM, ServiceSpecType.SSH, AZURE_WEBAPP, ServiceSpecType.WINRM,
      KUBERNETES, CUSTOM_DEPLOYMENT, ServiceSpecType.ELASTIGROUP, ServiceSpecType.TAS, ServiceSpecType.ASG,
      ServiceSpecType.GOOGLE_CLOUD_FUNCTIONS, ServiceSpecType.AWS_LAMBDA, ServiceSpecType.AWS_SAM);

  @Inject InjectorUtils injectorUtils;
  @Inject DeploymentStageVariableCreator deploymentStageVariableCreator;

  @Override
  public List<PartialPlanCreator<?>> getPlanCreators() {
    List<PartialPlanCreator<?>> planCreators = new LinkedList<>();
    planCreators.add(new GitOpsCreatePRStepPlanCreatorV2());
    planCreators.add(new GitOpsMergePRStepPlanCreatorV2());
    planCreators.add(new GitOpsUpdateReleaseRepoStepPlanCreator());
    planCreators.add(new GitOpsFetchLinkedAppsStepPlanCreatorV2());
    planCreators.add(new GitOpsSyncStepPlanCreator());
    planCreators.add(new DeploymentStagePMSPlanCreatorV2());
    planCreators.add(new K8sCanaryStepPlanCreator());
    planCreators.add(new K8sApplyStepPlanCreator());
    planCreators.add(new K8sBlueGreenStepPlanCreator());
    planCreators.add(new K8sRollingStepPlanCreator());
    planCreators.add(new K8sRollingRollbackStepPlanCreator());
    planCreators.add(new K8sScaleStepPlanCreator());
    planCreators.add(new K8sDeleteStepPlanCreator());
    planCreators.add(new K8sBGSwapServicesStepPlanCreator());
    planCreators.add(new K8sCanaryDeleteStepPlanCreator());
    planCreators.add(new TerraformApplyStepPlanCreator());
    planCreators.add(new FetchInstanceScriptStepPlanCreator());
    planCreators.add(new TerraformPlanStepPlanCreator());
    planCreators.add(new TerraformDestroyStepPlanCreator());
    planCreators.add(new TerraformRollbackStepPlanCreator());
    planCreators.add(new HelmDeployStepPlanCreatorV2());
    planCreators.add(new HelmRollbackStepPlanCreatorV2());
    planCreators.add(new CDExecutionPMSPlanCreator());
    planCreators.add(new ExecutionStepsRollbackPMSPlanCreator());
    planCreators.add(new ServicePlanCreator());
    planCreators.add(new ServiceDefinitionPlanCreator());
    planCreators.add(new ArtifactsPlanCreator());
    planCreators.add(new PrimaryArtifactPlanCreator());
    planCreators.add(new SideCarListPlanCreator());
    planCreators.add(new SideCarArtifactPlanCreator());
    planCreators.add(new ManifestsPlanCreator());
    planCreators.add(new IndividualManifestPlanCreator());
    planCreators.add(new CDStepsPlanCreator());
    planCreators.add(new StepGroupPMSPlanCreatorV2());
    planCreators.add(new ServerlessAwsLambdaDeployStepPlanCreator());
    planCreators.add(new ServerlessAwsLambdaRollbackStepPlanCreator());
    planCreators.add(new CloudformationCreateStackStepPlanCreator());
    planCreators.add(new CloudformationDeleteStackStepPlanCreator());
    planCreators.add(new CloudformationRollbackStackStepPlanCreator());
    planCreators.add(new IndividualConfigFilePlanCreator());
    planCreators.add(new ConfigFilesPlanCreator());
    planCreators.add(new CommandStepPlanCreator());
    planCreators.add(new AzureWebAppRollbackStepPlanCreator());
    planCreators.add(new AzureWebAppSlotDeploymentStepPlanCreator());
    planCreators.add(new AzureWebAppSlotSwapSlotPlanCreator());
    planCreators.add(new AzureWebAppTrafficShiftStepPlanCreator());
    planCreators.add(new JenkinsCreateStepPlanCreator());
    planCreators.add(new StartupCommandPlanCreator());
    planCreators.add(new ApplicationSettingsPlanCreator());
    planCreators.add(new ConnectionStringsPlanCreator());
    // ECS
    planCreators.add(new EcsRollingDeployStepPlanCreator());
    planCreators.add(new EcsRollingRollbackStepPlanCreator());
    planCreators.add(new EcsCanaryDeployStepPlanCreator());
    planCreators.add(new EcsCanaryDeleteStepPlanCreator());
    planCreators.add(new EcsBlueGreenCreateServiceStepPlanCreator());
    planCreators.add(new EcsBlueGreenSwapTargetGroupsStepPlanCreator());
    planCreators.add(new EcsBlueGreenRollbackStepPlanCreator());
    planCreators.add(new EcsRunTaskStepPlanCreator());

    planCreators.add(new AzureCreateARMResourceStepPlanCreator());
    planCreators.add(new AzureCreateBPResourceStepPlanCreator());

    planCreators.add(new AzureARMRollbackResourceStepPlanCreator());
    planCreators.add(new ShellScriptProvisionStepPlanCreator());

    // CHaos
    planCreators.add(new ChaosStepPlanCreator());

    // Elastigroup
    planCreators.add(new ElastigroupDeployStepPlanCreator());
    planCreators.add(new ElastigroupRollbackStepPlanCreator());
    planCreators.add(new ElastigroupSetupStepPlanCreator());
    planCreators.add(new ElastigroupBGStageSetupStepPlanCreator());
    planCreators.add(new ElastigroupSwapRouteStepPlanCreator());

    // Terragrunt
    planCreators.add(new TerragruntPlanStepPlanCreator());
    planCreators.add(new TerragruntApplyStepPlanCreator());
    planCreators.add(new TerragruntDestroyStepPlanCreator());
    planCreators.add(new TerragruntRollbackStepPlanCreator());

    // Asg
    planCreators.add(new AsgCanaryDeployStepPlanCreator());
    planCreators.add(new AsgCanaryDeleteStepPlanCreator());
    planCreators.add(new AsgRollingDeployStepPlanCreator());
    planCreators.add(new AsgRollingRollbackStepPlanCreator());
    planCreators.add(new AsgBlueGreenSwapServiceStepPlanCreator());
    planCreators.add(new AsgBlueGreenDeployStepPlanCreator());
    planCreators.add(new AsgBlueGreenRollbackStepPlanCreator());

    // TAS
    planCreators.add(new TasCanaryAppSetupStepPlanCreator());
    planCreators.add(new TasBGAppSetupStepPlanCreator());
    planCreators.add(new TasBasicAppSetupStepPlanCreator());
    planCreators.add(new TasCommandStepPlanCreator());
    planCreators.add(new TasSwapRoutesStepPlanCreator());
    planCreators.add(new TasSwapRollbackStepPlanCreator());
    planCreators.add(new TasAppResizeStepPlanCreator());
    planCreators.add(new TasRollbackStepPlanCreator());
    planCreators.add(new TasRollingDeployStepPlanCreator());
    planCreators.add(new TasRollingRollbackStepPlanCreator());
    planCreators.add(new TasRouteMappingStepPlanCreator());

    planCreators.add(new K8sDryRunManifestStepPlanCreator());

    planCreators.add(new GoogleFunctionsDeployStepPlanCreator());
    planCreators.add(new GoogleFunctionsDeployWithoutTrafficStepPlanCreator());
    planCreators.add(new GoogleFunctionsTrafficShiftStepPlanCreator());
    planCreators.add(new GoogleFunctionsRollbackStepPlanCreator());
    planCreators.add(new GoogleFunctionsGenOneDeployStepPlanCreator());
    planCreators.add(new GoogleFunctionsGenOneRollbackStepPlanCreator());

    // Terraform Cloud
    planCreators.add(new TerraformCloudRunStepPlanCreator());
    planCreators.add(new BambooCreateStepPlanCreator());
    planCreators.add(new TerraformCloudRollbackStepPlanCreator());

    // AWS Lambda
    planCreators.add(new AwsLambdaDeployStepPlanCreator());
    planCreators.add(new AwsLambdaRollbackStepPlanCreator());

    // AWS SAM
    planCreators.add(new AwsSamDeployStepPlanCreator());
    planCreators.add(new AwsSamBuildStepPlanCreator());
    planCreators.add(new AwsSamRollbackStepPlanCreator());

    planCreators.add(new K8sBGStageScaleDownStepPlanCreator());

    injectorUtils.injectMembers(planCreators);
    return planCreators;
  }

  @Override
  public List<FilterJsonCreator> getFilterJsonCreators() {
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();
    filterJsonCreators.add(new DeploymentStageFilterJsonCreatorV2());
    filterJsonCreators.add(new CDPMSStepFilterJsonCreator());
    filterJsonCreators.add(new CDPMSStepFilterJsonCreatorV2());
    filterJsonCreators.add(new CDPMSCommandStepFilterJsonCreator());
    filterJsonCreators.add(new ChaosStepFilterJsonCreator());
    filterJsonCreators.add(new EmptyAnyFilterJsonCreator(EMPTY_FILTER_IDENTIFIERS));
    filterJsonCreators.add(new EmptyFilterJsonCreator(SIDECAR, EMPTY_SIDECAR_TYPES));
    filterJsonCreators.add(new EmptyFilterJsonCreator(MANIFEST_CONFIG, EMPTY_MANIFEST_TYPES));
    filterJsonCreators.add(new EmptyFilterJsonCreator(ENVIRONMENT_YAML, EMPTY_ENVIRONMENT_TYPES));
    filterJsonCreators.add(new EmptyFilterJsonCreator(PRIMARY, EMPTY_PRIMARY_TYPES));
    filterJsonCreators.add(new EmptyFilterJsonCreator(SERVICE_DEFINITION, EMPTY_SERVICE_DEFINITION_TYPES));

    injectorUtils.injectMembers(filterJsonCreators);

    return filterJsonCreators;
  }

  @Override
  public List<VariableCreator> getVariableCreators() {
    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(new EmptyAnyVariableCreator(new HashSet<>(EMPTY_VARIABLE_IDENTIFIERS)));
    variableCreators.add(new EmptyVariableCreator(SIDECAR, EMPTY_SIDECAR_TYPES));
    variableCreators.add(new EmptyVariableCreator(MANIFEST_CONFIG, EMPTY_MANIFEST_TYPES));
    variableCreators.add(new EmptyVariableCreator(ENVIRONMENT_YAML, EMPTY_ENVIRONMENT_TYPES));
    variableCreators.add(new EmptyVariableCreator(PRIMARY, EMPTY_PRIMARY_TYPES));
    variableCreators.add(new EmptyVariableCreator(SERVICE_DEFINITION, EMPTY_SERVICE_DEFINITION_TYPES));
    variableCreators.add(new GitOpsCreatePRStepVariableCreator());
    variableCreators.add(new GitOpsMergePRStepVariableCreator());
    variableCreators.add(new GitOpsUpdateReleaseRepoStepVariableCreator());
    variableCreators.add(new GitOpsFetchLinkedAppsStepVariableCreator());
    variableCreators.add(new GitOpsSyncStepVariableCreator());
    variableCreators.add(deploymentStageVariableCreator);
    variableCreators.add(new StepGroupVariableCreator());
    variableCreators.add(new K8sApplyStepVariableCreator());
    variableCreators.add(new K8sBGSwapServicesVariableCreator());
    variableCreators.add(new K8sBlueGreenStepVariableCreator());
    variableCreators.add(new K8sCanaryDeleteStepVariableCreator());
    variableCreators.add(new K8sCanaryStepVariableCreator());
    variableCreators.add(new K8sDeleteStepVariableCreator());
    variableCreators.add(new K8sRollingRollbackStepVariableCreator());
    variableCreators.add(new K8sRollingStepVariableCreator());
    variableCreators.add(new K8sScaleStepVariableCreator());
    variableCreators.add(new TerraformApplyStepVariableCreator());
    variableCreators.add(new TerraformPlanStepVariableCreator());
    variableCreators.add(new TerraformDestroyStepVariableCreator());
    variableCreators.add(new TerraformRollbackStepVariableCreator());
    variableCreators.add(new HelmDeployStepVariableCreator());
    variableCreators.add(new HelmRollbackStepVariableCreator());
    variableCreators.add(new ServerlessAwsLambdaDeployStepVariableCreator());
    variableCreators.add(new ServerlessAwsLambdaRollbackStepVariableCreator());
    variableCreators.add(new CloudformationCreateStepVariableCreator());
    variableCreators.add(new CloudformationDeleteStepVariableCreator());
    variableCreators.add(new CloudformationRollbackStepVariableCreator());
    variableCreators.add(new CommandStepVariableCreator());
    variableCreators.add(new AzureWebAppSlotDeploymentStepVariableCreator());
    variableCreators.add(new AzureWebAppTrafficShiftStepVariableCreator());
    variableCreators.add(new AzureWebAppSwapSlotStepVariableCreator());
    variableCreators.add(new AzureWebAppRollbackStepVariableCreator());
    variableCreators.add(new FetchInstanceScriptStepVariableCreator());
    variableCreators.add(new JenkinsBuildStepVariableCreator());
    variableCreators.add(new BambooBuildStepVariableCreator());
    // ECS
    variableCreators.add(new EcsRollingDeployStepVariableCreator());
    variableCreators.add(new EcsRollingRollbackStepVariableCreator());
    variableCreators.add(new EcsCanaryDeployStepVariableCreator());
    variableCreators.add(new EcsCanaryDeleteStepVariableCreator());
    variableCreators.add(new EcsBlueGreenCreateServiceStepVariableCreator());
    variableCreators.add(new EcsBlueGreenSwapTargetGroupsStepVariableCreator());
    variableCreators.add(new EcsBlueGreenRollbackStepVariableCreator());
    variableCreators.add(new EcsRunTaskStepVariableCreator());

    variableCreators.add(new AzureCreateARMResourceStepVariableCreator());
    variableCreators.add(new AzureCreateBPStepVariableCreator());
    variableCreators.add(new AzureARMRollbackStepVariableCreator());
    variableCreators.add(new ShellScriptProvisionStepVariableCreator());
    variableCreators.add(new ChaosStepVariableCreator());

    // Elastigroup
    variableCreators.add(new ElastigroupDeployStepVariableCreator());
    variableCreators.add(new ElastigroupRollbackStepVariableCreator());
    variableCreators.add(new ElastigroupSetupStepVariableCreator());
    variableCreators.add(new ElastigroupBGStageSetupStepVariableCreator());
    variableCreators.add(new ElastigroupSwapRouteStepVariableCreator());

    // Terragrunt
    variableCreators.add(new TerragruntPlanStepVariableCreator());
    variableCreators.add(new TerragruntApplyStepVariableCreator());
    variableCreators.add(new TerragruntDestroyStepVariableCreator());
    variableCreators.add(new TerragruntRollbackStepVariableCreator());

    // Asg
    variableCreators.add(new AsgCanaryDeployStepVariableCreator());
    variableCreators.add(new AsgCanaryDeleteStepVariableCreator());
    variableCreators.add(new AsgRollingDeployStepVariableCreator());
    variableCreators.add(new AsgRollingRollbackStepVariableCreator());
    variableCreators.add(new AsgBlueGreenSwapServiceStepVariableCreator());
    variableCreators.add(new AsgBlueGreenDeployStepVariableCreator());
    variableCreators.add(new AsgBlueGreenRollbackStepVariableCreator());

    // TAS
    variableCreators.add(new TasCanaryAppSetupStepVariableCreator());
    variableCreators.add(new TasBGAppSetupStepVariableCreator());
    variableCreators.add(new TasBasicAppSetupStepVariableCreator());
    variableCreators.add(new TasCommandStepVariableCreator());
    variableCreators.add(new TasSwapRoutesStepVariableCreator());
    variableCreators.add(new TasSwapRollbackStepVariableCreator());
    variableCreators.add(new TasAppResizeStepVariableCreator());
    variableCreators.add(new TasRollbackStepVariableCreator());
    variableCreators.add(new TasRollingDeployStepVariableCreator());
    variableCreators.add(new TasRollingRollbackStepVariableCreator());
    variableCreators.add(new TasRouteMappingStepVariableCreator());

    variableCreators.add(new K8sDryRunManifestStepVariableCreator());

    variableCreators.add(new GoogleFunctionsDeployStepVariableCreator());
    variableCreators.add(new GoogleFunctionsDeployWithoutTrafficStepVariableCreator());
    variableCreators.add(new GoogleFunctionsTrafficShiftStepVariableCreator());
    variableCreators.add(new GoogleFunctionsRollbackStepVariableCreator());
    variableCreators.add(new GoogleFunctionsGenOneDeployStepVariableCreator());
    variableCreators.add(new GoogleFunctionsGenOneRollbackStepVariableCreator());
    // Terraform Cloud
    variableCreators.add(new TerraformCloudRunStepVariableCreator());
    variableCreators.add(new TerraformCloudRollbackStepVariableCreator());

    // AWS Lambda
    variableCreators.add(new AwsLambdaDeployStepVariableCreator());
    variableCreators.add(new AwsLambdaRollbackStepVariableCreator());

    // AWS SAM
    variableCreators.add(new AwsSamDeployStepVariableCreator());
    variableCreators.add(new AwsSamBuildStepVariableCreator());
    variableCreators.add(new AwsSamRollbackStepVariableCreator());

    variableCreators.add(new K8sBGStageScaleDownStepVariableCreator());

    return variableCreators;
  }

  @Override
  public List<StepInfo> getStepInfo() {
    StepInfo gitOpsCreatePR =
        StepInfo.newBuilder()
            .setName("GitOps Create PR")
            .setType(StepSpecTypeConstants.GITOPS_CREATE_PR)
            .setFeatureFlag(FeatureName.CDS_SHOW_CREATE_PR.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").setFolderPath("GitOps").build())
            .build();

    StepInfo gitOpsMergePR =
        StepInfo.newBuilder()
            .setName("GitOps Merge PR")
            .setType(StepSpecTypeConstants.GITOPS_MERGE_PR)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").setFolderPath("GitOps").build())
            .build();

    StepInfo gitOpsSync =
        StepInfo.newBuilder()
            .setName("GitOps Sync")
            .setType(StepSpecTypeConstants.GITOPS_SYNC)
            .setFeatureFlag(FeatureName.GITOPS_SYNC_STEP.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").setFolderPath("GitOps").build())
            .build();

    StepInfo updateReleaseRepo =
        StepInfo.newBuilder()
            .setName("GitOps Update Release Repo")
            .setType(StepSpecTypeConstants.GITOPS_UPDATE_RELEASE_REPO)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").setFolderPath("GitOps").build())
            .build();

    StepInfo fetchLinkedApps =
        StepInfo.newBuilder()
            .setName("GitOps Fetch Linked Apps")
            .setType(StepSpecTypeConstants.GITOPS_FETCH_LINKED_APPS)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").setFolderPath("GitOps").build())
            .build();

    StepInfo k8sRolling =
        StepInfo.newBuilder()
            .setName("Rolling Deployment")
            .setType(StepSpecTypeConstants.K8S_ROLLING_DEPLOY)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_ROLLING_DEPLOY.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .build();

    StepInfo canaryDeploy =
        StepInfo.newBuilder()
            .setName("Canary Deployment")
            .setType(StepSpecTypeConstants.K8S_CANARY_DEPLOY)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_CANARY_DEPLOY.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .build();
    StepInfo canaryDelete =
        StepInfo.newBuilder()
            .setName("Canary Delete")
            .setType(StepSpecTypeConstants.K8S_CANARY_DELETE)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_CANARY_DELETE.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .build();
    StepInfo delete = StepInfo.newBuilder()
                          .setName("Delete")
                          .setType(StepSpecTypeConstants.K8S_DELETE)
                          .setFeatureRestrictionName(FeatureRestrictionName.K8S_DELETE.name())
                          .setStepMetaData(StepMetaData.newBuilder()
                                               .addCategory("Kubernetes")
                                               .addCategory("Helm")
                                               .addFolderPaths("Kubernetes")
                                               .build())
                          .build();

    StepInfo stageDeployment =
        StepInfo.newBuilder()
            .setName("Stage Deployment")
            .setType(StepSpecTypeConstants.K8S_BLUE_GREEN_DEPLOY)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_BLUE_GREEN_DEPLOY.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .build();
    StepInfo bgSwapServices =
        StepInfo.newBuilder()
            .setName("BG Swap Services")
            .setType(StepSpecTypeConstants.K8S_BG_SWAP_SERVICES)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_BG_SWAP_SERVICES.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .build();

    StepInfo apply = StepInfo.newBuilder()
                         .setName("Apply")
                         .setType(StepSpecTypeConstants.K8S_APPLY)
                         .setFeatureRestrictionName(FeatureRestrictionName.K8S_APPLY.name())
                         .setStepMetaData(StepMetaData.newBuilder()
                                              .addCategory("Kubernetes")
                                              .addCategory("Helm")
                                              .addFolderPaths("Kubernetes")
                                              .build())
                         .build();
    StepInfo scale = StepInfo.newBuilder()
                         .setName("Scale")
                         .setType(StepSpecTypeConstants.K8S_SCALE)
                         .setFeatureRestrictionName(FeatureRestrictionName.K8S_SCALE.name())
                         .setStepMetaData(StepMetaData.newBuilder()
                                              .addCategory("Kubernetes")
                                              .addCategory("Helm")
                                              .addFolderPaths("Kubernetes")
                                              .build())
                         .build();

    StepInfo k8sRollingRollback =
        StepInfo.newBuilder()
            .setName("Rolling Rollback")
            .setType(StepSpecTypeConstants.K8S_ROLLING_ROLLBACK)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_ROLLING_ROLLBACK.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .build();

    StepInfo terraformApply = StepInfo.newBuilder()
                                  .setName("Terraform Apply")
                                  .setType(StepSpecTypeConstants.TERRAFORM_APPLY)
                                  .setFeatureRestrictionName(FeatureRestrictionName.TERRAFORM_APPLY.name())
                                  .setStepMetaData(StepMetaData.newBuilder()
                                                       .addAllCategory(TERRAFORM_CATEGORY)
                                                       .addFolderPaths(TERRAFORM_STEP_METADATA)
                                                       .build())
                                  .build();
    StepInfo terraformPlan = StepInfo.newBuilder()
                                 .setName("Terraform Plan")
                                 .setType(StepSpecTypeConstants.TERRAFORM_PLAN)
                                 .setFeatureRestrictionName(FeatureRestrictionName.TERRAFORM_PLAN.name())
                                 .setStepMetaData(StepMetaData.newBuilder()
                                                      .addAllCategory(TERRAFORM_CATEGORY)
                                                      .addFolderPaths(TERRAFORM_STEP_METADATA)
                                                      .build())
                                 .build();
    StepInfo terraformDestroy = StepInfo.newBuilder()
                                    .setName("Terraform Destroy")
                                    .setType(StepSpecTypeConstants.TERRAFORM_DESTROY)
                                    .setFeatureRestrictionName(FeatureRestrictionName.TERRAFORM_DESTROY.name())
                                    .setStepMetaData(StepMetaData.newBuilder()
                                                         .addAllCategory(TERRAFORM_CATEGORY)
                                                         .addFolderPaths(TERRAFORM_STEP_METADATA)
                                                         .build())
                                    .build();
    StepInfo terraformRollback = StepInfo.newBuilder()
                                     .setName("Terraform Rollback")
                                     .setType(StepSpecTypeConstants.TERRAFORM_ROLLBACK)
                                     .setFeatureRestrictionName(FeatureRestrictionName.TERRAFORM_ROLLBACK.name())
                                     .setStepMetaData(StepMetaData.newBuilder()
                                                          .addAllCategory(TERRAFORM_CATEGORY)
                                                          .addFolderPaths(TERRAFORM_STEP_METADATA)
                                                          .build())
                                     .build();

    StepInfo helmDeploy =
        StepInfo.newBuilder()
            .setName("Helm Deploy")
            .setType(StepSpecTypeConstants.HELM_DEPLOY)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Helm").setFolderPath("Helm").build())
            .build();

    StepInfo helmRollback =
        StepInfo.newBuilder()
            .setName("Helm Rollback")
            .setType(StepSpecTypeConstants.HELM_ROLLBACK)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Helm").setFolderPath("Helm").build())
            .build();

    StepInfo executeCommand =
        StepInfo.newBuilder()
            .setName("Command")
            .setType(StepSpecTypeConstants.COMMAND)
            .setFeatureRestrictionName(FeatureRestrictionName.COMMAND.name())
            .setStepMetaData(
                StepMetaData.newBuilder().addAllCategory(CUSTOM_DEPLOYMENT_CATEGORY).addFolderPaths(COMMANDS).build())
            .build();

    StepInfo serverlessDeploy =
        StepInfo.newBuilder()
            .setName("Serverless Lambda Deploy")
            .setType(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_DEPLOY)
            .setStepMetaData(
                StepMetaData.newBuilder().addCategory("ServerlessAwsLambda").setFolderPath("Serverless Lambda").build())
            .build();

    StepInfo serverlessRollback =
        StepInfo.newBuilder()
            .setName("Serverless Lambda Rollback")
            .setType(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK)
            .setStepMetaData(
                StepMetaData.newBuilder().addCategory("ServerlessAwsLambda").setFolderPath("Serverless Lambda").build())
            .build();

    StepInfo ecsRollingDeploy =
        StepInfo.newBuilder()
            .setName("ECS Rolling Deploy")
            .setType(StepSpecTypeConstants.ECS_ROLLING_DEPLOY)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("ECS").setFolderPath("ECS").build())
            .setFeatureFlag(FeatureName.NG_SVC_ENV_REDESIGN.name())
            .build();

    StepInfo ecsRollingRollack =
        StepInfo.newBuilder()
            .setName("ECS Rolling Rollback")
            .setType(StepSpecTypeConstants.ECS_ROLLING_ROLLBACK)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("ECS").setFolderPath("ECS").build())
            .setFeatureFlag(FeatureName.NG_SVC_ENV_REDESIGN.name())
            .build();

    StepInfo ecsCanaryDeploy =
        StepInfo.newBuilder()
            .setName("ECS Canary Deploy")
            .setType(StepSpecTypeConstants.ECS_CANARY_DEPLOY)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("ECS").setFolderPath("ECS").build())
            .setFeatureFlag(FeatureName.NG_SVC_ENV_REDESIGN.name())
            .build();

    StepInfo ecsCanaryDelete =
        StepInfo.newBuilder()
            .setName("ECS Canary Delete")
            .setType(StepSpecTypeConstants.ECS_CANARY_DELETE)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("ECS").setFolderPath("ECS").build())
            .setFeatureFlag(FeatureName.NG_SVC_ENV_REDESIGN.name())
            .build();

    StepInfo ecsBlueGreenCreateService =
        StepInfo.newBuilder()
            .setName("ECS Blue Green Create Service")
            .setType(StepSpecTypeConstants.ECS_BLUE_GREEN_CREATE_SERVICE)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("ECS").setFolderPath("ECS").build())
            .setFeatureFlag(FeatureName.NG_SVC_ENV_REDESIGN.name())
            .build();

    StepInfo ecsBlueGreenSwapTargetGroups =
        StepInfo.newBuilder()
            .setName("ECS Blue Green Swap Target Groups")
            .setType(StepSpecTypeConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("ECS").setFolderPath("ECS").build())
            .setFeatureFlag(FeatureName.NG_SVC_ENV_REDESIGN.name())
            .build();

    StepInfo ecsBlueGreenRollback =
        StepInfo.newBuilder()
            .setName("ECS Blue Green Rollback")
            .setType(StepSpecTypeConstants.ECS_BLUE_GREEN_ROLLBACK)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("ECS").setFolderPath("ECS").build())
            .setFeatureFlag(FeatureName.NG_SVC_ENV_REDESIGN.name())
            .build();

    StepInfo ecsRunTask =
        StepInfo.newBuilder()
            .setName("ECS Run Task")
            .setType(StepSpecTypeConstants.ECS_RUN_TASK)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("ECS").setFolderPath("ECS").build())
            .setFeatureFlag(FeatureName.NG_SVC_ENV_REDESIGN.name())
            .build();

    StepInfo googleFunctionDeploy =
        StepInfo.newBuilder()
            .setName("Google Function Deploy")
            .setType(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_DEPLOY)
            .setStepMetaData(
                StepMetaData.newBuilder().addCategory("GoogleCloudFunctions").setFolderPath("Google Functions").build())
            .setFeatureFlag(FeatureName.CDS_GOOGLE_CLOUD_FUNCTION.name())
            .build();

    StepInfo googleFunctionDeployWithoutTraffic =
        StepInfo.newBuilder()
            .setName("Google Function Deploy With No Traffic")
            .setType(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC)
            .setStepMetaData(
                StepMetaData.newBuilder().addCategory("GoogleCloudFunctions").setFolderPath("Google Functions").build())
            .setFeatureFlag(FeatureName.CDS_GOOGLE_CLOUD_FUNCTION.name())
            .build();

    StepInfo googleFunctionTrafficShift =
        StepInfo.newBuilder()
            .setName("Google Function Traffic Shift")
            .setType(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT)
            .setStepMetaData(
                StepMetaData.newBuilder().addCategory("GoogleCloudFunctions").setFolderPath("Google Functions").build())
            .setFeatureFlag(FeatureName.CDS_GOOGLE_CLOUD_FUNCTION.name())
            .build();

    StepInfo googleFunctionRollback =
        StepInfo.newBuilder()
            .setName("Google Function Rollback")
            .setType(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_ROLLBACK)
            .setStepMetaData(
                StepMetaData.newBuilder().addCategory("GoogleCloudFunctions").setFolderPath("Google Functions").build())
            .setFeatureFlag(FeatureName.CDS_GOOGLE_CLOUD_FUNCTION.name())
            .build();

    StepInfo googleFunctionGenOneDeploy = StepInfo.newBuilder()
                                              .setName("Google Function Deploy")
                                              .setType(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_DEPLOY)
                                              .setStepMetaData(StepMetaData.newBuilder()
                                                                   .addCategory("GoogleCloudFunctionsGenOne")
                                                                   .setFolderPath("Google Functions")
                                                                   .build())
                                              .setFeatureFlag(FeatureName.CDS_GOOGLE_CLOUD_FUNCTION.name())
                                              .build();

    StepInfo googleFunctionGenOneRollback = StepInfo.newBuilder()
                                                .setName("Google Function Rollback")
                                                .setType(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_ROLLBACK)
                                                .setStepMetaData(StepMetaData.newBuilder()
                                                                     .addCategory("GoogleCloudFunctionsGenOne")
                                                                     .setFolderPath("Google Functions")
                                                                     .build())
                                                .setFeatureFlag(FeatureName.CDS_GOOGLE_CLOUD_FUNCTION.name())
                                                .build();

    StepInfo awsLambdaDeploy = StepInfo.newBuilder()
                                   .setName("Aws Lambda Deploy")
                                   .setType(StepSpecTypeConstants.AWS_LAMBDA_DEPLOY)
                                   .setStepMetaData(StepMetaData.newBuilder()
                                                        .addCategory(StepSpecTypeConstants.AWS_LAMBDA)
                                                        .setFolderPath("Aws Lambda")
                                                        .build())
                                   .setFeatureFlag(FeatureName.CDS_AWS_NATIVE_LAMBDA.name())
                                   .build();

    StepInfo createStack = StepInfo.newBuilder()
                               .setName("CloudFormation Create Stack")
                               .setType(StepSpecTypeConstants.CLOUDFORMATION_CREATE_STACK)
                               .setFeatureRestrictionName(FeatureRestrictionName.CREATE_STACK.name())
                               .setStepMetaData(StepMetaData.newBuilder()
                                                    .addAllCategory(CLOUDFORMATION_CATEGORY)
                                                    .addFolderPaths(CLOUDFORMATION_STEP_METADATA)
                                                    .build())
                               .build();

    StepInfo deleteStack = StepInfo.newBuilder()
                               .setName("CloudFormation Delete Stack")
                               .setType(StepSpecTypeConstants.CLOUDFORMATION_DELETE_STACK)
                               .setFeatureRestrictionName(FeatureRestrictionName.DELETE_STACK.name())
                               .setStepMetaData(StepMetaData.newBuilder()
                                                    .addAllCategory(CLOUDFORMATION_CATEGORY)
                                                    .addFolderPaths(CLOUDFORMATION_STEP_METADATA)
                                                    .build())
                               .build();

    StepInfo rollbackStack = StepInfo.newBuilder()
                                 .setName("CloudFormation Rollback")
                                 .setType(StepSpecTypeConstants.CLOUDFORMATION_ROLLBACK_STACK)
                                 .setFeatureRestrictionName(FeatureRestrictionName.ROLLBACK_STACK.name())
                                 .setStepMetaData(StepMetaData.newBuilder()
                                                      .addAllCategory(CLOUDFORMATION_CATEGORY)
                                                      .addFolderPaths(CLOUDFORMATION_STEP_METADATA)
                                                      .build())
                                 .build();

    StepInfo azureWebAppSlotDeployment =
        StepInfo.newBuilder()
            .setName("Azure Slot Deployment")
            .setType(StepSpecTypeConstants.AZURE_SLOT_DEPLOYMENT)
            .setFeatureRestrictionName(FeatureRestrictionName.AZURE_SLOT_DEPLOYMENT.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("AzureWebApp").addFolderPaths("AzureWebApp").build())
            .setFeatureFlag(FeatureName.NG_SVC_ENV_REDESIGN.name())
            .build();

    StepInfo azureWebAppTrafficShift =
        StepInfo.newBuilder()
            .setName("Azure Traffic Shift")
            .setType(StepSpecTypeConstants.AZURE_TRAFFIC_SHIFT)
            .setFeatureRestrictionName(FeatureRestrictionName.AZURE_TRAFFIC_SHIFT.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("AzureWebApp").addFolderPaths("AzureWebApp").build())
            .setFeatureFlag(FeatureName.NG_SVC_ENV_REDESIGN.name())
            .build();

    StepInfo azureWebAppSwapSlot =
        StepInfo.newBuilder()
            .setName("Azure Swap Slot")
            .setType(StepSpecTypeConstants.AZURE_SWAP_SLOT)
            .setFeatureRestrictionName(FeatureRestrictionName.AZURE_SWAP_SLOT.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("AzureWebApp").addFolderPaths("AzureWebApp").build())
            .setFeatureFlag(FeatureName.NG_SVC_ENV_REDESIGN.name())
            .build();

    StepInfo azureWebAppRollback =
        StepInfo.newBuilder()
            .setName("Azure WebApp Rollback")
            .setType(StepSpecTypeConstants.AZURE_WEBAPP_ROLLBACK)
            .setFeatureRestrictionName(FeatureRestrictionName.AZURE_WEBAPP_ROLLBACK.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("AzureWebApp").addFolderPaths("AzureWebApp").build())
            .setFeatureFlag(FeatureName.NG_SVC_ENV_REDESIGN.name())
            .build();

    StepInfo jenkinsBuildStepInfo =
        StepInfo.newBuilder()
            .setName("Jenkins Build")
            .setType(StepSpecTypeConstants.JENKINS_BUILD)
            .setFeatureRestrictionName(FeatureRestrictionName.JENKINS_BUILD.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory(BUILD_STEP).addFolderPaths("Builds").build())
            .build();

    StepInfo bambooBuildStepInfo =
        StepInfo.newBuilder()
            .setName("Bamboo Build")
            .setType(StepSpecTypeConstants.BAMBOO_BUILD)
            .setFeatureRestrictionName(FeatureRestrictionName.BAMBOO_BUILD.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory(BUILD_STEP).addFolderPaths("Builds").build())
            .setFeatureFlag(FeatureName.BAMBOO_BUILD.name())
            .build();

    StepInfo azureCreateARMResources =
        StepInfo.newBuilder()
            .setName("Create Azure ARM Resources")
            .setType(StepSpecTypeConstants.AZURE_CREATE_ARM_RESOURCE)
            .setFeatureRestrictionName(FeatureRestrictionName.AZURE_CREATE_ARM_RESOURCE.name())
            .setStepMetaData(StepMetaData.newBuilder()
                                 .addAllCategory(AZURE_RESOURCE_CATEGORY)
                                 .addFolderPaths(AZURE_RESOURCE_STEP_METADATA)
                                 .build())
            .build();

    StepInfo azureCreateBPResources =
        StepInfo.newBuilder()
            .setName("Create Azure BP Resources")
            .setType(StepSpecTypeConstants.AZURE_CREATE_BP_RESOURCE)
            .setFeatureRestrictionName(FeatureRestrictionName.AZURE_CREATE_BP_RESOURCE.name())
            .setStepMetaData(StepMetaData.newBuilder()
                                 .addAllCategory(AZURE_RESOURCE_CATEGORY)
                                 .addFolderPaths(AZURE_RESOURCE_STEP_METADATA)
                                 .build())
            .build();

    StepInfo azureARMRollback =
        StepInfo.newBuilder()
            .setName("Rollback Azure ARM Resources")
            .setType(StepSpecTypeConstants.AZURE_ROLLBACK_ARM_RESOURCE)
            .setFeatureRestrictionName(FeatureRestrictionName.AZURE_ROLLBACK_ARM_RESOURCE.name())
            .setStepMetaData(StepMetaData.newBuilder()
                                 .addAllCategory(AZURE_RESOURCE_CATEGORY)
                                 .addFolderPaths(AZURE_RESOURCE_STEP_METADATA)
                                 .build())
            .build();

    StepInfo fetchInstanceScript =
        StepInfo.newBuilder()
            .setName(CustomDeploymentConstants.FETCH_INSTANCE_SCRIPT)
            .setType(StepSpecTypeConstants.CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(CUSTOM_DEPLOYMENT).addFolderPaths(CUSTOM).build())
            .setFeatureFlag(FeatureName.NG_SVC_ENV_REDESIGN.name())
            .build();

    StepInfo shellScriptProvision = StepInfo.newBuilder()
                                        .setName("Shell Script Provision")
                                        .setType(StepSpecTypeConstants.SHELL_SCRIPT_PROVISION)
                                        .setFeatureRestrictionName(FeatureRestrictionName.SHELL_SCRIPT_PROVISION.name())
                                        .setStepMetaData(StepMetaData.newBuilder()
                                                             .addAllCategory(SHELL_SCRIPT_PROVISIONER_CATEGORY)
                                                             .addFolderPaths(SHELL_SCRIPT_PROVISIONER_STEM_METADATA)
                                                             .build())
                                        .build();

    StepInfo chaosStep =
        StepInfo.newBuilder()
            .setName("Chaos Step")
            .setType(StepSpecTypeConstants.CHAOS_STEP)
            .setStepMetaData(
                StepMetaData.newBuilder().addAllCategory(Arrays.asList("Chaos")).addFolderPaths("Chaos").build())
            .build();

    StepInfo elastigroupDeployStep =
        StepInfo.newBuilder()
            .setName("Elastigroup Deploy")
            .setType(StepSpecTypeConstants.ELASTIGROUP_DEPLOY)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(ELASTIGROUP).addFolderPaths("Elastigroup").build())
            .build();

    StepInfo elastigroupRollbackStep =
        StepInfo.newBuilder()
            .setName("Elastigroup Rollback")
            .setType(StepSpecTypeConstants.ELASTIGROUP_ROLLBACK)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(ELASTIGROUP).addFolderPaths("Elastigroup").build())
            .build();

    StepInfo elastigroupSetup =
        StepInfo.newBuilder()
            .setName("Elastigroup Setup")
            .setType(StepSpecTypeConstants.ELASTIGROUP_SETUP)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Elastigroup").setFolderPath("Elastigroup").build())
            .build();

    StepInfo terragruntPlan = StepInfo.newBuilder()
                                  .setName("Terragrunt Plan")
                                  .setType(StepSpecTypeConstants.TERRAGRUNT_PLAN)
                                  .setFeatureRestrictionName(FeatureRestrictionName.TERRAGRUNT_PLAN.name())
                                  .setStepMetaData(StepMetaData.newBuilder()
                                                       .addAllCategory(TERRAGRUNT_CATEGORY)
                                                       .addFolderPaths(TERRAGRUNT_STEP_METADATA)
                                                       .build())
                                  .build();

    StepInfo terragruntApply = StepInfo.newBuilder()
                                   .setName("Terragrunt Apply")
                                   .setType(StepSpecTypeConstants.TERRAGRUNT_APPLY)
                                   .setFeatureRestrictionName(FeatureRestrictionName.TERRAGRUNT_APPLY.name())
                                   .setStepMetaData(StepMetaData.newBuilder()
                                                        .addAllCategory(TERRAGRUNT_CATEGORY)
                                                        .addFolderPaths(TERRAGRUNT_STEP_METADATA)
                                                        .build())
                                   .build();

    StepInfo terragruntDestroy = StepInfo.newBuilder()
                                     .setName("Terragrunt Destroy")
                                     .setType(StepSpecTypeConstants.TERRAGRUNT_DESTROY)
                                     .setFeatureRestrictionName(FeatureRestrictionName.TERRAGRUNT_DESTROY.name())
                                     .setStepMetaData(StepMetaData.newBuilder()
                                                          .addAllCategory(TERRAGRUNT_CATEGORY)
                                                          .addFolderPaths(TERRAGRUNT_STEP_METADATA)
                                                          .build())
                                     .build();

    StepInfo terragruntRollback = StepInfo.newBuilder()
                                      .setName("Terragrunt Rollback")
                                      .setType(StepSpecTypeConstants.TERRAGRUNT_ROLLBACK)
                                      .setFeatureRestrictionName(FeatureRestrictionName.TERRAGRUNT_ROLLBACK.name())
                                      .setStepMetaData(StepMetaData.newBuilder()
                                                           .addAllCategory(TERRAGRUNT_CATEGORY)
                                                           .addFolderPaths(TERRAGRUNT_STEP_METADATA)
                                                           .build())
                                      .build();
    StepInfo elastigroupBGStageSetup =
        StepInfo.newBuilder()
            .setName("Elastigroup BG Stage Setup")
            .setType(StepSpecTypeConstants.ELASTIGROUP_BG_STAGE_SETUP)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Elastigroup").setFolderPath("Elastigroup").build())
            .build();

    StepInfo elastigroupSwapRoute =
        StepInfo.newBuilder()
            .setName("Elastigroup Swap Route")
            .setType(StepSpecTypeConstants.ELASTIGROUP_SWAP_ROUTE)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Elastigroup").setFolderPath("Elastigroup").build())
            .build();

    StepInfo asgCanaryDeploy =
        StepInfo.newBuilder()
            .setName("ASG Canary Deploy")
            .setType(StepSpecTypeConstants.ASG_CANARY_DEPLOY)
            .setStepMetaData(StepMetaData.newBuilder().addAllCategory(ASG_CATEGORY).setFolderPath(ASG).build())
            .build();

    StepInfo asgCanaryDelete =
        StepInfo.newBuilder()
            .setName("ASG Canary Delete")
            .setType(StepSpecTypeConstants.ASG_CANARY_DELETE)
            .setStepMetaData(StepMetaData.newBuilder().addAllCategory(ASG_CATEGORY).setFolderPath(ASG).build())
            .build();

    StepInfo tasCanaryAppSetup =
        StepInfo.newBuilder()
            .setName("Canary App Setup")
            .setType(StepSpecTypeConstants.TAS_CANARY_APP_SETUP)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(TAS).addFolderPaths(TAS).build())
            .setFeatureFlag(FeatureName.CDS_TAS_NG.name())
            .build();
    StepInfo tasBGAppSetup =
        StepInfo.newBuilder()
            .setName("BG App Setup")
            .setType(StepSpecTypeConstants.TAS_BG_APP_SETUP)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(TAS).addFolderPaths(TAS).build())
            .setFeatureFlag(FeatureName.CDS_TAS_NG.name())
            .build();
    StepInfo tasBasicAppSetup =
        StepInfo.newBuilder()
            .setName("Basic App Setup")
            .setType(StepSpecTypeConstants.TAS_BASIC_APP_SETUP)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(TAS).addFolderPaths(TAS).build())
            .setFeatureFlag(FeatureName.CDS_TAS_NG.name())
            .build();
    StepInfo tasAppResize = StepInfo.newBuilder()
                                .setName("App Resize")
                                .setType(StepSpecTypeConstants.TAS_APP_RESIZE)
                                .setStepMetaData(StepMetaData.newBuilder().addCategory(TAS).addFolderPaths(TAS).build())
                                .setFeatureFlag(FeatureName.CDS_TAS_NG.name())
                                .build();
    StepInfo tasSwapRoutes =
        StepInfo.newBuilder()
            .setName("Swap Routes")
            .setType(StepSpecTypeConstants.TAS_SWAP_ROUTES)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(TAS).addFolderPaths(TAS).build())
            .setFeatureFlag(FeatureName.CDS_TAS_NG.name())
            .build();
    StepInfo tasRollback = StepInfo.newBuilder()
                               .setName("App Rollback")
                               .setType(StepSpecTypeConstants.TAS_ROLLBACK)
                               .setStepMetaData(StepMetaData.newBuilder().addCategory(TAS).addFolderPaths(TAS).build())
                               .setFeatureFlag(FeatureName.CDS_TAS_NG.name())
                               .build();
    StepInfo tasSwapRollback =
        StepInfo.newBuilder()
            .setName("Swap Rollback")
            .setType(StepSpecTypeConstants.SWAP_ROLLBACK)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(TAS).setFolderPath(TAS).build())
            .setFeatureFlag(FeatureName.CDS_TAS_NG.name())
            .build();

    StepInfo tanzuCommand = StepInfo.newBuilder()
                                .setName("Tanzu Command")
                                .setType(StepSpecTypeConstants.TANZU_COMMAND)
                                .setStepMetaData(StepMetaData.newBuilder().addCategory(TAS).setFolderPath(TAS).build())
                                .setFeatureFlag(FeatureName.CDS_TAS_NG.name())
                                .build();

    StepInfo asgRollingDeploy =
        StepInfo.newBuilder()
            .setName("ASG Rolling Deploy")
            .setType(StepSpecTypeConstants.ASG_ROLLING_DEPLOY)
            .setStepMetaData(StepMetaData.newBuilder().addAllCategory(ASG_CATEGORY).setFolderPath(ASG).build())
            .build();

    StepInfo asgRollingRollback =
        StepInfo.newBuilder()
            .setName("ASG Rolling Rollback")
            .setType(StepSpecTypeConstants.ASG_ROLLING_ROLLBACK)
            .setStepMetaData(StepMetaData.newBuilder().addAllCategory(ASG_CATEGORY).setFolderPath(ASG).build())
            .build();

    StepInfo asgBlueGreenDeploy =
        StepInfo.newBuilder()
            .setName("ASG Blue Green Deploy")
            .setType(StepSpecTypeConstants.ASG_BLUE_GREEN_DEPLOY)
            .setStepMetaData(StepMetaData.newBuilder().addAllCategory(ASG_CATEGORY).setFolderPath(ASG).build())
            .build();

    StepInfo asgBlueGreenRollback =
        StepInfo.newBuilder()
            .setName("ASG Blue Green Rollback")
            .setType(StepSpecTypeConstants.ASG_BLUE_GREEN_ROLLBACK)
            .setStepMetaData(StepMetaData.newBuilder().addAllCategory(ASG_CATEGORY).setFolderPath(ASG).build())
            .build();

    StepInfo tasRollingDeploy =
        StepInfo.newBuilder()
            .setName("Rolling Deploy")
            .setType(StepSpecTypeConstants.TAS_ROLLING_DEPLOY)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(TAS).setFolderPath(TAS).build())
            .setFeatureFlag(FeatureName.CDS_TAS_NG.name())
            .build();

    StepInfo tasRollingRollback =
        StepInfo.newBuilder()
            .setName("Rolling Rollback")
            .setType(StepSpecTypeConstants.TAS_ROLLING_ROLLBACK)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(TAS).setFolderPath(TAS).build())
            .setFeatureFlag(FeatureName.CDS_TAS_NG.name())
            .build();

    StepInfo k8sDryRunManifest = StepInfo.newBuilder()
                                     .setName("Dry Run")
                                     .setType(StepSpecTypeConstants.K8S_DRY_RUN_MANIFEST)
                                     .setFeatureRestrictionName(FeatureRestrictionName.K8S_DRY_RUN.name())
                                     .setStepMetaData(StepMetaData.newBuilder()
                                                          .addCategory("Kubernetes")
                                                          .addCategory("Helm")
                                                          .addFolderPaths("Kubernetes")
                                                          .build())
                                     .setFeatureFlag(FeatureName.K8S_DRY_RUN_NG.name())
                                     .build();

    StepInfo asgBlueGreenSwapService =
        StepInfo.newBuilder()
            .setName("ASG Blue Green Swap Service")
            .setType(StepSpecTypeConstants.ASG_BLUE_GREEN_SWAP_SERVICE)
            .setStepMetaData(StepMetaData.newBuilder().addAllCategory(ASG_CATEGORY).setFolderPath(ASG).build())
            .build();

    StepInfo tasRouteMapping =
        StepInfo.newBuilder()
            .setName("Route Mapping")
            .setType(StepSpecTypeConstants.TAS_ROUTE_MAPPING)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(TAS).setFolderPath(TAS).build())
            .setFeatureFlag(FeatureName.CDS_TAS_NG.name())
            .build();

    StepInfo terraformCloudRun = StepInfo.newBuilder()
                                     .setName("Terraform Cloud Run")
                                     .setType(StepSpecTypeConstants.TERRAFORM_CLOUD_RUN)
                                     .setFeatureRestrictionName(FeatureRestrictionName.TERRAFORM_CLOUD_RUN.name())
                                     .setStepMetaData(StepMetaData.newBuilder()
                                                          .addAllCategory(TERRAFORM_CLOUD_CATEGORY)
                                                          .setFolderPath(TERRAFORM_CLOUD_STEP_METADATA)
                                                          .build())
                                     .setFeatureFlag(FeatureName.CDS_TERRAFORM_CLOUD.name())
                                     .build();
    StepInfo awsSamDeploy =
        StepInfo.newBuilder()
            .setName("AWS SAM Deploy")
            .setType(StepSpecTypeConstants.AWS_SAM_DEPLOY)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("AwsSamDeploy").setFolderPath("AWS SAM").build())
            .setFeatureFlag(FeatureName.CDP_AWS_SAM.name())
            .build();

    StepInfo awsSamBuild =
        StepInfo.newBuilder()
            .setName("AWS SAM Build")
            .setType(StepSpecTypeConstants.AWS_SAM_BUILD)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("AwsSamBuild").setFolderPath("AWS SAM").build())
            .setFeatureFlag(FeatureName.CDP_AWS_SAM.name())
            .build();

    StepInfo awsSamRollback =
        StepInfo.newBuilder()
            .setName("AWS SAM Rollback")
            .setType(StepSpecTypeConstants.AWS_SAM_ROLLBACK)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("AwsSamRollback").setFolderPath("AWS SAM").build())
            .setFeatureFlag(FeatureName.CDP_AWS_SAM.name())
            .build();

    StepInfo terraformCloudRollback =
        StepInfo.newBuilder()
            .setName("Terraform Cloud Rollback")
            .setType(StepSpecTypeConstants.TERRAFORM_CLOUD_ROLLBACK)
            .setFeatureRestrictionName(FeatureRestrictionName.TERRAFORM_CLOUD_ROLLBACK.name())
            .setStepMetaData(StepMetaData.newBuilder()
                                 .addAllCategory(TERRAFORM_CLOUD_CATEGORY)
                                 .setFolderPath(TERRAFORM_CLOUD_STEP_METADATA)
                                 .build())
            .setFeatureFlag(FeatureName.CDS_TERRAFORM_CLOUD.name())
            .build();

    StepInfo awsLambdaRollback =
        StepInfo.newBuilder()
            .setName("Aws Lambda Rollback")
            .setType(StepSpecTypeConstants.AWS_LAMBDA_ROLLBACK)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(AWS_LAMBDA).setFolderPath("Aws Lambda").build())
            .setFeatureFlag(FeatureName.CDS_AWS_NATIVE_LAMBDA.name())
            .build();

    StepInfo k8sBGStageScaleDown =
        StepInfo.newBuilder()
            .setName("Blue Green Stage Scale Down")
            .setType(StepSpecTypeConstants.K8S_BLUE_GREEN_STAGE_SCALE_DOWN)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_BLUE_GREEN_STAGE_SCALE_DOWN.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .setFeatureFlag(FeatureName.CDS_BG_STAGE_SCALE_DOWN_STEP_NG.name())
            .build();

    List<StepInfo> stepInfos = new ArrayList<>();

    stepInfos.add(gitOpsCreatePR);
    stepInfos.add(gitOpsMergePR);
    stepInfos.add(gitOpsSync);
    stepInfos.add(updateReleaseRepo);
    stepInfos.add(fetchLinkedApps);
    stepInfos.add(k8sRolling);
    stepInfos.add(delete);
    stepInfos.add(canaryDeploy);
    stepInfos.add(canaryDelete);
    stepInfos.add(stageDeployment);
    stepInfos.add(bgSwapServices);
    stepInfos.add(apply);
    stepInfos.add(scale);
    stepInfos.add(k8sRollingRollback);
    stepInfos.add(terraformApply);
    stepInfos.add(terraformPlan);
    stepInfos.add(terraformRollback);
    stepInfos.add(terraformDestroy);
    stepInfos.add(helmDeploy);
    stepInfos.add(helmRollback);
    stepInfos.add(serverlessDeploy);
    stepInfos.add(serverlessRollback);
    stepInfos.add(createStack);
    stepInfos.add(deleteStack);
    stepInfos.add(rollbackStack);
    stepInfos.add(executeCommand);
    stepInfos.add(azureWebAppSlotDeployment);
    stepInfos.add(azureWebAppTrafficShift);
    stepInfos.add(azureWebAppSwapSlot);
    stepInfos.add(azureWebAppRollback);
    stepInfos.add(jenkinsBuildStepInfo);
    stepInfos.add(bambooBuildStepInfo);
    stepInfos.add(ecsRollingDeploy);
    stepInfos.add(ecsRollingRollack);
    stepInfos.add(ecsCanaryDeploy);
    stepInfos.add(ecsCanaryDelete);
    stepInfos.add(ecsRunTask);
    stepInfos.add(azureCreateARMResources);
    stepInfos.add(azureCreateBPResources);
    stepInfos.add(azureARMRollback);
    stepInfos.add(fetchInstanceScript);
    stepInfos.add(ecsBlueGreenCreateService);
    stepInfos.add(ecsBlueGreenSwapTargetGroups);
    stepInfos.add(ecsBlueGreenRollback);
    stepInfos.add(shellScriptProvision);
    stepInfos.add(chaosStep);
    stepInfos.add(elastigroupDeployStep);
    stepInfos.add(elastigroupRollbackStep);
    stepInfos.add(elastigroupSetup);
    stepInfos.add(terragruntPlan);
    stepInfos.add(terragruntApply);
    stepInfos.add(terragruntDestroy);
    stepInfos.add(terragruntRollback);
    stepInfos.add(asgCanaryDeploy);
    stepInfos.add(asgCanaryDelete);
    stepInfos.add(tasCanaryAppSetup);
    stepInfos.add(tasBGAppSetup);
    stepInfos.add(tasBasicAppSetup);
    stepInfos.add(tanzuCommand);
    stepInfos.add(tasSwapRoutes);
    stepInfos.add(tasSwapRollback);
    stepInfos.add(tasAppResize);
    stepInfos.add(tasRollback);
    stepInfos.add(elastigroupBGStageSetup);
    stepInfos.add(elastigroupSwapRoute);
    stepInfos.add(asgRollingDeploy);
    stepInfos.add(asgRollingRollback);
    stepInfos.add(googleFunctionDeploy);
    stepInfos.add(googleFunctionDeployWithoutTraffic);
    stepInfos.add(googleFunctionTrafficShift);
    stepInfos.add(googleFunctionRollback);
    stepInfos.add(asgBlueGreenDeploy);
    stepInfos.add(asgBlueGreenRollback);
    stepInfos.add(tasRollingDeploy);
    stepInfos.add(tasRollingRollback);
    stepInfos.add(k8sDryRunManifest);
    stepInfos.add(asgBlueGreenSwapService);
    stepInfos.add(terraformCloudRun);
    stepInfos.add(awsLambdaDeploy);
    stepInfos.add(awsSamDeploy);
    stepInfos.add(awsSamBuild);
    stepInfos.add(awsSamRollback);
    stepInfos.add(terraformCloudRollback);
    stepInfos.add(awsLambdaRollback);
    stepInfos.add(tasRouteMapping);
    stepInfos.add(googleFunctionGenOneDeploy);
    stepInfos.add(googleFunctionGenOneRollback);
    stepInfos.add(k8sBGStageScaleDown);

    return stepInfos;
  }
}