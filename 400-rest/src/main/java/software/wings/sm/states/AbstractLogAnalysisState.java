/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.common.VerificationConstants.DEFAULT_GROUP_NAME;
import static software.wings.common.VerificationConstants.DUMMY_HOST_NAME;
import static software.wings.common.VerificationConstants.GA_PER_MINUTE_CV_STATES;
import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.COMPARE_WITH_CURRENT;
import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS;
import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.PREDICTIVE;
import static software.wings.service.intfc.security.SecretManagementDelegateService.NUM_OF_RETRIES;

import static java.time.Duration.ofMillis;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.context.ContextElementType;
import io.harness.exception.ExceptionUtils;
import io.harness.serializer.JsonUtils;
import io.harness.tasks.ResponseData;
import io.harness.time.Timestamp;
import io.harness.version.VersionInfoManager;

import software.wings.beans.DatadogConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SumoConfig;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.metrics.RiskLevel;
import software.wings.service.impl.VerificationLogContext;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.AnalysisContext.AnalysisContextKeys;
import software.wings.service.impl.analysis.AnalysisTolerance;
import software.wings.service.impl.analysis.CustomLogDataCollectionInfo;
import software.wings.service.impl.analysis.DataCollectionInfo;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.LogMLAnalysisSummary;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.stackdriver.StackDriverLogDataCollectionInfo;
import software.wings.service.impl.sumo.SumoDataCollectionInfo;
import software.wings.service.intfc.analysis.AnalysisService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.StackDriverLogState.StackDriverLogStateKeys;
import software.wings.stencils.DefaultValue;
import software.wings.verification.VerificationDataAnalysisResponse;
import software.wings.verification.VerificationStateAnalysisExecutionData;
import software.wings.verification.log.LogsCVConfiguration;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by rsingh on 7/6/17.
 */
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public abstract class AbstractLogAnalysisState extends AbstractAnalysisState {
  public static final int HOST_BATCH_SIZE = 5;
  private static String DEMO_REQUEST_BODY;
  private static String DEMO_RESPONSE_BODY;
  static {
    initDemoParams();
  }
  private static void initDemoParams() {
    try {
      String json = IOUtils.toString(
          AbstractAnalysisState.class.getResourceAsStream("cv-demo-api-call-logs.json"), StandardCharsets.UTF_8.name());
      JsonNode node = JsonUtils.readTree(json).get("logs");
      DEMO_REQUEST_BODY = JsonUtils.asPrettyJson(node.get("request"));
      DEMO_RESPONSE_BODY = JsonUtils.asPrettyJson(node.get("response"));
    } catch (IOException e) {
      throw new RuntimeException("Could not read demo data from resources");
    }
  }

  protected String query;

  @Transient @Inject @SchemaIgnore protected AnalysisService analysisService;
  @Transient @Inject @SchemaIgnore protected VersionInfoManager versionInfoManager;
  @Transient
  @Inject
  @SchemaIgnore
  protected WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @SchemaIgnore @Transient private String renderedQuery;

  public AbstractLogAnalysisState(String name, String stateType) {
    super(name, stateType);
  }

  @Attributes(required = true, title = "Search Keywords")
  @DefaultValue("*exception*")
  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query.trim();
  }

  public String getRenderedQuery() {
    return renderedQuery;
  }

  private void cleanUpForRetry(ExecutionContext executionContext) {
    analysisService.cleanUpForLogRetry(executionContext.getStateExecutionInstanceId());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext executionContext) {
    try (VerificationLogContext ignored = new VerificationLogContext(executionContext.getAccountId(), null,
             executionContext.getStateExecutionInstanceId(), DelegateStateType.valueOf(getStateType()),
             OVERRIDE_ERROR)) {
      getLogger().info("Executing state {}", executionContext.getStateExecutionInstanceId());
      String correlationId = UUID.randomUUID().toString();
      CVActivityLogger activityLogger = cvActivityLogService.getLoggerByStateExecutionId(
          executionContext.getAccountId(), executionContext.getStateExecutionInstanceId());
      if (executionContext.isRetry()) {
        activityLogger.info(RETRYING_VERIFICATION_STATE_MSG);
      }
      String delegateTaskId = null;
      try {
        renderedQuery = executionContext.renderExpression(query);
        cleanUpForRetry(executionContext);
        AnalysisContext analysisContext = getLogAnalysisContext(executionContext, correlationId);
        getLogger().info("context: {}", analysisContext);
        saveWorkflowVerificationResult(analysisContext);

        if (!checkLicense(appService.getAccountIdByAppId(executionContext.getAppId()),
                StateType.valueOf(getStateType()), executionContext.getStateExecutionInstanceId())) {
          return generateAnalysisResponse(analysisContext, ExecutionStatus.SKIPPED, false,
              "Your license type does not support running this verification. Skipping Analysis");
        }

        if (!isEmpty(getTimeDuration(executionContext))
            && Integer.parseInt(getTimeDuration(executionContext)) > MAX_WORKFLOW_TIMEOUT) {
          return generateAnalysisResponse(analysisContext, ExecutionStatus.SKIPPED, false,
              "Time duration cannot be more than 4 hours. Skipping Analysis");
        }

        if (unresolvedHosts(analysisContext)) {
          return generateAnalysisResponse(analysisContext, ExecutionStatus.FAILED, false,
              "The expression " + hostnameTemplate + " could not be resolved for hosts");
        }

        saveMetaDataForDashboard(analysisContext.getAccountId(), executionContext);

        Set<String> canaryNewHostNames = analysisContext.getTestNodes().keySet();
        if (isDemoPath(analysisContext)) {
          boolean failedState =
              settingsService.get(getAnalysisServerConfigId()).getName().toLowerCase().endsWith("dev");
          generateDemoActivityLogs(executionContext, activityLogger, failedState);
          generateDemoThirdPartyApiCallLogs(
              executionContext, analysisContext.getAccountId(), analysisContext.getStateExecutionId(), failedState);
          return getDemoExecutionResponse(analysisContext);
        }

        if (analysisContext.isSkipVerification()) {
          getLogger().warn("Could not find test nodes to compare the data");

          if (shouldFailOnEmptyNodes(analysisContext.getAccountId())) {
            getLogger().info("Could not find newly deployed instances. failOnEmptyNodes is true. Failing execution");
            return generateAnalysisResponse(analysisContext, ExecutionStatus.FAILED, false,
                "Could not find newly deployed instances. Marking execution as failed.");
          }

          return generateAnalysisResponse(analysisContext, ExecutionStatus.SKIPPED, false,
              "Could not find newly deployed instances. Skipping verification");
        }

        Set<String> lastExecutionNodes = analysisContext.getControlNodes().keySet();
        if (isEmpty(lastExecutionNodes)) {
          if (getComparisonStrategy() == COMPARE_WITH_CURRENT) {
            getLogger().info("No nodes with older version found to compare the logs. Skipping analysis");

            if (shouldFailOnEmptyNodes(analysisContext.getAccountId())) {
              getLogger().info(
                  "No nodes with older version found to compare the logs. failOnEmptyNodes is true. Failing execution");
              return generateAnalysisResponse(analysisContext, ExecutionStatus.FAILED, false,
                  "As no previous version instances exist for comparison, analysis will be marked as failed."
                      + " Check your setup if this is the first deployment or if the previous instances have been deleted or replaced.");
            }

            return generateAnalysisResponse(analysisContext, ExecutionStatus.SKIPPED, false,
                "As no previous version instances exist for comparison, analysis will be skipped. Check your setup if this is the first deployment or if the previous instances have been deleted or replaced.");
          }

          getLogger().warn(
              "It seems that there is no successful run for this workflow yet. Log data will be collected to be analyzed for next deployment run");
        }

        if (analysisContext.getNewNodesTrafficShiftPercent() != null
            && (analysisContext.getNewNodesTrafficShiftPercent() == 0
                || analysisContext.getNewNodesTrafficShiftPercent() > 50)) {
          getLogger().info(
              "New nodes cannot be analyzed against old nodes if new traffic percentage is greater than 50 or equal to 0");
          return generateAnalysisResponse(analysisContext, ExecutionStatus.FAILED, false,
              "Analysis cannot be performed with this traffic split. Please run verify steps with new traffic percentage greater than 0 and less than 50");
        }

        String responseMessage = "Log Verification running.";
        String baselineWorkflowExecutionId = null;
        if (getComparisonStrategy() == COMPARE_WITH_PREVIOUS) {
          WorkflowStandardParams workflowStandardParams =
              executionContext.getContextElement(ContextElementType.STANDARD);
          baselineWorkflowExecutionId = workflowExecutionBaselineService.getBaselineExecutionId(
              analysisContext.getAppId(), analysisContext.getWorkflowId(),
              workflowStandardParamsExtensionService.getEnv(workflowStandardParams).getUuid(),
              analysisContext.getServiceId());
          if (isEmpty(baselineWorkflowExecutionId)) {
            responseMessage = "No baseline was set for the workflow. Workflow running with auto baseline.";
            getLogger().info("{}", responseMessage);
            baselineWorkflowExecutionId = analysisService.getLastSuccessfulWorkflowExecutionIdWithLogs(
                analysisContext.getStateExecutionId(), analysisContext.getStateType(), analysisContext.getAppId(),
                analysisContext.getServiceId(), analysisContext.getWorkflowId(), analysisContext.getQuery(),
                getPhaseInfraMappingId(executionContext), workflowStandardParams.getEnvId());
          } else {
            responseMessage = "Baseline is pinned for the workflow. Analyzing against pinned baseline.";
            getLogger().info("Baseline is pinned for stateExecution: {}, baselineId: {}",
                analysisContext.getStateExecutionId(), baselineWorkflowExecutionId);
          }
          if (baselineWorkflowExecutionId == null) {
            responseMessage += " No previous execution found. This will be the baseline run.";
            getLogger().warn("No previous execution found. This will be the baseline run");
          }
          getLogger().info(
              "Baseline execution for {} is {}", analysisContext.getStateExecutionId(), baselineWorkflowExecutionId);
          analysisContext.setPrevWorkflowExecutionId(baselineWorkflowExecutionId);
        }
        activityLogger.info(responseMessage);

        final VerificationStateAnalysisExecutionData executionData =
            VerificationStateAnalysisExecutionData.builder()
                .stateExecutionInstanceId(analysisContext.getStateExecutionId())
                .baselineExecutionId(baselineWorkflowExecutionId)
                .serverConfigId(getAnalysisServerConfigId())
                .query(getRenderedQuery())
                .canaryNewHostNames(canaryNewHostNames)
                .lastExecutionNodes(lastExecutionNodes == null ? new HashSet<>() : new HashSet<>(lastExecutionNodes))
                .correlationId(analysisContext.getCorrelationId())
                .comparisonStrategy(getComparisonStrategy())
                .build();

        executionData.setStatus(ExecutionStatus.RUNNING);
        executionData.setErrorMsg(responseMessage);

        Set<String> hostsToBeCollected = new HashSet<>();
        if (getComparisonStrategy() == COMPARE_WITH_CURRENT && lastExecutionNodes != null) {
          hostsToBeCollected.addAll(lastExecutionNodes);
        }

        hostsToBeCollected.addAll(canaryNewHostNames);
        hostsToBeCollected.remove(null);
        getLogger().info("triggering data collection for {} state", getStateType());

        if (isCVTaskEnqueuingEnabled(executionContext.getAccountId())) {
          getLogger().info("Data collection will be done with cv tasks.");
          analysisContext.setDataCollectionInfov2(createDataCollectionInfo(executionContext, hostsToBeCollected));
        } else if (isEligibleForPerMinuteTask(executionContext.getAccountId())) {
          // In case of predictive the data collection will be handled as per 24x7 logic
          // Or in case when feature flag CV_DATA_COLLECTION_JOB is enabled. Delegate task creation will be every minute
          getLogger().info("Per Minute data collection will be done for triggering delegate task");
        } else {
          delegateTaskId = triggerAnalysisDataCollection(executionContext, executionData, hostsToBeCollected);
          getLogger().info("triggered data collection for {} state, delgateTaskId: {}", getStateType(), delegateTaskId);
        }
        logDataCollectionTriggeredMessage(executionContext, activityLogger);
        // Set the rendered query into the analysis context which will be used during task analysis.
        analysisContext.setQuery(getRenderedQuery());

        scheduleAnalysisCronJob(analysisContext, delegateTaskId);

        return ExecutionResponse.builder()
            .async(true)
            .correlationIds(Collections.singletonList(analysisContext.getCorrelationId()))
            .executionStatus(ExecutionStatus.RUNNING)
            .errorMessage(responseMessage)
            .stateExecutionData(executionData)
            .delegateTaskId(delegateTaskId)
            .build();
      } catch (Exception ex) {
        getLogger().error("log analysis state failed ", ex);
        // set the CV Metadata status to ERROR as well.
        activityLogger.error("Data collection failed: " + ex.getMessage());
        continuousVerificationService.setMetaDataExecutionStatus(
            executionContext.getStateExecutionInstanceId(), ExecutionStatus.ERROR, true, false);

        return ExecutionResponse.builder()
            .async(false)
            .correlationIds(Collections.singletonList(correlationId))
            .executionStatus(ExecutionStatus.ERROR)
            .errorMessage(ExceptionUtils.getMessage(ex))
            .stateExecutionData(VerificationStateAnalysisExecutionData.builder()
                                    .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
                                    .serverConfigId(getAnalysisServerConfigId())
                                    .query(getRenderedQuery())
                                    .comparisonStrategy(getComparisonStrategy())
                                    .build())
            .build();
      }
    }
  }

  protected DataCollectionInfoV2 createDataCollectionInfo(ExecutionContext context, Set<String> hosts) {
    throw new RuntimeException("Not implemented."); // TODO: this method needs to be abstract eventually.
  }

  protected abstract String triggerAnalysisDataCollection(
      ExecutionContext context, VerificationStateAnalysisExecutionData executionData, Set<String> hosts);

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext executionContext, Map<String, ResponseData> response) {
    VerificationDataAnalysisResponse executionResponse =
        (VerificationDataAnalysisResponse) response.values().iterator().next();

    if (ExecutionStatus.isBrokeStatus(executionResponse.getExecutionStatus())) {
      updateSweepingOutputWithCVResult(executionContext, executionResponse.getExecutionStatus().name());
      return getErrorExecutionResponse(executionContext, executionResponse);
    } else {
      AnalysisContext context =
          wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
              .filter(AnalysisContextKeys.stateExecutionId, executionContext.getStateExecutionInstanceId())
              .get();
      int analysisMinute = executionResponse.getStateExecutionData().getAnalysisMinute();
      for (int i = 0; i < NUM_OF_RETRIES; i++) {
        final LogMLAnalysisSummary analysisSummary = analysisService.getAnalysisSummary(
            context.getStateExecutionId(), context.getAppId(), StateType.valueOf(getStateType()));

        if (analysisSummary == null) {
          getLogger().info("No analysis summary. This can happen if there is no data with the given queries");
          continuousVerificationService.setMetaDataExecutionStatus(
              executionContext.getStateExecutionInstanceId(), ExecutionStatus.SUCCESS, true, false);
          ExecutionStatus executionStatus = isQAVerificationPath(context.getAccountId(), context.getAppId())
              ? ExecutionStatus.FAILED
              : ExecutionStatus.SUCCESS;
          String msg = isQAVerificationPath(context.getAccountId(), context.getAppId())
              ? "No Analysis result found. This is not a failure."
              : "No data found with given queries. Skipped Analysis";
          updateSweepingOutputWithCVResult(executionContext, executionStatus.name());
          return generateAnalysisResponse(context, executionStatus, true, msg);
        }

        if (analysisSummary.getAnalysisMinute() < analysisMinute) {
          getLogger().info("analysis for minute {} hasn't been found yet. Analysis found so far {}", analysisMinute,
              analysisSummary);
          sleep(ofMillis(5000));
          continue;
        }
        getLogger().info("found analysisSummary with message {}", analysisSummary.getAnalysisSummaryMessage());

        ExecutionStatus executionStatus = ExecutionStatus.SUCCESS;
        if (analysisSummary.getRiskLevel() == RiskLevel.HIGH) {
          getLogger().info(analysisSummary.getAnalysisSummaryMessage() + " Marking it failed.");
          executionStatus = ExecutionStatus.FAILED;
        } else if (analysisSummary.getRiskLevel() == RiskLevel.MEDIUM
            && getAnalysisTolerance().compareTo(AnalysisTolerance.MEDIUM) <= 0) {
          getLogger().info(analysisSummary.getAnalysisSummaryMessage() + " Marking it failed.");
          executionStatus = ExecutionStatus.FAILED;
        } else if (analysisSummary.getRiskLevel() == RiskLevel.LOW
            && getAnalysisTolerance().compareTo(AnalysisTolerance.LOW) == 0) {
          getLogger().info(analysisSummary.getAnalysisSummaryMessage() + " Marking it failed.");
          executionStatus = ExecutionStatus.FAILED;
        }

        getLogger().info("the final status is {}", executionStatus);
        executionResponse.getStateExecutionData().setStatus(executionStatus);
        continuousVerificationService.setMetaDataExecutionStatus(
            executionContext.getStateExecutionInstanceId(), executionStatus, false, false);
        updateExecutionStatus(context.getStateExecutionId(), true, executionStatus, "Analysis completed");
        executionStatus = isQAVerificationPath(context.getAccountId(), context.getAppId()) ? ExecutionStatus.SUCCESS
                                                                                           : executionStatus;
        updateSweepingOutputWithCVResult(executionContext, executionStatus.name());
        return ExecutionResponse.builder()
            .executionStatus(executionStatus)
            .stateExecutionData(executionResponse.getStateExecutionData())
            .build();
      }

      executionResponse.getStateExecutionData().setErrorMsg(
          "Analysis for minute " + analysisMinute + " failed to save in DB");
      updateExecutionStatus(context.getStateExecutionId(), false, ExecutionStatus.ERROR, "Error");
      updateSweepingOutputWithCVResult(executionContext, ExecutionStatus.ERROR.name());
      return ExecutionResponse.builder()
          .executionStatus(ExecutionStatus.ERROR)
          .stateExecutionData(executionResponse.getStateExecutionData())
          .build();
    }
  }

  @Override
  public void handleAbortEvent(ExecutionContext executionContext) {
    updateExecutionStatus(executionContext.getStateExecutionInstanceId(), false, ExecutionStatus.ABORTED, "Aborted");
    continuousVerificationService.setMetaDataExecutionStatus(
        executionContext.getStateExecutionInstanceId(), ExecutionStatus.ABORTED, true, false);
    AnalysisContext analysisContext =
        wingsPersistence.createQuery(AnalysisContext.class)
            .filter("appId", executionContext.getAppId())
            .filter(AnalysisContextKeys.stateExecutionId, executionContext.getStateExecutionInstanceId())
            .get();

    if (analysisContext == null) {
      analysisContext = getLogAnalysisContext(executionContext, UUID.randomUUID().toString());
    }

    final LogMLAnalysisSummary analysisSummary = analysisService.getAnalysisSummary(
        analysisContext.getStateExecutionId(), analysisContext.getAppId(), StateType.valueOf(getStateType()));

    if (analysisSummary == null) {
      generateAnalysisResponse(analysisContext, ExecutionStatus.ABORTED, false, "Workflow was aborted while analysing");
    }

    if (isNotEmpty(analysisContext.getPredictiveCvConfigId())) {
      getLogger().info("disabling the predictive cv config {} state {}", analysisContext.getPredictiveCvConfigId(),
          analysisContext.getStateExecutionId());
      wingsPersistence.updateField(
          LogsCVConfiguration.class, analysisContext.getPredictiveCvConfigId(), "enabled24x7", false);
    }
  }

  @Override
  protected ExecutionResponse generateAnalysisResponse(
      AnalysisContext context, ExecutionStatus status, boolean analyzed, String message) {
    analysisService.createAndSaveSummary(context.getStateType(), context.getAppId(), context.getStateExecutionId(),
        context.getQuery(), message, context.getAccountId());
    updateExecutionStatus(context.getStateExecutionId(), analyzed, status, message);
    return createExecutionResponse(context, status, message, true);
  }

  private AnalysisContext getLogAnalysisContext(ExecutionContext context, String correlationId) {
    CVInstanceApiResponse cvInstanceAPIResponse = getCVInstanceAPIResponse(context);
    getLogger().info("Using new instance API");
    Map<String, String> testNodes =
        cvInstanceAPIResponse.getTestNodes().stream().collect(Collectors.toMap(key -> key, key -> DEFAULT_GROUP_NAME));
    Map<String, String> controlNodes = cvInstanceAPIResponse.getControlNodes().stream().collect(
        Collectors.toMap(key -> key, key -> DEFAULT_GROUP_NAME));

    renderedQuery = context.renderExpression(query);

    String accountId = this.appService.get(context.getAppId()).getAccountId();
    String hostNameField = getHostnameField(context);

    AnalysisContext analysisContext =
        AnalysisContext.builder()
            .accountId(accountId)
            .appId(context.getAppId())
            .workflowId(getWorkflowId(context))
            .workflowExecutionId(context.getWorkflowExecutionId())
            .stateExecutionId(context.getStateExecutionInstanceId())
            .serviceId(getPhaseServiceId(context))
            .analysisType(MLAnalysisType.LOG_ML)
            .controlNodes(controlNodes)
            .testNodes(testNodes)
            .query(getRenderedQuery())
            .isSSL(this.configuration.isSslEnabled())
            .appPort(this.configuration.getApplicationPort())
            .comparisonStrategy(getComparisonStrategy())
            .timeDuration(Integer.parseInt(getTimeDuration(context)))
            .stateType(StateType.valueOf(getStateType()))
            .analysisServerConfigId(getResolvedConnectorId(
                context, AnalysisContextKeys.analysisServerConfigId, getAnalysisServerConfigId()))
            .correlationId(correlationId)
            .managerVersion(versionInfoManager.getVersionInfo().getVersion())
            .envId(getEnvId(context))
            .hostNameField(hostNameField)
            .startDataCollectionMinute(TimeUnit.MILLISECONDS.toMinutes(Timestamp.currentMinuteBoundary()))
            .predictiveHistoryMinutes(Integer.parseInt(getPredictiveHistoryMinutes()))
            .initialDelaySeconds(getDelaySeconds(initialAnalysisDelay))
            .inspectHostsInLogs(shouldInspectHostsForLogAnalysis())
            .isHistoricalDataCollection(isHistoricalAnalysis(context.getAccountId()))
            .newNodesTrafficShiftPercent(cvInstanceAPIResponse.getNewNodesTrafficShiftPercent().isPresent()
                    ? cvInstanceAPIResponse.getNewNodesTrafficShiftPercent().get()
                    : null)
            .skipVerification(cvInstanceAPIResponse.isSkipVerification())
            .perMinCollectionFinished(!isEligibleForPerMinuteTask(accountId))
            .build();
    // Saving data collection info as part of context.
    // This will be directly used as part of delegate task
    if (getComparisonStrategy() == PREDICTIVE || GA_PER_MINUTE_CV_STATES.contains(StateType.valueOf(getStateType()))) {
      DataCollectionInfo dataCollectionInfo = createDataCollectionInfo(analysisContext, context);
      analysisContext.setDataCollectionInfo(dataCollectionInfo);
    }
    sampleHostsMap(analysisContext);
    if (getCVTaskFeatureName().isPresent()) {
      analysisContext.setFeatureFlag(
          getCVTaskFeatureName().get(), isCVTaskEnqueuingEnabled(analysisContext.getAccountId()));
    }
    return analysisContext;
  }

  protected boolean shouldInspectHostsForLogAnalysis() {
    return true;
  }

  protected String getHostnameField(ExecutionContext context) {
    switch (StateType.valueOf(getStateType())) {
      case ELK:
        return context.renderExpression(((ElkAnalysisState) this).getHostnameField());
      case DATA_DOG_LOG:
      case STACK_DRIVER_LOG:
        return this.getHostnameField(context);
      case SPLUNKV2:
        if (isEmpty(hostnameField)) {
          return "host";
        }
        return getResolvedFieldValue(context, AbstractAnalysisStateKeys.hostnameField, hostnameField);
      default:
        return null;
    }
  }

  public DataCollectionInfo createDataCollectionInfo(
      AnalysisContext analysisContext, ExecutionContext executionContext) {
    StateType stateType = StateType.valueOf(getStateType());
    String resolvedConfigId = getResolvedConnectorId(
        executionContext, AnalysisContextKeys.analysisServerConfigId, getAnalysisServerConfigId());
    final SettingAttribute settingAttribute = getSettingAttribute(resolvedConfigId);
    switch (stateType) {
      case SUMO:
        SumoLogicAnalysisState sumoLogicAnalysisState = (SumoLogicAnalysisState) this;
        SumoConfig sumoConfig = (SumoConfig) settingAttribute.getValue();
        return SumoDataCollectionInfo.builder()
            .sumoConfig(sumoConfig)
            .accountId(analysisContext.getAccountId())
            .applicationId(analysisContext.getAppId())
            .stateExecutionId(analysisContext.getStateExecutionId())
            .workflowId(analysisContext.getWorkflowId())
            .workflowExecutionId(analysisContext.getWorkflowExecutionId())
            .serviceId(analysisContext.getServiceId())
            .query(sumoLogicAnalysisState.getRenderedQuery())
            .hosts(Sets.newHashSet(DUMMY_HOST_NAME))
            .hostnameField(getResolvedFieldValue(
                executionContext, AbstractAnalysisStateKeys.hostnameField, sumoLogicAnalysisState.getHostnameField()))
            .build();
      case DATA_DOG_LOG:
        DatadogLogState datadogLogState = (DatadogLogState) this;
        DatadogConfig datadogConfig = (DatadogConfig) settingAttribute.getValue();
        CustomLogDataCollectionInfo datadogCustomLogDataCollectionInfo =
            CustomLogDataCollectionInfo.builder()
                .baseUrl(datadogConfig.getUrl())
                .validationUrl(DatadogConfig.validationUrl)
                .dataUrl(DatadogConfig.LOG_API_PATH_SUFFIX)
                .headers(new HashMap<>())
                .options(datadogConfig.fetchLogOptionsMap())
                .body(DatadogLogState.resolveHostnameField(
                    datadogConfig.fetchLogBodyMap(false), analysisContext.getHostNameField()))
                .query(getRenderedQuery())
                .hosts(Sets.newHashSet(DUMMY_HOST_NAME))
                .stateType(DelegateStateType.DATA_DOG_LOG)
                .applicationId(analysisContext.getAppId())
                .stateExecutionId(analysisContext.getStateExecutionId())
                .workflowId(analysisContext.getWorkflowId())
                .workflowExecutionId(analysisContext.getWorkflowExecutionId())
                .serviceId(analysisContext.getServiceId())
                .hostnameSeparator(DatadogLogState.HOST_NAME_SEPARATOR)
                .hostnameField(analysisContext.getHostNameField())
                .responseDefinition(
                    datadogLogState.constructLogDefinitions(datadogConfig, analysisContext.getHostNameField(), false))
                .shouldDoHostBasedFiltering(shouldInspectHostsForLogAnalysis())
                .collectionFrequency(1)
                .collectionTime(Integer.parseInt(getTimeDuration(executionContext)))
                .accountId(analysisContext.getAccountId())
                .build();
        datadogCustomLogDataCollectionInfo.setDelayMinutes(0);
        return datadogCustomLogDataCollectionInfo;
      case STACK_DRIVER_LOG:
        StackDriverLogState stackDriverLogState = (StackDriverLogState) this;
        GcpConfig gcpConfig = (GcpConfig) settingAttribute.getValue();
        return StackDriverLogDataCollectionInfo.builder()
            .gcpConfig(gcpConfig)
            .projectId(stackDriverLogState.getProjectId())
            .hostnameField(getResolvedFieldValue(executionContext, AnalysisContextKeys.hostNameField, hostnameField))
            .stateType(DelegateStateType.STACK_DRIVER_LOG)
            .applicationId(analysisContext.getAppId())
            .logMessageField(getResolvedFieldValue(
                executionContext, StackDriverLogStateKeys.messageField, stackDriverLogState.getMessageField()))
            .stateExecutionId(analysisContext.getStateExecutionId())
            .workflowId(analysisContext.getWorkflowId())
            .workflowExecutionId(analysisContext.getWorkflowExecutionId())
            .serviceId(analysisContext.getServiceId())
            .collectionTime(Integer.parseInt(getTimeDuration(executionContext)))
            .hosts(Sets.newHashSet(DUMMY_HOST_NAME))
            .accountId(analysisContext.getAccountId())
            .query(getRenderedQuery())
            .initialDelayMinutes(0)
            .build();
      default:
        return null;
    }
  }

  public AnalysisTolerance getAnalysisTolerance() {
    if (isBlank(tolerance)) {
      return AnalysisTolerance.LOW;
    }
    return AnalysisTolerance.valueOf(tolerance);
  }

  public void setAnalysisTolerance(String tolerance) {
    this.tolerance = tolerance;
  }

  protected List<Set<String>> batchHosts(Set<String> hosts) {
    List<Set<String>> batchedHosts = new ArrayList<>();
    Set<String> batch = new HashSet<>();
    for (String host : hosts) {
      if (batch.size() == HOST_BATCH_SIZE) {
        batchedHosts.add(batch);
        batch = new HashSet<>();
      }
      batch.add(host);
    }
    if (!batch.isEmpty()) {
      batchedHosts.add(batch);
    }

    return batchedHosts;
  }

  private void generateDemoThirdPartyApiCallLogs(
      ExecutionContext executionContext, String accountId, String stateExecutionId, boolean failedState) {
    super.generateDemoThirdPartyApiCallLogs(
        executionContext, accountId, stateExecutionId, failedState, demoRequestBody(), demoLogResponse());
  }
  private String demoRequestBody() {
    return DEMO_REQUEST_BODY;
  }
  private String demoLogResponse() {
    return DEMO_RESPONSE_BODY;
  }
}
