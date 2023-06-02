/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static org.mockito.Mockito.mock;

import io.harness.annotations.dev.OwnedBy;
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
import io.harness.azure.client.AzureAutoScaleSettingsClient;
import io.harness.azure.client.AzureComputeClient;
import io.harness.azure.client.AzureMonitorClient;
import io.harness.azure.client.AzureNetworkClient;
import io.harness.azure.impl.AzureAutoScaleSettingsClientImpl;
import io.harness.azure.impl.AzureComputeClientImpl;
import io.harness.azure.impl.AzureMonitorClientImpl;
import io.harness.azure.impl.AzureNetworkClientImpl;
import io.harness.cache.HarnessCacheManager;
import io.harness.dataretention.LongerDataRetentionService;
import io.harness.dataretention.LongerDataRetentionServiceImpl;
import io.harness.delegate.DelegateConfigurationServiceProvider;
import io.harness.delegate.DelegatePropertiesServiceProvider;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.task.cloudformation.CloudformationBaseHelper;
import io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl;
import io.harness.exception.WingsException;
import io.harness.git.GitClientV2;
import io.harness.git.GitClientV2Impl;
import io.harness.helpers.EncryptDecryptHelperImpl;
import io.harness.manage.ManagedExecutorService;
import io.harness.manifest.CustomManifestService;
import io.harness.manifest.CustomManifestServiceImpl;
import io.harness.openshift.OpenShiftClient;
import io.harness.openshift.OpenShiftClientImpl;
import io.harness.pcf.CfCliClient;
import io.harness.pcf.CfDeploymentManagerImpl;
import io.harness.pcf.CfSdkClient;
import io.harness.pcf.cfcli.client.CfCliClientImpl;
import io.harness.pcf.cfsdk.CfSdkClientImpl;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.secrets.SecretsDelegateCacheService;
import io.harness.secrets.SecretsDelegateCacheServiceImpl;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.ShellExecutionService;
import io.harness.shell.ShellExecutionServiceImpl;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.SpotInstHelperServiceDelegateImpl;
import io.harness.threading.ThreadPool;

