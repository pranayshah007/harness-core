/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.app;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.beans.OrchestrationWorkflowType.BASIC;
import static io.harness.beans.OrchestrationWorkflowType.BLUE_GREEN;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.beans.OrchestrationWorkflowType.CANARY;
import static io.harness.beans.OrchestrationWorkflowType.MULTI_SERVICE;
import static io.harness.beans.OrchestrationWorkflowType.ROLLING;

import static software.wings.beans.InfrastructureMappingType.AWS_AMI;
import static software.wings.beans.InfrastructureMappingType.AWS_AWS_CODEDEPLOY;
import static software.wings.beans.InfrastructureMappingType.AWS_AWS_LAMBDA;
import static software.wings.beans.InfrastructureMappingType.AWS_ECS;
import static software.wings.beans.InfrastructureMappingType.AWS_SSH;
import static software.wings.beans.InfrastructureMappingType.AZURE_INFRA;
import static software.wings.beans.InfrastructureMappingType.AZURE_KUBERNETES;
import static software.wings.beans.InfrastructureMappingType.DIRECT_KUBERNETES;
import static software.wings.beans.InfrastructureMappingType.GCP_KUBERNETES;
import static software.wings.beans.InfrastructureMappingType.PCF_PCF;
import static software.wings.beans.InfrastructureMappingType.PHYSICAL_DATA_CENTER_SSH;
import static software.wings.beans.InfrastructureMappingType.PHYSICAL_DATA_CENTER_WINRM;
import static software.wings.beans.InfrastructureType.AWS_INSTANCE;
import static software.wings.beans.InfrastructureType.AZURE_SSH;
import static software.wings.beans.InfrastructureType.AZURE_VMSS;
import static software.wings.beans.InfrastructureType.AZURE_WEBAPP;
import static software.wings.beans.InfrastructureType.CUSTOM_INFRASTRUCTURE;
import static software.wings.beans.InfrastructureType.GCP_KUBERNETES_ENGINE;
import static software.wings.beans.InfrastructureType.PCF_INFRASTRUCTURE;
import static software.wings.beans.InfrastructureType.PHYSICAL_INFRA;
import static software.wings.beans.InfrastructureType.PHYSICAL_INFRA_WINRM;
import static software.wings.beans.artifact.ArtifactStreamType.ACR;
import static software.wings.beans.artifact.ArtifactStreamType.AMAZON_S3;
import static software.wings.beans.artifact.ArtifactStreamType.AMI;
import static software.wings.beans.artifact.ArtifactStreamType.ARTIFACTORY;
import static software.wings.beans.artifact.ArtifactStreamType.AZURE_ARTIFACTS;
import static software.wings.beans.artifact.ArtifactStreamType.AZURE_MACHINE_IMAGE;
import static software.wings.beans.artifact.ArtifactStreamType.BAMBOO;
import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;
import static software.wings.beans.artifact.ArtifactStreamType.DOCKER;
import static software.wings.beans.artifact.ArtifactStreamType.ECR;
import static software.wings.beans.artifact.ArtifactStreamType.GCR;
import static software.wings.beans.artifact.ArtifactStreamType.GCS;
import static software.wings.beans.artifact.ArtifactStreamType.JENKINS;
import static software.wings.beans.artifact.ArtifactStreamType.NEXUS;
import static software.wings.beans.artifact.ArtifactStreamType.SFTP;
import static software.wings.beans.artifact.ArtifactStreamType.SMB;
import static software.wings.beans.trigger.TriggerConditionType.NEW_ARTIFACT;
import static software.wings.beans.trigger.TriggerConditionType.NEW_MANIFEST;
import static software.wings.beans.trigger.TriggerConditionType.PIPELINE_COMPLETION;
import static software.wings.beans.trigger.TriggerConditionType.SCHEDULED;
import static software.wings.beans.trigger.TriggerConditionType.WEBHOOK;

import io.harness.annotations.dev.OwnedBy;
import io.harness.governance.BlackoutWindowFilterType;
import io.harness.governance.EnvironmentFilter.EnvironmentFilterType;

