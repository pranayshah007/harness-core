/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.advisers.RollbackCustomStepParameters;
import io.harness.cdng.artifact.bean.artifactsource.DockerArtifactSource;
import io.harness.cdng.artifact.steps.beans.ArtifactStepParameters;
import io.harness.cdng.aws.asg.AsgCanaryDeleteStepInfo;
import io.harness.cdng.aws.asg.AsgCanaryDeployStepInfo;
import io.harness.cdng.aws.lambda.AwsLambdaStepExceptionPassThroughData;
import io.harness.cdng.aws.lambda.AwsLambdaStepPassThroughData;
import io.harness.cdng.aws.lambda.rollback.AwsLambdaRollbackStepInfo;
import io.harness.cdng.aws.lambda.rollback.AwsLambdaRollbackStepParameters;
import io.harness.cdng.azure.webapp.ApplicationSettingsParameters;
import io.harness.cdng.azure.webapp.ConnectionStringsParameters;
import io.harness.cdng.azure.webapp.StartupCommandParameters;
import io.harness.cdng.bamboo.BambooBuildStepInfo;
import io.harness.cdng.chaos.ChaosStepNotifyData;
import io.harness.cdng.configfile.ConfigFilesOutcome;
import io.harness.cdng.configfile.steps.ConfigFileStepParameters;
import io.harness.cdng.customDeployment.FetchInstanceScriptStepInfo;
import io.harness.cdng.customDeployment.FetchInstanceScriptStepParameters;
import io.harness.cdng.elastigroup.ElastigroupBGStageSetupStepInfo;
import io.harness.cdng.elastigroup.ElastigroupSetupStepInfo;
import io.harness.cdng.elastigroup.ElastigroupSwapRouteStepInfo;
import io.harness.cdng.elastigroup.deploy.ElastigroupDeployStepInfo;
import io.harness.cdng.elastigroup.rollback.ElastigroupRollbackStepInfo;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.gitops.CreatePRStepInfo;
import io.harness.cdng.gitops.CreatePRStepParams;
import io.harness.cdng.gitops.MergePRStepInfo;
import io.harness.cdng.gitops.MergePRStepParams;
import io.harness.cdng.gitops.UpdateReleaseRepoStepInfo;
import io.harness.cdng.gitops.UpdateReleaseRepoStepParams;
import io.harness.cdng.gitops.beans.FetchLinkedAppsStepParams;
import io.harness.cdng.gitops.beans.GitOpsLinkedAppsOutcome;
import io.harness.cdng.gitops.syncstep.SyncResponse;
import io.harness.cdng.gitops.syncstep.SyncStepOutcome;
import io.harness.cdng.helm.HelmDeployStepInfo;
import io.harness.cdng.helm.HelmDeployStepParams;
import io.harness.cdng.helm.rollback.HelmRollbackStepInfo;
import io.harness.cdng.helm.rollback.HelmRollbackStepParams;
import io.harness.cdng.hooks.ServiceHookAction;
import io.harness.cdng.hooks.ServiceHookType;
import io.harness.cdng.hooks.steps.ServiceHooksOutcome;
import io.harness.cdng.infra.InfraSectionStepParameters;
import io.harness.cdng.infra.InfraUseFromStage;
import io.harness.cdng.infra.InfrastructureDef;
import io.harness.cdng.infra.steps.InfraStepParameters;
import io.harness.cdng.infra.yaml.AwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.AwsSamInfrastructure;
import io.harness.cdng.infra.yaml.GoogleFunctionsInfrastructure;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildStepInfo;
import io.harness.cdng.k8s.DeleteResourcesWrapper;
import io.harness.cdng.k8s.K8sBlueGreenOutcome;
import io.harness.cdng.k8s.K8sCanaryOutcome;
import io.harness.cdng.k8s.K8sCanaryStepInfo;
import io.harness.cdng.k8s.K8sCanaryStepParameters;
import io.harness.cdng.k8s.K8sDeleteStepInfo;
import io.harness.cdng.k8s.K8sDeleteStepParameters;
import io.harness.cdng.k8s.K8sDryRunManifestOutcome;
import io.harness.cdng.k8s.K8sDryRunManifestStepInfo;
import io.harness.cdng.k8s.K8sDryRunManifestStepParameters;
import io.harness.cdng.k8s.K8sInstanceUnitType;
import io.harness.cdng.k8s.K8sRollingOutcome;
import io.harness.cdng.k8s.K8sRollingRollbackOutcome;
import io.harness.cdng.k8s.K8sRollingRollbackStepInfo;
import io.harness.cdng.k8s.K8sRollingRollbackStepParameters;
import io.harness.cdng.k8s.K8sRollingStepInfo;
import io.harness.cdng.k8s.K8sRollingStepParameters;
import io.harness.cdng.k8s.K8sScaleStepInfo;
import io.harness.cdng.k8s.K8sScaleStepParameter;
import io.harness.cdng.k8s.K8sStepPassThroughData;
import io.harness.cdng.k8s.beans.CustomFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.steps.ManifestStepParameters;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.pipeline.beans.DeploymentStageStepParameters;
import io.harness.cdng.pipeline.beans.RollbackNode;
import io.harness.cdng.pipeline.beans.RollbackOptionalChildChainStepParameters;
import io.harness.cdng.pipeline.executions.CDAccountExecutionMetadata;
import io.harness.cdng.provision.azure.AzureARMRollbackStepInfo;
import io.harness.cdng.provision.azure.AzureCreateARMResourceStepInfo;
import io.harness.cdng.provision.azure.AzureCreateBPStepInfo;
import io.harness.cdng.provision.cloudformation.CloudformationCreateStackStepInfo;
import io.harness.cdng.provision.cloudformation.CloudformationDeleteStackStepInfo;
import io.harness.cdng.provision.cloudformation.CloudformationRollbackStepInfo;
import io.harness.cdng.provision.terraform.TerraformApplyStepInfo;
import io.harness.cdng.provision.terraform.TerraformPlanStepInfo;
import io.harness.cdng.provision.terraformcloud.TerraformCloudRollbackStepInfo;
import io.harness.cdng.provision.terraformcloud.TerraformCloudRunStepInfo;
import io.harness.cdng.provision.terragrunt.TerragruntApplyStepInfo;
import io.harness.cdng.provision.terragrunt.TerragruntDestroyStepInfo;
import io.harness.cdng.provision.terragrunt.TerragruntPlanStepInfo;
import io.harness.cdng.provision.terragrunt.TerragruntRollbackStepInfo;
import io.harness.cdng.serverless.ServerlessAwsLambdaDeployStepInfo;
import io.harness.cdng.serverless.ServerlessAwsLambdaDeployStepParameters;
import io.harness.cdng.serverless.ServerlessAwsLambdaRollbackDataOutcome;
import io.harness.cdng.serverless.ServerlessAwsLambdaRollbackStepInfo;
import io.harness.cdng.serverless.ServerlessAwsLambdaRollbackStepParameters;
import io.harness.cdng.serverless.ServerlessFetchFileOutcome;
import io.harness.cdng.serverless.ServerlessStepPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessExecutionPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessGitFetchFailurePassThroughData;
import io.harness.cdng.serverless.beans.ServerlessStepExceptionPassThroughData;
import io.harness.cdng.service.steps.ServiceStepParameters;
import io.harness.cdng.ssh.CommandStepInfo;
import io.harness.cdng.tas.TasAppResizeStepInfo;
import io.harness.cdng.tas.TasAppResizeStepParameters;
import io.harness.cdng.tas.TasBGAppSetupStepInfo;
import io.harness.cdng.tas.TasBGAppSetupStepParameters;
import io.harness.cdng.tas.TasBasicAppSetupStepInfo;
import io.harness.cdng.tas.TasBasicAppSetupStepParameters;
import io.harness.cdng.tas.TasCanaryAppSetupStepInfo;
import io.harness.cdng.tas.TasCanaryAppSetupStepParameters;
import io.harness.cdng.tas.TasCommandStepInfo;
import io.harness.cdng.tas.TasCommandStepParameters;
import io.harness.cdng.tas.TasRollbackStepInfo;
import io.harness.cdng.tas.TasRollbackStepParameters;
import io.harness.cdng.tas.TasRollingDeployStepInfo;
import io.harness.cdng.tas.TasRollingDeployStepParameters;
import io.harness.cdng.tas.TasRollingRollbackStepInfo;
import io.harness.cdng.tas.TasRollingRollbackStepParameters;
import io.harness.cdng.tas.TasRouteMappingStepInfo;
import io.harness.cdng.tas.TasRouteMappingStepParameters;
import io.harness.cdng.tas.TasSwapRollbackStepInfo;
import io.harness.cdng.tas.TasSwapRollbackStepParameters;
import io.harness.cdng.tas.TasSwapRoutesStepInfo;
import io.harness.cdng.tas.TasSwapRoutesStepParameters;
import io.harness.cdng.tasks.manifestFetch.step.ManifestFetchOutcome;
import io.harness.cdng.tasks.manifestFetch.step.ManifestFetchParameters;
import io.harness.gitops.models.Application;
import io.harness.serializer.KryoRegistrar;
import io.harness.telemetry.beans.CdTelemetrySentStatus;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(CDC)
public class NGKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(CreatePRStepInfo.class, 13007);
    kryo.register(CreatePRStepParams.class, 13008);
    kryo.register(UpdateReleaseRepoStepInfo.class, 13009);
    kryo.register(UpdateReleaseRepoStepParams.class, 13010);

    kryo.register(ArtifactStepParameters.class, 8001);
    kryo.register(ServiceStepParameters.class, 8008);
    kryo.register(DockerArtifactSource.class, 8017);
    kryo.register(ManifestFetchOutcome.class, 8027);
    kryo.register(EnvironmentYaml.class, 8029);
    kryo.register(K8sRollingOutcome.class, 8034);
    kryo.register(K8sRollingRollbackOutcome.class, 8054);
    kryo.register(InfraUseFromStage.class, 8039);
    kryo.register(InfraUseFromStage.Overrides.class, 8040);
    kryo.register(InfraStepParameters.class, 8042);

    kryo.register(DeploymentStageStepParameters.class, 8047);
    kryo.register(K8sRollingRollbackStepInfo.class, 8049);
    kryo.register(K8sRollingRollbackStepParameters.class, 8050);
    kryo.register(K8sRollingStepInfo.class, 8051);
    kryo.register(K8sRollingStepParameters.class, 8052);
    kryo.register(ManifestFetchParameters.class, 8053);
    kryo.register(K8sStepPassThroughData.class, 8056);

    // Starting using 8100 series
    kryo.register(PipelineInfrastructure.class, 8101);
    kryo.register(InfrastructureDef.class, 8102);
    kryo.register(RollbackOptionalChildChainStepParameters.class, 8108);
    kryo.register(RollbackNode.class, 8109);

    // Starting using 12500 series as 8100 series is also used in 400-rest
    kryo.register(K8sBlueGreenOutcome.class, 12500);

    kryo.register(K8sInstanceUnitType.class, 12512);
    kryo.register(K8sScaleStepInfo.class, 12513);
    kryo.register(K8sScaleStepParameter.class, 12514);
    kryo.register(K8sCanaryOutcome.class, 12515);
    kryo.register(K8sCanaryStepInfo.class, 12516);
    kryo.register(K8sCanaryStepParameters.class, 12517);
    kryo.register(DeleteResourcesWrapper.class, 12519);
    kryo.register(K8sDeleteStepParameters.class, 12520);
    kryo.register(K8sDeleteStepInfo.class, 12521);
    kryo.register(GitFetchResponsePassThroughData.class, 12522);

    kryo.register(RollbackCustomStepParameters.class, 12540);
    kryo.register(TerraformApplyStepInfo.class, 12541);
    kryo.register(TerraformPlanStepInfo.class, 12543);
    kryo.register(HelmValuesFetchResponsePassThroughData.class, 12544);
    kryo.register(StepExceptionPassThroughData.class, 12545);
    kryo.register(ManifestStepParameters.class, 12559);

    kryo.register(HelmDeployStepInfo.class, 13001);
    kryo.register(HelmDeployStepParams.class, 13002);
    kryo.register(HelmRollbackStepInfo.class, 13004);
    kryo.register(HelmRollbackStepParams.class, 13005);
    kryo.register(CdTelemetrySentStatus.class, 13006);

    kryo.register(K8sExecutionPassThroughData.class, 12546);
    kryo.register(CDAccountExecutionMetadata.class, 12550);

    kryo.register(InfraSectionStepParameters.class, 12552);
    kryo.register(CloudformationCreateStackStepInfo.class, 12566);
    kryo.register(CloudformationDeleteStackStepInfo.class, 12567);

    kryo.register(ServerlessAwsLambdaDeployStepInfo.class, 12571);
    kryo.register(ServerlessAwsLambdaDeployStepParameters.class, 12572);
    kryo.register(ServerlessStepPassThroughData.class, 12573);
    kryo.register(ServerlessAwsLambdaRollbackStepInfo.class, 12574);
    kryo.register(ServerlessAwsLambdaRollbackStepParameters.class, 12575);
    kryo.register(ServerlessExecutionPassThroughData.class, 12577);

    kryo.register(ServerlessStepExceptionPassThroughData.class, 12580);
    kryo.register(ServerlessGitFetchFailurePassThroughData.class, 12581);
    kryo.register(ServerlessFetchFileOutcome.class, 12582);
    kryo.register(ServerlessAwsLambdaRollbackDataOutcome.class, 12583);
    kryo.register(CloudformationRollbackStepInfo.class, 12584);
    kryo.register(ConfigFileStepParameters.class, 12585);
    kryo.register(CommandStepInfo.class, 12600);
    kryo.register(StartupCommandParameters.class, 12601);
    kryo.register(ApplicationSettingsParameters.class, 12602);
    kryo.register(ConnectionStringsParameters.class, 12603);
    kryo.register(JenkinsBuildStepInfo.class, 12700);
    kryo.register(MergePRStepParams.class, 12604);
    kryo.register(MergePRStepInfo.class, 12605);
    kryo.register(CustomFetchResponsePassThroughData.class, 12705);
    kryo.register(ConfigFilesOutcome.class, 12608);

    kryo.register(AzureCreateARMResourceStepInfo.class, 12609);
    kryo.register(AzureCreateBPStepInfo.class, 12610);

    kryo.register(AzureARMRollbackStepInfo.class, 12611);

    kryo.register(FetchInstanceScriptStepInfo.class, 12614);
    kryo.register(FetchInstanceScriptStepParameters.class, 12615);
    kryo.register(ChaosStepNotifyData.class, 12616);
    kryo.register(ElastigroupDeployStepInfo.class, 12617);
    kryo.register(ElastigroupRollbackStepInfo.class, 12618);
    kryo.register(ElastigroupSetupStepInfo.class, 12619);
    kryo.register(FetchLinkedAppsStepParams.class, 12620);
    kryo.register(GitOpsLinkedAppsOutcome.class, 12621);
    kryo.register(TerragruntPlanStepInfo.class, 12622);
    kryo.register(TerragruntApplyStepInfo.class, 12623);
    kryo.register(TerragruntDestroyStepInfo.class, 12624);
    kryo.register(TerragruntRollbackStepInfo.class, 12625);
    kryo.register(AsgCanaryDeployStepInfo.class, 12626);
    kryo.register(AsgCanaryDeleteStepInfo.class, 12627);
    kryo.register(TasCanaryAppSetupStepInfo.class, 12628);
    kryo.register(TasCanaryAppSetupStepParameters.class, 12629);
    kryo.register(TasBGAppSetupStepInfo.class, 12630);
    kryo.register(TasBGAppSetupStepParameters.class, 12631);
    kryo.register(TasBasicAppSetupStepInfo.class, 12632);
    kryo.register(TasBasicAppSetupStepParameters.class, 12633);
    kryo.register(TasCommandStepInfo.class, 12634);
    kryo.register(TasCommandStepParameters.class, 12635);
    kryo.register(TasSwapRoutesStepInfo.class, 12636);
    kryo.register(TasSwapRoutesStepParameters.class, 12637);
    kryo.register(TasSwapRollbackStepInfo.class, 12638);
    kryo.register(TasSwapRollbackStepParameters.class, 12639);
    kryo.register(TasAppResizeStepInfo.class, 12640);
    kryo.register(TasAppResizeStepParameters.class, 12641);
    kryo.register(TasRollbackStepInfo.class, 12642);
    kryo.register(TasRollbackStepParameters.class, 12643);
    kryo.register(ElastigroupBGStageSetupStepInfo.class, 12800);
    kryo.register(ElastigroupSwapRouteStepInfo.class, 12801);
    kryo.register(TasRollingDeployStepInfo.class, 12802);
    kryo.register(TasRollingDeployStepParameters.class, 12803);
    kryo.register(TasRollingRollbackStepInfo.class, 12804);
    kryo.register(TasRollingRollbackStepParameters.class, 12805);

    kryo.register(K8sDryRunManifestStepInfo.class, 12644);
    kryo.register(K8sDryRunManifestStepParameters.class, 12645);
    kryo.register(K8sDryRunManifestOutcome.class, 12646);
    kryo.register(TerraformCloudRunStepInfo.class, 12656);
    kryo.register(GoogleFunctionsInfrastructure.class, 12657);
    kryo.register(AwsSamInfrastructure.class, 12658);
    kryo.register(AwsLambdaInfrastructure.class, 12659);
    kryo.register(TerraformCloudRollbackStepInfo.class, 12660);
    kryo.register(AwsLambdaStepPassThroughData.class, 12661);
    kryo.register(AwsLambdaStepExceptionPassThroughData.class, 12662);
    kryo.register(BambooBuildStepInfo.class, 1800700);
    kryo.register(AwsLambdaRollbackStepInfo.class, 12663);
    kryo.register(AwsLambdaRollbackStepParameters.class, 12664);
    kryo.register(SyncStepOutcome.class, 12665);
    kryo.register(TasRouteMappingStepInfo.class, 12666);
    kryo.register(TasRouteMappingStepParameters.class, 12667);
    kryo.register(SyncResponse.class, 12668);
    kryo.register(Application.class, 12669);

    kryo.register(ServiceHooksOutcome.class, 12671);
    kryo.register(ServiceHookAction.class, 12672);
    kryo.register(InlineStoreConfig.class, 12673);
    kryo.register(ServiceHookType.class, 12674);
  }
}
