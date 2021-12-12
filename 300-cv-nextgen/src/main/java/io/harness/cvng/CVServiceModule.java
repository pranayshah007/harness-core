package io.harness.cvng;

import static io.harness.cvng.beans.change.ChangeSourceType.HARNESS_CD;
import static io.harness.cvng.cdng.services.impl.CVNGNotifyEventListener.CVNG_ORCHESTRATION;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.PrimaryVersionManagerModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.cvng.activity.entities.Activity.ActivityUpdatableEntity;
import io.harness.cvng.activity.entities.DeploymentActivity.DeploymentActivityUpdatableEntity;
import io.harness.cvng.activity.entities.HarnessCDCurrentGenActivity.HarnessCDCurrentGenActivityUpdatableEntity;
import io.harness.cvng.activity.entities.KubernetesClusterActivity.KubernetesClusterActivityUpdatableEntity;
import io.harness.cvng.activity.entities.PagerDutyActivity.PagerDutyActivityUpdatableEntity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.activity.services.api.ActivityUpdateHandler;
import io.harness.cvng.activity.services.impl.ActivityServiceImpl;
import io.harness.cvng.activity.services.impl.DeploymentActivityUpdateHandler;
import io.harness.cvng.activity.services.impl.KubernetesClusterActivityUpdateHandler;
import io.harness.cvng.activity.source.services.api.KubernetesActivitySourceService;
import io.harness.cvng.activity.source.services.impl.KubernetesActivitySourceServiceImpl;
import io.harness.cvng.alert.services.AlertRuleAnomalyService;
import io.harness.cvng.alert.services.api.AlertRuleService;
import io.harness.cvng.alert.services.impl.AlertRuleAnomalyServiceImpl;
import io.harness.cvng.alert.services.impl.AlertRuleServiceImpl;
import io.harness.cvng.analysis.services.api.AnalysisService;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.analysis.services.api.HealthVerificationService;
import io.harness.cvng.analysis.services.api.LearningEngineDevService;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
import io.harness.cvng.analysis.services.api.LogClusterService;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.analysis.services.api.TimeSeriesAnomalousPatternsService;
import io.harness.cvng.analysis.services.api.TrendAnalysisService;
import io.harness.cvng.analysis.services.api.VerificationJobInstanceAnalysisService;
import io.harness.cvng.analysis.services.impl.AnalysisServiceImpl;
import io.harness.cvng.analysis.services.impl.DeploymentLogAnalysisServiceImpl;
import io.harness.cvng.analysis.services.impl.DeploymentTimeSeriesAnalysisServiceImpl;
import io.harness.cvng.analysis.services.impl.HealthVerificationServiceImpl;
import io.harness.cvng.analysis.services.impl.LearningEngineDevServiceImpl;
import io.harness.cvng.analysis.services.impl.LearningEngineTaskServiceImpl;
import io.harness.cvng.analysis.services.impl.LogAnalysisServiceImpl;
import io.harness.cvng.analysis.services.impl.LogClusterServiceImpl;
import io.harness.cvng.analysis.services.impl.TimeSeriesAnalysisServiceImpl;
import io.harness.cvng.analysis.services.impl.TimeSeriesAnomalousPatternsServiceImpl;
import io.harness.cvng.analysis.services.impl.TrendAnalysisServiceImpl;
import io.harness.cvng.analysis.services.impl.VerificationJobInstanceAnalysisServiceImpl;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.cdng.services.api.CVNGStepService;
import io.harness.cvng.cdng.services.api.CVNGStepTaskService;
import io.harness.cvng.cdng.services.api.VerifyStepDemoService;
import io.harness.cvng.cdng.services.impl.CVNGStepServiceImpl;
import io.harness.cvng.cdng.services.impl.CVNGStepTaskServiceImpl;
import io.harness.cvng.cdng.services.impl.VerifyStepDemoServiceImpl;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.client.NextGenServiceImpl;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.client.VerificationManagerServiceImpl;
import io.harness.cvng.core.entities.AppDynamicsCVConfig.AppDynamicsCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.CVConfig.CVConfigUpdatableEntity;
import io.harness.cvng.core.entities.DataCollectionTask.Type;
import io.harness.cvng.core.entities.DatadogLogCVConfig.DatadogLogCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.DatadogMetricCVConfig.DatadogMetricCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.NewRelicCVConfig.NewRelicCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.PrometheusCVConfig.PrometheusUpdatableEntity;
import io.harness.cvng.core.entities.SplunkCVConfig.SplunkCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.StackdriverCVConfig.StackDriverCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.StackdriverLogCVConfig.StackdriverLogCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.entities.changeSource.HarnessCDChangeSource;
import io.harness.cvng.core.entities.changeSource.HarnessCDCurrentGenChangeSource;
import io.harness.cvng.core.entities.changeSource.KubernetesChangeSource;
import io.harness.cvng.core.entities.changeSource.PagerDutyChangeSource;
import io.harness.cvng.core.jobs.AccountChangeEventMessageProcessor;
import io.harness.cvng.core.jobs.ConnectorChangeEventMessageProcessor;
import io.harness.cvng.core.jobs.ConsumerMessageProcessor;
import io.harness.cvng.core.jobs.OrganizationChangeEventMessageProcessor;
import io.harness.cvng.core.jobs.ProjectChangeEventMessageProcessor;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.AppDynamicsService;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.CVNGYamlSchemaService;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.cvng.core.services.api.CustomHealthService;
import io.harness.cvng.core.services.api.DataCollectionInfoMapper;
import io.harness.cvng.core.services.api.DataCollectionSLIInfoMapper;
import io.harness.cvng.core.services.api.DataCollectionTaskManagementService;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.cvng.core.services.api.DataSourceConnectivityChecker;
import io.harness.cvng.core.services.api.DatadogService;
import io.harness.cvng.core.services.api.DeleteEntityByHandler;
import io.harness.cvng.core.services.api.DeletedCVConfigService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.HostRecordService;
import io.harness.cvng.core.services.api.LogRecordService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.NewRelicService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.core.services.api.PagerDutyService;
import io.harness.cvng.core.services.api.PrometheusService;
import io.harness.cvng.core.services.api.SetupUsageEventService;
import io.harness.cvng.core.services.api.SplunkService;
import io.harness.cvng.core.services.api.StackdriverService;
import io.harness.cvng.core.services.api.SumoLogicService;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.WebhookService;
import io.harness.cvng.core.services.api.demo.CVNGDemoDataIndexService;
import io.harness.cvng.core.services.api.demo.CVNGDemoPerpetualTaskService;
import io.harness.cvng.core.services.api.demo.ChangeSourceDemoDataGenerator;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.services.api.monitoredService.ServiceDependencyService;
import io.harness.cvng.core.services.impl.AppDynamicsDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.AppDynamicsServiceImpl;
import io.harness.cvng.core.services.impl.CVConfigServiceImpl;
import io.harness.cvng.core.services.impl.CVNGLogServiceImpl;
import io.harness.cvng.core.services.impl.CVNGYamlSchemaServiceImpl;
import io.harness.cvng.core.services.impl.ChangeEventServiceImpl;
import io.harness.cvng.core.services.impl.ChangeSourceUpdateHandler;
import io.harness.cvng.core.services.impl.CustomHealthServiceImpl;
import io.harness.cvng.core.services.impl.DataCollectionTaskServiceImpl;
import io.harness.cvng.core.services.impl.DatadogMetricDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.DatadogServiceImpl;
import io.harness.cvng.core.services.impl.DefaultDeleteEntityByHandler;
import io.harness.cvng.core.services.impl.DeletedCVConfigServiceImpl;
import io.harness.cvng.core.services.impl.FeatureFlagServiceImpl;
import io.harness.cvng.core.services.impl.HostRecordServiceImpl;
import io.harness.cvng.core.services.impl.KubernetesChangeSourceUpdateHandler;
import io.harness.cvng.core.services.impl.LogRecordServiceImpl;
import io.harness.cvng.core.services.impl.MetricPackServiceImpl;
import io.harness.cvng.core.services.impl.MonitoringSourcePerpetualTaskServiceImpl;
import io.harness.cvng.core.services.impl.NewRelicDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.NewRelicServiceImpl;
import io.harness.cvng.core.services.impl.OnboardingServiceImpl;
import io.harness.cvng.core.services.impl.PagerDutyServiceImpl;
import io.harness.cvng.core.services.impl.PagerdutyChangeSourceUpdateHandler;
import io.harness.cvng.core.services.impl.PrometheusDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.PrometheusServiceImpl;
import io.harness.cvng.core.services.impl.SLIDataCollectionTaskServiceImpl;
import io.harness.cvng.core.services.impl.ServiceGuardDataCollectionTaskServiceImpl;
import io.harness.cvng.core.services.impl.SetupUsageEventServiceImpl;
import io.harness.cvng.core.services.impl.SplunkDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.SplunkServiceImpl;
import io.harness.cvng.core.services.impl.StackdriverDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.StackdriverLogDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.StackdriverServiceImpl;
import io.harness.cvng.core.services.impl.SumoLogicServiceImpl;
import io.harness.cvng.core.services.impl.TimeSeriesRecordServiceImpl;
import io.harness.cvng.core.services.impl.VerificationTaskServiceImpl;
import io.harness.cvng.core.services.impl.WebhookServiceImpl;
import io.harness.cvng.core.services.impl.demo.CVNGDemoDataIndexServiceImpl;
import io.harness.cvng.core.services.impl.demo.CVNGDemoPerpetualTaskServiceImpl;
import io.harness.cvng.core.services.impl.demo.changesource.CDNGChangeSourceDemoDataGenerator;
import io.harness.cvng.core.services.impl.demo.changesource.PagerdutyChangeSourceDemoDataGenerator;
import io.harness.cvng.core.services.impl.monitoredService.ChangeSourceServiceImpl;
import io.harness.cvng.core.services.impl.monitoredService.DatadogLogDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.monitoredService.HealthSourceServiceImpl;
import io.harness.cvng.core.services.impl.monitoredService.MonitoredServiceServiceImpl;
import io.harness.cvng.core.services.impl.monitoredService.ServiceDependencyServiceImpl;
import io.harness.cvng.core.transformer.changeEvent.ChangeEventEntityAndDTOTransformer;
import io.harness.cvng.core.transformer.changeEvent.ChangeEventMetaDataTransformer;
import io.harness.cvng.core.transformer.changeEvent.HarnessCDChangeEventTransformer;
import io.harness.cvng.core.transformer.changeEvent.HarnessCDCurrentGenChangeEventTransformer;
import io.harness.cvng.core.transformer.changeEvent.KubernetesClusterChangeEventMetadataTransformer;
import io.harness.cvng.core.transformer.changeEvent.PagerDutyChangeEventTransformer;
import io.harness.cvng.core.transformer.changeSource.ChangeSourceEntityAndDTOTransformer;
import io.harness.cvng.core.transformer.changeSource.ChangeSourceSpecTransformer;
import io.harness.cvng.core.transformer.changeSource.HarnessCDChangeSourceSpecTransformer;
import io.harness.cvng.core.transformer.changeSource.HarnessCDCurrentGenChangeSourceSpecTransformer;
import io.harness.cvng.core.transformer.changeSource.KubernetesChangeSourceSpecTransformer;
import io.harness.cvng.core.transformer.changeSource.PagerDutyChangeSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.AppDynamicsHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.CVConfigToHealthSourceTransformer;
import io.harness.cvng.core.utils.monitoredService.DatadogLogHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.DatadogMetricHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.NewRelicHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.PrometheusHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.SplunkHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.StackdriverLogHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.StackdriverMetricHealthSourceSpecTransformer;
import io.harness.cvng.dashboard.services.api.HealthVerificationHeatMapService;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.cvng.dashboard.services.api.LogDashboardService;
import io.harness.cvng.dashboard.services.api.ServiceDependencyGraphService;
import io.harness.cvng.dashboard.services.api.TimeSeriesDashboardService;
import io.harness.cvng.dashboard.services.impl.HealthVerificationHeatMapServiceImpl;
import io.harness.cvng.dashboard.services.impl.HeatMapServiceImpl;
import io.harness.cvng.dashboard.services.impl.LogDashboardServiceImpl;
import io.harness.cvng.dashboard.services.impl.ServiceDependencyGraphServiceImpl;
import io.harness.cvng.dashboard.services.impl.TimeSeriesDashboardServiceImpl;
import io.harness.cvng.migration.impl.CVNGMigrationServiceImpl;
import io.harness.cvng.migration.service.CVNGMigrationService;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.entities.RatioServiceLevelIndicator.RatioServiceLevelIndicatorUpdatableEntity;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorUpdatableEntity;
import io.harness.cvng.servicelevelobjective.entities.ThresholdServiceLevelIndicator.ThresholdServiceLevelIndicatorUpdatableEntity;
import io.harness.cvng.servicelevelobjective.services.api.SLIAnalyserService;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataProcessorService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLODashboardService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.cvng.servicelevelobjective.services.api.UserJourneyService;
import io.harness.cvng.servicelevelobjective.services.impl.RatioAnalyserServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.SLIDataProcessorServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.SLIRecordServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.SLODashboardServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.ServiceLevelIndicatorServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.ServiceLevelObjectiveServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.ThresholdAnalyserServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.UserJourneyServiceImpl;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.CalenderSLOTargetTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.RatioServiceLevelIndicatorTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.RollingSLOTargetTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.SLOTargetTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorEntityAndDTOTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ThresholdServiceLevelIndicatorTransformer;
import io.harness.cvng.statemachine.beans.AnalysisState.StateType;
import io.harness.cvng.statemachine.services.api.ActivityVerificationStateExecutor;
import io.harness.cvng.statemachine.services.api.AnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.AnalysisStateMachineService;
import io.harness.cvng.statemachine.services.api.CanaryTimeSeriesAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.DeploymentLogAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.DeploymentLogClusterStateExecutor;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.cvng.statemachine.services.api.PreDeploymentLogClusterStateExecutor;
import io.harness.cvng.statemachine.services.api.SLIMetricAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.ServiceGuardLogAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.ServiceGuardLogClusterStateExecutor;
import io.harness.cvng.statemachine.services.api.ServiceGuardTimeSeriesAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.ServiceGuardTrendAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.TestTimeSeriesAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.impl.AnalysisStateMachineServiceImpl;
import io.harness.cvng.statemachine.services.impl.OrchestrationServiceImpl;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.cvng.verificationjob.services.api.VerificationJobService;
import io.harness.cvng.verificationjob.services.impl.VerificationJobInstanceServiceImpl;
import io.harness.cvng.verificationjob.services.impl.VerificationJobServiceImpl;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.lock.DistributedLockImplementation;
import io.harness.mongo.MongoPersistence;
import io.harness.packages.HarnessPackages;
import io.harness.persistence.HPersistence;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.redis.RedisConfig;
import io.harness.serializer.AnnotationAwareJsonSubtypeResolver;
import io.harness.serializer.CvNextGenRegistrars;
import io.harness.serializer.jackson.HarnessJacksonModule;
import io.harness.threading.ThreadPool;
import io.harness.waiter.AbstractWaiterModule;
import io.harness.waiter.AsyncWaitEngineImpl;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.waiter.WaiterConfiguration;
import io.harness.waiter.WaiterConfiguration.PersistenceLayer;
import io.harness.yaml.YamlSdkModule;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.dropwizard.jackson.Jackson;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

