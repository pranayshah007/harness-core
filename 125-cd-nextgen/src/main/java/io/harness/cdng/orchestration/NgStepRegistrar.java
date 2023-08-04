/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.orchestration;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.steps.ArtifactStep;
import io.harness.cdng.artifact.steps.ArtifactSyncStep;
import io.harness.cdng.artifact.steps.ArtifactsStep;
import io.harness.cdng.artifact.steps.ArtifactsStepV2;
import io.harness.cdng.artifact.steps.SidecarsStep;
import io.harness.cdng.artifact.steps.constants.ArtifactStepConstants;
import io.harness.cdng.artifact.steps.constants.ArtifactSyncStepConstants;
import io.harness.cdng.artifact.steps.constants.ArtifactsStepConstants;
import io.harness.cdng.artifact.steps.constants.ArtifactsStepV2Constants;
import io.harness.cdng.artifact.steps.constants.SidecarsStepConstants;
import io.harness.cdng.aws.asg.AsgBlueGreenDeployStep;
import io.harness.cdng.aws.asg.AsgBlueGreenRollbackStep;
import io.harness.cdng.aws.asg.AsgBlueGreenSwapServiceStep;
import io.harness.cdng.aws.asg.AsgCanaryDeleteStep;
import io.harness.cdng.aws.asg.AsgCanaryDeployStep;
import io.harness.cdng.aws.asg.AsgRollingDeployStep;
import io.harness.cdng.aws.asg.AsgRollingRollbackStep;
import io.harness.cdng.aws.lambda.deploy.AwsLambdaDeployStep;
import io.harness.cdng.aws.lambda.rollback.AwsLambdaRollbackStep;
import io.harness.cdng.aws.sam.AwsSamBuildStep;
import io.harness.cdng.aws.sam.AwsSamDeployStep;
import io.harness.cdng.aws.sam.AwsSamRollbackStep;
import io.harness.cdng.aws.sam.DownloadManifestsStep;
import io.harness.cdng.azure.webapp.ApplicationSettingsStep;
import io.harness.cdng.azure.webapp.AzureServiceSettingsStep;
import io.harness.cdng.azure.webapp.AzureWebAppRollbackStep;
import io.harness.cdng.azure.webapp.AzureWebAppSlotDeploymentStep;
import io.harness.cdng.azure.webapp.AzureWebAppSwapSlotStep;
import io.harness.cdng.azure.webapp.AzureWebAppTrafficShiftStep;
import io.harness.cdng.azure.webapp.ConnectionStringsStep;
import io.harness.cdng.azure.webapp.StartupCommandStep;
import io.harness.cdng.bamboo.BambooBuildStep;
import io.harness.cdng.chaos.ChaosStep;
import io.harness.cdng.configfile.steps.ConfigFilesStep;
import io.harness.cdng.configfile.steps.ConfigFilesStepV2;
import io.harness.cdng.configfile.steps.IndividualConfigFileStep;
import io.harness.cdng.customDeployment.FetchInstanceScriptStep;
import io.harness.cdng.ecs.EcsBlueGreenCreateServiceStep;
import io.harness.cdng.ecs.EcsBlueGreenRollbackStep;
import io.harness.cdng.ecs.EcsBlueGreenSwapTargetGroupsStep;
import io.harness.cdng.ecs.EcsCanaryDeleteStep;
import io.harness.cdng.ecs.EcsCanaryDeployStep;
import io.harness.cdng.ecs.EcsRollingDeployStep;
import io.harness.cdng.ecs.EcsRollingRollbackStep;
import io.harness.cdng.ecs.EcsRunTaskStep;
import io.harness.cdng.elastigroup.ElastigroupBGStageSetupStep;
import io.harness.cdng.elastigroup.ElastigroupServiceSettingsStep;
import io.harness.cdng.elastigroup.ElastigroupSetupStep;
import io.harness.cdng.elastigroup.ElastigroupSwapRouteStep;
import io.harness.cdng.elastigroup.deploy.ElastigroupDeployStep;
import io.harness.cdng.elastigroup.rollback.ElastigroupRollbackStep;
import io.harness.cdng.gitops.MergePRStep;
import io.harness.cdng.gitops.UpdateReleaseRepoStep;
import io.harness.cdng.gitops.revertpr.RevertPRStep;
import io.harness.cdng.gitops.steps.FetchLinkedAppsStep;
import io.harness.cdng.gitops.steps.GitopsClustersStep;
import io.harness.cdng.gitops.syncstep.SyncStep;
import io.harness.cdng.googlefunctions.deploy.GoogleFunctionsDeployStep;
import io.harness.cdng.googlefunctions.deployWithoutTraffic.GoogleFunctionsDeployWithoutTrafficStep;
import io.harness.cdng.googlefunctions.deploygenone.GoogleFunctionsGenOneDeployStep;
import io.harness.cdng.googlefunctions.rollback.GoogleFunctionsRollbackStep;
import io.harness.cdng.googlefunctions.rollbackgenone.GoogleFunctionsGenOneRollbackStep;
import io.harness.cdng.googlefunctions.trafficShift.GoogleFunctionsTrafficShiftStep;
import io.harness.cdng.helm.HelmDeployStep;
import io.harness.cdng.helm.HelmRollbackStep;
import io.harness.cdng.hooks.steps.ServiceHooksStep;
import io.harness.cdng.infra.steps.EnvironmentStep;
import io.harness.cdng.infra.steps.InfrastructureSectionStep;
import io.harness.cdng.infra.steps.InfrastructureStep;
import io.harness.cdng.infra.steps.InfrastructureTaskExecutableStep;
import io.harness.cdng.infra.steps.InfrastructureTaskExecutableStepV2;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildStep;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildStepV2;
import io.harness.cdng.k8s.K8sApplyStep;
import io.harness.cdng.k8s.K8sBGStageScaleDownStep;
import io.harness.cdng.k8s.K8sBGSwapServicesStep;
import io.harness.cdng.k8s.K8sBlueGreenStep;
import io.harness.cdng.k8s.K8sCanaryDeleteStep;
import io.harness.cdng.k8s.K8sCanaryStep;
import io.harness.cdng.k8s.K8sDeleteStep;
import io.harness.cdng.k8s.K8sDryRunManifestStep;
import io.harness.cdng.k8s.K8sRollingRollbackStep;
import io.harness.cdng.k8s.K8sRollingStep;
import io.harness.cdng.k8s.K8sScaleStep;
import io.harness.cdng.manifest.steps.ManifestStep;
import io.harness.cdng.manifest.steps.ManifestsStep;
import io.harness.cdng.manifest.steps.ManifestsStepV2;
import io.harness.cdng.pipeline.steps.CombinedRollbackStep;
import io.harness.cdng.pipeline.steps.DeploymentStageStep;
import io.harness.cdng.pipeline.steps.MultiDeploymentSpawnerStep;
import io.harness.cdng.pipeline.steps.NGSectionStep;
import io.harness.cdng.pipeline.steps.RollbackOptionalChildChainStep;
import io.harness.cdng.pipeline.steps.RollbackOptionalChildrenStep;
import io.harness.cdng.provision.awscdk.AwsCdkBootstrapStep;
import io.harness.cdng.provision.awscdk.AwsCdkDeployStep;
import io.harness.cdng.provision.awscdk.AwsCdkDestroyStep;
import io.harness.cdng.provision.awscdk.AwsCdkDiffStep;
import io.harness.cdng.provision.awscdk.AwsCdkSynthStep;
import io.harness.cdng.provision.azure.AzureARMRollbackStep;
import io.harness.cdng.provision.azure.AzureCreateARMResourceStep;
import io.harness.cdng.provision.azure.AzureCreateBPStep;
import io.harness.cdng.provision.cloudformation.CloudformationCreateStackStep;
import io.harness.cdng.provision.cloudformation.CloudformationDeleteStackStep;
import io.harness.cdng.provision.cloudformation.CloudformationRollbackStep;
import io.harness.cdng.provision.shellscript.ShellScriptProvisionStep;
import io.harness.cdng.provision.terraform.TerraformApplyStep;
import io.harness.cdng.provision.terraform.TerraformApplyStepV2;
import io.harness.cdng.provision.terraform.TerraformDestroyStep;
import io.harness.cdng.provision.terraform.TerraformDestroyStepV2;
import io.harness.cdng.provision.terraform.TerraformPlanStep;
import io.harness.cdng.provision.terraform.TerraformPlanStepV2;
import io.harness.cdng.provision.terraform.steps.rolllback.TerraformRollbackStep;
import io.harness.cdng.provision.terraform.steps.rolllback.TerraformRollbackStepV2;
import io.harness.cdng.provision.terraformcloud.steps.TerraformCloudRollbackStep;
import io.harness.cdng.provision.terraformcloud.steps.TerraformCloudRunStep;
import io.harness.cdng.provision.terragrunt.TerragruntApplyStep;
import io.harness.cdng.provision.terragrunt.TerragruntDestroyStep;
import io.harness.cdng.provision.terragrunt.TerragruntPlanStep;
import io.harness.cdng.provision.terragrunt.TerragruntRollbackStep;
import io.harness.cdng.rollback.steps.InfrastructureDefinitionStep;
import io.harness.cdng.rollback.steps.InfrastructureProvisionerStep;
import io.harness.cdng.rollback.steps.RollbackStepsStep;
import io.harness.cdng.serverless.ServerlessAwsLambdaDeployStep;
import io.harness.cdng.serverless.ServerlessAwsLambdaRollbackStep;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaDeployV2Step;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaPackageV2Step;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaPrepareRollbackV2Step;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaRollbackV2Step;
import io.harness.cdng.service.steps.ServiceConfigStep;
import io.harness.cdng.service.steps.ServiceDefinitionStep;
import io.harness.cdng.service.steps.ServiceSectionStep;
import io.harness.cdng.service.steps.ServiceSpecStep;
import io.harness.cdng.service.steps.ServiceStep;
import io.harness.cdng.service.steps.ServiceStepV3;
import io.harness.cdng.service.steps.constants.ServiceConfigStepConstants;
import io.harness.cdng.service.steps.constants.ServiceSectionStepConstants;
import io.harness.cdng.service.steps.constants.ServiceStepConstants;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.ssh.CommandStep;
import io.harness.cdng.tas.TasAppResizeStep;
import io.harness.cdng.tas.TasBGAppSetupStep;
import io.harness.cdng.tas.TasBasicAppSetupStep;
import io.harness.cdng.tas.TasCanaryAppSetupStep;
import io.harness.cdng.tas.TasCommandStep;
import io.harness.cdng.tas.TasRollbackStep;
import io.harness.cdng.tas.TasRollingDeployStep;
import io.harness.cdng.tas.TasRollingRollbackStep;
import io.harness.cdng.tas.TasRouteMappingStep;
import io.harness.cdng.tas.TasSwapRollbackStep;
import io.harness.cdng.tas.TasSwapRoutesStep;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.registrar.NGCommonUtilStepsRegistrar;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_GITOPS, HarnessModuleComponent.CDS_SERVERLESS,
        HarnessModuleComponent.CDS_INFRA_PROVISIONERS, HarnessModuleComponent.CDS_ECS})