import software.wings.api.DeploymentType;
import software.wings.beans.InfrastructureProvisionerType;
import software.wings.beans.InfrastructureType;
import software.wings.beans.trigger.PayloadSource;
import software.wings.common.TemplateConstants;
import software.wings.service.impl.yaml.AppYamlResourceServiceImpl;
import software.wings.service.impl.yaml.YamlArtifactStreamServiceImpl;
import software.wings.service.impl.yaml.YamlDirectoryServiceImpl;
import software.wings.service.impl.yaml.YamlGitServiceImpl;
import software.wings.service.impl.yaml.YamlHistoryServiceImpl;
import software.wings.service.impl.yaml.YamlPushServiceImpl;
import software.wings.service.impl.yaml.YamlResourceServiceImpl;
import software.wings.service.impl.yaml.handler.CloudProviderInfrastructure.CloudProviderInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AwsAmiInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AwsEcsInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AwsInstanceInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AwsLambdaInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AzureInstanceInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AzureKubernetesServiceYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AzureVMSSInfraYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AzureWebAppInfraYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.CodeDeployInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.CustomInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.DirectKubernetesInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.GoogleKubernetesEngineYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.PcfInfraStructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.PhysicalInfraWinrmYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.PhysicalInfraYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.RancherKubernetesInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.handler.artifactstream.AcrArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.AmazonS3ArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.AmiArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.ArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.ArtifactoryArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.AzureArtifactsArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.AzureMachineImageArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.BambooArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.CustomArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.DockerArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.EcrArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.GcrArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.GcsArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.JenkinsArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.NexusArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.SftpArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.artifactstream.SmbArtifactStreamYamlHandler;
import software.wings.service.impl.yaml.handler.deploymentspec.DeploymentSpecificationYamlHandler;
import software.wings.service.impl.yaml.handler.deploymentspec.container.EcsContainerTaskYamlHandler;
import software.wings.service.impl.yaml.handler.deploymentspec.container.EcsServiceSpecificationYamlHandler;
import software.wings.service.impl.yaml.handler.deploymentspec.container.HelmChartSpecificationYamlHandler;
import software.wings.service.impl.yaml.handler.deploymentspec.container.KubernetesContainerTaskYamlHandler;
import software.wings.service.impl.yaml.handler.deploymentspec.container.PcfServiceSpecificationYamlHandler;
import software.wings.service.impl.yaml.handler.deploymentspec.lambda.LambdaSpecificationYamlHandler;
import software.wings.service.impl.yaml.handler.deploymentspec.userdata.UserDataSpecificationYamlHandler;
import software.wings.service.impl.yaml.handler.governance.AllAppFilterYamlHandler;
import software.wings.service.impl.yaml.handler.governance.AllEnvFilterYamlHandler;
import software.wings.service.impl.yaml.handler.governance.AllNonProdEnvFilterYamlHandler;
import software.wings.service.impl.yaml.handler.governance.AllProdEnvFilterYamlHandler;
import software.wings.service.impl.yaml.handler.governance.ApplicationFilterYamlHandler;
import software.wings.service.impl.yaml.handler.governance.CustomAppFilterYamlHandler;
import software.wings.service.impl.yaml.handler.governance.CustomEnvFilterYamlHandler;
import software.wings.service.impl.yaml.handler.governance.EnvironmentFilterYamlHandler;
import software.wings.service.impl.yaml.handler.governance.GovernanceFreezeConfigYamlHandler;
import software.wings.service.impl.yaml.handler.governance.TimeRangeBasedFreezeConfigYamlHandler;
import software.wings.service.impl.yaml.handler.inframapping.AwsAmiInfraMappingYamlHandler;
import software.wings.service.impl.yaml.handler.inframapping.AwsInfraMappingYamlHandler;
import software.wings.service.impl.yaml.handler.inframapping.AwsLambdaInfraMappingYamlHandler;
import software.wings.service.impl.yaml.handler.inframapping.AzureInfraMappingYamlHandler;
import software.wings.service.impl.yaml.handler.inframapping.AzureKubernetesInfraMappingYamlHandler;
import software.wings.service.impl.yaml.handler.inframapping.CodeDeployInfraMappingYamlHandler;
import software.wings.service.impl.yaml.handler.inframapping.DirectKubernetesInfraMappingYamlHandler;
import software.wings.service.impl.yaml.handler.inframapping.EcsInfraMappingYamlHandler;
import software.wings.service.impl.yaml.handler.inframapping.GcpKubernetesInfraMappingYamlHandler;
import software.wings.service.impl.yaml.handler.inframapping.InfraMappingYamlHandler;
import software.wings.service.impl.yaml.handler.inframapping.PcfInfraMappingYamlHandler;
import software.wings.service.impl.yaml.handler.inframapping.PhysicalInfraMappingWinRmYamlHandler;
import software.wings.service.impl.yaml.handler.inframapping.PhysicalInfraMappingYamlHandler;
import software.wings.service.impl.yaml.handler.infraprovisioner.ARMInfrastructureProvisionerYamlHandler;
import software.wings.service.impl.yaml.handler.infraprovisioner.CloudFormationInfrastructureProvisionerYamlHandler;
import software.wings.service.impl.yaml.handler.infraprovisioner.InfrastructureProvisionerYamlHandler;
import software.wings.service.impl.yaml.handler.infraprovisioner.ShellScriptProvisionerYamlHandler;
import software.wings.service.impl.yaml.handler.infraprovisioner.TerraformInfrastructureProvisionerYamlHandler;
import software.wings.service.impl.yaml.handler.infraprovisioner.TerragruntInfrastructureProvisionerYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.AmazonS3HelmRepoConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.ArtifactServerYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.ArtifactoryConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.AzureArtifactsPATConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.AzureArtifactsYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.BambooConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.CustomArtifactServerConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.DockerRegistryConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.GcsHelmRepoConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.HelmRepoYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.HttpHelmRepoConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.JenkinsConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.NexusConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.OciHelmRepoConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.SftpConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.artifactserver.SmbConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.cloudprovider.AwsConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.cloudprovider.AzureConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.cloudprovider.CloudProviderYamlHandler;
import software.wings.service.impl.yaml.handler.setting.cloudprovider.GcpConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.cloudprovider.KubernetesClusterConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.cloudprovider.PcfConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.cloudprovider.PhysicalDataCenterConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.cloudprovider.RancherConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.collaborationprovider.CollaborationProviderYamlHandler;
import software.wings.service.impl.yaml.handler.setting.collaborationprovider.JiraConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.collaborationprovider.ServiceNowConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.collaborationprovider.SmtpConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.collaborationprovider.SpotInstConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.sourcerepoprovider.GitConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.sourcerepoprovider.SourceRepoProviderYamlHandler;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.APMConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.AppDynamicsConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.BugsnagConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.DatadogConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.DynaTraceConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.ElkConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.InstanaConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.JenkinsConfigVerificationYamlHandler;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.LogzConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.NewRelicConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.PrometheusConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.ScalyrConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.SplunkConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.SumoConfigYamlHandler;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.VerificationProviderYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.ArtifactSourceTemplateYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.CommandTemplateYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.CustomDeploymentTypeTemplateYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.HttpTemplateYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.PcfCommandTemplateYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.ShellScriptTemplateYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.TemplateLibraryYamlHandler;
import software.wings.service.impl.yaml.handler.trigger.BitbucketPayloadSourceYamlHandler;
import software.wings.service.impl.yaml.handler.trigger.CustomPayloadSourceYamlHandler;
import software.wings.service.impl.yaml.handler.trigger.GithubPayloadSourceYamlHandler;
import software.wings.service.impl.yaml.handler.trigger.GitlabPayloadSourceYamlHandler;
import software.wings.service.impl.yaml.handler.trigger.PayloadSourceYamlHandler;
import software.wings.service.impl.yaml.handler.trigger.TriggerConditionYamlHandler;
import software.wings.service.impl.yaml.handler.workflow.BasicWorkflowYamlHandler;
import software.wings.service.impl.yaml.handler.workflow.BlueGreenWorkflowYamlHandler;
import software.wings.service.impl.yaml.handler.workflow.BuildWorkflowYamlHandler;
import software.wings.service.impl.yaml.handler.workflow.CanaryWorkflowYamlHandler;
import software.wings.service.impl.yaml.handler.workflow.MultiServiceWorkflowYamlHandler;
import software.wings.service.impl.yaml.handler.workflow.RollingWorkflowYamlHandler;
import software.wings.service.impl.yaml.handler.workflow.WorkflowYamlHandler;
import software.wings.service.impl.yaml.service.YamlServiceImpl;
import software.wings.service.intfc.yaml.AppYamlResourceService;
import software.wings.service.intfc.yaml.YamlArtifactStreamService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.YamlHistoryService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.service.intfc.yaml.YamlResourceService;
import software.wings.service.intfc.yaml.sync.YamlService;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.StateType;
import software.wings.verification.APMCVConfigurationYamlHandler;
import software.wings.verification.AppDynamicsCVConfigurationYamlHandler;
import software.wings.verification.BugsnagCVConfigurationYamlHandler;
import software.wings.verification.CVConfigurationYamlHandler;
import software.wings.verification.CloudWatchCVConfigurationYamlHandler;
import software.wings.verification.CustomLogCVConfigurationYamlHandler;
import software.wings.verification.DatadogCvConfigurationYamlHandler;
import software.wings.verification.DatadogLogCVConfigurationYamlHandler;
import software.wings.verification.DynatraceCVConfigurationYamlHandler;
import software.wings.verification.ElkCVConfigurationYamlHandler;
import software.wings.verification.InstanaCVConfigurationYamlHandler;
import software.wings.verification.LogsCVConfigurationYamlHandler;
import software.wings.verification.NewRelicCVConfigurationYamlHandler;
import software.wings.verification.PrometheusCVConfigurationYamlHandler;
import software.wings.verification.SplunkCVConfigurationYamlHandler;
import software.wings.verification.StackDriverMetricsCVConfigurationYamlHandler;
import software.wings.verification.StackdriverCVConfigurationYamlHandler;
import software.wings.yaml.trigger.ArtifactTriggerConditionHandler;
import software.wings.yaml.trigger.ManifestTriggerConditionHandler;
import software.wings.yaml.trigger.PipelineTriggerConditionHandler;
import software.wings.yaml.trigger.ScheduledTriggerConditionHandler;
import software.wings.yaml.trigger.WebhookTriggerConditionHandler;

