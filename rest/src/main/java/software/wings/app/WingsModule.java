package software.wings.app;

import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

import ro.fortsoft.pf4j.DefaultPluginManager;
import ro.fortsoft.pf4j.PluginManager;
import software.wings.api.LoadBalancer;
import software.wings.beans.AwsConfig;
import software.wings.beans.BambooConfig;
import software.wings.beans.DockerConfig;
import software.wings.beans.EcrConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.cloudprovider.aws.AwsClusterServiceImpl;
import software.wings.cloudprovider.aws.AwsCodeDeployService;
import software.wings.cloudprovider.aws.AwsCodeDeployServiceImpl;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.cloudprovider.aws.EcsContainerServiceImpl;
import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.cloudprovider.gke.GkeClusterServiceImpl;
import software.wings.cloudprovider.gke.KubernetesContainerService;
import software.wings.cloudprovider.gke.KubernetesContainerServiceImpl;
import software.wings.common.WingsExpressionProcessorFactory;
import software.wings.core.cloud.ElasticLoadBalancer;
import software.wings.dl.WingsMongoPersistence;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.amazons3.AmazonS3Service;
import software.wings.helpers.ext.amazons3.AmazonS3ServiceImpl;
import software.wings.helpers.ext.artifactory.ArtifactoryService;
import software.wings.helpers.ext.artifactory.ArtifactoryServiceImpl;
import software.wings.helpers.ext.bamboo.BambooService;
import software.wings.helpers.ext.bamboo.BambooServiceImpl;
import software.wings.helpers.ext.docker.DockerRegistryService;
import software.wings.helpers.ext.docker.DockerRegistryServiceImpl;
import software.wings.helpers.ext.ecr.EcrClassicService;
import software.wings.helpers.ext.ecr.EcrClassicServiceImpl;
import software.wings.helpers.ext.ecr.EcrService;
import software.wings.helpers.ext.ecr.EcrServiceImpl;
import software.wings.helpers.ext.gcr.GcrService;
import software.wings.helpers.ext.gcr.GcrServiceImpl;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.helpers.ext.jenkins.JenkinsFactory;
import software.wings.helpers.ext.jenkins.JenkinsImpl;
import software.wings.helpers.ext.nexus.NexusService;
import software.wings.helpers.ext.nexus.NexusServiceImpl;
import software.wings.licensing.DatabaseLicenseProviderImpl;
import software.wings.licensing.LicenseManager;
import software.wings.licensing.LicenseManagerImpl;
import software.wings.licensing.LicenseProvider;
import software.wings.scheduler.JobScheduler;
import software.wings.scheduler.QuartzScheduler;
import software.wings.scheduler.VerificationJobScheduler;
import software.wings.service.EcrClassicBuildServiceImpl;
import software.wings.service.impl.AccountServiceImpl;
import software.wings.service.impl.ActivityServiceImpl;
import software.wings.service.impl.AmazonS3BuildServiceImpl;
import software.wings.service.impl.AppContainerServiceImpl;
import software.wings.service.impl.AppServiceImpl;
import software.wings.service.impl.ArtifactServiceImpl;
import software.wings.service.impl.ArtifactStreamServiceImpl;
import software.wings.service.impl.ArtifactoryBuildServiceImpl;
import software.wings.service.impl.AssignDelegateServiceImpl;
import software.wings.service.impl.AuditServiceImpl;
import software.wings.service.impl.AuthServiceImpl;
import software.wings.service.impl.AwsHelperResourceServiceImpl;
import software.wings.service.impl.AwsInfrastructureProvider;
import software.wings.service.impl.BambooBuildServiceImpl;
import software.wings.service.impl.BuildSourceServiceImpl;
import software.wings.service.impl.CatalogServiceImpl;
import software.wings.service.impl.CloudWatchServiceImpl;
import software.wings.service.impl.CommandServiceImpl;
import software.wings.service.impl.ConfigServiceImpl;
import software.wings.service.impl.DelegateScopeServiceImpl;
import software.wings.service.impl.DelegateServiceImpl;
import software.wings.service.impl.DirectInfrastructureProvider;
import software.wings.service.impl.DockerBuildServiceImpl;
import software.wings.service.impl.DownloadTokenServiceImpl;
import software.wings.service.impl.EcrBuildServiceImpl;
import software.wings.service.impl.EmailNotificationServiceImpl;
import software.wings.service.impl.EntityVersionServiceImpl;
import software.wings.service.impl.EnvironmentServiceImpl;
import software.wings.service.impl.FileServiceImpl;
import software.wings.service.impl.GcpInfrastructureProvider;
import software.wings.service.impl.GcrBuildServiceImpl;
import software.wings.service.impl.HostServiceImpl;
import software.wings.service.impl.InfrastructureMappingServiceImpl;
import software.wings.service.impl.JenkinsBuildServiceImpl;
import software.wings.service.impl.LogServiceImpl;
import software.wings.service.impl.NexusBuildServiceImpl;
import software.wings.service.impl.NotificationDispatcherServiceImpl;
import software.wings.service.impl.NotificationServiceImpl;
import software.wings.service.impl.NotificationSetupServiceImpl;
import software.wings.service.impl.PipelineServiceImpl;
import software.wings.service.impl.PlatformServiceImpl;
import software.wings.service.impl.PluginServiceImpl;
import software.wings.service.impl.RoleServiceImpl;
import software.wings.service.impl.ServiceInstanceServiceImpl;
import software.wings.service.impl.ServiceLocator;
import software.wings.service.impl.ServiceResourceServiceImpl;
import software.wings.service.impl.ServiceTemplateServiceImpl;
import software.wings.service.impl.ServiceVariableServiceImpl;
import software.wings.service.impl.SettingsServiceImpl;
import software.wings.service.impl.SetupServiceImpl;
import software.wings.service.impl.SlackNotificationServiceImpl;
import software.wings.service.impl.StaticInfrastructureProvider;
import software.wings.service.impl.StatisticsServiceImpl;
import software.wings.service.impl.SystemCatalogSeviceImpl;
import software.wings.service.impl.UserServiceImpl;
import software.wings.service.impl.WorkflowExecutionServiceImpl;
import software.wings.service.impl.WorkflowServiceImpl;
import software.wings.service.impl.analysis.AnalysisServiceImpl;
import software.wings.service.impl.appdynamics.AppdynamicsServiceImpl;
import software.wings.service.impl.dashboardStats.DashboardStatisticsServiceImpl;
import software.wings.service.impl.dashboardStats.InstanceServiceImpl;
import software.wings.service.impl.elk.ElkAnalysisServiceImpl;
import software.wings.service.impl.expression.ExpressionBuilderServiceImpl;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AmazonS3BuildService;
import software.wings.service.intfc.AppContainerService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactoryBuildService;
import software.wings.service.intfc.AssignDelegateService;
import software.wings.service.intfc.AuditService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.AwsHelperResourceService;
import software.wings.service.intfc.BambooBuildService;
import software.wings.service.intfc.BuildService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.CatalogService;
import software.wings.service.intfc.CloudWatchService;
import software.wings.service.intfc.CommandService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.DelegateScopeService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.DockerBuildService;
import software.wings.service.intfc.DownloadTokenService;
import software.wings.service.intfc.EcrBuildService;
import software.wings.service.intfc.EcrClassicBuildService;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.service.intfc.EntityVersionService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.GcrBuildService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureProvider;
import software.wings.service.intfc.JenkinsBuildService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.NexusBuildService;
import software.wings.service.intfc.NotificationDispatcherService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.NotificationSetupService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.PlatformService;
import software.wings.service.intfc.PluginService;
import software.wings.service.intfc.RoleService;
import software.wings.service.intfc.ServiceInstanceService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.SetupService;
import software.wings.service.intfc.SlackNotificationService;
import software.wings.service.intfc.StatisticsService;
import software.wings.service.intfc.SystemCatalogService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.analysis.AnalysisService;
import software.wings.service.intfc.appdynamics.AppdynamicsService;
import software.wings.service.intfc.dashboardStats.DashboardStatisticsService;
import software.wings.service.intfc.dashboardStats.InstanceService;
import software.wings.service.intfc.elk.ElkAnalysisService;
import software.wings.service.intfc.expression.ExpressionBuilderService;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingValue.SettingVariableTypes;
import software.wings.sm.ExpressionProcessorFactory;
import software.wings.utils.HostValidationService;
import software.wings.utils.HostValidationServiceImpl;

