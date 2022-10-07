/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.runner.modules;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.artifactory.ArtifactoryNgServiceImpl;
import io.harness.artifacts.gar.service.GARApiServiceImpl;
import io.harness.artifacts.gar.service.GarApiService;
import io.harness.artifacts.gcr.service.GcrApiService;
import io.harness.artifacts.gcr.service.GcrApiServiceImpl;
import io.harness.aws.AWSCloudformationClient;
import io.harness.aws.AWSCloudformationClientImpl;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsClientImpl;
import io.harness.awscli.AwsCliClient;
import io.harness.awscli.AwsCliClientImpl;
import io.harness.azure.client.AzureAuthorizationClient;
import io.harness.azure.client.AzureAutoScaleSettingsClient;
import io.harness.azure.client.AzureBlueprintClient;
import io.harness.azure.client.AzureComputeClient;
import io.harness.azure.client.AzureContainerRegistryClient;
import io.harness.azure.client.AzureKubernetesClient;
import io.harness.azure.client.AzureManagementClient;
import io.harness.azure.client.AzureMonitorClient;
import io.harness.azure.client.AzureNetworkClient;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.impl.AzureAuthorizationClientImpl;
import io.harness.azure.impl.AzureAutoScaleSettingsClientImpl;
import io.harness.azure.impl.AzureBlueprintClientImpl;
import io.harness.azure.impl.AzureComputeClientImpl;
import io.harness.azure.impl.AzureContainerRegistryClientImpl;
import io.harness.azure.impl.AzureKubernetesClientImpl;
import io.harness.azure.impl.AzureManagementClientImpl;
import io.harness.azure.impl.AzureMonitorClientImpl;
import io.harness.azure.impl.AzureNetworkClientImpl;
import io.harness.azure.impl.AzureWebClientImpl;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.service.git.NGGitServiceImpl;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.connector.task.NotSupportedValidationHandler;
import io.harness.connector.task.artifactory.ArtifactoryValidationHandler;
import io.harness.cvng.CVNGDataCollectionDelegateServiceImpl;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.DelegateConfigurationServiceProvider;
import io.harness.delegate.DelegatePropertiesServiceProvider;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.exceptionhandler.handler.AmazonClientExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.AmazonServiceExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.AuthenticationExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.AzureVaultSecretManagerExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.CVConnectorExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.DockerServerExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.GcpClientExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.HashicorpVaultExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.HelmClientRuntimeExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.InterruptedIOExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.SCMExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.SecretExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.SocketExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.TerraformRuntimeExceptionHandler;
import io.harness.delegate.k8s.K8sApplyRequestHandler;
import io.harness.delegate.k8s.K8sBGRequestHandler;
import io.harness.delegate.k8s.K8sCanaryDeleteRequestHandler;
import io.harness.delegate.k8s.K8sCanaryRequestHandler;
import io.harness.delegate.k8s.K8sDeleteRequestHandler;
import io.harness.delegate.k8s.K8sRequestHandler;
import io.harness.delegate.k8s.K8sRollingRequestHandler;
import io.harness.delegate.k8s.K8sRollingRollbackRequestHandler;
import io.harness.delegate.k8s.K8sScaleRequestHandler;
import io.harness.delegate.k8s.K8sSwapServiceSelectorsHandler;
import io.harness.delegate.runner.K8sTask;
import io.harness.delegate.runner.filemanager.DelegateFileManagerImpl;
import io.harness.delegate.runner.logging.DelegateLogServiceImpl;
import io.harness.delegate.service.DelegateCVActivityLogServiceImpl;
import io.harness.delegate.service.DelegateCVTaskServiceImpl;
import io.harness.delegate.service.K8sGlobalConfigServiceImpl;
import io.harness.delegate.task.DelegateRunnableTask;
import io.harness.delegate.task.azure.artifact.AzureArtifactDownloadService;
import io.harness.delegate.task.azure.artifact.AzureArtifactDownloadServiceImpl;
import io.harness.delegate.task.azure.exception.AzureARMRuntimeExceptionHandler;
import io.harness.delegate.task.azure.exception.AzureAppServicesRuntimeExceptionHandler;
import io.harness.delegate.task.azure.exception.AzureClientExceptionHandler;
import io.harness.delegate.task.cek8s.CEKubernetesValidationHandler;
import io.harness.delegate.task.cloudformation.CloudformationBaseHelper;
import io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl;
import io.harness.delegate.task.cvng.CVConnectorValidationHandler;
import io.harness.delegate.task.helm.HelmDeployServiceImplNG;
import io.harness.delegate.task.helm.HelmDeployServiceNG;
import io.harness.delegate.task.helm.HttpHelmValidationHandler;
import io.harness.delegate.task.helm.OciHelmValidationHandler;
import io.harness.delegate.task.jira.JiraValidationHandler;
import io.harness.delegate.task.k8s.K8sTaskNG;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.delegate.task.k8s.KubernetesValidationHandler;
import io.harness.delegate.task.k8s.exception.KubernetesApiClientRuntimeExceptionHandler;
import io.harness.delegate.task.k8s.exception.KubernetesApiExceptionHandler;
import io.harness.delegate.task.k8s.exception.KubernetesCliRuntimeExceptionHandler;
import io.harness.delegate.task.nexus.NexusValidationHandler;
import io.harness.delegate.task.scm.ScmDelegateClientImpl;
import io.harness.delegate.task.servicenow.ServiceNowValidationHandler;
import io.harness.delegate.utils.DecryptionHelperDelegate;
import io.harness.exception.ExplanationException;
import io.harness.exception.exceptionmanager.ExceptionModule;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.gcp.client.GcpClient;
import io.harness.gcp.impl.GcpClientImpl;
import io.harness.git.GitClientV2;
import io.harness.git.GitClientV2Impl;
import io.harness.helm.HelmClient;
import io.harness.helm.HelmClientImpl;
import io.harness.http.HttpService;
import io.harness.http.HttpServiceImpl;
import io.harness.impl.scm.ScmServiceClientImpl;
import io.harness.k8s.K8sGlobalConfigService;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.KubernetesContainerServiceImpl;
import io.harness.kustomize.KustomizeClient;
import io.harness.kustomize.KustomizeClientImpl;
import io.harness.manifest.CustomManifestService;
import io.harness.manifest.CustomManifestServiceImpl;
import io.harness.openshift.OpenShiftClient;
import io.harness.openshift.OpenShiftClientImpl;
import io.harness.pcf.CfCliClient;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.CfDeploymentManagerImpl;
import io.harness.pcf.CfSdkClient;
import io.harness.pcf.cfcli.client.CfCliClientImpl;
import io.harness.pcf.cfsdk.CfSdkClientImpl;
import io.harness.perpetualtask.polling.manifest.HelmChartCollectionService;
import io.harness.perpetualtask.polling.manifest.ManifestCollectionService;
import io.harness.secrets.SecretDecryptor;
import io.harness.secrets.SecretsDelegateCacheHelperService;
import io.harness.secrets.SecretsDelegateCacheHelperServiceImpl;
import io.harness.secrets.SecretsDelegateCacheService;
import io.harness.secrets.SecretsDelegateCacheServiceImpl;
import io.harness.security.encryption.DelegateDecryptionService;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.service.ScmServiceClient;
import io.harness.shell.ShellExecutionService;
import io.harness.shell.ShellExecutionServiceImpl;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.SpotInstHelperServiceDelegateImpl;
import io.harness.terraform.TerraformClient;
import io.harness.terraform.TerraformClientImpl;
import io.harness.terragrunt.TerragruntClient;
import io.harness.terragrunt.TerragruntClientImpl;
import io.harness.threading.ThreadPool;
import io.harness.time.TimeModule;
import io.harness.version.VersionModule;