import com.google.inject.multibindings.MapBinder;

/**
 * Guice Module for initializing all yaml classes.
 *
 * @author rktummala on 10/17/17
 */
@OwnedBy(DX)
public class YamlModule extends CommandLibrarySharedModule {
  public YamlModule() {
    super(true);
  }

  /* (non-Javadoc)
   * @see com.google.inject.AbstractModule#configure()
   */
  @Override
  protected void configure() {
    super.configure();
    bind(YamlHistoryService.class).to(YamlHistoryServiceImpl.class);
    bind(YamlDirectoryService.class).to(YamlDirectoryServiceImpl.class);
    bind(YamlResourceService.class).to(YamlResourceServiceImpl.class);
    bind(AppYamlResourceService.class).to(AppYamlResourceServiceImpl.class);
    bind(YamlGitService.class).to(YamlGitServiceImpl.class);
    bind(YamlArtifactStreamService.class).to(YamlArtifactStreamServiceImpl.class);
    bind(YamlService.class).to(YamlServiceImpl.class);
    bind(YamlPushService.class).to(YamlPushServiceImpl.class);

    MapBinder<String, ArtifactStreamYamlHandler> artifactStreamYamlHelperMapBinder =
        MapBinder.newMapBinder(binder(), String.class, ArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(AMAZON_S3.name()).to(AmazonS3ArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(AMI.name()).to(AmiArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(AZURE_MACHINE_IMAGE.name())
        .to(AzureMachineImageArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(ARTIFACTORY.name()).to(ArtifactoryArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(BAMBOO.name()).to(BambooArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(DOCKER.name()).to(DockerArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(ECR.name()).to(EcrArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(GCR.name()).to(GcrArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(ACR.name()).to(AcrArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(JENKINS.name()).to(JenkinsArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(NEXUS.name()).to(NexusArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(GCS.name()).to(GcsArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(SMB.name()).to(SmbArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(SFTP.name()).to(SftpArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(CUSTOM.name()).to(CustomArtifactStreamYamlHandler.class);
    artifactStreamYamlHelperMapBinder.addBinding(AZURE_ARTIFACTS.name())
        .to(AzureArtifactsArtifactStreamYamlHandler.class);

    MapBinder<String, InfraMappingYamlHandler> infraMappingYamlHelperMapBinder =
        MapBinder.newMapBinder(binder(), String.class, InfraMappingYamlHandler.class);
    infraMappingYamlHelperMapBinder.addBinding(AWS_SSH.name()).to(AwsInfraMappingYamlHandler.class);
    infraMappingYamlHelperMapBinder.addBinding(AWS_AWS_CODEDEPLOY.name()).to(CodeDeployInfraMappingYamlHandler.class);
    infraMappingYamlHelperMapBinder.addBinding(AWS_AWS_LAMBDA.name()).to(AwsLambdaInfraMappingYamlHandler.class);
    infraMappingYamlHelperMapBinder.addBinding(AWS_AMI.name()).to(AwsAmiInfraMappingYamlHandler.class);
    infraMappingYamlHelperMapBinder.addBinding(DIRECT_KUBERNETES.name())
        .to(DirectKubernetesInfraMappingYamlHandler.class);
    infraMappingYamlHelperMapBinder.addBinding(AWS_ECS.name()).to(EcsInfraMappingYamlHandler.class);
    infraMappingYamlHelperMapBinder.addBinding(GCP_KUBERNETES.name()).to(GcpKubernetesInfraMappingYamlHandler.class);
    infraMappingYamlHelperMapBinder.addBinding(AZURE_KUBERNETES.name())
        .to(AzureKubernetesInfraMappingYamlHandler.class);
    infraMappingYamlHelperMapBinder.addBinding(AZURE_INFRA.name()).to(AzureInfraMappingYamlHandler.class);
    infraMappingYamlHelperMapBinder.addBinding(PHYSICAL_DATA_CENTER_SSH.name())
        .to(PhysicalInfraMappingYamlHandler.class);
    infraMappingYamlHelperMapBinder.addBinding(PHYSICAL_DATA_CENTER_WINRM.name())
        .to(PhysicalInfraMappingWinRmYamlHandler.class);
    infraMappingYamlHelperMapBinder.addBinding(PCF_PCF.name()).to(PcfInfraMappingYamlHandler.class);

    MapBinder<String, CloudProviderInfrastructureYamlHandler> cloudProviderInfrastructureYamlHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, CloudProviderInfrastructureYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(InfrastructureType.AWS_AMI)
        .to(AwsAmiInfrastructureYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(InfrastructureType.AWS_ECS)
        .to(AwsEcsInfrastructureYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(AWS_INSTANCE)
        .to(AwsInstanceInfrastructureYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(InfrastructureType.AWS_LAMBDA)
        .to(AwsLambdaInfrastructureYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(AZURE_SSH).to(
        AzureInstanceInfrastructureYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(InfrastructureType.AZURE_KUBERNETES)
        .to(AzureKubernetesServiceYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(InfrastructureType.CODE_DEPLOY)
        .to(CodeDeployInfrastructureYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(InfrastructureType.DIRECT_KUBERNETES)
        .to(DirectKubernetesInfrastructureYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(InfrastructureType.RANCHER_KUBERNETES)
        .to(RancherKubernetesInfrastructureYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(GCP_KUBERNETES_ENGINE)
        .to(GoogleKubernetesEngineYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(PCF_INFRASTRUCTURE)
        .to(PcfInfraStructureYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(PHYSICAL_INFRA).to(PhysicalInfraYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(PHYSICAL_INFRA_WINRM)
        .to(PhysicalInfraWinrmYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(AZURE_VMSS).to(AzureVMSSInfraYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(AZURE_WEBAPP).to(AzureWebAppInfraYamlHandler.class);
    cloudProviderInfrastructureYamlHandlerMapBinder.addBinding(CUSTOM_INFRASTRUCTURE)
        .to(CustomInfrastructureYamlHandler.class);

    MapBinder<String, DeploymentSpecificationYamlHandler> deploymentSpecYamlHelperMapBinder =
        MapBinder.newMapBinder(binder(), String.class, DeploymentSpecificationYamlHandler.class);
    deploymentSpecYamlHelperMapBinder.addBinding(DeploymentType.ECS.name()).to(EcsContainerTaskYamlHandler.class);
    deploymentSpecYamlHelperMapBinder.addBinding(YamlHandlerFactory.ECS_SERVICE_SPEC)
        .to(EcsServiceSpecificationYamlHandler.class);
    deploymentSpecYamlHelperMapBinder.addBinding(DeploymentType.KUBERNETES.name())
        .to(KubernetesContainerTaskYamlHandler.class);
    deploymentSpecYamlHelperMapBinder.addBinding(DeploymentType.HELM.name())
        .to(HelmChartSpecificationYamlHandler.class);
    deploymentSpecYamlHelperMapBinder.addBinding(DeploymentType.PCF.name())
        .to(PcfServiceSpecificationYamlHandler.class);
    deploymentSpecYamlHelperMapBinder.addBinding(DeploymentType.AWS_LAMBDA.name())
        .to(LambdaSpecificationYamlHandler.class);
    deploymentSpecYamlHelperMapBinder.addBinding(DeploymentType.AMI.name()).to(UserDataSpecificationYamlHandler.class);

    MapBinder<String, ArtifactServerYamlHandler> artifactServerYamlHelperMapBinder =
        MapBinder.newMapBinder(binder(), String.class, ArtifactServerYamlHandler.class);
    artifactServerYamlHelperMapBinder.addBinding(SettingVariableTypes.ARTIFACTORY.name())
        .to(ArtifactoryConfigYamlHandler.class);
    artifactServerYamlHelperMapBinder.addBinding(SettingVariableTypes.BAMBOO.name()).to(BambooConfigYamlHandler.class);
    artifactServerYamlHelperMapBinder.addBinding(SettingVariableTypes.DOCKER.name())
        .to(DockerRegistryConfigYamlHandler.class);

    artifactServerYamlHelperMapBinder.addBinding(SettingVariableTypes.JENKINS.name())
        .to(JenkinsConfigYamlHandler.class);
    artifactServerYamlHelperMapBinder.addBinding(SettingVariableTypes.NEXUS.name()).to(NexusConfigYamlHandler.class);
    artifactServerYamlHelperMapBinder.addBinding(SettingVariableTypes.SMB.name()).to(SmbConfigYamlHandler.class);
    artifactServerYamlHelperMapBinder.addBinding(SettingVariableTypes.SFTP.name()).to(SftpConfigYamlHandler.class);
    artifactServerYamlHelperMapBinder.addBinding(SettingVariableTypes.CUSTOM.name())
        .to(CustomArtifactServerConfigYamlHandler.class);

    MapBinder<String, HelmRepoYamlHandler> helmRepoYamlHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, HelmRepoYamlHandler.class);
    helmRepoYamlHandlerMapBinder.addBinding(SettingVariableTypes.HTTP_HELM_REPO.name())
        .to(HttpHelmRepoConfigYamlHandler.class);
    helmRepoYamlHandlerMapBinder.addBinding(SettingVariableTypes.OCI_HELM_REPO.name())
        .to(OciHelmRepoConfigYamlHandler.class);
    helmRepoYamlHandlerMapBinder.addBinding(SettingVariableTypes.AMAZON_S3_HELM_REPO.name())
        .to(AmazonS3HelmRepoConfigYamlHandler.class);
    helmRepoYamlHandlerMapBinder.addBinding(SettingVariableTypes.GCS_HELM_REPO.name())
        .to(GcsHelmRepoConfigYamlHandler.class);

    MapBinder<String, AzureArtifactsYamlHandler> azureArtifactsYamlHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, AzureArtifactsYamlHandler.class);
    azureArtifactsYamlHandlerMapBinder.addBinding(SettingVariableTypes.AZURE_ARTIFACTS_PAT.name())
        .to(AzureArtifactsPATConfigYamlHandler.class);

    MapBinder<String, VerificationProviderYamlHandler> verificationProviderYamlHelperMapBinder =
        MapBinder.newMapBinder(binder(), String.class, VerificationProviderYamlHandler.class);
    verificationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.JENKINS.name())
        .to(JenkinsConfigVerificationYamlHandler.class);
    verificationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.APP_DYNAMICS.name())
        .to(AppDynamicsConfigYamlHandler.class);
    verificationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.ELK.name()).to(ElkConfigYamlHandler.class);
    verificationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.LOGZ.name())
        .to(LogzConfigYamlHandler.class);
    verificationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.NEW_RELIC.name())
        .to(NewRelicConfigYamlHandler.class);
    verificationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.DYNA_TRACE.name())
        .to(DynaTraceConfigYamlHandler.class);
    verificationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.PROMETHEUS.name())
        .to(PrometheusConfigYamlHandler.class);
    verificationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.SPLUNK.name())
        .to(SplunkConfigYamlHandler.class);
    verificationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.SUMO.name())
        .to(SumoConfigYamlHandler.class);
    verificationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.INSTANA.name())
        .to(InstanaConfigYamlHandler.class);
    verificationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.SCALYR.name())
        .to(ScalyrConfigYamlHandler.class);
    verificationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.DATA_DOG.name())
        .to(DatadogConfigYamlHandler.class);
    verificationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.APM_VERIFICATION.name())
        .to(APMConfigYamlHandler.class);
    verificationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.BUG_SNAG.name())
        .to(BugsnagConfigYamlHandler.class);

    MapBinder<String, CVConfigurationYamlHandler> cvConfigYamlHelperMapBinder =
        MapBinder.newMapBinder(binder(), String.class, CVConfigurationYamlHandler.class);

    cvConfigYamlHelperMapBinder.addBinding(StateType.APP_DYNAMICS.name())
        .to(AppDynamicsCVConfigurationYamlHandler.class);
    cvConfigYamlHelperMapBinder.addBinding(StateType.INSTANA.name()).to(InstanaCVConfigurationYamlHandler.class);
    cvConfigYamlHelperMapBinder.addBinding(StateType.NEW_RELIC.name()).to(NewRelicCVConfigurationYamlHandler.class);
    cvConfigYamlHelperMapBinder.addBinding(StateType.DATA_DOG.name()).to(DatadogCvConfigurationYamlHandler.class);
    cvConfigYamlHelperMapBinder.addBinding(StateType.SUMO.name()).to(LogsCVConfigurationYamlHandler.class);
    cvConfigYamlHelperMapBinder.addBinding(StateType.ELK.name()).to(ElkCVConfigurationYamlHandler.class);
    cvConfigYamlHelperMapBinder.addBinding(StateType.SPLUNKV2.name()).to(SplunkCVConfigurationYamlHandler.class);
    cvConfigYamlHelperMapBinder.addBinding(StateType.BUG_SNAG.name()).to(BugsnagCVConfigurationYamlHandler.class);
    cvConfigYamlHelperMapBinder.addBinding(StateType.PROMETHEUS.name()).to(PrometheusCVConfigurationYamlHandler.class);
    cvConfigYamlHelperMapBinder.addBinding(StateType.CLOUD_WATCH.name()).to(CloudWatchCVConfigurationYamlHandler.class);
    cvConfigYamlHelperMapBinder.addBinding(StateType.DATA_DOG_LOG.name())
        .to(DatadogLogCVConfigurationYamlHandler.class);
    cvConfigYamlHelperMapBinder.addBinding(StateType.APM_VERIFICATION.name()).to(APMCVConfigurationYamlHandler.class);
    cvConfigYamlHelperMapBinder.addBinding(StateType.STACK_DRIVER_LOG.name())
        .to(StackdriverCVConfigurationYamlHandler.class);
    cvConfigYamlHelperMapBinder.addBinding(StateType.DYNA_TRACE.name()).to(DynatraceCVConfigurationYamlHandler.class);
    cvConfigYamlHelperMapBinder.addBinding(StateType.STACK_DRIVER.name())
        .to(StackDriverMetricsCVConfigurationYamlHandler.class);
    cvConfigYamlHelperMapBinder.addBinding(StateType.LOG_VERIFICATION.name())
        .to(CustomLogCVConfigurationYamlHandler.class);

    MapBinder<String, CollaborationProviderYamlHandler> collaborationProviderYamlHelperMapBinder =
        MapBinder.newMapBinder(binder(), String.class, CollaborationProviderYamlHandler.class);
    collaborationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.SMTP.name())
        .to(SmtpConfigYamlHandler.class);
    collaborationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.JIRA.name())
        .to(JiraConfigYamlHandler.class);
    collaborationProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.SERVICENOW.name())
        .to(ServiceNowConfigYamlHandler.class);

    MapBinder<String, SourceRepoProviderYamlHandler> sourceRepoProviderYamlHelperMapBinder =
        MapBinder.newMapBinder(binder(), String.class, SourceRepoProviderYamlHandler.class);
    sourceRepoProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.GIT.name()).to(GitConfigYamlHandler.class);

    MapBinder<String, CloudProviderYamlHandler> cloudProviderYamlHelperMapBinder =
        MapBinder.newMapBinder(binder(), String.class, CloudProviderYamlHandler.class);
    cloudProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.AWS.name()).to(AwsConfigYamlHandler.class);
    cloudProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.GCP.name()).to(GcpConfigYamlHandler.class);
    cloudProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.AZURE.name()).to(AzureConfigYamlHandler.class);
    cloudProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.KUBERNETES_CLUSTER.name())
        .to(KubernetesClusterConfigYamlHandler.class);
    cloudProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.RANCHER.name()).to(RancherConfigYamlHandler.class);
    cloudProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.PHYSICAL_DATA_CENTER.name())
        .to(PhysicalDataCenterConfigYamlHandler.class);
    cloudProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.PCF.name()).to(PcfConfigYamlHandler.class);
    cloudProviderYamlHelperMapBinder.addBinding(SettingVariableTypes.SPOT_INST.name())
        .to(SpotInstConfigYamlHandler.class);

    MapBinder<String, WorkflowYamlHandler> workflowYamlHelperMapBinder =
        MapBinder.newMapBinder(binder(), String.class, WorkflowYamlHandler.class);
    workflowYamlHelperMapBinder.addBinding(BASIC.name()).to(BasicWorkflowYamlHandler.class);
    workflowYamlHelperMapBinder.addBinding(ROLLING.name()).to(RollingWorkflowYamlHandler.class);
    workflowYamlHelperMapBinder.addBinding(BLUE_GREEN.name()).to(BlueGreenWorkflowYamlHandler.class);
    workflowYamlHelperMapBinder.addBinding(BUILD.name()).to(BuildWorkflowYamlHandler.class);
    workflowYamlHelperMapBinder.addBinding(CANARY.name()).to(CanaryWorkflowYamlHandler.class);
    workflowYamlHelperMapBinder.addBinding(MULTI_SERVICE.name()).to(MultiServiceWorkflowYamlHandler.class);

    MapBinder<String, TriggerConditionYamlHandler> triggerYamlHelperMapBinder =
        MapBinder.newMapBinder(binder(), String.class, TriggerConditionYamlHandler.class);
    triggerYamlHelperMapBinder.addBinding(SCHEDULED.name()).to(ScheduledTriggerConditionHandler.class);
    triggerYamlHelperMapBinder.addBinding(NEW_ARTIFACT.name()).to(ArtifactTriggerConditionHandler.class);
    triggerYamlHelperMapBinder.addBinding(WEBHOOK.name()).to(WebhookTriggerConditionHandler.class);
    triggerYamlHelperMapBinder.addBinding(PIPELINE_COMPLETION.name()).to(PipelineTriggerConditionHandler.class);
    triggerYamlHelperMapBinder.addBinding(NEW_MANIFEST.name()).to(ManifestTriggerConditionHandler.class);

    MapBinder<String, PayloadSourceYamlHandler> payloadSourceMapBinder =
        MapBinder.newMapBinder(binder(), String.class, PayloadSourceYamlHandler.class);
    payloadSourceMapBinder.addBinding(PayloadSource.Type.BITBUCKET.name()).to(BitbucketPayloadSourceYamlHandler.class);
    payloadSourceMapBinder.addBinding(PayloadSource.Type.GITHUB.name()).to(GithubPayloadSourceYamlHandler.class);
    payloadSourceMapBinder.addBinding(PayloadSource.Type.GITLAB.name()).to(GitlabPayloadSourceYamlHandler.class);
    payloadSourceMapBinder.addBinding(PayloadSource.Type.CUSTOM.name()).to(CustomPayloadSourceYamlHandler.class);

    MapBinder<String, InfrastructureProvisionerYamlHandler> infrastructureProvisionerYamlHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, InfrastructureProvisionerYamlHandler.class);
    infrastructureProvisionerYamlHandlerMapBinder.addBinding(InfrastructureProvisionerType.TERRAFORM.name())
        .to(TerraformInfrastructureProvisionerYamlHandler.class);
    infrastructureProvisionerYamlHandlerMapBinder.addBinding(InfrastructureProvisionerType.CLOUD_FORMATION.name())
        .to(CloudFormationInfrastructureProvisionerYamlHandler.class);
    infrastructureProvisionerYamlHandlerMapBinder.addBinding(InfrastructureProvisionerType.SHELL_SCRIPT.name())
        .to(ShellScriptProvisionerYamlHandler.class);
    infrastructureProvisionerYamlHandlerMapBinder.addBinding(InfrastructureProvisionerType.ARM.name())
        .to(ARMInfrastructureProvisionerYamlHandler.class);
    infrastructureProvisionerYamlHandlerMapBinder.addBinding(InfrastructureProvisionerType.TERRAGRUNT.name())
        .to(TerragruntInfrastructureProvisionerYamlHandler.class);

    MapBinder<String, TemplateLibraryYamlHandler> templateLibraryYamlHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, TemplateLibraryYamlHandler.class);
    templateLibraryYamlHandlerMapBinder.addBinding(TemplateConstants.SHELL_SCRIPT)
        .to(ShellScriptTemplateYamlHandler.class);
    templateLibraryYamlHandlerMapBinder.addBinding(TemplateConstants.HTTP).to(HttpTemplateYamlHandler.class);
    templateLibraryYamlHandlerMapBinder.addBinding(TemplateConstants.SSH).to(CommandTemplateYamlHandler.class);
    templateLibraryYamlHandlerMapBinder.addBinding(TemplateConstants.ARTIFACT_SOURCE)
        .to(ArtifactSourceTemplateYamlHandler.class);
    templateLibraryYamlHandlerMapBinder.addBinding(TemplateConstants.PCF_PLUGIN)
        .to(PcfCommandTemplateYamlHandler.class);
    templateLibraryYamlHandlerMapBinder.addBinding(TemplateConstants.CUSTOM_DEPLOYMENT_TYPE)
        .to(CustomDeploymentTypeTemplateYamlHandler.class);

    MapBinder<String, GovernanceFreezeConfigYamlHandler> governanceFreezeConfigYamlHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, GovernanceFreezeConfigYamlHandler.class);
    governanceFreezeConfigYamlHandlerMapBinder.addBinding("TIME_RANGE_BASED_FREEZE_CONFIG")
        .to(TimeRangeBasedFreezeConfigYamlHandler.class);

