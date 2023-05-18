/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import static io.harness.cvng.beans.TimeSeriesThresholdActionType.IGNORE;
import static io.harness.cvng.core.utils.DateTimeUtils.roundDownToMinBoundary;
import static io.harness.cvng.downtime.utils.DateTimeUtils.dtf;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import io.harness.cvng.activity.entities.CustomChangeActivity;
import io.harness.cvng.activity.entities.CustomChangeActivity.CustomChangeActivityBuilder;
import io.harness.cvng.activity.entities.DeploymentActivity;
import io.harness.cvng.activity.entities.DeploymentActivity.DeploymentActivityBuilder;
import io.harness.cvng.activity.entities.HarnessCDCurrentGenActivity;
import io.harness.cvng.activity.entities.HarnessCDCurrentGenActivity.HarnessCDCurrentGenActivityBuilder;
import io.harness.cvng.activity.entities.InternalChangeActivity;
import io.harness.cvng.activity.entities.InternalChangeActivity.InternalChangeActivityBuilder;
import io.harness.cvng.activity.entities.KubernetesClusterActivity;
import io.harness.cvng.activity.entities.KubernetesClusterActivity.KubernetesClusterActivityBuilder;
import io.harness.cvng.activity.entities.KubernetesClusterActivity.RelatedAppMonitoredService;
import io.harness.cvng.activity.entities.PagerDutyActivity;
import io.harness.cvng.activity.entities.PagerDutyActivity.PagerDutyActivityBuilder;
import io.harness.cvng.analysis.entities.CanaryLogAnalysisLearningEngineTask;
import io.harness.cvng.analysis.entities.CanaryLogAnalysisLearningEngineTask.CanaryLogAnalysisLearningEngineTaskBuilder;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.DeviationType;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.cvng.beans.ThresholdConfigType;
import io.harness.cvng.beans.TimeSeriesCustomThresholdActions;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.beans.TimeSeriesThresholdComparisonType;
import io.harness.cvng.beans.TimeSeriesThresholdCriteria;
import io.harness.cvng.beans.TimeSeriesThresholdType;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeEventDTO.ChangeEventDTOBuilder;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.CustomChangeEvent;
import io.harness.cvng.beans.change.CustomChangeEventMetadata;
import io.harness.cvng.beans.change.DeepLink;
import io.harness.cvng.beans.change.HarnessCDCurrentGenEventMetadata;
import io.harness.cvng.beans.change.HarnessCDEventMetadata;
import io.harness.cvng.beans.change.InternalChangeEvent;
import io.harness.cvng.beans.change.InternalChangeEventMetaData;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata.Action;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata.KubernetesResourceType;
import io.harness.cvng.beans.change.PagerDutyEventMetaData;
import io.harness.cvng.beans.customhealth.TimestampInfo;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO.ExecutionLogDTOBuilder;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO.LogLevel;
import io.harness.cvng.beans.cvnglog.TraceableType;
import io.harness.cvng.beans.job.Sensitivity;
import io.harness.cvng.cdng.beans.CVNGStepInfo;
import io.harness.cvng.cdng.beans.CVNGStepInfo.CVNGStepInfoBuilder;
import io.harness.cvng.cdng.beans.ConfiguredMonitoredServiceSpec;
import io.harness.cvng.cdng.beans.ConfiguredMonitoredServiceSpec.ConfiguredMonitoredServiceSpecBuilder;
import io.harness.cvng.cdng.beans.DefaultMonitoredServiceSpec;
import io.harness.cvng.cdng.beans.DefaultMonitoredServiceSpec.DefaultMonitoredServiceSpecBuilder;
import io.harness.cvng.cdng.beans.TemplateMonitoredServiceSpec;
import io.harness.cvng.cdng.beans.TemplateMonitoredServiceSpec.TemplateMonitoredServiceSpecBuilder;
import io.harness.cvng.cdng.beans.TestVerificationJobSpec;
import io.harness.cvng.cdng.beans.v2.AnalysedDeploymentTestDataNode;
import io.harness.cvng.cdng.beans.v2.AnalysisReason;
import io.harness.cvng.cdng.beans.v2.AnalysisResult;
import io.harness.cvng.cdng.beans.v2.ControlDataType;
import io.harness.cvng.cdng.beans.v2.MetricThreshold;
import io.harness.cvng.cdng.beans.v2.MetricThresholdCriteria;
import io.harness.cvng.cdng.beans.v2.MetricThresholdType;
import io.harness.cvng.cdng.beans.v2.MetricType;
import io.harness.cvng.cdng.beans.v2.MetricValue;
import io.harness.cvng.cdng.beans.v2.MetricsAnalysis;
import io.harness.cvng.cdng.entities.CVNGStepTask;
import io.harness.cvng.cdng.entities.CVNGStepTask.CVNGStepTaskBuilder;
import io.harness.cvng.cdng.entities.CVNGStepTask.Status;
import io.harness.cvng.core.beans.CustomChangeWebhookPayload;
import io.harness.cvng.core.beans.CustomChangeWebhookPayload.CustomChangeWebhookEventDetail;
import io.harness.cvng.core.beans.CustomChangeWebhookPayload.CustomChangeWebhookPayloadBuilder;
import io.harness.cvng.core.beans.CustomHealthLogDefinition;
import io.harness.cvng.core.beans.CustomHealthMetricDefinition;
import io.harness.cvng.core.beans.CustomHealthRequestDefinition;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.HealthSourceQueryType;
import io.harness.cvng.core.beans.RiskCategory;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.healthsource.HealthSourceParamsDTO;
import io.harness.cvng.core.beans.healthsource.QueryDefinition;
import io.harness.cvng.core.beans.healthsource.QueryParamsDTO;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO.ChangeSourceDTOBuilder;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.MonitoredServiceDTOBuilder;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.ServiceDependencyDTO;
import io.harness.cvng.core.beans.monitoredService.RiskCategoryDTO;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.CustomChangeSourceSpec;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.HarnessCDChangeSourceSpec;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.HarnessCDCurrentGenChangeSourceSpec;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.KubernetesChangeSourceSpec;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.PagerDutyChangeSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.AppDynamicsHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CustomHealthSourceLogSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CustomHealthSourceMetricSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NextGenHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NextGenHealthSourceSpec.NextGenHealthSourceSpecBuilder;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.IgnoreMetricThresholdSpec;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricCustomThresholdActions;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricThresholdActionType;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricThresholdCriteriaType;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.beans.sidekick.CompositeSLORecordsCleanupSideKickData;
import io.harness.cvng.core.beans.sidekick.CompositeSLORecordsCleanupSideKickData.CompositeSLORecordsCleanupSideKickDataBuilder;
import io.harness.cvng.core.beans.sidekick.VerificationJobInstanceCleanupSideKickData;
import io.harness.cvng.core.beans.sidekick.VerificationJobInstanceCleanupSideKickData.VerificationJobInstanceCleanupSideKickDataBuilder;
import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.AppDynamicsCVConfig.AppDynamicsCVConfigBuilder;
import io.harness.cvng.core.entities.AwsPrometheusCVConfig;
import io.harness.cvng.core.entities.AwsPrometheusCVConfig.AwsPrometheusCVConfigBuilder;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CloudWatchMetricCVConfig;
import io.harness.cvng.core.entities.CloudWatchMetricCVConfig.CloudWatchMetricCVConfigBuilder;
import io.harness.cvng.core.entities.CustomHealthLogCVConfig;
import io.harness.cvng.core.entities.CustomHealthMetricCVConfig;
import io.harness.cvng.core.entities.DatadogLogCVConfig;
import io.harness.cvng.core.entities.DatadogLogCVConfig.DatadogLogCVConfigBuilder;
import io.harness.cvng.core.entities.DatadogMetricCVConfig;
import io.harness.cvng.core.entities.DatadogMetricCVConfig.DatadogMetricCVConfigBuilder;
import io.harness.cvng.core.entities.DynatraceCVConfig;
import io.harness.cvng.core.entities.DynatraceCVConfig.DynatraceCVConfigBuilder;
import io.harness.cvng.core.entities.ELKCVConfig;
import io.harness.cvng.core.entities.ELKCVConfig.ELKCVConfigBuilder;
import io.harness.cvng.core.entities.ErrorTrackingCVConfig;
import io.harness.cvng.core.entities.ErrorTrackingCVConfig.ErrorTrackingCVConfigBuilder;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.NewRelicCVConfig;
import io.harness.cvng.core.entities.NewRelicCVConfig.NewRelicCVConfigBuilder;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.core.entities.NextGenLogCVConfig.NextGenLogCVConfigBuilder;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.cvng.core.entities.NextGenMetricCVConfig.NextGenMetricCVConfigBuilder;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.entities.PrometheusCVConfig.PrometheusCVConfigBuilder;
import io.harness.cvng.core.entities.QueryParams;
import io.harness.cvng.core.entities.SplunkCVConfig;
import io.harness.cvng.core.entities.SplunkCVConfig.SplunkCVConfigBuilder;
import io.harness.cvng.core.entities.SplunkMetricCVConfig;
import io.harness.cvng.core.entities.SplunkMetricCVConfig.SplunkMetricCVConfigBuilder;
import io.harness.cvng.core.entities.StackdriverCVConfig;
import io.harness.cvng.core.entities.StackdriverCVConfig.StackdriverCVConfigBuilder;
import io.harness.cvng.core.entities.StackdriverLogCVConfig;
import io.harness.cvng.core.entities.StackdriverLogCVConfig.StackdriverLogCVConfigBuilder;
import io.harness.cvng.core.entities.TimeSeriesThreshold;
import io.harness.cvng.core.entities.changeSource.CustomChangeSource;
import io.harness.cvng.core.entities.changeSource.CustomChangeSource.CustomChangeSourceBuilder;
import io.harness.cvng.core.entities.changeSource.HarnessCDChangeSource;
import io.harness.cvng.core.entities.changeSource.HarnessCDChangeSource.HarnessCDChangeSourceBuilder;
import io.harness.cvng.core.entities.changeSource.HarnessCDCurrentGenChangeSource;
import io.harness.cvng.core.entities.changeSource.HarnessCDCurrentGenChangeSource.HarnessCDCurrentGenChangeSourceBuilder;
import io.harness.cvng.core.entities.changeSource.KubernetesChangeSource;
import io.harness.cvng.core.entities.changeSource.KubernetesChangeSource.KubernetesChangeSourceBuilder;
import io.harness.cvng.core.entities.changeSource.PagerDutyChangeSource;
import io.harness.cvng.core.entities.changeSource.PagerDutyChangeSource.PagerDutyChangeSourceBuilder;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.dashboard.entities.HeatMap;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapBuilder;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapRisk;
import io.harness.cvng.downtime.beans.DowntimeCategory;
import io.harness.cvng.downtime.beans.DowntimeDTO;
import io.harness.cvng.downtime.beans.DowntimeDuration;
import io.harness.cvng.downtime.beans.DowntimeDurationType;
import io.harness.cvng.downtime.beans.DowntimeRecurrence;
import io.harness.cvng.downtime.beans.DowntimeRecurrenceType;
import io.harness.cvng.downtime.beans.DowntimeScope;
import io.harness.cvng.downtime.beans.DowntimeSpecDTO;
import io.harness.cvng.downtime.beans.DowntimeType;
import io.harness.cvng.downtime.beans.EntityDetails;
import io.harness.cvng.downtime.beans.EntityIdentifiersRule;
import io.harness.cvng.downtime.beans.EntityType;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatus;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec;
import io.harness.cvng.downtime.beans.OnetimeDowntimeType;
import io.harness.cvng.downtime.beans.RecurringDowntimeSpec;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.notification.beans.ErrorBudgetRemainingPercentageConditionSpec;
import io.harness.cvng.notification.beans.HealthScoreConditionSpec;
import io.harness.cvng.notification.beans.NotificationRuleCondition;
import io.harness.cvng.notification.beans.NotificationRuleConditionType;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleDTO.NotificationRuleDTOBuilder;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.channelDetails.CVNGEmailChannelSpec;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannel;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.servicelevelobjective.beans.AnnotationDTO;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO.SLOErrorBudgetResetDTOBuilder;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO.ServiceLevelIndicatorDTOBuilder;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO.ServiceLevelObjectiveV2DTOBuilder;
import io.harness.cvng.servicelevelobjective.beans.UserJourneyDTO;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdType;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.RequestBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.RollingSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.WindowBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.entities.RatioServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.RatioServiceLevelIndicator.RatioServiceLevelIndicatorBuilder;
import io.harness.cvng.servicelevelobjective.entities.RequestServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.RequestServiceLevelIndicator.RequestServiceLevelIndicatorBuilder;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator.SLOHealthIndicatorBuilder;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective.SimpleServiceLevelObjectiveBuilder;
import io.harness.cvng.verificationjob.entities.AutoVerificationJob;
import io.harness.cvng.verificationjob.entities.AutoVerificationJob.AutoVerificationJobBuilder;
import io.harness.cvng.verificationjob.entities.BlueGreenVerificationJob;
import io.harness.cvng.verificationjob.entities.BlueGreenVerificationJob.BlueGreenVerificationJobBuilder;
import io.harness.cvng.verificationjob.entities.CanaryBlueGreenVerificationJob.CanaryBlueGreenVerificationJobBuilder;
import io.harness.cvng.verificationjob.entities.CanaryVerificationJob;
import io.harness.cvng.verificationjob.entities.TestVerificationJob;
import io.harness.cvng.verificationjob.entities.TestVerificationJob.TestVerificationJobBuilder;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob.RuntimeParameter;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceBuilder;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.eventsframework.schemas.cv.EventDetails;
import io.harness.eventsframework.schemas.cv.InternalChangeEventDTO;
import io.harness.eventsframework.schemas.deployment.ArtifactDetails;
import io.harness.eventsframework.schemas.deployment.DeploymentEventDTO;
import io.harness.eventsframework.schemas.deployment.ExecutionDetails;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO.EnvironmentResponseDTOBuilder;
import io.harness.pms.yaml.ParameterField;

