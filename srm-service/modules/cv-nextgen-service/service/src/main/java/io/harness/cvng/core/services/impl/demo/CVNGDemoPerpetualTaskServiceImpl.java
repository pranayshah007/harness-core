/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.demo;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.beans.DataCollectionTaskDTO.DataCollectionTaskResult;
import io.harness.cvng.beans.DataCollectionTaskDTO.DataCollectionTaskResult.DataCollectionTaskResultBuilder;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.core.beans.demo.DemoMetricParams;
import io.harness.cvng.core.beans.demo.DemoTemplate;
import io.harness.cvng.core.beans.dependency.DependencyMetadataType;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.VerificationTask.DeploymentInfo;
import io.harness.cvng.core.entities.VerificationTask.LiveMonitoringInfo;
import io.harness.cvng.core.entities.VerificationTask.SLIInfo;
import io.harness.cvng.core.entities.demo.CVNGDemoPerpetualTask;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.cvng.core.services.api.LogRecordService;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.demo.CVNGDemoPerpetualTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.utils.analysisinfo.AnalysisInfoUtility;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CVNGDemoPerpetualTaskServiceImpl implements CVNGDemoPerpetualTaskService {
  @Inject HPersistence hPersistence;
  @Inject DataCollectionTaskService dataCollectionTaskService;
  @Inject TimeSeriesRecordService timeSeriesRecordService;
  @Inject LogRecordService logRecordService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private CVConfigService cvConfigService;
  @Inject private ActivityService activityService;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private MonitoredServiceService monitoredServiceService;

  @Override
  public String createCVNGDemoPerpetualTask(String accountId, String dataCollectionWorkerId) {
    CVNGDemoPerpetualTask cvngDemoPerpetualTask =
        CVNGDemoPerpetualTask.builder().accountId(accountId).dataCollectionWorkerId(dataCollectionWorkerId).build();
    return hPersistence.save(cvngDemoPerpetualTask);
  }

  @Override
  public void execute(CVNGDemoPerpetualTask cvngDemoPerpetualTask) throws Exception {
    while (true) {
      Optional<DataCollectionTask> dataCollectionTask = dataCollectionTaskService.getNextTask(
          cvngDemoPerpetualTask.getAccountId(), cvngDemoPerpetualTask.getDataCollectionWorkerId());
      if (!dataCollectionTask.isPresent()) {
        break;
      }
      DataCollectionTaskResultBuilder dataCollectionTaskResultBuilder =
          DataCollectionTaskResult.builder().dataCollectionTaskId(dataCollectionTask.get().getUuid());
      try {
        saveResults(dataCollectionTask.get());
        dataCollectionTaskResultBuilder.status(DataCollectionExecutionStatus.SUCCESS);
      } catch (Exception e) {
        dataCollectionTaskResultBuilder.status(DataCollectionExecutionStatus.FAILED)
            .stacktrace(Arrays.toString(e.getStackTrace()))
            .exception(e.getMessage());
        log.warn("Demo data perpetual task failed for verificationTaskId"
                + dataCollectionTask.get().getVerificationTaskId()
                + "  for time frame: " + dataCollectionTask.get().getStartTime() + " to "
                + dataCollectionTask.get().getEndTime() + " with exception: " + e.getMessage() + ": stacktrace: {}",
            e);
        throw e;
      } finally {
        dataCollectionTaskService.updateTaskStatus(dataCollectionTaskResultBuilder.build());
      }
    }
  }

  @Override
  public void deletePerpetualTask(String accountId, String perpetualTaskId) {
    hPersistence.delete(hPersistence.get(CVNGDemoPerpetualTask.class, perpetualTaskId));
  }

  private void saveResults(DataCollectionTask dataCollectionTask) throws IOException {
    DemoTemplate demoTemplate;
    VerificationTask verificationTask = verificationTaskService.get(dataCollectionTask.getVerificationTaskId());
    CVConfig cvConfig = getRelatedCvConfig(verificationTask);
    // get activity for monitored service that finished in last 15 mins.
    demoTemplate = getDemoTemplate(cvConfig, isHighRiskTimeRange(cvConfig, dataCollectionTask));
    if (dataCollectionTask.getDataCollectionInfo().getVerificationType().equals(VerificationType.TIME_SERIES)) {
      MetricCVConfig metricCVConfig = (MetricCVConfig) cvConfig;
      List<AnalysisInfo> analysisInfos = AnalysisInfoUtility.filterApplicableForDataCollection(
          metricCVConfig.getMetricInfos(), verificationTask.getTaskInfo().getTaskType());
      timeSeriesRecordService.createDemoAnalysisData(dataCollectionTask.getAccountId(),
          dataCollectionTask.getVerificationTaskId(), dataCollectionTask.getDataCollectionWorkerId(),
          dataCollectionTask.getStartTime(), dataCollectionTask.getEndTime(),
          DemoMetricParams.builder()
              .customMetric(isNotEmpty(metricCVConfig.getMetricInfos()))
              .groupName((String) metricCVConfig.maybeGetGroupName().orElse("default"))
              .analysisInfos(analysisInfos)
              .demoTemplate(demoTemplate)
              .build());
    } else {
      logRecordService.createDemoAnalysisData(dataCollectionTask.getAccountId(),
          dataCollectionTask.getVerificationTaskId(), dataCollectionTask.getDataCollectionWorkerId(), demoTemplate,
          dataCollectionTask.getStartTime(), dataCollectionTask.getEndTime());
    }
  }

  private boolean isHighRiskTimeRange(CVConfig cvConfig, DataCollectionTask dataCollectionTask) {
    MonitoredServiceParams monitoredServiceParams =
        MonitoredServiceParams.builder()
            .accountIdentifier(cvConfig.getAccountId())
            .orgIdentifier(cvConfig.getOrgIdentifier())
            .projectIdentifier(cvConfig.getProjectIdentifier())
            .monitoredServiceIdentifier(cvConfig.getMonitoredServiceIdentifier())
            .build();
    boolean isHighRiskTimeRange =
        activityService
            .getAnyDemoDeploymentEvent(monitoredServiceParams,
                dataCollectionTask.getStartTime().minus(Duration.ofMinutes(15)), dataCollectionTask.getStartTime(),
                ActivityVerificationStatus.VERIFICATION_FAILED)
            .isPresent();
    if (isHighRiskTimeRange) {
      return true;
    } else {
      Optional<MonitoredService> kubernetesMonitoredService =
          getKubernetesDependentMonitoredService(monitoredServiceParams);
      if (kubernetesMonitoredService.isPresent()) {
        MonitoredServiceParams dependencyServiceEnvParams =
            MonitoredServiceParams.builder()
                .accountIdentifier(kubernetesMonitoredService.get().getAccountId())
                .orgIdentifier(kubernetesMonitoredService.get().getOrgIdentifier())
                .projectIdentifier(kubernetesMonitoredService.get().getProjectIdentifier())
                .monitoredServiceIdentifier(kubernetesMonitoredService.get().getIdentifier())
                .build();
        return activityService
            .getAnyKubernetesEvent(dependencyServiceEnvParams,
                dataCollectionTask.getStartTime().minus(Duration.ofMinutes(15)), dataCollectionTask.getStartTime())
            .map(event
                -> activityService
                       .getAnyDemoDeploymentEvent(monitoredServiceParams,
                           event.getEventTime().minus(Duration.ofMinutes(15)),
                           event.getEventTime().minus(Duration.ofMinutes(5)),
                           ActivityVerificationStatus.VERIFICATION_PASSED)
                       .orElse(null))
            .isPresent();
      } else {
        return false;
      }
    }
  }

  private Optional<MonitoredService> getKubernetesDependentMonitoredService(
      MonitoredServiceParams monitoredServiceParams) {
    MonitoredServiceDTO monitoredService = monitoredServiceService.getMonitoredServiceDTO(monitoredServiceParams);
    Optional<MonitoredServiceDTO.ServiceDependencyDTO> serviceDependencyDTO =
        monitoredService.getDependencies()
            .stream()
            .filter(serviceDependency
                -> serviceDependency.getDependencyMetadata() != null
                    && serviceDependency.getDependencyMetadata().getType() == DependencyMetadataType.KUBERNETES)
            .findAny();
    if (serviceDependencyDTO.isPresent()) {
      MonitoredService kubernetesMonitoredService = monitoredServiceService.getMonitoredService(
          monitoredServiceParams, serviceDependencyDTO.get().getMonitoredServiceIdentifier());
      return Optional.of(kubernetesMonitoredService);
    }
    return Optional.empty();
  }

  private CVConfig getRelatedCvConfig(VerificationTask verificationTask) {
    switch (verificationTask.getTaskInfo().getTaskType()) {
      case DEPLOYMENT:
        DeploymentInfo deploymentInfo = (DeploymentInfo) verificationTask.getTaskInfo();
        return cvConfigService.get(deploymentInfo.getCvConfigId());
      case LIVE_MONITORING:
        LiveMonitoringInfo liveMonitoringInfo = (LiveMonitoringInfo) verificationTask.getTaskInfo();
        return cvConfigService.get(liveMonitoringInfo.getCvConfigId());
      case SLI:
        SLIInfo sliInfo = (SLIInfo) verificationTask.getTaskInfo();
        return serviceLevelIndicatorService.fetchCVConfigForSLI(sliInfo.getSliId()).get(0);
      default:
        throw new IllegalStateException("Unknown type:" + verificationTask.getTaskInfo().getTaskType());
    }
  }

  private DemoTemplate getDemoTemplate(CVConfig cvConfig, boolean highRisk) {
    String template = "default";
    // appd_template_demo_dev
    Pattern identifierTemplatePattern = Pattern.compile(".*template_(.*)_dev");
    Matcher matcher = identifierTemplatePattern.matcher(cvConfig.getFullyQualifiedIdentifier());
    if (matcher.matches()) {
      String templateSubstring = matcher.group(1);
      if (isNotEmpty(templateSubstring)) {
        template = templateSubstring;
      }
    }
    return DemoTemplate.builder().demoTemplateIdentifier(template).isHighRisk(highRisk).build();
  }
}
