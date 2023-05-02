/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.artifactory.ArtifactoryNgServiceImpl;
import io.harness.artifactory.service.ArtifactoryRegistryService;
import io.harness.artifactory.service.ArtifactoryRegistryServiceImpl;
import io.harness.artifacts.ami.service.AMIRegistryService;
import io.harness.artifacts.ami.service.AMIRegistryServiceImpl;
import io.harness.artifacts.azureartifacts.service.AzureArtifactsRegistryService;
import io.harness.artifacts.azureartifacts.service.AzureArtifactsRegistryServiceImpl;
import io.harness.artifacts.docker.client.DockerRestClientFactory;
import io.harness.artifacts.docker.client.DockerRestClientFactoryImpl;
import io.harness.artifacts.docker.service.DockerRegistryService;
import io.harness.artifacts.docker.service.DockerRegistryServiceImpl;
import io.harness.artifacts.gar.service.GARApiServiceImpl;
import io.harness.artifacts.gar.service.GarApiService;
import io.harness.artifacts.gcr.service.GcrApiService;
import io.harness.artifacts.gcr.service.GcrApiServiceImpl;
import io.harness.artifacts.githubpackages.client.GithubPackagesRestClientFactory;
import io.harness.artifacts.githubpackages.client.GithubPackagesRestClientFactoryImpl;
import io.harness.artifacts.githubpackages.service.GithubPackagesRegistryService;
import io.harness.artifacts.githubpackages.service.GithubPackagesRegistryServiceImpl;
import io.harness.aws.AWSCloudformationClient;
import io.harness.aws.AWSCloudformationClientImpl;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsClientImpl;
import io.harness.aws.v2.ecs.EcsV2Client;
import io.harness.aws.v2.ecs.EcsV2ClientImpl;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.aws.v2.ecs.ElbV2ClientImpl;
import io.harness.aws.v2.eks.EksV2Client;
import io.harness.aws.v2.eks.EksV2ClientImpl;
import io.harness.aws.v2.lambda.AwsLambdaClient;
import io.harness.aws.v2.lambda.AwsLambdaClientImpl;
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
import io.harness.cdng.notification.task.MailSenderDelegateTask;
import io.harness.cdng.secrets.tasks.SSHConfigValidationDelegateTask;
import io.harness.cdng.secrets.tasks.WinRmConfigValidationDelegateTask;
import io.harness.cistatus.service.GithubService;
import io.harness.cistatus.service.GithubServiceImpl;
import io.harness.cistatus.service.azurerepo.AzureRepoService;
import io.harness.cistatus.service.azurerepo.AzureRepoServiceImpl;
import io.harness.cistatus.service.bitbucket.BitbucketService;
import io.harness.cistatus.service.bitbucket.BitbucketServiceImpl;
import io.harness.cistatus.service.gitlab.GitlabService;
import io.harness.cistatus.service.gitlab.GitlabServiceImpl;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.service.git.NGGitServiceImpl;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.connector.task.NotSupportedValidationHandler;
import io.harness.connector.task.artifactory.ArtifactoryValidationHandler;
import io.harness.connector.task.aws.AwsValidationHandler;
import io.harness.connector.task.azure.AzureValidationHandler;
import io.harness.connector.task.docker.DockerValidationHandler;
import io.harness.connector.task.gcp.GcpValidationTaskHandler;
import io.harness.connector.task.git.GitValidationHandler;
import io.harness.connector.task.terraformcloud.TerraformCloudValidationHandler;
import io.harness.cvng.CVNGDataCollectionDelegateServiceImpl;
import io.harness.cvng.K8InfoDataServiceImpl;
import io.harness.cvng.connectiontask.CVNGConnectorValidationDelegateTask;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.DelegateConfigurationServiceProvider;
import io.harness.delegate.DelegatePropertiesServiceProvider;
import io.harness.delegate.app.DelegateApplication;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.cf.PcfApplicationDetailsCommandTaskHandler;
import io.harness.delegate.cf.PcfCommandTaskHandler;
import io.harness.delegate.cf.PcfCreatePcfResourceCommandTaskHandler;
import io.harness.delegate.cf.PcfDataFetchCommandTaskHandler;
import io.harness.delegate.cf.PcfDeployCommandTaskHandler;
import io.harness.delegate.cf.PcfRollbackCommandTaskHandler;
import io.harness.delegate.cf.PcfRouteUpdateCommandTaskHandler;
import io.harness.delegate.cf.PcfRunPluginCommandTaskHandler;
import io.harness.delegate.cf.PcfValidationCommandTaskHandler;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.ecs.EcsBlueGreenCreateServiceCommandTaskHandler;
import io.harness.delegate.ecs.EcsBlueGreenPrepareRollbackCommandTaskHandler;
import io.harness.delegate.ecs.EcsBlueGreenRollbackCommandTaskHandler;
import io.harness.delegate.ecs.EcsBlueGreenSwapTargetGroupsCommandTaskHandler;
import io.harness.delegate.ecs.EcsCanaryDeleteCommandTaskHandler;
import io.harness.delegate.ecs.EcsCanaryDeployCommandTaskHandler;
import io.harness.delegate.ecs.EcsCommandTaskNGHandler;
import io.harness.delegate.ecs.EcsPrepareRollbackCommandTaskHandler;
import io.harness.delegate.ecs.EcsRollingDeployCommandTaskHandler;
import io.harness.delegate.ecs.EcsRollingRollbackCommandTaskHandler;
import io.harness.delegate.ecs.EcsRunTaskArnCommandTaskHandler;
import io.harness.delegate.ecs.EcsRunTaskCommandTaskHandler;
import io.harness.delegate.ecs.EcsTaskArnBlueGreenCreateServiceCommandTaskHandler;
import io.harness.delegate.ecs.EcsTaskArnCanaryDeployCommandTaskHandler;
import io.harness.delegate.ecs.EcsTaskArnRollingDeployCommandTaskHandler;
import io.harness.delegate.exceptionhandler.handler.AmazonClientExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.AmazonServiceExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.AuthenticationExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.AzureExceptionHandler;
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
import io.harness.delegate.exceptionhandler.handler.TerragruntRuntimeExceptionHandler;
import io.harness.delegate.googlefunction.GoogleFunctionCommandTaskHandler;
import io.harness.delegate.googlefunction.GoogleFunctionDeployCommandTaskHandler;
import io.harness.delegate.googlefunction.GoogleFunctionDeployWithoutTrafficCommandTaskHandler;
import io.harness.delegate.googlefunction.GoogleFunctionGenOneDeployCommandTaskHandler;
import io.harness.delegate.googlefunction.GoogleFunctionGenOnePrepareRollbackCommandTaskHandler;
import io.harness.delegate.googlefunction.GoogleFunctionGenOneRollbackCommandTaskHandler;
import io.harness.delegate.googlefunction.GoogleFunctionPrepareRollbackCommandTaskHandler;
import io.harness.delegate.googlefunction.GoogleFunctionRollbackCommandTaskHandler;
import io.harness.delegate.googlefunction.GoogleFunctionTrafficShiftCommandTaskHandler;
import io.harness.delegate.http.HttpTaskNG;
import io.harness.delegate.k8s.K8sApplyRequestHandler;
import io.harness.delegate.k8s.K8sBGRequestHandler;
import io.harness.delegate.k8s.K8sCanaryDeleteRequestHandler;
import io.harness.delegate.k8s.K8sCanaryRequestHandler;
import io.harness.delegate.k8s.K8sDeleteRequestHandler;
import io.harness.delegate.k8s.K8sDryRunManifestRequestHandler;
import io.harness.delegate.k8s.K8sRequestHandler;
import io.harness.delegate.k8s.K8sRollingRequestHandler;
import io.harness.delegate.k8s.K8sRollingRollbackRequestHandler;
import io.harness.delegate.k8s.K8sScaleRequestHandler;
import io.harness.delegate.k8s.K8sSwapServiceSelectorsHandler;
import io.harness.delegate.message.MessageService;
import io.harness.delegate.message.MessageServiceImpl;
import io.harness.delegate.message.MessengerType;
import io.harness.delegate.provider.DelegateConfigurationServiceProviderImpl;
import io.harness.delegate.provider.DelegatePropertiesServiceProviderImpl;
import io.harness.delegate.serverless.ServerlessAwsLambdaDeployCommandTaskHandler;
import io.harness.delegate.serverless.ServerlessAwsLambdaPrepareRollbackCommandTaskHandler;
import io.harness.delegate.serverless.ServerlessAwsLambdaRollbackCommandTaskHandler;
import io.harness.delegate.serverless.ServerlessCommandTaskHandler;
import io.harness.delegate.service.DelegateAgentService;
import io.harness.delegate.service.DelegateAgentServiceImpl;
import io.harness.delegate.service.DelegateCVActivityLogServiceImpl;
import io.harness.delegate.service.DelegateCVTaskServiceImpl;
import io.harness.delegate.service.DelegateConfigServiceImpl;
import io.harness.delegate.service.DelegateFileManagerImpl;
import io.harness.delegate.service.DelegatePropertyService;
import io.harness.delegate.service.DelegatePropertyServiceImpl;
import io.harness.delegate.service.K8sGlobalConfigServiceImpl;
import io.harness.delegate.service.LogAnalysisStoreServiceImpl;
import io.harness.delegate.service.MetricDataStoreServiceImpl;
import io.harness.delegate.service.tasklogging.DelegateLogServiceImpl;
import io.harness.delegate.task.artifactory.ArtifactoryDelegateTask;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.ami.AMIArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ami.AMIArtifactTaskHandler;
import io.harness.delegate.task.artifacts.ami.AMIArtifactTaskNG;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactTaskHandler;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactTaskNG;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryGenericArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.azure.AcrArtifactTaskHandler;
import io.harness.delegate.task.artifacts.azure.AcrArtifactTaskNG;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsDelegateRequest;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsTaskHandler;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsTaskNG;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactTaskHandler;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactTaskNG;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.custom.CustomArtifactTaskHandler;
import io.harness.delegate.task.artifacts.custom.CustomArtifactTaskNG;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactTaskHandler;
import io.harness.delegate.task.artifacts.docker.DockerArtifactTaskNG;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactTaskNG;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactTaskNG;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactTaskHandler;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactTaskNG;
import io.harness.delegate.task.artifacts.googleartifactregistry.GARArtifactTaskNG;
import io.harness.delegate.task.artifacts.googlecloudsource.GoogleCloudSourceArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.googlecloudsource.GoogleCloudSourceArtifactTaskHandler;
import io.harness.delegate.task.artifacts.googlecloudsource.GoogleCloudSourceArtifactTaskNG;
import io.harness.delegate.task.artifacts.googlecloudstorage.GoogleCloudStorageArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.googlecloudstorage.GoogleCloudStorageArtifactTaskHandler;
import io.harness.delegate.task.artifacts.googlecloudstorage.GoogleCloudStorageArtifactTaskNG;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactTaskHandler;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactTaskNG;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactTaskHandler;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactTaskNG;
import io.harness.delegate.task.artifacts.s3.S3ArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.s3.S3ArtifactTaskHandler;
import io.harness.delegate.task.artifacts.s3.S3ArtifactTaskNG;
import io.harness.delegate.task.aws.AwsCodeCommitApiDelegateTask;
import io.harness.delegate.task.aws.AwsCodeCommitDelegateTask;
import io.harness.delegate.task.aws.AwsDelegateTask;
import io.harness.delegate.task.aws.AwsEKSListClustersDelegateTaskNG;
import io.harness.delegate.task.aws.S3FetchFilesTaskNG;
import io.harness.delegate.task.aws.asg.AsgBlueGreenDeployTaskNG;
import io.harness.delegate.task.aws.asg.AsgBlueGreenPrepareRollbackDataTaskNG;
import io.harness.delegate.task.aws.asg.AsgBlueGreenRollbackTaskNG;
import io.harness.delegate.task.aws.asg.AsgBlueGreenSwapServiceTaskNG;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteTaskNG;
import io.harness.delegate.task.aws.asg.AsgCanaryDeployTaskNG;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataTaskNG;
import io.harness.delegate.task.aws.asg.AsgRollingDeployTaskNG;
import io.harness.delegate.task.aws.asg.AsgRollingRollbackTaskNG;
import io.harness.delegate.task.aws.lambda.AwsLambdaDeployTask;
import io.harness.delegate.task.aws.lambda.AwsLambdaPrepareRollbackTask;
import io.harness.delegate.task.aws.lambda.AwsLambdaRollbackTask;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters.AzureAppServiceTaskType;
import io.harness.delegate.task.azure.appservice.webapp.AzureWebAppTaskNG;
import io.harness.delegate.task.azure.appservice.webapp.handler.AzureWebAppFetchPreDeploymentDataRequestHandler;
import io.harness.delegate.task.azure.appservice.webapp.handler.AzureWebAppRequestHandler;
import io.harness.delegate.task.azure.appservice.webapp.handler.AzureWebAppRollbackRequestHandler;
import io.harness.delegate.task.azure.appservice.webapp.handler.AzureWebAppSlotDeploymentRequestHandler;
import io.harness.delegate.task.azure.appservice.webapp.handler.AzureWebAppSlotSwapRequestHandler;
import io.harness.delegate.task.azure.appservice.webapp.handler.AzureWebAppTrafficShiftRequestHandler;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppRequestType;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppTaskRequest;
import io.harness.delegate.task.azure.arm.AzureARMBaseHelperImpl;
import io.harness.delegate.task.azure.arm.AzureARMTaskType;
import io.harness.delegate.task.azure.arm.AzureResourceCreationBaseHelper;
import io.harness.delegate.task.azure.arm.AzureResourceCreationTaskNG;
import io.harness.delegate.task.azure.arm.handlers.AzureCreateArmResourceTaskHandler;
import io.harness.delegate.task.azure.arm.handlers.AzureCreateBlueprintTaskHandler;
import io.harness.delegate.task.azure.arm.handlers.AzureResourceCreationAbstractTaskHandler;
import io.harness.delegate.task.azure.arm.handlers.FetchArmPreDeploymentDataTaskHandler;
import io.harness.delegate.task.azure.artifact.AzureArtifactDownloadService;
import io.harness.delegate.task.azure.artifact.AzureArtifactDownloadServiceImpl;
import io.harness.delegate.task.azure.exception.AzureARMRuntimeExceptionHandler;
import io.harness.delegate.task.azure.exception.AzureAppServicesRuntimeExceptionHandler;
import io.harness.delegate.task.azure.exception.AzureBPRuntimeExceptionHandler;
import io.harness.delegate.task.azure.exception.AzureClientExceptionHandler;
import io.harness.delegate.task.azure.resource.operation.AzureResourceProvider;
import io.harness.delegate.task.azureartifacts.AzureArtifactsTestConnectionDelegateTask;
import io.harness.delegate.task.azureartifacts.AzureArtifactsValidationHandler;
import io.harness.delegate.task.bamboo.BambooTestConnectionDelegateTask;
import io.harness.delegate.task.bamboo.BambooValidationHandler;
import io.harness.delegate.task.buildsource.BuildSourceTask;
import io.harness.delegate.task.cek8s.CEKubernetesTestConnectionDelegateTask;
import io.harness.delegate.task.cek8s.CEKubernetesValidationHandler;
import io.harness.delegate.task.cf.PcfCommandTask;
import io.harness.delegate.task.ci.CIBuildStatusPushTask;
import io.harness.delegate.task.citasks.CICleanupTask;
import io.harness.delegate.task.citasks.CIExecuteStepTask;
import io.harness.delegate.task.citasks.CIInitializeTask;
import io.harness.delegate.task.citasks.ExecuteCommandTask;
import io.harness.delegate.task.cloudformation.CloudformationBaseHelper;
import io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl;
import io.harness.delegate.task.cloudformation.CloudformationTaskNG;
import io.harness.delegate.task.cloudformation.CloudformationTaskType;
import io.harness.delegate.task.cloudformation.handlers.CloudformationAbstractTaskHandler;
import io.harness.delegate.task.cloudformation.handlers.CloudformationCreateStackTaskHandler;
import io.harness.delegate.task.cloudformation.handlers.CloudformationDeleteStackTaskHandler;
import io.harness.delegate.task.common.DelegateRunnableTask;
import io.harness.delegate.task.customdeployment.FetchInstanceScriptTaskNG;
import io.harness.delegate.task.cvng.CVConnectorValidationHandler;
import io.harness.delegate.task.docker.DockerTestConnectionDelegateTask;
import io.harness.delegate.task.ecs.EcsCommandTaskNG;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsGitFetchRunTask;
import io.harness.delegate.task.ecs.EcsGitFetchTask;
import io.harness.delegate.task.ecs.EcsRunTaskArnTask;
import io.harness.delegate.task.ecs.EcsS3FetchTask;
import io.harness.delegate.task.ecs.EcsTaskArnBlueGreenCreateServiceTaskNG;
import io.harness.delegate.task.ecs.EcsTaskArnCanaryDeployTaskNG;
import io.harness.delegate.task.ecs.EcsTaskArnRollingDeployTaskNG;
import io.harness.delegate.task.elastigroup.ElastigroupBGStageSetupCommandTaskNG;
import io.harness.delegate.task.elastigroup.ElastigroupDeployTask;
import io.harness.delegate.task.elastigroup.ElastigroupParametersFetchTask;
import io.harness.delegate.task.elastigroup.ElastigroupPreFetchTaskNG;
import io.harness.delegate.task.elastigroup.ElastigroupRollbackTask;
import io.harness.delegate.task.elastigroup.ElastigroupSetupCommandTaskNG;
import io.harness.delegate.task.elastigroup.ElastigroupStartupScriptFetchRunTask;
import io.harness.delegate.task.elastigroup.ElastigroupSwapRouteCommandTaskNG;
import io.harness.delegate.task.executioncapability.BatchCapabilityCheckTask;
import io.harness.delegate.task.gcp.GcpProjectTask;
import io.harness.delegate.task.gcp.GcpTask;
import io.harness.delegate.task.gcp.GcpTaskType;
import io.harness.delegate.task.gcp.GcsBucketPerProjectTask;
import io.harness.delegate.task.gcp.taskhandlers.GcpListBucketsTaskHandler;
import io.harness.delegate.task.gcp.taskhandlers.GcpListClustersTaskHandler;
import io.harness.delegate.task.gcp.taskhandlers.TaskHandler;
import io.harness.delegate.task.git.GitFetchTaskNG;
import io.harness.delegate.task.git.NGGitCommandTask;
import io.harness.delegate.task.git.NGGitOpsCommandTask;
import io.harness.delegate.task.gitapi.DecryptGitAPIAccessTask;
import io.harness.delegate.task.gitapi.GitApiTask;
import io.harness.delegate.task.gitcommon.GitTaskNG;
import io.harness.delegate.task.gitops.GitOpsFetchAppTask;
import io.harness.delegate.task.googlefunction.GoogleFunctionCommandTask;
import io.harness.delegate.task.googlefunction.GoogleFunctionDeployTask;
import io.harness.delegate.task.googlefunction.GoogleFunctionDeployWithoutTrafficTask;
import io.harness.delegate.task.googlefunction.GoogleFunctionGenOneDeployTask;
import io.harness.delegate.task.googlefunction.GoogleFunctionGenOnePrepareRollbackTask;
import io.harness.delegate.task.googlefunction.GoogleFunctionGenOneRollbackTask;
import io.harness.delegate.task.googlefunction.GoogleFunctionPrepareRollbackTask;
import io.harness.delegate.task.googlefunction.GoogleFunctionRollbackTask;
import io.harness.delegate.task.googlefunction.GoogleFunctionTrafficShiftTask;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionCommandTypeNG;
import io.harness.delegate.task.helm.HelmCommandTaskNG;
import io.harness.delegate.task.helm.HelmDeployServiceImplNG;
import io.harness.delegate.task.helm.HelmDeployServiceNG;
import io.harness.delegate.task.helm.HelmFetchChartVersionTaskNG;
import io.harness.delegate.task.helm.HelmValuesFetchTaskNG;
import io.harness.delegate.task.helm.HttpHelmConnectivityDelegateTask;
import io.harness.delegate.task.helm.HttpHelmValidationHandler;
import io.harness.delegate.task.helm.OciHelmConnectivityDelegateTask;
import io.harness.delegate.task.helm.OciHelmDockerApiListTagsDelegateTask;
import io.harness.delegate.task.helm.OciHelmValidationHandler;
import io.harness.delegate.task.jenkins.JenkinsTestConnectionDelegateTask;
import io.harness.delegate.task.jenkins.JenkinsValidationHandler;
import io.harness.delegate.task.jira.JiraTaskNG;
import io.harness.delegate.task.jira.JiraValidationHandler;
import io.harness.delegate.task.jira.connection.JiraTestConnectionTaskNG;
import io.harness.delegate.task.k8s.K8sFetchServiceAccountTask;
import io.harness.delegate.task.k8s.K8sTaskNG;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.delegate.task.k8s.KubernetesTestConnectionDelegateTask;
import io.harness.delegate.task.k8s.KubernetesValidationHandler;
import io.harness.delegate.task.k8s.exception.KubernetesApiClientRuntimeExceptionHandler;
import io.harness.delegate.task.k8s.exception.KubernetesApiExceptionHandler;
import io.harness.delegate.task.k8s.exception.KubernetesCliRuntimeExceptionHandler;
import io.harness.delegate.task.ldap.NGLdapGroupSearchTask;
import io.harness.delegate.task.ldap.NGLdapGroupSyncTask;
import io.harness.delegate.task.ldap.NGLdapTestAuthenticationTask;
import io.harness.delegate.task.ldap.NGLdapValidateConnectionSettingTask;
import io.harness.delegate.task.ldap.NGLdapValidateGroupQuerySettingTask;
import io.harness.delegate.task.ldap.NGLdapValidateUserQuerySettingTask;
import io.harness.delegate.task.manifests.CustomManifestFetchTask;
import io.harness.delegate.task.manifests.CustomManifestFetchTaskNG;
import io.harness.delegate.task.manifests.CustomManifestValuesFetchTask;
import io.harness.delegate.task.microsoftteams.MicrosoftTeamsSenderDelegateTask;
import io.harness.delegate.task.nexus.NexusDelegateTask;
import io.harness.delegate.task.nexus.NexusValidationHandler;
import io.harness.delegate.task.pagerduty.PagerDutySenderDelegateTask;
import io.harness.delegate.task.pcf.CfCommandRequest.PcfCommandType;
import io.harness.delegate.task.pcf.TasConnectorValidationTask;
import io.harness.delegate.task.pdc.HostConnectivityValidationDelegateTask;
import io.harness.delegate.task.scm.ScmBatchGetFileTask;
import io.harness.delegate.task.scm.ScmDelegateClientImpl;
import io.harness.delegate.task.scm.ScmGitFileTask;
import io.harness.delegate.task.scm.ScmGitPRTask;
import io.harness.delegate.task.scm.ScmGitRefTask;
import io.harness.delegate.task.scm.ScmGitWebhookTask;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTask;
import io.harness.delegate.task.scm.ScmPushTask;
import io.harness.delegate.task.serverless.ServerlessCommandTask;
import io.harness.delegate.task.serverless.ServerlessCommandType;
import io.harness.delegate.task.serverless.ServerlessGitFetchTask;
import io.harness.delegate.task.serverless.ServerlessS3FetchTask;
import io.harness.delegate.task.servicenow.ServiceNowTaskNG;
import io.harness.delegate.task.servicenow.ServiceNowValidationHandler;
import io.harness.delegate.task.servicenow.connection.ServiceNowTestConnectionTaskNG;
import io.harness.delegate.task.shell.CommandTaskNG;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.delegate.task.shell.WinRmShellScriptTaskNG;
import io.harness.delegate.task.shell.provisioner.ShellScriptProvisionTaskNG;
import io.harness.delegate.task.shell.ssh.ArtifactCommandUnitHandler;
import io.harness.delegate.task.shell.ssh.ArtifactoryCommandUnitHandler;
import io.harness.delegate.task.shell.ssh.AwsS3ArtifactCommandUnitHandler;
import io.harness.delegate.task.shell.ssh.AzureArtifactCommandUnitHandler;
import io.harness.delegate.task.shell.ssh.CommandHandler;
import io.harness.delegate.task.shell.ssh.JenkinsArtifactCommandUnitHandler;
import io.harness.delegate.task.shell.ssh.NexusArtifactCommandUnitHandler;
import io.harness.delegate.task.shell.ssh.SshCleanupCommandHandler;
import io.harness.delegate.task.shell.ssh.SshCopyCommandHandler;
import io.harness.delegate.task.shell.ssh.SshDownloadArtifactCommandHandler;
import io.harness.delegate.task.shell.ssh.SshInitCommandHandler;
import io.harness.delegate.task.shell.ssh.SshScriptCommandHandler;
import io.harness.delegate.task.shell.winrm.WinRmCleanupCommandHandler;
import io.harness.delegate.task.shell.winrm.WinRmCopyCommandHandler;
import io.harness.delegate.task.shell.winrm.WinRmDownloadArtifactCommandHandler;
import io.harness.delegate.task.shell.winrm.WinRmInitCommandHandler;
import io.harness.delegate.task.shell.winrm.WinRmScriptCommandHandler;
import io.harness.delegate.task.slack.SlackSenderDelegateTask;
import io.harness.delegate.task.spot.SpotDelegateTask;
import io.harness.delegate.task.ssh.NGCommandUnitType;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactType;
import io.harness.delegate.task.stepstatus.StepStatusTask;
import io.harness.delegate.task.tas.TasAppResizeTask;
import io.harness.delegate.task.tas.TasBGSetupTask;
import io.harness.delegate.task.tas.TasBasicSetupTask;
import io.harness.delegate.task.tas.TasCommandTask;
import io.harness.delegate.task.tas.TasDataFetchTask;
import io.harness.delegate.task.tas.TasRollbackTask;
import io.harness.delegate.task.tas.TasRollingDeploymentTask;
import io.harness.delegate.task.tas.TasRollingRollbackTask;
import io.harness.delegate.task.tas.TasRouteMappingTask;
import io.harness.delegate.task.tas.TasSwapRollbackTask;
import io.harness.delegate.task.tas.TasSwapRouteTask;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformBaseHelper;
import io.harness.delegate.task.terraform.TerraformBaseHelperImpl;
import io.harness.delegate.task.terraform.TerraformTaskNG;
import io.harness.delegate.task.terraform.cleanup.TerraformSecretCleanupTaskNG;
import io.harness.delegate.task.terraform.handlers.TerraformAbstractTaskHandler;
import io.harness.delegate.task.terraform.handlers.TerraformApplyTaskHandler;
import io.harness.delegate.task.terraform.handlers.TerraformDestroyTaskHandler;
import io.harness.delegate.task.terraform.handlers.TerraformPlanTaskHandler;
import io.harness.delegate.task.terraformcloud.TerraformCloudCleanupTaskNG;
import io.harness.delegate.task.terraformcloud.TerraformCloudTaskNG;
import io.harness.delegate.task.terragrunt.TerragruntApplyTaskNG;
import io.harness.delegate.task.terragrunt.TerragruntDestroyTaskNG;
import io.harness.delegate.task.terragrunt.TerragruntPlanTaskNG;
import io.harness.delegate.task.terragrunt.TerragruntRollbackTaskNG;
import io.harness.delegate.task.trigger.TriggerAuthenticationTask;
import io.harness.delegate.task.winrm.ArtifactDownloadHandler;
import io.harness.delegate.task.winrm.ArtifactoryArtifactDownloadHandler;
import io.harness.delegate.task.winrm.AwsS3ArtifactDownloadHandler;
import io.harness.delegate.task.winrm.AzureArtifactDownloadHandler;
import io.harness.delegate.task.winrm.JenkinsArtifactDownloadHandler;
import io.harness.delegate.task.winrm.NexusArtifactDownloadHandler;
import io.harness.delegate.utils.DecryptionHelperDelegate;
import io.harness.delegatetasks.DeleteSecretTask;
import io.harness.delegatetasks.EncryptSecretTask;
import io.harness.delegatetasks.EncryptSecretTaskValidationHandler;
import io.harness.delegatetasks.FetchCustomSecretTask;
import io.harness.delegatetasks.FetchSecretTask;
import io.harness.delegatetasks.NGAzureKeyVaultFetchEngineTask;
import io.harness.delegatetasks.NGVaultFetchEngineTask;
import io.harness.delegatetasks.NGVaultRenewalAppRoleTask;
import io.harness.delegatetasks.NGVaultRenewalTask;
import io.harness.delegatetasks.NGVaultTokenLookupTask;
import io.harness.delegatetasks.ResolveCustomSecretManagerConfigTask;
import io.harness.delegatetasks.UpsertSecretTask;
import io.harness.delegatetasks.UpsertSecretTaskValidationHandler;
import io.harness.delegatetasks.ValidateCustomSecretManagerSecretReferenceTask;
import io.harness.delegatetasks.ValidateSecretManagerConfigurationTask;
import io.harness.delegatetasks.ValidateSecretReferenceTask;
import io.harness.encryptors.CustomEncryptor;
import io.harness.encryptors.Encryptors;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.clients.AwsKmsEncryptor;
import io.harness.encryptors.clients.AwsSecretsManagerEncryptor;
import io.harness.encryptors.clients.AzureVaultEncryptor;
import io.harness.encryptors.clients.CustomSecretsManagerEncryptor;
import io.harness.encryptors.clients.GcpKmsEncryptor;
import io.harness.encryptors.clients.GcpSecretsManagerEncryptor;
import io.harness.encryptors.clients.HashicorpVaultEncryptor;
import io.harness.encryptors.clients.LocalEncryptor;
import io.harness.encryptors.clients.NGCustomSecretManagerEncryptor;
import io.harness.exception.DelegateServiceDriverExceptionHandler;
import io.harness.exception.ExplanationException;
import io.harness.exception.KeyManagerBuilderException;
import io.harness.exception.TrustManagerBuilderException;
import io.harness.exception.exceptionmanager.ExceptionModule;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.gcp.client.GcpClient;
import io.harness.gcp.impl.GcpClientImpl;
import io.harness.git.GitClientV2;
import io.harness.git.GitClientV2Impl;
import io.harness.googlefunctions.GoogleCloudFunctionClient;
import io.harness.googlefunctions.GoogleCloudFunctionClientImpl;
import io.harness.googlefunctions.GoogleCloudFunctionGenOneClient;
import io.harness.googlefunctions.GoogleCloudFunctionGenOneClientImpl;
import io.harness.googlefunctions.GoogleCloudRunClient;
import io.harness.googlefunctions.GoogleCloudRunClientImpl;
import io.harness.helm.HelmClient;
import io.harness.helm.HelmClientImpl;
import io.harness.helpers.EncryptDecryptHelperImpl;
import io.harness.http.HttpService;
import io.harness.http.HttpServiceImpl;
import io.harness.impl.scm.ScmServiceClientImpl;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.KubernetesContainerServiceImpl;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.manifest.CustomManifestService;
import io.harness.manifest.CustomManifestServiceImpl;
import io.harness.nexus.service.NexusRegistryService;
import io.harness.nexus.service.NexusRegistryServiceImpl;
import io.harness.openshift.OpenShiftClient;
import io.harness.openshift.OpenShiftClientImpl;
import io.harness.pcf.CfCliClient;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.CfDeploymentManagerImpl;
import io.harness.pcf.CfSdkClient;
import io.harness.pcf.cfcli.client.CfCliClientImpl;
import io.harness.pcf.cfsdk.CfSdkClientImpl;
import io.harness.perpetualtask.internal.AssignmentTask;
import io.harness.perpetualtask.manifest.HelmRepositoryService;
import io.harness.perpetualtask.manifest.ManifestRepositoryService;
import io.harness.perpetualtask.polling.manifest.HelmChartCollectionService;
import io.harness.perpetualtask.polling.manifest.ManifestCollectionService;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.secrets.SecretDecryptor;
import io.harness.secrets.SecretsDelegateCacheHelperService;
import io.harness.secrets.SecretsDelegateCacheHelperServiceImpl;
import io.harness.secrets.SecretsDelegateCacheService;
import io.harness.secrets.SecretsDelegateCacheServiceImpl;
import io.harness.security.X509KeyManagerBuilder;
import io.harness.security.X509TrustManagerBuilder;
import io.harness.security.encryption.DelegateDecryptionService;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.service.ScmServiceClient;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutionService;
import io.harness.shell.ShellExecutionServiceImpl;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.SpotInstHelperServiceDelegateImpl;
import io.harness.terraform.TerraformClient;
import io.harness.terraform.TerraformClientImpl;
import io.harness.terraformcloud.TerraformCloudClient;
import io.harness.terraformcloud.TerraformCloudClientImpl;
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
import software.wings.beans.trigger.WebHookTriggerTask;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.cloudprovider.aws.AwsClusterServiceImpl;
import software.wings.cloudprovider.aws.AwsCodeDeployService;
import software.wings.cloudprovider.aws.AwsCodeDeployServiceImpl;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.cloudprovider.aws.EcsContainerServiceImpl;
import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.cloudprovider.gke.GkeClusterServiceImpl;
import software.wings.core.ssh.executors.SshExecutorFactory;
import software.wings.delegatetasks.APMDataCollectionTask;
import software.wings.delegatetasks.AppdynamicsDataCollectionTask;
import software.wings.delegatetasks.BambooTask;
import software.wings.delegatetasks.CloudWatchDataCollectionTask;
import software.wings.delegatetasks.CollaborationProviderTask;
import software.wings.delegatetasks.CommandTask;
import software.wings.delegatetasks.ConnectivityValidationTask;
import software.wings.delegatetasks.CustomLogDataCollectionTask;
import software.wings.delegatetasks.DelegateCVActivityLogService;
import software.wings.delegatetasks.DelegateCVTaskService;
import software.wings.delegatetasks.DelegateConfigService;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.DynaTraceDataCollectionTask;
import software.wings.delegatetasks.EcsSteadyStateCheckTask;
import software.wings.delegatetasks.ElkLogzDataCollectionTask;
import software.wings.delegatetasks.GcbTask;
import software.wings.delegatetasks.GitCommandTask;
import software.wings.delegatetasks.GitFetchFilesTask;
import software.wings.delegatetasks.HostValidationTask;
import software.wings.delegatetasks.HttpTask;
import software.wings.delegatetasks.JenkinsTask;
import software.wings.delegatetasks.KubernetesSteadyStateCheckTask;
import software.wings.delegatetasks.KubernetesSwapServiceSelectorsTask;
import software.wings.delegatetasks.LogAnalysisStoreService;
import software.wings.delegatetasks.MetricDataStoreService;
import software.wings.delegatetasks.NewRelicDataCollectionTask;
import software.wings.delegatetasks.NewRelicDeploymentMarkerTask;
import software.wings.delegatetasks.PerpetualTaskCapabilityCheckTask;
import software.wings.delegatetasks.ServiceImplDelegateTask;
import software.wings.delegatetasks.ShellScriptTask;
import software.wings.delegatetasks.StackDriverDataCollectionTask;
import software.wings.delegatetasks.StackDriverLogDataCollectionTask;
import software.wings.delegatetasks.SumoDataCollectionTask;
import software.wings.delegatetasks.TerraformFetchTargetsTask;
import software.wings.delegatetasks.TerraformInputVariablesObtainTask;
import software.wings.delegatetasks.TerraformProvisionTask;
import software.wings.delegatetasks.TerragruntProvisionTask;
import software.wings.delegatetasks.TriggerTask;
import software.wings.delegatetasks.aws.AwsAmiAsyncTask;
import software.wings.delegatetasks.aws.AwsAsgTask;
import software.wings.delegatetasks.aws.AwsCFTask;
import software.wings.delegatetasks.aws.AwsCodeDeployTask;
import software.wings.delegatetasks.aws.AwsEc2Task;
import software.wings.delegatetasks.aws.AwsEcrTask;
import software.wings.delegatetasks.aws.AwsEcsTask;
import software.wings.delegatetasks.aws.AwsElbTask;
import software.wings.delegatetasks.aws.AwsIamTask;
import software.wings.delegatetasks.aws.AwsLambdaTask;
import software.wings.delegatetasks.aws.AwsRoute53Task;
import software.wings.delegatetasks.aws.AwsS3Task;
import software.wings.delegatetasks.aws.ecs.EcsCommandTask;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsBlueGreenRoute53DNSWeightHandler;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsBlueGreenRoute53SetupCommandHandler;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsBlueGreenSetupCommandHandler;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsCommandTaskHandler;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsListenerUpdateBGTaskHandler;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsSetupCommandHandler;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.deploy.EcsDeployCommandHandler;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.deploy.EcsDeployRollbackDataFetchCommandHandler;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.deploy.EcsRunTaskDeployCommandHandler;
import software.wings.delegatetasks.azure.AzureTask;
import software.wings.delegatetasks.azure.AzureVMSSTask;
import software.wings.delegatetasks.azure.appservice.AbstractAzureAppServiceTaskHandler;
import software.wings.delegatetasks.azure.appservice.AzureAppServiceTask;
import software.wings.delegatetasks.azure.appservice.webapp.taskhandler.AzureWebAppListWebAppDeploymentSlotNamesTaskHandler;
import software.wings.delegatetasks.azure.appservice.webapp.taskhandler.AzureWebAppListWebAppInstancesTaskHandler;
import software.wings.delegatetasks.azure.appservice.webapp.taskhandler.AzureWebAppListWebAppNamesTaskHandler;
import software.wings.delegatetasks.azure.appservice.webapp.taskhandler.AzureWebAppRollbackTaskHandler;
import software.wings.delegatetasks.azure.appservice.webapp.taskhandler.AzureWebAppSlotSetupTaskHandler;
import software.wings.delegatetasks.azure.appservice.webapp.taskhandler.AzureWebAppSlotShiftTrafficTaskHandler;
import software.wings.delegatetasks.azure.appservice.webapp.taskhandler.AzureWebAppSlotSwapTaskHandler;
import software.wings.delegatetasks.azure.arm.AbstractAzureARMTaskHandler;
import software.wings.delegatetasks.azure.arm.AzureARMTask;
import software.wings.delegatetasks.azure.arm.taskhandler.AzureARMDeploymentTaskHandler;
import software.wings.delegatetasks.azure.arm.taskhandler.AzureARMListManagementGroupTaskHandler;
import software.wings.delegatetasks.azure.arm.taskhandler.AzureARMListSubscriptionLocationsTaskHandler;
import software.wings.delegatetasks.azure.arm.taskhandler.AzureARMRollbackTaskHandler;
import software.wings.delegatetasks.azure.arm.taskhandler.AzureBlueprintDeploymentTaskHandler;
import software.wings.delegatetasks.azure.resource.AzureResourceTask;
import software.wings.delegatetasks.azure.resource.taskhandler.ACRResourceProviderTaskHandler;
import software.wings.delegatetasks.azure.resource.taskhandler.AbstractAzureResourceTaskHandler;
import software.wings.delegatetasks.azure.resource.taskhandler.AzureK8sResourceProviderTaskHandler;
import software.wings.delegatetasks.cloudformation.CloudFormationCommandTask;
import software.wings.delegatetasks.collect.artifacts.AmazonS3CollectionTask;
import software.wings.delegatetasks.collect.artifacts.ArtifactoryCollectionTask;
import software.wings.delegatetasks.collect.artifacts.AzureArtifactsCollectionTask;
import software.wings.delegatetasks.collect.artifacts.JenkinsCollectionTask;
import software.wings.delegatetasks.collect.artifacts.NexusCollectionTask;
import software.wings.delegatetasks.container.ContainerDummyTask;
import software.wings.delegatetasks.cv.LogDataCollectionTask;
import software.wings.delegatetasks.cv.MetricsDataCollectionTask;
import software.wings.delegatetasks.cvng.K8InfoDataService;
import software.wings.delegatetasks.helm.ArtifactoryHelmRepositoryService;
import software.wings.delegatetasks.helm.HelmCollectChartTask;
import software.wings.delegatetasks.helm.HelmCommandTask;
import software.wings.delegatetasks.helm.HelmRepoConfigValidationTask;
import software.wings.delegatetasks.helm.HelmValuesFetchTask;
import software.wings.delegatetasks.helm.ManifestRepoServiceType;
import software.wings.delegatetasks.jira.JiraTask;
import software.wings.delegatetasks.jira.ShellScriptApprovalTask;
import software.wings.delegatetasks.k8s.K8sTask;
import software.wings.delegatetasks.k8s.taskhandler.K8sApplyTaskHandler;
import software.wings.delegatetasks.k8s.taskhandler.K8sBlueGreenDeployTaskHandler;
import software.wings.delegatetasks.k8s.taskhandler.K8sCanaryDeployTaskHandler;
import software.wings.delegatetasks.k8s.taskhandler.K8sDeleteTaskHandler;
import software.wings.delegatetasks.k8s.taskhandler.K8sInstanceSyncTaskHandler;
import software.wings.delegatetasks.k8s.taskhandler.K8sRollingDeployRollbackTaskHandler;
import software.wings.delegatetasks.k8s.taskhandler.K8sRollingDeployTaskHandler;
import software.wings.delegatetasks.k8s.taskhandler.K8sScaleTaskHandler;
import software.wings.delegatetasks.k8s.taskhandler.K8sTaskHandler;
import software.wings.delegatetasks.k8s.taskhandler.K8sTrafficSplitTaskHandler;
import software.wings.delegatetasks.k8s.taskhandler.K8sVersionTaskHandler;
import software.wings.delegatetasks.pcf.pcftaskhandler.PcfSetupCommandTaskHandler;
import software.wings.delegatetasks.rancher.RancherResolveClustersTask;
import software.wings.delegatetasks.s3.S3FetchFilesTask;
import software.wings.delegatetasks.servicenow.ServicenowTask;
import software.wings.delegatetasks.shellscript.provisioner.ShellScriptProvisionTask;
import software.wings.delegatetasks.spotinst.SpotInstTask;
import software.wings.delegatetasks.terraform.TerraformConfigInspectClient;
import software.wings.delegatetasks.terraform.helper.TerraformConfigInspectClientImpl;
import software.wings.helpers.ext.amazons3.AmazonS3Service;
import software.wings.helpers.ext.amazons3.AmazonS3ServiceImpl;
import software.wings.helpers.ext.ami.AmiService;
import software.wings.helpers.ext.ami.AmiServiceImpl;
import software.wings.helpers.ext.artifactory.ArtifactoryService;
import software.wings.helpers.ext.artifactory.ArtifactoryServiceImpl;
import software.wings.helpers.ext.azure.AcrService;
import software.wings.helpers.ext.azure.AcrServiceImpl;
import software.wings.helpers.ext.azure.devops.AzureArtifactsService;
import software.wings.helpers.ext.azure.devops.AzureArtifactsServiceImpl;
import software.wings.helpers.ext.bamboo.BambooCollectionTask;
import software.wings.helpers.ext.bamboo.BambooService;
import software.wings.helpers.ext.bamboo.BambooServiceImpl;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.customrepository.CustomRepositoryService;
import software.wings.helpers.ext.customrepository.CustomRepositoryServiceImpl;
import software.wings.helpers.ext.ecr.EcrClassicService;
import software.wings.helpers.ext.ecr.EcrClassicServiceImpl;
import software.wings.helpers.ext.ecr.EcrService;
import software.wings.helpers.ext.ecr.EcrServiceImpl;
import software.wings.helpers.ext.ecs.request.EcsCommandRequest.EcsCommandType;
import software.wings.helpers.ext.gcb.GcbService;
import software.wings.helpers.ext.gcb.GcbServiceImpl;
import software.wings.helpers.ext.gcs.GcsService;
import software.wings.helpers.ext.gcs.GcsServiceImpl;
import software.wings.helpers.ext.helm.HelmDeployService;
import software.wings.helpers.ext.helm.HelmDeployServiceImpl;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.helpers.ext.jenkins.JenkinsFactory;
import software.wings.helpers.ext.jenkins.JenkinsImpl;
import software.wings.helpers.ext.nexus.NexusService;
import software.wings.helpers.ext.nexus.NexusServiceImpl;
import software.wings.helpers.ext.sftp.SftpService;
import software.wings.helpers.ext.sftp.SftpServiceImpl;
import software.wings.helpers.ext.smb.SmbService;
import software.wings.helpers.ext.smb.SmbServiceImpl;
import software.wings.misc.MemoryHelper;
import software.wings.service.EcrClassicBuildServiceImpl;
import software.wings.service.impl.AcrBuildServiceImpl;
import software.wings.service.impl.AmazonS3BuildServiceImpl;
import software.wings.service.impl.AmiBuildServiceImpl;
import software.wings.service.impl.ArtifactoryBuildServiceImpl;
import software.wings.service.impl.AzureArtifactsBuildServiceImpl;
import software.wings.service.impl.AzureMachineImageBuildServiceImpl;
import software.wings.service.impl.BambooBuildServiceImpl;
import software.wings.service.impl.CodeDeployCommandUnitExecutorServiceImpl;
import software.wings.service.impl.ContainerCommandUnitExecutorServiceImpl;
import software.wings.service.impl.ContainerServiceImpl;
import software.wings.service.impl.CustomBuildServiceImpl;
import software.wings.service.impl.DockerBuildServiceImpl;
import software.wings.service.impl.EcrBuildServiceImpl;
import software.wings.service.impl.GcrBuildServiceImpl;
import software.wings.service.impl.GcsBuildServiceImpl;
import software.wings.service.impl.GitServiceImpl;
import software.wings.service.impl.JenkinsBuildServiceImpl;
import software.wings.service.impl.NexusBuildServiceImpl;
import software.wings.service.impl.ServiceCommandExecutorServiceImpl;
import software.wings.service.impl.SftpBuildServiceImpl;
import software.wings.service.impl.SlackMessageSenderImpl;
import software.wings.service.impl.SmbBuildServiceImpl;
import software.wings.service.impl.SshCommandUnitExecutorServiceImpl;
import software.wings.service.impl.TerraformConfigInspectServiceImpl;
import software.wings.service.impl.WinRMCommandUnitExecutorServiceImpl;
import software.wings.service.impl.analysis.APMDelegateService;
import software.wings.service.impl.analysis.APMDelegateServiceImpl;
import software.wings.service.impl.appdynamics.AppdynamicsDelegateServiceImpl;
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
import software.wings.service.impl.aws.delegate.AwsLambdaHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsLambdaHelperServiceDelegateNGImpl;
import software.wings.service.impl.aws.delegate.AwsRoute53HelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsS3HelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsServiceDiscoveryHelperServiceDelegateImpl;
import software.wings.service.impl.bugsnag.BugsnagDelegateService;
import software.wings.service.impl.bugsnag.BugsnagDelegateServiceImpl;
import software.wings.service.impl.cloudwatch.CloudWatchDelegateServiceImpl;
import software.wings.service.impl.dynatrace.DynaTraceDelegateServiceImpl;
import software.wings.service.impl.elk.ElkDelegateServiceImpl;
import software.wings.service.impl.instana.InstanaDelegateServiceImpl;
import software.wings.service.impl.ldap.LdapDelegateServiceImpl;
import software.wings.service.impl.logz.LogzDelegateServiceImpl;
import software.wings.service.impl.newrelic.NewRelicDelgateServiceImpl;
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
import software.wings.service.intfc.AmazonS3BuildService;
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
import software.wings.service.intfc.ServiceCommandExecutorService;
import software.wings.service.intfc.SftpBuildService;
import software.wings.service.intfc.SlackMessageSender;
import software.wings.service.intfc.SmbBuildService;
import software.wings.service.intfc.TerraformConfigInspectService;
import software.wings.service.intfc.appdynamics.AppdynamicsDelegateService;
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
import software.wings.service.intfc.aws.delegate.AwsLambdaHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsLambdaHelperServiceDelegateNG;
import software.wings.service.intfc.aws.delegate.AwsRoute53HelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsS3HelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsServiceDiscoveryHelperServiceDelegate;
import software.wings.service.intfc.cloudwatch.CloudWatchDelegateService;
import software.wings.service.intfc.cvng.CVNGDataCollectionDelegateService;
import software.wings.service.intfc.dynatrace.DynaTraceDelegateService;
import software.wings.service.intfc.elk.ElkDelegateService;
import software.wings.service.intfc.instana.InstanaDelegateService;
import software.wings.service.intfc.ldap.LdapDelegateService;
import software.wings.service.intfc.logz.LogzDelegateService;
import software.wings.service.intfc.newrelic.NewRelicDelegateService;
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
import com.google.inject.name.Names;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;