    MapBinder<String, ApplicationFilterYamlHandler> applicationFilterYamlHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, ApplicationFilterYamlHandler.class);
    applicationFilterYamlHandlerMapBinder.addBinding(BlackoutWindowFilterType.ALL.name())
        .to(AllAppFilterYamlHandler.class);
    applicationFilterYamlHandlerMapBinder.addBinding(BlackoutWindowFilterType.CUSTOM.name())
        .to(CustomAppFilterYamlHandler.class);

    MapBinder<String, EnvironmentFilterYamlHandler> environmentFilterYamlHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, EnvironmentFilterYamlHandler.class);
    environmentFilterYamlHandlerMapBinder.addBinding(EnvironmentFilterType.ALL.name())
        .to(AllEnvFilterYamlHandler.class);
    environmentFilterYamlHandlerMapBinder.addBinding(EnvironmentFilterType.CUSTOM.name())
        .to(CustomEnvFilterYamlHandler.class);
    environmentFilterYamlHandlerMapBinder.addBinding(EnvironmentFilterType.ALL_PROD.name())
        .to(AllProdEnvFilterYamlHandler.class);
    environmentFilterYamlHandlerMapBinder.addBinding(EnvironmentFilterType.ALL_NON_PROD.name())
        .to(AllNonProdEnvFilterYamlHandler.class);
  }
}