import software.wings.api.DeploymentType;
import software.wings.beans.AwsConfig;
import software.wings.beans.AzureConfig;
import software.wings.beans.BambooConfig;
import software.wings.beans.DockerConfig;
import software.wings.beans.EcrConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.TaskType;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.cloudprovider.aws.AwsClusterServiceImpl;
import software.wings.cloudprovider.aws.AwsCodeDeployService;
import software.wings.cloudprovider.aws.AwsCodeDeployServiceImpl;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.cloudprovider.aws.EcsContainerServiceImpl;
import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.cloudprovider.gke.GkeClusterServiceImpl;
import software.wings.delegatetasks.DelegateCVActivityLogService;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.KubernetesSwapServiceSelectorsTask;
import software.wings.delegatetasks.k8s.taskhandler.K8sCanaryDeployTaskHandler;
import software.wings.delegatetasks.k8s.taskhandler.K8sScaleTaskHandler;
import software.wings.delegatetasks.k8s.taskhandler.K8sTaskHandler;
import software.wings.delegatetasks.terraform.TerraformConfigInspectClient;
import software.wings.delegatetasks.terraform.helper.TerraformConfigInspectClientImpl;
import software.wings.helpers.ext.ami.AmiService;
import software.wings.helpers.ext.ami.AmiServiceImpl;
import software.wings.helpers.ext.azure.AcrService;
import software.wings.helpers.ext.azure.AcrServiceImpl;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.customrepository.CustomRepositoryService;
import software.wings.helpers.ext.customrepository.CustomRepositoryServiceImpl;
import software.wings.helpers.ext.ecr.EcrClassicService;
import software.wings.helpers.ext.ecr.EcrClassicServiceImpl;
import software.wings.helpers.ext.ecr.EcrService;
import software.wings.helpers.ext.ecr.EcrServiceImpl;
import software.wings.helpers.ext.gcb.GcbService;
import software.wings.helpers.ext.gcb.GcbServiceImpl;
import software.wings.helpers.ext.gcs.GcsService;
import software.wings.helpers.ext.gcs.GcsServiceImpl;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.helpers.ext.jenkins.JenkinsFactory;
import software.wings.helpers.ext.jenkins.JenkinsImpl;
import software.wings.helpers.ext.sftp.SftpService;
import software.wings.helpers.ext.sftp.SftpServiceImpl;
import software.wings.helpers.ext.smb.SmbService;
import software.wings.helpers.ext.smb.SmbServiceImpl;
import software.wings.service.EcrClassicBuildServiceImpl;
import software.wings.service.impl.AcrBuildServiceImpl;
import software.wings.service.impl.AmiBuildServiceImpl;
import software.wings.service.impl.AzureMachineImageBuildServiceImpl;
import software.wings.service.impl.CodeDeployCommandUnitExecutorServiceImpl;
import software.wings.service.impl.ContainerCommandUnitExecutorServiceImpl;
import software.wings.service.impl.ContainerServiceImpl;
import software.wings.service.impl.CustomBuildServiceImpl;
import software.wings.service.impl.EcrBuildServiceImpl;
import software.wings.service.impl.GcrBuildServiceImpl;
import software.wings.service.impl.GcsBuildServiceImpl;
import software.wings.service.impl.GitServiceImpl;
import software.wings.service.impl.SftpBuildServiceImpl;
import software.wings.service.impl.SlackMessageSenderImpl;
import software.wings.service.impl.SmbBuildServiceImpl;
import software.wings.service.impl.TerraformConfigInspectServiceImpl;
import software.wings.service.impl.analysis.APMDelegateService;
import software.wings.service.impl.analysis.APMDelegateServiceImpl;
import software.wings.service.impl.aws.delegate.AwsAmiHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsAppAutoScalingHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsAsgHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsCFHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsCloudWatchHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsCodeDeployHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsEc2HelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsEcrHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsEcsHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsElbHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsIamHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsLambdaHelperServiceDelegateNGImpl;
import software.wings.service.impl.aws.delegate.AwsRoute53HelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsS3HelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsServiceDiscoveryHelperServiceDelegateImpl;
import software.wings.service.impl.bugsnag.BugsnagDelegateService;
import software.wings.service.impl.bugsnag.BugsnagDelegateServiceImpl;
import software.wings.service.impl.elk.ElkDelegateServiceImpl;
import software.wings.service.impl.instana.InstanaDelegateServiceImpl;
import software.wings.service.impl.ldap.LdapDelegateServiceImpl;
import software.wings.service.impl.logz.LogzDelegateServiceImpl;
import software.wings.service.impl.security.DelegateDecryptionServiceImpl;
import software.wings.service.impl.security.EncryptionServiceImpl;
import software.wings.service.impl.security.SecretDecryptionServiceImpl;
import software.wings.service.impl.security.SecretDecryptorImpl;
import software.wings.service.impl.security.SecretManagementDelegateServiceImpl;
import software.wings.service.impl.servicenow.ServiceNowDelegateServiceImpl;
import software.wings.service.impl.splunk.SplunkDelegateServiceImpl;
import software.wings.service.impl.stackdriver.StackDriverDelegateServiceImpl;
import software.wings.service.impl.sumo.SumoDelegateServiceImpl;
import software.wings.service.impl.yaml.GitClientImpl;
import software.wings.service.intfc.AcrBuildService;
import software.wings.service.intfc.AmiBuildService;
import software.wings.service.intfc.ArtifactoryBuildService;
import software.wings.service.intfc.AzureArtifactsBuildService;
import software.wings.service.intfc.AzureMachineImageBuildService;
import software.wings.service.intfc.BambooBuildService;
import software.wings.service.intfc.BuildService;
import software.wings.service.intfc.CommandUnitExecutorService;
import software.wings.service.intfc.ContainerService;
import software.wings.service.intfc.CustomBuildService;
import software.wings.service.intfc.DockerBuildService;
import software.wings.service.intfc.EcrBuildService;
import software.wings.service.intfc.EcrClassicBuildService;
import software.wings.service.intfc.GcrBuildService;
import software.wings.service.intfc.GcsBuildService;
import software.wings.service.intfc.GitService;
import software.wings.service.intfc.JenkinsBuildService;
import software.wings.service.intfc.NexusBuildService;
import software.wings.service.intfc.SftpBuildService;
import software.wings.service.intfc.SlackMessageSender;
import software.wings.service.intfc.SmbBuildService;
import software.wings.service.intfc.TerraformConfigInspectService;
import software.wings.service.intfc.aws.delegate.AwsAmiHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsAppAutoScalingHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsAsgHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsCloudWatchHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsCodeDeployHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEcrHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEcsHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsIamHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsLambdaHelperServiceDelegateNG;
import software.wings.service.intfc.aws.delegate.AwsRoute53HelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsS3HelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsServiceDiscoveryHelperServiceDelegate;
import software.wings.service.intfc.cvng.CVNGDataCollectionDelegateService;
import software.wings.service.intfc.elk.ElkDelegateService;
import software.wings.service.intfc.instana.InstanaDelegateService;
import software.wings.service.intfc.ldap.LdapDelegateService;
import software.wings.service.intfc.logz.LogzDelegateService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManagementDelegateService;
import software.wings.service.intfc.servicenow.ServiceNowDelegateService;
import software.wings.service.intfc.splunk.SplunkDelegateService;
import software.wings.service.intfc.stackdriver.StackDriverDelegateService;
import software.wings.service.intfc.sumo.SumoDelegateService;
import software.wings.service.intfc.yaml.GitClient;
import software.wings.settings.SettingValue;
import software.wings.utils.HostValidationService;
import software.wings.utils.HostValidationServiceImpl;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
@BreakDependencyOn("io.harness.delegate.beans.connector.ConnectorType")
@BreakDependencyOn("io.harness.encryptors.clients.CustomSecretsManagerEncryptor")
@BreakDependencyOn("io.harness.impl.scm.ScmServiceClientImpl")
@BreakDependencyOn("io.harness.perpetualtask.internal.AssignmentTask")
@BreakDependencyOn("io.harness.perpetualtask.polling.manifest.HelmChartCollectionService")
@BreakDependencyOn("io.harness.perpetualtask.polling.manifest.ManifestCollectionService")
@BreakDependencyOn("io.harness.service.ScmServiceClient")
@BreakDependencyOn("software.wings.api.DeploymentType")
@BreakDependencyOn("software.wings.beans.AwsConfig")
@BreakDependencyOn("software.wings.beans.AzureConfig")
@RequiredArgsConstructor
public class DelegateRunnerTasksModule extends AbstractModule {
  /*
   * Creates and return ScheduledExecutorService object, which can be used for health monitoring purpose.
   * This threadpool currently being used for various below operations:
   *  1) Sending heartbeat to manager and watcher.
   *  2) Receiving heartbeat from manager.
   *  3) Sending KeepAlive packet to manager.
   *  4) Perform self upgrade check.
   *  5) Perform watcher upgrade check.
   *  6) Track changes in delegate profile.
   */

