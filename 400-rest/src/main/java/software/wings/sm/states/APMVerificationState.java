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
import static software.wings.delegatetasks.cv.CVConstants.CONTROL_HOST_NAME;
import static software.wings.delegatetasks.cv.CVConstants.TEST_HOST_NAME;
import static software.wings.service.impl.apm.APMMetricInfo.ResponseMapper;
import static software.wings.service.impl.newrelic.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.WingsException;

import software.wings.beans.APMVerificationConfig;
import software.wings.beans.ApmMetricCollectionInfo;
import software.wings.beans.ApmResponseMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.apm.Method;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.DataCollectionCallback;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.TimeSeriesMetricGroup;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.apm.APMDataCollectionInfo;
import software.wings.service.impl.apm.APMMetricInfo;
import software.wings.service.impl.apm.CustomAPMDataCollectionInfo;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;
import software.wings.stencils.DefaultValue;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.github.reinert.jjschema.Attributes;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

@Slf4j
@FieldNameConstants(innerTypeName = "APMVerificationStateKeys")
@OwnedBy(HarnessTeam.CV)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class APMVerificationState extends AbstractMetricAnalysisState {
  public APMVerificationState(String name) {
    super(name, StateType.APM_VERIFICATION);
  }

  @Attributes(required = true, title = "APM Server") private String analysisServerConfigId;

  private List<ApmMetricCollectionInfo> metricCollectionInfos;

  public void setMetricCollectionInfos(List<ApmMetricCollectionInfo> metricCollectionInfos) {
    this.metricCollectionInfos = metricCollectionInfos;
  }

  public List<ApmMetricCollectionInfo> getMetricCollectionInfos() {
    return this.metricCollectionInfos;
  }

  @Attributes(required = false, title = "APM DataCollection Rate (mins)") private int dataCollectionRate;

  @Override
  public int getDataCollectionRate() {
    return dataCollectionRate < 1 ? 1 : dataCollectionRate;
  }

  public void setDataCollectionRate(int dataCollectionRate) {
    this.dataCollectionRate = dataCollectionRate;
  }

  @Override
  @Attributes(title = "Expression for Host/Container name")
  @DefaultValue("")
  public String getHostnameTemplate() {
    return hostnameTemplate;
  }

  @Override
  public void setHostnameTemplate(String hostnameTemplate) {
    this.hostnameTemplate = hostnameTemplate;
  }

  @Override
  @Attributes(required = true, title = "Include nodes from previous phases")
  public boolean getIncludePreviousPhaseNodes() {
    return includePreviousPhaseNodes;
  }

  @Override
  public void setIncludePreviousPhaseNodes(boolean includePreviousPhaseNodes) {
    this.includePreviousPhaseNodes = includePreviousPhaseNodes;
  }

  @Attributes(title = "Initial Delay (10s, 30s, 1m, 2m")
  @DefaultValue("2m")
  public String getInitialAnalysisDelay() {
    return initialAnalysisDelay;
  }

  public void setInitialAnalysisDelay(String initialDelay) {
    this.initialAnalysisDelay = initialDelay;
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

  @Override
  protected Optional<FeatureName> getCVTaskFeatureName() {
    return Optional.of(FeatureName.CUSTOM_APM_CV_TASK);
  }

  @Override
  protected DataCollectionInfoV2 createDataCollectionInfo(
      ExecutionContext context, Map<String, String> hostsToCollect) {
    String envId = getEnvId(context);

    List<APMMetricInfo> canaryMetricInfos = getCanaryMetricInfos(context);
    Map<String, List<APMMetricInfo>> apmMetricInfos = isNotEmpty(canaryMetricInfos)
        ? new HashMap<>()
        : buildMetricInfoMap(metricCollectionInfos, Optional.of(context));

    metricAnalysisService.saveMetricTemplates(context.getAppId(), StateType.APM_VERIFICATION,
        context.getStateExecutionInstanceId(), null,
        metricDefinitions(
            isNotEmpty(canaryMetricInfos) ? Collections.singletonList(canaryMetricInfos) : apmMetricInfos.values()));
    APMVerificationConfig apmConfig = getApmVerificationConfig(context);
    return CustomAPMDataCollectionInfo.builder()
        .connectorId(
            getResolvedConnectorId(context, APMVerificationStateKeys.analysisServerConfigId, analysisServerConfigId))
        .workflowExecutionId(context.getWorkflowExecutionId())
        .stateExecutionId(context.getStateExecutionInstanceId())
        .workflowId(context.getWorkflowId())
        .accountId(context.getAccountId())
        .envId(envId)
        .applicationId(context.getAppId())
        .hosts(hostsToCollect.keySet())
        .apmConfig(apmConfig)
        .headers(apmConfig.collectionHeaders())
        .options(apmConfig.collectionParams())
        .metricEndpoints(buildMetricInfoList(metricCollectionInfos, Optional.of(context)))
        .canaryMetricInfos(canaryMetricInfos)
        .hostsToGroupNameMap(hostsToCollect)
        .serviceId(getPhaseServiceId(context))
        .build();
  }

  @Override
  protected boolean isHistoricalAnalysis(String accountId) {
    if (!getCVTaskFeatureName().isPresent() || !featureFlagService.isEnabled(getCVTaskFeatureName().get(), accountId)) {
      return false;
    }

    boolean isHistorical = true;
    if (isNotEmpty(metricCollectionInfos)) {
      for (ApmMetricCollectionInfo metricCollectionInfo : metricCollectionInfos) {
        if ((isNotEmpty(metricCollectionInfo.getCollectionUrl())
                && metricCollectionInfo.getCollectionUrl().contains(VERIFICATION_HOST_PLACEHOLDER))
            || (isNotEmpty(metricCollectionInfo.getCollectionBody())
                && metricCollectionInfo.getCollectionBody().contains(VERIFICATION_HOST_PLACEHOLDER))) {
          isHistorical = false;
        }
      }
      return isHistorical;
    }
    return false;
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = new HashMap<>();
    if (isEmpty(metricCollectionInfos)) {
      invalidFields.put("Metric Collection Info", "Metric collection info should not be empty");
      return invalidFields;
    }

    if (isNotEmpty(initialAnalysisDelay)) {
      int delaySeconds = getDelaySeconds(initialAnalysisDelay);
      if (delaySeconds > 5 * 60) {
        invalidFields.put("initialAnalysisDelay", "Initial Delay can be 5mins at most");
      }
    }
    AtomicBoolean hasBaselineUrl = new AtomicBoolean(false);
    metricCollectionInfos.forEach(metricCollectionInfo -> {
      if (isEmpty(metricCollectionInfo.getMetricName())) {
        invalidFields.put("metricName", "MetricName is empty");
        return;
      }

      if (isEmpty(metricCollectionInfo.getCollectionUrl())) {
        invalidFields.put(
            "collectionUrl", "Metric Collection URL is empty for metric " + metricCollectionInfo.getMetricName());
        return;
      }

      if (!metricCollectionInfo.getCollectionUrl().contains("${host}")) {
        if (isEmpty(metricCollectionInfo.getCollectionBody())
            || !metricCollectionInfo.getCollectionBody().contains("${host}")) {
          invalidFields.put("collectionUrl", "MetricCollection URL or body should contain ${host}");
        }
      }
      if (!metricCollectionInfo.getCollectionUrl().contains("${start_time}")
          && !metricCollectionInfo.getCollectionUrl().contains("${start_time_seconds}")) {
        if (isEmpty(metricCollectionInfo.getCollectionBody())
            || (!metricCollectionInfo.getCollectionBody().contains("${start_time}")
                && !metricCollectionInfo.getCollectionBody().contains("${start_time_seconds}"))) {
          invalidFields.put(
              "collectionUrl", "MetricCollection URL or body should contain ${start_time} or ${start_time_seconds}");
        }
      }

      if (!metricCollectionInfo.getCollectionUrl().contains("${end_time}")
          && !metricCollectionInfo.getCollectionUrl().contains("${end_time_seconds}")) {
        if (isEmpty(metricCollectionInfo.getCollectionBody())
            || (!metricCollectionInfo.getCollectionBody().contains("${end_time}")
                && !metricCollectionInfo.getCollectionBody().contains("${end_time_seconds}"))) {
          invalidFields.put(
              "collectionUrl", "MetricCollection URL or body should contain ${end_time} or ${end_time_seconds}");
        }
      }

      if (metricCollectionInfo.getResponseMapping() == null) {
        invalidFields.put("responseMapping",
            "Valid JSON Mappings for the response have not been provided for " + metricCollectionInfo.getMetricName());
        return;
      }

      ApmResponseMapping mapping = metricCollectionInfo.getResponseMapping();

      if (isEmpty(mapping.getMetricValueJsonPath())) {
        invalidFields.put("metricValueJsonPath/timestampJsonPath",
            "Metric value path is empty for " + metricCollectionInfo.getMetricName());
        return;
      }

      if (isEmpty(mapping.getTxnNameFieldValue()) && isEmpty(mapping.getTxnNameJsonPath())) {
        invalidFields.put("transactionName", "Transaction Name is empty for " + metricCollectionInfo.getMetricName());
        return;
      }

      if (metricCollectionInfo.getCollectionUrl().contains(VERIFICATION_HOST_PLACEHOLDER)
          && isNotEmpty(metricCollectionInfo.getBaselineCollectionUrl())) {
        invalidFields.put("collectionUrl",
            "for " + metricCollectionInfo.getMetricName() + " the collection url has " + VERIFICATION_HOST_PLACEHOLDER
                + " and baseline collection url as well");
        return;
      }

      if (hasBaselineUrl.get() && metricCollectionInfo.getCollectionUrl().contains(VERIFICATION_HOST_PLACEHOLDER)) {
        invalidFields.put("collectionUrl",
            "for " + metricCollectionInfo.getMetricName() + " the url has " + VERIFICATION_HOST_PLACEHOLDER
                + ". When configuring multi url verification all metrics should follow the same pattern.");
        return;
      }

      if (isNotEmpty(metricCollectionInfo.getBaselineCollectionUrl())) {
        hasBaselineUrl.set(true);
        if (AnalysisComparisonStrategy.COMPARE_WITH_CURRENT != getComparisonStrategy()) {
          invalidFields.put("collectionUrl",
              "Baseline url can only be set for canary verification strategy. For "
                  + metricCollectionInfo.getMetricName() + " there is baseline url set "
                  + VERIFICATION_HOST_PLACEHOLDER);
          return;
        }

        if (metricCollectionInfo.getBaselineCollectionUrl().contains(VERIFICATION_HOST_PLACEHOLDER)) {
          invalidFields.put("collectionUrl",
              "for " + metricCollectionInfo.getMetricName() + " baseline url contains "
                  + VERIFICATION_HOST_PLACEHOLDER);
          return;
        }
      }
    });

    return invalidFields;
  }

  public static Map<String, TimeSeriesMetricDefinition> metricDefinitions(Collection<List<APMMetricInfo>> metricInfos) {
    Map<String, TimeSeriesMetricDefinition> metricTypeMap = new HashMap<>();
    metricInfos.forEach(metricInfoList
        -> metricInfoList.forEach(metricInfo
            -> metricTypeMap.put(metricInfo.getMetricName(),
                TimeSeriesMetricDefinition.builder()
                    .metricName(metricInfo.getMetricName())
                    .metricType(metricInfo.getMetricType())
                    .tags(Sets.newHashSet(metricInfo.getTag()))
                    .build())));
    return metricTypeMap;
  }

  public static Map<String, TimeSeriesMetricGroup.TimeSeriesMlAnalysisGroupInfo> metricGroup(
      Map<String, List<APMMetricInfo>> metricInfos) {
    Set<String> groups = new HashSet<>();
    for (List<APMMetricInfo> metricInfoList : metricInfos.values()) {
      for (APMMetricInfo metricInfo : metricInfoList) {
        groups.add(metricInfo.getTag());
      }
    }
    Map<String, TimeSeriesMetricGroup.TimeSeriesMlAnalysisGroupInfo> groupInfoMap = new HashMap<>();
    for (String group : groups) {
      groupInfoMap.put(group,
          TimeSeriesMetricGroup.TimeSeriesMlAnalysisGroupInfo.builder()
              .groupName(group)
              .mlAnalysisType(TimeSeriesMlAnalysisType.COMPARATIVE)
              .build());
    }
    if (groupInfoMap.size() == 0) {
      throw new WingsException("No Metric Group Names found. This is a required field");
    }
    return groupInfoMap;
  }

  private APMVerificationConfig getApmVerificationConfig(ExecutionContext context) {
    String serverConfigId =
        getResolvedConnectorId(context, APMVerificationStateKeys.analysisServerConfigId, analysisServerConfigId);
    SettingAttribute settingAttribute = settingsService.get(serverConfigId);
    Preconditions.checkNotNull(settingAttribute, "No AMP settings with id: " + analysisServerConfigId + " found");
    return (APMVerificationConfig) settingAttribute.getValue();
  }
  @Override
  protected String triggerAnalysisDataCollection(ExecutionContext context, AnalysisContext analysisContext,
      VerificationStateAnalysisExecutionData executionData, Map<String, String> hosts) {
    String envId = getEnvId(context);
    String serverConfigId =
        getResolvedConnectorId(context, APMVerificationStateKeys.analysisServerConfigId, analysisServerConfigId);
    SettingAttribute settingAttribute = settingsService.get(serverConfigId);
    Preconditions.checkNotNull(settingAttribute, "No AMP settings with id: " + analysisServerConfigId + " found");
    final APMVerificationConfig apmConfig = (APMVerificationConfig) settingAttribute.getValue();

    List<APMMetricInfo> canaryMetricInfos = getCanaryMetricInfos(context);
    Map<String, List<APMMetricInfo>> apmMetricInfos = isNotEmpty(canaryMetricInfos)
        ? new HashMap<>()
        : buildMetricInfoMap(metricCollectionInfos, Optional.of(context));

    metricAnalysisService.saveMetricTemplates(context.getAppId(), StateType.APM_VERIFICATION,
        context.getStateExecutionInstanceId(), null,
        metricDefinitions(
            isNotEmpty(canaryMetricInfos) ? Collections.singletonList(canaryMetricInfos) : apmMetricInfos.values()));
    final long dataCollectionStartTimeStamp = dataCollectionStartTimestampMillis();
    String accountId = appService.get(context.getAppId()).getAccountId();
    final APMDataCollectionInfo dataCollectionInfo =
        APMDataCollectionInfo.builder()
            .baseUrl(apmConfig.getUrl())
            .validationUrl(apmConfig.getValidationUrl())
            .headers(apmConfig.collectionHeaders())
            .options(apmConfig.collectionParams())
            .encryptedDataDetails(apmConfig.encryptedDataDetails(secretManager))
            .hosts(hosts)
            .stateType(DelegateStateType.APM_VERIFICATION)
            .applicationId(context.getAppId())
            .stateExecutionId(context.getStateExecutionInstanceId())
            .workflowId(getWorkflowId(context))
            .workflowExecutionId(context.getWorkflowExecutionId())
            .serviceId(getPhaseServiceId(context))
            .startTime(dataCollectionStartTimeStamp)
            .dataCollectionMinute(0)
            .dataCollectionFrequency(getDataCollectionRate())
            .dataCollectionTotalTime(Integer.parseInt(getTimeDuration(context)))
            .metricEndpoints(apmMetricInfos)
            .canaryMetricInfos(canaryMetricInfos)
            .accountId(accountId)
            .strategy(getComparisonStrategy())
            .initialDelaySeconds(getDelaySeconds(initialAnalysisDelay))
            .validateCert(accountService.isCertValidationRequired(accountId))
            .build();

    analysisContext.getTestNodes().put(TEST_HOST_NAME, DEFAULT_GROUP_NAME);
    if (getComparisonStrategy() == AnalysisComparisonStrategy.COMPARE_WITH_CURRENT) {
      analysisContext.getControlNodes().put(CONTROL_HOST_NAME, DEFAULT_GROUP_NAME);
      for (int i = 1; i <= CANARY_DAYS_TO_COLLECT; ++i) {
        analysisContext.getControlNodes().put(CONTROL_HOST_NAME + "-" + i, DEFAULT_GROUP_NAME);
      }
    }

    String waitId = generateUuid();
    String infrastructureMappingId = context.fetchInfraMappingId();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, context.getAppId())
            .waitId(waitId)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.APM_METRIC_DATA_COLLECTION_TASK.name())
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
            .build(),
        waitId);
    return delegateService.queueTask(delegateTask);
  }

  public static Map<String, List<APMMetricInfo>> buildMetricInfoMap(
      List<ApmMetricCollectionInfo> metricCollectionInfos, Optional<ExecutionContext> context) {
    Map<String, List<APMMetricInfo>> metricInfoMap = new HashMap<>();
    for (ApmMetricCollectionInfo metricCollectionInfo : metricCollectionInfos) {
      String evaluatedUrl = context != null && context.isPresent()
          ? context.get().renderExpression(metricCollectionInfo.getCollectionUrl())
          : metricCollectionInfo.getCollectionUrl();

      if (metricCollectionInfo.getMethod() != null && metricCollectionInfo.getMethod() == Method.POST) {
        evaluatedUrl += URL_BODY_APPENDER + metricCollectionInfo.getCollectionBody();
      }

      if (!metricInfoMap.containsKey(evaluatedUrl)) {
        metricInfoMap.put(evaluatedUrl, new ArrayList<>());
      }
      String evaluatedBody = context != null && context.isPresent()
          ? context.get().renderExpression(metricCollectionInfo.getCollectionBody())
          : metricCollectionInfo.getCollectionBody();

      // render expressions for the responseMappers also.
      if (context != null && context.isPresent()) {
        metricCollectionInfo.setResponseMapping(
            evaluateResponseMappingForExpressions(context.get(), metricCollectionInfo.getResponseMapping()));
      }

      APMMetricInfo metricInfo = APMMetricInfo.builder()
                                     .metricName(metricCollectionInfo.getMetricName())
                                     .metricType(metricCollectionInfo.getMetricType())
                                     .method(metricCollectionInfo.getMethod())
                                     .body(evaluatedBody)
                                     .tag(metricCollectionInfo.getTag())
                                     .responseMappers(getResponseMappers(metricCollectionInfo))
                                     .build();
      log.info("In APMMetricInfos, evaluatedUrl is: {}", evaluatedUrl);
      metricInfoMap.get(evaluatedUrl).add(metricInfo);
    }
    return metricInfoMap;
  }

  private static ApmResponseMapping evaluateResponseMappingForExpressions(
      ExecutionContext context, ApmResponseMapping responseMapping) {
    return ApmResponseMapping.builder()
        .txnNameJsonPath(renderExpressionIfNotEmpty(context, responseMapping.getTxnNameJsonPath()))
        .hostJsonPath(renderExpressionIfNotEmpty(context, responseMapping.getHostJsonPath()))
        .metricValueJsonPath(renderExpressionIfNotEmpty(context, responseMapping.getMetricValueJsonPath()))
        .timestampJsonPath(renderExpressionIfNotEmpty(context, responseMapping.getTimestampJsonPath()))
        .timeStampFormat(responseMapping.getTimeStampFormat())
        .hostRegex(renderExpressionIfNotEmpty(context, responseMapping.getHostRegex()))
        .txnNameFieldValue(renderExpressionIfNotEmpty(context, responseMapping.getTxnNameFieldValue()))
        .txnNameRegex(renderExpressionIfNotEmpty(context, responseMapping.getTxnNameRegex()))
        .build();
  }

  private static String renderExpressionIfNotEmpty(ExecutionContext context, String fieldValue) {
    if (isNotEmpty(fieldValue)) {
      fieldValue = context.renderExpression(fieldValue);
    }
    return fieldValue;
  }

  public static List<APMMetricInfo> buildMetricInfoList(
      List<ApmMetricCollectionInfo> metricCollectionInfos, Optional<ExecutionContext> context) {
    List<APMMetricInfo> metricInfoList = new ArrayList<>();
    for (ApmMetricCollectionInfo metricCollectionInfo : metricCollectionInfos) {
      String evaluatedUrl = context != null && context.isPresent()
          ? context.get().renderExpression(metricCollectionInfo.getCollectionUrl())
          : metricCollectionInfo.getCollectionUrl();

      if (metricCollectionInfo.getMethod() != null && metricCollectionInfo.getMethod() == Method.POST) {
        evaluatedUrl += URL_BODY_APPENDER + metricCollectionInfo.getCollectionBody();
      }

      String evaluatedBody = context != null && context.isPresent()
          ? context.get().renderExpression(metricCollectionInfo.getCollectionBody())
          : metricCollectionInfo.getCollectionBody();
      APMMetricInfo metricInfo = APMMetricInfo.builder()
                                     .metricName(metricCollectionInfo.getMetricName())
                                     .metricType(metricCollectionInfo.getMetricType())
                                     .method(metricCollectionInfo.getMethod())
                                     .body(evaluatedBody)
                                     .url(evaluatedUrl)
                                     .tag(metricCollectionInfo.getTag())
                                     .responseMappers(getResponseMappers(metricCollectionInfo))
                                     .build();
      log.info("In APMMetricInfos, evaluatedUrl is: {}", evaluatedUrl);
      metricInfoList.add(metricInfo);
    }
    return metricInfoList;
  }

  private List<APMMetricInfo> getCanaryMetricInfos(final ExecutionContext context) {
    if (AnalysisComparisonStrategy.COMPARE_WITH_CURRENT != getComparisonStrategy()) {
      return Collections.emptyList();
    }

    List<APMMetricInfo> metricInfos = new ArrayList<>();
    for (ApmMetricCollectionInfo metricCollectionInfo : metricCollectionInfos) {
      final String collectionUrl = metricCollectionInfo.getCollectionUrl();
      if (collectionUrl != null && collectionUrl.contains("\n") && collectionUrl.split("\n").length == 2) {
        final String[] canaryCollectionUrls = collectionUrl.split("\n");
        log.info("for {} canary url is provided", context.getStateExecutionInstanceId());
        metricCollectionInfo.setCollectionUrl(canaryCollectionUrls[0]);
        metricCollectionInfo.setBaselineCollectionUrl(canaryCollectionUrls[1]);
      }
      if (isEmpty(metricCollectionInfo.getBaselineCollectionUrl())) {
        continue;
      }
      String testUrl = context.renderExpression(metricCollectionInfo.getCollectionUrl());
      String controlUrl = context.renderExpression(metricCollectionInfo.getBaselineCollectionUrl());

      if (metricCollectionInfo.getMethod() != null && metricCollectionInfo.getMethod() == Method.POST) {
        testUrl += URL_BODY_APPENDER + metricCollectionInfo.getCollectionBody();
        controlUrl += URL_BODY_APPENDER + metricCollectionInfo.getCollectionBody();
      }

      metricInfos.add(APMMetricInfo.builder()
                          .url(testUrl)
                          .hostName(TEST_HOST_NAME)
                          .metricName(metricCollectionInfo.getMetricName())
                          .metricType(metricCollectionInfo.getMetricType())
                          .method(metricCollectionInfo.getMethod())
                          .body(context.renderExpression(metricCollectionInfo.getCollectionBody()))
                          .tag(metricCollectionInfo.getTag())
                          .responseMappers(getResponseMappers(metricCollectionInfo))
                          .build());

      metricInfos.add(APMMetricInfo.builder()
                          .url(controlUrl)
                          .hostName(CONTROL_HOST_NAME)
                          .metricName(metricCollectionInfo.getMetricName())
                          .metricType(metricCollectionInfo.getMetricType())
                          .method(metricCollectionInfo.getMethod())
                          .body(context.renderExpression(metricCollectionInfo.getCollectionBody()))
                          .tag(metricCollectionInfo.getTag())
                          .responseMappers(getResponseMappers(metricCollectionInfo))
                          .build());
    }
    log.info("for {} canaryInfo is {}", context.getStateExecutionInstanceId(), metricInfos);
    return metricInfos;
  }

  private static Map<String, ResponseMapper> getResponseMappers(ApmMetricCollectionInfo metricCollectionInfo) {
    ApmResponseMapping responseMapping = metricCollectionInfo.getResponseMapping();
    Map<String, ResponseMapper> responseMappers = new HashMap<>();
    List<String> txnRegex =
        responseMapping.getTxnNameRegex() == null ? null : Lists.newArrayList(responseMapping.getTxnNameRegex());
    ResponseMapper txnNameResponseMapper = ResponseMapper.builder().fieldName("txnName").regexs(txnRegex).build();
    if (!isEmpty(responseMapping.getTxnNameFieldValue())) {
      txnNameResponseMapper.setFieldValue(responseMapping.getTxnNameFieldValue());
    } else {
      txnNameResponseMapper.setJsonPath(responseMapping.getTxnNameJsonPath());
    }
    // Set the host details (if exists) in the responseMapper
    if (!isEmpty(responseMapping.getHostJsonPath())) {
      String hostJson = responseMapping.getHostJsonPath();
      List<String> hostRegex =
          isEmpty(responseMapping.getHostRegex()) ? null : Lists.newArrayList(responseMapping.getHostRegex());
      ResponseMapper hostResponseMapper =
          ResponseMapper.builder().fieldName("host").regexs(hostRegex).jsonPath(hostJson).build();
      responseMappers.put("host", hostResponseMapper);
    }
    responseMappers.put("txnName", txnNameResponseMapper);
    if (isNotEmpty(responseMapping.getTimestampJsonPath())
        && isNotEmpty(responseMapping.getTimestampJsonPath().trim())) {
      responseMappers.put("timestamp",
          ResponseMapper.builder()
              .fieldName("timestamp")
              .jsonPath(responseMapping.getTimestampJsonPath())
              .timestampFormat(responseMapping.getTimeStampFormat())
              .build());
    }

    responseMappers.put("value",
        ResponseMapper.builder().fieldName("value").jsonPath(responseMapping.getMetricValueJsonPath()).build());

    return responseMappers;
  }
}