import com.google.common.collect.Sets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;

@Data
@Builder(buildMethodName = "unsafeBuild")
public class BuilderFactory {
  public static final String CONNECTOR_IDENTIFIER = "connectorIdentifier";
  @Getter @Setter(AccessLevel.PRIVATE) private Clock clock;
  @Getter @Setter(AccessLevel.PRIVATE) private Context context;

  public static BuilderFactory getDefault() {
    return BuilderFactory.builder().build();
  }

  public CanaryLogAnalysisLearningEngineTaskBuilder canaryLogAnalysisLearningEngineTaskBuilder() {
    return CanaryLogAnalysisLearningEngineTask.builder()
        .accountId(context.getAccountId())
        .uuid(generateUuid())
        .analysisEndTime(clock.instant())
        .analysisStartTime(clock.instant())
        .analysisType(LearningEngineTaskType.CANARY_LOG_ANALYSIS)
        .analysisSaveUrl("saveUrl")
        .controlDataUrl("controlDataUrl")
        .pickedAt(clock.instant())
        .failureUrl("failureUrl")
        .previousAnalysisUrl("previousAnalysisUrl")
        .verificationTaskId("verificationTaskId");
  }

  public CVNGStepTaskBuilder cvngStepTaskBuilder() {
    return CVNGStepTask.builder()
        .accountId(context.getAccountId())
        .activityId(generateUuid())
        .status(Status.IN_PROGRESS)
        .callbackId(generateUuid());
  }

  public ProjectParams getProjectParams() {
    return ProjectParams.builder()
        .accountIdentifier(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .build();
  }

  public EnvironmentResponseDTOBuilder environmentResponseDTOBuilder() {
    return EnvironmentResponseDTO.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .identifier(context.getEnvIdentifier())
        .projectIdentifier(context.getProjectIdentifier());
  }

  public VerificationJobInstanceBuilder verificationJobInstanceBuilder() {
    CVConfig cvConfig = appDynamicsCVConfigBuilder().uuid(generateUuid()).build();
    CVConfig cvConfig2 = errorTrackingCVConfigBuilder().uuid(generateUuid()).build();
    Map<String, CVConfig> cvConfigMap = new HashMap<>();
    cvConfigMap.put(cvConfig.getUuid(), cvConfig);
    cvConfigMap.put(cvConfig2.getUuid(), cvConfig2);
    List<CVConfig> cvConfigs = new ArrayList<>();
    cvConfigs.add(cvConfig);
    cvConfigs.add(cvConfig2);
    return VerificationJobInstance.builder()
        .accountId(context.getAccountId())
        .deploymentStartTime(clock.instant().minus(Duration.ofMinutes(2)))
        .startTime(clock.instant())
        .cvConfigMap(cvConfigMap)
        .dataCollectionDelay(Duration.ofMinutes(2))
        .resolvedJob(getVerificationJob(cvConfigs));
  }

  public SLOHealthIndicatorBuilder sLOHealthIndicatorBuilder() {
    return SLOHealthIndicator.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .errorBudgetRisk(ErrorBudgetRisk.EXHAUSTED)
        .errorBudgetRemainingPercentage(10)
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .serviceLevelObjectiveIdentifier("sloIdentifier");
  }

  public MonitoredServiceDTOBuilder monitoredServiceDTOBuilder() {
    return MonitoredServiceDTO.builder()
        .identifier(context.getMonitoredServiceIdentifier())
        .name("monitored service name")
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .type(MonitoredServiceType.APPLICATION)
        .description(generateUuid())
        .serviceRef(context.getServiceIdentifier())
        .environmentRef(context.getEnvIdentifier())
        .tags(new HashMap<>())
        .dependencies(Sets.newHashSet(ServiceDependencyDTO.builder().monitoredServiceIdentifier("service1").build(),
            ServiceDependencyDTO.builder().monitoredServiceIdentifier("service2").build()))
        .sources(
            MonitoredServiceDTO.Sources.builder()
                .healthSources(
                    Arrays.asList(createHealthSource(CVMonitoringCategory.ERRORS)).stream().collect(Collectors.toSet()))
                .changeSources(Sets.newHashSet(getHarnessCDChangeSourceDTOBuilder().build()))
                .build());
  }

  public HeatMapBuilder heatMapBuilder() {
    Instant bucketEndTime = clock.instant();
    bucketEndTime = roundDownToMinBoundary(bucketEndTime, 30);
    Instant bucketStartTime = bucketEndTime.minus(24, ChronoUnit.HOURS);
    List<HeatMapRisk> heatMapRisks = new ArrayList<>();

    int index = 0;
    for (Instant startTime = bucketStartTime; startTime.isBefore(bucketEndTime);
         startTime = startTime.plus(30, ChronoUnit.MINUTES)) {
      heatMapRisks.add(HeatMapRisk.builder()
                           .riskScore(-1)
                           .startTime(startTime)
                           .endTime(startTime.plus(30, ChronoUnit.MINUTES))
                           .anomalousLogsCount(index)
                           .anomalousMetricsCount(index + 1)
                           .build());
      index++;
    }

    return HeatMap.builder()
        .accountId(context.getAccountId())
        .projectIdentifier(context.getProjectIdentifier())
        .orgIdentifier(context.getOrgIdentifier())
        .category(CVMonitoringCategory.ERRORS)
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .heatMapResolution(HeatMapResolution.THIRTY_MINUTES)
        .heatMapBucketStartTime(bucketStartTime)
        .heatMapBucketEndTime(bucketEndTime)
        .heatMapRisks(heatMapRisks);
  }

  public HealthSource createHealthSource(CVMonitoringCategory cvMonitoringCategory) {
    return HealthSource.builder()
        .identifier("healthSourceIdentifier")
        .name("health source name")
        .type(MonitoredServiceDataSourceType.APP_DYNAMICS)
        .spec(createHealthSourceSpec(cvMonitoringCategory))
        .build();
  }

  public HealthSourceSpec createHealthSourceSpec(CVMonitoringCategory cvMonitoringCategory) {
    return AppDynamicsHealthSourceSpec.builder()
        .applicationName("appApplicationName")
        .tierName("tier")
        .connectorRef(CONNECTOR_IDENTIFIER)
        .feature("Application Monitoring")
        .metricDefinitions(Collections.emptyList())
        .metricPacks(new HashSet<TimeSeriesMetricPackDTO>() {
          { add(TimeSeriesMetricPackDTO.builder().identifier(cvMonitoringCategory.getDisplayName()).build()); }
        })
        .build();
  }

  public NextGenHealthSourceSpecBuilder createNextGenHealthSourceSpecMetric(
      String identifier, DataSourceType dataSourceType) {
    return NextGenHealthSourceSpec.builder()
        .healthSourceParams(HealthSourceParamsDTO.builder().build())
        .dataSourceType(DataSourceType.SUMOLOGIC_METRICS)
        .queryDefinitions(List.of(
            QueryDefinition.builder()
                .name("sample_metric_name")
                .identifier(identifier)
                .groupName("sample_group")
                .query("metric=Mem_UsedPercent")
                .liveMonitoringEnabled(false)
                .continuousVerificationEnabled(true)
                .sliEnabled(false)
                .riskProfile(RiskProfile.builder()
                                 .riskCategory(RiskCategory.PERFORMANCE_OTHER)
                                 .thresholdTypes(List.of(TimeSeriesThresholdType.ACT_WHEN_LOWER))
                                 .build())
                .queryParams(QueryParamsDTO.builder().serviceInstanceField("_sourceHost").build())
                .metricThresholds(List.of(
                    io.harness.cvng.core.beans.monitoredService.MetricThreshold.builder()
                        .metricName("sample_metric_name")
                        .metricIdentifier("sample_identifier")
                        .metricType("string")
                        .groupName("sample_group")
                        .type(MetricThresholdActionType.IGNORE)
                        .spec(IgnoreMetricThresholdSpec.builder().build())
                        .criteria(io.harness.cvng.core.beans.monitoredService.MetricThreshold.MetricThresholdCriteria
                                      .builder()
                                      .type(MetricThresholdCriteriaType.ABSOLUTE)
                                      .spec(io.harness.cvng.core.beans.monitoredService.MetricThreshold
                                                .MetricThresholdCriteria.MetricThresholdCriteriaSpec.builder()
                                                .greaterThan(0d)
                                                .lessThan(0d)
                                                .build())
                                      .build())
                        .build()))
                .build()))
        .connectorRef("account.sumologic_try_2");
  }

  public NextGenHealthSourceSpecBuilder createNextGenHealthSourceSpecLogs(
      String identifier, DataSourceType dataSourceType) {
    return NextGenHealthSourceSpec.builder()
        .healthSourceParams(HealthSourceParamsDTO.builder().build())
        .dataSourceType(dataSourceType)
        .queryDefinitions(List.of(QueryDefinition.builder()
                                      .name("sample_log_name")
                                      .identifier(identifier)
                                      .groupName("default_group")
                                      .query("_sourceCategory=windows/performance")
                                      .queryParams(QueryParamsDTO.builder().serviceInstanceField("_sourceHost").build())
                                      .build()))
        .connectorRef("account.sumologic_try_2");
  }

  public CVNGStepInfoBuilder cvngStepInfoBuilder() {
    return CVNGStepInfo.builder()
        .type("LoadTest")
        .spec(TestVerificationJobSpec.builder()
                  .duration(ParameterField.createValueField("5m"))
                  .deploymentTag(ParameterField.createValueField("build#1"))
                  .sensitivity(ParameterField.createValueField("Low"))
                  .build());
  }

  public AppDynamicsCVConfigBuilder appDynamicsCVConfigBuilder() {
    return AppDynamicsCVConfig.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .identifier(context.getMonitoredServiceIdentifier() + "/" + generateUuid())
        .monitoringSourceName(generateUuid())
        .metricPack(
            MetricPack.builder()
                .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                .metrics(Set.of(
                    MetricPack.MetricDefinition.builder()
                        .identifier("identifier")
                        .type(TimeSeriesMetricType.OTHER)
                        .name("name")
                        .thresholds(Arrays.asList(TimeSeriesThreshold.builder()
                                                      .uuid("thresholdId")
                                                      .metricPackIdentifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                                                      .metricType(TimeSeriesMetricType.OTHER)
                                                      .metricGroupName("*")
                                                      .metricType(TimeSeriesMetricType.OTHER)
                                                      .metricIdentifier("identifier")
                                                      .deviationType(DeviationType.HIGHER_IS_RISKY)
                                                      .criteria(TimeSeriesThresholdCriteria.builder().build())
                                                      .action(IGNORE)
                                                      .build()))
                        .build(),
                    MetricPack.MetricDefinition.builder()
                        .identifier("zero metric identifier")
                        .type(TimeSeriesMetricType.OTHER)
                        .name("zero metric")
                        .thresholds(Arrays.asList(TimeSeriesThreshold.builder()
                                                      .uuid("thresholdId")
                                                      .metricPackIdentifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                                                      .metricType(TimeSeriesMetricType.OTHER)
                                                      .metricGroupName("*")
                                                      .metricType(TimeSeriesMetricType.OTHER)
                                                      .metricIdentifier("zero metric identifier")
                                                      .deviationType(DeviationType.HIGHER_IS_RISKY)
                                                      .criteria(TimeSeriesThresholdCriteria.builder().build())
                                                      .action(IGNORE)
                                                      .build()))
                        .build()))
                .dataCollectionDsl("dsl")
                .build())
        .metricInfos(
            Arrays.asList(AppDynamicsCVConfig.MetricInfo.builder().identifier("identifier").metricName("name").build(),
                AppDynamicsCVConfig.MetricInfo.builder()
                    .identifier("zero metric identifier")
                    .metricName("zero metric")
                    .build()))
        .applicationName(generateUuid())
        .tierName("tier-name")
        .connectorIdentifier("AppDynamics Connector")
        .category(CVMonitoringCategory.PERFORMANCE)
        .verificationType(VerificationType.TIME_SERIES)
        .enabled(true)
        .productName(generateUuid());
  }

  public StackdriverLogCVConfigBuilder stackdriverLogCVConfigBuilder() {
    return StackdriverLogCVConfig.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .queryName(randomAlphabetic(10))
        .query(randomAlphabetic(10))
        .messageIdentifier(randomAlphabetic(10))
        .serviceInstanceIdentifier(randomAlphabetic(10))
        .identifier(generateUuid())
        .monitoringSourceName(generateUuid())
        .connectorIdentifier("StackdriverLog Connector")
        .category(CVMonitoringCategory.ERRORS)
        .productName(generateUuid());
  }

  public DatadogLogCVConfigBuilder datadogLogCVConfigBuilder() {
    return DatadogLogCVConfig.builder()
        .uuid(UUID.randomUUID().toString())
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .queryName(randomAlphabetic(10))
        .query(randomAlphabetic(10))
        .serviceInstanceIdentifier(randomAlphabetic(10))
        .identifier(generateUuid())
        .monitoringSourceName(generateUuid())
        .connectorIdentifier("DatadogLogConnector")
        .category(CVMonitoringCategory.PERFORMANCE)
        .productName(generateUuid());
  }

  public NewRelicCVConfigBuilder newRelicCVConfigBuilder() {
    return NewRelicCVConfig.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .identifier(context.getMonitoredServiceIdentifier() + "/" + generateUuid())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier());
  }