  @Provides
  @Singleton
  @Named("verificationExecutor")
  public ScheduledExecutorService verificationExecutor() {
    return new ScheduledThreadPoolExecutor(
        2, new ThreadFactoryBuilder().setNameFormat("verification-%d").setPriority(Thread.NORM_PRIORITY).build());
  }

  @Provides
  @Singleton
  @Named("verificationDataCollectorCVNGCallExecutor")
  public ExecutorService verificationDataCollectorCVNGCallExecutor() {
    return ThreadPool.create(4, 20, 5, TimeUnit.SECONDS,
        new ThreadFactoryBuilder()
            .setNameFormat("verificationDataCollectorCVNGCaller-%d")
            .setPriority(Thread.MIN_PRIORITY)
            .build());
  }

  @Provides
  @Singleton
  @Named("cvngParallelExecutor")
  public ExecutorService cvngParallelExecutor() {
    return ThreadPool.create(1, CVNextGenConstants.CVNG_MAX_PARALLEL_THREADS, 5, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("cvngParallelExecutor-%d").setPriority(Thread.MIN_PRIORITY).build());
  }

  @Provides
  @Singleton
  @Named("cvngSyncCallExecutor")
  public ExecutorService cvngSyncCallExecutor() {
    return ThreadPool.create(1, 5, 5, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("cvngSyncCallExecutor-%d").setPriority(Thread.MIN_PRIORITY).build());
  }