@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
@OwnedBy(HarnessTeam.DEL)
@BreakDependencyOn("io.harness.delegate.beans.connector.ConnectorType")
@BreakDependencyOn("io.harness.encryptors.clients.CustomSecretsManagerEncryptor")
@BreakDependencyOn("io.harness.exception.DelegateServiceDriverExceptionHandler")
@BreakDependencyOn("io.harness.impl.scm.ScmServiceClientImpl")
@BreakDependencyOn("io.harness.perpetualtask.internal.AssignmentTask")
@BreakDependencyOn("io.harness.perpetualtask.polling.manifest.HelmChartCollectionService")
@BreakDependencyOn("io.harness.perpetualtask.polling.manifest.ManifestCollectionService")
@BreakDependencyOn("io.harness.service.ScmServiceClient")
@BreakDependencyOn("software.wings.api.DeploymentType")
@BreakDependencyOn("software.wings.beans.AwsConfig")
@BreakDependencyOn("software.wings.beans.AzureConfig")
@RequiredArgsConstructor
public class DelegateModule extends AbstractModule {
  private final DelegateConfiguration configuration;

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
  @Named("healthMonitorExecutor")
  public ScheduledExecutorService healthMonitorExecutor() {
    return new ScheduledThreadPoolExecutor(
        20, new ThreadFactoryBuilder().setNameFormat("healthMonitor-%d").setPriority(Thread.MAX_PRIORITY).build());
  }

