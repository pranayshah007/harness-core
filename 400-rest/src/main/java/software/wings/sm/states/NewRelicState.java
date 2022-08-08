/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.waiter.OrchestrationNotifyEventListener.ORCHESTRATION;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.WingsException;

import software.wings.beans.NewRelicConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.metrics.MetricType;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.DataCollectionCallback;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.TimeSeriesMetricGroup.TimeSeriesMlAnalysisGroupInfo;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.newrelic.NewRelicApplication;
import software.wings.service.impl.newrelic.NewRelicDataCollectionInfo;
import software.wings.service.impl.newrelic.NewRelicDataCollectionInfoV2;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.impl.newrelic.NewRelicMetricValueDefinition;
import software.wings.service.intfc.newrelic.NewRelicService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * Created by rsingh on 8/28/17.
 */
@Slf4j
@FieldNameConstants(innerTypeName = "NewRelicStateKeys")
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class NewRelicState extends AbstractMetricAnalysisState {
  private String analysisServerConfigId;
  private String applicationId;

  public TimeSeriesMlAnalysisType getAnalysisType() {
    if (getComparisonStrategy() == AnalysisComparisonStrategy.PREDICTIVE) {
      return TimeSeriesMlAnalysisType.PREDICTIVE;
    } else {
      return TimeSeriesMlAnalysisType.COMPARATIVE;
    }
  }

  @Attributes(required = true, title = "Metrics") private List<String> metrics;

  @Inject @SchemaIgnore private transient NewRelicService newRelicService;
  public NewRelicState(String name) {
    super(name, StateType.NEW_RELIC);
  }

  @Override
  @Attributes(required = true, title = "Include nodes from previous phases")
  public boolean getIncludePreviousPhaseNodes() {
    return includePreviousPhaseNodes;
  }

  @Override
  public Logger getLogger() {
    return log;
  }

  @Override
  public String getAnalysisServerConfigId() {
    return analysisServerConfigId;
  }

  @Override
  public void setAnalysisServerConfigId(String analysisServerConfigId) {
    this.analysisServerConfigId = analysisServerConfigId;
  }

  public String getApplicationId() {
    return applicationId;
  }

  public void setApplicationId(String applicationId) {
    this.applicationId = applicationId;
  }

  @Override
  protected String triggerAnalysisDataCollection(ExecutionContext context, AnalysisContext analysisContext,
      VerificationStateAnalysisExecutionData executionData, Map<String, String> hosts) {
    final Collection<Metric> metricNameToObjectMap =
        newRelicService.getMetricsCorrespondingToMetricNames(metrics).values();
    final Map<String, TimeSeriesMetricDefinition> metricTemplate =
        newRelicService.metricDefinitions(metricNameToObjectMap);
    metricAnalysisService.saveMetricTemplates(
        context.getAppId(), StateType.NEW_RELIC, context.getStateExecutionInstanceId(), null, metricTemplate);
    String envId = getEnvId(context);
    String finalServerConfigId =
        getResolvedConnectorId(context, NewRelicStateKeys.analysisServerConfigId, analysisServerConfigId);
    String finalNewRelicApplicationId = getResolvedFieldValue(context, NewRelicStateKeys.applicationId, applicationId);

    SettingAttribute settingAttribute = getSettingAttribute(finalServerConfigId);

    try {
      newRelicService.resolveApplicationId(
          finalServerConfigId, finalNewRelicApplicationId, context.getAppId(), context.getWorkflowExecutionId());
    } catch (WingsException e) {
      // see if we can resolve the application by name
      final NewRelicApplication newRelicApplication = newRelicService.resolveApplicationName(
          finalServerConfigId, finalNewRelicApplicationId, context.getAppId(), context.getWorkflowExecutionId());
      finalNewRelicApplicationId = String.valueOf(newRelicApplication.getId());
    }

    final NewRelicConfig newRelicConfig = (NewRelicConfig) settingAttribute.getValue();
    final long dataCollectionStartTimeStamp = dataCollectionStartTimestampMillis();
    final NewRelicDataCollectionInfo dataCollectionInfo =
        NewRelicDataCollectionInfo.builder()
            .newRelicConfig(newRelicConfig)
            .applicationId(context.getAppId())
            .stateExecutionId(context.getStateExecutionInstanceId())
            .workflowId(getWorkflowId(context))
            .workflowExecutionId(context.getWorkflowExecutionId())
            .serviceId(getPhaseServiceId(context))
            .startTime(dataCollectionStartTimeStamp)
            .collectionTime(Integer.parseInt(getTimeDuration(context)))
            .newRelicAppId(Long.parseLong(finalNewRelicApplicationId))
            .timeSeriesMlAnalysisType(getAnalysisType())
            .dataCollectionMinute(0)
            .encryptedDataDetails(secretManager.getEncryptionDetails(
                newRelicConfig, context.getAppId(), context.getWorkflowExecutionId()))
            .hosts(hosts)
            .settingAttributeId(finalServerConfigId)
            .checkNotAllowedStrings(!featureFlagService.isEnabled(
                FeatureName.DISABLE_METRIC_NAME_CURLY_BRACE_CHECK, newRelicConfig.getAccountId()))
            .build();

    String waitId = generateUuid();
    String infrastructureMappingId = context.fetchInfraMappingId();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(appService.get(context.getAppId()).getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, context.getAppId())
            .waitId(waitId)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.NEWRELIC_COLLECT_METRIC_DATA.name())
                      .parameters(new Object[] {dataCollectionInfo})
                      .timeout(TimeUnit.MINUTES.toMillis(Integer.parseInt(getTimeDuration(context)) + 120))
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infrastructureMappingId)
            .build();
    waitNotifyEngine.waitForAllOn(ORCHESTRATION,
        DataCollectionCallback.builder()
            .appId(context.getAppId())
            .stateExecutionId(context.getStateExecutionInstanceId())
            .dataCollectionStartTime(dataCollectionStartTimeStamp)
            .dataCollectionEndTime(
                dataCollectionStartTimeStamp + TimeUnit.MINUTES.toMillis(Integer.parseInt(getTimeDuration(context))))
            .executionData(executionData)
            .isDataCollectionPerMinuteTask(false)
            .build(),
        waitId);
    return delegateService.queueTask(delegateTask);
  }

  @Override
  protected DataCollectionInfoV2 createDataCollectionInfo(
      ExecutionContext context, Map<String, String> hostsToCollect) {
    final Collection<Metric> metricNameToObjectMap =
        newRelicService.getMetricsCorrespondingToMetricNames(metrics).values();
    final Map<String, TimeSeriesMetricDefinition> metricTemplate =
        newRelicService.metricDefinitions(metricNameToObjectMap);
    metricAnalysisService.saveMetricTemplates(
        context.getAppId(), StateType.NEW_RELIC, context.getStateExecutionInstanceId(), null, metricTemplate);
    String envId = getEnvId(context);

    String finalServerConfigId =
        getResolvedConnectorId(context, NewRelicStateKeys.analysisServerConfigId, analysisServerConfigId);

    String finalNewRelicApplicationId = getResolvedFieldValue(context, NewRelicStateKeys.applicationId, applicationId);
    try {
      newRelicService.resolveApplicationId(
          finalServerConfigId, finalNewRelicApplicationId, context.getAppId(), context.getWorkflowExecutionId());
    } catch (Exception e) {
      // see if we can resolve the application by name
      final NewRelicApplication newRelicApplication = newRelicService.resolveApplicationName(
          finalServerConfigId, finalNewRelicApplicationId, context.getAppId(), context.getWorkflowExecutionId());
      finalNewRelicApplicationId = String.valueOf(newRelicApplication.getId());
    }

    return NewRelicDataCollectionInfoV2.builder()
        .connectorId(finalServerConfigId)
        .workflowExecutionId(context.getWorkflowExecutionId())
        .stateExecutionId(context.getStateExecutionInstanceId())
        .workflowId(context.getWorkflowId())
        .accountId(appService.get(context.getAppId()).getAccountId())
        .envId(envId)
        .applicationId(context.getAppId())
        .hosts(hostsToCollect.keySet())
        .newRelicAppId(Long.parseLong(finalNewRelicApplicationId))
        .hostsToGroupNameMap(hostsToCollect)
        .serviceId(getPhaseServiceId(context))
        .build();
  }
  @Override
  protected void createAndSaveMetricGroups(ExecutionContext context, Map<String, String> hostsToCollect) {
    Map<String, TimeSeriesMlAnalysisGroupInfo> metricGroups = new HashMap<>();
    Set<String> hostGroups = new HashSet<>(hostsToCollect.values());
    getLogger().info("saving host groups are {}", hostGroups);
    hostGroups.forEach(hostGroup
        -> metricGroups.put(hostGroup,
            TimeSeriesMlAnalysisGroupInfo.builder().groupName(hostGroup).mlAnalysisType(getAnalysisType()).build()));
    getLogger().info("saving metric groups {}", metricGroups);
    metricAnalysisService.saveMetricGroups(
        context.getAppId(), StateType.valueOf(getStateType()), context.getStateExecutionInstanceId(), metricGroups);
  }

  @Override
  public Map<String, String> parentTemplateFields(String fieldName) {
    Map<String, String> parentTemplateFields = new LinkedHashMap<>();
    if (fieldName.equals("applicationId")) {
      if (!configIdTemplatized()) {
        parentTemplateFields.put("analysisServerConfigId", analysisServerConfigId);
      }
    }
    return parentTemplateFields;
  }
  @SuppressWarnings("PMD") // pmd complains about the values.get being null even after contains check.
  public static Double getNormalizedErrorMetric(String metricName, NewRelicMetricDataRecord metricDataRecord) {
    if (metricDataRecord != null) {
      Map<String, Double> values = metricDataRecord.getValues();
      if (metricName.equals(NewRelicMetricValueDefinition.ERROR)
          && values.containsKey(NewRelicMetricValueDefinition.ERROR)
          && values.containsKey(NewRelicMetricValueDefinition.REQUSET_PER_MINUTE)) {
        double errorCount = values.get(NewRelicMetricValueDefinition.ERROR);
        double callsCount = values.get(NewRelicMetricValueDefinition.REQUSET_PER_MINUTE);

        if (callsCount != 0.0) {
          DecimalFormat twoDForm = new DecimalFormat("#.00");
          return Double.valueOf(twoDForm.format(errorCount / callsCount * 100));
        } else {
          return 0.0;
        }
      }
      return values.get(metricName);
    }
    return null;
  }

  public static String getMetricTypeForMetric(String metricName) {
    if (isEmpty(metricName)) {
      return null;
    }
    if (NewRelicMetricValueDefinition.NEW_RELIC_VALUES_TO_ANALYZE.containsKey(metricName)) {
      return NewRelicMetricValueDefinition.NEW_RELIC_VALUES_TO_ANALYZE.get(metricName).getMetricType().name();
    }
    log.info("Invalid metricName in NewRelic {}", metricName);
    return null;
  }

  @Override
  @Attributes(required = false, title = "Expression for Host/Container name")
  public String getHostnameTemplate() {
    return hostnameTemplate;
  }

  @Override
  public void setHostnameTemplate(String hostnameTemplate) {
    this.hostnameTemplate = hostnameTemplate;
  }

  private boolean configIdTemplatized() {
    return TemplateExpressionProcessor.checkFieldTemplatized("analysisServerConfigId", getTemplateExpressions());
  }

  public List<String> getMetrics() {
    return metrics;
  }

  public void setMetrics(List<String> metrics) {
    this.metrics = metrics;
  }

  @Override
  protected Optional<FeatureName> getCVTaskFeatureName() {
    return Optional.of(FeatureName.NEW_RELIC_CV_TASK);
  }
  @Data
  @Builder
  public static class Metric {
    private String metricName;
    private MetricType mlMetricType;
    private String displayName;
    private Set<String> tags;
  }
}