  @Provides
  @Singleton
  @Named("k8sSteadyStateExecutor")
  public ExecutorService k8sSteadyStateExecutor() {
    return Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setNameFormat("k8sSteadyState-%d").setPriority(Thread.MAX_PRIORITY).build());
  }

  @Provides
  @Singleton
  @Named("asyncExecutor")
  public ExecutorService asyncExecutor() {
    return ThreadPool.create(10, 400, 1, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("async-%d").setPriority(Thread.MIN_PRIORITY).build());
  }

  @Provides
  @Singleton
  @Named("timeoutExecutor")
  public ThreadPoolExecutor timeoutExecutor() {
    return ThreadPool.create(10, 40, 7, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("timeout-%d").setPriority(Thread.NORM_PRIORITY).build());
  }

  @Provides
  @Singleton
  @Named("jenkinsExecutor")
  public ExecutorService jenkinsExecutor() {
    return ThreadPool.create(1, 40, 1, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("jenkins-%d").setPriority(Thread.NORM_PRIORITY).build());
  }

    @Provides
    @Singleton
    @Named("verificationDataCollectorExecutor")
    public ExecutorService verificationDataCollectorExecutor() {
        return ThreadPool.create(4, 20, 5, TimeUnit.SECONDS,
            new ThreadFactoryBuilder()
                .setNameFormat("verificationDataCollector-%d")
                .setPriority(Thread.MIN_PRIORITY)
                .build());
    }

  @Override
  protected void configure() {
    bindDelegateTasks();

    install(VersionModule.getInstance());
    install(TimeModule.getInstance());
    install(ExceptionModule.getInstance());

        install(new FactoryModuleBuilder().implement(Jenkins.class, JenkinsImpl.class).build(JenkinsFactory.class));
//        bind(SshExecutorFactory.class);
//        bind(DelegateCVTaskService.class).to(DelegateCVTaskServiceImpl.class);
        bind(DelegateCVActivityLogService.class).to(DelegateCVActivityLogServiceImpl.class);
//        bind(JenkinsBuildService.class).to(JenkinsBuildServiceImpl.class);
        bind(SmbBuildService.class).to(SmbBuildServiceImpl.class);
        bind(SmbService.class).to(SmbServiceImpl.class);
//        bind(BambooBuildService.class).to(BambooBuildServiceImpl.class);
//        bind(DockerBuildService.class).to(DockerBuildServiceImpl.class);
//        bind(BambooService.class).to(BambooServiceImpl.class);
        // DefaultAsyncHttpClient is being bound using a separate function (as this function can't throw)
        bind(AsyncHttpClient.class).to(DefaultAsyncHttpClient.class);
        bind(AwsClusterService.class).to(AwsClusterServiceImpl.class);
        bind(EcsContainerService.class).to(EcsContainerServiceImpl.class);
        bind(GkeClusterService.class).to(GkeClusterServiceImpl.class);
        try {
            bind(new TypeLiteral<DataStore<StoredCredential>>() {
            }).toInstance(StoredCredential.getDefaultDataStore(new MemoryDataStoreFactory()));
        } catch (IOException e) {
            String msg =
                "Could not initialise GKE access token memory cache. This should not never happen with memory data store.";
            throw new ExplanationException(msg, e);
        }
        bind(KubernetesContainerService.class).to(KubernetesContainerServiceImpl.class);
        bind(AwsCodeDeployService.class).to(AwsCodeDeployServiceImpl.class);
        bind(AwsCodeDeployService.class).to(AwsCodeDeployServiceImpl.class);
//        bind(NexusBuildService.class).to(NexusBuildServiceImpl.class);
//        bind(NexusService.class).to(NexusServiceImpl.class);
//        bind(AppdynamicsDelegateService.class).to(AppdynamicsDelegateServiceImpl.class);
        bind(InstanaDelegateService.class).to(InstanaDelegateServiceImpl.class);
        bind(StackDriverDelegateService.class).to(StackDriverDelegateServiceImpl.class);
        bind(APMDelegateService.class).to(APMDelegateServiceImpl.class);
//        bind(NewRelicDelegateService.class).to(NewRelicDelgateServiceImpl.class);
        bind(BugsnagDelegateService.class).to(BugsnagDelegateServiceImpl.class);
//        bind(DynaTraceDelegateService.class).to(DynaTraceDelegateServiceImpl.class);
        bind(SplunkDelegateService.class).to(SplunkDelegateServiceImpl.class);
        bind(ElkDelegateService.class).to(ElkDelegateServiceImpl.class);
        bind(LogzDelegateService.class).to(LogzDelegateServiceImpl.class);
        bind(SumoDelegateService.class).to(SumoDelegateServiceImpl.class);
        bind(SplunkDelegateService.class).to(SplunkDelegateServiceImpl.class);
//        bind(CloudWatchDelegateService.class).to(CloudWatchDelegateServiceImpl.class);
//        bind(ArtifactoryBuildService.class).to(ArtifactoryBuildServiceImpl.class);
//        bind(ArtifactoryService.class).to(ArtifactoryServiceImpl.class);
        bind(EcrBuildService.class).to(EcrBuildServiceImpl.class);
//        bind(AmazonS3BuildService.class).to(AmazonS3BuildServiceImpl.class);
//        bind(AmazonS3Service.class).to(AmazonS3ServiceImpl.class);
        bind(GcsBuildService.class).to(GcsBuildServiceImpl.class);
        bind(GcsService.class).to(GcsServiceImpl.class);
        bind(EcrClassicBuildService.class).to(EcrClassicBuildServiceImpl.class);
        bind(EcrService.class).to(EcrServiceImpl.class);
        bind(EcrClassicService.class).to(EcrClassicServiceImpl.class);
        bind(GcrApiService.class).to(GcrApiServiceImpl.class);
        bind(GarApiService.class).to(GARApiServiceImpl.class);
        bind(GcrBuildService.class).to(GcrBuildServiceImpl.class);
        bind(AcrService.class).to(AcrServiceImpl.class);
        bind(AcrBuildService.class).to(AcrBuildServiceImpl.class);
        bind(AmiBuildService.class).to(AmiBuildServiceImpl.class);
        bind(AzureMachineImageBuildService.class).to(AzureMachineImageBuildServiceImpl.class);
        bind(CustomBuildService.class).to(CustomBuildServiceImpl.class);
        bind(CustomRepositoryService.class).to(CustomRepositoryServiceImpl.class);
        bind(AmiService.class).to(AmiServiceImpl.class);
//        bind(AzureArtifactsBuildService.class).to(AzureArtifactsBuildServiceImpl.class);
        bind(HostValidationService.class).to(HostValidationServiceImpl.class);
        bind(ContainerService.class).to(ContainerServiceImpl.class);
        bind(GitClient.class).to(GitClientImpl.class).asEagerSingleton();
        bind(GitClientV2.class).to(GitClientV2Impl.class).asEagerSingleton();
        bind(Clock.class).toInstance(Clock.systemUTC());
        bind(HelmClient.class).to(HelmClientImpl.class);
        bind(TerragruntClient.class).to(TerragruntClientImpl.class);
        bind(KustomizeClient.class).to(KustomizeClientImpl.class);
        bind(OpenShiftClient.class).to(OpenShiftClientImpl.class);
//        bind(HelmDeployService.class).to(HelmDeployServiceImpl.class);
        bind(ContainerDeploymentDelegateHelper.class);
        bind(CfCliClient.class).to(CfCliClientImpl.class);
        bind(CfSdkClient.class).to(CfSdkClientImpl.class);
        bind(CfDeploymentManager.class).to(CfDeploymentManagerImpl.class);
        bind(AwsEcrHelperServiceDelegate.class).to(AwsEcrHelperServiceDelegateImpl.class);
        bind(AwsElbHelperServiceDelegate.class).to(AwsElbHelperServiceDelegateImpl.class);
        bind(AwsEcsHelperServiceDelegate.class).to(AwsEcsHelperServiceDelegateImpl.class);
        bind(AwsAppAutoScalingHelperServiceDelegate.class).to(AwsAppAutoScalingHelperServiceDelegateImpl.class);
        bind(AwsIamHelperServiceDelegate.class).to(AwsIamHelperServiceDelegateImpl.class);
        bind(AwsEc2HelperServiceDelegate.class).to(AwsEc2HelperServiceDelegateImpl.class);
        bind(AwsAsgHelperServiceDelegate.class).to(AwsAsgHelperServiceDelegateImpl.class);
        bind(AwsCodeDeployHelperServiceDelegate.class).to(AwsCodeDeployHelperServiceDelegateImpl.class);
//        bind(AwsLambdaHelperServiceDelegate.class).to(AwsLambdaHelperServiceDelegateImpl.class);
        bind(AwsLambdaHelperServiceDelegateNG.class).to(AwsLambdaHelperServiceDelegateNGImpl.class);
        bind(AwsAmiHelperServiceDelegate.class).to(AwsAmiHelperServiceDelegateImpl.class);
        bind(GitService.class).to(GitServiceImpl.class);
        bind(LdapDelegateService.class).to(LdapDelegateServiceImpl.class);
        bind(AwsCFHelperServiceDelegate.class).to(AwsCFHelperServiceDelegateImpl.class);
        bind(SftpBuildService.class).to(SftpBuildServiceImpl.class);
        bind(SftpService.class).to(SftpServiceImpl.class);
        bind(K8sGlobalConfigService.class).to(K8sGlobalConfigServiceImpl.class);
        bind(ShellExecutionService.class).to(ShellExecutionServiceImpl.class);
        bind(CustomRepositoryService.class).to(CustomRepositoryServiceImpl.class);
        bind(AwsRoute53HelperServiceDelegate.class).to(AwsRoute53HelperServiceDelegateImpl.class);
        bind(AwsServiceDiscoveryHelperServiceDelegate.class).to(AwsServiceDiscoveryHelperServiceDelegateImpl.class);
        bind(ServiceNowDelegateService.class).to(ServiceNowDelegateServiceImpl.class);
        bind(SpotInstHelperServiceDelegate.class).to(SpotInstHelperServiceDelegateImpl.class);
        bind(AwsS3HelperServiceDelegate.class).to(AwsS3HelperServiceDelegateImpl.class);
        bind(GcbService.class).to(GcbServiceImpl.class);
        bind(CustomManifestService.class).to(CustomManifestServiceImpl.class);
        bind(DecryptionHelper.class).to(DecryptionHelperDelegate.class);
        bind(SlackMessageSender.class).to(SlackMessageSenderImpl.class);

        bind(AwsCloudWatchHelperServiceDelegate.class).to(AwsCloudWatchHelperServiceDelegateImpl.class);
//        bind(AzureArtifactsService.class).to(AzureArtifactsServiceImpl.class);
        //bind(TerraformBaseHelper.class).to(TerraformBaseHelperImpl.class);
        bind(TerraformClient.class).to(TerraformClientImpl.class);
        bind(CloudformationBaseHelper.class).to(CloudformationBaseHelperImpl.class);
        bind(HelmDeployServiceNG.class).to(HelmDeployServiceImplNG.class);
        bind(AwsCliClient.class).to(AwsCliClientImpl.class);

        MapBinder<String, CommandUnitExecutorService> serviceCommandExecutorServiceMapBinder =
            MapBinder.newMapBinder(binder(), String.class, CommandUnitExecutorService.class);
        serviceCommandExecutorServiceMapBinder.addBinding(DeploymentType.ECS.name())
            .to(ContainerCommandUnitExecutorServiceImpl.class);
        serviceCommandExecutorServiceMapBinder.addBinding(DeploymentType.KUBERNETES.name())
            .to(ContainerCommandUnitExecutorServiceImpl.class);
//        serviceCommandExecutorServiceMapBinder.addBinding(DeploymentType.SSH.name())
//            .to(SshCommandUnitExecutorServiceImpl.class);
//        serviceCommandExecutorServiceMapBinder.addBinding(DeploymentType.WINRM.name())
//            .to(WinRMCommandUnitExecutorServiceImpl.class);
        serviceCommandExecutorServiceMapBinder.addBinding(DeploymentType.AWS_CODEDEPLOY.name())
            .to(CodeDeployCommandUnitExecutorServiceImpl.class);

//        MapBinder<String, PcfCommandTaskHandler> commandTaskTypeToTaskHandlerMap =
//            MapBinder.newMapBinder(binder(), String.class, PcfCommandTaskHandler.class);
//        commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.SETUP.name()).to(PcfSetupCommandTaskHandler.class);
//        commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.RESIZE.name()).to(PcfDeployCommandTaskHandler.class);
//        commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.ROLLBACK.name()).to(PcfRollbackCommandTaskHandler.class);
//        commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.UPDATE_ROUTE.name())
//            .to(PcfRouteUpdateCommandTaskHandler.class);
//        commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.VALIDATE.name())
//            .to(PcfValidationCommandTaskHandler.class);
//        commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.APP_DETAILS.name())
//            .to(PcfApplicationDetailsCommandTaskHandler.class);
//        commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.DATAFETCH.name())
//            .to(PcfDataFetchCommandTaskHandler.class);
//        commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.CREATE_ROUTE.name())
//            .to(PcfCreatePcfResourceCommandTaskHandler.class);
//        commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.RUN_PLUGIN.name())
//            .to(PcfRunPluginCommandTaskHandler.class);

        MapBinder<Class<? extends SettingValue>, Class<? extends BuildService>> buildServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends SettingValue>>() {},
                new TypeLiteral<Class<? extends BuildService>>() {});

      buildServiceMapBinder.addBinding(JenkinsConfig.class).toInstance(JenkinsBuildService.class);
      buildServiceMapBinder.addBinding(BambooConfig.class).toInstance(BambooBuildService.class);
      buildServiceMapBinder.addBinding(DockerConfig.class).toInstance(DockerBuildService.class);
      buildServiceMapBinder.addBinding(AwsConfig.class).toInstance(EcrBuildService.class);
      buildServiceMapBinder.addBinding(EcrConfig.class).toInstance(EcrClassicBuildService.class);
      buildServiceMapBinder.addBinding(GcpConfig.class).toInstance(GcrBuildService.class);
      buildServiceMapBinder.addBinding(AzureConfig.class).toInstance(AcrBuildService.class);
      buildServiceMapBinder.addBinding(NexusConfig.class).toInstance(NexusBuildService.class);
      buildServiceMapBinder.addBinding(ArtifactoryConfig.class).toInstance(ArtifactoryBuildService.class);
      buildServiceMapBinder.addBinding(AzureArtifactsPATConfig.class).toInstance(AzureArtifactsBuildService.class);

        // ECS Command Tasks