import software.wings.delegatetasks.DelegateCVActivityLogService;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.amazons3.AmazonS3Service;
import software.wings.helpers.ext.amazons3.AmazonS3ServiceImpl;
import software.wings.helpers.ext.artifactory.ArtifactoryService;
import software.wings.helpers.ext.artifactory.ArtifactoryServiceImpl;
import software.wings.helpers.ext.azure.devops.AzureArtifactsService;
import software.wings.helpers.ext.azure.devops.AzureArtifactsServiceImpl;
import software.wings.helpers.ext.bamboo.BambooService;
import software.wings.helpers.ext.bamboo.BambooServiceImpl;
import software.wings.helpers.ext.nexus.NexusService;
import software.wings.helpers.ext.nexus.NexusServiceImpl;
import software.wings.provider.NoopDelegateConfigurationServiceProviderImpl;
import software.wings.provider.NoopDelegatePropertiesServiceProviderImpl;
import software.wings.service.impl.AmazonS3BuildServiceImpl;
import software.wings.service.impl.ArtifactoryBuildServiceImpl;
import software.wings.service.impl.AzureArtifactsBuildServiceImpl;
import software.wings.service.impl.BambooBuildServiceImpl;
import software.wings.service.impl.ContainerServiceImpl;
import software.wings.service.impl.DockerBuildServiceImpl;
import software.wings.service.impl.EcrBuildServiceImpl;
import software.wings.service.impl.GitServiceImpl;
import software.wings.service.impl.NexusBuildServiceImpl;
import software.wings.service.impl.analysis.APMDelegateService;
import software.wings.service.impl.analysis.APMDelegateServiceImpl;
import software.wings.service.impl.appdynamics.AppdynamicsDelegateServiceImpl;
import software.wings.service.impl.aws.delegate.AwsAppAutoScalingHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsAsgHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsCFHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsEc2HelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsEcsHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsElbHelperServiceDelegateImpl;
import software.wings.service.impl.aws.delegate.AwsS3HelperServiceDelegateImpl;
import software.wings.service.impl.aws.manager.AwsS3HelperServiceManagerImpl;
import software.wings.service.impl.cloudwatch.CloudWatchDelegateServiceImpl;
import software.wings.service.impl.dynatrace.DynaTraceDelegateServiceImpl;
import software.wings.service.impl.elk.ElkDelegateServiceImpl;
import software.wings.service.impl.instana.InstanaDelegateServiceImpl;
import software.wings.service.impl.logz.LogzDelegateServiceImpl;
import software.wings.service.impl.newrelic.NewRelicDelgateServiceImpl;
import software.wings.service.impl.security.EncryptionServiceImpl;
import software.wings.service.impl.security.SecretDecryptionServiceImpl;
import software.wings.service.impl.security.SecretManagementDelegateServiceImpl;
import software.wings.service.impl.splunk.SplunkDelegateServiceImpl;
import software.wings.service.impl.stackdriver.StackDriverDelegateServiceImpl;
import software.wings.service.impl.sumo.SumoDelegateServiceImpl;
import software.wings.service.impl.yaml.GitClientHelper;
import software.wings.service.impl.yaml.GitClientImpl;
import software.wings.service.intfc.AmazonS3BuildService;
import software.wings.service.intfc.ArtifactoryBuildService;
import software.wings.service.intfc.AzureArtifactsBuildService;
import software.wings.service.intfc.BambooBuildService;
import software.wings.service.intfc.ContainerService;
import software.wings.service.intfc.DockerBuildService;
import software.wings.service.intfc.EcrBuildService;
import software.wings.service.intfc.GitService;
import software.wings.service.intfc.NexusBuildService;
import software.wings.service.intfc.appdynamics.AppdynamicsDelegateService;
import software.wings.service.intfc.aws.delegate.AwsAppAutoScalingHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsAsgHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEcsHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsS3HelperServiceDelegate;
import software.wings.service.intfc.aws.manager.AwsS3HelperServiceManager;
import software.wings.service.intfc.cloudwatch.CloudWatchDelegateService;
import software.wings.service.intfc.dynatrace.DynaTraceDelegateService;
import software.wings.service.intfc.elk.ElkDelegateService;
import software.wings.service.intfc.instana.InstanaDelegateService;
import software.wings.service.intfc.logz.LogzDelegateService;
import software.wings.service.intfc.newrelic.NewRelicDelegateService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManagementDelegateService;
import software.wings.service.intfc.splunk.SplunkDelegateService;
import software.wings.service.intfc.stackdriver.StackDriverDelegateService;
import software.wings.service.intfc.sumo.SumoDelegateService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class WingsTestModule extends AbstractModule {
  @Provides
  @Named("TestCache")
  @Singleton
  public Cache<Integer, Integer> getTestCache(HarnessCacheManager harnessCacheManager) {
    return harnessCacheManager.getCache(
        "TestCache", Integer.class, Integer.class, AccessedExpiryPolicy.factoryOf(Duration.TEN_MINUTES));
  }

  @Provides
  @Named("ExceptionTestCache")
  @Singleton
  public Cache<Integer, WingsException> getExceptionTestCache(HarnessCacheManager harnessCacheManager) {
    return harnessCacheManager.getCache("ExceptionTestCache", Integer.class, WingsException.class,
        AccessedExpiryPolicy.factoryOf(Duration.TEN_MINUTES));
  }

  @Override
  protected void configure() {
    DelegateFileManager mockDelegateFileManager = mock(DelegateFileManager.class);
    bind(DelegateFileManager.class).toInstance(mockDelegateFileManager);
    bind(AmazonS3BuildService.class).to(AmazonS3BuildServiceImpl.class);
    bind(AmazonS3Service.class).to(AmazonS3ServiceImpl.class);
    bind(NewRelicDelegateService.class).to(NewRelicDelgateServiceImpl.class);
    bind(AppdynamicsDelegateService.class).to(AppdynamicsDelegateServiceImpl.class);
    bind(DynaTraceDelegateService.class).to(DynaTraceDelegateServiceImpl.class);
    bind(APMDelegateService.class).to(APMDelegateServiceImpl.class);
    bind(DelegatePropertiesServiceProvider.class).to(NoopDelegatePropertiesServiceProviderImpl.class);
    bind(DelegateConfigurationServiceProvider.class).to(NoopDelegateConfigurationServiceProviderImpl.class);
    bind(SecretsDelegateCacheService.class).to(SecretsDelegateCacheServiceImpl.class);
    bind(EncryptionService.class).to(EncryptionServiceImpl.class);
    bind(SecretDecryptionService.class).to(SecretDecryptionServiceImpl.class);
    bind(SecretManagementDelegateService.class).to(SecretManagementDelegateServiceImpl.class);
    bind(ElkDelegateService.class).to(ElkDelegateServiceImpl.class);
    bind(LogzDelegateService.class).to(LogzDelegateServiceImpl.class);
    bind(SplunkDelegateService.class).to(SplunkDelegateServiceImpl.class);
    bind(SumoDelegateService.class).to(SumoDelegateServiceImpl.class);
    bind(InstanaDelegateService.class).to(InstanaDelegateServiceImpl.class);
    bind(StackDriverDelegateService.class).to(StackDriverDelegateServiceImpl.class);
    bind(ArtifactoryBuildService.class).to(ArtifactoryBuildServiceImpl.class);
    bind(ArtifactoryService.class).to(ArtifactoryServiceImpl.class);
    bind(NexusService.class).to(NexusServiceImpl.class);
    bind(NexusBuildService.class).to(NexusBuildServiceImpl.class);
    bind(BambooBuildService.class).to(BambooBuildServiceImpl.class);
    bind(BambooService.class).to(BambooServiceImpl.class);
    bind(DockerBuildService.class).to(DockerBuildServiceImpl.class);
    bind(DockerRegistryService.class).to(DockerRegistryServiceImpl.class);
    bind(AzureArtifactsRegistryService.class).to(AzureArtifactsRegistryServiceImpl.class);
    bind(GithubPackagesRegistryService.class).to(GithubPackagesRegistryServiceImpl.class);
    bind(AMIRegistryService.class).to(AMIRegistryServiceImpl.class);
    bind(DockerRestClientFactory.class).to(DockerRestClientFactoryImpl.class);
    bind(GcrApiService.class).to(GcrApiServiceImpl.class);
    bind(GithubPackagesRestClientFactory.class).to(GithubPackagesRestClientFactoryImpl.class);
    bind(GarApiService.class).to(GARApiServiceImpl.class);
    bind(EcrBuildService.class).to(EcrBuildServiceImpl.class);
    bind(ContainerService.class).to(ContainerServiceImpl.class);
    bind(AwsAppAutoScalingHelperServiceDelegate.class).to(AwsAppAutoScalingHelperServiceDelegateImpl.class);
    bind(AwsElbHelperServiceDelegate.class).to(AwsElbHelperServiceDelegateImpl.class);
    bind(SpotInstHelperServiceDelegate.class).to(SpotInstHelperServiceDelegateImpl.class);
    bind(AwsAsgHelperServiceDelegate.class).to(AwsAsgHelperServiceDelegateImpl.class);
    bind(AwsEc2HelperServiceDelegate.class).to(AwsEc2HelperServiceDelegateImpl.class);
    bind(AwsEcsHelperServiceDelegate.class).to(AwsEcsHelperServiceDelegateImpl.class);
    bind(AwsS3HelperServiceDelegate.class).to(AwsS3HelperServiceDelegateImpl.class);
    bind(GitService.class).to(GitServiceImpl.class);
    bind(OpenShiftClient.class).to(OpenShiftClientImpl.class);
    bind(CfCliClient.class).to(CfCliClientImpl.class);
    bind(CfSdkClient.class).to(CfSdkClientImpl.class);
    DelegateLogService mockDelegateLogService = mock(DelegateLogService.class);
    bind(DelegateLogService.class).toInstance(mockDelegateLogService);
    DelegateCVActivityLogService mockDelegateCVActivityLogService = mock(DelegateCVActivityLogService.class);
    bind(DelegateCVActivityLogService.class).toInstance(mockDelegateCVActivityLogService);
    GitClientHelper gitClientHelper = mock(GitClientHelper.class);
    bind(GitClientImpl.class);
    bind(CfDeploymentManagerImpl.class);
    bind(GitClientV2.class).to(GitClientV2Impl.class);
    bind(AwsCFHelperServiceDelegate.class).to(AwsCFHelperServiceDelegateImpl.class);
    bind(AwsS3HelperServiceManager.class).to(AwsS3HelperServiceManagerImpl.class);
    bind(AzureArtifactsService.class).to(AzureArtifactsServiceImpl.class);
    bind(AzureArtifactsBuildService.class).to(AzureArtifactsBuildServiceImpl.class);
    bind(CloudWatchDelegateService.class).to(CloudWatchDelegateServiceImpl.class);
    bind(AzureComputeClient.class).to(AzureComputeClientImpl.class);
    bind(AzureAutoScaleSettingsClient.class).to(AzureAutoScaleSettingsClientImpl.class);
    bind(AzureNetworkClient.class).to(AzureNetworkClientImpl.class);
    bind(AzureMonitorClient.class).to(AzureMonitorClientImpl.class);
    bind(CustomManifestService.class).to(CustomManifestServiceImpl.class);
    bind(EncryptDecryptHelper.class).to(EncryptDecryptHelperImpl.class);
    bind(DelegateFileManagerBase.class).toInstance(mock(DelegateFileManagerBase.class));
    bind(AWSCloudformationClient.class).to(AWSCloudformationClientImpl.class);
    bind(CloudformationBaseHelper.class).to(CloudformationBaseHelperImpl.class);
    bind(LongerDataRetentionService.class).to(LongerDataRetentionServiceImpl.class);

    bind(ExecutorService.class)
        .annotatedWith(Names.named("systemExecutor"))
        .toInstance(ThreadPool.create(4, 8, 1, TimeUnit.SECONDS,
            new ThreadFactoryBuilder().setNameFormat("system-%d").setPriority(Thread.MAX_PRIORITY).build()));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("artifactExecutor"))
        .toInstance(ThreadPool.create(10, 40, 1, TimeUnit.SECONDS,
            new ThreadFactoryBuilder()
                .setNameFormat("artifact-collection-%d")
                .setPriority(Thread.MIN_PRIORITY)
                .build()));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("timeoutExecutor"))
        .toInstance(ThreadPool.create(10, 40, 1, TimeUnit.SECONDS,
            new ThreadFactoryBuilder().setNameFormat("timeout-enforcer-%d").setPriority(Thread.NORM_PRIORITY).build()));
    bind(ManagedExecutorService.class)
        .toInstance(new ManagedExecutorService(ThreadPool.create(1, 1, 0, TimeUnit.SECONDS)));
    bind(ShellExecutionService.class).to(ShellExecutionServiceImpl.class);

    MapBinder<String, Cache<?, ?>> mapBinder =
        MapBinder.newMapBinder(binder(), TypeLiteral.get(String.class), new TypeLiteral<Cache<?, ?>>() {});
    mapBinder.addBinding("TestCache").to(Key.get(new TypeLiteral<Cache<Integer, Integer>>() {
    }, Names.named("TestCache")));
    mapBinder.addBinding("ExceptionTestCache").to(Key.get(new TypeLiteral<Cache<Integer, WingsException>>() {
    }, Names.named("ExceptionTestCache")));
  }
}
