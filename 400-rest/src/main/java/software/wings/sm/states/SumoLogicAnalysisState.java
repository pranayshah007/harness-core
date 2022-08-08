/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.waiter.OrchestrationNotifyEventListener.ORCHESTRATION;

import static software.wings.common.VerificationConstants.DELAY_MINUTES;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SumoConfig;
import software.wings.beans.TaskType;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisComparisonStrategyProvider;
import software.wings.service.impl.analysis.AnalysisTolerance;
import software.wings.service.impl.analysis.AnalysisToleranceProvider;
import software.wings.service.impl.analysis.DataCollectionCallback;
import software.wings.service.impl.sumo.SumoDataCollectionInfo;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;
import software.wings.stencils.DefaultValue;
import software.wings.stencils.EnumData;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.github.reinert.jjschema.Attributes;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

/**
 * Created by sriram_parthasarathy on 9/11/17.
 */
@Slf4j
@FieldNameConstants(innerTypeName = "SumoLogicAnalysisStateKeys")
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class SumoLogicAnalysisState extends AbstractLogAnalysisState {
  @Attributes(required = true, title = "Sumo Logic Server") protected String analysisServerConfigId;

  public SumoLogicAnalysisState(String name) {
    super(name, StateType.SUMO.getType());
  }

  @Override
  @EnumData(enumDataProvider = AnalysisComparisonStrategyProvider.class)
  @Attributes(required = true, title = "Baseline for Risk Analysis")
  @DefaultValue("COMPARE_WITH_PREVIOUS")
  public AnalysisComparisonStrategy getComparisonStrategy() {
    if (isBlank(comparisonStrategy)) {
      return AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS;
    }
    return AnalysisComparisonStrategy.valueOf(comparisonStrategy);
  }

  @Override
  @EnumData(enumDataProvider = AnalysisToleranceProvider.class)
  @Attributes(required = true, title = "Algorithm Sensitivity")
  @DefaultValue("MEDIUM")
  public AnalysisTolerance getAnalysisTolerance() {
    if (isBlank(tolerance)) {
      return AnalysisTolerance.LOW;
    }
    return AnalysisTolerance.valueOf(tolerance);
  }

  @Override
  @Attributes(required = true, title = "Search Keywords")
  @DefaultValue("*exception*")
  public String getQuery() {
    return query;
  }

  @Override
  public Logger getLogger() {
    return log;
  }

  @Override
  @Attributes(required = true, title = "Sumo Logic Server")
  public String getAnalysisServerConfigId() {
    return analysisServerConfigId;
  }

  @Attributes(title = "Execute with previous steps")
  public boolean getExecuteWithPreviousSteps() {
    return super.isExecuteWithPreviousSteps();
  }

  @Override
  public void setAnalysisServerConfigId(String analysisServerConfigId) {
    this.analysisServerConfigId = analysisServerConfigId;
  }

  @Override
  protected String triggerAnalysisDataCollection(
      ExecutionContext context, VerificationStateAnalysisExecutionData executionData, Set<String> hosts) {
    String envId = getEnvId(context);

    String finalServerConfigId =
        getResolvedConnectorId(context, SumoLogicAnalysisStateKeys.analysisServerConfigId, analysisServerConfigId);
    SettingAttribute settingAttribute = getSettingAttribute(finalServerConfigId);

    final SumoConfig sumoConfig = (SumoConfig) settingAttribute.getValue();
    final long logCollectionStartTimeStamp = dataCollectionStartTimestampMillis();

    List<Set<String>> batchedHosts = batchHosts(hosts);
    String[] waitIds = new String[batchedHosts.size()];
    List<DelegateTask> delegateTasks = new ArrayList<>();
    int i = 0;

    for (Set<String> hostBatch : batchedHosts) {
      final SumoDataCollectionInfo dataCollectionInfo =
          SumoDataCollectionInfo.builder()
              .sumoConfig(sumoConfig)
              .accountId(appService.get(context.getAppId()).getAccountId())
              .applicationId(context.getAppId())
              .stateExecutionId(context.getStateExecutionInstanceId())
              .workflowId(getWorkflowId(context))
              .workflowExecutionId(context.getWorkflowExecutionId())
              .serviceId(getPhaseServiceId(context))
              .query(getRenderedQuery())
              .startTime(logCollectionStartTimeStamp)
              .startMinute((int) (logCollectionStartTimeStamp / TimeUnit.MINUTES.toMillis(1)))
              .collectionTime(Integer.parseInt(getTimeDuration(context)))
              .hosts(hostBatch)
              .encryptedDataDetails(
                  secretManager.getEncryptionDetails(sumoConfig, context.getAppId(), context.getWorkflowExecutionId()))
              .hostnameField(getHostnameField(context))
              .initialDelayMinutes(DELAY_MINUTES)
              .build();

      String waitId = generateUuid();
      String infrastructureMappingId = context.fetchInfraMappingId();
      delegateTasks.add(
          DelegateTask.builder()
              .accountId(appService.get(context.getAppId()).getAccountId())
              .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, context.getAppId())
              .waitId(waitId)
              .data(TaskData.builder()
                        .async(true)
                        .taskType(TaskType.SUMO_COLLECT_LOG_DATA.name())
                        .parameters(new Object[] {dataCollectionInfo})
                        .timeout(TimeUnit.MINUTES.toMillis(Integer.parseInt(getTimeDuration(context)) + 60))
                        .build())
              .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
              .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infrastructureMappingId)
              .build());
      waitIds[i++] = waitId;
    }
    waitNotifyEngine.waitForAllOn(ORCHESTRATION,
        DataCollectionCallback.builder()
            .appId(context.getAppId())
            .stateExecutionId(context.getStateExecutionInstanceId())
            .dataCollectionStartTime(logCollectionStartTimeStamp)
            .dataCollectionEndTime(
                logCollectionStartTimeStamp + TimeUnit.MINUTES.toMillis(Integer.parseInt(getTimeDuration(context))))
            .executionData(executionData)
            .build(),
        waitIds);
    List<String> delegateTaskIds = new ArrayList<>();
    for (DelegateTask task : delegateTasks) {
      delegateTaskIds.add(delegateService.queueTask(task));
    }
    return StringUtils.join(delegateTaskIds, ",");
  }

  @DefaultValue("_sourceHost")
  @Attributes(required = true, title = "Field name for Host/Container")
  public String getHostnameField() {
    return hostnameField;
  }

  public void setHostnameField(String hostnameField) {
    this.hostnameField = hostnameField;
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

  @Override
  @Attributes(title = "Analysis Time duration (in minutes)", description = "Default 15 minutes")
  @DefaultValue("15")
  public String getTimeDuration() {
    if (isBlank(timeDuration)) {
      return String.valueOf(15);
    }
    return timeDuration;
  }

  // for backward compatibilty we need to make "_sourceHost" and "_sourceName" lowercase
  @Override
  protected String getHostnameField(ExecutionContext context) {
    Preconditions.checkNotNull(hostnameField, "hostnameField can't be null");
    switch (hostnameField) {
      case "_sourceHost":
        return hostnameField.toLowerCase();
      case "_sourceName":
        return hostnameField.toLowerCase();
      default:
        return getResolvedFieldValue(context, AbstractAnalysisStateKeys.hostnameField, hostnameField);
    }
  }
}