/**
 * Guice Module for initializing all beans.
 *
 * @author Rishi
 */
public class WingsModule extends AbstractModule {
  private MainConfiguration configuration;

  /**
   * Creates a guice module for portal app.
   *
   * @param configuration Dropwizard configuration
   */
  public WingsModule(MainConfiguration configuration) {
    this.configuration = configuration;
  }

  /* (non-Javadoc)
   * @see com.google.inject.AbstractModule#configure()
   */
  @Override
  protected void configure() {
    bind(AuthService.class).to(AuthServiceImpl.class);
    bind(MainConfiguration.class).toInstance(configuration);
    bind(WingsPersistence.class).to(WingsMongoPersistence.class);
    bind(AppService.class).to(AppServiceImpl.class);
    bind(ArtifactService.class).to(ArtifactServiceImpl.class);
    bind(AuditService.class).to(AuditServiceImpl.class);
    bind(FileService.class).to(FileServiceImpl.class);
    bind(PlatformService.class).to(PlatformServiceImpl.class);
    bind(ArtifactStreamService.class).to(ArtifactStreamServiceImpl.class);
    bind(UserService.class).to(UserServiceImpl.class);
    bind(RoleService.class).to(RoleServiceImpl.class);
    bind(ServiceResourceService.class).to(ServiceResourceServiceImpl.class);
    bind(EnvironmentService.class).to(EnvironmentServiceImpl.class);
    bind(ServiceTemplateService.class).to(ServiceTemplateServiceImpl.class);
    bind(WorkflowService.class).to(WorkflowServiceImpl.class);
    bind(WorkflowExecutionService.class).to(WorkflowExecutionServiceImpl.class);
    bind(PluginManager.class).to(DefaultPluginManager.class).asEagerSingleton();
    bind(ConfigService.class).to(ConfigServiceImpl.class);
    bind(AppContainerService.class).to(AppContainerServiceImpl.class);
    bind(CatalogService.class).to(CatalogServiceImpl.class);
    bind(HostService.class).to(HostServiceImpl.class);
    bind(JenkinsBuildService.class).to(JenkinsBuildServiceImpl.class);
    bind(SettingsService.class).to(SettingsServiceImpl.class);
    bind(ExpressionProcessorFactory.class).to(WingsExpressionProcessorFactory.class);
    bind(EmailNotificationService.class).to(EmailNotificationServiceImpl.class);
    bind(ServiceInstanceService.class).to(ServiceInstanceServiceImpl.class);
    bind(ActivityService.class).to(ActivityServiceImpl.class);
    bind(LogService.class).to(LogServiceImpl.class);
    bind(SetupService.class).to(SetupServiceImpl.class);
    bind(NotificationService.class).to(NotificationServiceImpl.class);
    bind(StatisticsService.class).to(StatisticsServiceImpl.class);
    bind(DashboardStatisticsService.class).to(DashboardStatisticsServiceImpl.class);
    bind(InstanceService.class).to(InstanceServiceImpl.class);
    bind(BuildSourceService.class).to(BuildSourceServiceImpl.class);
    bind(ServiceVariableService.class).to(ServiceVariableServiceImpl.class);
    bind(AccountService.class).to(AccountServiceImpl.class);
    bind(PipelineService.class).to(PipelineServiceImpl.class);
    bind(NotificationSetupService.class).to(NotificationSetupServiceImpl.class);
    bind(NotificationDispatcherService.class).to(NotificationDispatcherServiceImpl.class);
    bind(ServiceLocator.class);
    bind(EntityVersionService.class).to(EntityVersionServiceImpl.class);
    bind(PluginService.class).to(PluginServiceImpl.class);
    bind(CommandService.class).to(CommandServiceImpl.class);
    bind(DelegateService.class).to(DelegateServiceImpl.class);
    bind(DelegateScopeService.class).to(DelegateScopeServiceImpl.class);
    bind(BambooService.class).to(BambooServiceImpl.class);
    bind(BambooBuildService.class).to(BambooBuildServiceImpl.class);
    bind(DownloadTokenService.class).to(DownloadTokenServiceImpl.class);
    bind(CloudWatchService.class).to(CloudWatchServiceImpl.class);
    bind(SlackNotificationService.class).to(SlackNotificationServiceImpl.class);
    bind(EcsContainerService.class).to(EcsContainerServiceImpl.class);
    bind(AwsClusterService.class).to(AwsClusterServiceImpl.class);
    bind(GkeClusterService.class).to(GkeClusterServiceImpl.class);
    bind(KubernetesContainerService.class).to(KubernetesContainerServiceImpl.class);
    bind(DockerBuildService.class).to(DockerBuildServiceImpl.class);
    bind(DockerRegistryService.class).to(DockerRegistryServiceImpl.class);
    bind(InfrastructureMappingService.class).to(InfrastructureMappingServiceImpl.class);
    bind(LicenseManager.class).to(LicenseManagerImpl.class);
    bind(LicenseProvider.class).to(DatabaseLicenseProviderImpl.class);
    bind(NexusService.class).to(NexusServiceImpl.class);
    bind(NexusBuildService.class).to(NexusBuildServiceImpl.class);
    bind(AppdynamicsService.class).to(AppdynamicsServiceImpl.class);
    bind(AnalysisService.class).to(AnalysisServiceImpl.class);
    bind(ElkAnalysisService.class).to(ElkAnalysisServiceImpl.class);
    bind(SystemCatalogService.class).to(SystemCatalogSeviceImpl.class);
    bind(AwsCodeDeployService.class).to(AwsCodeDeployServiceImpl.class);
    bind(ArtifactoryBuildService.class).to(ArtifactoryBuildServiceImpl.class);
    bind(ArtifactoryService.class).to(ArtifactoryServiceImpl.class);
    bind(EcrBuildService.class).to(EcrBuildServiceImpl.class);
    bind(EcrClassicBuildService.class).to(EcrClassicBuildServiceImpl.class);
    bind(EcrService.class).to(EcrServiceImpl.class);
    bind(AmazonS3BuildService.class).to(AmazonS3BuildServiceImpl.class);
    bind(AmazonS3Service.class).to(AmazonS3ServiceImpl.class);
    bind(EcrClassicService.class).to(EcrClassicServiceImpl.class);
    bind(GcrService.class).to(GcrServiceImpl.class);
    bind(GcrBuildService.class).to(GcrBuildServiceImpl.class);
    bind(AwsHelperResourceService.class).to(AwsHelperResourceServiceImpl.class);
    bind(AssignDelegateService.class).to(AssignDelegateServiceImpl.class);
    bind(ExpressionBuilderService.class).to(ExpressionBuilderServiceImpl.class);
    bind(HostValidationService.class).to(HostValidationServiceImpl.class);

    MapBinder<String, InfrastructureProvider> infrastructureProviderMapBinder =
        MapBinder.newMapBinder(binder(), String.class, InfrastructureProvider.class);
    infrastructureProviderMapBinder.addBinding(SettingVariableTypes.AWS.name()).to(AwsInfrastructureProvider.class);
    infrastructureProviderMapBinder.addBinding(SettingVariableTypes.GCP.name()).to(GcpInfrastructureProvider.class);
    infrastructureProviderMapBinder.addBinding(SettingVariableTypes.PHYSICAL_DATA_CENTER.name())
        .to(StaticInfrastructureProvider.class);
    infrastructureProviderMapBinder.addBinding(SettingVariableTypes.DIRECT.name())
        .to(DirectInfrastructureProvider.class);

    MapBinder<Class<? extends SettingValue>, Class<? extends BuildService>> buildServiceMapBinder =
        MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends SettingValue>>() {},
            new TypeLiteral<Class<? extends BuildService>>() {});

