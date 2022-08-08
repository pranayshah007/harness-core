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
import software.wings.beans.TaskType;
import software.wings.beans.config.LogzConfig;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisComparisonStrategyProvider;
import software.wings.service.impl.analysis.AnalysisContext.AnalysisContextKeys;
import software.wings.service.impl.analysis.AnalysisTolerance;
import software.wings.service.impl.analysis.AnalysisToleranceProvider;
import software.wings.service.impl.analysis.DataCollectionCallback;
import software.wings.service.impl.elk.ElkQueryType;
import software.wings.service.impl.logz.LogzDataCollectionInfo;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;
import software.wings.stencils.DefaultValue;
import software.wings.stencils.EnumData;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

/**
 * Created by rsingh on 8/21/17.
 */
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class LogzAnalysisState extends ElkAnalysisState {
  public LogzAnalysisState(String name) {
    super(name, StateType.LOGZ.getType());
  }

  @Override
  protected String triggerAnalysisDataCollection(
      ExecutionContext context, VerificationStateAnalysisExecutionData executionData, Set<String> hosts) {
    String envId = getEnvId(context);
    String resolvedServerConfigId =
        getResolvedConnectorId(context, ElkAnalysisStateKeys.analysisServerConfigId, analysisServerConfigId);
    final SettingAttribute settingAttribute = settingsService.get(resolvedServerConfigId);

    Preconditions.checkNotNull(
        settingAttribute, "No logz config setting with id: " + resolvedServerConfigId + " found");

    final LogzConfig logzConfig = (LogzConfig) settingAttribute.getValue();
    final long logCollectionStartTimeStamp = dataCollectionStartTimestampMillis();

    List<Set<String>> batchedHosts = batchHosts(hosts);
    String[] waitIds = new String[batchedHosts.size()];
    List<DelegateTask> delegateTasks = new ArrayList<>();
    int i = 0;
    for (Set<String> hostBatch : batchedHosts) {
      final LogzDataCollectionInfo dataCollectionInfo =
          LogzDataCollectionInfo.builder()
              .logzConfig(logzConfig)
              .accountId(appService.get(context.getAppId()).getAccountId())
              .applicationId(context.getAppId())
              .stateExecutionId(context.getStateExecutionInstanceId())
              .workflowId(getWorkflowId(context))
              .workflowExecutionId(context.getWorkflowExecutionId())
              .serviceId(getPhaseServiceId(context))
              .query(getRenderedQuery())
              .hostnameField(getResolvedFieldValue(context, AnalysisContextKeys.hostNameField, hostnameField))
              .messageField(getResolvedFieldValue(context, ElkAnalysisStateKeys.messageField, messageField))
              .timestampField(DEFAULT_TIME_FIELD)
              .timestampFieldFormat(
                  getResolvedFieldValue(context, ElkAnalysisStateKeys.timestampFormat, getTimestampFormat()))
              .queryType(getQueryType())
              .startTime(logCollectionStartTimeStamp)
              .startMinute(0)
              .collectionTime(Integer.parseInt(getTimeDuration(context)))
              .initialDelayMinutes(DELAY_MINUTES)
              .hosts(hostBatch)
              .encryptedDataDetails(
                  secretManager.getEncryptionDetails(logzConfig, context.getAppId(), context.getWorkflowExecutionId()))
              .build();

      String waitId = generateUuid();
      delegateTasks.add(
          DelegateTask.builder()
              .accountId(appService.get(context.getAppId()).getAccountId())
              .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, context.getAppId())
              .waitId(waitId)
              .data(TaskData.builder()
                        .async(true)
                        .taskType(TaskType.LOGZ_COLLECT_LOG_DATA.name())
                        .parameters(new Object[] {dataCollectionInfo})
                        .timeout(TimeUnit.MINUTES.toMillis(Integer.parseInt(getTimeDuration(context)) + 5))
                        .build())
              .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
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

  @Override
  @Attributes(required = true, title = "Logz Server")
  public String getAnalysisServerConfigId() {
    return analysisServerConfigId;
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
  @Attributes(title = "Analysis Time duration (in minutes)")
  @DefaultValue("15")
  public String getTimeDuration() {
    if (isBlank(timeDuration)) {
      return String.valueOf(15);
    }
    return timeDuration;
  }

  @Override
  @Attributes(required = true, title = "Search Keywords")
  @DefaultValue(".*[e|E]xception.*")
  public String getQuery() {
    return query;
  }

  @Override
  @SchemaIgnore
  public String getIndices() {
    return indices;
  }

  @Override
  @Attributes(required = true, title = "Hostname Field")
  @DefaultValue("hostname")
  public String getHostnameField() {
    return hostnameField;
  }

  @Override
  @Attributes(required = true, title = "Message Field")
  @DefaultValue("message")
  public String getMessageField() {
    return messageField;
  }

  @Override
  @Attributes(title = "Execute with previous steps")
  public boolean getExecuteWithPreviousSteps() {
    return super.isExecuteWithPreviousSteps();
  }

  @Override
  @Attributes(required = true, title = "Query Type")
  @DefaultValue("TERM")
  public ElkQueryType getQueryType() {
    return super.getQueryType();
  }

  @Override
  @Attributes(required = true, title = "Timestamp format")
  @DefaultValue("yyyy-MM-dd'T'HH:mm:ss.SSSX")
  public String getTimestampFormat() {
    return super.getTimestampFormat();
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
  @SchemaIgnore
  public Logger getLogger() {
    return log;
  }

  @Override
  protected boolean isCVTaskEnqueuingEnabled(String accountId) {
    return false;
  }
}
