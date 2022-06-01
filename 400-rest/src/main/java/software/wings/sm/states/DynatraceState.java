/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.waiter.OrchestrationNotifyEventListener.ORCHESTRATION;

import static software.wings.delegatetasks.cv.CVConstants.CONTROL_HOST_NAME;
import static software.wings.delegatetasks.cv.CVConstants.TEST_HOST_NAME;
import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS;
import static software.wings.service.impl.newrelic.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.TaskData;

import software.wings.beans.DynaTraceConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.DataCollectionCallback;
import software.wings.service.impl.dynatrace.DynaTraceDataCollectionInfo;
import software.wings.service.impl.dynatrace.DynaTraceTimeSeries;
import software.wings.service.intfc.dynatrace.DynaTraceService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * Created by rsingh on 2/6/18.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Slf4j
@FieldNameConstants(innerTypeName = "DynatraceStateKeys")
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class DynatraceState extends AbstractMetricAnalysisState {
  @Inject @SchemaIgnore private transient DynaTraceService dynaTraceService;
  @Attributes(required = true, title = "Dynatrace Server") private String analysisServerConfigId;

  private String serviceEntityId;

  public DynatraceState(String name) {
    super(name, StateType.DYNA_TRACE);
  }

  @Override
  public Logger getLogger() {
    return log;
  }

  @Override
  protected String triggerAnalysisDataCollection(ExecutionContext context, AnalysisContext analysisContext,
      VerificationStateAnalysisExecutionData executionData, Map<String, String> hosts) {
    final String resolvedConnectorId =
        getResolvedConnectorId(context, DynatraceStateKeys.analysisServerConfigId, analysisServerConfigId);
    String envId = getEnvId(context);

    final SettingAttribute settingAttribute = settingsService.get(resolvedConnectorId);
    Preconditions.checkNotNull(settingAttribute, "No dynatrace setting with id: " + analysisServerConfigId + " found");
    final long dataCollectionStartTimeStamp = dataCollectionStartTimestampMillis();
    final DynaTraceConfig dynaTraceConfig = (DynaTraceConfig) settingAttribute.getValue();
    String resolvedServiceField = getResolvedFieldValue(context, DynatraceStateKeys.serviceEntityId, serviceEntityId);

    Set<String> entityIdsToQuery = new HashSet<>();
    if (isNotEmpty(resolvedServiceField)) {
      List<String> resolvedServiceIds = Arrays.asList(resolvedServiceField.split(","));
      resolvedServiceIds.replaceAll(String::trim);

      if (resolvedServiceIds.size() > 1
          && !featureFlagService.isEnabled(FeatureName.DYNATRACE_MULTI_SERVICE, context.getAccountId())) {
        throw new DataCollectionException("Dynatrace Multiservice CV is not supported for this account. "
            + "Please contact Harness support");
      }

      resolvedServiceIds.forEach(resolvedServiceEntityId -> {
        // we will do the resolution for serviceID only if it is not empty
        // this is because already existing setups will not have this field.
        String validatedServiceId = null;
        if (isNotEmpty(resolvedServiceEntityId)) {
          // if this is a name, get the corresponding ID.
          try {
            validatedServiceId =
                dynaTraceService.resolveDynatraceServiceNameToId(resolvedConnectorId, resolvedServiceEntityId);
            if (isNotEmpty(validatedServiceId)) {
              resolvedServiceEntityId = validatedServiceId;
            }
          } catch (Exception ex) {
            log.info("Exception while trying to resolve dynatrace service name to id");
          }
          if (validatedServiceId == null) {
            boolean isValidId =
                dynaTraceService.validateDynatraceServiceId(resolvedConnectorId, resolvedServiceEntityId);
            if (!isValidId) {
              throw new DataCollectionException("Invalid serviceId provided in setup: " + resolvedServiceField);
            }
          }
          entityIdsToQuery.add(resolvedServiceEntityId);
        }
      });
    }

    final DynaTraceDataCollectionInfo dataCollectionInfo =
        DynaTraceDataCollectionInfo.builder()
            .dynaTraceConfig(dynaTraceConfig)
            .applicationId(context.getAppId())
            .stateExecutionId(context.getStateExecutionInstanceId())
            .workflowId(context.getWorkflowId())
            .workflowExecutionId(context.getWorkflowExecutionId())
            .serviceId(getPhaseServiceId(context))
            .startTime(dataCollectionStartTimeStamp)
            .collectionTime(Integer.parseInt(getTimeDuration(context)))
            .timeSeriesDefinitions(Lists.newArrayList(DynaTraceTimeSeries.values()))
            .dataCollectionMinute(0)
            .dynatraceServiceIds(entityIdsToQuery)
            .encryptedDataDetails(secretManager.getEncryptionDetails(
                dynaTraceConfig, context.getAppId(), context.getWorkflowExecutionId()))
            .analysisComparisonStrategy(getComparisonStrategy())
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
                      .taskType(TaskType.DYNA_TRACE_METRIC_DATA_COLLECTION_TASK.name())
                      .parameters(new Object[] {dataCollectionInfo})
                      .timeout(TimeUnit.MINUTES.toMillis(Integer.parseInt(getTimeDuration(context)) + 5))
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

  protected Map<String, String> getLastExecutionNodes(ExecutionContext context) {
    Map<String, String> controlHostMap = new HashMap<>();
    for (int i = 1; i <= CANARY_DAYS_TO_COLLECT; i++) {
      controlHostMap.put(CONTROL_HOST_NAME + i, DEFAULT_GROUP_NAME);
    }
    return controlHostMap;
  }

  protected Map<String, String> getCanaryNewHostNames(ExecutionContext context) {
    return Collections.singletonMap(TEST_HOST_NAME, DEFAULT_GROUP_NAME);
  }
  @Override
  protected CVInstanceApiResponse getCVInstanceAPIResponse(ExecutionContext context) {
    return CVInstanceApiResponse.builder()
        .testNodes(getCanaryNewHostNames(context).keySet())
        .controlNodes(getComparisonStrategy() == COMPARE_WITH_PREVIOUS ? Collections.emptySet()
                                                                       : getLastExecutionNodes(context).keySet())
        .newNodesTrafficShiftPercent(Optional.empty())
        .build();
  }

  public static String getMetricTypeForMetric(String metricName) {
    List<DynaTraceTimeSeries> timeSeriesList = Lists.newArrayList(DynaTraceTimeSeries.values());
    return timeSeriesList.stream()
        .filter(timeSeries -> timeSeries.getSavedFieldName().equals(metricName))
        .findAny()
        .map(timeSeries -> timeSeries.getMetricType().name())
        .orElse(null);
  }
}