  public DynatraceCVConfigBuilder dynatraceCVConfigBuilder() {
    return DynatraceCVConfig.builder()
        .groupName("group")
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .connectorIdentifier("DynatraceConnector")
        .identifier(context.getMonitoredServiceIdentifier() + "/healthSourceIdentifier")
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier());
  }

  public PrometheusCVConfigBuilder prometheusCVConfigBuilder() {
    return PrometheusCVConfig.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .connectorIdentifier("connectorRef")
        .identifier(context.getMonitoredServiceIdentifier() + "/" + generateUuid())
        .category(CVMonitoringCategory.PERFORMANCE);
  }

  public AwsPrometheusCVConfigBuilder awsPrometheusCVConfigBuilder() {
    return AwsPrometheusCVConfig.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .connectorIdentifier("connectorRef")
        .identifier(context.getMonitoredServiceIdentifier() + "/" + generateUuid())
        .category(CVMonitoringCategory.PERFORMANCE)
        .region("us-east-1")
        .workspaceId("ws-bd297196-b5ca-48c5-9857-972fe759354f");
  }

  public SplunkMetricCVConfigBuilder splunkMetricCVConfigBuilder() {
    return SplunkMetricCVConfig.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .connectorIdentifier("connectorRef")
        .identifier(context.getMonitoredServiceIdentifier() + "/" + generateUuid())
        .category(CVMonitoringCategory.PERFORMANCE);
  }

  public NextGenMetricCVConfigBuilder nextGenMetricCVConfigBuilder(DataSourceType dataSourceType) {
    return NextGenMetricCVConfig.builder()
        .accountId(context.getAccountId())
        .dataSourceType(dataSourceType)
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .connectorIdentifier("connectorRef")
        .identifier(context.getMonitoredServiceIdentifier() + "/" + generateUuid());
  }

  public NextGenLogCVConfigBuilder nextGenLogCVConfigBuilder(
      DataSourceType dataSourceType, String groupName, String queryIdentifier) {
    return NextGenLogCVConfig.builder()
        .accountId(context.getAccountId())
        .dataSourceType(dataSourceType)
        .groupName(groupName)
        .queryIdentifier(queryIdentifier)
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .queryParams(QueryParams.builder().serviceInstanceField("hostname").build())
        .enabled(true)
        .category(CVMonitoringCategory.ERRORS)
        .connectorIdentifier("connectorRef")
        .productName(generateUuid())
        .createdAt(clock.millis());
  }

  public ErrorTrackingCVConfigBuilder errorTrackingCVConfigBuilder() {
    return ErrorTrackingCVConfig.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .serviceIdentifier(context.getServiceIdentifier())
        .envIdentifier(context.getEnvIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .queryName(randomAlphabetic(10))
        .query(randomAlphabetic(10))
        .identifier(generateUuid())
        .monitoringSourceName(generateUuid())
        .connectorIdentifier("Error Tracking Connector")
        .category(CVMonitoringCategory.ERRORS)
        .identifier(context.getMonitoredServiceIdentifier() + "/" + generateUuid())
        .productName(generateUuid());
  }

  public StackdriverCVConfigBuilder stackdriverMetricCVConfigBuilder() {
    return StackdriverCVConfig.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .connectorIdentifier("connectorRef")
        .dashboardName("dashboardName")
        .identifier(context.getMonitoredServiceIdentifier() + "/" + generateUuid())
        .category(CVMonitoringCategory.PERFORMANCE);
  }

  public DatadogMetricCVConfigBuilder datadogMetricCVConfigBuilder() {
    return DatadogMetricCVConfig.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .connectorIdentifier("connectorRef")
        .dashboardId("dashboardId")
        .dashboardName("dashboardName")
        .identifier(context.getMonitoredServiceIdentifier() + "/" + generateUuid())
        .category(CVMonitoringCategory.PERFORMANCE);
  }

  public SplunkCVConfigBuilder splunkCVConfigBuilder() {
    return SplunkCVConfig.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .serviceInstanceIdentifier(randomAlphabetic(10))
        .createdAt(clock.millis())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .queryName(randomAlphabetic(10))
        .query(randomAlphabetic(10))
        .serviceInstanceIdentifier(randomAlphabetic(10))
        .identifier(context.getMonitoredServiceIdentifier() + "/healthSourceIdentifier")
        .monitoringSourceName(generateUuid())
        .connectorIdentifier("Splunk Connector")
        .category(CVMonitoringCategory.ERRORS)
        .enabled(true)
        .productName(generateUuid());
  }

  public CloudWatchMetricCVConfigBuilder cloudWatchMetricCVConfigBuilder() {
    return CloudWatchMetricCVConfig.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .identifier(context.getMonitoredServiceIdentifier() + "/" + generateUuid())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier());
  }

  public ELKCVConfigBuilder elkCVConfigBuilder() {
    return ELKCVConfig.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .identifier(context.getMonitoredServiceIdentifier() + "/" + generateUuid())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .index("*")
        .timeStampFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .messageIdentifier("message")
        .serviceInstanceIdentifier("hostname")
        .timeStampIdentifier("@timestamp")
        .connectorIdentifier("connectorRef");
  }

  public CustomHealthSourceMetricSpec customHealthMetricSourceSpecBuilder(String metricValueJSONPath,
      String timestampJsonPath, String serviceInstanceJsonPath, String groupName, String metricName, String identifier,
      HealthSourceQueryType queryType, CVMonitoringCategory monitoringCategory, boolean isDeploymentEnabled,
      boolean isLiveMonitoringEnabled, boolean isSliEnabled) {
    MetricResponseMapping responseMapping = MetricResponseMapping.builder()
                                                .metricValueJsonPath(metricValueJSONPath)
                                                .timestampJsonPath(timestampJsonPath)
                                                .serviceInstanceJsonPath(serviceInstanceJsonPath)
                                                .build();

    CustomHealthMetricDefinition metricDefinition =
        CustomHealthMetricDefinition.builder()
            .groupName(groupName)
            .metricName(metricName)
            .queryType(queryType)
            .metricResponseMapping(responseMapping)
            .requestDefinition(CustomHealthRequestDefinition.builder().method(CustomHealthMethod.GET).build())
            .identifier(identifier)
            .analysis(
                HealthSourceMetricDefinition.AnalysisDTO.builder()
                    .deploymentVerification(HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO.builder()
                                                .enabled(isDeploymentEnabled)
                                                .build())
                    .liveMonitoring(HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO.builder()
                                        .enabled(isLiveMonitoringEnabled)
                                        .build())
                    .build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().enabled(isSliEnabled).build())
            .riskProfile(RiskProfile.builder().category(CVMonitoringCategory.PERFORMANCE).build())
            .build();

    List<CustomHealthMetricDefinition> customHealthSourceSpecs = new ArrayList<>();
    customHealthSourceSpecs.add(metricDefinition);
    return CustomHealthSourceMetricSpec.builder().metricDefinitions(customHealthSourceSpecs).build();
  }

  public CustomHealthMetricCVConfig customHealthMetricCVConfigBuilder(String metricName, boolean isDeploymentEnabled,
      boolean isLiveMonitoringEnabled, boolean isSliEnabled, MetricResponseMapping responseMapping, String group,
      HealthSourceQueryType queryType, CustomHealthMethod method, CVMonitoringCategory category, String requestBody) {
    CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition metricDefinition =
        CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition.builder()
            .metricName(metricName)
            .sli(AnalysisInfo.SLI.builder().enabled(isSliEnabled).build())
            .deploymentVerification(AnalysisInfo.DeploymentVerification.builder().enabled(isDeploymentEnabled).build())
            .liveMonitoring(AnalysisInfo.LiveMonitoring.builder().enabled(isLiveMonitoringEnabled).build())
            .metricResponseMapping(responseMapping)
            .requestDefinition(CustomHealthRequestDefinition.builder().method(method).requestBody(requestBody).build())
            .build();

    return CustomHealthMetricCVConfig.builder()
        .metricDefinitions(new ArrayList<CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition>() {
          { add(metricDefinition); }
        })
        .groupName(group)
        .queryType(queryType)
        .category(category)
        .build();
  }

  public CustomHealthMetricCVConfig customHealthMetricCVConfigBuilderForAppd(String metricName,
      boolean isDeploymentEnabled, boolean isLiveMonitoringEnabled, boolean isSliEnabled,
      MetricResponseMapping responseMapping, String group, HealthSourceQueryType queryType, CustomHealthMethod method,
      CVMonitoringCategory category, String requestBody) {
    CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition metricDefinition =
        CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition.builder()
            .metricName(metricName)
            .identifier(metricName)
            .sli(AnalysisInfo.SLI.builder().enabled(isSliEnabled).build())
            .deploymentVerification(AnalysisInfo.DeploymentVerification.builder().enabled(isDeploymentEnabled).build())
            .liveMonitoring(AnalysisInfo.LiveMonitoring.builder().enabled(isLiveMonitoringEnabled).build())
            .metricResponseMapping(responseMapping)
            .requestDefinition(
                CustomHealthRequestDefinition.builder()
                    .startTimeInfo(TimestampInfo.builder()
                                       .placeholder("start_time")
                                       .timestampFormat(TimestampInfo.TimestampFormat.MILLISECONDS)
                                       .build())
                    .endTimeInfo(TimestampInfo.builder()
                                     .placeholder("end_time")
                                     .timestampFormat(TimestampInfo.TimestampFormat.MILLISECONDS)
                                     .build())
                    .method(method)
                    .urlPath(
                        "rest/applications/cv-app/metric-data?metric-path=Overall Application Performance|docker-tier|Individual Nodes|*|Errors per Minute&time-range-type=BETWEEN_TIMES&start-time=start_time&end-time=end_time&rollup=false&output=json")
                    .requestBody(requestBody)
                    .build())
            .build();

    return CustomHealthMetricCVConfig.builder()
        .metricDefinitions(new ArrayList<CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition>() {
          { add(metricDefinition); }
        })
        .groupName(group)
        .queryType(queryType)
        .category(category)
        .build();
  }

  public CustomHealthMetricCVConfig customHealthMetricCVConfigBuilderForELK(String metricName,
      boolean isDeploymentEnabled, boolean isLiveMonitoringEnabled, boolean isSliEnabled,
      MetricResponseMapping responseMapping, String group, HealthSourceQueryType queryType, CustomHealthMethod method,
      CVMonitoringCategory category, String requestBody, String index) {
    CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition metricDefinition =
        CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition.builder()
            .metricName(metricName)
            .identifier(metricName)
            .sli(AnalysisInfo.SLI.builder().enabled(isSliEnabled).build())
            .deploymentVerification(AnalysisInfo.DeploymentVerification.builder().enabled(isDeploymentEnabled).build())
            .liveMonitoring(AnalysisInfo.LiveMonitoring.builder().enabled(isLiveMonitoringEnabled).build())
            .metricResponseMapping(responseMapping)
            .requestDefinition(CustomHealthRequestDefinition.builder()
                                   .startTimeInfo(TimestampInfo.builder()
                                                      .placeholder("start_time")
                                                      .timestampFormat(TimestampInfo.TimestampFormat.MILLISECONDS)
                                                      .build())
                                   .endTimeInfo(TimestampInfo.builder()
                                                    .placeholder("end_time")
                                                    .timestampFormat(TimestampInfo.TimestampFormat.MILLISECONDS)
                                                    .build())
                                   .method(method)
                                   .urlPath(index + "/_search")
                                   .requestBody(requestBody)
                                   .build())
            .build();

    return CustomHealthMetricCVConfig.builder()
        .metricDefinitions(new ArrayList<CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition>() {
          { add(metricDefinition); }
        })
        .groupName(group)
        .queryType(queryType)
        .category(category)
        .build();
  }

  public CustomHealthSourceLogSpec customHealthLogSourceSpecBuilder(
      String queryName, String queryValueJSONPath, String urlPath, String timestampValueJSONPath) {
    List<CustomHealthLogDefinition> customHealthLogDefinitions = new ArrayList<>();
    CustomHealthLogDefinition customHealthSpecLogDefinition =
        CustomHealthLogDefinition.builder()
            .logMessageJsonPath(queryValueJSONPath)
            .queryName(queryName)
            .requestDefinition(CustomHealthRequestDefinition.builder()
                                   .method(CustomHealthMethod.GET)
                                   .startTimeInfo(TimestampInfo.builder().build())
                                   .endTimeInfo(TimestampInfo.builder().build())
                                   .urlPath(urlPath)
                                   .build())
            .timestampJsonPath(timestampValueJSONPath)
            .build();
    customHealthLogDefinitions.add(customHealthSpecLogDefinition);
    return CustomHealthSourceLogSpec.builder().logDefinitions(customHealthLogDefinitions).build();
  }

  public CustomHealthLogCVConfig customHealthLogCVConfigBuilder(String logMessageJsonPath, String timestampJsonPath,
      String query, String queryName, String urlPath, String requestBody, CustomHealthMethod method) {
    return CustomHealthLogCVConfig.builder()
        .logMessageJsonPath(logMessageJsonPath)
        .timestampJsonPath(timestampJsonPath)
        .query(query)
        .queryName(queryName)
        .requestDefinition(CustomHealthRequestDefinition.builder()
                               .method(method)
                               .urlPath(urlPath)
                               .requestBody(requestBody)
                               .endTimeInfo(TimestampInfo.builder().build())
                               .startTimeInfo(TimestampInfo.builder().build())
                               .build())
        .build();
  }

  public HarnessCDChangeSourceBuilder getHarnessCDChangeSourceBuilder() {
    return HarnessCDChangeSource.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .enabled(true)
        .type(ChangeSourceType.HARNESS_CD);
  }

  public PagerDutyChangeSourceBuilder getPagerDutyChangeSourceBuilder() {
    return PagerDutyChangeSource.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .enabled(true)
        .connectorIdentifier(randomAlphabetic(20))
        .pagerDutyServiceId(randomAlphabetic(20))
        .type(ChangeSourceType.PAGER_DUTY);
  }

  public KubernetesChangeSourceBuilder getKubernetesChangeSourceBuilder() {
    return KubernetesChangeSource.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .enabled(true)
        .type(ChangeSourceType.KUBERNETES)
        .connectorIdentifier(generateUuid())
        .identifier(generateUuid());
  }

  public HarnessCDCurrentGenChangeSourceBuilder getHarnessCDCurrentGenChangeSourceBuilder() {
    return HarnessCDCurrentGenChangeSource.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .enabled(true)
        .harnessApplicationId(randomAlphabetic(20))
        .harnessServiceId(randomAlphabetic(20))
        .harnessEnvironmentId(randomAlphabetic(20))
        .type(ChangeSourceType.HARNESS_CD_CURRENT_GEN);
  }

  public CustomChangeSourceBuilder getCustomChangeSourceBuilder(ChangeSourceType customChangeSourceType) {
    return CustomChangeSource.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .enabled(true)
        .name(randomAlphabetic(20))
        .type(customChangeSourceType)
        .identifier("customIdentifier");
  }

  public ChangeSourceDTOBuilder getHarnessCDChangeSourceDTOBuilder() {
    return getChangeSourceDTOBuilder(ChangeSourceType.HARNESS_CD).spec(new HarnessCDChangeSourceSpec());
  }

  public ChangeSourceDTOBuilder getPagerDutyChangeSourceDTOBuilder() {
    return getChangeSourceDTOBuilder(ChangeSourceType.PAGER_DUTY)
        .spec(PagerDutyChangeSourceSpec.builder()
                  .connectorRef(randomAlphabetic(20))
                  .pagerDutyServiceId(randomAlphabetic(20))
                  .build());
  }

  public ChangeSourceDTOBuilder getKubernetesChangeSourceDTOBuilder() {
    return getChangeSourceDTOBuilder(ChangeSourceType.KUBERNETES)
        .spec(KubernetesChangeSourceSpec.builder().connectorRef(generateUuid()).build());
  }

  public ChangeSourceDTOBuilder getHarnessCDCurrentGenChangeSourceDTOBuilder() {
    return getChangeSourceDTOBuilder(ChangeSourceType.HARNESS_CD_CURRENT_GEN)
        .spec(HarnessCDCurrentGenChangeSourceSpec.builder()
                  .harnessApplicationId(randomAlphabetic(20))
                  .harnessServiceId(randomAlphabetic(20))
                  .harnessEnvironmentId(randomAlphabetic(20))
                  .build());
  }

  public ChangeSourceDTOBuilder getCustomChangeSourceDTOBuilder(ChangeSourceType customChangeSourceType) {
    return getChangeSourceDTOBuilder(customChangeSourceType)
        .spec(CustomChangeSourceSpec.builder()
                  .name(randomAlphabetic(20))
                  .type(customChangeSourceType.getChangeCategory())
                  .build());
  }

  public DeploymentActivityBuilder getDeploymentActivityBuilder() {
    return DeploymentActivity.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceParams().getMonitoredServiceIdentifier())
        .eventTime(clock.instant())
        .changeSourceIdentifier("changeSourceID")
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .type(ChangeSourceType.HARNESS_CD.getActivityType())
        .stageStepId("stageStepId")
        .verificationStartTime(clock.millis())
        .deploymentTag("deploymentTag")
        .stageId("stageId")
        .pipelineId("pipelineId")
        .planExecutionId(generateUuid())
        .artifactType("artifactType")
        .artifactTag("artifactTag")
        .activityName(generateUuid())
        .deploymentStatus(generateUuid())
        .verificationJobInstanceIds(Arrays.asList(generateUuid()))
        .activityEndTime(clock.instant())
        .activityStartTime(clock.instant());
  }

  public InternalChangeActivityBuilder<?, ?> getInternalChangeActivity_FFBuilder() {
    return InternalChangeActivity.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceParams().getMonitoredServiceIdentifier())
        .eventTime(clock.instant())
        .changeSourceIdentifier("changeSourceID")
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .type(ActivityType.FEATURE_FLAG)
        .activityType(ActivityType.FEATURE_FLAG)
        .updatedBy("user")
        .internalChangeEvent(
            InternalChangeEvent.builder()
                .changeEventDetailsLink(
                    DeepLink.builder().action(DeepLink.Action.FETCH_DIFF_DATA).url("changeEventDetails").build())
                .internalLinkToEntity(
                    DeepLink.builder().action(DeepLink.Action.REDIRECT_URL).url("internalUrl").build())
                .eventDescriptions(Arrays.asList("eventDesc1", "eventDesc2"))
                .build())
        .eventEndTime(clock.instant().toEpochMilli());
  }

  public InternalChangeActivityBuilder getInternalChangeActivity_CEBuilder() {
    return InternalChangeActivity.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceParams().getMonitoredServiceIdentifier())
        .eventTime(clock.instant())
        .changeSourceIdentifier("changeSourceID")
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .type(ActivityType.CHAOS_EXPERIMENT)
        .activityType(ActivityType.CHAOS_EXPERIMENT)
        .updatedBy("user")
        .internalChangeEvent(
            InternalChangeEvent.builder()
                .changeEventDetailsLink(
                    DeepLink.builder().action(DeepLink.Action.FETCH_DIFF_DATA).url("changeEventDetails").build())
                .internalLinkToEntity(
                    DeepLink.builder().action(DeepLink.Action.REDIRECT_URL).url("internalUrl").build())
                .eventDescriptions(Arrays.asList("eventDesc1", "eventDesc2"))
                .build())
        .activityStartTime(clock.instant())
        .activityEndTime(clock.instant())
        .eventEndTime(clock.instant().toEpochMilli());
  }

  public CustomChangeActivityBuilder getCustomChangeActivity(ChangeSourceType customChangeSourceType) {
    return CustomChangeActivity.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceParams().getMonitoredServiceIdentifier())
        .eventTime(clock.instant())
        .changeSourceIdentifier("changeSourceID")
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .type(customChangeSourceType.getActivityType())
        .activityType(customChangeSourceType.getActivityType())
        .user("user")
        .customChangeEvent(CustomChangeEvent.builder()
                               .description("description")
                               .changeEventDetailsLink("changeEventDetailsLink")
                               .externalLinkToEntity("externalLinkToEntity")
                               .build());
  }

  public HarnessCDCurrentGenActivityBuilder getHarnessCDCurrentGenActivityBuilder() {
    return HarnessCDCurrentGenActivity.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceParams().getMonitoredServiceIdentifier())
        .eventTime(clock.instant())
        .changeSourceIdentifier("changeSourceID")
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .type(ChangeSourceType.HARNESS_CD.getActivityType())
        .artifactType("artifactType")
        .artifactName("artifactName")
        .workflowEndTime(clock.instant())
        .workflowStartTime(clock.instant())
        .workflowId("workflowId")
        .workflowExecutionId(generateUuid())
        .activityName(generateUuid())
        .activityEndTime(clock.instant())
        .appId(generateUuid())
        .serviceId(generateUuid())
        .environmentId(generateUuid())
        .name(generateUuid())
        .activityStartTime(clock.instant());
  }

  public KubernetesClusterActivityBuilder getKubernetesClusterActivityBuilder() {
    return KubernetesClusterActivity.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .eventTime(clock.instant())
        .changeSourceIdentifier("changeSourceID")
        .type(ChangeSourceType.KUBERNETES.getActivityType())
        .oldYaml("oldYaml")
        .newYaml("newYaml")
        .resourceType(KubernetesResourceType.ReplicaSet)
        .action(Action.Update)
        .reason("replica set update")
        .namespace("cv")
        .workload("workload");
  }

  public PagerDutyActivityBuilder getPagerDutyActivityBuilder() {
    return PagerDutyActivity.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .eventTime(clock.instant())
        .changeSourceIdentifier("changeSourceID")
        .type(ChangeSourceType.PAGER_DUTY.getActivityType())
        .pagerDutyUrl("https://myurl.com/pagerduty/token")
        .eventId("eventId")
        .activityName("New pager duty incident")
        .status(generateUuid())
        .htmlUrl(generateUuid())
        .pagerDutyUrl(generateUuid())
        .activityStartTime(clock.instant());
  }

  public KubernetesClusterActivityBuilder getKubernetesClusterActivityForAppServiceBuilder() {
    return KubernetesClusterActivity.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .eventTime(clock.instant())
        .changeSourceIdentifier("changeSourceID")
        .type(ChangeSourceType.KUBERNETES.getActivityType())
        .activityStartTime(clock.instant())
        .activityName("K8 Activity")
        .resourceType(KubernetesResourceType.ConfigMap)
        .action(Action.Add)
        .resourceVersion("resource-version")
        .relatedAppServices(Arrays.asList(
            RelatedAppMonitoredService.builder().monitoredServiceIdentifier("dependent_service").build()));
  }

  public ChangeEventDTOBuilder harnessCDChangeEventDTOBuilder() {
    return getChangeEventDTOBuilder()
        .type(ChangeSourceType.HARNESS_CD)
        .metadata(HarnessCDEventMetadata.builder()
                      .stageStepId("stage")
                      .planExecutionId("executionId")
                      .deploymentEndTime(Instant.now().toEpochMilli())
                      .deploymentStartTime(Instant.now().toEpochMilli())
                      .stageStepId("stageStepId")
                      .stageId("stageId")
                      .pipelineId("pipelineId")
                      .planExecutionId("executionId")
                      .artifactType("artifactType")
                      .artifactTag("artifactTag")
                      .status("status")
                      .build());
  }

  public ChangeEventDTOBuilder harnessCDCurrentGenChangeEventDTOBuilder() {
    return getChangeEventDTOBuilder()
        .type(ChangeSourceType.HARNESS_CD_CURRENT_GEN)
        .metadata(HarnessCDCurrentGenEventMetadata.builder()
                      .artifactType("artifactType")
                      .artifactName("artifactName")
                      .workflowEndTime(clock.millis())
                      .workflowStartTime(clock.millis())
                      .workflowId("workflowId")
                      .workflowExecutionId("workflowExecutionId")
                      .build());
  }

  public ChangeEventDTOBuilder getKubernetesClusterChangeEventDTOBuilder() {
    return getChangeEventDTOBuilder()
        .type(ChangeSourceType.KUBERNETES)
        .metadata(KubernetesChangeEventMetadata.builder()
                      .oldYaml("oldYaml")
                      .newYaml("newYaml")
                      .resourceType(KubernetesResourceType.ReplicaSet)
                      .action(Action.Update)
                      .reason("replica set update")
                      .namespace("cv")
                      .workload("workload")
                      .timestamp(Instant.now())
                      .build());
  }

  public ChangeEventDTOBuilder getPagerDutyChangeEventDTOBuilder() {
    return getChangeEventDTOBuilder()
        .type(ChangeSourceType.PAGER_DUTY)
        .metadata(PagerDutyEventMetaData.builder()
                      .eventId("eventId")
                      .pagerDutyUrl("https://myurl.com/pagerduty/token")
                      .title("New pager duty incident")
                      .build());
  }

  public ChangeEventDTOBuilder getInternalChangeEventDTO_FFBuilder() {
    return getChangeEventDTOBuilder()
        .type(ChangeSourceType.HARNESS_FF)
        .metadata(InternalChangeEventMetaData.builder()
                      .activityType(ActivityType.FEATURE_FLAG)
                      .updatedBy("user")
                      .eventStartTime(1000l)
                      .internalChangeEvent(
                          InternalChangeEvent.builder()
                              .changeEventDetailsLink(DeepLink.builder()
                                                          .action(DeepLink.Action.FETCH_DIFF_DATA)
                                                          .url("changeEventDetails")
                                                          .build())
                              .internalLinkToEntity(
                                  DeepLink.builder().action(DeepLink.Action.REDIRECT_URL).url("internalUrl").build())
                              .eventDescriptions(Arrays.asList("eventDesc1", "eventDesc2"))
                              .build())
                      .build());
  }

  public ChangeEventDTOBuilder getCustomChangeEventBuilder(ChangeSourceType customChangeSourceType) {
    return getChangeEventDTOBuilder()
        .type(customChangeSourceType)
        .metadata(CustomChangeEventMetadata.builder()
                      .user("user")
                      .startTime(1000l)
                      .endTime(2000l)
                      .type(customChangeSourceType)
                      .customChangeEvent(CustomChangeEvent.builder()
                                             .description("description")
                                             .changeEventDetailsLink("changeEventDetailsLink")
                                             .externalLinkToEntity("externalLinkToEntity")
                                             .build())
                      .build());
  }

  public ChangeEventDTOBuilder getChangeEventDTOBuilder() {
    return ChangeEventDTO.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .serviceIdentifier(context.getServiceIdentifier())
        .envIdentifier(context.getEnvIdentifier())
        .eventTime(Instant.EPOCH.getEpochSecond())
        .changeSourceIdentifier("changeSourceID")
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier());
  }

  public DeploymentEventDTO.Builder getDeploymentEventDTOBuilder() {
    return DeploymentEventDTO.newBuilder()
        .setAccountId(context.getAccountId())
        .setOrgIdentifier(context.getOrgIdentifier())
        .setProjectIdentifier(context.getProjectIdentifier())
        .setServiceIdentifier(context.getServiceIdentifier())
        .setEnvironmentIdentifier(context.getEnvIdentifier())
        .setDeploymentStartTime(Instant.now().toEpochMilli())
        .setDeploymentEndTime(Instant.now().toEpochMilli())
        .setDeploymentStatus("SUCCESS")
        .setExecutionDetails(ExecutionDetails.newBuilder()
                                 .setStageId("stageId")
                                 .setPipelineId("pipelineId")
                                 .setPlanExecutionId("planExecutionId")
                                 .setStageSetupId("stageStepId")
                                 .build())
        .setArtifactDetails(
            ArtifactDetails.newBuilder().setArtifactTag("artifactTag").setArtifactType("artifactType").build());
  }

  public InternalChangeEventDTO.Builder getInternalChangeEventWithMultipleServiceEnvBuilder() {
    return getInternalChangeEventBuilder().addServiceIdentifier("Service2").addEnvironmentIdentifier("Env2");
  }

  public InternalChangeEventDTO.Builder getInternalChangeEventBuilder() {
    return InternalChangeEventDTO.newBuilder()
        .setAccountId(context.getAccountId())
        .setOrgIdentifier(context.getOrgIdentifier())
        .setProjectIdentifier(context.getProjectIdentifier())
        .addServiceIdentifier("Service1")
        .addEnvironmentIdentifier("Env1")
        .setEventDetails(EventDetails.newBuilder()
                             .setUser("user")
                             .addEventDetails("test event detail")
                             .setInternalLinkToEntity("testInternalUrl")
                             .setChangeEventDetailsLink("testChangeEventDetailsLink")
                             .build())
        .setType("FEATURE_FLAG")
        .setExecutionTime(1000l);
  }

  private ChangeSourceDTOBuilder getChangeSourceDTOBuilder(ChangeSourceType changeSourceType) {
    return ChangeSourceDTO.builder()
        .identifier(generateUuid())
        .name(generateUuid())
        .enabled(true)
        .type(changeSourceType);
  }

  public ServiceLevelObjectiveV2DTOBuilder getSimpleServiceLevelObjectiveV2DTOBuilder() {
    return ServiceLevelObjectiveV2DTO.builder()
        .type(ServiceLevelObjectiveType.SIMPLE)
        .projectIdentifier(context.getProjectIdentifier())
        .orgIdentifier(context.getOrgIdentifier())
        .identifier("sloIdentifier")
        .name("sloName")
        .tags(new HashMap<String, String>() {
          {
            put("tag1", "value1");
            put("tag2", "");
          }
        })
        .description("slo description")
        .sloTarget(SLOTargetDTO.builder()
                       .type(SLOTargetType.ROLLING)
                       .sloTargetPercentage(80.0)
                       .spec(RollingSLOTargetSpec.builder().periodLength("30d").build())
                       .build())
        .spec(SimpleServiceLevelObjectiveSpec.builder()
                  .serviceLevelIndicators(Collections.singletonList(getServiceLevelIndicatorDTOBuilder()))
                  .healthSourceRef("healthSourceIdentifier")
                  .monitoredServiceRef(context.serviceIdentifier + "_" + context.getEnvIdentifier())
                  .serviceLevelIndicatorType(ServiceLevelIndicatorType.AVAILABILITY)
                  .build())
        .notificationRuleRefs(Collections.emptyList())
        .userJourneyRefs(Collections.singletonList("userJourney"));
  }

  public ServiceLevelObjectiveV2DTOBuilder getSimpleCalendarServiceLevelObjectiveV2DTOBuilder() {
    return ServiceLevelObjectiveV2DTO.builder()
        .type(ServiceLevelObjectiveType.SIMPLE)
        .projectIdentifier(context.getProjectIdentifier())
        .orgIdentifier(context.getOrgIdentifier())
        .identifier("sloIdentifier")
        .name("sloName")
        .tags(new HashMap<String, String>() {
          {
            put("tag1", "value1");
            put("tag2", "");
          }
        })
        .description("slo description")
        .sloTarget(SLOTargetDTO.builder()
                       .type(SLOTargetType.CALENDER)
                       .sloTargetPercentage(80.0)
                       .spec(CalenderSLOTargetSpec.builder()
                                 .spec(CalenderSLOTargetSpec.MonthlyCalenderSpec.builder().dayOfMonth(2).build())
                                 .build())
                       .build())
        .spec(SimpleServiceLevelObjectiveSpec.builder()
                  .serviceLevelIndicators(Collections.singletonList(getServiceLevelIndicatorDTOBuilder()))
                  .healthSourceRef("healthSourceIdentifier")
                  .monitoredServiceRef(context.serviceIdentifier + "_" + context.getEnvIdentifier())
                  .serviceLevelIndicatorType(ServiceLevelIndicatorType.AVAILABILITY)
                  .build())
        .notificationRuleRefs(Collections.emptyList())
        .userJourneyRefs(Collections.singletonList("userJourney"));
  }

  public ServiceLevelObjectiveV2DTOBuilder getSimpleRequestServiceLevelObjectiveV2DTOBuilder() {
    return ServiceLevelObjectiveV2DTO.builder()
        .type(ServiceLevelObjectiveType.SIMPLE)
        .projectIdentifier(context.getProjectIdentifier())
        .orgIdentifier(context.getOrgIdentifier())
        .identifier("sloIdentifier_request")
        .name("sloName_request")
        .tags(new HashMap<String, String>() {
          {
            put("tag1", "value1");
            put("tag2", "");
          }
        })
        .description("slo description")
        .sloTarget(SLOTargetDTO.builder()
                       .type(SLOTargetType.ROLLING)
                       .sloTargetPercentage(80.0)
                       .spec(RollingSLOTargetSpec.builder().periodLength("30d").build())
                       .build())
        .spec(SimpleServiceLevelObjectiveSpec.builder()
                  .serviceLevelIndicators(Collections.singletonList(getRequestServiceLevelIndicatorDTOBuilder()
                                                                        .name(generateUuid())
                                                                        .identifier(generateUuid())
                                                                        .build()))
                  .healthSourceRef("healthSourceIdentifier")
                  .monitoredServiceRef(context.serviceIdentifier + "_" + context.getEnvIdentifier())
                  .serviceLevelIndicatorType(ServiceLevelIndicatorType.AVAILABILITY)
                  .build())
        .notificationRuleRefs(Collections.emptyList())
        .userJourneyRefs(Collections.singletonList("userJourney"));
  }

  public ServiceLevelObjectiveV2DTOBuilder getCompositeServiceLevelObjectiveV2DTOBuilder() {
    return ServiceLevelObjectiveV2DTO.builder()
        .type(ServiceLevelObjectiveType.COMPOSITE)
        .projectIdentifier(context.getProjectIdentifier())
        .orgIdentifier(context.getOrgIdentifier())
        .identifier("compositeSloIdentifier")
        .name("sloName")
        .tags(new HashMap<String, String>() {
          {
            put("tag1", "value1");
            put("tag2", "");
          }
        })
        .description("slo description")
        .sloTarget(SLOTargetDTO.builder()
                       .type(SLOTargetType.ROLLING)
                       .sloTargetPercentage(80.0)
                       .spec(RollingSLOTargetSpec.builder().periodLength("30d").build())
                       .build())
        .spec(CompositeServiceLevelObjectiveSpec.builder()
                  .serviceLevelObjectivesDetails(Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                                                   .serviceLevelObjectiveRef("uuid1")
                                                                   .weightagePercentage(75.0)
                                                                   .projectIdentifier(context.getProjectIdentifier())
                                                                   .orgIdentifier(context.getOrgIdentifier())
                                                                   .accountId(context.getAccountId())
                                                                   .build(),
                      ServiceLevelObjectiveDetailsDTO.builder()
                          .serviceLevelObjectiveRef("uuid2")
                          .weightagePercentage(25.0)
                          .projectIdentifier(context.getProjectIdentifier())
                          .orgIdentifier(context.getOrgIdentifier())
                          .accountId(context.getAccountId())
                          .build()))
                  .build())
        .userJourneyRefs(Collections.singletonList("userJourney"));
  }

  public SimpleServiceLevelObjectiveBuilder getSimpleServiceLevelObjectiveBuilder() {
    return SimpleServiceLevelObjective.builder()
        .type(ServiceLevelObjectiveType.SIMPLE)
        .accountId(context.getAccountId())
        .projectIdentifier(context.getProjectIdentifier())
        .orgIdentifier(context.getOrgIdentifier())
        .identifier("sloIdentifier")
        .name("sloName")
        .tags(Collections.singletonList(NGTag.builder().key("key").value("value").build()))
        .desc("slo description")
        .target(io.harness.cvng.servicelevelobjective.entities.RollingSLOTarget.builder().periodLengthDays(30).build())
        .sloTargetPercentage(80.0)
        .serviceLevelIndicators(Collections.singletonList("sloIdentifier_metric1"))
        .healthSourceIdentifier("healthSourceIdentifier")
        .monitoredServiceIdentifier(context.serviceIdentifier + "_" + context.getEnvIdentifier())
        .userJourneyIdentifiers(Collections.singletonList("userJourney"));
  }

  public SLOErrorBudgetResetDTOBuilder getSLOErrorBudgetResetDTOBuilder() {
    return SLOErrorBudgetResetDTO.builder()
        .serviceLevelObjectiveIdentifier("slo")
        .errorBudgetIncrementPercentage(10.0)
        .errorBudgetIncrementMinutes(10)
        .remainingErrorBudgetAtReset(100)
        .errorBudgetAtReset(100)
        .reason("reason");
  }

  public UserJourneyDTO getUserJourneyDTOBuilder() {
    return UserJourneyDTO.builder().identifier("userJourney").name("userJourney").build();
  }

  public ServiceLevelIndicatorDTO getServiceLevelIndicatorDTOBuilder() {
    return ServiceLevelIndicatorDTO.builder()
        .type(SLIEvaluationType.WINDOW)
        .spec(WindowBasedServiceLevelIndicatorSpec.builder()
                  .sliMissingDataType(SLIMissingDataType.GOOD)
                  .type(SLIMetricType.RATIO)
                  .spec(RatioSLIMetricSpec.builder()
                            .thresholdType(ThresholdType.GREATER_THAN)
                            .thresholdValue(20.0)
                            .eventType(RatioSLIMetricEventType.GOOD)
                            .metric1("metric1")
                            .metric2("metric2")
                            .build())
                  .build())
        .build();
  }

  public RatioServiceLevelIndicatorBuilder ratioServiceLevelIndicatorBuilder() {
    return RatioServiceLevelIndicator.builder()
        .uuid("ratio_sli")
        .sliMissingDataType(SLIMissingDataType.GOOD)
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .metric1("metric1")
        .metric2("metric2")
        .healthSourceIdentifier("healthSourceIdentifier")
        .monitoredServiceIdentifier("monitoredServiceIdentifier")
        .version(0);
  }

  public RequestServiceLevelIndicatorBuilder requestServiceLevelIndicatorBuilder() {
    return RequestServiceLevelIndicator.builder()
        .sliMissingDataType(SLIMissingDataType.GOOD)
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .eventType(RatioSLIMetricEventType.GOOD)
        .metric1("metric1")
        .metric2("metric2")
        .healthSourceIdentifier("healthSourceIdentifier")
        .monitoredServiceIdentifier("monitoredServiceIdentifier");
  }

  public ServiceLevelIndicatorDTOBuilder getThresholdServiceLevelIndicatorDTOBuilder() {
    return ServiceLevelIndicatorDTO.builder()
        .type(SLIEvaluationType.WINDOW)
        .healthSourceRef("healthSourceIdentifier")
        .spec(WindowBasedServiceLevelIndicatorSpec.builder()
                  .sliMissingDataType(SLIMissingDataType.GOOD)
                  .type(SLIMetricType.THRESHOLD)
                  .spec(ThresholdSLIMetricSpec.builder()
                            .metric1("Calls per Minute")
                            .thresholdValue(500.0)
                            .thresholdType(ThresholdType.GREATER_THAN_EQUAL_TO)
                            .build())
                  .build());
  }

  public ServiceLevelIndicatorDTOBuilder getRatioServiceLevelIndicatorDTOBuilder() {
    return ServiceLevelIndicatorDTO.builder()
        .type(SLIEvaluationType.WINDOW)
        .healthSourceRef("healthSourceIdentifier")
        .spec(WindowBasedServiceLevelIndicatorSpec.builder()
                  .sliMissingDataType(SLIMissingDataType.GOOD)
                  .type(SLIMetricType.RATIO)
                  .spec(RatioSLIMetricSpec.builder()
                            .metric1("Errors per Minute")
                            .metric2("Calls per Minute")
                            .eventType(RatioSLIMetricEventType.GOOD)
                            .thresholdValue(100.0)
                            .thresholdType(ThresholdType.GREATER_THAN_EQUAL_TO)
                            .build())
                  .build());
  }

  public ServiceLevelIndicatorDTOBuilder getRequestServiceLevelIndicatorDTOBuilder() {
    return ServiceLevelIndicatorDTO.builder()
        .name("name")
        .identifier("identifier")
        .type(SLIEvaluationType.REQUEST)
        .spec(RequestBasedServiceLevelIndicatorSpec.builder()
                  .metric1("Errors per Minute")
                  .metric2("Calls per Minute")
                  .eventType(RatioSLIMetricEventType.GOOD)
                  .build());
  }

  private VerificationJob getVerificationJob(List<CVConfig> cvConfigs) {
    TestVerificationJob testVerificationJob = new TestVerificationJob();
    testVerificationJob.setAccountId(context.getAccountId());
    testVerificationJob.setIdentifier("identifier");
    testVerificationJob.setJobName(generateUuid());
    testVerificationJob.setSensitivity(Sensitivity.MEDIUM);
    testVerificationJob.setServiceIdentifier(context.getServiceIdentifier(), false);
    testVerificationJob.setEnvIdentifier(context.getEnvIdentifier(), false);
    testVerificationJob.setBaselineVerificationJobInstanceId(generateUuid());
    testVerificationJob.setDuration(Duration.ofMinutes(5));
    testVerificationJob.setMonitoredServiceIdentifier(context.getMonitoredServiceIdentifier());
    testVerificationJob.setProjectIdentifier(context.getProjectIdentifier());
    testVerificationJob.setOrgIdentifier(context.getOrgIdentifier());
    testVerificationJob.setCvConfigs(cvConfigs);
    return testVerificationJob;
  }

  public VerificationJob getDeploymentVerificationJob() {
    CanaryVerificationJob canaryVerificationJob = new CanaryVerificationJob();
    canaryVerificationJob.setAccountId(context.getAccountId());
    canaryVerificationJob.setIdentifier("identifier");
    canaryVerificationJob.setJobName(generateUuid());
    canaryVerificationJob.setMonitoringSources(Arrays.asList("monitoringIdentifier"));
    canaryVerificationJob.setSensitivity(Sensitivity.MEDIUM);
    canaryVerificationJob.setServiceIdentifier(context.getServiceIdentifier(), false);
    canaryVerificationJob.setEnvIdentifier(context.getEnvIdentifier(), false);
    canaryVerificationJob.setDuration(Duration.ofMinutes(5));
    canaryVerificationJob.setProjectIdentifier(context.getProjectIdentifier());
    canaryVerificationJob.setOrgIdentifier(context.getOrgIdentifier());
    return canaryVerificationJob;
  }

  public CanaryBlueGreenVerificationJobBuilder canaryVerificationJobBuilder() {
    return CanaryVerificationJob.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .identifier("identifier")
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .serviceIdentifier(RuntimeParameter.builder().value(context.getServiceIdentifier()).build())
        .envIdentifier(RuntimeParameter.builder().value(context.getEnvIdentifier()).build())
        .monitoringSources(Collections.singletonList(context.getMonitoredServiceIdentifier() + "/" + generateUuid()))
        .sensitivity(RuntimeParameter.builder().value("High").build())
        .duration(RuntimeParameter.builder().value("10m").build());
  }

  public BlueGreenVerificationJobBuilder blueGreenVerificationJobBuilder() {
    return BlueGreenVerificationJob.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .identifier("identifier")
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .serviceIdentifier(RuntimeParameter.builder().value(context.getServiceIdentifier()).build())
        .envIdentifier(RuntimeParameter.builder().value(context.getEnvIdentifier()).build())
        .monitoringSources(Collections.singletonList(context.getMonitoredServiceIdentifier() + "/" + generateUuid()))
        .sensitivity(RuntimeParameter.builder().value("High").build())
        .trafficSplitPercentage(10)
        .duration(RuntimeParameter.builder().value("10m").build());
  }

  public AutoVerificationJobBuilder autoVerificationJobBuilder() {
    return AutoVerificationJob.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .identifier("identifier")
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .serviceIdentifier(RuntimeParameter.builder().value(context.getServiceIdentifier()).build())
        .envIdentifier(RuntimeParameter.builder().value(context.getEnvIdentifier()).build())
        .monitoringSources(Collections.singletonList(context.getMonitoredServiceIdentifier() + "/" + generateUuid()))
        .sensitivity(RuntimeParameter.builder().value("High").build())
        .trafficSplitPercentage(10)
        .duration(RuntimeParameter.builder().value("10m").build());
  }

  public TestVerificationJobBuilder testVerificationJobBuilder() {
    return TestVerificationJob.builder()
        .accountId(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .identifier("identifier")
        .monitoredServiceIdentifier(context.getMonitoredServiceIdentifier())
        .serviceIdentifier(RuntimeParameter.builder().value(context.getServiceIdentifier()).build())
        .envIdentifier(RuntimeParameter.builder().value(context.getEnvIdentifier()).build())
        .monitoringSources(Collections.singletonList(context.getMonitoredServiceIdentifier() + "/" + generateUuid()))
        .sensitivity(RuntimeParameter.builder().value("Medium").build())
        .baselineVerificationJobInstanceId(generateUuid())
        .duration(RuntimeParameter.builder().value("15m").build());
  }

  public static class BuilderFactoryBuilder {
    public BuilderFactory build() {
      BuilderFactory builder = unsafeBuild();
      if (builder.clock == null) {
        builder.setClock(Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC));
      }
      if (builder.getContext() == null) {
        builder.setContext(Context.defaultContext());
      }
      return builder;
    }
  }

  @Value
  @Builder
  public static class Context {
    ProjectParams projectParams;
    String serviceIdentifier;
    String envIdentifier;

    public String getMonitoredServiceIdentifier() {
      return serviceIdentifier + "_" + envIdentifier;
    }

    public static Context defaultContext() {
      return Context.builder()
          .projectParams(ProjectParams.builder()
                             .accountIdentifier(randomAlphabetic(20))
                             .orgIdentifier(randomAlphabetic(20))
                             .projectIdentifier(randomAlphabetic(20))
                             .build())
          .envIdentifier(randomAlphabetic(20))
          .serviceIdentifier(randomAlphabetic(20))
          .build();
    }

    public String getAccountId() {
      return projectParams.getAccountIdentifier();
    }

    public String getOrgIdentifier() {
      return projectParams.getOrgIdentifier();
    }

    public String getProjectIdentifier() {
      return projectParams.getProjectIdentifier();
    }

    public void setAccountId(String accountId) {
      projectParams.setAccountIdentifier(accountId);
    }
    public void setOrgIdentifier(String orgIdentifier) {
      projectParams.setOrgIdentifier(orgIdentifier);
    }
    public void setProjectIdentifier(String projectIdentifier) {
      projectParams.setProjectIdentifier(projectIdentifier);
    }
    public ServiceEnvironmentParams getServiceEnvironmentParams() {
      return ServiceEnvironmentParams.builder()
          .accountIdentifier(projectParams.getAccountIdentifier())
          .orgIdentifier(projectParams.getOrgIdentifier())
          .projectIdentifier(projectParams.getProjectIdentifier())
          .serviceIdentifier(serviceIdentifier)
          .environmentIdentifier(envIdentifier)
          .build();
    }

    public MonitoredServiceParams getMonitoredServiceParams() {
      return MonitoredServiceParams.builder()
          .accountIdentifier(projectParams.getAccountIdentifier())
          .orgIdentifier(projectParams.getOrgIdentifier())
          .projectIdentifier(projectParams.getProjectIdentifier())
          .serviceIdentifier(serviceIdentifier)
          .environmentIdentifier(envIdentifier)
          .monitoredServiceIdentifier(getMonitoredServiceIdentifier())
          .build();
    }
  }

  public ExecutionLogDTOBuilder executionLogDTOBuilder() {
    long createdAt = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().toEpochMilli();
    Instant startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().minusSeconds(5);
    Instant endTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant();
    return ExecutionLogDTO.builder()
        .accountId(context.getAccountId())
        .traceableId("traceableId")
        .log("Data Collection successfully completed.")
        .logLevel(LogLevel.INFO)
        .startTime(startTime.toEpochMilli())
        .endTime(endTime.toEpochMilli())
        .createdAt(createdAt)
        .traceableType(TraceableType.VERIFICATION_TASK);
  }

  public NotificationRuleDTOBuilder getNotificationRuleDTOBuilder(NotificationRuleType type) {
    return NotificationRuleDTO.builder()
        .name("rule")
        .identifier("rule")
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .type(type)
        .conditions(getNotificationRuleConditions(type))
        .notificationMethod(CVNGNotificationChannel.builder()
                                .type(CVNGNotificationChannelType.EMAIL)
                                .spec(CVNGEmailChannelSpec.builder()
                                          .recipients(Arrays.asList("test@harness.io"))
                                          .userGroups(Arrays.asList("testUserGroup"))
                                          .build())
                                .build());
  }

  public DefaultMonitoredServiceSpecBuilder getDefaultMonitoredServiceSpecBuilder() {
    return DefaultMonitoredServiceSpec.builder();
  }

  public ConfiguredMonitoredServiceSpecBuilder getConfiguredMonitoredServiceSpecBuilder() {
    return ConfiguredMonitoredServiceSpec.builder().monitoredServiceRef(
        ParameterField.<String>builder().value(context.getMonitoredServiceIdentifier()).build());
  }

  public TemplateMonitoredServiceSpecBuilder getTemplateMonitoredServiceSpecBuilder() {
    return TemplateMonitoredServiceSpec.builder()
        .monitoredServiceTemplateRef(
            ParameterField.<String>builder().value(context.getMonitoredServiceIdentifier()).build())
        .versionLabel("1");
  }

  public VerificationJobInstanceCleanupSideKickDataBuilder getVerificationJobInstanceCleanupSideKickDataBuilder(
      String verificationJobInstanceId, List<String> sources) {
    return VerificationJobInstanceCleanupSideKickData.builder()
        .accountIdentifier(context.getAccountId())
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .verificationJobInstanceIdentifier(verificationJobInstanceId)
        .sourceIdentifiers(sources);
  }

  public CompositeSLORecordsCleanupSideKickDataBuilder getCompositeSLORecordsCleanupSideKickDataBuilder(
      String sloId, Instant afterStartTime) {
    return CompositeSLORecordsCleanupSideKickData.builder().sloVersion(0).sloId(sloId).afterStartTime(
        afterStartTime.getEpochSecond() / 60);
  }

  public TimeSeriesThreshold getMetricThresholdBuilder(String metricName, String metricGroupName) {
    return TimeSeriesThreshold.builder()
        .thresholdConfigType(ThresholdConfigType.USER_DEFINED)
        .metricName(metricName)
        .metricGroupName(metricGroupName)
        .action(IGNORE)
        .metricType(TimeSeriesMetricType.INFRA)
        .criteria(TimeSeriesThresholdCriteria.builder()
                      .value(20.0)
                      .action(TimeSeriesCustomThresholdActions.IGNORE)
                      .thresholdType(TimeSeriesThresholdType.ACT_WHEN_HIGHER)
                      .type(TimeSeriesThresholdComparisonType.ABSOLUTE)
                      .build())
        .build();
  }

  private List<NotificationRuleCondition> getNotificationRuleConditions(NotificationRuleType type) {
    if (type.equals(NotificationRuleType.SLO)) {
      return Arrays.asList(NotificationRuleCondition.builder()
                               .type(NotificationRuleConditionType.ERROR_BUDGET_REMAINING_PERCENTAGE)
                               .spec(ErrorBudgetRemainingPercentageConditionSpec.builder().threshold(10.0).build())
                               .build());
    } else {
      return Arrays.asList(NotificationRuleCondition.builder()
                               .type(NotificationRuleConditionType.HEALTH_SCORE)
                               .spec(HealthScoreConditionSpec.builder().threshold(20.0).period("10m").build())
                               .build());
    }
  }

  public List<RiskCategoryDTO> getRiskCategoryList() {
    RiskCategoryDTO performanceThroughputRiskCategory = RiskCategoryDTO.builder()
                                                            .displayName("Performance/Throughput")
                                                            .cvMonitoringCategory(CVMonitoringCategory.PERFORMANCE)
                                                            .timeSeriesMetricType(TimeSeriesMetricType.THROUGHPUT)
                                                            .build();
    RiskCategoryDTO infrastructureRiskCategory = RiskCategoryDTO.builder()
                                                     .displayName("Infrastructure")
                                                     .cvMonitoringCategory(CVMonitoringCategory.INFRASTRUCTURE)
                                                     .timeSeriesMetricType(TimeSeriesMetricType.INFRA)
                                                     .build();
    return Arrays.asList(performanceThroughputRiskCategory, infrastructureRiskCategory);
  }

  public MetricThreshold getMetricThreshold() {
    return MetricThreshold.builder()
        .thresholdType(MetricThresholdType.IGNORE)
        .isUserDefined(true)
        .action(MetricCustomThresholdActions.IGNORE)
        .criteria(MetricThresholdCriteria.builder().lessThanThreshold(1.0).build())
        .build();
  }

  public AnalysedDeploymentTestDataNode getAnalysedDeploymentTestDataNode() {
    return AnalysedDeploymentTestDataNode.builder()
        .nodeIdentifier("nodeIdentifier")
        .analysisResult(AnalysisResult.NO_ANALYSIS)
        .analysisReason(AnalysisReason.NO_CONTROL_DATA)
        .controlDataType(ControlDataType.MINIMUM_DEVIATION)
        .controlNodeIdentifier("controlNodeIdentifier")
        .controlData(Collections.singletonList(MetricValue.builder().value(1.0).timestampInMillis(1L).build()))
        .testData(Collections.singletonList(MetricValue.builder().value(1.0).timestampInMillis(1L).build()))
        .normalisedTestData(Collections.singletonList(MetricValue.builder().value(1.0).timestampInMillis(1L).build()))
        .normalisedControlData(
            Collections.singletonList(MetricValue.builder().value(1.0).timestampInMillis(1L).build()))
        .build();
  }

  public MetricsAnalysis getMetricsAnalysis() {
    return MetricsAnalysis.builder()
        .metricName("metricName")
        .metricType(MetricType.ERROR)
        .metricIdentifier("metricIdentifier")
        .healthSource(io.harness.cvng.cdng.beans.v2.HealthSource.builder().identifier("healthSourceIdentifier").build())
        .transactionGroup("transactionGroup")
        .thresholds(Collections.singletonList(getMetricThreshold()))
        .analysisResult(AnalysisResult.NO_ANALYSIS)
        .testDataNodes(Collections.singletonList(getAnalysedDeploymentTestDataNode()))
        .build();
  }

  public DowntimeDTO getOnetimeDurationBasedDowntimeDTO() {
    long startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond();
    LocalDateTime startDateTime = LocalDateTime.now(CVNGTestConstants.FIXED_TIME_FOR_TESTS);
    return DowntimeDTO.builder()
        .identifier("downtimeOneTimeDuration")
        .name("downtime OneTime Duration")
        .category(DowntimeCategory.SCHEDULED_MAINTENANCE)
        .description("Scheduled Maintenance")
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .enabled(true)
        .tags(new HashMap<>())
        .scope(DowntimeScope.PROJECT)
        .entitiesRule(
            EntityIdentifiersRule.builder()
                .entityIdentifiers(Collections.singletonList(
                    EntityDetails.builder().enabled(true).entityRef(context.getMonitoredServiceIdentifier()).build()))
                .build())
        .spec(DowntimeSpecDTO.builder()
                  .type(DowntimeType.ONE_TIME)
                  .spec(OnetimeDowntimeSpec.builder()
                            .startTime(startTime)
                            .startDateTime(dtf.format(startDateTime))
                            .timezone("UTC")
                            .type(OnetimeDowntimeType.DURATION)
                            .spec(OnetimeDowntimeSpec.OnetimeDurationBasedSpec.builder()
                                      .downtimeDuration(DowntimeDuration.builder()
                                                            .durationValue(30)
                                                            .durationType(DowntimeDurationType.MINUTES)
                                                            .build())
                                      .build())
                            .build())
                  .build())
        .build();
  }

  public DowntimeDTO getOnetimeEndTimeBasedDowntimeDTO() {
    long startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond();
    LocalDateTime startDateTime = LocalDateTime.now(CVNGTestConstants.FIXED_TIME_FOR_TESTS);
    long endTime = startTime + Duration.ofMinutes(30).toSeconds();
    LocalDateTime endDateTime = startDateTime.plusMinutes(30);
    return DowntimeDTO.builder()
        .identifier("downtimeOneTimeEndTime")
        .name("downtime OneTime EndTime")
        .category(DowntimeCategory.SCHEDULED_MAINTENANCE)
        .description("Scheduled Maintenance")
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .enabled(true)
        .tags(new HashMap<>())
        .scope(DowntimeScope.PROJECT)
        .entitiesRule(
            EntityIdentifiersRule.builder()
                .entityIdentifiers(Collections.singletonList(
                    EntityDetails.builder().enabled(true).entityRef(context.getMonitoredServiceIdentifier()).build()))
                .build())
        .spec(DowntimeSpecDTO.builder()
                  .type(DowntimeType.ONE_TIME)
                  .spec(OnetimeDowntimeSpec.builder()
                            .startTime(startTime)
                            .startDateTime(dtf.format(startDateTime))
                            .timezone("UTC")
                            .type(OnetimeDowntimeType.END_TIME)
                            .spec(OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec.builder()
                                      .endTime(endTime)
                                      .endDateTime(dtf.format(endDateTime))
                                      .build())
                            .build())
                  .build())
        .build();
  }

  public DowntimeDTO getRecurringDowntimeDTO() {
    long startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond();
    LocalDateTime startDateTime = LocalDateTime.now(CVNGTestConstants.FIXED_TIME_FOR_TESTS);
    long endTime = startTime + Duration.ofDays(365).toSeconds();
    LocalDateTime endDateTime = startDateTime.plusDays(365);
    return DowntimeDTO.builder()
        .identifier("downtimeRecurring")
        .name("downtime Recurring")
        .category(DowntimeCategory.SCHEDULED_MAINTENANCE)
        .description("Scheduled Maintenance")
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .enabled(true)
        .tags(new HashMap<>())
        .scope(DowntimeScope.PROJECT)
        .entitiesRule(
            EntityIdentifiersRule.builder()
                .entityIdentifiers(Collections.singletonList(
                    EntityDetails.builder().enabled(true).entityRef(context.getMonitoredServiceIdentifier()).build()))
                .build())
        .spec(DowntimeSpecDTO.builder()
                  .type(DowntimeType.RECURRING)
                  .spec(RecurringDowntimeSpec.builder()
                            .startTime(startTime)
                            .startDateTime(dtf.format(startDateTime))
                            .timezone("UTC")
                            .downtimeDuration(DowntimeDuration.builder()
                                                  .durationValue(30)
                                                  .durationType(DowntimeDurationType.MINUTES)
                                                  .build())
                            .recurrenceEndTime(endTime)
                            .recurrenceEndDateTime(dtf.format(endDateTime))
                            .downtimeRecurrence(DowntimeRecurrence.builder()
                                                    .recurrenceValue(1)
                                                    .recurrenceType(DowntimeRecurrenceType.WEEK)
                                                    .build())
                            .build())
                  .build())
        .build();
  }

  public EntityUnavailabilityStatusesDTO getDowntimeEntityUnavailabilityStatusesDTO() {
    long startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond();
    long endTime = startTime + Duration.ofMinutes(30).toSeconds();
    return EntityUnavailabilityStatusesDTO.builder()
        .entityType(EntityType.MAINTENANCE_WINDOW)
        .entityId("downtimeRecurring")
        .status(EntityUnavailabilityStatus.MAINTENANCE_WINDOW)
        .startTime(startTime)
        .endTime(endTime)
        .projectIdentifier(context.getProjectIdentifier())
        .orgIdentifier(context.getOrgIdentifier())
        .build();
  }

  public EntityUnavailabilityStatusesDTO getSLOEntityUnavailabilityStatusesDTO() {
    long startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond();
    long endTime = startTime + Duration.ofMinutes(30).toSeconds();
    return EntityUnavailabilityStatusesDTO.builder()
        .entityType(EntityType.SLO)
        .entityId("sliId")
        .status(EntityUnavailabilityStatus.DATA_COLLECTION_FAILED)
        .startTime(startTime)
        .endTime(endTime)
        .projectIdentifier(context.getProjectIdentifier())
        .orgIdentifier(context.getOrgIdentifier())
        .build();
  }

  public AnnotationDTO getAnnotationDTO() {
    long startTime =
        CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond() + Duration.ofMinutes(1).toSeconds();
    long endTime = startTime + Duration.ofMinutes(30).toSeconds();
    return AnnotationDTO.builder()
        .orgIdentifier(context.getOrgIdentifier())
        .projectIdentifier(context.getProjectIdentifier())
        .message("Errors spiked")
        .startTime(startTime)
        .endTime(endTime)
        .sloIdentifier("sloIdentifier")
        .build();
  }

  public CustomChangeWebhookPayloadBuilder getCustomChangeWebhookPayloadBuilder() {
    return CustomChangeWebhookPayload.builder()
        .endTime(1000l)
        .startTime(1000l)
        .user("testUser")
        .eventDetail(CustomChangeWebhookEventDetail.builder()
                         .changeEventDetailsLink("testLink")
                         .externalLinkToEntity("externalLink")
                         .description("desc")
                         .name("name")
                         .build());
  }
}
