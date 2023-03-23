/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import static io.harness.authorization.AuthorizationServiceHeader.CV_NEXT_GEN;
import static io.harness.cvng.beans.change.ChangeSourceType.HARNESS_CD;
import static io.harness.cvng.cdng.services.impl.CVNGNotifyEventListener.CVNG_ORCHESTRATION;
import static io.harness.eventsframework.EventsFrameworkConstants.SRM_STATEMACHINE_EVENT;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_OUTBOX_POLL_CONFIGURATION;

import io.harness.account.AccountClientModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.retry.MethodExecutionHelper;
import io.harness.annotations.retry.RetryOnException;
import io.harness.annotations.retry.RetryOnExceptionInterceptor;
import io.harness.app.PrimaryVersionManagerModule;
import io.harness.audit.ResourceTypeConstants;
import io.harness.audit.client.remote.AuditClientModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.concurrent.HTimeLimiter;
import io.harness.cvng.activity.entities.Activity.ActivityUpdatableEntity;
import io.harness.cvng.activity.entities.CustomChangeActivity.CustomChangeActivityUpdatableEntity;
import io.harness.cvng.activity.entities.DeploymentActivity.DeploymentActivityUpdatableEntity;
import io.harness.cvng.activity.entities.HarnessCDCurrentGenActivity.HarnessCDCurrentGenActivityUpdatableEntity;
import io.harness.cvng.activity.entities.InternalChangeActivity.InternalChangeActivityUpdatableEntity;
import io.harness.cvng.activity.entities.KubernetesClusterActivity.KubernetesClusterActivityUpdatableEntity;
import io.harness.cvng.activity.entities.PagerDutyActivity.PagerDutyActivityUpdatableEntity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.activity.services.api.ActivityUpdateHandler;
import io.harness.cvng.activity.services.impl.ActivityServiceImpl;
import io.harness.cvng.activity.services.impl.CustomChangeActivityUpdateHandler;
import io.harness.cvng.activity.services.impl.DeploymentActivityUpdateHandler;
import io.harness.cvng.activity.services.impl.InternalChangeActivityUpdateHandler;
import io.harness.cvng.activity.services.impl.KubernetesClusterActivityUpdateHandler;
import io.harness.cvng.activity.source.services.api.KubernetesActivitySourceService;
import io.harness.cvng.activity.source.services.impl.KubernetesActivitySourceServiceImpl;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.analysis.services.api.LearningEngineDevService;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
import io.harness.cvng.analysis.services.api.LogClusterService;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.analysis.services.api.TimeSeriesAnomalousPatternsService;
import io.harness.cvng.analysis.services.api.TrendAnalysisService;
import io.harness.cvng.analysis.services.api.VerificationJobInstanceAnalysisService;
import io.harness.cvng.analysis.services.impl.DeploymentLogAnalysisServiceImpl;
import io.harness.cvng.analysis.services.impl.DeploymentTimeSeriesAnalysisServiceImpl;
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
import io.harness.cvng.cdng.beans.MonitoredServiceSpec.MonitoredServiceSpecType;
import io.harness.cvng.cdng.resources.VerifyStepResourceImpl;
import io.harness.cvng.cdng.services.api.CDStageMetaDataService;
import io.harness.cvng.cdng.services.api.CVNGStepService;
import io.harness.cvng.cdng.services.api.CVNGStepTaskService;
import io.harness.cvng.cdng.services.api.VerifyStepDemoService;
import io.harness.cvng.cdng.services.api.VerifyStepMonitoredServiceResolutionService;
import io.harness.cvng.cdng.services.impl.CDStageMetaDataServiceImpl;
import io.harness.cvng.cdng.services.impl.CVNGStepServiceImpl;
import io.harness.cvng.cdng.services.impl.CVNGStepTaskServiceImpl;
import io.harness.cvng.cdng.services.impl.ConfiguredVerifyStepMonitoredServiceResolutionServiceImpl;
import io.harness.cvng.cdng.services.impl.DefaultVerifyStepMonitoredServiceResolutionServiceImpl;
import io.harness.cvng.cdng.services.impl.TemplateVerifyStepMonitoredServiceResolutionServiceImpl;
import io.harness.cvng.cdng.services.impl.VerifyStepDemoServiceImpl;
import io.harness.cvng.client.ErrorTrackingService;
import io.harness.cvng.client.ErrorTrackingServiceImpl;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.client.NextGenServiceImpl;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.client.VerificationManagerServiceImpl;
import io.harness.cvng.core.entities.AppDynamicsCVConfig.AppDynamicsCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.AwsPrometheusCVConfig.AwsPrometheusUpdatableEntity;
import io.harness.cvng.core.entities.CVConfig.CVConfigUpdatableEntity;
import io.harness.cvng.core.entities.CloudWatchMetricCVConfig.CloudWatchMetricCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.CustomHealthLogCVConfig.CustomHealthLogCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.CustomHealthMetricCVConfig.CustomHealthMetricCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.DataCollectionTask.Type;
import io.harness.cvng.core.entities.DatadogLogCVConfig.DatadogLogCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.DatadogMetricCVConfig.DatadogMetricCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.DynatraceCVConfig.DynatraceCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.ELKCVConfig.ELKCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.ErrorTrackingCVConfig.ErrorTrackingCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.NewRelicCVConfig.NewRelicCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.PrometheusCVConfig.PrometheusUpdatableEntity;
import io.harness.cvng.core.entities.SideKick;
import io.harness.cvng.core.entities.SplunkCVConfig.SplunkCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.SplunkMetricCVConfig.SplunkMetricUpdatableEntity;
import io.harness.cvng.core.entities.StackdriverCVConfig.StackDriverCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.StackdriverLogCVConfig.StackdriverLogCVConfigUpdatableEntity;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.entities.changeSource.CustomChangeSource;
import io.harness.cvng.core.entities.changeSource.HarnessCDChangeSource;
import io.harness.cvng.core.entities.changeSource.HarnessCDCurrentGenChangeSource;
import io.harness.cvng.core.entities.changeSource.KubernetesChangeSource;
import io.harness.cvng.core.entities.changeSource.PagerDutyChangeSource;
import io.harness.cvng.core.handler.monitoredService.BaseMonitoredServiceHandler;
import io.harness.cvng.core.handler.monitoredService.MonitoredServiceSLIMetricUpdateHandler;
import io.harness.cvng.core.jobs.AccountChangeEventMessageProcessor;
import io.harness.cvng.core.jobs.ConnectorChangeEventMessageProcessor;
import io.harness.cvng.core.jobs.ConsumerMessageProcessor;
import io.harness.cvng.core.jobs.CustomChangeEventPublisherService;
import io.harness.cvng.core.jobs.CustomChangeEventPublisherServiceImpl;
import io.harness.cvng.core.jobs.FakeFeatureFlagSRMProducer;
import io.harness.cvng.core.jobs.OrganizationChangeEventMessageProcessor;
import io.harness.cvng.core.jobs.ProjectChangeEventMessageProcessor;
import io.harness.cvng.core.jobs.StateMachineEventPublisherService;
import io.harness.cvng.core.jobs.StateMachineEventPublisherServiceImpl;
import io.harness.cvng.core.jobs.StateMachineMessageProcessor;
import io.harness.cvng.core.jobs.StateMachineMessageProcessorImpl;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.DebugConfigService;
import io.harness.cvng.core.services.DeeplinkURLService;
import io.harness.cvng.core.services.api.AppDynamicsService;
import io.harness.cvng.core.services.api.AwsService;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.CVNGYamlSchemaService;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.cvng.core.services.api.CloudWatchService;
import io.harness.cvng.core.services.api.CustomHealthService;
import io.harness.cvng.core.services.api.DataCollectionInfoMapper;
import io.harness.cvng.core.services.api.DataCollectionSLIInfoMapper;
import io.harness.cvng.core.services.api.DataCollectionTaskManagementService;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.cvng.core.services.api.DataSourceConnectivityChecker;
import io.harness.cvng.core.services.api.DatadogService;
import io.harness.cvng.core.services.api.DebugService;
import io.harness.cvng.core.services.api.DeleteEntityByHandler;
import io.harness.cvng.core.services.api.DynatraceService;
import io.harness.cvng.core.services.api.ELKService;
import io.harness.cvng.core.services.api.EntityDisabledTimeService;
import io.harness.cvng.core.services.api.ExecutionLogService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.HealthSourceOnboardingService;
import io.harness.cvng.core.services.api.HostRecordService;
import io.harness.cvng.core.services.api.InternalChangeConsumerService;
import io.harness.cvng.core.services.api.LogFeedbackService;
import io.harness.cvng.core.services.api.LogRecordService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.NewRelicService;
import io.harness.cvng.core.services.api.NextGenHealthSourceHelper;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.core.services.api.PagerDutyService;
import io.harness.cvng.core.services.api.ParseSampleDataService;
import io.harness.cvng.core.services.api.PrometheusService;
import io.harness.cvng.core.services.api.RiskCategoryService;
import io.harness.cvng.core.services.api.SetupUsageEventService;
import io.harness.cvng.core.services.api.SideKickExecutor;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.cvng.core.services.api.SplunkService;
import io.harness.cvng.core.services.api.StackdriverService;
import io.harness.cvng.core.services.api.SumoLogicService;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.TimeSeriesThresholdService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.WebhookConfigService;
import io.harness.cvng.core.services.api.WebhookService;
import io.harness.cvng.core.services.api.demo.CVNGDemoDataIndexService;
import io.harness.cvng.core.services.api.demo.CVNGDemoPerpetualTaskService;
import io.harness.cvng.core.services.api.demo.ChangeSourceDemoDataGenerator;
import io.harness.cvng.core.services.api.demo.ChiDemoService;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.services.api.monitoredService.ServiceDependencyService;
import io.harness.cvng.core.services.impl.AppDynamicsDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.AppDynamicsServiceImpl;
import io.harness.cvng.core.services.impl.AwsPrometheusDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.AwsServiceImpl;
import io.harness.cvng.core.services.impl.CVConfigServiceImpl;
import io.harness.cvng.core.services.impl.CVNGLogServiceImpl;
import io.harness.cvng.core.services.impl.CVNGYamlSchemaServiceImpl;
import io.harness.cvng.core.services.impl.ChangeEventServiceImpl;
import io.harness.cvng.core.services.impl.ChangeSourceUpdateHandler;
import io.harness.cvng.core.services.impl.CloudWatchMetricDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.CloudWatchServiceImpl;
import io.harness.cvng.core.services.impl.CustomHealthLogDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.CustomHealthMetricDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.CustomHealthServiceImpl;
import io.harness.cvng.core.services.impl.DataCollectionTaskServiceImpl;
import io.harness.cvng.core.services.impl.DatadogMetricDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.DatadogServiceImpl;
import io.harness.cvng.core.services.impl.DebugServiceImpl;
import io.harness.cvng.core.services.impl.DeeplinkURLServiceImpl;
import io.harness.cvng.core.services.impl.DefaultDeleteEntityByHandler;
import io.harness.cvng.core.services.impl.DynatraceDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.DynatraceServiceImpl;
import io.harness.cvng.core.services.impl.ELKDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.ELKServiceImpl;
import io.harness.cvng.core.services.impl.ElasticSearchLogNextGenHealthSourceHelper;
import io.harness.cvng.core.services.impl.EntityDisabledTimeServiceImpl;
import io.harness.cvng.core.services.impl.ErrorTrackingDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.ExecutionLogServiceImpl;
import io.harness.cvng.core.services.impl.FeatureFlagServiceImpl;
import io.harness.cvng.core.services.impl.HealthSourceOnboardingServiceImpl;
import io.harness.cvng.core.services.impl.HostRecordServiceImpl;
import io.harness.cvng.core.services.impl.InternalChangeConsumerServiceImpl;
import io.harness.cvng.core.services.impl.KubernetesChangeSourceUpdateHandler;
import io.harness.cvng.core.services.impl.LogFeedbackServiceImpl;
import io.harness.cvng.core.services.impl.LogRecordServiceImpl;
import io.harness.cvng.core.services.impl.MetricPackServiceImpl;
import io.harness.cvng.core.services.impl.MonitoringSourcePerpetualTaskServiceImpl;
import io.harness.cvng.core.services.impl.NewRelicDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.NewRelicServiceImpl;
import io.harness.cvng.core.services.impl.OnboardingServiceImpl;
import io.harness.cvng.core.services.impl.PagerDutyServiceImpl;
import io.harness.cvng.core.services.impl.PagerdutyChangeSourceUpdateHandler;
import io.harness.cvng.core.services.impl.ParseSampleDataServiceImpl;
import io.harness.cvng.core.services.impl.PrometheusDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.PrometheusServiceImpl;
import io.harness.cvng.core.services.impl.SLIDataCollectionTaskServiceImpl;
import io.harness.cvng.core.services.impl.ServiceGuardDataCollectionTaskServiceImpl;
import io.harness.cvng.core.services.impl.SetupUsageEventServiceImpl;
import io.harness.cvng.core.services.impl.SideKickServiceImpl;
import io.harness.cvng.core.services.impl.SplunkDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.SplunkMetricDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.SplunkServiceImpl;
import io.harness.cvng.core.services.impl.StackdriverDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.StackdriverLogDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.StackdriverServiceImpl;
import io.harness.cvng.core.services.impl.SumoLogicServiceImpl;
import io.harness.cvng.core.services.impl.SumologicLogDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.SumologicLogNextGenHealthSourceHelper;
import io.harness.cvng.core.services.impl.SumologicMetricDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.SumologicMetricNextGenHealthSourceHelper;
import io.harness.cvng.core.services.impl.TimeSeriesRecordServiceImpl;
import io.harness.cvng.core.services.impl.TimeSeriesThresholdServiceImpl;
import io.harness.cvng.core.services.impl.VerificationTaskServiceImpl;
import io.harness.cvng.core.services.impl.WebhookServiceImpl;
import io.harness.cvng.core.services.impl.demo.CVNGDemoDataIndexServiceImpl;
import io.harness.cvng.core.services.impl.demo.CVNGDemoPerpetualTaskServiceImpl;
import io.harness.cvng.core.services.impl.demo.ChiDemoServiceImpl;
import io.harness.cvng.core.services.impl.demo.changesource.CDNGChangeSourceDemoDataGenerator;
import io.harness.cvng.core.services.impl.demo.changesource.KubernetesChangeSourceDemoDataGenerator;
import io.harness.cvng.core.services.impl.demo.changesource.PagerdutyChangeSourceDemoDataGenerator;
import io.harness.cvng.core.services.impl.monitoredService.ChangeSourceServiceImpl;
import io.harness.cvng.core.services.impl.monitoredService.DatadogLogDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.monitoredService.HealthSourceServiceImpl;
import io.harness.cvng.core.services.impl.monitoredService.MonitoredServiceServiceImpl;
import io.harness.cvng.core.services.impl.monitoredService.RiskCategoryServiceImpl;
import io.harness.cvng.core.services.impl.monitoredService.ServiceDependencyServiceImpl;
import io.harness.cvng.core.services.impl.sidekickexecutors.CompositeSLORecordsCleanupSideKickExecutor;
import io.harness.cvng.core.services.impl.sidekickexecutors.DemoActivitySideKickExecutor;
import io.harness.cvng.core.services.impl.sidekickexecutors.RetryChangeSourceHandleDeleteSideKickExecutor;
import io.harness.cvng.core.services.impl.sidekickexecutors.VerificationJobInstanceCleanupSideKickExecutor;
import io.harness.cvng.core.services.impl.sidekickexecutors.VerificationTaskCleanupSideKickExecutor;
import io.harness.cvng.core.transformer.changeEvent.ChangeEventEntityAndDTOTransformer;
import io.harness.cvng.core.transformer.changeEvent.ChangeEventMetaDataTransformer;
import io.harness.cvng.core.transformer.changeEvent.CustomChangeEventTransformer;
import io.harness.cvng.core.transformer.changeEvent.HarnessCDChangeEventTransformer;
import io.harness.cvng.core.transformer.changeEvent.HarnessCDCurrentGenChangeEventTransformer;
import io.harness.cvng.core.transformer.changeEvent.InternalChangeEventTransformer;
import io.harness.cvng.core.transformer.changeEvent.KubernetesClusterChangeEventMetadataTransformer;
import io.harness.cvng.core.transformer.changeEvent.PagerDutyChangeEventTransformer;
import io.harness.cvng.core.transformer.changeSource.ChangeSourceEntityAndDTOTransformer;
import io.harness.cvng.core.transformer.changeSource.ChangeSourceSpecTransformer;
import io.harness.cvng.core.transformer.changeSource.CustomChangeSourceSpecTransformer;
import io.harness.cvng.core.transformer.changeSource.HarnessCDChangeSourceSpecTransformer;
import io.harness.cvng.core.transformer.changeSource.HarnessCDCurrentGenChangeSourceSpecTransformer;
import io.harness.cvng.core.transformer.changeSource.KubernetesChangeSourceSpecTransformer;
import io.harness.cvng.core.transformer.changeSource.PagerDutyChangeSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.AppDynamicsHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.AwsPrometheusHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.CVConfigToHealthSourceTransformer;
import io.harness.cvng.core.utils.monitoredService.CloudWatchMetricHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.CustomHealthSourceSpecLogTransformer;
import io.harness.cvng.core.utils.monitoredService.CustomHealthSourceSpecMetricTransformer;
import io.harness.cvng.core.utils.monitoredService.DatadogLogHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.DatadogMetricHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.DynatraceHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.ELKHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.ErrorTrackingHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.NewRelicHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.PrometheusHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.SplunkHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.SplunkMetricHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.StackdriverLogHealthSourceSpecTransformer;
import io.harness.cvng.core.utils.monitoredService.StackdriverMetricHealthSourceSpecTransformer;
import io.harness.cvng.dashboard.services.api.ErrorTrackingDashboardService;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.cvng.dashboard.services.api.LogDashboardService;
import io.harness.cvng.dashboard.services.api.ServiceDependencyGraphService;
import io.harness.cvng.dashboard.services.api.TimeSeriesDashboardService;
import io.harness.cvng.dashboard.services.impl.ErrorTrackingDashboardServiceImpl;
import io.harness.cvng.dashboard.services.impl.HeatMapServiceImpl;
import io.harness.cvng.dashboard.services.impl.LogDashboardServiceImpl;
import io.harness.cvng.dashboard.services.impl.ServiceDependencyGraphServiceImpl;
import io.harness.cvng.dashboard.services.impl.TimeSeriesDashboardServiceImpl;
import io.harness.cvng.downtime.beans.DowntimeType;
import io.harness.cvng.downtime.services.api.DowntimeService;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.downtime.services.impl.DowntimeServiceImpl;
import io.harness.cvng.downtime.services.impl.EntityUnavailabilityStatusesServiceImpl;
import io.harness.cvng.downtime.transformer.DowntimeSpecDetailsTransformer;
import io.harness.cvng.downtime.transformer.EntityUnavailabilityStatusesEntityAndDTOTransformer;
import io.harness.cvng.downtime.transformer.OnetimeDowntimeSpecDetailsTransformer;
import io.harness.cvng.downtime.transformer.RecurringDowntimeSpecDetailsTransformer;
import io.harness.cvng.migration.impl.CVNGMigrationServiceImpl;
import io.harness.cvng.migration.service.CVNGMigrationService;
import io.harness.cvng.notification.beans.NotificationRuleConditionType;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceNotificationRuleUpdatableEntity;
import io.harness.cvng.notification.entities.NotificationRule.NotificationRuleUpdatableEntity;
import io.harness.cvng.notification.entities.SLONotificationRule.SLONotificationRuleUpdatableEntity;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;
import io.harness.cvng.notification.services.impl.BurnRateTemplateDataGenerator;
import io.harness.cvng.notification.services.impl.ChangeImpactTemplateDataGenerator;
import io.harness.cvng.notification.services.impl.ChangeObservedTemplateDataGenerator;
import io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator;
import io.harness.cvng.notification.services.impl.HealthScoreTemplateDataGenerator;
import io.harness.cvng.notification.services.impl.NotificationRuleServiceImpl;
import io.harness.cvng.notification.services.impl.RemainingMinutesTemplateDataGenerator;
import io.harness.cvng.notification.services.impl.RemainingPercentageTemplateDataGenerator;
import io.harness.cvng.notification.transformer.EmailNotificationMethodTransformer;
import io.harness.cvng.notification.transformer.MSTeamsNotificationMethodTransformer;
import io.harness.cvng.notification.transformer.MonitoredServiceNotificationRuleConditionTransformer;
import io.harness.cvng.notification.transformer.NotificationMethodTransformer;
import io.harness.cvng.notification.transformer.NotificationRuleConditionTransformer;
import io.harness.cvng.notification.transformer.PagerDutyNotificationMethodTransformer;
import io.harness.cvng.notification.transformer.SLONotificationRuleConditionTransformer;
import io.harness.cvng.notification.transformer.SlackNotificationMethodTransformer;
import io.harness.cvng.outbox.CVServiceOutboxEventHandler;
import io.harness.cvng.outbox.MonitoredServiceOutboxEventHandler;
import io.harness.cvng.outbox.ServiceLevelObjectiveOutboxEventHandler;
import io.harness.cvng.resources.VerifyStepResource;
import io.harness.cvng.servicelevelobjective.beans.SLIExecutionType;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective.AbstractServiceLevelObjectiveUpdatableEntity;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective.CompositeServiceLevelObjectiveUpdatableEntity;
import io.harness.cvng.servicelevelobjective.entities.RatioServiceLevelIndicator.RatioServiceLevelIndicatorUpdatableEntity;
import io.harness.cvng.servicelevelobjective.entities.RequestServiceLevelIndicator.RequestServiceLevelIndicatorUpdatableEntity;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorUpdatableEntity;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective.SimpleServiceLevelObjectiveUpdatableEntity;
import io.harness.cvng.servicelevelobjective.entities.ThresholdServiceLevelIndicator.ThresholdServiceLevelIndicatorUpdatableEntity;
import io.harness.cvng.servicelevelobjective.services.api.AnnotationService;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLORecordService;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLOService;
import io.harness.cvng.servicelevelobjective.services.api.GraphDataService;
import io.harness.cvng.servicelevelobjective.services.api.SLIAnalyserService;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataProcessorService;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataUnavailabilityInstancesHandlerService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLODashboardService;
import io.harness.cvng.servicelevelobjective.services.api.SLOErrorBudgetResetService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.SLOTimeScaleService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.servicelevelobjective.services.api.UserJourneyService;
import io.harness.cvng.servicelevelobjective.services.impl.AnnotationServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.CompositeSLORecordServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.CompositeSLOServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.GraphDataServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.RatioAnalyserServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.SLIDataProcessorServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.SLIDataUnavailabilityInstancesHandlerServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.SLIRecordServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.SLODashboardServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.SLOErrorBudgetResetServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.SLOHealthIndicatorServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.SLOTimeScaleServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.ServiceLevelIndicatorServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.ServiceLevelObjectiveV2ServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.ThresholdAnalyserServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.UserJourneyServiceImpl;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.CalenderSLOTargetTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.RatioServiceLevelIndicatorTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.RequestServiceLevelIndicatorTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.RollingSLOTargetTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.SLOTargetTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorEntityAndDTOTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ThresholdServiceLevelIndicatorTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelobjectivev2.CompositeSLOTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelobjectivev2.SLOV2Transformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelobjectivev2.SimpleSLOTransformer;
import io.harness.cvng.statemachine.beans.AnalysisState.StateType;
import io.harness.cvng.statemachine.services.api.AnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.AnalysisStateMachineService;
import io.harness.cvng.statemachine.services.api.CanaryTimeSeriesAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.CompositeSLOMetricAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.DeploymentLogAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.DeploymentLogClusterStateExecutor;
import io.harness.cvng.statemachine.services.api.DeploymentLogFeedbackStateExecutor;
import io.harness.cvng.statemachine.services.api.DeploymentLogHostSamplingStateExecutor;
import io.harness.cvng.statemachine.services.api.DeploymentMetricHostSamplingStateExecutor;
import io.harness.cvng.statemachine.services.api.DeploymentTimeSeriesAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.cvng.statemachine.services.api.PreDeploymentLogClusterStateExecutor;
import io.harness.cvng.statemachine.services.api.SLIMetricAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.ServiceGuardLogAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.ServiceGuardLogClusterStateExecutor;
import io.harness.cvng.statemachine.services.api.ServiceGuardTimeSeriesAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.ServiceGuardTrendAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.TestTimeSeriesAnalysisStateExecutor;
import io.harness.cvng.statemachine.services.impl.AnalysisStateMachineServiceImpl;
import io.harness.cvng.statemachine.services.impl.CompositeSLOAnalysisStateMachineServiceImpl;
import io.harness.cvng.statemachine.services.impl.DeploymentStateMachineServiceImpl;
import io.harness.cvng.statemachine.services.impl.LiveMonitoringStateMachineServiceImpl;
import io.harness.cvng.statemachine.services.impl.OrchestrationServiceImpl;
import io.harness.cvng.statemachine.services.impl.SLIAnalysisStateMachineServiceImpl;
import io.harness.cvng.usage.impl.SRMLicenseUsageImpl;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.cvng.verificationjob.services.impl.VerificationJobInstanceServiceImpl;
import io.harness.enforcement.client.EnforcementClientModule;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.govern.ProviderMethodInterceptor;
import io.harness.licensing.remote.NgLicenseHttpClientModule;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.lock.DistributedLockImplementation;
import io.harness.mongo.MongoPersistence;
import io.harness.opaclient.OpaClientModule;
import io.harness.outbox.TransactionOutboxModule;
import io.harness.outbox.api.OutboxDao;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.api.impl.OutboxDaoImpl;
import io.harness.outbox.api.impl.OutboxServiceImpl;
import io.harness.persistence.HPersistence;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.redis.RedisConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.serializer.CvNextGenRegistrars;
import io.harness.template.TemplateResourceClientModule;
import io.harness.threading.ThreadPool;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.timescaledb.TimeScaleDBServiceImpl;
import io.harness.waiter.AbstractWaiterModule;
import io.harness.waiter.AsyncWaitEngineImpl;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.waiter.WaiterConfiguration;
import io.harness.waiter.WaiterConfiguration.PersistenceLayer;
import io.harness.yaml.YamlSdkModule;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.matcher.Matchers;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
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
    install(new TemplateResourceClientModule(verificationConfiguration.getTemplateServiceClientConfig(),
        verificationConfiguration.getTemplateServiceSecret(), AuthorizationServiceHeader.CV_NEXT_GEN.getServiceId()));
    install(new AccountClientModule(getManagerClientConfig(verificationConfiguration.getManagerClientConfig()),
        verificationConfiguration.getNgManagerServiceConfig().getManagerServiceSecret(),
        AuthorizationServiceHeader.CV_NEXT_GEN.toString()));
    install(new OpaClientModule(verificationConfiguration.getOpaClientConfig(),
        verificationConfiguration.getPolicyManagerSecret(), CV_NEXT_GEN.getServiceId()));
    bind(HPersistence.class).to(MongoPersistence.class);
    bind(TimeSeriesRecordService.class).to(TimeSeriesRecordServiceImpl.class);
    bind(OrchestrationService.class).to(OrchestrationServiceImpl.class);
    bind(AnalysisStateMachineService.class).to(AnalysisStateMachineServiceImpl.class).in(Scopes.SINGLETON);
    bind(TimeSeriesAnalysisService.class).to(TimeSeriesAnalysisServiceImpl.class);
    bind(TrendAnalysisService.class).to(TrendAnalysisServiceImpl.class);
    bind(LearningEngineTaskService.class).to(LearningEngineTaskServiceImpl.class);
    bind(LogClusterService.class).to(LogClusterServiceImpl.class);
    bind(LogAnalysisService.class).to(LogAnalysisServiceImpl.class);
    bind(DataCollectionTaskService.class).to(DataCollectionTaskServiceImpl.class);
    bind(WebhookConfigService.class).toInstance(new WebhookConfigService() {
      @Override
      public String getWebhookApiBaseUrl() {
        return verificationConfiguration.getWebhookConfig().getWebhookBaseUrl();
      }
    });

    bind(DebugConfigService.class).toInstance(new DebugConfigService() {
      @Override
      public boolean isDebugEnabled() {
        return verificationConfiguration.isEnableDebugAPI();
      }
    });
    MapBinder<Type, DataCollectionTaskManagementService> dataCollectionTaskServiceMapBinder =
        MapBinder.newMapBinder(binder(), Type.class, DataCollectionTaskManagementService.class);
    dataCollectionTaskServiceMapBinder.addBinding(Type.SERVICE_GUARD)
        .to(ServiceGuardDataCollectionTaskServiceImpl.class)
        .in(Scopes.SINGLETON);
    dataCollectionTaskServiceMapBinder.addBinding(Type.DEPLOYMENT)
        .to(ServiceGuardDataCollectionTaskServiceImpl.class)
        .in(Scopes.SINGLETON);
    dataCollectionTaskServiceMapBinder.addBinding(Type.SLI)
        .to(SLIDataCollectionTaskServiceImpl.class)
        .in(Scopes.SINGLETON);
    bind(VerificationManagerService.class).to(VerificationManagerServiceImpl.class);
    bind(ErrorTrackingService.class).to(ErrorTrackingServiceImpl.class);
    bind(Clock.class).toInstance(Clock.systemUTC());
    bind(MetricPackService.class).to(MetricPackServiceImpl.class);
    bind(HeatMapService.class).to(HeatMapServiceImpl.class);
    bind(MetricPackService.class).to(MetricPackServiceImpl.class);
    bind(SplunkService.class).to(SplunkServiceImpl.class);
    bind(CVConfigService.class).to(CVConfigServiceImpl.class);
    bind(CompositeSLORecordService.class).to(CompositeSLORecordServiceImpl.class);
    MapBinder<DataSourceType, CVConfigToHealthSourceTransformer> dataSourceTypeToHealthSourceTransformerMapBinder =
        MapBinder.newMapBinder(binder(), DataSourceType.class, CVConfigToHealthSourceTransformer.class);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.APP_DYNAMICS)
        .to(AppDynamicsHealthSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.NEW_RELIC)
        .to(NewRelicHealthSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.STACKDRIVER_LOG)
        .to(StackdriverLogHealthSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.SPLUNK)
        .to(SplunkHealthSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.SPLUNK_METRIC)
        .to(SplunkMetricHealthSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.PROMETHEUS)
        .to(PrometheusHealthSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.STACKDRIVER)
        .to(StackdriverMetricHealthSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.DATADOG_METRICS)
        .to(DatadogMetricHealthSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.DATADOG_LOG)
        .to(DatadogLogHealthSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.CUSTOM_HEALTH_METRIC)
        .to(CustomHealthSourceSpecMetricTransformer.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.ELASTICSEARCH)
        .to(ELKHealthSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.CUSTOM_HEALTH_LOG)
        .to(CustomHealthSourceSpecLogTransformer.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.DYNATRACE)
        .to(DynatraceHealthSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.ERROR_TRACKING)
        .to(ErrorTrackingHealthSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.CLOUDWATCH_METRICS)
        .to(CloudWatchMetricHealthSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToHealthSourceTransformerMapBinder.addBinding(DataSourceType.AWS_PROMETHEUS)
        .to(AwsPrometheusHealthSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    MapBinder<DataSourceType, DataCollectionInfoMapper> dataSourceTypeDataCollectionInfoMapperMapBinder =
        MapBinder.newMapBinder(binder(), DataSourceType.class, DataCollectionInfoMapper.class);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.APP_DYNAMICS)
        .to(AppDynamicsDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.NEW_RELIC)
        .to(NewRelicDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.STACKDRIVER_LOG)
        .to(StackdriverLogDataCollectionInfoMapper.class);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.CUSTOM_HEALTH_METRIC)
        .to(CustomHealthMetricDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.CUSTOM_HEALTH_LOG)
        .to(CustomHealthLogDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.SPLUNK)
        .to(SplunkDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.SPLUNK_METRIC)
        .to(SplunkMetricDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.PROMETHEUS)
        .to(PrometheusDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.STACKDRIVER)
        .to(StackdriverDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.DATADOG_METRICS)
        .to(DatadogMetricDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.DATADOG_LOG)
        .to(DatadogLogDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.ELASTICSEARCH)
        .to(ELKDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.DYNATRACE)
        .to(DynatraceDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.ERROR_TRACKING)
        .to(ErrorTrackingDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.CLOUDWATCH_METRICS)
        .to(CloudWatchMetricDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.AWS_PROMETHEUS)
        .to(AwsPrometheusDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.SUMOLOGIC_LOG)
        .to(SumologicLogDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionInfoMapperMapBinder.addBinding(DataSourceType.SUMOLOGIC_METRICS)
        .to(SumologicMetricDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    MapBinder<DataSourceType, DataCollectionSLIInfoMapper> dataSourceTypeDataCollectionSLIInfoMapperMapBinder =
        MapBinder.newMapBinder(binder(), DataSourceType.class, DataCollectionSLIInfoMapper.class);
    dataSourceTypeDataCollectionSLIInfoMapperMapBinder.addBinding(DataSourceType.PROMETHEUS)
        .to(PrometheusDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionSLIInfoMapperMapBinder.addBinding(DataSourceType.APP_DYNAMICS)
        .to(AppDynamicsDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionSLIInfoMapperMapBinder.addBinding(DataSourceType.NEW_RELIC)
        .to(NewRelicDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionSLIInfoMapperMapBinder.addBinding(DataSourceType.DATADOG_METRICS)
        .to(DatadogMetricDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionSLIInfoMapperMapBinder.addBinding(DataSourceType.STACKDRIVER)
        .to(StackdriverDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionSLIInfoMapperMapBinder.addBinding(DataSourceType.CUSTOM_HEALTH_METRIC)
        .to(CustomHealthMetricDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionSLIInfoMapperMapBinder.addBinding(DataSourceType.DYNATRACE)
        .to(DynatraceDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionSLIInfoMapperMapBinder.addBinding(DataSourceType.SPLUNK_METRIC)
        .to(SplunkMetricDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionSLIInfoMapperMapBinder.addBinding(DataSourceType.CLOUDWATCH_METRICS)
        .to(CloudWatchMetricDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionSLIInfoMapperMapBinder.addBinding(DataSourceType.AWS_PROMETHEUS)
        .to(AwsPrometheusDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeDataCollectionSLIInfoMapperMapBinder.addBinding(DataSourceType.SUMOLOGIC_METRICS)
        .to(SumologicMetricDataCollectionInfoMapper.class)
        .in(Scopes.SINGLETON);
    MapBinder<MonitoredServiceSpecType, VerifyStepMonitoredServiceResolutionService>
        verifyStepCvConfigServiceMapBinder = MapBinder.newMapBinder(
            binder(), MonitoredServiceSpecType.class, VerifyStepMonitoredServiceResolutionService.class);
    verifyStepCvConfigServiceMapBinder.addBinding(MonitoredServiceSpecType.CONFIGURED)
        .to(ConfiguredVerifyStepMonitoredServiceResolutionServiceImpl.class)
        .in(Scopes.SINGLETON);
    verifyStepCvConfigServiceMapBinder.addBinding(MonitoredServiceSpecType.DEFAULT)
        .to(DefaultVerifyStepMonitoredServiceResolutionServiceImpl.class)
        .in(Scopes.SINGLETON);
    verifyStepCvConfigServiceMapBinder.addBinding(MonitoredServiceSpecType.TEMPLATE)
        .to(TemplateVerifyStepMonitoredServiceResolutionServiceImpl.class)
        .in(Scopes.SINGLETON);

    bind(VerifyStepResource.class).to(VerifyStepResourceImpl.class);
    bind(MetricPackService.class).to(MetricPackServiceImpl.class);
    bind(AppDynamicsService.class).to(AppDynamicsServiceImpl.class).in(Singleton.class);
    bind(LogRecordService.class).to(LogRecordServiceImpl.class);
    bind(VerificationJobInstanceService.class).to(VerificationJobInstanceServiceImpl.class);
    bind(VerificationTaskService.class).to(VerificationTaskServiceImpl.class);
    bind(TimeSeriesDashboardService.class).to(TimeSeriesDashboardServiceImpl.class);
    bind(ActivityService.class).to(ActivityServiceImpl.class);
    bind(LogDashboardService.class).to(LogDashboardServiceImpl.class);
    bind(ErrorTrackingDashboardService.class).to(ErrorTrackingDashboardServiceImpl.class);
    bind(LicenseUsageInterface.class).to(SRMLicenseUsageImpl.class);
    bind(DeploymentTimeSeriesAnalysisService.class).to(DeploymentTimeSeriesAnalysisServiceImpl.class);
    bind(NextGenService.class).to(NextGenServiceImpl.class);
    bind(HostRecordService.class).to(HostRecordServiceImpl.class);
    bind(LogFeedbackService.class).to(LogFeedbackServiceImpl.class);
    bind(KubernetesActivitySourceService.class).to(KubernetesActivitySourceServiceImpl.class);
    bind(DeploymentLogAnalysisService.class).to(DeploymentLogAnalysisServiceImpl.class);
    bind(VerificationJobInstanceAnalysisService.class).to(VerificationJobInstanceAnalysisServiceImpl.class);
    bind(OnboardingService.class).to(OnboardingServiceImpl.class);
    bind(CVNGMigrationService.class).to(CVNGMigrationServiceImpl.class).in(Singleton.class);
    bind(TimeLimiter.class).toInstance(HTimeLimiter.create());
    bind(StackdriverService.class).to(StackdriverServiceImpl.class);
    bind(DatadogService.class).to(DatadogServiceImpl.class);
    bind(DynatraceService.class).to(DynatraceServiceImpl.class);
    bind(DeeplinkURLService.class).to(DeeplinkURLServiceImpl.class);
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
    bind(NewRelicService.class).to(NewRelicServiceImpl.class);
    bind(CloudWatchService.class).to(CloudWatchServiceImpl.class);
    bind(AwsService.class).to(AwsServiceImpl.class);
    bind(ParseSampleDataService.class).to(ParseSampleDataServiceImpl.class);
    bind(VerifyStepDemoService.class).to(VerifyStepDemoServiceImpl.class);
    bind(StateMachineEventPublisherService.class).to(StateMachineEventPublisherServiceImpl.class);
    bind(CustomChangeEventPublisherService.class).to(CustomChangeEventPublisherServiceImpl.class);
    bind(FakeFeatureFlagSRMProducer.class);
    bind(String.class)
        .annotatedWith(Names.named("portalUrl"))
        .toInstance(verificationConfiguration.getPortalUrl().endsWith("/")
                ? verificationConfiguration.getPortalUrl()
                : verificationConfiguration.getPortalUrl() + "/");
    bind(CVNGLogService.class).to(CVNGLogServiceImpl.class);
    bind(ExecutionLogService.class).to(ExecutionLogServiceImpl.class);
    bind(DeleteEntityByHandler.class).to(DefaultDeleteEntityByHandler.class);
    bind(TimeSeriesAnomalousPatternsService.class).to(TimeSeriesAnomalousPatternsServiceImpl.class);
    bind(SLOTimeScaleService.class).to(SLOTimeScaleServiceImpl.class);
    try {
      bind(TimeScaleDBService.class)
          .toConstructor(TimeScaleDBServiceImpl.class.getConstructor(TimeScaleDBConfig.class));
    } catch (NoSuchMethodException e) {
      log.error("TimeScaleDbServiceImpl Initialization Failed in due to missing constructor", e);
    }

    if (verificationConfiguration.getEnableDashboardTimescale() != null
        && verificationConfiguration.getEnableDashboardTimescale()) {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(verificationConfiguration.getTimeScaleDBConfig() != null
                  ? verificationConfiguration.getTimeScaleDBConfig()
                  : TimeScaleDBConfig.builder().build());
    } else {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(TimeScaleDBConfig.builder().build());
    }

    bind(MonitoringSourcePerpetualTaskService.class).to(MonitoringSourcePerpetualTaskServiceImpl.class);
    MapBinder<DataSourceType, DataSourceConnectivityChecker> dataSourceTypeToServiceMapBinder =
        MapBinder.newMapBinder(binder(), DataSourceType.class, DataSourceConnectivityChecker.class);
    dataSourceTypeToServiceMapBinder.addBinding(DataSourceType.APP_DYNAMICS)
        .to(AppDynamicsService.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToServiceMapBinder.addBinding(DataSourceType.SPLUNK).to(SplunkService.class).in(Scopes.SINGLETON);
    dataSourceTypeToServiceMapBinder.addBinding(DataSourceType.STACKDRIVER)
        .to(StackdriverService.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeToServiceMapBinder.addBinding(DataSourceType.DATADOG_METRICS)
        .to(DatadogService.class)
        .in(Scopes.SINGLETON);

    MapBinder<DataSourceType, NextGenHealthSourceHelper> dataSourceTypeNextGenHelperMapBinder =
        MapBinder.newMapBinder(binder(), DataSourceType.class, NextGenHealthSourceHelper.class);
    dataSourceTypeNextGenHelperMapBinder.addBinding(DataSourceType.SUMOLOGIC_METRICS)
        .to(SumologicMetricNextGenHealthSourceHelper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeNextGenHelperMapBinder.addBinding(DataSourceType.SUMOLOGIC_LOG)
        .to(SumologicLogNextGenHealthSourceHelper.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeNextGenHelperMapBinder.addBinding(DataSourceType.ELASTICSEARCH)
        .to(ElasticSearchLogNextGenHealthSourceHelper.class)
        .in(Scopes.SINGLETON);

    MapBinder<DataSourceType, CVConfigUpdatableEntity> dataSourceTypeCVConfigMapBinder =
        MapBinder.newMapBinder(binder(), DataSourceType.class, CVConfigUpdatableEntity.class);

    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.APP_DYNAMICS)
        .to(AppDynamicsCVConfigUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.NEW_RELIC)
        .to(NewRelicCVConfigUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.PROMETHEUS)
        .to(PrometheusUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.CUSTOM_HEALTH_METRIC)
        .to(CustomHealthMetricCVConfigUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.CUSTOM_HEALTH_LOG)
        .to(CustomHealthLogCVConfigUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.STACKDRIVER)
        .to(StackDriverCVConfigUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.STACKDRIVER_LOG)
        .to(StackdriverLogCVConfigUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.SPLUNK)
        .to(SplunkCVConfigUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.DATADOG_METRICS)
        .to(DatadogMetricCVConfigUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.DATADOG_LOG)
        .to(DatadogLogCVConfigUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.ELASTICSEARCH)
        .to(ELKCVConfigUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.DYNATRACE)
        .to(DynatraceCVConfigUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.ERROR_TRACKING)
        .to(ErrorTrackingCVConfigUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.SPLUNK_METRIC)
        .to(SplunkMetricUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.CLOUDWATCH_METRICS)
        .to(CloudWatchMetricCVConfigUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    dataSourceTypeCVConfigMapBinder.addBinding(DataSourceType.AWS_PROMETHEUS)
        .to(AwsPrometheusUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    MapBinder<String, ServiceLevelIndicatorUpdatableEntity> serviceLevelIndicatorMapBinder =
        MapBinder.newMapBinder(binder(), String.class, ServiceLevelIndicatorUpdatableEntity.class);
    serviceLevelIndicatorMapBinder
        .addBinding(ServiceLevelIndicator.getEvaluationAndMetricType(SLIExecutionType.WINDOW, SLIMetricType.RATIO))
        .to(RatioServiceLevelIndicatorUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    serviceLevelIndicatorMapBinder
        .addBinding(ServiceLevelIndicator.getEvaluationAndMetricType(SLIExecutionType.WINDOW, SLIMetricType.THRESHOLD))
        .to(ThresholdServiceLevelIndicatorUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    serviceLevelIndicatorMapBinder
        .addBinding(ServiceLevelIndicator.getEvaluationAndMetricType(SLIExecutionType.REQUEST, null))
        .to(RequestServiceLevelIndicatorUpdatableEntity.class)
        .in(Scopes.SINGLETON);

    MapBinder<ServiceLevelObjectiveType, AbstractServiceLevelObjectiveUpdatableEntity>
        serviceLevelObjectiveTypeUpdatableEntityMapBinder = MapBinder.newMapBinder(
            binder(), ServiceLevelObjectiveType.class, AbstractServiceLevelObjectiveUpdatableEntity.class);
    serviceLevelObjectiveTypeUpdatableEntityMapBinder.addBinding(ServiceLevelObjectiveType.SIMPLE)
        .to(SimpleServiceLevelObjectiveUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    serviceLevelObjectiveTypeUpdatableEntityMapBinder.addBinding(ServiceLevelObjectiveType.COMPOSITE)
        .to(CompositeServiceLevelObjectiveUpdatableEntity.class)
        .in(Scopes.SINGLETON);
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
    bind(EntityDisabledTimeService.class).to(EntityDisabledTimeServiceImpl.class);
    bind(ServiceDependencyService.class).to(ServiceDependencyServiceImpl.class);
    bind(ServiceDependencyGraphService.class).to(ServiceDependencyGraphServiceImpl.class);
    bind(SetupUsageEventService.class).to(SetupUsageEventServiceImpl.class);
    bind(CVNGStepService.class).to(CVNGStepServiceImpl.class);
    bind(PagerDutyService.class).to(PagerDutyServiceImpl.class);
    bind(WebhookService.class).to(WebhookServiceImpl.class);
    bind(CVNGDemoPerpetualTaskService.class).to(CVNGDemoPerpetualTaskServiceImpl.class);
    bind(CVNGDemoDataIndexService.class).to(CVNGDemoDataIndexServiceImpl.class);
    bind(ChiDemoService.class).to(ChiDemoServiceImpl.class);
    bindChangeSourceUpdatedEntity();
    bindChangeSourceDemoHandler();
    bind(ChangeSourceService.class).to(ChangeSourceServiceImpl.class);
    bind(ChangeSourceEntityAndDTOTransformer.class);
    bind(SLIRecordService.class).to(SLIRecordServiceImpl.class);
    bind(SLODashboardService.class).to(SLODashboardServiceImpl.class);
    bind(SLIDataProcessorService.class).to(SLIDataProcessorServiceImpl.class);
    bind(SLOHealthIndicatorService.class).to(SLOHealthIndicatorServiceImpl.class);
    bind(TimeSeriesThresholdService.class).to(TimeSeriesThresholdServiceImpl.class);
    bind(RiskCategoryService.class).to(RiskCategoryServiceImpl.class);
    bind(GraphDataService.class).to(GraphDataServiceImpl.class);
    bind(DowntimeService.class).to(DowntimeServiceImpl.class);
    bind(EntityUnavailabilityStatusesService.class).to(EntityUnavailabilityStatusesServiceImpl.class);
    bind(AnnotationService.class).to(AnnotationServiceImpl.class);
    install(NgLicenseHttpClientModule.getInstance(verificationConfiguration.getNgManagerClientConfig(),
        verificationConfiguration.getNgManagerServiceSecret(), CV_NEXT_GEN.getServiceId()));

    MapBinder<ChangeSourceType, ChangeSourceSpecTransformer> changeSourceTypeChangeSourceSpecTransformerMapBinder =
        MapBinder.newMapBinder(binder(), ChangeSourceType.class, ChangeSourceSpecTransformer.class);
    changeSourceTypeChangeSourceSpecTransformerMapBinder.addBinding(ChangeSourceType.HARNESS_CD)
        .to(HarnessCDChangeSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    changeSourceTypeChangeSourceSpecTransformerMapBinder.addBinding(ChangeSourceType.PAGER_DUTY)
        .to(PagerDutyChangeSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    changeSourceTypeChangeSourceSpecTransformerMapBinder.addBinding(ChangeSourceType.KUBERNETES)
        .to(KubernetesChangeSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    changeSourceTypeChangeSourceSpecTransformerMapBinder.addBinding(ChangeSourceType.HARNESS_CD_CURRENT_GEN)
        .to(HarnessCDCurrentGenChangeSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    changeSourceTypeChangeSourceSpecTransformerMapBinder.addBinding(ChangeSourceType.CUSTOM_DEPLOY)
        .to(CustomChangeSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    changeSourceTypeChangeSourceSpecTransformerMapBinder.addBinding(ChangeSourceType.CUSTOM_INFRA)
        .to(CustomChangeSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    changeSourceTypeChangeSourceSpecTransformerMapBinder.addBinding(ChangeSourceType.CUSTOM_INCIDENT)
        .to(CustomChangeSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);
    changeSourceTypeChangeSourceSpecTransformerMapBinder.addBinding(ChangeSourceType.CUSTOM_FF)
        .to(CustomChangeSourceSpecTransformer.class)
        .in(Scopes.SINGLETON);

    bindAnalysisStateExecutor();

    Multibinder<BaseMonitoredServiceHandler> monitoredServiceHandlerMultibinder =
        Multibinder.newSetBinder(binder(), BaseMonitoredServiceHandler.class);
    monitoredServiceHandlerMultibinder.addBinding().to(MonitoredServiceSLIMetricUpdateHandler.class);

    MapBinder<ActivityType, ActivityUpdatableEntity> activityTypeActivityUpdatableEntityMapBinder =
        MapBinder.newMapBinder(binder(), ActivityType.class, ActivityUpdatableEntity.class);
    activityTypeActivityUpdatableEntityMapBinder.addBinding(ActivityType.PAGER_DUTY)
        .to(PagerDutyActivityUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    activityTypeActivityUpdatableEntityMapBinder.addBinding(ActivityType.DEPLOYMENT)
        .to(DeploymentActivityUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    activityTypeActivityUpdatableEntityMapBinder.addBinding(ActivityType.KUBERNETES)
        .to(KubernetesClusterActivityUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    activityTypeActivityUpdatableEntityMapBinder.addBinding(ActivityType.HARNESS_CD_CURRENT_GEN)
        .to(HarnessCDCurrentGenActivityUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    activityTypeActivityUpdatableEntityMapBinder.addBinding(ActivityType.FEATURE_FLAG)
        .to(InternalChangeActivityUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    activityTypeActivityUpdatableEntityMapBinder.addBinding(ActivityType.CHAOS_EXPERIMENT)
        .to(InternalChangeActivityUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    activityTypeActivityUpdatableEntityMapBinder.addBinding(ActivityType.CUSTOM_DEPLOY)
        .to(CustomChangeActivityUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    activityTypeActivityUpdatableEntityMapBinder.addBinding(ActivityType.CUSTOM_INCIDENT)
        .to(CustomChangeActivityUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    activityTypeActivityUpdatableEntityMapBinder.addBinding(ActivityType.CUSTOM_INFRA)
        .to(CustomChangeActivityUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    activityTypeActivityUpdatableEntityMapBinder.addBinding(ActivityType.CUSTOM_FF)
        .to(CustomChangeActivityUpdatableEntity.class)
        .in(Scopes.SINGLETON);

    MapBinder<ChangeSourceType, ChangeSourceUpdateHandler> changeSourceUpdateHandlerMapBinder =
        MapBinder.newMapBinder(binder(), ChangeSourceType.class, ChangeSourceUpdateHandler.class);
    changeSourceUpdateHandlerMapBinder.addBinding(ChangeSourceType.PAGER_DUTY)
        .to(PagerdutyChangeSourceUpdateHandler.class)
        .in(Scopes.SINGLETON);
    changeSourceUpdateHandlerMapBinder.addBinding(ChangeSourceType.KUBERNETES)
        .to(KubernetesChangeSourceUpdateHandler.class)
        .in(Scopes.SINGLETON);

    MapBinder<ActivityType, ActivityUpdateHandler> activityUpdateHandlerMapBinder =
        MapBinder.newMapBinder(binder(), ActivityType.class, ActivityUpdateHandler.class);
    activityUpdateHandlerMapBinder.addBinding(ActivityType.KUBERNETES)
        .to(KubernetesClusterActivityUpdateHandler.class)
        .in(Scopes.SINGLETON);
    activityUpdateHandlerMapBinder.addBinding(ActivityType.DEPLOYMENT)
        .to(DeploymentActivityUpdateHandler.class)
        .in(Scopes.SINGLETON);
    activityUpdateHandlerMapBinder.addBinding(ActivityType.FEATURE_FLAG)
        .to(InternalChangeActivityUpdateHandler.class)
        .in(Scopes.SINGLETON);
    activityUpdateHandlerMapBinder.addBinding(ActivityType.CHAOS_EXPERIMENT)
        .to(InternalChangeActivityUpdateHandler.class)
        .in(Scopes.SINGLETON);
    activityUpdateHandlerMapBinder.addBinding(ActivityType.CUSTOM_DEPLOY)
        .to(CustomChangeActivityUpdateHandler.class)
        .in(Scopes.SINGLETON);
    activityUpdateHandlerMapBinder.addBinding(ActivityType.CUSTOM_INFRA)
        .to(CustomChangeActivityUpdateHandler.class)
        .in(Scopes.SINGLETON);
    activityUpdateHandlerMapBinder.addBinding(ActivityType.CUSTOM_INCIDENT)
        .to(CustomChangeActivityUpdateHandler.class)
        .in(Scopes.SINGLETON);
    activityUpdateHandlerMapBinder.addBinding(ActivityType.CUSTOM_FF)
        .to(CustomChangeActivityUpdateHandler.class)
        .in(Scopes.SINGLETON);

    MapBinder<SLOTargetType, SLOTargetTransformer> sloTargetTypeSLOTargetTransformerMapBinder =
        MapBinder.newMapBinder(binder(), SLOTargetType.class, SLOTargetTransformer.class);
    sloTargetTypeSLOTargetTransformerMapBinder.addBinding(SLOTargetType.CALENDER)
        .to(CalenderSLOTargetTransformer.class)
        .in(Scopes.SINGLETON);
    sloTargetTypeSLOTargetTransformerMapBinder.addBinding(SLOTargetType.ROLLING).to(RollingSLOTargetTransformer.class);
    bind(ChangeEventService.class).to(ChangeEventServiceImpl.class).in(Scopes.SINGLETON);
    bind(InternalChangeConsumerService.class).to(InternalChangeConsumerServiceImpl.class).in(Scopes.SINGLETON);
    bind(ChangeEventEntityAndDTOTransformer.class);

    MapBinder<ServiceLevelObjectiveType, SLOV2Transformer> serviceLevelObjectiveTypeSLOV2TransformerMapBinder =
        MapBinder.newMapBinder(binder(), ServiceLevelObjectiveType.class, SLOV2Transformer.class);
    serviceLevelObjectiveTypeSLOV2TransformerMapBinder.addBinding(ServiceLevelObjectiveType.SIMPLE)
        .to(SimpleSLOTransformer.class)
        .in(Scopes.SINGLETON);
    serviceLevelObjectiveTypeSLOV2TransformerMapBinder.addBinding(ServiceLevelObjectiveType.COMPOSITE)
        .to(CompositeSLOTransformer.class)
        .in(Scopes.SINGLETON);

    bind(ServiceLevelObjectiveV2Service.class).to(ServiceLevelObjectiveV2ServiceImpl.class).in(Singleton.class);
    bind(SLOErrorBudgetResetService.class).to(SLOErrorBudgetResetServiceImpl.class).in(Singleton.class);
    bind(UserJourneyService.class).to(UserJourneyServiceImpl.class);
    bind(ServiceLevelIndicatorService.class).to(ServiceLevelIndicatorServiceImpl.class).in(Singleton.class);
    bind(SLIDataProcessorService.class).to(SLIDataProcessorServiceImpl.class);
    bind(SLIDataUnavailabilityInstancesHandlerService.class).to(SLIDataUnavailabilityInstancesHandlerServiceImpl.class);
    bind(ServiceLevelIndicatorEntityAndDTOTransformer.class);
    bind(CompositeSLOService.class).to(CompositeSLOServiceImpl.class);
    bind(DebugService.class).to(DebugServiceImpl.class).in(Singleton.class);

    MapBinder<String, ServiceLevelIndicatorTransformer> serviceLevelIndicatorFQDITransformerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, ServiceLevelIndicatorTransformer.class);
    serviceLevelIndicatorFQDITransformerMapBinder
        .addBinding(ServiceLevelIndicator.getEvaluationAndMetricType(SLIExecutionType.REQUEST, null))
        .to(RequestServiceLevelIndicatorTransformer.class)
        .in(Scopes.SINGLETON);
    serviceLevelIndicatorFQDITransformerMapBinder
        .addBinding(ServiceLevelIndicator.getEvaluationAndMetricType(SLIExecutionType.WINDOW, SLIMetricType.RATIO))
        .to(RatioServiceLevelIndicatorTransformer.class)
        .in(Scopes.SINGLETON);
    serviceLevelIndicatorFQDITransformerMapBinder
        .addBinding(ServiceLevelIndicator.getEvaluationAndMetricType(SLIExecutionType.WINDOW, SLIMetricType.THRESHOLD))
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
    changeTypeMetaDataTransformerMapBinder.addBinding(ChangeSourceType.HARNESS_FF)
        .to(InternalChangeEventTransformer.class)
        .in(Scopes.SINGLETON);
    changeTypeMetaDataTransformerMapBinder.addBinding(ChangeSourceType.HARNESS_CE)
        .to(InternalChangeEventTransformer.class)
        .in(Scopes.SINGLETON);
    changeTypeMetaDataTransformerMapBinder.addBinding(ChangeSourceType.CUSTOM_DEPLOY)
        .to(CustomChangeEventTransformer.class)
        .in(Scopes.SINGLETON);
    changeTypeMetaDataTransformerMapBinder.addBinding(ChangeSourceType.CUSTOM_INFRA)
        .to(CustomChangeEventTransformer.class)
        .in(Scopes.SINGLETON);
    changeTypeMetaDataTransformerMapBinder.addBinding(ChangeSourceType.CUSTOM_INCIDENT)
        .to(CustomChangeEventTransformer.class)
        .in(Scopes.SINGLETON);
    changeTypeMetaDataTransformerMapBinder.addBinding(ChangeSourceType.CUSTOM_FF)
        .to(CustomChangeEventTransformer.class)
        .in(Scopes.SINGLETON);

    bind(StateMachineMessageProcessor.class).to(StateMachineMessageProcessorImpl.class);

    MapBinder<SLIMetricType, SLIAnalyserService> sliAnalyserServiceMapBinder =
        MapBinder.newMapBinder(binder(), SLIMetricType.class, SLIAnalyserService.class);
    sliAnalyserServiceMapBinder.addBinding(SLIMetricType.RATIO).to(RatioAnalyserServiceImpl.class).in(Scopes.SINGLETON);
    sliAnalyserServiceMapBinder.addBinding(SLIMetricType.THRESHOLD)
        .to(ThresholdAnalyserServiceImpl.class)
        .in(Scopes.SINGLETON);
    bind(SideKickService.class).to(SideKickServiceImpl.class);
    MapBinder<SideKick.Type, SideKickExecutor> sideKickExecutorMapBinder =
        MapBinder.newMapBinder(binder(), SideKick.Type.class, SideKickExecutor.class);
    sideKickExecutorMapBinder.addBinding(SideKick.Type.DEMO_DATA_ACTIVITY_CREATOR)
        .to(DemoActivitySideKickExecutor.class)
        .in(Scopes.SINGLETON);
    sideKickExecutorMapBinder.addBinding(SideKick.Type.RETRY_CHANGE_SOURCE_HANDLE_DELETE)
        .to(RetryChangeSourceHandleDeleteSideKickExecutor.class)
        .in(Scopes.SINGLETON);
    sideKickExecutorMapBinder.addBinding(SideKick.Type.VERIFICATION_TASK_CLEANUP)
        .to(VerificationTaskCleanupSideKickExecutor.class)
        .in(Scopes.SINGLETON);
    sideKickExecutorMapBinder.addBinding(SideKick.Type.VERIFICATION_JOB_INSTANCE_CLEANUP)
        .to(VerificationJobInstanceCleanupSideKickExecutor.class)
        .in(Scopes.SINGLETON);
    sideKickExecutorMapBinder.addBinding(SideKick.Type.COMPOSITE_SLO_RECORDS_CLEANUP)
        .to(CompositeSLORecordsCleanupSideKickExecutor.class)
        .in(Scopes.SINGLETON);
    bind(NotificationRuleService.class).to(NotificationRuleServiceImpl.class);
    bind(CDStageMetaDataService.class).to(CDStageMetaDataServiceImpl.class);
    MapBinder<NotificationRuleType, NotificationRuleConditionTransformer>
        notificationRuleTypeNotificationRuleConditionTransformerMapBinder =
            MapBinder.newMapBinder(binder(), NotificationRuleType.class, NotificationRuleConditionTransformer.class);
    notificationRuleTypeNotificationRuleConditionTransformerMapBinder.addBinding(NotificationRuleType.SLO)
        .to(SLONotificationRuleConditionTransformer.class)
        .in(Scopes.SINGLETON);
    notificationRuleTypeNotificationRuleConditionTransformerMapBinder.addBinding(NotificationRuleType.MONITORED_SERVICE)
        .to(MonitoredServiceNotificationRuleConditionTransformer.class)
        .in(Scopes.SINGLETON);

    MapBinder<NotificationRuleType, NotificationRuleUpdatableEntity> notificationRuleMapBinder =
        MapBinder.newMapBinder(binder(), NotificationRuleType.class, NotificationRuleUpdatableEntity.class);
    notificationRuleMapBinder.addBinding(NotificationRuleType.SLO)
        .to(SLONotificationRuleUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    notificationRuleMapBinder.addBinding(NotificationRuleType.MONITORED_SERVICE)
        .to(MonitoredServiceNotificationRuleUpdatableEntity.class)
        .in(Scopes.SINGLETON);
    MapBinder<CVNGNotificationChannelType, NotificationMethodTransformer>
        channelTypeNotificationMethodTransformerMapBinder =
            MapBinder.newMapBinder(binder(), CVNGNotificationChannelType.class, NotificationMethodTransformer.class);
    channelTypeNotificationMethodTransformerMapBinder.addBinding(CVNGNotificationChannelType.EMAIL)
        .to(EmailNotificationMethodTransformer.class)
        .in(Scopes.SINGLETON);
    channelTypeNotificationMethodTransformerMapBinder.addBinding(CVNGNotificationChannelType.SLACK)
        .to(SlackNotificationMethodTransformer.class)
        .in(Scopes.SINGLETON);
    channelTypeNotificationMethodTransformerMapBinder.addBinding(CVNGNotificationChannelType.PAGERDUTY)
        .to(PagerDutyNotificationMethodTransformer.class)
        .in(Scopes.SINGLETON);
    channelTypeNotificationMethodTransformerMapBinder.addBinding(CVNGNotificationChannelType.MSTEAMS)
        .to(MSTeamsNotificationMethodTransformer.class)
        .in(Scopes.SINGLETON);

    MapBinder<NotificationRuleConditionType, NotificationRuleTemplateDataGenerator>
        notificationRuleConditionTypeTemplateDataGeneratorMapBinder = MapBinder.newMapBinder(
            binder(), NotificationRuleConditionType.class, NotificationRuleTemplateDataGenerator.class);
    notificationRuleConditionTypeTemplateDataGeneratorMapBinder
        .addBinding(NotificationRuleConditionType.CHANGE_OBSERVED)
        .to(ChangeObservedTemplateDataGenerator.class)
        .in(Scopes.SINGLETON);
    notificationRuleConditionTypeTemplateDataGeneratorMapBinder.addBinding(NotificationRuleConditionType.CHANGE_IMPACT)
        .to(ChangeImpactTemplateDataGenerator.class)
        .in(Scopes.SINGLETON);
    notificationRuleConditionTypeTemplateDataGeneratorMapBinder.addBinding(NotificationRuleConditionType.HEALTH_SCORE)
        .to(HealthScoreTemplateDataGenerator.class)
        .in(Scopes.SINGLETON);
    notificationRuleConditionTypeTemplateDataGeneratorMapBinder
        .addBinding(NotificationRuleConditionType.ERROR_BUDGET_REMAINING_PERCENTAGE)
        .to(RemainingPercentageTemplateDataGenerator.class)
        .in(Scopes.SINGLETON);
    notificationRuleConditionTypeTemplateDataGeneratorMapBinder
        .addBinding(NotificationRuleConditionType.ERROR_BUDGET_REMAINING_MINUTES)
        .to(RemainingMinutesTemplateDataGenerator.class)
        .in(Scopes.SINGLETON);
    notificationRuleConditionTypeTemplateDataGeneratorMapBinder
        .addBinding(NotificationRuleConditionType.ERROR_BUDGET_BURN_RATE)
        .to(BurnRateTemplateDataGenerator.class)
        .in(Scopes.SINGLETON);
    notificationRuleConditionTypeTemplateDataGeneratorMapBinder.addBinding(NotificationRuleConditionType.CODE_ERRORS)
        .to(ErrorTrackingTemplateDataGenerator.class)
        .in(Scopes.SINGLETON);

    ServiceHttpClientConfig serviceHttpClientConfig = this.verificationConfiguration.getAuditClientConfig();
    String secret = this.verificationConfiguration.getTemplateServiceSecret();
    String serviceId = CV_NEXT_GEN.getServiceId();
    bind(OutboxDao.class).to(OutboxDaoImpl.class);
    bind(OutboxService.class).to(OutboxServiceImpl.class);
    install(new AuditClientModule(
        serviceHttpClientConfig, secret, serviceId, this.verificationConfiguration.isEnableAudit()));
    install(new TransactionOutboxModule(DEFAULT_OUTBOX_POLL_CONFIGURATION, serviceId, false));
    MapBinder<String, OutboxEventHandler> outboxEventHandlerMap =
        MapBinder.newMapBinder(binder(), String.class, OutboxEventHandler.class);
    outboxEventHandlerMap.addBinding(ResourceTypeConstants.MONITORED_SERVICE)
        .to(MonitoredServiceOutboxEventHandler.class);
    outboxEventHandlerMap.addBinding(ResourceTypeConstants.SERVICE_LEVEL_OBJECTIVE)
        .to(ServiceLevelObjectiveOutboxEventHandler.class);
    bind(OutboxEventHandler.class).to(CVServiceOutboxEventHandler.class);
    bindRetryOnExceptionInterceptor();
    install(EnforcementClientModule.getInstance(verificationConfiguration.getNgManagerClientConfig(),
        verificationConfiguration.getNgManagerServiceSecret(), CV_NEXT_GEN.getServiceId(),
        verificationConfiguration.getEnforcementClientConfiguration()));
    bind(ELKService.class).to(ELKServiceImpl.class);
    MapBinder<VerificationTask.TaskType, AnalysisStateMachineService> taskTypeAnalysisStateMachineServiceMapBinder =
        MapBinder.newMapBinder(binder(), VerificationTask.TaskType.class, AnalysisStateMachineService.class);
    taskTypeAnalysisStateMachineServiceMapBinder.addBinding(VerificationTask.TaskType.LIVE_MONITORING)
        .to(LiveMonitoringStateMachineServiceImpl.class);
    taskTypeAnalysisStateMachineServiceMapBinder.addBinding(VerificationTask.TaskType.SLI)
        .to(SLIAnalysisStateMachineServiceImpl.class);
    taskTypeAnalysisStateMachineServiceMapBinder.addBinding(VerificationTask.TaskType.DEPLOYMENT)
        .to(DeploymentStateMachineServiceImpl.class);
    taskTypeAnalysisStateMachineServiceMapBinder.addBinding(VerificationTask.TaskType.COMPOSITE_SLO)
        .to(CompositeSLOAnalysisStateMachineServiceImpl.class);
    bind(HealthSourceOnboardingService.class).to(HealthSourceOnboardingServiceImpl.class);

    MapBinder<DowntimeType, DowntimeSpecDetailsTransformer> downtimeTransformerMapBinder =
        MapBinder.newMapBinder(binder(), DowntimeType.class, DowntimeSpecDetailsTransformer.class);
    downtimeTransformerMapBinder.addBinding(DowntimeType.ONE_TIME)
        .to(OnetimeDowntimeSpecDetailsTransformer.class)
        .in(Scopes.SINGLETON);
    downtimeTransformerMapBinder.addBinding(DowntimeType.RECURRING)
        .to(RecurringDowntimeSpecDetailsTransformer.class)
        .in(Scopes.SINGLETON);
    bind(EntityUnavailabilityStatusesEntityAndDTOTransformer.class);
  }

  private void bindChangeSourceUpdatedEntity() {
    MapBinder<ChangeSourceType, ChangeSource.UpdatableChangeSourceEntity> changeTypeSourceMapBinder =
        MapBinder.newMapBinder(binder(), ChangeSourceType.class, ChangeSource.UpdatableChangeSourceEntity.class);
    changeTypeSourceMapBinder.addBinding(HARNESS_CD)
        .to(HarnessCDChangeSource.UpdatableCDNGChangeSourceEntity.class)
        .in(Scopes.SINGLETON);
    changeTypeSourceMapBinder.addBinding(ChangeSourceType.PAGER_DUTY)
        .to(PagerDutyChangeSource.UpdatablePagerDutyChangeSourceEntity.class)
        .in(Scopes.SINGLETON);
    changeTypeSourceMapBinder.addBinding(ChangeSourceType.KUBERNETES)
        .to(KubernetesChangeSource.UpdatableKubernetesChangeSourceEntity.class)
        .in(Scopes.SINGLETON);
    changeTypeSourceMapBinder.addBinding(ChangeSourceType.HARNESS_CD_CURRENT_GEN)
        .to(HarnessCDCurrentGenChangeSource.UpdatableHarnessCDCurrentGenChangeSourceEntity.class)
        .in(Scopes.SINGLETON);
    changeTypeSourceMapBinder.addBinding(ChangeSourceType.CUSTOM_DEPLOY)
        .to(CustomChangeSource.UpdatableCustomChangeSourceEntity.class)
        .in(Scopes.SINGLETON);
    changeTypeSourceMapBinder.addBinding(ChangeSourceType.CUSTOM_INCIDENT)
        .to(CustomChangeSource.UpdatableCustomChangeSourceEntity.class)
        .in(Scopes.SINGLETON);
    changeTypeSourceMapBinder.addBinding(ChangeSourceType.CUSTOM_INFRA)
        .to(CustomChangeSource.UpdatableCustomChangeSourceEntity.class)
        .in(Scopes.SINGLETON);
    changeTypeSourceMapBinder.addBinding(ChangeSourceType.CUSTOM_FF)
        .to(CustomChangeSource.UpdatableCustomChangeSourceEntity.class)
        .in(Scopes.SINGLETON);
  }

  private void bindChangeSourceDemoHandler() {
    MapBinder<ChangeSourceType, ChangeSourceDemoDataGenerator> changeTypeSourceMapBinder =
        MapBinder.newMapBinder(binder(), ChangeSourceType.class, ChangeSourceDemoDataGenerator.class);
    changeTypeSourceMapBinder.addBinding(ChangeSourceType.PAGER_DUTY)
        .to(PagerdutyChangeSourceDemoDataGenerator.class)
        .in(Scopes.SINGLETON);
    changeTypeSourceMapBinder.addBinding(HARNESS_CD).to(CDNGChangeSourceDemoDataGenerator.class).in(Scopes.SINGLETON);
    changeTypeSourceMapBinder.addBinding(ChangeSourceType.KUBERNETES)
        .to(KubernetesChangeSourceDemoDataGenerator.class)
        .in(Scopes.SINGLETON);
  }

  private void bindRetryOnExceptionInterceptor() {
    bind(MethodExecutionHelper.class).asEagerSingleton();
    ProviderMethodInterceptor retryOnExceptionInterceptor =
        new ProviderMethodInterceptor(getProvider(RetryOnExceptionInterceptor.class));
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(RetryOnException.class), retryOnExceptionInterceptor);
  }

  private void bindAnalysisStateExecutor() {
    MapBinder<StateType, AnalysisStateExecutor> stateTypeAnalysisStateExecutorMap =
        MapBinder.newMapBinder(binder(), StateType.class, AnalysisStateExecutor.class);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.CANARY_TIME_SERIES)
        .to(CanaryTimeSeriesAnalysisStateExecutor.class)
        .in(Scopes.SINGLETON);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.DEPLOYMENT_LOG_ANALYSIS)
        .to(DeploymentLogAnalysisStateExecutor.class)
        .in(Scopes.SINGLETON);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.SERVICE_GUARD_LOG_ANALYSIS)
        .to(ServiceGuardLogAnalysisStateExecutor.class)
        .in(Scopes.SINGLETON);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.SERVICE_GUARD_TIME_SERIES)
        .to(ServiceGuardTimeSeriesAnalysisStateExecutor.class)
        .in(Scopes.SINGLETON);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.TEST_TIME_SERIES)
        .to(TestTimeSeriesAnalysisStateExecutor.class)
        .in(Scopes.SINGLETON);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.DEPLOYMENT_LOG_CLUSTER)
        .to(DeploymentLogClusterStateExecutor.class)
        .in(Scopes.SINGLETON);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.PRE_DEPLOYMENT_LOG_CLUSTER)
        .to(PreDeploymentLogClusterStateExecutor.class)
        .in(Scopes.SINGLETON);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.SERVICE_GUARD_LOG_CLUSTER)
        .to(ServiceGuardLogClusterStateExecutor.class)
        .in(Scopes.SINGLETON);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.SERVICE_GUARD_TREND_ANALYSIS)
        .to(ServiceGuardTrendAnalysisStateExecutor.class)
        .in(Scopes.SINGLETON);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.SLI_METRIC_ANALYSIS)
        .to(SLIMetricAnalysisStateExecutor.class)
        .in(Scopes.SINGLETON);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.COMPOSOITE_SLO_METRIC_ANALYSIS)
        .to(CompositeSLOMetricAnalysisStateExecutor.class)
        .in(Scopes.SINGLETON);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.DEPLOYMENT_METRIC_HOST_SAMPLING_STATE)
        .to(DeploymentMetricHostSamplingStateExecutor.class)
        .in(Scopes.SINGLETON);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.DEPLOYMENT_LOG_HOST_SAMPLING_STATE)
        .to(DeploymentLogHostSamplingStateExecutor.class)
        .in(Scopes.SINGLETON);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.DEPLOYMENT_TIME_SERIES_ANALYSIS_STATE)
        .to(DeploymentTimeSeriesAnalysisStateExecutor.class)
        .in(Scopes.SINGLETON);
    stateTypeAnalysisStateExecutorMap.addBinding(StateType.DEPLOYMENT_LOG_FEEDBACK_STATE)
        .to(DeploymentLogFeedbackStateExecutor.class)
        .in(Scopes.SINGLETON);
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
  @Singleton
  @Named("stateMachineMessageProcessorExecutor")
  public ExecutorService stateMachineMessageProcessorExecutor() {
    ExecutorService stateMachineMessageProcessorExecutor =
        ThreadPool.create(4, CVNextGenConstants.SRM_STATEMACHINE_MAX_THREADS, 5, TimeUnit.SECONDS,
            new ThreadFactoryBuilder()
                .setNameFormat(SRM_STATEMACHINE_EVENT + "_thread_pool")
                .setPriority(Thread.MIN_PRIORITY)
                .build());
    Runtime.getRuntime().addShutdownHook(new Thread(() -> stateMachineMessageProcessorExecutor.shutdownNow()));
    return stateMachineMessageProcessorExecutor;
  }

  @Provides
  @Named("yaml-schema-mapper")
  @Singleton
  public ObjectMapper getYamlSchemaObjectMapper() {
    ObjectMapper objectMapper = Jackson.newObjectMapper();
    VerificationApplication.configureObjectMapper(objectMapper);
    return objectMapper;
  }

  @Provides
  @Named("yaml-schema-subtypes")
  @Singleton
  public Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes() {
    Set<Class<? extends StepSpecType>> subTypesOfStepSpecType =
        HarnessReflections.get().getSubTypesOf(StepSpecType.class);
    Set<Class<?>> set = new HashSet<>(subTypesOfStepSpecType);
    return ImmutableMap.of(StepSpecType.class, set);
  }

  @Provides
  @Singleton
  List<YamlSchemaRootClass> yamlSchemaRootClasses() {
    return ImmutableList.<YamlSchemaRootClass>builder().addAll(CvNextGenRegistrars.yamlSchemaRegistrars).build();
  }

  private ServiceHttpClientConfig getManagerClientConfig(ServiceHttpClientConfig serviceHttpClientConfig) {
    String managerBaseUrl = serviceHttpClientConfig.getBaseUrl();
    managerBaseUrl = managerBaseUrl + (managerBaseUrl.endsWith("/") ? "api/" : "/api/");
    return ServiceHttpClientConfig.builder()
        .baseUrl(managerBaseUrl)
        .connectTimeOutSeconds(serviceHttpClientConfig.getConnectTimeOutSeconds())
        .readTimeOutSeconds(serviceHttpClientConfig.getReadTimeOutSeconds())
        .enableHttpLogging(serviceHttpClientConfig.getEnableHttpLogging())
        .build();
  }
}