  @Provides
  public DataCollectionDSLService dataCollectionDSLService() throws KeyManagerBuilderException {
    if (StringUtils.isNotEmpty(configuration.getClientCertificateFilePath())
        && StringUtils.isNotEmpty(configuration.getClientCertificateKeyFilePath())) {
      KeyManager keyManager = new X509KeyManagerBuilder()
                                  .withClientCertificateFromFile(this.configuration.getClientCertificateFilePath(),
                                      this.configuration.getClientCertificateKeyFilePath())
                                  .build();
      return new DataCollectionServiceImpl(keyManager);
    }
    return new DataCollectionServiceImpl();
  }

  /*
   * Creates and return ScheduledExecutorService object, which can be used for monitoring watcher.
   * Note that, this will only be used for monitoring, any action taken will be than executed by some
   * another threadpool.
   */
  @Provides
  @Singleton
  @Named("watcherMonitorExecutor")
  public ScheduledExecutorService watcherMonitorExecutor() {
    return new ScheduledThreadPoolExecutor(
        1, new ThreadFactoryBuilder().setNameFormat("watcherMonitor-%d").setPriority(Thread.MAX_PRIORITY).build());
  }

  /*
   * Creates and return ScheduledExecutorService object, which can be used for reading message from
   * watcher and take appropriate action.
   */
  @Provides
  @Singleton
  @Named("inputExecutor")
  public ScheduledExecutorService inputExecutor() {
    return new ScheduledThreadPoolExecutor(
        1, new ThreadFactoryBuilder().setNameFormat("input-%d").setPriority(Thread.NORM_PRIORITY).build());
  }

  @Provides
  @Singleton
  @Named("verificationExecutor")
  public ScheduledExecutorService verificationExecutor() {
    return new ScheduledThreadPoolExecutor(
        2, new ThreadFactoryBuilder().setNameFormat("verification-%d").setPriority(Thread.NORM_PRIORITY).build());
  }

  @Provides
  @Singleton
  @Named("taskPollExecutor")
  public ScheduledExecutorService taskPollExecutor() {
    return new ScheduledThreadPoolExecutor(
        4, new ThreadFactoryBuilder().setNameFormat("task-poll-%d").setPriority(Thread.MAX_PRIORITY).build());
  }

