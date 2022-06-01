/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.waiter.OrchestrationNotifyEventListener.ORCHESTRATION;

import static software.wings.common.VerificationConstants.DELAY_MINUTES;
import static software.wings.service.impl.analysis.TimeSeriesMlAnalysisType.PREDICTIVE;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;

import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.metrics.MetricType;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.DataCollectionCallback;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.stackdriver.StackDriverDataCollectionInfo;
import software.wings.service.intfc.stackdriver.StackDriverService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.verification.VerificationStateAnalysisExecutionData;
import software.wings.verification.stackdriver.StackDriverMetricCVConfiguration;
import software.wings.verification.stackdriver.StackDriverMetricDefinition;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.base.Preconditions;
import com.google.common.collect.TreeBasedTable;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * Created by Pranjal on 11/28/2018
 */
@Slf4j
@OwnedBy(HarnessTeam.CV)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@FieldNameConstants(innerTypeName = "StackDriverStateKeys")
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class StackDriverState extends AbstractMetricAnalysisState {
  @Inject private transient StackDriverService stackDriverService;
  @Inject private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  private String analysisServerConfigId;

  private boolean isLogState;

  private String projectId;

  private List<StackDriverMetricDefinition> metricDefinitions;

  public void setMetricDefinitions(List<StackDriverMetricDefinition> metricDefinitions) {
    this.metricDefinitions = metricDefinitions;
    if (isNotEmpty(metricDefinitions)) {
      metricDefinitions.forEach(StackDriverMetricDefinition::extractJson);
    }
  }

  public List<StackDriverMetricDefinition> getMetricDefinitions() {
    return this.metricDefinitions;
  }

  public List<StackDriverMetricDefinition> fetchMetricDefinitions(ExecutionContext executionContext) {
    for (StackDriverMetricDefinition metricDefinition : this.metricDefinitions) {
      String resolvedJsonQuery = getResolvedFieldValue(executionContext, "", metricDefinition.getFilterJson());
      metricDefinition.setFilterJson(resolvedJsonQuery);
      metricDefinition.extractJson();
    }
    return this.metricDefinitions;
  }

  /**
   * Instantiates a new state.
   *
   * @param name the name
   */
  public StackDriverState(String name) {
    super(name, StateType.STACK_DRIVER);
  }

  @Override
  @SchemaIgnore
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

  public void saveMetricTemplates(ExecutionContext context) {
    metricAnalysisService.saveMetricTemplates(context.getAppId(), StateType.STACK_DRIVER,
        context.getStateExecutionInstanceId(), null, fetchMetricTemplates(metricDefinitions));
  }

  @Override
  protected String triggerAnalysisDataCollection(ExecutionContext context, AnalysisContext analysisContext,
      VerificationStateAnalysisExecutionData executionData, Map<String, String> hosts) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String envId =
        workflowStandardParams == null || workflowStandardParamsExtensionService.getEnv(workflowStandardParams) == null
        ? null
        : workflowStandardParamsExtensionService.getEnv(workflowStandardParams).getUuid();

    String resolvedConnectorId =
        getResolvedConnectorId(context, StackDriverStateKeys.analysisServerConfigId, analysisServerConfigId);

    SettingAttribute settingAttribute = settingsService.get(resolvedConnectorId);

    Preconditions.checkNotNull(settingAttribute, "No Gcp config setting with id: " + resolvedConnectorId + " found");

    TimeSeriesMlAnalysisType analyzedTierAnalysisType = getComparisonStrategy() == AnalysisComparisonStrategy.PREDICTIVE
        ? PREDICTIVE
        : TimeSeriesMlAnalysisType.COMPARATIVE;

    final GcpConfig gcpConfig = (GcpConfig) settingAttribute.getValue();

    // StartTime will be current time in milliseconds
    final long dataCollectionStartTimeStamp = dataCollectionStartTimestampMillis();

    saveMetricTemplates(context);

    final StackDriverDataCollectionInfo dataCollectionInfo =
        StackDriverDataCollectionInfo.builder()
            .gcpConfig(gcpConfig)
            .projectId(projectId)
            .applicationId(context.getAppId())
            .stateExecutionId(context.getStateExecutionInstanceId())
            .workflowId(getWorkflowId(context))
            .workflowExecutionId(context.getWorkflowExecutionId())
            .serviceId(getPhaseServiceId(context))
            .timeSeriesMlAnalysisType(analyzedTierAnalysisType)
            .startMinute((int) (dataCollectionStartTimeStamp / TimeUnit.MINUTES.toMillis(1)))
            .startTime(dataCollectionStartTimeStamp)
            // Collection time is amount of time data collection needs to happen
            .collectionTime(Integer.parseInt(getTimeDuration(context)))
            .initialDelayMinutes(DELAY_MINUTES)
            // its a counter for each minute data. So basically the max value of
            // dataCollectionMinute can be equal to timeDuration
            //            .dataCollectionMinute(0)
            .encryptedDataDetails(
                secretManager.getEncryptionDetails(gcpConfig, context.getAppId(), context.getWorkflowExecutionId()))
            .hosts(hosts)
            .timeSeriesToCollect(fetchMetricDefinitions(context))
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
                      .taskType(TaskType.STACKDRIVER_COLLECT_METRIC_DATA.name())
                      .parameters(new Object[] {dataCollectionInfo})
                      .timeout(TimeUnit.MINUTES.toMillis(Integer.parseInt(timeDuration) + 120))
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
            .build(),
        waitId);
    return delegateService.queueTask(delegateTask);
  }

  public static Map<String, TimeSeriesMetricDefinition> fetchMetricTemplates(
      List<StackDriverMetricDefinition> timeSeriesToCollect) {
    Map<String, TimeSeriesMetricDefinition> rv = new HashMap<>();

    for (StackDriverMetricDefinition metricDefinition : timeSeriesToCollect) {
      rv.put(metricDefinition.getMetricName(),
          TimeSeriesMetricDefinition.builder()
              .metricName(metricDefinition.getMetricName())
              .metricType(MetricType.valueOf(metricDefinition.getMetricType()))
              .build());
    }
    return rv;
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

  public boolean isLogState() {
    return isLogState;
  }

  public void setLogState(boolean logState) {
    isLogState = logState;
  }

  public static String getMetricTypeForMetric(StackDriverMetricCVConfiguration cvConfiguration, String metricName) {
    if (cvConfiguration != null && isNotEmpty(metricName)) {
      return cvConfiguration.getMetricDefinitions()
          .stream()
          .filter(timeSeries -> timeSeries.getMetricName().equals(metricName))
          .findAny()
          .map(StackDriverMetricDefinition::getMetricType)
          .orElse(null);
    }
    return null;
  }

  public static Map<String, String> validateMetricDefinitions(
      List<StackDriverMetricDefinition> metricDefinitions, boolean serviceLevel) {
    Map<String, String> invalidFields = new HashMap<>();
    if (isEmpty(metricDefinitions)) {
      invalidFields.put("Invalid Setup: ", "No metrics given to analyze.");
      return invalidFields;
    }
    Map<String, String> metricNameToType = new HashMap<>();
    final TreeBasedTable<String, MetricType, Set<String>> txnToMetricType = TreeBasedTable.create();

    metricDefinitions.forEach(timeSeries -> {
      MetricType metricType = MetricType.valueOf(timeSeries.getMetricType());
      final String filter = timeSeries.getFilter();
      if (isEmpty(filter)) {
        invalidFields.put("Invalid metrics: ",
            "No Filter JSON specified for group: " + timeSeries.getTxnName()
                + " and metric: " + timeSeries.getMetricName());
      }

      if (!serviceLevel && !filter.contains("${host}")) {
        invalidFields.put("Invalid query: ",
            "Host field not specified for group: " + timeSeries.getTxnName()
                + " and metric: " + timeSeries.getMetricName());
      }

      if (metricNameToType.get(timeSeries.getMetricName()) == null) {
        metricNameToType.put(timeSeries.getMetricName(), timeSeries.getMetricType());
      } else if (!metricNameToType.get(timeSeries.getMetricName()).equals(timeSeries.getMetricType())) {
        invalidFields.put(
            "Invalid metric type for group: " + timeSeries.getTxnName() + ", metric : " + timeSeries.getMetricName(),
            timeSeries.getMetricName() + " has been configured as " + metricNameToType.get(timeSeries.getMetricName())
                + " in previous transactions. Same metric name can not have different metric types.");
      }

      if (!txnToMetricType.contains(timeSeries.getTxnName(), metricType)) {
        txnToMetricType.put(timeSeries.getTxnName(), metricType, new HashSet<>());
      }

      txnToMetricType.get(timeSeries.getTxnName(), metricType).add(timeSeries.getMetricName());
    });

    txnToMetricType.rowKeySet().forEach(txnName -> {
      final SortedMap<MetricType, Set<String>> txnRow = txnToMetricType.row(txnName);
      if (txnRow.containsKey(MetricType.ERROR) || txnRow.containsKey(MetricType.RESP_TIME)) {
        if (!txnRow.containsKey(MetricType.THROUGHPUT)) {
          invalidFields.put("Invalid metrics for group: " + txnName,
              txnName + " has error metrics: "
                  + (txnRow.get(MetricType.ERROR) == null ? Collections.emptySet() : txnRow.get(MetricType.ERROR))
                  + " and/or response time metrics: "
                  + (txnRow.get(MetricType.RESP_TIME) == null ? Collections.emptySet()
                                                              : txnRow.get(MetricType.RESP_TIME))
                  + " but no throughput metrics.");
        } else if (txnRow.get(MetricType.THROUGHPUT).size() > 1) {
          invalidFields.put("Invalid metrics for group: " + txnName,
              txnName + " has more than one throughput metrics are defined: " + txnRow.get(MetricType.THROUGHPUT));
        }
      }

      if (txnRow.containsKey(MetricType.THROUGHPUT) && txnRow.size() == 1) {
        invalidFields.put("Invalid metrics for group: " + txnName,
            txnName + " has only throughput metrics " + txnRow.get(MetricType.THROUGHPUT)
                + ". Throughput metrics is used to analyze other metrics and is not analyzed.");
      }
    });

    return invalidFields;
  }

  @Override
  public Map<String, String> validateFields() {
    return validateMetricDefinitions(metricDefinitions, false);
  }
}