//        MapBinder<String, EcsCommandTaskHandler> ecsCommandTaskTypeToTaskHandlerMap =
//            MapBinder.newMapBinder(binder(), String.class, EcsCommandTaskHandler.class);
//
//        ecsCommandTaskTypeToTaskHandlerMap.addBinding(EcsCommandType.BG_SERVICE_SETUP.name())
//            .to(EcsBlueGreenSetupCommandHandler.class);
//        ecsCommandTaskTypeToTaskHandlerMap.addBinding(EcsCommandType.ROUTE53_BG_SERVICE_SETUP.name())
//            .to(EcsBlueGreenRoute53SetupCommandHandler.class);
//        ecsCommandTaskTypeToTaskHandlerMap.addBinding(EcsCommandType.ROUTE53_DNS_WEIGHT_UPDATE.name())
//            .to(EcsBlueGreenRoute53DNSWeightHandler.class);
//        ecsCommandTaskTypeToTaskHandlerMap.addBinding(EcsCommandType.SERVICE_SETUP.name()).to(EcsSetupCommandHandler.class);
//        ecsCommandTaskTypeToTaskHandlerMap.addBinding(EcsCommandType.SERVICE_DEPLOY.name())
//            .to(EcsDeployCommandHandler.class);
////        ecsCommandTaskTypeToTaskHandlerMap.addBinding(EcsCommandType.DEPLOY_ROLLBACK_DATA_FETCH.name())
////            .to(EcsDeployRollbackDataFetchCommandHandler.class);
////        ecsCommandTaskTypeToTaskHandlerMap.addBinding(EcsCommandType.ECS_RUN_TASK_DEPLOY.name())
////            .to(EcsRunTaskDeployCommandHandler.class);

        MapBinder<String, K8sTaskHandler> k8sCommandTaskTypeToTaskHandlerMap =
            MapBinder.newMapBinder(binder(), String.class, K8sTaskHandler.class);
        k8sCommandTaskTypeToTaskHandlerMap.addBinding(K8sTaskType.CANARY_DEPLOY.name())
            .to(K8sCanaryDeployTaskHandler.class);
        k8sCommandTaskTypeToTaskHandlerMap.addBinding(K8sTaskType.SCALE.name()).to(K8sScaleTaskHandler.class);


    bind(TerraformConfigInspectClient.class).toInstance(new TerraformConfigInspectClientImpl());
        bind(TerraformConfigInspectService.class).toInstance(new TerraformConfigInspectServiceImpl());
        bind(DataCollectionDSLService.class).to(DataCollectionServiceImpl.class);
        bind(AzureComputeClient.class).to(AzureComputeClientImpl.class);
        bind(AzureAutoScaleSettingsClient.class).to(AzureAutoScaleSettingsClientImpl.class);
        bind(AzureNetworkClient.class).to(AzureNetworkClientImpl.class);
        bind(AzureMonitorClient.class).to(AzureMonitorClientImpl.class);
        bind(AzureContainerRegistryClient.class).to(AzureContainerRegistryClientImpl.class);
        bind(AzureWebClient.class).to(AzureWebClientImpl.class);
        bind(NGGitService.class).to(NGGitServiceImpl.class);
        bind(GcpClient.class).to(GcpClientImpl.class);