    buildServiceMapBinder.addBinding(JenkinsConfig.class).toInstance(JenkinsBuildService.class);
    buildServiceMapBinder.addBinding(BambooConfig.class).toInstance(BambooBuildService.class);
    buildServiceMapBinder.addBinding(DockerConfig.class).toInstance(DockerBuildService.class);
    buildServiceMapBinder.addBinding(AwsConfig.class).toInstance(EcrBuildService.class);
    buildServiceMapBinder.addBinding(EcrConfig.class).toInstance(EcrClassicBuildService.class);
    buildServiceMapBinder.addBinding(GcpConfig.class).toInstance(GcrBuildService.class);
    buildServiceMapBinder.addBinding(NexusConfig.class).toInstance(NexusBuildService.class);
    buildServiceMapBinder.addBinding(ArtifactoryConfig.class).toInstance(ArtifactoryBuildService.class);

    install(new FactoryModuleBuilder().implement(Jenkins.class, JenkinsImpl.class).build(JenkinsFactory.class));

    Multibinder<LoadBalancer> loadBalancerMultibinder = Multibinder.newSetBinder(binder(), LoadBalancer.class);
    loadBalancerMultibinder.addBinding().to(ElasticLoadBalancer.class);
    bind(TimeLimiter.class).toInstance(new SimpleTimeLimiter());

    bind(QuartzScheduler.class).annotatedWith(Names.named("JobScheduler")).to(JobScheduler.class);
    bind(QuartzScheduler.class)
        .annotatedWith(Names.named("VerificationJobScheduler"))
        .to(VerificationJobScheduler.class);
  }
}