/**
 * Guice Module for initializing all beans.
 *
 * @author Raghu
 */
@Slf4j
@OwnedBy(HarnessTeam.CV)
public class CVServiceModule extends AbstractModule {
  private VerificationConfiguration verificationConfiguration;

  public CVServiceModule(VerificationConfiguration verificationConfiguration) {
    this.verificationConfiguration = verificationConfiguration;
  }

  /* (non-Javadoc)
   * @see com.google.inject.AbstractModule#configure()
   */
  @Override
  protected void configure() {
    install(YamlSdkModule.getInstance());
    install(new AbstractWaiterModule() {
      @Override
      public WaiterConfiguration waiterConfiguration() {
        return WaiterConfiguration.builder().persistenceLayer(PersistenceLayer.MORPHIA).build();
      }
    });
    bind(ExecutorService.class)
        .toInstance(ThreadPool.create(1, 20, 5, TimeUnit.SECONDS,
            new ThreadFactoryBuilder()
                .setNameFormat("default-cv-nextgen-executor-%d")
                .setPriority(Thread.MIN_PRIORITY)
                .setUncaughtExceptionHandler((t, e) -> log.error("error while processing task", e))
                .build()));
    install(PrimaryVersionManagerModule.getInstance());
    bind(HPersistence.class).to(MongoPersistence.class);
    bind(TimeSeriesRecordService.class).to(TimeSeriesRecordServiceImpl.class);
    bind(OrchestrationService.class).to(OrchestrationServiceImpl.class);
    bind(AnalysisStateMachineService.class).to(AnalysisStateMachineServiceImpl.class);
    bind(TimeSeriesAnalysisService.class).to(TimeSeriesAnalysisServiceImpl.class);
    bind(TrendAnalysisService.class).to(TrendAnalysisServiceImpl.class);
    bind(LearningEngineTaskService.class).to(LearningEngineTaskServiceImpl.class);
    bind(LogClusterService.class).to(LogClusterServiceImpl.class);
    bind(LogAnalysisService.class).to(LogAnalysisServiceImpl.class);
    bind(DataCollectionTaskService.class).to(DataCollectionTaskServiceImpl.class);
    MapBinder<Type, DataCollectionTaskManagementService> dataCollectionTaskServiceMapBinder =
        MapBinder.newMapBinder(binder(), Type.class, DataCollectionTaskManagementService.class);
    dataCollectionTaskServiceMapBinder.addBinding(Type.SERVICE_GUARD)
        .to(ServiceGuardDataCollectionTaskServiceImpl.class);
    dataCollectionTaskServiceMapBinder.addBinding(Type.DEPLOYMENT).to(ServiceGuardDataCollectionTaskServiceImpl.class);
    dataCollectionTaskServiceMapBinder.addBinding(Type.SLI).to(SLIDataCollectionTaskServiceImpl.class);
    bind(VerificationManagerService.class).to(VerificationManagerServiceImpl.class);
    bind(Clock.class).toInstance(Clock.systemUTC());
    bind(MetricPackService.class).to(MetricPackServiceImpl.class);
    bind(HeatMapService.class).to(HeatMapServiceImpl.class);
    bind(MetricPackService.class).to(MetricPackServiceImpl.class);
    bind(SplunkService.class).to(SplunkServiceImpl.class);
    bind(CVConfigService.class).to(CVConfigServiceImpl.class);
    bind(DeletedCVConfigService.class).to(DeletedCVConfigServiceImpl.class);
    MapBinder<DataSourceType, CVConfigToHealthSourceTransformer> dataSourceTypeToHealthSourceTransformerMapBinder =
        MapBinder.newMapBinder(binder(), DataSourceType.class, CVConfigToHealthSourceTransformer.class);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.APP_DYNAMICS)
        .to(AppDynamicsHealthSourceSpecTransformer.class);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.NEW_RELIC)
        .to(NewRelicHealthSourceSpecTransformer.class);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.STACKDRIVER_LOG)
        .to(StackdriverLogHealthSourceSpecTransformer.class);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.SPLUNK)
        .to(SplunkHealthSourceSpecTransformer.class);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.PROMETHEUS)
        .to(PrometheusHealthSourceSpecTransformer.class);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.STACKDRIVER)
        .to(StackdriverMetricHealthSourceSpecTransformer.class);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.DATADOG_METRICS)
        .to(DatadogMetricHealthSourceSpecTransformer.class);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.DATADOG_LOG)
        .to(DatadogLogHealthSourceSpecTransformer.class);
    MapBinder<DataSourceType, DataCollectionInfoMapper> dataSourceTypeDataCollectionInfoMapperMapBinder =
        MapBinder.newMapBinder(binder(), DataSourceType.class, DataCollectionInfoMapper.class);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.APP_DYNAMICS)
        .to(AppDynamicsDataCollectionInfoMapper.class);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.NEW_RELIC)
        .to(NewRelicDataCollectionInfoMapper.class);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.STACKDRIVER_LOG)
        .to(StackdriverLogDataCollectionInfoMapper.class);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.SPLUNK)
        .to(SplunkDataCollectionInfoMapper.class);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.PROMETHEUS)
        .to(PrometheusDataCollectionInfoMapper.class);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.STACKDRIVER)
        .to(StackdriverDataCollectionInfoMapper.class);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.DATADOG_METRICS)
        .to(DatadogMetricDataCollectionInfoMapper.class);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.DATADOG_LOG)
        .to(DatadogLogDataCollectionInfoMapper.class);

    MapBinder<DataSourceType, DataCollectionSLIInfoMapper> dataSourceTypeDataCollectionSLIInfoMapperMapBinder =
        MapBinder.newMapBinder(binder(), DataSourceType.class, DataCollectionSLIInfoMapper.class);
    dataSourceTypeDataCollectionSLIInfoMapperMapBinder.addBinding(DataSourceType.PROMETHEUS)
        .to(PrometheusDataCollectionInfoMapper.class);
    dataSourceTypeDataCollectionSLIInfoMapperMapBinder.addBinding(DataSourceType.APP_DYNAMICS)
        .to(AppDynamicsDataCollectionInfoMapper.class);
    dataSourceTypeDataCollectionSLIInfoMapperMapBinder.addBinding(DataSourceType.DATADOG_METRICS)
        .to(DatadogMetricDataCollectionInfoMapper.class);
    dataSourceTypeDataCollectionSLIInfoMapperMapBinder.addBinding(DataSourceType.STACKDRIVER_LOG)
        .to(StackdriverDataCollectionInfoMapper.class);

    bind(MetricPackService.class).to(MetricPackServiceImpl.class);
    bind(AppDynamicsService.class).to(AppDynamicsServiceImpl.class);
    bind(VerificationJobService.class).to(VerificationJobServiceImpl.class);
    bind(LogRecordService.class).to(LogRecordServiceImpl.class);
    bind(VerificationJobInstanceService.class).to(VerificationJobInstanceServiceImpl.class);
    bind(VerificationTaskService.class).to(VerificationTaskServiceImpl.class);
    bind(TimeSeriesDashboardService.class).to(TimeSeriesDashboardServiceImpl.class);
    bind(ActivityService.class).to(ActivityServiceImpl.class);
    bind(AlertRuleService.class).to(AlertRuleServiceImpl.class);
    bind(LogDashboardService.class).to(LogDashboardServiceImpl.class);
    bind(DeploymentTimeSeriesAnalysisService.class).to(DeploymentTimeSeriesAnalysisServiceImpl.class);
    bind(NextGenService.class).to(NextGenServiceImpl.class);
    bind(HostRecordService.class).to(HostRecordServiceImpl.class);
    bind(KubernetesActivitySourceService.class).to(KubernetesActivitySourceServiceImpl.class);
    bind(DeploymentLogAnalysisService.class).to(DeploymentLogAnalysisServiceImpl.class);
    bind(VerificationJobInstanceAnalysisService.class).to(VerificationJobInstanceAnalysisServiceImpl.class);
    bind(HealthVerificationService.class).to(HealthVerificationServiceImpl.class);
    bind(HealthVerificationHeatMapService.class).to(HealthVerificationHeatMapServiceImpl.class);
    bind(AnalysisService.class).to(AnalysisServiceImpl.class);
    bind(OnboardingService.class).to(OnboardingServiceImpl.class);
    bind(CVNGMigrationService.class).to(CVNGMigrationServiceImpl.class).in(Singleton.class);
    bind(TimeLimiter.class).toInstance(HTimeLimiter.create());
    bind(StackdriverService.class).to(StackdriverServiceImpl.class);
    bind(DatadogService.class).to(DatadogServiceImpl.class);
    bind(RedisConfig.class)
        .annotatedWith(Names.named("lock"))
        .toInstance(verificationConfiguration.getEventsFrameworkConfiguration().getRedisConfig());
    bind(ConsumerMessageProcessor.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.PROJECT_ENTITY))
        .to(ProjectChangeEventMessageProcessor.class);
    bind(ConsumerMessageProcessor.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY))
        .to(OrganizationChangeEventMessageProcessor.class);
    bind(ConsumerMessageProcessor.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.ACCOUNT_ENTITY))
        .to(AccountChangeEventMessageProcessor.class);
    bind(ConsumerMessageProcessor.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.CONNECTOR_ENTITY))
        .to(ConnectorChangeEventMessageProcessor.class);
    bind(AlertRuleAnomalyService.class).to(AlertRuleAnomalyServiceImpl.class);
    bind(NewRelicService.class).to(NewRelicServiceImpl.class);
    bind(VerifyStepDemoService.class).to(VerifyStepDemoServiceImpl.class);
    bind(String.class)
        .annotatedWith(Names.named("portalUrl"))
        .toInstance(verificationConfiguration.getPortalUrl().endsWith("/")
                ? verificationConfiguration.getPortalUrl()
                : verificationConfiguration.getPortalUrl() + "/");
    bind(CVNGLogService.class).to(CVNGLogServiceImpl.class);
    bind(DeleteEntityByHandler.class).to(DefaultDeleteEntityByHandler.class);
    bind(TimeSeriesAnomalousPatternsService.class).to(TimeSeriesAnomalousPatternsServiceImpl.class);

    bind(MonitoringSourcePerpetualTaskService.class).to(MonitoringSourcePerpetualTaskServiceImpl.class);
    MapBinder<DataSourceType, DataSourceConnectivityChecker> dataSourceTypeToServiceMapBinder =
        MapBinder.newMapBinder(binder(), DataSourceType.class, DataSourceConnectivityChecker.class);
    dataSourceTypeToServiceMapBinder.addBinding(DataSourceType.APP_DYNAMICS).to(AppDynamicsService.class);
    dataSourceTypeToServiceMapBinder.addBinding(DataSourceType.SPLUNK).to(SplunkService.class);
    dataSourceTypeToServiceMapBinder.addBinding(DataSourceType.STACKDRIVER).to(StackdriverService.class);
    dataSourceTypeToServiceMapBinder.addBinding(DataSourceType.DATADOG_METRICS).to(DatadogService.class);

    MapBinder<DataSourceType, CVConfigUpdatableEntity> dataSourceTypeCVConfigMapBinder =
        MapBinder.newMapBinder(binder(), DataSourceType.class, CVConfigUpdatableEntity.class);

    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.APP_DYNAMICS)
        .to(AppDynamicsCVConfigUpdatableEntity.class);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.NEW_RELIC).to(NewRelicCVConfigUpdatableEntity.class);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.PROMETHEUS).to(PrometheusUpdatableEntity.class);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.STACKDRIVER).to(StackDriverCVConfigUpdatableEntity.class);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.STACKDRIVER_LOG)
        .to(StackdriverLogCVConfigUpdatableEntity.class);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.SPLUNK).to(SplunkCVConfigUpdatableEntity.class);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.DATADOG_METRICS)
        .to(DatadogMetricCVConfigUpdatableEntity.class);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.DATADOG_LOG).to(DatadogLogCVConfigUpdatableEntity.class);

    MapBinder<SLIMetricType, ServiceLevelIndicatorUpdatableEntity> serviceLevelIndicatorMapBinder =
        MapBinder.newMapBinder(binder(), SLIMetricType.class, ServiceLevelIndicatorUpdatableEntity.class);
    serviceLevelIndicatorMapBinder.addBinding(SLIMetricType.RATIO).to(RatioServiceLevelIndicatorUpdatableEntity.class);
    serviceLevelIndicatorMapBinder.addBinding(SLIMetricType.THRESHOLD)
        .to(ThresholdServiceLevelIndicatorUpdatableEntity.class);
    bindDataSourceConnectivityChecker();
    // We have not used FeatureFlag module as it depends on stream and we don't have reliable way to tracking
    // if something goes wrong in feature flags stream
    // We are dependent on source of truth (Manager) for this.
    bind(FeatureFlagService.class).to(FeatureFlagServiceImpl.class);
    bind(CVNGStepTaskService.class).to(CVNGStepTaskServiceImpl.class);
    bind(PrometheusService.class).to(PrometheusServiceImpl.class);
    bind(CustomHealthService.class).to(CustomHealthServiceImpl.class);
    bind(CVNGYamlSchemaService.class).to(CVNGYamlSchemaServiceImpl.class);
    bind(SumoLogicService.class).to(SumoLogicServiceImpl.class);
    bind(HealthSourceService.class).to(HealthSourceServiceImpl.class);
    bind(MonitoredServiceService.class).to(MonitoredServiceServiceImpl.class);
    bind(ServiceDependencyService.class).to(ServiceDependencyServiceImpl.class);
    bind(ServiceDependencyGraphService.class).to(ServiceDependencyGraphServiceImpl.class);
    bind(SetupUsageEventService.class).to(SetupUsageEventServiceImpl.class);
    bind(CVNGStepService.class).to(CVNGStepServiceImpl.class);
    bind(PagerDutyService.class).to(PagerDutyServiceImpl.class);
    bind(WebhookService.class).to(WebhookServiceImpl.class);
    bind(CVNGDemoPerpetualTaskService.class).to(CVNGDemoPerpetualTaskServiceImpl.class);
    bind(CVNGDemoDataIndexService.class).to(CVNGDemoDataIndexServiceImpl.class);
    bindChangeSourceUpdatedEntity();
    bindChangeSourceDemoHandler();
    bind(ChangeSourceService.class).to(ChangeSourceServiceImpl.class);
    bind(ChangeSourceEntityAndDTOTransformer.class);
    bind(SLIRecordService.class).to(SLIRecordServiceImpl.class);
    bind(SLODashboardService.class).to(SLODashboardServiceImpl.class);
    bind(SLIDataProcessorService.class).to(SLIDataProcessorServiceImpl.class);
    MapBinder<ChangeSourceType, ChangeSourceSpecTransformer> changeSourceTypeChangeSourceSpecTransformerMapBinder =
        MapBinder.newMapBinder(binder(), ChangeSourceType.class, ChangeSourceSpecTransformer.class);
    changeSourceTypeChangeSourceSpecTransformerMapBinder.addBinding(ChangeSourceType.HARNESS_CD)
        .to(HarnessCDChangeSourceSpecTransformer.class);
    changeSourceTypeChangeSourceSpecTransformerMapBinder.addBinding(ChangeSourceType.PAGER_DUTY)
        .to(PagerDutyChangeSourceSpecTransformer.class);
    changeSourceTypeChangeSourceSpecTransformerMapBinder.addBinding(ChangeSourceType.KUBERNETES)
        .to(KubernetesChangeSourceSpecTransformer.class);
    changeSourceTypeChangeSourceSpecTransformerMapBinder.addBinding(ChangeSourceType.HARNESS_CD_CURRENT_GEN)
        .to(HarnessCDCurrentGenChangeSourceSpecTransformer.class);

    bindAnalysisStateExecutor();

    MapBinder<ActivityType, ActivityUpdatableEntity> activityTypeActivityUpdatableEntityMapBinder =
        MapBinder.newMapBinder(binder(), ActivityType.class, ActivityUpdatableEntity.class);
    activityTypeActivityUpdatableEntityMapBinder.addBinding(ActivityType.PAGER_DUTY)
        .to(PagerDutyActivityUpdatableEntity.class);
    activityTypeActivityUpdatableEntityMapBinder.addBinding(ActivityType.DEPLOYMENT)
        .to(DeploymentActivityUpdatableEntity.class);
    activityTypeActivityUpdatableEntityMapBinder.addBinding(ActivityType.KUBERNETES)
        .to(KubernetesClusterActivityUpdatableEntity.class);
    activityTypeActivityUpdatableEntityMapBinder.addBinding(ActivityType.HARNESS_CD_CURRENT_GEN)
        .to(HarnessCDCurrentGenActivityUpdatableEntity.class);

    MapBinder<ChangeSourceType, ChangeSourceUpdateHandler> changeSourceUpdateHandlerMapBinder =
        MapBinder.newMapBinder(binder(), ChangeSourceType.class, ChangeSourceUpdateHandler.class);
    changeSourceUpdateHandlerMapBinder.addBinding(ChangeSourceType.PAGER_DUTY)
        .to(PagerdutyChangeSourceUpdateHandler.class);
    changeSourceUpdateHandlerMapBinder.addBinding(ChangeSourceType.KUBERNETES)
        .to(KubernetesChangeSourceUpdateHandler.class);

    MapBinder<ActivityType, ActivityUpdateHandler> activityUpdateHandlerMapBinder =
        MapBinder.newMapBinder(binder(), ActivityType.class, ActivityUpdateHandler.class);
    activityUpdateHandlerMapBinder.addBinding(ActivityType.KUBERNETES).to(KubernetesClusterActivityUpdateHandler.class);
    activityUpdateHandlerMapBinder.addBinding(ActivityType.DEPLOYMENT).to(DeploymentActivityUpdateHandler.class);

    MapBinder<SLOTargetType, SLOTargetTransformer> sloTargetTypeSLOTargetTransformerMapBinder =
        MapBinder.newMapBinder(binder(), SLOTargetType.class, SLOTargetTransformer.class);
    sloTargetTypeSLOTargetTransformerMapBinder.addBinding(SLOTargetType.CALENDER)
        .to(CalenderSLOTargetTransformer.class);
    sloTargetTypeSLOTargetTransformerMapBinder.addBinding(SLOTargetType.ROLLING).to(RollingSLOTargetTransformer.class);
    bind(ChangeEventService.class).to(ChangeEventServiceImpl.class);
    bind(ChangeEventEntityAndDTOTransformer.class);

    bind(ServiceLevelObjectiveService.class).to(ServiceLevelObjectiveServiceImpl.class);
    bind(UserJourneyService.class).to(UserJourneyServiceImpl.class);
    bind(ServiceLevelIndicatorService.class).to(ServiceLevelIndicatorServiceImpl.class);
    bind(SLIDataProcessorService.class).to(SLIDataProcessorServiceImpl.class);
    bind(ServiceLevelIndicatorEntityAndDTOTransformer.class);
    MapBinder<SLIMetricType, ServiceLevelIndicatorTransformer> serviceLevelIndicatorTransformerMapBinder =
        MapBinder.newMapBinder(binder(), SLIMetricType.class, ServiceLevelIndicatorTransformer.class);
    serviceLevelIndicatorTransformerMapBinder.addBinding(SLIMetricType.RATIO)
        .to(RatioServiceLevelIndicatorTransformer.class)
        .in(Scopes.SINGLETON);
    serviceLevelIndicatorTransformerMapBinder.addBinding(SLIMetricType.THRESHOLD)
        .to(ThresholdServiceLevelIndicatorTransformer.class)
        .in(Scopes.SINGLETON);

    bind(LearningEngineDevService.class).to(LearningEngineDevServiceImpl.class);
    MapBinder<ChangeSourceType, ChangeEventMetaDataTransformer> changeTypeMetaDataTransformerMapBinder =
        MapBinder.newMapBinder(binder(), ChangeSourceType.class, ChangeEventMetaDataTransformer.class);
    changeTypeMetaDataTransformerMapBinder.addBinding(HARNESS_CD)
        .to(HarnessCDChangeEventTransformer.class)
        .in(Scopes.SINGLETON);
    changeTypeMetaDataTransformerMapBinder.addBinding(ChangeSourceType.KUBERNETES)
        .to(KubernetesClusterChangeEventMetadataTransformer.class)
        .in(Scopes.SINGLETON);
    changeTypeMetaDataTransformerMapBinder.addBinding(ChangeSourceType.PAGER_DUTY)
        .to(PagerDutyChangeEventTransformer.class)
        .in(Scopes.SINGLETON);
    changeTypeMetaDataTransformerMapBinder.addBinding(ChangeSourceType.HARNESS_CD_CURRENT_GEN)
        .to(HarnessCDCurrentGenChangeEventTransformer.class)
        .in(Scopes.SINGLETON);

    MapBinder<SLIMetricType, SLIAnalyserService> sliAnalyserServiceMapBinder =
        MapBinder.newMapBinder(binder(), SLIMetricType.class, SLIAnalyserService.class);
    sliAnalyserServiceMapBinder.addBinding(SLIMetricType.RATIO).to(RatioAnalyserServiceImpl.class);
    sliAnalyserServiceMapBinder.addBinding(SLIMetricType.THRESHOLD).to(ThresholdAnalyserServiceImpl.class);
  }

  private void bindChangeSourceUpdatedEntity() {
    MapBinder<ChangeSourceType, ChangeSource.UpdatableChangeSourceEntity> changeTypeSourceMapBinder =
        MapBinder.newMapBinder(binder(), ChangeSourceType.class, ChangeSource.UpdatableChangeSourceEntity.class);
    changeTypeSourceMapBinder.addBinding(HARNESS_CD).to(HarnessCDChangeSource.UpdatableCDNGChangeSourceEntity.class);
    changeTypeSourceMapBinder.addBinding(ChangeSourceType.PAGER_DUTY)
        .to(PagerDutyChangeSource.UpdatablePagerDutyChangeSourceEntity.class);
    changeTypeSourceMapBinder.addBinding(ChangeSourceType.KUBERNETES)
        .to(KubernetesChangeSource.UpdatableKubernetesChangeSourceEntity.class);
    changeTypeSourceMapBinder.addBinding(ChangeSourceType.HARNESS_CD_CURRENT_GEN)
        .to(HarnessCDCurrentGenChangeSource.UpdatableHarnessCDCurrentGenChangeSourceEntity.class);
  }

  private void bindChangeSourceDemoHandler() {
    MapBinder<ChangeSourceType, ChangeSourceDemoDataGenerator> changeTypeSourceMapBinder =
        MapBinder.newMapBinder(binder(), ChangeSourceType.class, ChangeSourceDemoDataGenerator.class);
    changeTypeSourceMapBinder.addBinding(ChangeSourceType.PAGER_DUTY).to(PagerdutyChangeSourceDemoDataGenerator.class);
    changeTypeSourceMapBinder.addBinding(HARNESS_CD).to(CDNGChangeSourceDemoDataGenerator.class);
    /*changeTypeSourceMapBinder.addBinding(ChangeSourceType.KUBERNETES)
        .to(KubernetesChangeSource.UpdatableKubernetesChangeSourceEntity.class);
    changeTypeSourceMapBinder.addBinding(ChangeSourceType.HARNESS_CD_CURRENT_GEN)
        .to(HarnessCDCurrentGenChangeSource.UpdatableHarnessCDCurrentGenChangeSourceEntity.class);*/
  }

  private void bindAnalysisStateExecutor() {
    MapBinder<StateType, AnalysisStateExecutor> stateTypeAnalysisStateExecutorMap =
        MapBinder.newMapBinder(binder(), StateType.class, AnalysisStateExecutor.class);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.ACTIVITY_VERIFICATION)
        .to(ActivityVerificationStateExecutor.class);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.CANARY_TIME_SERIES)
        .to(CanaryTimeSeriesAnalysisStateExecutor.class);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.DEPLOYMENT_LOG_ANALYSIS)
        .to(DeploymentLogAnalysisStateExecutor.class);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.SERVICE_GUARD_LOG_ANALYSIS)
        .to(ServiceGuardLogAnalysisStateExecutor.class);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.SERVICE_GUARD_TIME_SERIES)
        .to(ServiceGuardTimeSeriesAnalysisStateExecutor.class);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.TEST_TIME_SERIES)
        .to(TestTimeSeriesAnalysisStateExecutor.class);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.DEPLOYMENT_LOG_CLUSTER)
        .to(DeploymentLogClusterStateExecutor.class);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.PRE_DEPLOYMENT_LOG_CLUSTER)
        .to(PreDeploymentLogClusterStateExecutor.class);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.SERVICE_GUARD_LOG_CLUSTER)
        .to(ServiceGuardLogClusterStateExecutor.class);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.SERVICE_GUARD_TREND_ANALYSIS)
        .to(ServiceGuardTrendAnalysisStateExecutor.class);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.SLI_METRIC_ANALYSIS)
        .to(SLIMetricAnalysisStateExecutor.class);
  }

  private void bindDataSourceConnectivityChecker() {
    MapBinder<DataSourceType, DataSourceConnectivityChecker> dataSourceTypeToServiceMapBinder =
        MapBinder.newMapBinder(binder(), DataSourceType.class, DataSourceConnectivityChecker.class);
    dataSourceTypeToServiceMapBinder.addBinding(DataSourceType.APP_DYNAMICS).to(AppDynamicsService.class);
    dataSourceTypeToServiceMapBinder.addBinding(DataSourceType.SPLUNK).to(SplunkService.class);
    dataSourceTypeToServiceMapBinder.addBinding(DataSourceType.STACKDRIVER).to(StackdriverService.class);
    dataSourceTypeToServiceMapBinder.addBinding(DataSourceType.DATADOG_METRICS).to(DatadogService.class);

    MapBinder<DataSourceType, CVConfigUpdatableEntity> dataSourceTypeCVConfigMapBinder =
        MapBinder.newMapBinder(binder(), DataSourceType.class, CVConfigUpdatableEntity.class);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.APP_DYNAMICS)
        .to(AppDynamicsCVConfigUpdatableEntity.class);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.NEW_RELIC).to(NewRelicCVConfigUpdatableEntity.class);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.PROMETHEUS).to(PrometheusUpdatableEntity.class);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.STACKDRIVER).to(StackDriverCVConfigUpdatableEntity.class);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.STACKDRIVER_LOG)
        .to(StackdriverLogCVConfigUpdatableEntity.class);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.SPLUNK).to(SplunkCVConfigUpdatableEntity.class);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.DATADOG_METRICS)
        .to(DatadogMetricCVConfigUpdatableEntity.class);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.DATADOG_LOG).to(DatadogLogCVConfigUpdatableEntity.class);
  }

  @Provides
  @Singleton
  public AsyncWaitEngine asyncWaitEngine(WaitNotifyEngine waitNotifyEngine) {
    return new AsyncWaitEngineImpl(waitNotifyEngine, CVNG_ORCHESTRATION);
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return verificationConfiguration.getDistributedLockImplementation();
  }

  @Provides
  @Singleton
  @Named("cvngParallelExecutor")
  public ExecutorService cvngParallelExecutor() {
    ExecutorService cvngParallelExecutor = ThreadPool.create(4, CVNextGenConstants.CVNG_MAX_PARALLEL_THREADS, 5,
        TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("cvngParallelExecutor-%d").setPriority(Thread.MIN_PRIORITY).build());
    Runtime.getRuntime().addShutdownHook(new Thread(() -> cvngParallelExecutor.shutdownNow()));
    return cvngParallelExecutor;
  }

  @Provides
  @Named("yaml-schema-mapper")
  @Singleton
  public ObjectMapper getYamlSchemaObjectMapper() {
    ObjectMapper objectMapper = Jackson.newObjectMapper();
    configureYAMLSchemaObjectMapper(objectMapper);
    return objectMapper;
  }

  @Provides
  @Named("yaml-schema-subtypes")
  @Singleton
  public Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes() {
    Reflections reflections = new Reflections(HarnessPackages.IO_HARNESS);

    Set<Class<? extends StepSpecType>> subTypesOfStepSpecType = reflections.getSubTypesOf(StepSpecType.class);
    Set<Class<?>> set = new HashSet<>(subTypesOfStepSpecType);

    return ImmutableMap.of(StepSpecType.class, set);
  }

  @Provides
  @Singleton
  List<YamlSchemaRootClass> yamlSchemaRootClasses() {
    return ImmutableList.<YamlSchemaRootClass>builder().addAll(CvNextGenRegistrars.yamlSchemaRegistrars).build();
  }

  private void configureYAMLSchemaObjectMapper(final ObjectMapper mapper) {
    final AnnotationAwareJsonSubtypeResolver subtypeResolver =
        AnnotationAwareJsonSubtypeResolver.newInstance(mapper.getSubtypeResolver());
    mapper.setSubtypeResolver(subtypeResolver);
    mapper.setConfig(mapper.getSerializationConfig().withView(JsonViews.Public.class));
    mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
      @Override
      public List<NamedType> findSubtypes(Annotated a) {
        final List<NamedType> subtypesFromSuper = super.findSubtypes(a);
        if (isNotEmpty(subtypesFromSuper)) {
          return subtypesFromSuper;
        }
        return emptyIfNull(subtypeResolver.findSubtypes(a));
      }
    });
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new GuavaModule());
    mapper.registerModule(new JavaTimeModule());
    mapper.registerModule(new HarnessJacksonModule());
  }
}