//        bind(ManifestRepositoryService.class)
//            .annotatedWith(Names.named(ManifestRepoServiceType.ARTIFACTORY_HELM_SERVICE))
//            .to(ArtifactoryHelmRepositoryService.class);
        bind(AwsClient.class).to(AwsClientImpl.class);
        bind(CVNGDataCollectionDelegateService.class).to(CVNGDataCollectionDelegateServiceImpl.class);
        bind(AzureManagementClient.class).to(AzureManagementClientImpl.class);
        bind(AzureBlueprintClient.class).to(AzureBlueprintClientImpl.class);
        bind(AzureAuthorizationClient.class).to(AzureAuthorizationClientImpl.class);
        bind(ScmDelegateClient.class).to(ScmDelegateClientImpl.class);
        bind(ScmServiceClient.class).to(ScmServiceClientImpl.class);
        bind(ManifestCollectionService.class).to(HelmChartCollectionService.class);
        bind(AzureKubernetesClient.class).to(AzureKubernetesClientImpl.class);
        bind(ArtifactoryNgService.class).to(ArtifactoryNgServiceImpl.class);
        bind(AWSCloudformationClient.class).to(AWSCloudformationClientImpl.class);
        bind(AzureArtifactDownloadService.class).to(AzureArtifactDownloadServiceImpl.class);

    // NG Delegate
      MapBinder<String, K8sRequestHandler> k8sTaskTypeToRequestHandler =
          MapBinder.newMapBinder(binder(), String.class, K8sRequestHandler.class);
      k8sTaskTypeToRequestHandler.addBinding(K8sTaskType.DEPLOYMENT_ROLLING.name()).to(K8sRollingRequestHandler.class);
      k8sTaskTypeToRequestHandler.addBinding(K8sTaskType.BLUE_GREEN_DEPLOY.name()).to(K8sBGRequestHandler.class);
      k8sTaskTypeToRequestHandler.addBinding(K8sTaskType.APPLY.name()).to(K8sApplyRequestHandler.class);
      k8sTaskTypeToRequestHandler.addBinding(K8sTaskType.DEPLOYMENT_ROLLING_ROLLBACK.name())
          .to(K8sRollingRollbackRequestHandler.class);
      k8sTaskTypeToRequestHandler.addBinding(K8sTaskType.SCALE.name()).to(K8sScaleRequestHandler.class);
      k8sTaskTypeToRequestHandler.addBinding(K8sTaskType.CANARY_DEPLOY.name()).to(K8sCanaryRequestHandler.class);
      k8sTaskTypeToRequestHandler.addBinding(K8sTaskType.SWAP_SERVICE_SELECTORS.name())
          .to(K8sSwapServiceSelectorsHandler.class);
      k8sTaskTypeToRequestHandler.addBinding(K8sTaskType.DELETE.name()).to(K8sDeleteRequestHandler.class);
      k8sTaskTypeToRequestHandler.addBinding(K8sTaskType.CANARY_DELETE.name()).to(K8sCanaryDeleteRequestHandler.class);

      registerConnectorValidatorsBindings();
      registerSecretManagementBindings();
      bindExceptionHandlers();

      bind(DelegateLogService.class).to(DelegateLogServiceImpl.class);
      bind(DelegateFileManager.class).to(DelegateFileManagerImpl.class);
      bind(DelegateFileManagerBase.class).to(DelegateFileManagerImpl.class);
      bind(HttpService.class).to(HttpServiceImpl.class);
      bind(SecretsDelegateCacheHelperService.class).to(SecretsDelegateCacheHelperServiceImpl.class);
      bind(SecretsDelegateCacheService.class).to(SecretsDelegateCacheServiceImpl.class);
      bind(K8sGlobalConfigService.class).to(K8sGlobalConfigServiceImpl.class);
  }

  // POV only
  @Provides
  @Singleton
  public DelegateConfigurationServiceProvider getDelegateConfigurationServiceProvider(DelegateConfiguration delegateConfiguration) {
    return delegateConfiguration::getAccountId;
  }

  // POV only
  @Provides
  @Singleton
  public DelegatePropertiesServiceProvider getDelegatePropertiesServiceProvider() {
    return request->null;
  }


  private void bindDelegateTasks() {
    MapBinder<TaskType, Class<? extends DelegateRunnableTask>> mapBinder = MapBinder.newMapBinder(
        binder(), new TypeLiteral<TaskType>() {}, new TypeLiteral<Class<? extends DelegateRunnableTask>>() {});

    mapBinder.addBinding(TaskType.KUBERNETES_SWAP_SERVICE_SELECTORS_TASK)
        .toInstance(KubernetesSwapServiceSelectorsTask.class);
    mapBinder.addBinding(TaskType.K8S_COMMAND_TASK_NG).toInstance(K8sTaskNG.class);
    mapBinder.addBinding(TaskType.K8S_COMMAND_TASK).toInstance(K8sTask.class);
  }

  private void registerSecretManagementBindings() {
    bind(SecretManagementDelegateService.class).to(SecretManagementDelegateServiceImpl.class);
    bind(EncryptionService.class).to(EncryptionServiceImpl.class);
    bind(SecretDecryptionService.class).to(SecretDecryptionServiceImpl.class);
    bind(DelegateDecryptionService.class).to(DelegateDecryptionServiceImpl.class);
    bind(SecretDecryptor.class).to(SecretDecryptorImpl.class);
    //bind(EncryptDecryptHelper.class).to(EncryptDecryptHelperImpl.class);

//      binder()
//        .bind(CustomEncryptor.class)
//        .annotatedWith(Names.named(Encryptors.CUSTOM_ENCRYPTOR.getName()))
//        .to(CustomSecretsManagerEncryptor.class);
  }

  private void registerConnectorValidatorsBindings() {
    MapBinder<String, ConnectorValidationHandler> connectorTypeToConnectorValidationHandlerMap =
        MapBinder.newMapBinder(binder(), String.class, ConnectorValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.KUBERNETES_CLUSTER.getDisplayName())
        .to(KubernetesValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.CE_KUBERNETES_CLUSTER.getDisplayName())
        .to(CEKubernetesValidationHandler.class);
//    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.GIT.getDisplayName())
//        .to(GitValidationHandler.class);
//    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.GITHUB.getDisplayName())
//        .to(GitValidationHandler.class);
//    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.GITLAB.getDisplayName())
//        .to(GitValidationHandler.class);
//    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.BITBUCKET.getDisplayName())
//        .to(GitValidationHandler.class);
//    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.JENKINS.getDisplayName())
//        .to(JenkinsValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.HTTP_HELM_REPO.getDisplayName())
        .to(HttpHelmValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.OCI_HELM_REPO.getDisplayName())
        .to(OciHelmValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.ARTIFACTORY.getDisplayName())
        .to(ArtifactoryValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.NEXUS.getDisplayName())
        .to(NexusValidationHandler.class);
//    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.GCP.getDisplayName())
//        .to(GcpValidationTaskHandler.class);
//    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.AWS.getDisplayName())
//        .to(AwsValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.DATADOG.getDisplayName())
        .to(CVConnectorValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.APP_DYNAMICS.getDisplayName())
        .to(CVConnectorValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.NEW_RELIC.getDisplayName())
        .to(CVConnectorValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.SPLUNK.getDisplayName())
        .to(CVConnectorValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.PROMETHEUS.getDisplayName())
        .to(CVConnectorValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.SUMOLOGIC.getDisplayName())
        .to(CVConnectorValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.DYNATRACE.getDisplayName())
        .to(CVConnectorValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.PAGER_DUTY.getDisplayName())
        .to(CVConnectorValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.CUSTOM_HEALTH.getDisplayName())
        .to(CVConnectorValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.JIRA.getDisplayName())
        .to(JiraValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.SERVICENOW.getDisplayName())
        .to(ServiceNowValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.ERROR_TRACKING.getDisplayName())
        .to(CVConnectorValidationHandler.class);
//    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.AZURE.getDisplayName())
//        .to(AzureValidationHandler.class);
//    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.AZURE_REPO.getDisplayName())
//        .to(GitValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.CUSTOM_SECRET_MANAGER.getDisplayName())
        .to(NotSupportedValidationHandler.class);
  }

  private void bindExceptionHandlers() {
    MapBinder<Class<? extends Exception>, ExceptionHandler> exceptionHandlerMapBinder = MapBinder.newMapBinder(
        binder(), new TypeLiteral<Class<? extends Exception>>() {}, new TypeLiteral<ExceptionHandler>() {});

    AmazonServiceExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AmazonServiceExceptionHandler.class));
    AmazonClientExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AmazonClientExceptionHandler.class));
    AzureVaultSecretManagerExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AzureVaultSecretManagerExceptionHandler.class));
    GcpClientExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(GcpClientExceptionHandler.class));
    HashicorpVaultExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(HashicorpVaultExceptionHandler.class));
    DockerServerExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(DockerServerExceptionHandler.class));
    SecretExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(SecretExceptionHandler.class));
    SocketExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(SocketExceptionHandler.class));
    InterruptedIOExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(InterruptedIOExceptionHandler.class));
    CVConnectorExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(CVConnectorExceptionHandler.class));
    SCMExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(SCMExceptionHandler.class));
    AuthenticationExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AuthenticationExceptionHandler.class));
    HelmClientRuntimeExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(HelmClientRuntimeExceptionHandler.class));
    KubernetesApiExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(KubernetesApiExceptionHandler.class));
    KubernetesApiClientRuntimeExceptionHandler.exceptions().forEach(exception
        -> exceptionHandlerMapBinder.addBinding(exception).to(KubernetesApiClientRuntimeExceptionHandler.class));
    TerraformRuntimeExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(TerraformRuntimeExceptionHandler.class));
    KubernetesCliRuntimeExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(KubernetesCliRuntimeExceptionHandler.class));
    AzureAppServicesRuntimeExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AzureAppServicesRuntimeExceptionHandler.class));
    AzureClientExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AzureClientExceptionHandler.class));
    AzureARMRuntimeExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AzureARMRuntimeExceptionHandler.class));
  }
}
