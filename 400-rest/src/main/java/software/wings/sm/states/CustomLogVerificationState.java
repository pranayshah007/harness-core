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

import static software.wings.common.VerificationConstants.URL_BODY_APPENDER;
import static software.wings.common.VerificationConstants.VERIFICATION_HOST_PLACEHOLDER;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;

import software.wings.beans.APMVerificationConfig;
import software.wings.beans.LogCollectionInfo;
import software.wings.beans.LogResponseMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.apm.Method;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.beans.CustomLogResponseMapper;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisComparisonStrategyProvider;
import software.wings.service.impl.analysis.AnalysisTolerance;
import software.wings.service.impl.analysis.AnalysisToleranceProvider;
import software.wings.service.impl.analysis.CustomLogDataCollectionInfo;
import software.wings.service.impl.analysis.DataCollectionCallback;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;
import software.wings.stencils.DefaultValue;
import software.wings.stencils.EnumData;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.github.reinert.jjschema.Attributes;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * @author Praveen
 */
@Slf4j
@FieldNameConstants(innerTypeName = "CustomLogVerificationStateKeys")
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class CustomLogVerificationState extends AbstractLogAnalysisState {
  public CustomLogVerificationState(String name) {
    super(name, StateType.LOG_VERIFICATION.getType());
  }

  private List<LogCollectionInfo> logCollectionInfos;

  public void setLogCollectionInfos(List<LogCollectionInfo> collectionInfos) {
    this.logCollectionInfos = collectionInfos;
    if (isNotEmpty(collectionInfos)) {
      this.query = collectionInfos.get(0).getCollectionUrl();
    }
  }

  public List<LogCollectionInfo> getLogCollectionInfos() {
    return this.logCollectionInfos;
  }

  @Attributes(required = true, title = "Log Server") private String analysisServerConfigId;

  @Override
  public String getAnalysisServerConfigId() {
    return analysisServerConfigId;
  }

  @Override
  public void setAnalysisServerConfigId(String analysisServerConfigId) {
    this.analysisServerConfigId = analysisServerConfigId;
  }

  @Attributes(required = false, title = "Log DataCollection Rate (mins)") private int dataCollectionRate;

  private int getDataCollectionRate() {
    return dataCollectionRate < 1 ? 1 : dataCollectionRate;
  }

  public void setDataCollectionRate(int dataCollectionRate) {
    this.dataCollectionRate = dataCollectionRate;
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

  @Attributes(title = "Analysis Time duration (in minutes)")
  @DefaultValue("15")
  @Override
  public String getTimeDuration() {
    if (isBlank(timeDuration)) {
      return String.valueOf(15);
    }
    return timeDuration;
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
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = new HashMap<>();
    if (isEmpty(logCollectionInfos)) {
      invalidFields.put("Log Collection Info", "Log collection info should not be empty");
    }
    return invalidFields;
  }

  @Override
  public Logger getLogger() {
    return log;
  }

  @Override
  protected String triggerAnalysisDataCollection(
      ExecutionContext context, VerificationStateAnalysisExecutionData data, Set<String> hosts) {
    String envId = getEnvId(context);

    String serverConfigId =
        getResolvedConnectorId(context, CustomLogVerificationStateKeys.analysisServerConfigId, analysisServerConfigId);

    SettingAttribute settingAttribute = getSettingAttribute(serverConfigId);

    final APMVerificationConfig logConfig = (APMVerificationConfig) settingAttribute.getValue();
    boolean shouldDoHostbasedFiltering = shouldInspectHostsForLogAnalysis();
    final long dataCollectionStartTimeStamp = dataCollectionStartTimestampMillis();
    String accountId = appService.get(context.getAppId()).getAccountId();

    Map<String, Map<String, CustomLogResponseMapper>> logDefinitions =
        constructLogDefinitions(context, logCollectionInfos);
    CustomLogDataCollectionInfo dataCollectionInfo =
        CustomLogDataCollectionInfo.builder()
            .baseUrl(logConfig.getUrl())
            .validationUrl(logConfig.getValidationUrl())
            .headers(logConfig.collectionHeaders())
            .options(logConfig.collectionParams())
            .encryptedDataDetails(logConfig.encryptedDataDetails(secretManager))
            .hosts(hosts)
            .query(getRenderedQuery())
            .stateType(DelegateStateType.LOG_VERIFICATION)
            .applicationId(context.getAppId())
            .stateExecutionId(context.getStateExecutionInstanceId())
            .workflowId(getWorkflowId(context))
            .workflowExecutionId(context.getWorkflowExecutionId())
            .serviceId(getPhaseServiceId(context))
            .startTime(dataCollectionStartTimeStamp)
            .startMinute(0)
            .responseDefinition(logDefinitions)
            .collectionFrequency(getDataCollectionRate())
            .collectionTime(Integer.parseInt(getTimeDuration(context)))
            .accountId(accountId)
            .shouldDoHostBasedFiltering(shouldDoHostbasedFiltering)
            .build();

    String waitId = generateUuid();
    String infrastructureMappingId = context.fetchInfraMappingId();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, context.getAppId())
            .waitId(waitId)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.CUSTOM_LOG_COLLECTION_TASK.name())
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
            .executionData(data)
            .build(),
        waitId);
    return delegateService.queueTask(delegateTask);
  }

  @Override
  protected boolean shouldInspectHostsForLogAnalysis() {
    boolean shouldDoHostbasedFiltering = true;
    if (getComparisonStrategy().equals(AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS)) {
      shouldDoHostbasedFiltering = logCollectionInfos.stream().allMatch(logCollectionInfo
          -> logCollectionInfo.getCollectionUrl().contains(VERIFICATION_HOST_PLACEHOLDER)
              || (logCollectionInfo.getCollectionBody() != null
                  && logCollectionInfo.getCollectionBody().contains(VERIFICATION_HOST_PLACEHOLDER)));
    }
    return shouldDoHostbasedFiltering;
  }

  public static Map<String, Map<String, CustomLogResponseMapper>> constructLogDefinitions(
      final ExecutionContext context, final List<LogCollectionInfo> logCollectionInfos) {
    Map<String, Map<String, CustomLogResponseMapper>> logDefinition = new HashMap<>();
    for (LogCollectionInfo logInfo : logCollectionInfos) {
      String evaluatedUrl =
          context != null ? context.renderExpression(logInfo.getCollectionUrl()) : logInfo.getCollectionUrl();
      if (logInfo.getMethod() != null && logInfo.getMethod() == Method.POST) {
        String body = logInfo.getCollectionBody();
        if (context != null) {
          body = context.renderExpression(body);
        }
        evaluatedUrl += URL_BODY_APPENDER + body;
      }
      logDefinition.put(evaluatedUrl, getResponseMappers(logInfo));
    }
    return logDefinition;
  }

  private static Map<String, CustomLogResponseMapper> getResponseMappers(LogCollectionInfo logCollectionInfo) {
    LogResponseMapping responseMapping = logCollectionInfo.getResponseMapping();
    Map<String, CustomLogResponseMapper> responseMappers = new HashMap<>();

    // Set the host details (if exists) in the responseMapper
    if (!isEmpty(responseMapping.getHostJsonPath())) {
      List hostJsonList = new ArrayList();
      String hostJson = responseMapping.getHostJsonPath();
      hostJsonList.add(hostJson);
      List<String> hostRegex =
          isEmpty(responseMapping.getHostRegex()) ? null : Lists.newArrayList(responseMapping.getHostRegex());
      CustomLogResponseMapper hostResponseMapper =
          CustomLogResponseMapper.builder().fieldName("host").regexs(hostRegex).jsonPath(hostJsonList).build();
      responseMappers.put("host", hostResponseMapper);
    }
    List timestampJsonList = new ArrayList(), logMsgList = new ArrayList();
    timestampJsonList.add(responseMapping.getTimestampJsonPath());
    responseMappers.put("timestamp",
        CustomLogResponseMapper.builder()
            .fieldName("timestamp")
            .jsonPath(timestampJsonList)
            .timestampFormat(responseMapping.getTimestampFormat())
            .build());

    logMsgList.add(responseMapping.getLogMessageJsonPath());
    responseMappers.put(
        "logMessage", CustomLogResponseMapper.builder().fieldName("logMessage").jsonPath(logMsgList).build());

    return responseMappers;
  }
}