@OwnedBy(CDC)
@UtilityClass
public class NgStepRegistrar {
  public Map<StepType, Class<? extends Step>> getEngineSteps() {
    Map<StepType, Class<? extends Step>> engineSteps = new HashMap<>();

    // Add CDNG steps here
    engineSteps.put(MergePRStep.STEP_TYPE, MergePRStep.class);
    engineSteps.put(RevertPRStep.STEP_TYPE, RevertPRStep.class);
    engineSteps.put(UpdateReleaseRepoStep.STEP_TYPE, UpdateReleaseRepoStep.class);
    engineSteps.put(FetchLinkedAppsStep.STEP_TYPE, FetchLinkedAppsStep.class);
    engineSteps.put(SyncStep.STEP_TYPE, SyncStep.class);
    engineSteps.put(RollbackOptionalChildChainStep.STEP_TYPE, RollbackOptionalChildChainStep.class);
    engineSteps.put(CombinedRollbackStep.STEP_TYPE, CombinedRollbackStep.class);
    engineSteps.put(RollbackOptionalChildrenStep.STEP_TYPE, RollbackOptionalChildrenStep.class);
    engineSteps.put(NGSectionStep.STEP_TYPE, NGSectionStep.class);
    engineSteps.put(InfrastructureSectionStep.STEP_TYPE, InfrastructureSectionStep.class);
    engineSteps.put(InfrastructureStep.STEP_TYPE, InfrastructureStep.class);
    engineSteps.put(InfrastructureTaskExecutableStep.STEP_TYPE, InfrastructureTaskExecutableStep.class);
    engineSteps.put(InfrastructureTaskExecutableStepV2.STEP_TYPE, InfrastructureTaskExecutableStepV2.class);
    engineSteps.put(DeploymentStageStep.STEP_TYPE, DeploymentStageStep.class);
    engineSteps.put(ServiceConfigStepConstants.STEP_TYPE, ServiceConfigStep.class);
    engineSteps.put(ServiceSectionStepConstants.STEP_TYPE, ServiceSectionStep.class);
    engineSteps.put(ServiceStepConstants.STEP_TYPE, ServiceStep.class);
    engineSteps.put(ServiceStepV3Constants.STEP_TYPE, ServiceStepV3.class);
    engineSteps.put(ServiceDefinitionStep.STEP_TYPE, ServiceDefinitionStep.class);
    engineSteps.put(ServiceSpecStep.STEP_TYPE, ServiceSpecStep.class);
    engineSteps.put(ArtifactsStepConstants.STEP_TYPE, ArtifactsStep.class);
    engineSteps.put(ArtifactsStepV2Constants.STEP_TYPE, ArtifactsStepV2.class);
    engineSteps.put(SidecarsStepConstants.STEP_TYPE, SidecarsStep.class);
    engineSteps.put(ArtifactStepConstants.STEP_TYPE, ArtifactStep.class);
    engineSteps.put(ArtifactSyncStepConstants.STEP_TYPE, ArtifactSyncStep.class);
    engineSteps.put(ManifestsStep.STEP_TYPE, ManifestsStep.class);
    engineSteps.put(ManifestStep.STEP_TYPE, ManifestStep.class);
    engineSteps.put(ManifestsStepV2.STEP_TYPE, ManifestsStepV2.class);
    engineSteps.put(K8sDeleteStep.STEP_TYPE, K8sDeleteStep.class);
    engineSteps.put(K8sRollingStep.STEP_TYPE, K8sRollingStep.class);
    engineSteps.put(K8sRollingRollbackStep.STEP_TYPE, K8sRollingRollbackStep.class);
    engineSteps.put(K8sScaleStep.STEP_TYPE, K8sScaleStep.class);
    engineSteps.put(K8sCanaryStep.STEP_TYPE, K8sCanaryStep.class);
    engineSteps.put(K8sCanaryDeleteStep.STEP_TYPE, K8sCanaryDeleteStep.class);
    engineSteps.put(K8sBlueGreenStep.STEP_TYPE, K8sBlueGreenStep.class);
    engineSteps.put(K8sBGSwapServicesStep.STEP_TYPE, K8sBGSwapServicesStep.class);
    engineSteps.put(K8sApplyStep.STEP_TYPE, K8sApplyStep.class);
    engineSteps.put(K8sDryRunManifestStep.STEP_TYPE, K8sDryRunManifestStep.class);
    engineSteps.put(K8sBGStageScaleDownStep.STEP_TYPE, K8sBGStageScaleDownStep.class);
    engineSteps.put(TerraformApplyStep.STEP_TYPE, TerraformApplyStep.class);
    engineSteps.put(TerraformPlanStep.STEP_TYPE, TerraformPlanStep.class);
    engineSteps.put(TerraformDestroyStep.STEP_TYPE, TerraformDestroyStep.class);
    engineSteps.put(TerraformRollbackStep.STEP_TYPE, TerraformRollbackStep.class);
    engineSteps.put(InfrastructureDefinitionStep.STEP_TYPE, InfrastructureDefinitionStep.class);
    engineSteps.put(InfrastructureProvisionerStep.STEP_TYPE, InfrastructureProvisionerStep.class);
    engineSteps.put(RollbackStepsStep.STEP_TYPE, RollbackStepsStep.class);
    engineSteps.put(EnvironmentStep.STEP_TYPE, EnvironmentStep.class);
    engineSteps.put(HelmDeployStep.STEP_TYPE, HelmDeployStep.class);
    engineSteps.put(HelmRollbackStep.STEP_TYPE, HelmRollbackStep.class);
    engineSteps.put(CloudformationDeleteStackStep.STEP_TYPE, CloudformationDeleteStackStep.class);
    engineSteps.put(CloudformationCreateStackStep.STEP_TYPE, CloudformationCreateStackStep.class);
    engineSteps.put(CloudformationRollbackStep.STEP_TYPE, CloudformationRollbackStep.class);
    engineSteps.put(ServerlessAwsLambdaDeployStep.STEP_TYPE, ServerlessAwsLambdaDeployStep.class);
    engineSteps.put(ServerlessAwsLambdaRollbackStep.STEP_TYPE, ServerlessAwsLambdaRollbackStep.class);
    engineSteps.put(IndividualConfigFileStep.STEP_TYPE, IndividualConfigFileStep.class);
    engineSteps.put(ConfigFilesStep.STEP_TYPE, ConfigFilesStep.class);
    engineSteps.put(ConfigFilesStepV2.STEP_TYPE, ConfigFilesStepV2.class);
    engineSteps.put(CommandStep.STEP_TYPE, CommandStep.class);
    engineSteps.put(AzureWebAppSlotDeploymentStep.STEP_TYPE, AzureWebAppSlotDeploymentStep.class);
    engineSteps.put(AzureWebAppTrafficShiftStep.STEP_TYPE, AzureWebAppTrafficShiftStep.class);
    engineSteps.put(AzureWebAppSwapSlotStep.STEP_TYPE, AzureWebAppSwapSlotStep.class);
    engineSteps.put(AzureWebAppRollbackStep.STEP_TYPE, AzureWebAppRollbackStep.class);
    engineSteps.put(StartupCommandStep.STEP_TYPE, StartupCommandStep.class);
    engineSteps.put(AzureServiceSettingsStep.STEP_TYPE, AzureServiceSettingsStep.class);
    engineSteps.put(ElastigroupServiceSettingsStep.STEP_TYPE, ElastigroupServiceSettingsStep.class);
    engineSteps.put(ApplicationSettingsStep.STEP_TYPE, ApplicationSettingsStep.class);
    engineSteps.put(ConnectionStringsStep.STEP_TYPE, ConnectionStringsStep.class);
    engineSteps.putAll(NGCommonUtilStepsRegistrar.getEngineSteps());
    engineSteps.put(GitopsClustersStep.STEP_TYPE, GitopsClustersStep.class);
    engineSteps.put(JenkinsBuildStep.STEP_TYPE, JenkinsBuildStep.class);
    engineSteps.put(JenkinsBuildStepV2.STEP_TYPE, JenkinsBuildStepV2.class);
    // ECS
    engineSteps.put(EcsRollingDeployStep.STEP_TYPE, EcsRollingDeployStep.class);
    engineSteps.put(EcsRollingRollbackStep.STEP_TYPE, EcsRollingRollbackStep.class);
    engineSteps.put(EcsCanaryDeployStep.STEP_TYPE, EcsCanaryDeployStep.class);
    engineSteps.put(EcsCanaryDeleteStep.STEP_TYPE, EcsCanaryDeleteStep.class);
    engineSteps.put(EcsBlueGreenCreateServiceStep.STEP_TYPE, EcsBlueGreenCreateServiceStep.class);
    engineSteps.put(EcsBlueGreenSwapTargetGroupsStep.STEP_TYPE, EcsBlueGreenSwapTargetGroupsStep.class);
    engineSteps.put(EcsBlueGreenRollbackStep.STEP_TYPE, EcsBlueGreenRollbackStep.class);
    engineSteps.put(EcsRunTaskStep.STEP_TYPE, EcsRunTaskStep.class);

    engineSteps.put(AzureCreateARMResourceStep.STEP_TYPE, AzureCreateARMResourceStep.class);
    engineSteps.put(MultiDeploymentSpawnerStep.STEP_TYPE, MultiDeploymentSpawnerStep.class);
    engineSteps.put(AzureCreateBPStep.STEP_TYPE, AzureCreateBPStep.class);
    engineSteps.put(AzureARMRollbackStep.STEP_TYPE, AzureARMRollbackStep.class);
    engineSteps.put(FetchInstanceScriptStep.STEP_TYPE, FetchInstanceScriptStep.class);
    engineSteps.put(ShellScriptProvisionStep.STEP_TYPE, ShellScriptProvisionStep.class);

    // Chaos
    // TODO : Enable this for UI
    engineSteps.put(ChaosStep.STEP_TYPE, ChaosStep.class);

    engineSteps.put(ElastigroupDeployStep.STEP_TYPE, ElastigroupDeployStep.class);
    engineSteps.put(ElastigroupSetupStep.STEP_TYPE, ElastigroupSetupStep.class);
    engineSteps.put(TerragruntPlanStep.STEP_TYPE, TerragruntPlanStep.class);
    engineSteps.put(TerragruntApplyStep.STEP_TYPE, TerragruntApplyStep.class);
    engineSteps.put(TerragruntDestroyStep.STEP_TYPE, TerragruntDestroyStep.class);
    engineSteps.put(TerragruntRollbackStep.STEP_TYPE, TerragruntRollbackStep.class);
    engineSteps.put(ElastigroupBGStageSetupStep.STEP_TYPE, ElastigroupBGStageSetupStep.class);
    engineSteps.put(ElastigroupSwapRouteStep.STEP_TYPE, ElastigroupSwapRouteStep.class);
    engineSteps.put(ElastigroupRollbackStep.STEP_TYPE, ElastigroupRollbackStep.class);

    // Asg
    engineSteps.put(AsgCanaryDeployStep.STEP_TYPE, AsgCanaryDeployStep.class);
    engineSteps.put(AsgCanaryDeleteStep.STEP_TYPE, AsgCanaryDeleteStep.class);
    engineSteps.put(AsgRollingDeployStep.STEP_TYPE, AsgRollingDeployStep.class);
    engineSteps.put(AsgRollingRollbackStep.STEP_TYPE, AsgRollingRollbackStep.class);
    engineSteps.put(AsgBlueGreenSwapServiceStep.STEP_TYPE, AsgBlueGreenSwapServiceStep.class);
    engineSteps.put(AsgBlueGreenDeployStep.STEP_TYPE, AsgBlueGreenDeployStep.class);
    engineSteps.put(AsgBlueGreenRollbackStep.STEP_TYPE, AsgBlueGreenRollbackStep.class);

    // TAS
    engineSteps.put(TasCanaryAppSetupStep.STEP_TYPE, TasCanaryAppSetupStep.class);
    engineSteps.put(TasBGAppSetupStep.STEP_TYPE, TasBGAppSetupStep.class);
    engineSteps.put(TasBasicAppSetupStep.STEP_TYPE, TasBasicAppSetupStep.class);
    engineSteps.put(TasCommandStep.STEP_TYPE, TasCommandStep.class);
    engineSteps.put(TasSwapRoutesStep.STEP_TYPE, TasSwapRoutesStep.class);
    engineSteps.put(TasSwapRollbackStep.STEP_TYPE, TasSwapRollbackStep.class);
    engineSteps.put(TasAppResizeStep.STEP_TYPE, TasAppResizeStep.class);
    engineSteps.put(TasRollbackStep.STEP_TYPE, TasRollbackStep.class);
    engineSteps.put(TasRollingDeployStep.STEP_TYPE, TasRollingDeployStep.class);
    engineSteps.put(TasRollingRollbackStep.STEP_TYPE, TasRollingRollbackStep.class);
    engineSteps.put(TasRouteMappingStep.STEP_TYPE, TasRouteMappingStep.class);

    engineSteps.put(GoogleFunctionsDeployStep.STEP_TYPE, GoogleFunctionsDeployStep.class);
    engineSteps.put(GoogleFunctionsDeployWithoutTrafficStep.STEP_TYPE, GoogleFunctionsDeployWithoutTrafficStep.class);
    engineSteps.put(GoogleFunctionsTrafficShiftStep.STEP_TYPE, GoogleFunctionsTrafficShiftStep.class);
    engineSteps.put(GoogleFunctionsRollbackStep.STEP_TYPE, GoogleFunctionsRollbackStep.class);
    engineSteps.put(GoogleFunctionsGenOneDeployStep.STEP_TYPE, GoogleFunctionsGenOneDeployStep.class);
    engineSteps.put(GoogleFunctionsGenOneRollbackStep.STEP_TYPE, GoogleFunctionsGenOneRollbackStep.class);
    engineSteps.put(TerraformCloudRunStep.STEP_TYPE, TerraformCloudRunStep.class);
    engineSteps.put(BambooBuildStep.STEP_TYPE, BambooBuildStep.class);
    engineSteps.put(TerraformCloudRollbackStep.STEP_TYPE, TerraformCloudRollbackStep.class);

    // AWS Lambda
    engineSteps.put(AwsLambdaDeployStep.STEP_TYPE, AwsLambdaDeployStep.class);
    engineSteps.put(AwsLambdaRollbackStep.STEP_TYPE, AwsLambdaRollbackStep.class);

    // AWS SAM
    engineSteps.put(AwsSamDeployStep.STEP_TYPE, AwsSamDeployStep.class);
    engineSteps.put(AwsSamBuildStep.STEP_TYPE, AwsSamBuildStep.class);
    engineSteps.put(AwsSamRollbackStep.STEP_TYPE, AwsSamRollbackStep.class);
    engineSteps.put(DownloadManifestsStep.STEP_TYPE, DownloadManifestsStep.class);

    // Service Hooks
    engineSteps.put(ServiceHooksStep.STEP_TYPE, ServiceHooksStep.class);

    // Blue Green Stage Scale Down
    engineSteps.put(K8sBGStageScaleDownStep.STEP_TYPE, K8sBGStageScaleDownStep.class);

    engineSteps.put(ServerlessAwsLambdaPrepareRollbackV2Step.STEP_TYPE, ServerlessAwsLambdaPrepareRollbackV2Step.class);
    engineSteps.put(ServerlessAwsLambdaRollbackV2Step.STEP_TYPE, ServerlessAwsLambdaRollbackV2Step.class);
    engineSteps.put(ServerlessAwsLambdaDeployV2Step.STEP_TYPE, ServerlessAwsLambdaDeployV2Step.class);

    engineSteps.put(TerraformPlanStepV2.STEP_TYPE, TerraformPlanStepV2.class);
    engineSteps.put(TerraformApplyStepV2.STEP_TYPE, TerraformApplyStepV2.class);
    engineSteps.put(TerraformDestroyStepV2.STEP_TYPE, TerraformDestroyStepV2.class);
    engineSteps.put(TerraformRollbackStepV2.STEP_TYPE, TerraformRollbackStepV2.class);

    engineSteps.put(ServerlessAwsLambdaPackageV2Step.STEP_TYPE, ServerlessAwsLambdaPackageV2Step.class);

    // AWS CDK
    engineSteps.put(AwsCdkBootstrapStep.STEP_TYPE, AwsCdkBootstrapStep.class);
    engineSteps.put(AwsCdkSynthStep.STEP_TYPE, AwsCdkSynthStep.class);
    engineSteps.put(AwsCdkDiffStep.STEP_TYPE, AwsCdkDiffStep.class);
    engineSteps.put(AwsCdkDeployStep.STEP_TYPE, AwsCdkDeployStep.class);
    engineSteps.put(AwsCdkDestroyStep.STEP_TYPE, AwsCdkDestroyStep.class);

    return engineSteps;
  }
}