  /*
   * Creates and return ExecutorService object, which can be used for performing low priority activities.
   * Currently, this is being used for performing graceful stop.
   */
  @Provides
  @Singleton
  @Named("backgroundExecutor")
  public ExecutorService backgroundExecutor() {
    return ThreadPool.create(1, 5, 5, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("background-%d").setPriority(Thread.MIN_PRIORITY).build());
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
  @Named("grpcServiceExecutor")
  public ExecutorService grpcServiceExecutor() {
    return Executors.newFixedThreadPool(
        1, new ThreadFactoryBuilder().setNameFormat("grpc-%d").setPriority(Thread.MAX_PRIORITY).build());
  }

  @Provides
  @Singleton
  @Named("taskProgressExecutor")
  public ExecutorService taskProgressExecutor() {
    return Executors.newFixedThreadPool(
        10, new ThreadFactoryBuilder().setNameFormat("taskProgress-%d").setPriority(Thread.MAX_PRIORITY).build());
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
  @Named("taskExecutor")
  public ThreadPoolExecutor taskExecutor() {
    int maxPoolSize = Integer.MAX_VALUE;
    long delegateXmx = 0;
    try {
      delegateXmx = MemoryHelper.getProcessMaxMemoryMB();
      if (!configuration.isDynamicHandlingOfRequestEnabled()) {
        // Set max threads to 400, if dynamic handling is disabled.
        maxPoolSize = 400;
      }
    } catch (Exception ex) {
      // We failed to fetch memory bean, setting number of threads as 400.
      maxPoolSize = 400;
    }
    log.info(
        "Starting Delegate process with {} MB of Xmx and {} number of execution threads", delegateXmx, maxPoolSize);
    return ThreadPool.create(10, maxPoolSize, 1, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("task-exec-%d").setPriority(Thread.MIN_PRIORITY).build());
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

  // TODO: Club this thread pool with sync/async task thread pool
  @Provides
  @Singleton
  @Named("perpetualTaskTimeoutExecutor")
  public ScheduledExecutorService perpetualTaskTimeoutExecutor() {
    return new ScheduledThreadPoolExecutor(40,
        new ThreadFactoryBuilder()
            .setNameFormat("perpetual-task-timeout-%d")
            .setPriority(Thread.NORM_PRIORITY)
            .build());
  }

  @Provides
  @Singleton
  @Named("delegateAgentMetricsExecutor")
  public ScheduledExecutorService delegateAgentMetricsExecutor() {
    return newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                                                .setNameFormat("delegate-agent-metrics-executor-%d")
                                                .setPriority(Thread.NORM_PRIORITY)
                                                .build());
  }

  @Provides
  @Singleton
  public DefaultAsyncHttpClient defaultAsyncHttpClient()
      throws TrustManagerBuilderException, KeyManagerBuilderException, SSLException {
    TrustManager trustManager = new X509TrustManagerBuilder().trustAllCertificates().build();
    SslContextBuilder sslContextBuilder = SslContextBuilder.forClient().trustManager(trustManager);

    if (StringUtils.isNotEmpty(this.configuration.getClientCertificateFilePath())
        && StringUtils.isNotEmpty(this.configuration.getClientCertificateKeyFilePath())) {
      KeyManager keyManager = new X509KeyManagerBuilder()
                                  .withClientCertificateFromFile(this.configuration.getClientCertificateFilePath(),
                                      this.configuration.getClientCertificateKeyFilePath())
                                  .build();
      sslContextBuilder.keyManager(keyManager);
    }

    SslContext sslContext = sslContextBuilder.build();

    return new DefaultAsyncHttpClient(
        new DefaultAsyncHttpClientConfig.Builder().setUseProxyProperties(true).setSslContext(sslContext).build());
  }

  @Override
  protected void configure() {
    bindDelegateTasks();

    install(VersionModule.getInstance());
    install(TimeModule.getInstance());
    install(new NGDelegateModule());
    install(ExceptionModule.getInstance());

    bind(DelegateConfiguration.class).toInstance(configuration);

    bind(DelegateAgentService.class).to(DelegateAgentServiceImpl.class);
    bind(SecretsDelegateCacheHelperService.class).to(SecretsDelegateCacheHelperServiceImpl.class);
    bind(DelegatePropertyService.class).to(DelegatePropertyServiceImpl.class);
    bind(DelegatePropertiesServiceProvider.class).to(DelegatePropertiesServiceProviderImpl.class);
    bind(DelegateConfigurationServiceProvider.class).to(DelegateConfigurationServiceProviderImpl.class);
    install(new FactoryModuleBuilder().implement(Jenkins.class, JenkinsImpl.class).build(JenkinsFactory.class));
    bind(DelegateFileManager.class).to(DelegateFileManagerImpl.class).asEagerSingleton();
    bind(ServiceCommandExecutorService.class).to(ServiceCommandExecutorServiceImpl.class);
    bind(SshExecutorFactory.class);
    bind(DelegateLogService.class).to(DelegateLogServiceImpl.class);
    bind(MetricDataStoreService.class).to(MetricDataStoreServiceImpl.class);
    bind(LogAnalysisStoreService.class).to(LogAnalysisStoreServiceImpl.class);
    bind(DelegateCVTaskService.class).to(DelegateCVTaskServiceImpl.class);
    bind(DelegateCVActivityLogService.class).to(DelegateCVActivityLogServiceImpl.class);
    bind(DelegateConfigService.class).to(DelegateConfigServiceImpl.class);
    bind(JenkinsBuildService.class).to(JenkinsBuildServiceImpl.class);
    bind(SmbBuildService.class).to(SmbBuildServiceImpl.class);
    bind(SmbService.class).to(SmbServiceImpl.class);
    bind(BambooBuildService.class).to(BambooBuildServiceImpl.class);
    bind(DockerBuildService.class).to(DockerBuildServiceImpl.class);
    bind(BambooService.class).to(BambooServiceImpl.class);
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
    bind(NexusBuildService.class).to(NexusBuildServiceImpl.class);
    bind(NexusService.class).to(NexusServiceImpl.class);
    bind(AppdynamicsDelegateService.class).to(AppdynamicsDelegateServiceImpl.class);
    bind(InstanaDelegateService.class).to(InstanaDelegateServiceImpl.class);
    bind(StackDriverDelegateService.class).to(StackDriverDelegateServiceImpl.class);
    bind(APMDelegateService.class).to(APMDelegateServiceImpl.class);
    bind(NewRelicDelegateService.class).to(NewRelicDelgateServiceImpl.class);
    bind(BugsnagDelegateService.class).to(BugsnagDelegateServiceImpl.class);
    bind(DynaTraceDelegateService.class).to(DynaTraceDelegateServiceImpl.class);
    bind(SplunkDelegateService.class).to(SplunkDelegateServiceImpl.class);
    bind(ElkDelegateService.class).to(ElkDelegateServiceImpl.class);
    bind(LogzDelegateService.class).to(LogzDelegateServiceImpl.class);
    bind(SumoDelegateService.class).to(SumoDelegateServiceImpl.class);
    bind(SplunkDelegateService.class).to(SplunkDelegateServiceImpl.class);
    bind(CloudWatchDelegateService.class).to(CloudWatchDelegateServiceImpl.class);
    bind(ArtifactoryBuildService.class).to(ArtifactoryBuildServiceImpl.class);
    bind(ArtifactoryService.class).to(ArtifactoryServiceImpl.class);
    bind(EcrBuildService.class).to(EcrBuildServiceImpl.class);
    bind(AmazonS3BuildService.class).to(AmazonS3BuildServiceImpl.class);
    bind(AmazonS3Service.class).to(AmazonS3ServiceImpl.class);
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
    bind(AzureArtifactsBuildService.class).to(AzureArtifactsBuildServiceImpl.class);
    bind(HostValidationService.class).to(HostValidationServiceImpl.class);
    bind(ContainerService.class).to(ContainerServiceImpl.class);
    bind(GitClient.class).to(GitClientImpl.class).asEagerSingleton();
    bind(GitClientV2.class).to(GitClientV2Impl.class).asEagerSingleton();
    bind(Clock.class).toInstance(Clock.systemUTC());
    bind(HelmClient.class).to(HelmClientImpl.class);
    bind(TerragruntClient.class).to(TerragruntClientImpl.class);
    bind(OpenShiftClient.class).to(OpenShiftClientImpl.class);
    bind(HelmDeployService.class).to(HelmDeployServiceImpl.class);
    bind(ContainerDeploymentDelegateHelper.class);
    bind(MessageService.class)
        .toInstance(
            new MessageServiceImpl("", Clock.systemUTC(), MessengerType.DELEGATE, DelegateApplication.getProcessId()));
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
    bind(AwsLambdaHelperServiceDelegate.class).to(AwsLambdaHelperServiceDelegateImpl.class);
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
    bind(AzureArtifactsService.class).to(AzureArtifactsServiceImpl.class);
    bind(SecretsDelegateCacheService.class).to(SecretsDelegateCacheServiceImpl.class);
    bind(K8InfoDataService.class).to(K8InfoDataServiceImpl.class);
    bind(TerraformBaseHelper.class).to(TerraformBaseHelperImpl.class);
    bind(DelegateFileManagerBase.class).to(DelegateFileManagerImpl.class);
    bind(TerraformClient.class).to(TerraformClientImpl.class);
    bind(CloudformationBaseHelper.class).to(CloudformationBaseHelperImpl.class);
    bind(HelmDeployServiceNG.class).to(HelmDeployServiceImplNG.class);
    bind(AwsCliClient.class).to(AwsCliClientImpl.class);
    bind(TerraformCloudClient.class).to(TerraformCloudClientImpl.class);

    MapBinder<String, CommandUnitExecutorService> serviceCommandExecutorServiceMapBinder =
        MapBinder.newMapBinder(binder(), String.class, CommandUnitExecutorService.class);
    serviceCommandExecutorServiceMapBinder.addBinding(DeploymentType.ECS.name())
        .to(ContainerCommandUnitExecutorServiceImpl.class);
    serviceCommandExecutorServiceMapBinder.addBinding(DeploymentType.KUBERNETES.name())
        .to(ContainerCommandUnitExecutorServiceImpl.class);
    serviceCommandExecutorServiceMapBinder.addBinding(DeploymentType.SSH.name())
        .to(SshCommandUnitExecutorServiceImpl.class);
    serviceCommandExecutorServiceMapBinder.addBinding(DeploymentType.WINRM.name())
        .to(WinRMCommandUnitExecutorServiceImpl.class);
    serviceCommandExecutorServiceMapBinder.addBinding(DeploymentType.AWS_CODEDEPLOY.name())
        .to(CodeDeployCommandUnitExecutorServiceImpl.class);

    MapBinder<String, PcfCommandTaskHandler> commandTaskTypeToTaskHandlerMap =
        MapBinder.newMapBinder(binder(), String.class, PcfCommandTaskHandler.class);
    commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.SETUP.name()).to(PcfSetupCommandTaskHandler.class);
    commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.RESIZE.name()).to(PcfDeployCommandTaskHandler.class);
    commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.ROLLBACK.name()).to(PcfRollbackCommandTaskHandler.class);
    commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.UPDATE_ROUTE.name())
        .to(PcfRouteUpdateCommandTaskHandler.class);
    commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.VALIDATE.name())
        .to(PcfValidationCommandTaskHandler.class);
    commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.APP_DETAILS.name())
        .to(PcfApplicationDetailsCommandTaskHandler.class);
    commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.DATAFETCH.name())
        .to(PcfDataFetchCommandTaskHandler.class);
    commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.CREATE_ROUTE.name())
        .to(PcfCreatePcfResourceCommandTaskHandler.class);
    commandTaskTypeToTaskHandlerMap.addBinding(PcfCommandType.RUN_PLUGIN.name())
        .to(PcfRunPluginCommandTaskHandler.class);

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
    MapBinder<String, EcsCommandTaskHandler> ecsCommandTaskTypeToTaskHandlerMap =
        MapBinder.newMapBinder(binder(), String.class, EcsCommandTaskHandler.class);
    ecsCommandTaskTypeToTaskHandlerMap.addBinding(EcsCommandType.LISTENER_UPDATE_BG.name())
        .to(EcsListenerUpdateBGTaskHandler.class);
    ecsCommandTaskTypeToTaskHandlerMap.addBinding(EcsCommandType.BG_SERVICE_SETUP.name())
        .to(EcsBlueGreenSetupCommandHandler.class);
    ecsCommandTaskTypeToTaskHandlerMap.addBinding(EcsCommandType.ROUTE53_BG_SERVICE_SETUP.name())
        .to(EcsBlueGreenRoute53SetupCommandHandler.class);
    ecsCommandTaskTypeToTaskHandlerMap.addBinding(EcsCommandType.ROUTE53_DNS_WEIGHT_UPDATE.name())
        .to(EcsBlueGreenRoute53DNSWeightHandler.class);
    ecsCommandTaskTypeToTaskHandlerMap.addBinding(EcsCommandType.SERVICE_SETUP.name()).to(EcsSetupCommandHandler.class);
    ecsCommandTaskTypeToTaskHandlerMap.addBinding(EcsCommandType.SERVICE_DEPLOY.name())
        .to(EcsDeployCommandHandler.class);
    ecsCommandTaskTypeToTaskHandlerMap.addBinding(EcsCommandType.DEPLOY_ROLLBACK_DATA_FETCH.name())
        .to(EcsDeployRollbackDataFetchCommandHandler.class);
    ecsCommandTaskTypeToTaskHandlerMap.addBinding(EcsCommandType.ECS_RUN_TASK_DEPLOY.name())
        .to(EcsRunTaskDeployCommandHandler.class);

    MapBinder<String, K8sTaskHandler> k8sCommandTaskTypeToTaskHandlerMap =
        MapBinder.newMapBinder(binder(), String.class, K8sTaskHandler.class);
    k8sCommandTaskTypeToTaskHandlerMap.addBinding(K8sTaskType.DEPLOYMENT_ROLLING.name())
        .to(K8sRollingDeployTaskHandler.class);
    k8sCommandTaskTypeToTaskHandlerMap.addBinding(K8sTaskType.DEPLOYMENT_ROLLING_ROLLBACK.name())
        .to(K8sRollingDeployRollbackTaskHandler.class);
    k8sCommandTaskTypeToTaskHandlerMap.addBinding(K8sTaskType.CANARY_DEPLOY.name())
        .to(K8sCanaryDeployTaskHandler.class);
    k8sCommandTaskTypeToTaskHandlerMap.addBinding(K8sTaskType.SCALE.name()).to(K8sScaleTaskHandler.class);
    k8sCommandTaskTypeToTaskHandlerMap.addBinding(K8sTaskType.BLUE_GREEN_DEPLOY.name())
        .to(K8sBlueGreenDeployTaskHandler.class);
    k8sCommandTaskTypeToTaskHandlerMap.addBinding(K8sTaskType.INSTANCE_SYNC.name())
        .to(K8sInstanceSyncTaskHandler.class);
    k8sCommandTaskTypeToTaskHandlerMap.addBinding(K8sTaskType.DELETE.name()).to(K8sDeleteTaskHandler.class);
    k8sCommandTaskTypeToTaskHandlerMap.addBinding(K8sTaskType.TRAFFIC_SPLIT.name())
        .to(K8sTrafficSplitTaskHandler.class);
    k8sCommandTaskTypeToTaskHandlerMap.addBinding(K8sTaskType.APPLY.name()).to(K8sApplyTaskHandler.class);
    k8sCommandTaskTypeToTaskHandlerMap.addBinding(K8sTaskType.VERSION.name()).to(K8sVersionTaskHandler.class);

    bind(TerraformConfigInspectClient.class).toInstance(new TerraformConfigInspectClientImpl());
    bind(TerraformConfigInspectService.class).toInstance(new TerraformConfigInspectServiceImpl());
    bind(AzureComputeClient.class).to(AzureComputeClientImpl.class);
    bind(AzureAutoScaleSettingsClient.class).to(AzureAutoScaleSettingsClientImpl.class);
    bind(AzureNetworkClient.class).to(AzureNetworkClientImpl.class);
    bind(AzureMonitorClient.class).to(AzureMonitorClientImpl.class);
    bind(AzureContainerRegistryClient.class).to(AzureContainerRegistryClientImpl.class);
    bind(AzureWebClient.class).to(AzureWebClientImpl.class);
    bind(NGGitService.class).to(NGGitServiceImpl.class);
    bind(GcpClient.class).to(GcpClientImpl.class);
    bind(ManifestRepositoryService.class)
        .annotatedWith(Names.named(ManifestRepoServiceType.HELM_COMMAND_SERVICE))
        .to(HelmRepositoryService.class);
    bind(ManifestRepositoryService.class)
        .annotatedWith(Names.named(ManifestRepoServiceType.ARTIFACTORY_HELM_SERVICE))
        .to(ArtifactoryHelmRepositoryService.class);
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
    k8sTaskTypeToRequestHandler.addBinding(K8sTaskType.DRY_RUN_MANIFEST.name())
        .to(K8sDryRunManifestRequestHandler.class);

    // Terraform Task Handlers
    MapBinder<TFTaskType, TerraformAbstractTaskHandler> tfTaskTypeToHandlerMap =
        MapBinder.newMapBinder(binder(), TFTaskType.class, TerraformAbstractTaskHandler.class);
    tfTaskTypeToHandlerMap.addBinding(TFTaskType.APPLY).to(TerraformApplyTaskHandler.class);
    tfTaskTypeToHandlerMap.addBinding(TFTaskType.PLAN).to(TerraformPlanTaskHandler.class);
    tfTaskTypeToHandlerMap.addBinding(TFTaskType.DESTROY).to(TerraformDestroyTaskHandler.class);

    // Cloudformation Task Handlers
    MapBinder<CloudformationTaskType, CloudformationAbstractTaskHandler> cfnTaskTypeToHandlerMap =
        MapBinder.newMapBinder(binder(), CloudformationTaskType.class, CloudformationAbstractTaskHandler.class);
    cfnTaskTypeToHandlerMap.addBinding(CloudformationTaskType.CREATE_STACK)
        .to(CloudformationCreateStackTaskHandler.class);
    cfnTaskTypeToHandlerMap.addBinding(CloudformationTaskType.DELETE_STACK)
        .to(CloudformationDeleteStackTaskHandler.class);

    // Azure ARM/BP Task handlers
    MapBinder<AzureARMTaskType, AzureResourceCreationAbstractTaskHandler> azTaskTypeToHandlerMap =
        MapBinder.newMapBinder(binder(), AzureARMTaskType.class, AzureResourceCreationAbstractTaskHandler.class);
    azTaskTypeToHandlerMap.addBinding(AzureARMTaskType.ARM_DEPLOYMENT).to(AzureCreateArmResourceTaskHandler.class);
    azTaskTypeToHandlerMap.addBinding(AzureARMTaskType.BLUEPRINT_DEPLOYMENT).to(AzureCreateBlueprintTaskHandler.class);
    azTaskTypeToHandlerMap.addBinding(AzureARMTaskType.FETCH_ARM_PRE_DEPLOYMENT_DATA)
        .to(FetchArmPreDeploymentDataTaskHandler.class);
    bind(AzureResourceCreationBaseHelper.class).to(AzureARMBaseHelperImpl.class);

    // Tas NG Task Handler

    // HelmNG Task Handlers

    bind(DockerRegistryService.class).to(DockerRegistryServiceImpl.class);
    bind(AzureArtifactsRegistryService.class).to(AzureArtifactsRegistryServiceImpl.class);
    bind(GithubPackagesRegistryService.class).to(GithubPackagesRegistryServiceImpl.class);
    bind(AMIRegistryService.class).to(AMIRegistryServiceImpl.class);
    bind(NexusRegistryService.class).to(NexusRegistryServiceImpl.class);
    bind(ArtifactoryRegistryService.class).to(ArtifactoryRegistryServiceImpl.class);
    bind(HttpService.class).to(HttpServiceImpl.class);
    bind(GithubService.class).to(GithubServiceImpl.class);
    bind(GitlabService.class).to(GitlabServiceImpl.class);
    bind(BitbucketService.class).to(BitbucketServiceImpl.class);
    bind(AzureRepoService.class).to(AzureRepoServiceImpl.class);
    bind(DockerRestClientFactory.class).to(DockerRestClientFactoryImpl.class);
    bind(GithubPackagesRestClientFactory.class).to(GithubPackagesRestClientFactoryImpl.class);

    MapBinder<Class<? extends ArtifactSourceDelegateRequest>, Class<? extends DelegateArtifactTaskHandler>>
        artifactServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends ArtifactSourceDelegateRequest>>() {},
                new TypeLiteral<Class<? extends DelegateArtifactTaskHandler>>() {});
    artifactServiceMapBinder.addBinding(DockerArtifactDelegateRequest.class)
        .toInstance(DockerArtifactTaskHandler.class);

    MapBinder<Class<? extends ArtifactSourceDelegateRequest>, Class<? extends DelegateArtifactTaskHandler>>
        s3ArtifactServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends ArtifactSourceDelegateRequest>>() {},
                new TypeLiteral<Class<? extends DelegateArtifactTaskHandler>>() {});
    s3ArtifactServiceMapBinder.addBinding(S3ArtifactDelegateRequest.class).toInstance(S3ArtifactTaskHandler.class);

    MapBinder<Class<? extends ArtifactSourceDelegateRequest>, Class<? extends DelegateArtifactTaskHandler>>
        googleCloudSourceArtifactServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends ArtifactSourceDelegateRequest>>() {},
                new TypeLiteral<Class<? extends DelegateArtifactTaskHandler>>() {});
    googleCloudSourceArtifactServiceMapBinder.addBinding(GoogleCloudSourceArtifactDelegateRequest.class)
        .toInstance(GoogleCloudSourceArtifactTaskHandler.class);

    MapBinder<Class<? extends ArtifactSourceDelegateRequest>, Class<? extends DelegateArtifactTaskHandler>>
        googleCloudStorageArtifactServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends ArtifactSourceDelegateRequest>>() {},
                new TypeLiteral<Class<? extends DelegateArtifactTaskHandler>>() {});
    googleCloudStorageArtifactServiceMapBinder.addBinding(GoogleCloudStorageArtifactDelegateRequest.class)
        .toInstance(GoogleCloudStorageArtifactTaskHandler.class);

    MapBinder<Class<? extends ArtifactSourceDelegateRequest>, Class<? extends DelegateArtifactTaskHandler>>
        githubPackagesArtifactServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends ArtifactSourceDelegateRequest>>() {},
                new TypeLiteral<Class<? extends DelegateArtifactTaskHandler>>() {});
    githubPackagesArtifactServiceMapBinder.addBinding(GithubPackagesArtifactDelegateRequest.class)
        .toInstance(GithubPackagesArtifactTaskHandler.class);

    MapBinder<Class<? extends ArtifactSourceDelegateRequest>, Class<? extends DelegateArtifactTaskHandler>>
        azureArtifactsServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends ArtifactSourceDelegateRequest>>() {},
                new TypeLiteral<Class<? extends DelegateArtifactTaskHandler>>() {});
    azureArtifactsServiceMapBinder.addBinding(AzureArtifactsDelegateRequest.class)
        .toInstance(AzureArtifactsTaskHandler.class);

    MapBinder<Class<? extends ArtifactSourceDelegateRequest>, Class<? extends DelegateArtifactTaskHandler>>
        aMIArtifactsServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends ArtifactSourceDelegateRequest>>() {},
                new TypeLiteral<Class<? extends DelegateArtifactTaskHandler>>() {});
    aMIArtifactsServiceMapBinder.addBinding(AMIArtifactDelegateRequest.class).toInstance(AMIArtifactTaskHandler.class);

    MapBinder<Class<? extends ArtifactSourceDelegateRequest>, Class<? extends DelegateArtifactTaskHandler>>
        jenkinsArtifactServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends ArtifactSourceDelegateRequest>>() {},
                new TypeLiteral<Class<? extends DelegateArtifactTaskHandler>>() {});
    jenkinsArtifactServiceMapBinder.addBinding(JenkinsArtifactDelegateRequest.class)
        .toInstance(JenkinsArtifactTaskHandler.class);

    MapBinder<Class<? extends ArtifactSourceDelegateRequest>, Class<? extends DelegateArtifactTaskHandler>>
        bambooArtifactServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends ArtifactSourceDelegateRequest>>() {},
                new TypeLiteral<Class<? extends DelegateArtifactTaskHandler>>() {});
    bambooArtifactServiceMapBinder.addBinding(BambooArtifactDelegateRequest.class)
        .toInstance(BambooArtifactTaskHandler.class);

    MapBinder<Class<? extends ArtifactSourceDelegateRequest>, Class<? extends DelegateArtifactTaskHandler>>
        customArtifactServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends ArtifactSourceDelegateRequest>>() {},
                new TypeLiteral<Class<? extends DelegateArtifactTaskHandler>>() {});
    customArtifactServiceMapBinder.addBinding(CustomArtifactDelegateRequest.class)
        .toInstance(CustomArtifactTaskHandler.class);

    MapBinder<Class<? extends ArtifactSourceDelegateRequest>, Class<? extends DelegateArtifactTaskHandler>>
        nexusArtifactServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends ArtifactSourceDelegateRequest>>() {},
                new TypeLiteral<Class<? extends DelegateArtifactTaskHandler>>() {});
    nexusArtifactServiceMapBinder.addBinding(NexusArtifactDelegateRequest.class)
        .toInstance(NexusArtifactTaskHandler.class);

    MapBinder<Class<? extends ArtifactSourceDelegateRequest>, Class<? extends DelegateArtifactTaskHandler>>
        artifactoryDockerArtifactServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends ArtifactSourceDelegateRequest>>() {},
                new TypeLiteral<Class<? extends DelegateArtifactTaskHandler>>() {});
    artifactoryDockerArtifactServiceMapBinder.addBinding(ArtifactoryArtifactDelegateRequest.class)
        .toInstance(ArtifactoryArtifactTaskHandler.class);

    MapBinder<Class<? extends ArtifactSourceDelegateRequest>, Class<? extends DelegateArtifactTaskHandler>>
        artifactoryJenkinsArtifactServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends ArtifactSourceDelegateRequest>>() {},
                new TypeLiteral<Class<? extends DelegateArtifactTaskHandler>>() {});
    artifactoryJenkinsArtifactServiceMapBinder.addBinding(ArtifactoryArtifactDelegateRequest.class)
        .toInstance(ArtifactoryArtifactTaskHandler.class);

    MapBinder<Class<? extends ArtifactSourceDelegateRequest>, Class<? extends DelegateArtifactTaskHandler>>
        artifactoryGenericArtifactServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends ArtifactSourceDelegateRequest>>() {},
                new TypeLiteral<Class<? extends DelegateArtifactTaskHandler>>() {});
    artifactoryGenericArtifactServiceMapBinder.addBinding(ArtifactoryGenericArtifactDelegateRequest.class)
        .toInstance(ArtifactoryArtifactTaskHandler.class);

    MapBinder<Class<? extends ArtifactSourceDelegateRequest>, Class<? extends DelegateArtifactTaskHandler>>
        acrArtifactServiceMapBinder =
            MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends ArtifactSourceDelegateRequest>>() {},
                new TypeLiteral<Class<? extends DelegateArtifactTaskHandler>>() {});
    acrArtifactServiceMapBinder.addBinding(AcrArtifactDelegateRequest.class).toInstance(AcrArtifactTaskHandler.class);

    MapBinder<GcpTaskType, TaskHandler> gcpTaskTypeToTaskHandlerMap =
        MapBinder.newMapBinder(binder(), GcpTaskType.class, TaskHandler.class);
    gcpTaskTypeToTaskHandlerMap.addBinding(GcpTaskType.LIST_CLUSTERS).to(GcpListClustersTaskHandler.class);
    gcpTaskTypeToTaskHandlerMap.addBinding(GcpTaskType.LIST_BUCKETS).to(GcpListBucketsTaskHandler.class);

    // Azure App Service tasks
    MapBinder<String, AbstractAzureAppServiceTaskHandler> azureAppServiceTaskTypeToTaskHandlerMap =
        MapBinder.newMapBinder(binder(), String.class, AbstractAzureAppServiceTaskHandler.class);
    azureAppServiceTaskTypeToTaskHandlerMap.addBinding(AzureAppServiceTaskType.LIST_WEB_APP_NAMES.name())
        .to(AzureWebAppListWebAppNamesTaskHandler.class);
    azureAppServiceTaskTypeToTaskHandlerMap.addBinding(AzureAppServiceTaskType.LIST_WEB_APP_DEPLOYMENT_SLOTS.name())
        .to(AzureWebAppListWebAppDeploymentSlotNamesTaskHandler.class);
    azureAppServiceTaskTypeToTaskHandlerMap.addBinding(AzureAppServiceTaskType.LIST_WEB_APP_INSTANCES_DATA.name())
        .to(AzureWebAppListWebAppInstancesTaskHandler.class);
    azureAppServiceTaskTypeToTaskHandlerMap.addBinding(AzureAppServiceTaskType.SLOT_SETUP.name())
        .to(AzureWebAppSlotSetupTaskHandler.class);
    azureAppServiceTaskTypeToTaskHandlerMap.addBinding(AzureAppServiceTaskType.SLOT_SHIFT_TRAFFIC.name())
        .to(AzureWebAppSlotShiftTrafficTaskHandler.class);
    azureAppServiceTaskTypeToTaskHandlerMap.addBinding(AzureAppServiceTaskType.SLOT_SWAP.name())
        .to(AzureWebAppSlotSwapTaskHandler.class);
    azureAppServiceTaskTypeToTaskHandlerMap.addBinding(AzureAppServiceTaskType.SLOT_ROLLBACK.name())
        .to(AzureWebAppRollbackTaskHandler.class);

    // Azure ARM tasks
    MapBinder<String, AbstractAzureARMTaskHandler> azureARMTaskTypeToTaskHandlerMap =
        MapBinder.newMapBinder(binder(), String.class, AbstractAzureARMTaskHandler.class);
    azureARMTaskTypeToTaskHandlerMap.addBinding(AzureARMTaskType.ARM_DEPLOYMENT.name())
        .to(AzureARMDeploymentTaskHandler.class);
    azureARMTaskTypeToTaskHandlerMap.addBinding(AzureARMTaskType.ARM_ROLLBACK.name())
        .to(AzureARMRollbackTaskHandler.class);
    azureARMTaskTypeToTaskHandlerMap.addBinding(AzureARMTaskType.LIST_SUBSCRIPTION_LOCATIONS.name())
        .to(AzureARMListSubscriptionLocationsTaskHandler.class);
    azureARMTaskTypeToTaskHandlerMap.addBinding(AzureARMTaskType.LIST_MNG_GROUP.name())
        .to(AzureARMListManagementGroupTaskHandler.class);
    azureARMTaskTypeToTaskHandlerMap.addBinding(AzureARMTaskType.BLUEPRINT_DEPLOYMENT.name())
        .to(AzureBlueprintDeploymentTaskHandler.class);

    // Azure Resource tasks
    MapBinder<String, AbstractAzureResourceTaskHandler> azureResourceTaskTypeToTaskHandlerMap =
        MapBinder.newMapBinder(binder(), String.class, AbstractAzureResourceTaskHandler.class);
    azureResourceTaskTypeToTaskHandlerMap.addBinding(AzureResourceProvider.KUBERNETES.name())
        .to(AzureK8sResourceProviderTaskHandler.class);
    azureResourceTaskTypeToTaskHandlerMap.addBinding(AzureResourceProvider.CONTAINER_REGISTRY.name())
        .to(ACRResourceProviderTaskHandler.class);

    // Serverless Tasks
    MapBinder<String, ServerlessCommandTaskHandler> serverlessTaskTypeToTaskHandlerMap =
        MapBinder.newMapBinder(binder(), String.class, ServerlessCommandTaskHandler.class);
    serverlessTaskTypeToTaskHandlerMap.addBinding(ServerlessCommandType.SERVERLESS_AWS_LAMBDA_DEPLOY.name())
        .to(ServerlessAwsLambdaDeployCommandTaskHandler.class);
    serverlessTaskTypeToTaskHandlerMap.addBinding(ServerlessCommandType.SERVERLESS_AWS_LAMBDA_ROLLBACK.name())
        .to(ServerlessAwsLambdaRollbackCommandTaskHandler.class);
    serverlessTaskTypeToTaskHandlerMap.addBinding(ServerlessCommandType.SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK.name())
        .to(ServerlessAwsLambdaPrepareRollbackCommandTaskHandler.class);

    // Azure Web App NG
    MapBinder<String, AzureWebAppRequestHandler<? extends AzureWebAppTaskRequest>>
        azureWebAppRequestTypeToRequestHandlerMap = MapBinder.newMapBinder(binder(), new TypeLiteral<String>() {},
            new TypeLiteral<AzureWebAppRequestHandler<? extends AzureWebAppTaskRequest>>() {});
    azureWebAppRequestTypeToRequestHandlerMap.addBinding(AzureWebAppRequestType.SLOT_DEPLOYMENT.name())
        .to(AzureWebAppSlotDeploymentRequestHandler.class);
    azureWebAppRequestTypeToRequestHandlerMap.addBinding(AzureWebAppRequestType.ROLLBACK.name())
        .to(AzureWebAppRollbackRequestHandler.class);
    azureWebAppRequestTypeToRequestHandlerMap.addBinding(AzureWebAppRequestType.FETCH_PRE_DEPLOYMENT_DATA.name())
        .to(AzureWebAppFetchPreDeploymentDataRequestHandler.class);
    azureWebAppRequestTypeToRequestHandlerMap.addBinding(AzureWebAppRequestType.SLOT_TRAFFIC_SHIFT.name())
        .to(AzureWebAppTrafficShiftRequestHandler.class);
    azureWebAppRequestTypeToRequestHandlerMap.addBinding(AzureWebAppRequestType.SWAP_SLOTS.name())
        .to(AzureWebAppSlotSwapRequestHandler.class);

    // Ssh and WinRm task handlers
    MapBinder<Pair, CommandHandler> commandUnitHandlers =
        MapBinder.newMapBinder(binder(), Pair.class, CommandHandler.class);
    commandUnitHandlers.addBinding(Pair.of(NGCommandUnitType.INIT, ScriptType.BASH.name()))
        .to(SshInitCommandHandler.class);
    commandUnitHandlers.addBinding(Pair.of(NGCommandUnitType.SCRIPT, ScriptType.BASH.name()))
        .to(SshScriptCommandHandler.class);
    commandUnitHandlers.addBinding(Pair.of(NGCommandUnitType.COPY, ScriptType.BASH.name()))
        .to(SshCopyCommandHandler.class);
    commandUnitHandlers.addBinding(Pair.of(NGCommandUnitType.CLEANUP, ScriptType.BASH.name()))
        .to(SshCleanupCommandHandler.class);
    commandUnitHandlers.addBinding(Pair.of(NGCommandUnitType.DOWNLOAD_ARTIFACT, ScriptType.BASH.name()))
        .to(SshDownloadArtifactCommandHandler.class);
    commandUnitHandlers.addBinding(Pair.of(NGCommandUnitType.INIT, ScriptType.POWERSHELL.name()))
        .to(WinRmInitCommandHandler.class);
    commandUnitHandlers.addBinding(Pair.of(NGCommandUnitType.SCRIPT, ScriptType.POWERSHELL.name()))
        .to(WinRmScriptCommandHandler.class);
    commandUnitHandlers.addBinding(Pair.of(NGCommandUnitType.COPY, ScriptType.POWERSHELL.name()))
        .to(WinRmCopyCommandHandler.class);
    commandUnitHandlers.addBinding(Pair.of(NGCommandUnitType.CLEANUP, ScriptType.POWERSHELL.name()))
        .to(WinRmCleanupCommandHandler.class);
    commandUnitHandlers.addBinding(Pair.of(NGCommandUnitType.DOWNLOAD_ARTIFACT, ScriptType.POWERSHELL.name()))
        .to(WinRmDownloadArtifactCommandHandler.class);

    // Artifact handlers
    MapBinder<SshWinRmArtifactType, ArtifactDownloadHandler> artifactHandlers =
        MapBinder.newMapBinder(binder(), SshWinRmArtifactType.class, ArtifactDownloadHandler.class);
    artifactHandlers.addBinding(SshWinRmArtifactType.ARTIFACTORY).to(ArtifactoryArtifactDownloadHandler.class);
    artifactHandlers.addBinding(SshWinRmArtifactType.JENKINS).to(JenkinsArtifactDownloadHandler.class);
    artifactHandlers.addBinding(SshWinRmArtifactType.AWS_S3).to(AwsS3ArtifactDownloadHandler.class);
    artifactHandlers.addBinding(SshWinRmArtifactType.NEXUS_PACKAGE).to(NexusArtifactDownloadHandler.class);
    artifactHandlers.addBinding(SshWinRmArtifactType.AZURE).to(AzureArtifactDownloadHandler.class);

    MapBinder<String, ArtifactCommandUnitHandler> artifactCommandHandlers =
        MapBinder.newMapBinder(binder(), String.class, ArtifactCommandUnitHandler.class);
    artifactCommandHandlers.addBinding(SshWinRmArtifactType.ARTIFACTORY.name()).to(ArtifactoryCommandUnitHandler.class);
    artifactCommandHandlers.addBinding(SshWinRmArtifactType.JENKINS.name()).to(JenkinsArtifactCommandUnitHandler.class);
    artifactCommandHandlers.addBinding(SshWinRmArtifactType.AWS_S3.name()).to(AwsS3ArtifactCommandUnitHandler.class);
    artifactCommandHandlers.addBinding(SshWinRmArtifactType.NEXUS_PACKAGE.name())
        .to(NexusArtifactCommandUnitHandler.class);
    artifactCommandHandlers.addBinding(SshWinRmArtifactType.AZURE.name()).to(AzureArtifactCommandUnitHandler.class);

    registerSecretManagementBindings();
    registerConnectorValidatorsBindings();

    bindExceptionHandlers();
  }

  private void bindDelegateTasks() {
    MapBinder<TaskType, Class<? extends DelegateRunnableTask>> mapBinder = MapBinder.newMapBinder(
        binder(), new TypeLiteral<TaskType>() {}, new TypeLiteral<Class<? extends DelegateRunnableTask>>() {});

    mapBinder.addBinding(TaskType.BATCH_CAPABILITY_CHECK).toInstance(BatchCapabilityCheckTask.class);
    mapBinder.addBinding(TaskType.CAPABILITY_VALIDATION).toInstance(PerpetualTaskCapabilityCheckTask.class);
    mapBinder.addBinding(TaskType.COMMAND).toInstance(CommandTask.class);
    mapBinder.addBinding(TaskType.SCRIPT).toInstance(ShellScriptTask.class);
    mapBinder.addBinding(TaskType.HTTP).toInstance(HttpTask.class);
    mapBinder.addBinding(TaskType.GCB).toInstance(GcbTask.class);
    mapBinder.addBinding(TaskType.JENKINS).toInstance(JenkinsTask.class);
    mapBinder.addBinding(TaskType.JENKINS_COLLECTION).toInstance(JenkinsCollectionTask.class);
    mapBinder.addBinding(TaskType.JENKINS_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.JENKINS_GET_JOBS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.JENKINS_GET_JOB).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.JENKINS_GET_ARTIFACT_PATHS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.JENKINS_LAST_SUCCESSFUL_BUILD).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.JENKINS_GET_PLANS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.JENKINS_VALIDATE_ARTIFACT_SERVER).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.BAMBOO).toInstance(BambooTask.class);
    mapBinder.addBinding(TaskType.BAMBOO_COLLECTION).toInstance(BambooCollectionTask.class);
    mapBinder.addBinding(TaskType.BAMBOO_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.BAMBOO_GET_JOBS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.BAMBOO_GET_ARTIFACT_PATHS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.BAMBOO_LAST_SUCCESSFUL_BUILD).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.BAMBOO_GET_PLANS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.BAMBOO_VALIDATE_ARTIFACT_SERVER).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.DOCKER_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.DOCKER_GET_LABELS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.DOCKER_VALIDATE_ARTIFACT_SERVER).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.DOCKER_VALIDATE_ARTIFACT_STREAM).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.DOCKER_GET_ARTIFACT_META_INFO).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.JENKINS_VALIDATE_ARTIFACT_SERVER).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ECR_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ECR_VALIDATE_ARTIFACT_SERVER).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ECR_GET_PLANS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ECR_GET_ARTIFACT_PATHS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ECR_VALIDATE_ARTIFACT_STREAM).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ECR_GET_LABELS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.GCR_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.GCR_VALIDATE_ARTIFACT_STREAM).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.GCR_GET_PLANS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ACR_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ACR_VALIDATE_ARTIFACT_STREAM).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ACR_GET_PLANS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ACR_GET_ARTIFACT_PATHS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ACR_GET_REGISTRIES).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ACR_GET_REGISTRY_NAMES).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ACR_GET_REPOSITORIES).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NEXUS_GET_JOBS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NEXUS_GET_PLANS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NEXUS_GET_ARTIFACT_PATHS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NEXUS_GET_GROUP_IDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NEXUS_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NEXUS_LAST_SUCCESSFUL_BUILD).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NEXUS_COLLECTION).toInstance(NexusCollectionTask.class);
    mapBinder.addBinding(TaskType.NEXUS_VALIDATE_ARTIFACT_SERVER).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NEXUS_VALIDATE_ARTIFACT_STREAM).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.GCS_GET_ARTIFACT_PATHS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.GCS_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.GCS_GET_BUCKETS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.GCS_GET_PROJECT_ID).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.GCS_GET_PLANS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SFTP_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SFTP_GET_ARTIFACT_PATHS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SFTP_VALIDATE_ARTIFACT_SERVER).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SMB_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SMB_GET_SMB_PATHS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SMB_VALIDATE_ARTIFACT_SERVER).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AMAZON_S3_COLLECTION).toInstance(AmazonS3CollectionTask.class);
    mapBinder.addBinding(TaskType.AMAZON_S3_GET_ARTIFACT_PATHS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AMAZON_S3_LAST_SUCCESSFUL_BUILD).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AMAZON_S3_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AMAZON_S3_GET_PLANS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AZURE_ARTIFACTS_VALIDATE_ARTIFACT_SERVER).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AZURE_ARTIFACTS_VALIDATE_ARTIFACT_STREAM).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AZURE_ARTIFACTS_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AZURE_ARTIFACTS_GET_PROJECTS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AZURE_ARTIFACTS_GET_FEEDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AZURE_ARTIFACTS_GET_PACKAGES).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AZURE_ARTIFACTS_COLLECTION).toInstance(AzureArtifactsCollectionTask.class);
    mapBinder.addBinding(TaskType.AZURE_MACHINE_IMAGE_VALIDATE_ARTIFACT_SERVER)
        .toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AZURE_MACHINE_IMAGE_GET_IMAGE_GALLERIES).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AZURE_MACHINE_IMAGE_GET_IMAGE_DEFINITIONS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AZURE_MACHINE_IMAGE_GET_RESOURCE_GROUPS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AZURE_GET_SUBSCRIPTIONS).toInstance(ServiceImplDelegateTask.class);

    mapBinder.addBinding(TaskType.AZURE_MACHINE_IMAGE_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AZURE_VMSS_COMMAND_TASK).toInstance(AzureVMSSTask.class);
    mapBinder.addBinding(TaskType.AZURE_APP_SERVICE_TASK).toInstance(AzureAppServiceTask.class);
    mapBinder.addBinding(TaskType.AZURE_ARM_TASK).toInstance(AzureARMTask.class);
    mapBinder.addBinding(TaskType.AZURE_RESOURCE_TASK).toInstance(AzureResourceTask.class);
    mapBinder.addBinding(TaskType.LDAP_TEST_CONN_SETTINGS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.LDAP_TEST_USER_SETTINGS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.LDAP_TEST_GROUP_SETTINGS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.LDAP_VALIDATE_SETTINGS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.LDAP_AUTHENTICATION).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.LDAP_SEARCH_GROUPS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.LDAP_FETCH_GROUP).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NG_LDAP_TEST_CONN_SETTINGS).toInstance(NGLdapValidateConnectionSettingTask.class);
    mapBinder.addBinding(TaskType.NG_LDAP_TEST_USER_SETTINGS).toInstance(NGLdapValidateUserQuerySettingTask.class);
    mapBinder.addBinding(TaskType.NG_LDAP_TEST_GROUP_SETTINGS).toInstance(NGLdapValidateGroupQuerySettingTask.class);
    mapBinder.addBinding(TaskType.NG_LDAP_SEARCH_GROUPS).toInstance(NGLdapGroupSearchTask.class);
    mapBinder.addBinding(TaskType.NG_LDAP_GROUPS_SYNC).toInstance(NGLdapGroupSyncTask.class);
    mapBinder.addBinding(TaskType.NG_LDAP_TEST_AUTHENTICATION).toInstance(NGLdapTestAuthenticationTask.class);
    mapBinder.addBinding(TaskType.APM_VALIDATE_CONNECTOR_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.CUSTOM_LOG_VALIDATE_CONNECTOR_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.APM_GET_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.APPDYNAMICS_CONFIGURATION_VALIDATE_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.CVNG_CONNECTOR_VALIDATE_TASK).toInstance(CVNGConnectorValidationDelegateTask.class);
    mapBinder.addBinding(TaskType.GET_DATA_COLLECTION_RESULT).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.APPDYNAMICS_GET_APP_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.APPDYNAMICS_GET_APP_TASK_NG).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.APPDYNAMICS_GET_TIER_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.APPDYNAMICS_GET_TIER_TASK_NG).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.APPDYNAMICS_GET_TIER_MAP).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.APPDYNAMICS_COLLECT_METRIC_DATA).toInstance(AppdynamicsDataCollectionTask.class);
    mapBinder.addBinding(TaskType.APPDYNAMICS_COLLECT_METRIC_DATA_V2).toInstance(MetricsDataCollectionTask.class);
    mapBinder.addBinding(TaskType.APPDYNAMICS_COLLECT_24_7_METRIC_DATA).toInstance(AppdynamicsDataCollectionTask.class);
    mapBinder.addBinding(TaskType.APPDYNAMICS_METRIC_DATA_FOR_NODE).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.INSTANA_GET_INFRA_METRICS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.INSTANA_GET_TRACE_METRICS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.INSTANA_COLLECT_METRIC_DATA).toInstance(MetricsDataCollectionTask.class);
    mapBinder.addBinding(TaskType.INSTANA_VALIDATE_CONFIGURATION_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NEWRELIC_VALIDATE_CONFIGURATION_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.BUGSNAG_GET_APP_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.BUGSNAG_GET_RECORDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.CUSTOM_COLLECT_24_7_LOG_DATA).toInstance(CustomLogDataCollectionTask.class);
    mapBinder.addBinding(TaskType.CUSTOM_APM_COLLECT_METRICS_V2).toInstance(MetricsDataCollectionTask.class);
    mapBinder.addBinding(TaskType.NEWRELIC_GET_APP_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NEWRELIC_RESOLVE_APP_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NEWRELIC_RESOLVE_APP_ID_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NEWRELIC_GET_APP_INSTANCES_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NEWRELIC_COLLECT_METRIC_DATA).toInstance(NewRelicDataCollectionTask.class);
    mapBinder.addBinding(TaskType.NEWRELIC_COLLECT_METRIC_DATAV2).toInstance(MetricsDataCollectionTask.class);
    mapBinder.addBinding(TaskType.NEWRELIC_COLLECT_24_7_METRIC_DATA).toInstance(NewRelicDataCollectionTask.class);
    mapBinder.addBinding(TaskType.NEWRELIC_GET_TXNS_WITH_DATA).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NEWRELIC_GET_TXNS_WITH_DATA_FOR_NODE).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.NEWRELIC_POST_DEPLOYMENT_MARKER).toInstance(NewRelicDeploymentMarkerTask.class);
    mapBinder.addBinding(TaskType.STACKDRIVER_COLLECT_METRIC_DATA).toInstance(StackDriverDataCollectionTask.class);
    mapBinder.addBinding(TaskType.STACKDRIVER_METRIC_DATA_FOR_NODE).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.STACKDRIVER_LOG_DATA_FOR_NODE).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.STACKDRIVER_LIST_REGIONS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.STACKDRIVER_LIST_FORWARDING_RULES).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.STACKDRIVER_GET_LOG_SAMPLE).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.STACKDRIVER_COLLECT_24_7_METRIC_DATA).toInstance(StackDriverDataCollectionTask.class);
    mapBinder.addBinding(TaskType.STACKDRIVER_COLLECT_LOG_DATA).toInstance(StackDriverLogDataCollectionTask.class);
    mapBinder.addBinding(TaskType.STACKDRIVER_COLLECT_24_7_LOG_DATA).toInstance(StackDriverLogDataCollectionTask.class);
    mapBinder.addBinding(TaskType.SPLUNK).toInstance(HttpTask.class);
    mapBinder.addBinding(TaskType.SPLUNK_CONFIGURATION_VALIDATE_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SPLUNK_GET_HOST_RECORDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SPLUNK_NG_GET_SAVED_SEARCHES).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SPLUNK_NG_VALIDATION_RESPONSE_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SPLUNK_COLLECT_LOG_DATAV2).toInstance(LogDataCollectionTask.class);
    mapBinder.addBinding(TaskType.ELK_COLLECT_LOG_DATAV2).toInstance(LogDataCollectionTask.class);
    mapBinder.addBinding(TaskType.DATA_COLLECTION_NEXT_GEN_VALIDATION).toInstance(MetricsDataCollectionTask.class);
    mapBinder.addBinding(TaskType.SUMO_COLLECT_LOG_DATA).toInstance(SumoDataCollectionTask.class);
    mapBinder.addBinding(TaskType.SUMO_VALIDATE_CONFIGURATION_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SUMO_GET_HOST_RECORDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SUMO_GET_LOG_DATA_BY_HOST).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SUMO_COLLECT_24_7_LOG_DATA).toInstance(SumoDataCollectionTask.class);
    mapBinder.addBinding(TaskType.ELK_CONFIGURATION_VALIDATE_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ELK_COLLECT_LOG_DATA).toInstance(ElkLogzDataCollectionTask.class);
    mapBinder.addBinding(TaskType.ELK_COLLECT_INDICES).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ELK_GET_LOG_SAMPLE).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ELK_GET_HOST_RECORDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.KIBANA_GET_VERSION).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ELK_COLLECT_24_7_LOG_DATA).toInstance(ElkLogzDataCollectionTask.class);
    mapBinder.addBinding(TaskType.LOGZ_CONFIGURATION_VALIDATE_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.LOGZ_COLLECT_LOG_DATA).toInstance(ElkLogzDataCollectionTask.class);
    mapBinder.addBinding(TaskType.LOGZ_GET_LOG_SAMPLE).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.LOGZ_GET_HOST_RECORDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ARTIFACTORY_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ARTIFACTORY_GET_LABELS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ARTIFACTORY_GET_JOBS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ARTIFACTORY_GET_PLANS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ARTIFACTORY_GET_ARTIFACTORY_PATHS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ARTIFACTORY_GET_GROUP_IDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ARTIFACTORY_LAST_SUCCSSFUL_BUILD).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ARTIFACTORY_COLLECTION).toInstance(ArtifactoryCollectionTask.class);
    mapBinder.addBinding(TaskType.ARTIFACTORY_VALIDATE_ARTIFACT_SERVER).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.ARTIFACTORY_VALIDATE_ARTIFACT_STREAM).toInstance(ServiceImplDelegateTask.class);

    // Secret Management (Old Tasks)
    mapBinder.addBinding(TaskType.VAULT_GET_CHANGELOG).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.VAULT_RENEW_TOKEN).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.VAULT_TOKEN_LOOKUP).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.VAULT_LIST_ENGINES).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.VAULT_APPROLE_LOGIN).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SSH_SECRET_ENGINE_AUTH).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SECRET_DECRYPT).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.BATCH_SECRET_DECRYPT).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SECRET_DECRYPT_REF).toInstance(ServiceImplDelegateTask.class);

    // Secret Management (New Tasks)
    mapBinder.addBinding(TaskType.DELETE_SECRET).toInstance(DeleteSecretTask.class);
    mapBinder.addBinding(TaskType.VALIDATE_SECRET_REFERENCE).toInstance(ValidateSecretReferenceTask.class);
    mapBinder.addBinding(TaskType.UPSERT_SECRET).toInstance(UpsertSecretTask.class);
    mapBinder.addBinding(TaskType.FETCH_SECRET).toInstance(FetchSecretTask.class);
    mapBinder.addBinding(TaskType.ENCRYPT_SECRET).toInstance(EncryptSecretTask.class);
    mapBinder.addBinding(TaskType.NG_VAULT_RENEW_TOKEN).toInstance(NGVaultRenewalTask.class);
    mapBinder.addBinding(TaskType.NG_VAULT_RENEW_APP_ROLE_TOKEN).toInstance(NGVaultRenewalAppRoleTask.class);
    mapBinder.addBinding(TaskType.NG_VAULT_TOKEN_LOOKUP).toInstance(NGVaultTokenLookupTask.class);
    mapBinder.addBinding(TaskType.NG_VAULT_FETCHING_TASK).toInstance(NGVaultFetchEngineTask.class);
    mapBinder.addBinding(TaskType.NG_AZURE_VAULT_FETCH_ENGINES).toInstance(NGAzureKeyVaultFetchEngineTask.class);
    mapBinder.addBinding(TaskType.VALIDATE_SECRET_MANAGER_CONFIGURATION)
        .toInstance(ValidateSecretManagerConfigurationTask.class);
    mapBinder.addBinding(TaskType.VALIDATE_CUSTOM_SECRET_MANAGER_SECRET_REFERENCE)
        .toInstance(ValidateCustomSecretManagerSecretReferenceTask.class);
    mapBinder.addBinding(TaskType.FETCH_CUSTOM_SECRET).toInstance(FetchCustomSecretTask.class);
    mapBinder.addBinding(TaskType.RESOLVE_CUSTOM_SM_CONFIG).toInstance(ResolveCustomSecretManagerConfigTask.class);

    mapBinder.addBinding(TaskType.HOST_VALIDATION).toInstance(HostValidationTask.class);
    mapBinder.addBinding(TaskType.CONTAINER_ACTIVE_SERVICE_COUNTS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.CONTAINER_INFO).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.CONTROLLER_NAMES_WITH_LABELS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.AMI_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.CONTAINER_CE_VALIDATION).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.CE_DELEGATE_VALIDATION).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.CONTAINER_CONNECTION_VALIDATION).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.LIST_CLUSTERS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.CONTAINER_VALIDATION).toInstance(ContainerDummyTask.class);

    mapBinder.addBinding(TaskType.FETCH_MASTER_URL).toInstance(ServiceImplDelegateTask.class);

    mapBinder.addBinding(TaskType.DYNA_TRACE_VALIDATE_CONFIGURATION_TASK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.DYNA_TRACE_METRIC_DATA_COLLECTION_TASK).toInstance(DynaTraceDataCollectionTask.class);
    mapBinder.addBinding(TaskType.DYNA_TRACE_GET_TXNS_WITH_DATA_FOR_NODE).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.DYNA_TRACE_GET_SERVICES).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.DYNATRACE_COLLECT_24_7_METRIC_DATA).toInstance(DynaTraceDataCollectionTask.class);
    mapBinder.addBinding(TaskType.HELM_COMMAND_TASK).toInstance(HelmCommandTask.class);
    mapBinder.addBinding(TaskType.KUBERNETES_STEADY_STATE_CHECK_TASK).toInstance(KubernetesSteadyStateCheckTask.class);
    mapBinder.addBinding(TaskType.PCF_COMMAND_TASK).toInstance(PcfCommandTask.class);
    mapBinder.addBinding(TaskType.SPOTINST_COMMAND_TASK).toInstance(SpotInstTask.class);
    mapBinder.addBinding(TaskType.SPOT_TASK_NG).toInstance(SpotDelegateTask.class);
    mapBinder.addBinding(TaskType.ECS_COMMAND_TASK).toInstance(EcsCommandTask.class);
    mapBinder.addBinding(TaskType.COLLABORATION_PROVIDER_TASK).toInstance(CollaborationProviderTask.class);
    mapBinder.addBinding(TaskType.PROMETHEUS_METRIC_DATA_PER_HOST).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.CLOUD_WATCH_COLLECT_METRIC_DATA).toInstance(CloudWatchDataCollectionTask.class);
    mapBinder.addBinding(TaskType.CLOUD_WATCH_METRIC_DATA_FOR_NODE).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.CLOUD_WATCH_GENERIC_METRIC_STATISTICS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.CLOUD_WATCH_GENERIC_METRIC_DATA).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.CLOUD_WATCH_COLLECT_24_7_METRIC_DATA).toInstance(CloudWatchDataCollectionTask.class);
    mapBinder.addBinding(TaskType.APM_METRIC_DATA_COLLECTION_TASK).toInstance(APMDataCollectionTask.class);

    mapBinder.addBinding(TaskType.APM_24_7_METRIC_DATA_COLLECTION_TASK).toInstance(APMDataCollectionTask.class);

    mapBinder.addBinding(TaskType.CUSTOM_LOG_COLLECTION_TASK).toInstance(CustomLogDataCollectionTask.class);
    mapBinder.addBinding(TaskType.CLOUD_FORMATION_TASK).toInstance(CloudFormationCommandTask.class);
    mapBinder.addBinding(TaskType.FETCH_S3_FILE_TASK).toInstance(S3FetchFilesTask.class);

    mapBinder.addBinding(TaskType.TERRAFORM_PROVISION_TASK).toInstance(TerraformProvisionTask.class);
    mapBinder.addBinding(TaskType.TERRAFORM_PROVISION_TASK_V2).toInstance(TerraformProvisionTask.class);
    mapBinder.addBinding(TaskType.TERRAFORM_INPUT_VARIABLES_OBTAIN_TASK)
        .toInstance(TerraformInputVariablesObtainTask.class);
    mapBinder.addBinding(TaskType.TERRAFORM_INPUT_VARIABLES_OBTAIN_TASK_V2)
        .toInstance(TerraformInputVariablesObtainTask.class);
    mapBinder.addBinding(TaskType.TERRAFORM_FETCH_TARGETS_TASK).toInstance(TerraformFetchTargetsTask.class);
    mapBinder.addBinding(TaskType.TERRAFORM_FETCH_TARGETS_TASK_V2).toInstance(TerraformFetchTargetsTask.class);
    mapBinder.addBinding(TaskType.TERRAGRUNT_PROVISION_TASK).toInstance(TerragruntProvisionTask.class);
    mapBinder.addBinding(TaskType.KUBERNETES_SWAP_SERVICE_SELECTORS_TASK)
        .toInstance(KubernetesSwapServiceSelectorsTask.class);
    mapBinder.addBinding(TaskType.ECS_STEADY_STATE_CHECK_TASK).toInstance(EcsSteadyStateCheckTask.class);
    mapBinder.addBinding(TaskType.AWS_ECR_TASK).toInstance(AwsEcrTask.class);
    mapBinder.addBinding(TaskType.AWS_ELB_TASK).toInstance(AwsElbTask.class);
    mapBinder.addBinding(TaskType.AWS_ECS_TASK).toInstance(AwsEcsTask.class);
    mapBinder.addBinding(TaskType.AWS_IAM_TASK).toInstance(AwsIamTask.class);
    mapBinder.addBinding(TaskType.AWS_EC2_TASK).toInstance(AwsEc2Task.class);
    mapBinder.addBinding(TaskType.AWS_ASG_TASK).toInstance(AwsAsgTask.class);
    mapBinder.addBinding(TaskType.AWS_CODE_DEPLOY_TASK).toInstance(AwsCodeDeployTask.class);
    mapBinder.addBinding(TaskType.AWS_LAMBDA_TASK).toInstance(AwsLambdaTask.class);
    mapBinder.addBinding(TaskType.AWS_AMI_ASYNC_TASK).toInstance(AwsAmiAsyncTask.class);
    mapBinder.addBinding(TaskType.AWS_CF_TASK).toInstance(AwsCFTask.class);
    mapBinder.addBinding(TaskType.K8S_COMMAND_TASK).toInstance(K8sTask.class);
    mapBinder.addBinding(TaskType.K8S_COMMAND_TASK_NG).toInstance(K8sTaskNG.class);
    mapBinder.addBinding(TaskType.K8S_COMMAND_TASK_NG_V2).toInstance(K8sTaskNG.class);
    mapBinder.addBinding(TaskType.K8S_DRY_RUN_MANIFEST_TASK_NG).toInstance(K8sTaskNG.class);
    mapBinder.addBinding(TaskType.K8S_WATCH_TASK).toInstance(AssignmentTask.class);
    mapBinder.addBinding(TaskType.TRIGGER_TASK).toInstance(TriggerTask.class);
    mapBinder.addBinding(TaskType.WEBHOOK_TRIGGER_TASK).toInstance(WebHookTriggerTask.class);
    mapBinder.addBinding(TaskType.JIRA).toInstance(JiraTask.class);
    mapBinder.addBinding(TaskType.CONNECTIVITY_VALIDATION).toInstance(ConnectivityValidationTask.class);
    mapBinder.addBinding(TaskType.GIT_COMMAND).toInstance(GitCommandTask.class);
    mapBinder.addBinding(TaskType.GIT_FETCH_FILES_TASK).toInstance(GitFetchFilesTask.class);
    mapBinder.addBinding(TaskType.GIT_FETCH_NEXT_GEN_TASK).toInstance(GitFetchTaskNG.class);
    mapBinder.addBinding(TaskType.BUILD_SOURCE_TASK).toInstance(BuildSourceTask.class);
    mapBinder.addBinding(TaskType.DOCKER_ARTIFACT_TASK_NG).toInstance(DockerArtifactTaskNG.class);
    mapBinder.addBinding(TaskType.AMAZON_S3_ARTIFACT_TASK_NG).toInstance(S3ArtifactTaskNG.class);
    mapBinder.addBinding(TaskType.GOOGLE_CLOUD_STORAGE_ARTIFACT_TASK_NG)
        .toInstance(GoogleCloudStorageArtifactTaskNG.class);
    mapBinder.addBinding(TaskType.GOOGLE_CLOUD_SOURCE_ARTIFACT_TASK_NG)
        .toInstance(GoogleCloudSourceArtifactTaskNG.class);
    mapBinder.addBinding(TaskType.JENKINS_ARTIFACT_TASK_NG).toInstance(JenkinsArtifactTaskNG.class);
    mapBinder.addBinding(TaskType.AZURE_ARTIFACT_TASK_NG).toInstance(AzureArtifactsTaskNG.class);
    mapBinder.addBinding(TaskType.AMI_ARTIFACT_TASK_NG).toInstance(AMIArtifactTaskNG.class);
    mapBinder.addBinding(TaskType.GITHUB_PACKAGES_TASK_NG).toInstance(GithubPackagesArtifactTaskNG.class);
    mapBinder.addBinding(TaskType.GOOGLE_ARTIFACT_REGISTRY_TASK_NG).toInstance(GARArtifactTaskNG.class);
    mapBinder.addBinding(TaskType.GCR_ARTIFACT_TASK_NG).toInstance(GcrArtifactTaskNG.class);
    mapBinder.addBinding(TaskType.ECR_ARTIFACT_TASK_NG).toInstance(EcrArtifactTaskNG.class);
    mapBinder.addBinding(TaskType.ACR_ARTIFACT_TASK_NG).toInstance(AcrArtifactTaskNG.class);
    mapBinder.addBinding(TaskType.NEXUS_ARTIFACT_TASK_NG).toInstance(NexusArtifactTaskNG.class);
    mapBinder.addBinding(TaskType.ARTIFACTORY_ARTIFACT_TASK_NG).toInstance(ArtifactoryArtifactTaskNG.class);
    mapBinder.addBinding(TaskType.AWS_ROUTE53_TASK).toInstance(AwsRoute53Task.class);
    mapBinder.addBinding(TaskType.SHELL_SCRIPT_APPROVAL).toInstance(ShellScriptApprovalTask.class);
    mapBinder.addBinding(TaskType.CUSTOM_GET_BUILDS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.CUSTOM_VALIDATE_ARTIFACT_STREAM).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.CUSTOM_ARTIFACT_NG).toInstance(CustomArtifactTaskNG.class);
    mapBinder.addBinding(TaskType.SHELL_SCRIPT_PROVISION_TASK).toInstance(ShellScriptProvisionTask.class);
    mapBinder.addBinding(TaskType.SERVICENOW_ASYNC).toInstance(ServicenowTask.class);
    mapBinder.addBinding(TaskType.SERVICENOW_SYNC).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.SERVICENOW_VALIDATION).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.HELM_REPO_CONFIG_VALIDATION).toInstance(HelmRepoConfigValidationTask.class);
    mapBinder.addBinding(TaskType.HELM_VALUES_FETCH).toInstance(HelmValuesFetchTask.class);
    mapBinder.addBinding(TaskType.HELM_VALUES_FETCH_NG).toInstance(HelmValuesFetchTaskNG.class);
    mapBinder.addBinding(TaskType.HELM_COMMAND_TASK_NG).toInstance(HelmCommandTaskNG.class);
    mapBinder.addBinding(TaskType.HELM_COMMAND_TASK_NG_V2).toInstance(HelmCommandTaskNG.class);
    mapBinder.addBinding(TaskType.HELM_COLLECT_CHART).toInstance(HelmCollectChartTask.class);
    mapBinder.addBinding(TaskType.SLACK).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.INITIALIZATION_PHASE).toInstance(CIInitializeTask.class);
    mapBinder.addBinding(TaskType.CI_EXECUTE_STEP).toInstance(CIExecuteStepTask.class);
    mapBinder.addBinding(TaskType.CI_LE_STATUS).toInstance(StepStatusTask.class);
    mapBinder.addBinding(TaskType.EXECUTE_COMMAND).toInstance(ExecuteCommandTask.class);
    mapBinder.addBinding(TaskType.CI_CLEANUP).toInstance(CICleanupTask.class);
    mapBinder.addBinding(TaskType.AWS_S3_TASK).toInstance(AwsS3Task.class);
    mapBinder.addBinding(TaskType.CUSTOM_MANIFEST_VALUES_FETCH_TASK).toInstance(CustomManifestValuesFetchTask.class);
    mapBinder.addBinding(TaskType.CUSTOM_MANIFEST_FETCH_TASK).toInstance(CustomManifestFetchTask.class);
    mapBinder.addBinding(TaskType.RANCHER_RESOLVE_CLUSTERS).toInstance(RancherResolveClustersTask.class);

    // Add all NG tasks below this.
    mapBinder.addBinding(TaskType.CUSTOM_MANIFEST_VALUES_FETCH_TASK_NG).toInstance(CustomManifestFetchTaskNG.class);
    mapBinder.addBinding(TaskType.GITOPS_TASK_NG).toInstance(NGGitOpsCommandTask.class);
    mapBinder.addBinding(TaskType.GCP_TASK).toInstance(GcpTask.class);
    mapBinder.addBinding(TaskType.VALIDATE_KUBERNETES_CONFIG).toInstance(KubernetesTestConnectionDelegateTask.class);
    mapBinder.addBinding(TaskType.NG_GIT_COMMAND).toInstance(NGGitCommandTask.class);
    mapBinder.addBinding(TaskType.NG_SSH_VALIDATION).toInstance(SSHConfigValidationDelegateTask.class);
    mapBinder.addBinding(TaskType.NG_WINRM_VALIDATION).toInstance(WinRmConfigValidationDelegateTask.class);
    mapBinder.addBinding(TaskType.NG_HOST_CONNECTIVITY_TASK).toInstance(HostConnectivityValidationDelegateTask.class);
    mapBinder.addBinding(TaskType.DOCKER_CONNECTIVITY_TEST_TASK).toInstance(DockerTestConnectionDelegateTask.class);
    mapBinder.addBinding(TaskType.JENKINS_CONNECTIVITY_TEST_TASK).toInstance(JenkinsTestConnectionDelegateTask.class);
    mapBinder.addBinding(TaskType.BAMBOO_CONNECTIVITY_TEST_TASK).toInstance(BambooTestConnectionDelegateTask.class);
    mapBinder.addBinding(TaskType.AZURE_ARTIFACTS_CONNECTIVITY_TEST_TASK)
        .toInstance(AzureArtifactsTestConnectionDelegateTask.class);
    mapBinder.addBinding(TaskType.NG_AWS_TASK).toInstance(AwsDelegateTask.class);
    mapBinder.addBinding(TaskType.JIRA_CONNECTIVITY_TASK_NG).toInstance(JiraTestConnectionTaskNG.class);
    mapBinder.addBinding(TaskType.JIRA_TASK_NG).toInstance(JiraTaskNG.class);
    mapBinder.addBinding(TaskType.BUILD_STATUS).toInstance(CIBuildStatusPushTask.class);
    mapBinder.addBinding(TaskType.GIT_API_TASK).toInstance(GitApiTask.class);
    mapBinder.addBinding(TaskType.AWS_CODECOMMIT_API_TASK).toInstance(AwsCodeCommitApiDelegateTask.class);
    mapBinder.addBinding(TaskType.CE_VALIDATE_KUBERNETES_CONFIG)
        .toInstance(CEKubernetesTestConnectionDelegateTask.class);
    mapBinder.addBinding(TaskType.K8S_SERVICE_ACCOUNT_INFO).toInstance(K8sFetchServiceAccountTask.class);
    mapBinder.addBinding(TaskType.HTTP_HELM_CONNECTIVITY_TASK).toInstance(HttpHelmConnectivityDelegateTask.class);
    mapBinder.addBinding(TaskType.OCI_HELM_CONNECTIVITY_TASK).toInstance(OciHelmConnectivityDelegateTask.class);
    mapBinder.addBinding(TaskType.NG_AZURE_TASK).toInstance(AzureTask.class);
    mapBinder.addBinding(TaskType.VALIDATE_TAS_CONNECTOR_TASK_NG).toInstance(TasConnectorValidationTask.class);

    mapBinder.addBinding(TaskType.K8_FETCH_NAMESPACES).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.K8_FETCH_WORKLOADS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.K8_FETCH_EVENTS).toInstance(ServiceImplDelegateTask.class);
    mapBinder.addBinding(TaskType.HTTP_TASK_NG).toInstance(HttpTaskNG.class);
    mapBinder.addBinding(TaskType.NOTIFY_MAIL).toInstance(MailSenderDelegateTask.class);
    mapBinder.addBinding(TaskType.NOTIFY_SLACK).toInstance(SlackSenderDelegateTask.class);
    mapBinder.addBinding(TaskType.NOTIFY_PAGERDUTY).toInstance(PagerDutySenderDelegateTask.class);
    mapBinder.addBinding(TaskType.NOTIFY_MICROSOFTTEAMS).toInstance(MicrosoftTeamsSenderDelegateTask.class);
    mapBinder.addBinding(TaskType.SHELL_SCRIPT_TASK_NG).toInstance(ShellScriptTaskNG.class);
    mapBinder.addBinding(TaskType.WIN_RM_SHELL_SCRIPT_TASK_NG).toInstance(WinRmShellScriptTaskNG.class);
    mapBinder.addBinding(TaskType.NG_NEXUS_TASK).toInstance(NexusDelegateTask.class);
    mapBinder.addBinding(TaskType.NG_ARTIFACTORY_TASK).toInstance(ArtifactoryDelegateTask.class);
    mapBinder.addBinding(TaskType.NG_AWS_CODE_COMMIT_TASK).toInstance(AwsCodeCommitDelegateTask.class);
    mapBinder.addBinding(TaskType.NG_DECRYT_GIT_API_ACCESS_TASK).toInstance(DecryptGitAPIAccessTask.class);
    mapBinder.addBinding(TaskType.TERRAFORM_TASK_NG).toInstance(TerraformTaskNG.class);
    mapBinder.addBinding(TaskType.TERRAFORM_TASK_NG_V2).toInstance(TerraformTaskNG.class);
    mapBinder.addBinding(TaskType.TERRAFORM_TASK_NG_V3).toInstance(TerraformTaskNG.class);
    mapBinder.addBinding(TaskType.TERRAFORM_TASK_NG_V4).toInstance(TerraformTaskNG.class);
    mapBinder.addBinding(TaskType.TERRAFORM_TASK_NG_V5).toInstance(TerraformTaskNG.class);
    mapBinder.addBinding(TaskType.TERRAFORM_TASK_NG_V6).toInstance(TerraformTaskNG.class);
    mapBinder.addBinding(TaskType.SCM_PUSH_TASK).toInstance(ScmPushTask.class);
    mapBinder.addBinding(TaskType.SCM_PATH_FILTER_EVALUATION_TASK).toInstance(ScmPathFilterEvaluationTask.class);
    mapBinder.addBinding(TaskType.SCM_GIT_REF_TASK).toInstance(ScmGitRefTask.class);
    mapBinder.addBinding(TaskType.SCM_GIT_FILE_TASK).toInstance(ScmGitFileTask.class);
    mapBinder.addBinding(TaskType.SCM_BATCH_GET_FILE_TASK).toInstance(ScmBatchGetFileTask.class);
    mapBinder.addBinding(TaskType.SCM_PULL_REQUEST_TASK).toInstance(ScmGitPRTask.class);
    mapBinder.addBinding(TaskType.SCM_GIT_WEBHOOK_TASK).toInstance(ScmGitWebhookTask.class);
    mapBinder.addBinding(TaskType.SERVICENOW_CONNECTIVITY_TASK_NG).toInstance(ServiceNowTestConnectionTaskNG.class);
    mapBinder.addBinding(TaskType.SERVICENOW_TASK_NG).toInstance(ServiceNowTaskNG.class);
    mapBinder.addBinding(TaskType.CLOUDFORMATION_TASK_NG).toInstance(CloudformationTaskNG.class);
    mapBinder.addBinding(TaskType.FETCH_S3_FILE_TASK_NG).toInstance(S3FetchFilesTaskNG.class);
    mapBinder.addBinding(TaskType.SERVERLESS_GIT_FETCH_TASK_NG).toInstance(ServerlessGitFetchTask.class);
    mapBinder.addBinding(TaskType.SERVERLESS_COMMAND_TASK).toInstance(ServerlessCommandTask.class);
    mapBinder.addBinding(TaskType.SERVERLESS_S3_FETCH_TASK_NG).toInstance(ServerlessS3FetchTask.class);
    mapBinder.addBinding(TaskType.AZURE_WEB_APP_TASK_NG).toInstance(AzureWebAppTaskNG.class);
    mapBinder.addBinding(TaskType.AZURE_WEB_APP_TASK_NG_V2).toInstance(AzureWebAppTaskNG.class);
    mapBinder.addBinding(TaskType.FETCH_INSTANCE_SCRIPT_TASK_NG).toInstance(FetchInstanceScriptTaskNG.class);
    mapBinder.addBinding(TaskType.COMMAND_TASK_NG).toInstance(CommandTaskNG.class);
    mapBinder.addBinding(TaskType.COMMAND_TASK_NG_WITH_AZURE_ARTIFACT).toInstance(CommandTaskNG.class);
    mapBinder.addBinding(TaskType.COMMAND_TASK_NG_WITH_GIT_CONFIGS).toInstance(CommandTaskNG.class);
    mapBinder.addBinding(TaskType.TRIGGER_AUTHENTICATION_TASK).toInstance(TriggerAuthenticationTask.class);
    mapBinder.addBinding(TaskType.HELM_FETCH_CHART_VERSIONS_TASK_NG).toInstance(HelmFetchChartVersionTaskNG.class);
    mapBinder.addBinding(TaskType.TERRAFORM_CLOUD_TASK_NG).toInstance(TerraformCloudTaskNG.class);
    mapBinder.addBinding(TaskType.TERRAFORM_CLOUD_CLEANUP_TASK_NG).toInstance(TerraformCloudCleanupTaskNG.class);
    mapBinder.addBinding(TaskType.OCI_HELM_DOCKER_API_LIST_TAGS_TASK_NG)
        .toInstance(OciHelmDockerApiListTagsDelegateTask.class);

    // ECS NG
    MapBinder<String, EcsCommandTaskNGHandler> ecsTaskTypeToTaskHandlerMap =
        MapBinder.newMapBinder(binder(), String.class, EcsCommandTaskNGHandler.class);
    ecsTaskTypeToTaskHandlerMap.addBinding(EcsCommandTypeNG.ECS_ROLLING_DEPLOY.name())
        .to(EcsRollingDeployCommandTaskHandler.class);
    ecsTaskTypeToTaskHandlerMap.addBinding(EcsCommandTypeNG.ECS_PREPARE_ROLLBACK_DATA.name())
        .to(EcsPrepareRollbackCommandTaskHandler.class);
    ecsTaskTypeToTaskHandlerMap.addBinding(EcsCommandTypeNG.ECS_ROLLING_ROLLBACK.name())
        .to(EcsRollingRollbackCommandTaskHandler.class);
    ecsTaskTypeToTaskHandlerMap.addBinding(EcsCommandTypeNG.ECS_CANARY_DELETE.name())
        .to(EcsCanaryDeleteCommandTaskHandler.class);
    ecsTaskTypeToTaskHandlerMap.addBinding(EcsCommandTypeNG.ECS_CANARY_DEPLOY.name())
        .to(EcsCanaryDeployCommandTaskHandler.class);
    ecsTaskTypeToTaskHandlerMap.addBinding(EcsCommandTypeNG.ECS_BLUE_GREEN_CREATE_SERVICE.name())
        .to(EcsBlueGreenCreateServiceCommandTaskHandler.class);
    ecsTaskTypeToTaskHandlerMap.addBinding(EcsCommandTypeNG.ECS_BLUE_GREEN_PREPARE_ROLLBACK_DATA.name())
        .to(EcsBlueGreenPrepareRollbackCommandTaskHandler.class);
    ecsTaskTypeToTaskHandlerMap.addBinding(EcsCommandTypeNG.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS.name())
        .to(EcsBlueGreenSwapTargetGroupsCommandTaskHandler.class);
    ecsTaskTypeToTaskHandlerMap.addBinding(EcsCommandTypeNG.ECS_BLUE_GREEN_ROLLBACK.name())
        .to(EcsBlueGreenRollbackCommandTaskHandler.class);
    ecsTaskTypeToTaskHandlerMap.addBinding(EcsCommandTypeNG.ECS_RUN_TASK.name()).to(EcsRunTaskCommandTaskHandler.class);
    ecsTaskTypeToTaskHandlerMap.addBinding(EcsCommandTypeNG.ECS_RUN_TASK_ARN.name())
        .to(EcsRunTaskArnCommandTaskHandler.class);
    ecsTaskTypeToTaskHandlerMap.addBinding(EcsCommandTypeNG.ECS_TASK_ARN_ROLLING_DEPLOY.name())
        .to(EcsTaskArnRollingDeployCommandTaskHandler.class);
    ecsTaskTypeToTaskHandlerMap.addBinding(EcsCommandTypeNG.ECS_TASK_ARN_CANARY_DEPLOY.name())
        .to(EcsTaskArnCanaryDeployCommandTaskHandler.class);
    ecsTaskTypeToTaskHandlerMap.addBinding(EcsCommandTypeNG.ECS_TASK_ARN_BLUE_GREEN_CREATE_SERVICE.name())
        .to(EcsTaskArnBlueGreenCreateServiceCommandTaskHandler.class);

    mapBinder.addBinding(TaskType.ECS_GIT_FETCH_TASK_NG).toInstance(EcsGitFetchTask.class);
    mapBinder.addBinding(TaskType.ECS_GIT_FETCH_RUN_TASK_NG).toInstance(EcsGitFetchRunTask.class);
    mapBinder.addBinding(TaskType.ECS_COMMAND_TASK_NG).toInstance(EcsCommandTaskNG.class);
    mapBinder.addBinding(TaskType.ECS_S3_FETCH_TASK_NG).toInstance(EcsS3FetchTask.class);
    mapBinder.addBinding(TaskType.ECS_RUN_TASK_ARN).toInstance(EcsRunTaskArnTask.class);
    mapBinder.addBinding(TaskType.ECS_TASK_ARN_ROLLING_DEPLOY_NG).toInstance(EcsTaskArnRollingDeployTaskNG.class);
    mapBinder.addBinding(TaskType.ECS_TASK_ARN_CANARY_DEPLOY_NG).toInstance(EcsTaskArnCanaryDeployTaskNG.class);
    mapBinder.addBinding(TaskType.ECS_TASK_ARN_BLUE_GREEN_CREATE_SERVICE_NG)
        .toInstance(EcsTaskArnBlueGreenCreateServiceTaskNG.class);

    // GIT
    mapBinder.addBinding(TaskType.GIT_TASK_NG).toInstance(GitTaskNG.class);

    // Google Functions NG
    MapBinder<String, GoogleFunctionCommandTaskHandler> googleFunctionCommandTaskHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, GoogleFunctionCommandTaskHandler.class);
    googleFunctionCommandTaskHandlerMapBinder.addBinding(GoogleFunctionCommandTypeNG.GOOGLE_FUNCTION_DEPLOY.name())
        .to(GoogleFunctionDeployCommandTaskHandler.class);
    googleFunctionCommandTaskHandlerMapBinder
        .addBinding(GoogleFunctionCommandTypeNG.GOOGLE_FUNCTION_PREPARE_ROLLBACK.name())
        .to(GoogleFunctionPrepareRollbackCommandTaskHandler.class);
    googleFunctionCommandTaskHandlerMapBinder
        .addBinding(GoogleFunctionCommandTypeNG.GOOGLE_FUNCTION_WITHOUT_TRAFFIC_DEPLOY.name())
        .to(GoogleFunctionDeployWithoutTrafficCommandTaskHandler.class);
    googleFunctionCommandTaskHandlerMapBinder
        .addBinding(GoogleFunctionCommandTypeNG.GOOGLE_FUNCTION_TRAFFIC_SHIFT.name())
        .to(GoogleFunctionTrafficShiftCommandTaskHandler.class);
    googleFunctionCommandTaskHandlerMapBinder.addBinding(GoogleFunctionCommandTypeNG.GOOGLE_FUNCTION_ROLLBACK.name())
        .to(GoogleFunctionRollbackCommandTaskHandler.class);
    googleFunctionCommandTaskHandlerMapBinder
        .addBinding(GoogleFunctionCommandTypeNG.GOOGLE_FUNCTION_GEN_ONE_PREPARE_ROLLBACK.name())
        .to(GoogleFunctionGenOnePrepareRollbackCommandTaskHandler.class);
    googleFunctionCommandTaskHandlerMapBinder
        .addBinding(GoogleFunctionCommandTypeNG.GOOGLE_FUNCTION_GEN_ONE_DEPLOY.name())
        .to(GoogleFunctionGenOneDeployCommandTaskHandler.class);
    googleFunctionCommandTaskHandlerMapBinder
        .addBinding(GoogleFunctionCommandTypeNG.GOOGLE_FUNCTION_GEN_ONE_ROLLBACK.name())
        .to(GoogleFunctionGenOneRollbackCommandTaskHandler.class);
    // AWS ASG NG
    mapBinder.addBinding(TaskType.AWS_ASG_CANARY_DEPLOY_TASK_NG).toInstance(AsgCanaryDeployTaskNG.class);
    mapBinder.addBinding(TaskType.AWS_ASG_CANARY_DELETE_TASK_NG).toInstance(AsgCanaryDeleteTaskNG.class);
    mapBinder.addBinding(TaskType.AWS_ASG_ROLLING_DEPLOY_TASK_NG).toInstance(AsgRollingDeployTaskNG.class);
    mapBinder.addBinding(TaskType.AWS_ASG_PREPARE_ROLLBACK_DATA_TASK_NG).toInstance(AsgPrepareRollbackDataTaskNG.class);
    mapBinder.addBinding(TaskType.AWS_ASG_ROLLING_ROLLBACK_TASK_NG).toInstance(AsgRollingRollbackTaskNG.class);
    mapBinder.addBinding(TaskType.AWS_ASG_BLUE_GREEN_SWAP_SERVICE_TASK_NG)
        .toInstance(AsgBlueGreenSwapServiceTaskNG.class);
    mapBinder.addBinding(TaskType.AWS_ASG_BLUE_GREEN_PREPARE_ROLLBACK_DATA_TASK_NG)
        .toInstance(AsgBlueGreenPrepareRollbackDataTaskNG.class);
    mapBinder.addBinding(TaskType.AWS_ASG_BLUE_GREEN_DEPLOY_TASK_NG).toInstance(AsgBlueGreenDeployTaskNG.class);
    mapBinder.addBinding(TaskType.AWS_ASG_BLUE_GREEN_ROLLBACK_TASK_NG).toInstance(AsgBlueGreenRollbackTaskNG.class);

    bind(EcsV2Client.class).to(EcsV2ClientImpl.class);
    bind(ElbV2Client.class).to(ElbV2ClientImpl.class);
    bind(GoogleCloudFunctionClient.class).to(GoogleCloudFunctionClientImpl.class);
    bind(GoogleCloudFunctionGenOneClient.class).to(GoogleCloudFunctionGenOneClientImpl.class);
    bind(GoogleCloudRunClient.class).to(GoogleCloudRunClientImpl.class);
    bind(EksV2Client.class).to(EksV2ClientImpl.class);

    // AWS Lambda NG
    bind(AwsLambdaClient.class).to(AwsLambdaClientImpl.class);

    mapBinder.addBinding(TaskType.AZURE_NG_ARM).toInstance(AzureResourceCreationTaskNG.class);
    mapBinder.addBinding(TaskType.SHELL_SCRIPT_PROVISION).toInstance(ShellScriptProvisionTaskNG.class);
    mapBinder.addBinding(TaskType.ELASTIGROUP_STARTUP_SCRIPT_FETCH_RUN_TASK_NG)
        .toInstance(ElastigroupStartupScriptFetchRunTask.class);
    mapBinder.addBinding(TaskType.ELASTIGROUP_PARAMETERS_FETCH_RUN_TASK_NG)
        .toInstance(ElastigroupParametersFetchTask.class);
    mapBinder.addBinding(TaskType.ELASTIGROUP_SETUP_COMMAND_TASK_NG).toInstance(ElastigroupSetupCommandTaskNG.class);
    mapBinder.addBinding(TaskType.ELASTIGROUP_PRE_FETCH_TASK_NG).toInstance(ElastigroupPreFetchTaskNG.class);
    mapBinder.addBinding(TaskType.ELASTIGROUP_DEPLOY).toInstance(ElastigroupDeployTask.class);
    mapBinder.addBinding(TaskType.TERRAFORM_SECRET_CLEANUP_TASK_NG).toInstance(TerraformSecretCleanupTaskNG.class);
    mapBinder.addBinding(TaskType.TERRAGRUNT_PLAN_TASK_NG).toInstance(TerragruntPlanTaskNG.class);
    mapBinder.addBinding(TaskType.TERRAGRUNT_APPLY_TASK_NG).toInstance(TerragruntApplyTaskNG.class);
    mapBinder.addBinding(TaskType.TERRAGRUNT_DESTROY_TASK_NG).toInstance(TerragruntDestroyTaskNG.class);
    mapBinder.addBinding(TaskType.TERRAGRUNT_ROLLBACK_TASK_NG).toInstance(TerragruntRollbackTaskNG.class);
    mapBinder.addBinding(TaskType.GITOPS_FETCH_APP_TASK).toInstance(GitOpsFetchAppTask.class);
    mapBinder.addBinding(TaskType.CONTAINER_INITIALIZATION).toInstance(CIInitializeTask.class);
    mapBinder.addBinding(TaskType.ELASTIGROUP_ROLLBACK).toInstance(ElastigroupRollbackTask.class);
    mapBinder.addBinding(TaskType.CONTAINER_EXECUTE_STEP).toInstance(CIExecuteStepTask.class);
    mapBinder.addBinding(TaskType.CONTAINER_LE_STATUS).toInstance(StepStatusTask.class);
    mapBinder.addBinding(TaskType.CONTAINER_CLEANUP).toInstance(CICleanupTask.class);
    mapBinder.addBinding(TaskType.ELASTIGROUP_BG_STAGE_SETUP_COMMAND_TASK_NG)
        .toInstance(ElastigroupBGStageSetupCommandTaskNG.class);
    mapBinder.addBinding(TaskType.ELASTIGROUP_SWAP_ROUTE_COMMAND_TASK_NG)
        .toInstance(ElastigroupSwapRouteCommandTaskNG.class);

    // TAS NG
    mapBinder.addBinding(TaskType.TAS_BG_SETUP).toInstance(TasBGSetupTask.class);
    mapBinder.addBinding(TaskType.TAS_BASIC_SETUP).toInstance(TasBasicSetupTask.class);
    mapBinder.addBinding(TaskType.TAS_SWAP_ROUTES).toInstance(TasSwapRouteTask.class);
    mapBinder.addBinding(TaskType.TAS_APP_RESIZE).toInstance(TasAppResizeTask.class);
    mapBinder.addBinding(TaskType.TAS_ROLLBACK).toInstance(TasRollbackTask.class);
    mapBinder.addBinding(TaskType.TAS_SWAP_ROLLBACK).toInstance(TasSwapRollbackTask.class);
    mapBinder.addBinding(TaskType.TANZU_COMMAND).toInstance(TasCommandTask.class);
    mapBinder.addBinding(TaskType.TAS_DATA_FETCH).toInstance(TasDataFetchTask.class);
    mapBinder.addBinding(TaskType.TAS_ROLLING_DEPLOY).toInstance(TasRollingDeploymentTask.class);
    mapBinder.addBinding(TaskType.TAS_ROLLING_ROLLBACK).toInstance(TasRollingRollbackTask.class);
    mapBinder.addBinding(TaskType.BAMBOO_ARTIFACT_TASK_NG).toInstance(BambooArtifactTaskNG.class);
    mapBinder.addBinding(TaskType.GOOGLE_FUNCTION_COMMAND_TASK).toInstance(GoogleFunctionCommandTask.class);
    mapBinder.addBinding(TaskType.GCP_PROJECTS_TASK_NG).toInstance(GcpProjectTask.class);
    mapBinder.addBinding(TaskType.GCS_BUCKETS_TASK_NG).toInstance(GcsBucketPerProjectTask.class);
    mapBinder.addBinding(TaskType.GOOGLE_FUNCTION_DEPLOY_TASK).toInstance(GoogleFunctionDeployTask.class);
    mapBinder.addBinding(TaskType.GOOGLE_FUNCTION_DEPLOY_WITHOUT_TRAFFIC_TASK)
        .toInstance(GoogleFunctionDeployWithoutTrafficTask.class);
    mapBinder.addBinding(TaskType.GOOGLE_FUNCTION_PREPARE_ROLLBACK_TASK)
        .toInstance(GoogleFunctionPrepareRollbackTask.class);
    mapBinder.addBinding(TaskType.GOOGLE_FUNCTION_ROLLBACK_TASK).toInstance(GoogleFunctionRollbackTask.class);
    mapBinder.addBinding(TaskType.GOOGLE_FUNCTION_TRAFFIC_SHIFT_TASK).toInstance(GoogleFunctionTrafficShiftTask.class);
    mapBinder.addBinding(TaskType.GOOGLE_FUNCTION_GEN_ONE_DEPLOY_TASK).toInstance(GoogleFunctionGenOneDeployTask.class);
    mapBinder.addBinding(TaskType.GOOGLE_FUNCTION_GEN_ONE_PREPARE_ROLLBACK_TASK)
        .toInstance(GoogleFunctionGenOnePrepareRollbackTask.class);
    mapBinder.addBinding(TaskType.GOOGLE_FUNCTION_GEN_ONE_ROLLBACK_TASK)
        .toInstance(GoogleFunctionGenOneRollbackTask.class);

    // AWS Lambda
    mapBinder.addBinding(TaskType.AWS_LAMBDA_DEPLOY_COMMAND_TASK_NG).toInstance(AwsLambdaDeployTask.class);
    mapBinder.addBinding(TaskType.AWS_LAMBDA_PREPARE_ROLLBACK_COMMAND_TASK_NG)
        .toInstance(AwsLambdaPrepareRollbackTask.class);
    mapBinder.addBinding(TaskType.AWS_LAMBDA_ROLLBACK_COMMAND_TASK_NG).toInstance(AwsLambdaRollbackTask.class);
    mapBinder.addBinding(TaskType.TAS_ROUTE_MAPPING).toInstance(TasRouteMappingTask.class);

    // AWS EKS
    mapBinder.addBinding(TaskType.AWS_EKS_LIST_CLUSTERS_TASK).toInstance(AwsEKSListClustersDelegateTaskNG.class);
  }

  private void registerSecretManagementBindings() {
    bind(SecretManagementDelegateService.class).to(SecretManagementDelegateServiceImpl.class);
    bind(EncryptionService.class).to(EncryptionServiceImpl.class);
    bind(SecretDecryptionService.class).to(SecretDecryptionServiceImpl.class);
    bind(DelegateDecryptionService.class).to(DelegateDecryptionServiceImpl.class);
    bind(EncryptDecryptHelper.class).to(EncryptDecryptHelperImpl.class);
    bind(SecretDecryptor.class).to(SecretDecryptorImpl.class);

    binder()
        .bind(VaultEncryptor.class)
        .annotatedWith(Names.named(Encryptors.HASHICORP_VAULT_ENCRYPTOR.getName()))
        .to(HashicorpVaultEncryptor.class);

    binder()
        .bind(VaultEncryptor.class)
        .annotatedWith(Names.named(Encryptors.AWS_VAULT_ENCRYPTOR.getName()))
        .to(AwsSecretsManagerEncryptor.class);

    binder()
        .bind(VaultEncryptor.class)
        .annotatedWith(Names.named(Encryptors.AZURE_VAULT_ENCRYPTOR.getName()))
        .to(AzureVaultEncryptor.class);

    binder()
        .bind(VaultEncryptor.class)
        .annotatedWith(Names.named(Encryptors.GCP_VAULT_ENCRYPTOR.getName()))
        .to(GcpSecretsManagerEncryptor.class);

    binder()
        .bind(KmsEncryptor.class)
        .annotatedWith(Names.named(Encryptors.AWS_KMS_ENCRYPTOR.getName()))
        .to(AwsKmsEncryptor.class);

    binder()
        .bind(KmsEncryptor.class)
        .annotatedWith(Names.named(Encryptors.GCP_KMS_ENCRYPTOR.getName()))
        .to(GcpKmsEncryptor.class);

    binder()
        .bind(KmsEncryptor.class)
        .annotatedWith(Names.named(Encryptors.LOCAL_ENCRYPTOR.getName()))
        .to(LocalEncryptor.class);

    binder()
        .bind(KmsEncryptor.class)
        .annotatedWith(Names.named(Encryptors.GLOBAL_GCP_KMS_ENCRYPTOR.getName()))
        .to(GcpKmsEncryptor.class);

    binder()
        .bind(KmsEncryptor.class)
        .annotatedWith(Names.named(Encryptors.GLOBAL_AWS_KMS_ENCRYPTOR.getName()))
        .to(AwsKmsEncryptor.class);

    binder()
        .bind(CustomEncryptor.class)
        .annotatedWith(Names.named(Encryptors.CUSTOM_ENCRYPTOR.getName()))
        .to(CustomSecretsManagerEncryptor.class);

    binder()
        .bind(CustomEncryptor.class)
        .annotatedWith(Names.named(Encryptors.CUSTOM_ENCRYPTOR_NG.getName()))
        // Use ng encryptor
        .to(NGCustomSecretManagerEncryptor.class);
  }

  private void registerConnectorValidatorsBindings() {
    MapBinder<String, ConnectorValidationHandler> connectorTypeToConnectorValidationHandlerMap =
        MapBinder.newMapBinder(binder(), String.class, ConnectorValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.KUBERNETES_CLUSTER.getDisplayName())
        .to(KubernetesValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.CE_KUBERNETES_CLUSTER.getDisplayName())
        .to(CEKubernetesValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.GIT.getDisplayName())
        .to(GitValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.GITHUB.getDisplayName())
        .to(GitValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.GITLAB.getDisplayName())
        .to(GitValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.BITBUCKET.getDisplayName())
        .to(GitValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.DOCKER.getDisplayName())
        .to(DockerValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.JENKINS.getDisplayName())
        .to(JenkinsValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.BAMBOO.getDisplayName())
        .to(BambooValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.AZURE_ARTIFACTS.getDisplayName())
        .to(AzureArtifactsValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.HTTP_HELM_REPO.getDisplayName())
        .to(HttpHelmValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.OCI_HELM_REPO.getDisplayName())
        .to(OciHelmValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.VAULT.getDisplayName())
        .to(UpsertSecretTaskValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.AZURE_KEY_VAULT.getDisplayName())
        .to(UpsertSecretTaskValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.GCP_KMS.getDisplayName())
        .to(EncryptSecretTaskValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.AWS_KMS.getDisplayName())
        .to(EncryptSecretTaskValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.ARTIFACTORY.getDisplayName())
        .to(ArtifactoryValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.NEXUS.getDisplayName())
        .to(NexusValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.GCP.getDisplayName())
        .to(GcpValidationTaskHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.AWS.getDisplayName())
        .to(AwsValidationHandler.class);
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
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.AZURE.getDisplayName())
        .to(AzureValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.AZURE_REPO.getDisplayName())
        .to(GitValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.CUSTOM_SECRET_MANAGER.getDisplayName())
        .to(NotSupportedValidationHandler.class);
    connectorTypeToConnectorValidationHandlerMap.addBinding(ConnectorType.TERRAFORM_CLOUD.getDisplayName())
        .to(TerraformCloudValidationHandler.class);
  }

  private void bindExceptionHandlers() {
    MapBinder<Class<? extends Exception>, ExceptionHandler> exceptionHandlerMapBinder = MapBinder.newMapBinder(
        binder(), new TypeLiteral<Class<? extends Exception>>() {}, new TypeLiteral<ExceptionHandler>() {});

    AmazonServiceExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AmazonServiceExceptionHandler.class));
    AmazonClientExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AmazonClientExceptionHandler.class));
    AzureExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AzureExceptionHandler.class));
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
    DelegateServiceDriverExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(DelegateServiceDriverExceptionHandler.class));
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
    AzureBPRuntimeExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AzureBPRuntimeExceptionHandler.class));
    AzureClientExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AzureClientExceptionHandler.class));
    TerragruntRuntimeExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(TerragruntRuntimeExceptionHandler.class));
  }
}
