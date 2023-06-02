/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.beans.FeatureName.CV_SUCCEED_FOR_ANOMALY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.govern.Switch.noop;

import static software.wings.beans.AccountType.COMMUNITY;
import static software.wings.beans.AccountType.ESSENTIALS;
import static software.wings.common.TemplateExpressionProcessor.checkFieldTemplatized;
import static software.wings.common.VerificationConstants.DELAY_MINUTES;
import static software.wings.common.VerificationConstants.GA_PER_MINUTE_CV_STATES;
import static software.wings.common.VerificationConstants.URL_STRING;
import static software.wings.delegatetasks.AbstractDelegateDataCollectionTask.PREDECTIVE_HISTORY_MINUTES;
import static software.wings.service.impl.ThirdPartyApiCallLog.PAYLOAD;
import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS;
import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.PREDICTIVE;
import static software.wings.sm.ExecutionContextImpl.PHASE_PARAM;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SweepingOutputInstance;
import io.harness.context.ContextElementType;
import io.harness.cv.WorkflowVerificationResult;
import io.harness.cv.api.WorkflowVerificationResultService;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;
import io.harness.serializer.KryoSerializer;
import io.harness.time.Timestamp;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.api.DeploymentType;
import software.wings.api.PhaseElement;
import software.wings.api.instancedetails.InstanceApiResponse;
import software.wings.app.MainConfiguration;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData.ContinuousVerificationExecutionMetaDataBuilder;
import software.wings.service.impl.analysis.ContinuousVerificationService;
import software.wings.service.impl.instance.ContainerInstanceHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionBaselineService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.states.mixin.SweepingOutputStateMixin;
import software.wings.stencils.DefaultValue;
import software.wings.verification.VerificationDataAnalysisResponse;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

/**
 * Created by rsingh on 7/6/17.
 */
@OwnedBy(CV)
@FieldNameConstants(innerTypeName = "AbstractAnalysisStateKeys")
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public abstract class AbstractAnalysisState extends State implements SweepingOutputStateMixin {
  private static final SecureRandom random = new SecureRandom();
  // only use it in the new instance API.
  private static final String DEFAULT_HOSTNAME_TEMPLATE = "${instanceDetails.hostName}";
  public static final String RETRYING_VERIFICATION_STATE_MSG = "Retrying verification state..";
  public static final String START_TIME_PLACE_HOLDER = "$startTime";
  public static final String END_TIME_PLACE_HOLDER = "$endTime";
  public static final String HOST_NAME_PLACE_HOLDER = "$hostName";
  public static final int MAX_SAMPLING_SIZE_PER_GROUP = 10;
  protected static final String CV_STATUS_VARIABLE = "cvStatus";
  protected static final String CV_STATE_TYPE_VARIABLE = "cvStateType";

  protected boolean failOnEmptyNodes;
  protected String timeDuration;
  protected String comparisonStrategy;
  protected String tolerance;
  protected String predictiveHistoryMinutes;
  protected String initialAnalysisDelay = "2m";
  @Getter @Setter private String sweepingOutputName;
  @Getter @Setter private SweepingOutputInstance.Scope sweepingOutputScope;

  private static final int DEFAULT_VERIFICATION_STATE_TIMEOUT_MILLIS = 3 * 60 * 60 * 1000; // 3 hours
  private static final int TIMEOUT_BUFFER = 150; // 150 Minutes.
  protected static final int MAX_WORKFLOW_TIMEOUT = 4 * 60; // 4 hours

  @Inject protected WorkflowExecutionService workflowExecutionService;
  @Inject protected WaitNotifyEngine waitNotifyEngine;
  @Inject protected SettingsService settingsService;
  @Inject protected WingsPersistence wingsPersistence;
  @Inject protected AppService appService;
  @Inject protected DelegateService delegateService;
  @Inject protected SecretManager secretManager;
  @Inject @SchemaIgnore protected MainConfiguration configuration;
  @Inject @SchemaIgnore protected ContainerInstanceHandler containerInstanceHandler;
  @Inject @SchemaIgnore protected InfrastructureMappingService infraMappingService;
  @Inject protected TemplateExpressionProcessor templateExpressionProcessor;
  @Inject @SchemaIgnore protected WorkflowExecutionBaselineService workflowExecutionBaselineService;
  @Inject @SchemaIgnore protected ContinuousVerificationService continuousVerificationService;
  @Inject @SchemaIgnore private AwsHelperService awsHelperService;
  @Inject protected FeatureFlagService featureFlagService;
  @Inject protected StateExecutionService stateExecutionService;
  @Inject @SchemaIgnore protected ServiceResourceService serviceResourceService;
  @Inject private transient ExpressionEvaluator evaluator;
  @Inject protected AccountService accountService;
  @Inject protected CVActivityLogService cvActivityLogService;
  @Inject protected WorkflowVerificationResultService workflowVerificationResultService;
  @Inject @Transient protected SweepingOutputService sweepingOutputService;
  @Transient @Inject KryoSerializer kryoSerializer;
  protected String hostnameField;

  protected String hostnameTemplate;

  protected boolean includePreviousPhaseNodes;

  @Override
  public KryoSerializer getKryoSerializer() {
    return kryoSerializer;
  }

  @Attributes(title = "Analysis Time duration (in minutes)")
  @DefaultValue("15")
  public String getTimeDuration() {
    if (isBlank(timeDuration)) {
      return String.valueOf(15);
    }
    return timeDuration;
  }

  protected String getTimeDuration(ExecutionContext context) {
    if (isBlank(timeDuration)) {
      return String.valueOf(15);
    }
    return getResolvedFieldValue(context, AbstractAnalysisStateKeys.timeDuration, timeDuration);
  }

  protected boolean isEligibleForPerMinuteTask(String accountId) {
    return getComparisonStrategy() == PREDICTIVE || GA_PER_MINUTE_CV_STATES.contains(StateType.valueOf(getStateType()));
  }

  @Attributes(required = true, title = "Include nodes from previous phases")
  public boolean getIncludePreviousPhaseNodes() {
    return includePreviousPhaseNodes;
  }

  public void setIncludePreviousPhaseNodes(boolean includePreviousPhaseNodes) {
    this.includePreviousPhaseNodes = includePreviousPhaseNodes;
  }

  public void setTimeDuration(String timeDuration) {
    this.timeDuration = timeDuration;
  }

  public AnalysisComparisonStrategy getComparisonStrategy() {
    if (isBlank(comparisonStrategy)) {
      return AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS;
    }
    return AnalysisComparisonStrategy.valueOf(comparisonStrategy);
  }

  public void setComparisonStrategy(String comparisonStrategy) {
    this.comparisonStrategy = comparisonStrategy;
  }

  @Attributes(title = "Predictive history in Minutes")
  @DefaultValue("30")
  public String getPredictiveHistoryMinutes() {
    if (isEmpty(predictiveHistoryMinutes)) {
      return String.valueOf(PREDECTIVE_HISTORY_MINUTES);
    }
    return predictiveHistoryMinutes;
  }

  public void setPredictiveHistoryMinutes(String predictiveHistoryMinutes) {
    this.predictiveHistoryMinutes = predictiveHistoryMinutes;
  }

  public void setFailOnEmptyNodes(boolean failOnEmptyNodes) {
    this.failOnEmptyNodes = failOnEmptyNodes;
  }

  public boolean isFailOnEmptyNodes() {
    return failOnEmptyNodes;
  }

  public AbstractAnalysisState(String name, String stateType) {
    super(name, stateType);
  }
  protected void saveMetaDataForDashboard(String accountId, ExecutionContext executionContext) {
    try {
      WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(
          executionContext.getAppId(), executionContext.getWorkflowExecutionId());

      // TODO: ASR: update this for multi-artifact
      final Artifact artifactForService =
          ((ExecutionContextImpl) executionContext).getArtifactForService(getPhaseServiceId(executionContext));
      ContinuousVerificationExecutionMetaDataBuilder cvExecutionMetaDataBuilder =
          ContinuousVerificationExecutionMetaData.builder()
              .accountId(accountId)
              .applicationId(executionContext.getAppId())
              .workflowExecutionId(executionContext.getWorkflowExecutionId())
              .workflowId(executionContext.getWorkflowId())
              .stateExecutionId(executionContext.getStateExecutionInstanceId())
              .serviceId(getPhaseServiceId(executionContext))
              .envName(getEnvName(executionContext))
              .workflowName(executionContext.getWorkflowExecutionName())
              .appName(getAppName(executionContext))
              .artifactName(artifactForService == null ? null : artifactForService.getDisplayName())
              .serviceName(getPhaseServiceName(executionContext))
              .workflowStartTs(workflowExecution.getStartTs())
              .stateType(StateType.valueOf(getStateType()))
              .stateStartTs(((ExecutionContextImpl) executionContext).getStateExecutionInstance().getStartTs())
              .phaseName(getPhaseName(executionContext))
              .phaseId(getPhaseId(executionContext))
              .executionStatus(ExecutionStatus.RUNNING)
              .envId(getEnvId(executionContext));

      if (workflowExecution.getPipelineExecutionId() != null) {
        WorkflowExecution pipelineExecutionDetails = workflowExecutionService.getExecutionDetails(
            executionContext.getAppId(), workflowExecution.getPipelineExecutionId(), false, false);
        cvExecutionMetaDataBuilder.pipelineName(pipelineExecutionDetails.normalizedName())
            .pipelineStartTs(pipelineExecutionDetails.getStartTs())
            .pipelineExecutionId(workflowExecution.getPipelineExecutionId())
            .pipelineId(workflowExecution.getExecutionArgs().getPipelineId());
      }

      ContinuousVerificationExecutionMetaData metaData = cvExecutionMetaDataBuilder.build();
      metaData.setAppId(executionContext.getAppId());
      continuousVerificationService.saveCVExecutionMetaData(metaData);
    } catch (Exception ex) {
      getLogger().error("[learning-engine] Unable to save ml analysis metadata", ex);
    }
  }

  protected void saveWorkflowVerificationResult(AnalysisContext analysisContext) {
    workflowVerificationResultService.addWorkflowVerificationResult(
        WorkflowVerificationResult.builder()
            .accountId(analysisContext.getAccountId())
            .appId(analysisContext.getAppId())
            .stateExecutionId(analysisContext.getStateExecutionId())
            .serviceId(analysisContext.getServiceId())
            .envId(analysisContext.getEnvId())
            .workflowId(analysisContext.getWorkflowId())
            .stateType(getStateType())
            .analyzed(false)
            .executionStatus(ExecutionStatus.RUNNING)
            .build());
  }

  protected void updateExecutionStatus(
      String stateExecutionId, boolean analyzed, ExecutionStatus executionStatus, String message) {
    workflowVerificationResultService.updateWorkflowVerificationResult(
        stateExecutionId, analyzed, executionStatus, message);
  }

  protected String scheduleAnalysisCronJob(AnalysisContext context, String delegateTaskId) {
    context.setDelegateTaskId(delegateTaskId);
    return wingsPersistence.save(context);
  }

  protected boolean isAwsLambdaState(ExecutionContext context) {
    return getStateType().equals(StateType.CLOUD_WATCH.name())
        && getDeploymentType(context) == DeploymentType.AWS_LAMBDA;
  }

  protected boolean isAwsECSState(ExecutionContext context) {
    return getStateType().equals(StateType.CLOUD_WATCH.name()) && getDeploymentType(context) == DeploymentType.ECS;
  }

  PhaseElement getPhaseElement(ExecutionContext context) {
    return context.getContextElement(ContextElementType.PARAM, PHASE_PARAM);
  }
  protected String getPhaseServiceId(ExecutionContext context) {
    PhaseElement phaseElement = getPhaseElement(context);
    return phaseElement.getServiceElement().getUuid();
  }

  protected String getPhaseInfraMappingId(ExecutionContext context) {
    return context.fetchInfraMappingId();
  }

  protected DeploymentType getDeploymentType(ExecutionContext context) {
    InfrastructureMapping infrastructureMapping = getInfrastructureMapping(context);
    return serviceResourceService.getDeploymentType(infrastructureMapping, null, infrastructureMapping.getServiceId());
  }

  protected String getPhaseServiceName(ExecutionContext context) {
    PhaseElement phaseElement = getPhaseElement(context);
    return phaseElement.getServiceElement().getName();
  }

  protected String getPhaseName(ExecutionContext context) {
    PhaseElement phaseElement = getPhaseElement(context);
    return phaseElement.getPhaseName();
  }

  protected String getWorkflowId(ExecutionContext context) {
    final WorkflowExecution executionDetails =
        workflowExecutionService.getWorkflowExecution(context.getAppId(), context.getWorkflowExecutionId());
    return executionDetails.getWorkflowId();
  }

  protected String getPhaseId(ExecutionContext context) {
    PhaseElement phaseElement = getPhaseElement(context);
    return phaseElement.getUuid();
  }

  @SchemaIgnore public abstract Logger getLogger();

  public abstract String getAnalysisServerConfigId();

  public abstract void setAnalysisServerConfigId(String analysisServerConfigId);

  protected void generateDemoActivityLogs(
      ExecutionContext executionContext, CVActivityLogger activityLogger, boolean failedState) {
    logDataCollectionTriggeredMessage(executionContext, activityLogger);
    long startTime = dataCollectionStartTimestampMillis();
    int duration = Integer.parseInt(getTimeDuration(executionContext));
    for (int minute = 0; minute < duration; minute++) {
      long startTimeMSForCurrentMinute = startTime + Duration.ofMinutes(minute).toMillis();
      long endTimeMSForCurrentMinute = startTimeMSForCurrentMinute + Duration.ofMinutes(1).toMillis();
      activityLogger.info(
          "Starting data collection. Time range %t to %t", startTimeMSForCurrentMinute, endTimeMSForCurrentMinute);
      if (minute == duration - 1 && failedState) {
        activityLogger.info(
            "Starting data collection. Time range %t to %t", startTimeMSForCurrentMinute, endTimeMSForCurrentMinute);
        activityLogger.error(
            "Data collection failed for time range %t to %t", startTimeMSForCurrentMinute, endTimeMSForCurrentMinute);
      } else {
        activityLogger.info("Data collection successful for time range %t to %t", startTimeMSForCurrentMinute,
            endTimeMSForCurrentMinute);
        activityLogger.info(
            "Analysis completed for time range %t to %t", startTimeMSForCurrentMinute, endTimeMSForCurrentMinute);
      }
    }
    if (failedState) {
      activityLogger.error("Analysis failed");
    } else {
      activityLogger.info("Analysis successful");
    }
  }

  protected boolean isDemoPath(AnalysisContext analysisContext) {
    return featureFlagService.isEnabled(FeatureName.CV_DEMO, analysisContext.getAccountId())
        && (getSettingAttribute(getAnalysisServerConfigId()).getName().toLowerCase().endsWith("dev")
            || getSettingAttribute(getAnalysisServerConfigId()).getName().toLowerCase().endsWith("prod"));
  }

  protected void generateDemoThirdPartyApiCallLogs(ExecutionContext executionContext, String accountId,
      String stateExecutionId, boolean failedState, String demoRequestBody, String demoResponseBody) {
    List<ThirdPartyApiCallLog> thirdPartyApiCallLogs = new ArrayList<>();
    long startTime = dataCollectionStartTimestampMillis();
    int duration = Integer.parseInt(getTimeDuration(executionContext));
    for (int minute = 0; minute < duration; minute++) {
      long startTimeMSForCurrentMinute = startTime + Duration.ofMinutes(minute).toMillis();
      ThirdPartyApiCallLog apiCallLog = ThirdPartyApiCallLog.createApiCallLog(accountId, stateExecutionId);
      apiCallLog.setTitle("Demo third party API call log");
      apiCallLog.setRequestTimeStamp(startTimeMSForCurrentMinute);
      apiCallLog.addFieldToRequest(
          ThirdPartyApiCallField.builder().name(URL_STRING).value("http://example.com/").type(FieldType.URL).build());
      apiCallLog.addFieldToRequest(
          ThirdPartyApiCallField.builder().name(PAYLOAD).value(demoRequestBody).type(FieldType.JSON).build());
      if (minute == duration / 2 && failedState) {
        apiCallLog.addFieldToResponse(408, "Timeout from service provider", FieldType.JSON);
      } else {
        apiCallLog.addFieldToResponse(200, demoResponseBody, FieldType.JSON);
      }
      apiCallLog.setResponseTimeStamp(startTimeMSForCurrentMinute + random.nextInt(100));
      thirdPartyApiCallLogs.add(apiCallLog);
    }
    wingsPersistence.save(
        thirdPartyApiCallLogs.stream()
            .map(thirdPartyApiCallLog -> software.wings.service.impl.ThirdPartyApiCallLog.fromDto(thirdPartyApiCallLog))
            .collect(Collectors.toList()));
  }

  /**
   *  for QA in harness verification app, fail if there is no data but don't fail for anomaly
   */
  protected boolean isQAVerificationPath(String accountId, String appId) {
    return featureFlagService.isEnabled(CV_SUCCEED_FOR_ANOMALY, accountId)
        && appService.get(appId).getName().equals("Harness Verification");
  }

  public String getHostnameTemplate() {
    return hostnameTemplate;
  }

  public void setHostnameTemplate(String hostnameTemplate) {
    this.hostnameTemplate = hostnameTemplate;
  }

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis(ExecutionContext context) {
    timeDuration = getTimeDuration(context);
    if (!isEmpty(timeDuration)) {
      return 60 * 1000 * (Integer.parseInt(timeDuration) + TIMEOUT_BUFFER);
    }
    return DEFAULT_VERIFICATION_STATE_TIMEOUT_MILLIS;
  }

  @Override
  public Map<String, String> validateFields() {
    return null;
  }

  protected InfrastructureMapping getInfrastructureMapping(ExecutionContext context) {
    String infraMappingId = context.fetchInfraMappingId();
    Preconditions.checkNotNull(
        context.fetchInfraMappingId(), "for " + context.getStateExecutionInstanceId() + " no infra mapping id found");
    return infraMappingService.get(context.getAppId(), infraMappingId);
  }

  protected boolean checkLicense(String accountId, StateType stateType, String stateExecutionId) {
    switch (stateType) {
      case PROMETHEUS:
      case STACK_DRIVER:
      case STACK_DRIVER_LOG:
      case CLOUD_WATCH:
        return true;
      default:
        noop();
    }

    final Optional<String> accountType = accountService.getAccountType(accountId);
    if (!accountType.isPresent()) {
      getLogger().info("for id {} and stateType {} the account has no type set. License check will not be enforced",
          stateExecutionId, stateType);
      return false;
    }
    if (COMMUNITY.equals(accountType.get()) || ESSENTIALS.equals(accountType.get())) {
      getLogger().info("for id {}, stateType {} the account type {} does not have license to run. Skipping analysis",
          stateExecutionId, stateType, accountType.get());
      return false;
    }

    return true;
  }

  protected boolean unresolvedHosts(AnalysisContext analysisContext) {
    return unresolvedHosts(analysisContext.getTestNodes()) || unresolvedHosts(analysisContext.getControlNodes());
  }

  private boolean unresolvedHosts(Map<String, String> hostToGroupMap) {
    return hostToGroupMap.entrySet().stream().anyMatch(entry -> {
      String hostName = entry.getKey();
      if (hostName.contains("${") && hostName.contains("}")) {
        getLogger().info("{} is not resolved", hostName);
        return true;
      }
      return false;
    });
  }

  protected long dataCollectionStartTimestampMillis() {
    return Timestamp.currentMinuteBoundary();
  }

  private long dataCollectionEndTimestampMillis(ExecutionContext executionContext, long dataCollectionStartTime) {
    return Instant.ofEpochMilli(dataCollectionStartTime)
        .plusMillis(TimeUnit.MINUTES.toMillis(Integer.parseInt(getTimeDuration(executionContext))))
        .toEpochMilli();
  }

  protected void logDataCollectionTriggeredMessage(ExecutionContext executionContext, CVActivityLogger activityLogger) {
    long dataCollectionStartTime = dataCollectionStartTimestampMillis();
    long initDelayMins = TimeUnit.SECONDS.toMinutes(getDelaySeconds(initialAnalysisDelay));
    activityLogger.info("Triggered data collection for " + getTimeDuration(executionContext)
            + " minutes, Data will be collected for time range %t to %t. Waiting for " + initDelayMins
            + " minutes before starting data collection.",
        dataCollectionStartTime, dataCollectionEndTimestampMillis(executionContext, dataCollectionStartTime));
  }

  protected boolean isCVTaskEnqueuingEnabled(String accountId) {
    Optional<FeatureName> cvTaskFeatureName = getCVTaskFeatureName();
    if (cvTaskFeatureName.isPresent()) {
      return featureFlagService.isEnabled(cvTaskFeatureName.get(), accountId);
    } else {
      return false;
    }
  }

  protected Optional<FeatureName> getCVTaskFeatureName() {
    return Optional.empty();
  }

  protected boolean isExpression(String fieldName, String fieldValue, List<TemplateExpression> templateExpressions) {
    if (checkFieldTemplatized(fieldName, templateExpressions)) {
      return false;
    }
    if (isEmpty(fieldValue)) {
      return false;
    }
    int expressions = StringUtils.countMatches(fieldValue, "$");
    int hostFields = StringUtils.countMatches(fieldValue, "${host}");
    return expressions > hostFields;
  }

  protected void updateSweepingOutputWithCVResult(ExecutionContext executionContext, String status) {
    Map<String, Object> sweepingOutputMap = new HashMap<>();
    sweepingOutputMap.put(CV_STATE_TYPE_VARIABLE, getStateType());
    sweepingOutputMap.put(CV_STATUS_VARIABLE, status);
    handleSweepingOutput(sweepingOutputService, executionContext, sweepingOutputMap);
  }

  protected ExecutionResponse createExecutionResponse(
      AnalysisContext context, ExecutionStatus status, String message, boolean updateCVMetadataState) {
    final VerificationStateAnalysisExecutionData executionData =
        VerificationStateAnalysisExecutionData.builder()
            .stateExecutionInstanceId(context.getStateExecutionId())
            .serverConfigId(context.getAnalysisServerConfigId())
            .baselineExecutionId(context.getPrevWorkflowExecutionId())
            .canaryNewHostNames(isEmpty(context.getTestNodes()) ? Collections.emptySet()
                                                                : new HashSet<>(context.getTestNodes().keySet()))
            .lastExecutionNodes(isEmpty(context.getControlNodes()) ? Collections.emptySet()
                                                                   : new HashSet<>(context.getControlNodes().keySet()))
            .correlationId(context.getCorrelationId())
            .query(context.getQuery())
            .customThresholdRefId(context.getCustomThresholdRefId())
            .comparisonStrategy(getComparisonStrategy())
            .build();
    executionData.setStatus(status);
    if (updateCVMetadataState) {
      continuousVerificationService.setMetaDataExecutionStatus(context.getStateExecutionId(), status, true, false);
      cvActivityLogService.getLoggerByStateExecutionId(context.getAccountId(), context.getStateExecutionId())
          .info(message);
    }
    return ExecutionResponse.builder()
        .async(false)
        .executionStatus(status)
        .stateExecutionData(executionData)
        .errorMessage(message)
        .build();
  }

  protected ExecutionResponse getDemoExecutionResponse(AnalysisContext analysisContext) {
    boolean failedState = getSettingAttribute(getAnalysisServerConfigId()).getName().toLowerCase().endsWith("dev");
    if (failedState) {
      return generateAnalysisResponse(analysisContext, ExecutionStatus.FAILED, true, "Demo CV");
    } else {
      return generateAnalysisResponse(analysisContext, ExecutionStatus.SUCCESS, true, "Demo CV");
    }
  }

  protected abstract ExecutionResponse generateAnalysisResponse(
      AnalysisContext context, ExecutionStatus status, boolean analyzed, String message);

  protected ExecutionResponse getErrorExecutionResponse(
      ExecutionContext executionContext, VerificationDataAnalysisResponse executionResponse) {
    getLogger().info(
        "for {} got failed execution response {}", executionContext.getStateExecutionInstanceId(), executionResponse);
    continuousVerificationService.setMetaDataExecutionStatus(
        executionContext.getStateExecutionInstanceId(), executionResponse.getExecutionStatus(), true, false);
    return ExecutionResponse.builder()
        .executionStatus(executionResponse.getExecutionStatus())
        .stateExecutionData(executionResponse.getStateExecutionData())
        .errorMessage(executionResponse.getStateExecutionData().getErrorMsg())
        .build();
  }

  protected String getEnvId(ExecutionContext executionContext) {
    return ((ExecutionContextImpl) executionContext).getEnv() == null
        ? null
        : ((ExecutionContextImpl) executionContext).getEnv().getUuid();
  }

  protected String getEnvName(ExecutionContext executionContext) {
    return ((ExecutionContextImpl) executionContext).getEnv() == null
        ? null
        : ((ExecutionContextImpl) executionContext).getEnv().getName();
  }

  protected String getAppName(ExecutionContext executionContext) {
    return executionContext.getApp() == null ? null : executionContext.getApp().getName();
  }

  protected int getDelaySeconds(String initialDelay) {
    // https://harness.atlassian.net/browse/CV-3902
    // this is a hack because delay second is getting removed from UI and we will decide if we want to get rid this
    // logic completely
    return (int) TimeUnit.MINUTES.toSeconds(DELAY_MINUTES);
  }

  protected void sampleHostsMap(AnalysisContext analysisContext) {
    if (featureFlagService.isEnabled(FeatureName.CV_HOST_SAMPLING, analysisContext.getAccountId())) {
      analysisContext.setControlNodes(sampleHostsMap(analysisContext.getControlNodes(), MAX_SAMPLING_SIZE_PER_GROUP));
      analysisContext.setTestNodes(sampleHostsMap(analysisContext.getTestNodes(), MAX_SAMPLING_SIZE_PER_GROUP));
    }
  }

  private Map<String, String> sampleHostsMap(Map<String, String> hostToGroupMap, int maxSizePerGroup) {
    Map<String, List<String>> groupToHostMap = new HashMap<>();
    for (Map.Entry<String, String> entry : hostToGroupMap.entrySet()) {
      List<String> list = groupToHostMap.getOrDefault(entry.getValue(), new ArrayList<>());
      list.add(entry.getKey());
      groupToHostMap.put(entry.getValue(), list);
    }
    Map<String, String> sampledMap = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : groupToHostMap.entrySet()) {
      String groupName = entry.getKey();
      List<String> hosts = entry.getValue();
      hosts = randomSample(hosts, maxSizePerGroup);
      hosts.forEach(host -> sampledMap.put(host, groupName));
    }
    return sampledMap;
  }

  private List<String> randomSample(List<String> hosts, int maxHosts) {
    Collections.shuffle(hosts);
    return hosts.subList(0, Math.min(maxHosts, hosts.size()));
  }

  protected boolean isHistoricalAnalysis(String accountId) {
    return false;
  }

  protected String getResolvedConnectorId(ExecutionContext context, String fieldName, String fieldValue) {
    if (!isEmpty(getTemplateExpressions())) {
      TemplateExpression configIdExpression =
          templateExpressionProcessor.getTemplateExpression(getTemplateExpressions(), fieldName);
      if (configIdExpression != null) {
        SettingAttribute settingAttribute =
            templateExpressionProcessor.resolveSettingAttribute(context, configIdExpression);
        return settingAttribute.getUuid();
      }
    }

    if (isExpression(fieldName, fieldValue, getTemplateExpressions())) {
      String analysisServerConfigName = context.renderExpression(fieldValue);
      cvActivityLogService.getLoggerByStateExecutionId(context.getAccountId(), context.getStateExecutionInstanceId())
          .info("Expression " + fieldValue + " resolved to " + analysisServerConfigName);
      final SettingAttribute settingAttribute =
          settingsService.getSettingAttributeByName(context.getAccountId(), analysisServerConfigName);
      if (settingAttribute == null) {
        cvActivityLogService.getLoggerByStateExecutionId(context.getAccountId(), context.getStateExecutionInstanceId())
            .error(
                "The evaluated connector name " + analysisServerConfigName + " did not resolve to a valid connector");
        throw new DataCollectionException("Expression " + fieldValue + " resolved to " + analysisServerConfigName
            + ". There was no connector found with this name.");
      }
      cvActivityLogService.getLoggerByStateExecutionId(context.getAccountId(), context.getStateExecutionInstanceId())
          .info("The evaluated connector name " + analysisServerConfigName
              + " successfully resolved to a valid connector");
      return settingAttribute.getUuid();
    }

    return fieldValue;
  }

  protected String getResolvedFieldValue(ExecutionContext context, String fieldName, String fieldValue) {
    if (!isEmpty(getTemplateExpressions())) {
      TemplateExpression fieldExpression =
          templateExpressionProcessor.getTemplateExpression(getTemplateExpressions(), fieldName);
      if (fieldExpression != null) {
        final String resolveTemplateExpression =
            templateExpressionProcessor.resolveTemplateExpression(context, fieldExpression);
        if (isExpression(fieldName, resolveTemplateExpression, Collections.emptyList())) {
          throw new DataCollectionException("Template expression " + fieldValue + " could not be resolved");
        }

        return resolveTemplateExpression;
      }
    }

    if (isExpression(fieldName, fieldValue, getTemplateExpressions())) {
      final String renderedValue = context.renderExpression(fieldValue);
      cvActivityLogService.getLoggerByStateExecutionId(context.getAccountId(), context.getStateExecutionInstanceId())
          .info("Expression " + fieldValue + " resolved to " + renderedValue);
      if (isExpression(fieldName, renderedValue, getTemplateExpressions())) {
        throw new DataCollectionException("Expression " + fieldValue + " could not be resolved");
      }
      return renderedValue;
    }

    return fieldValue;
  }

  protected SettingAttribute getSettingAttribute(String configId) {
    SettingAttribute settingAttribute = settingsService.get(configId);
    Preconditions.checkNotNull(settingAttribute, "No connector found with id " + configId);
    return settingAttribute;
  }

  protected CVInstanceApiResponse getCVInstanceAPIResponse(ExecutionContext context) {
    String hostNameTemplate = isEmpty(getHostnameTemplate()) ? DEFAULT_HOSTNAME_TEMPLATE : getHostnameTemplate();
    Set<String> controlNodes, testNodes;
    Optional<Integer> newNodesTrafficShift;
    boolean skipVerification;
    if (getComparisonStrategy() == COMPARE_WITH_PREVIOUS) {
      InstanceApiResponse instanceApiResponse = context.renderExpressionsForInstanceDetails(hostNameTemplate, true);
      testNodes = new HashSet<>(instanceApiResponse.getInstances());
      newNodesTrafficShift = instanceApiResponse.getNewInstanceTrafficPercent();
      controlNodes = new HashSet<>();
      skipVerification = instanceApiResponse.isSkipVerification();
    } else {
      InstanceApiResponse allNodesResponse =
          context.renderExpressionsForInstanceDetailsForWorkflow(hostNameTemplate, false);
      Set<String> allNodes = new HashSet<>(allNodesResponse.getInstances());
      InstanceApiResponse instanceApiResponse = includePreviousPhaseNodes
          ? context.renderExpressionsForInstanceDetailsForWorkflow(hostNameTemplate, true)
          : context.renderExpressionsForInstanceDetails(hostNameTemplate, true);
      skipVerification = instanceApiResponse.isSkipVerification() || allNodesResponse.isSkipVerification();
      testNodes = new HashSet<>(instanceApiResponse.getInstances());
      newNodesTrafficShift = instanceApiResponse.getNewInstanceTrafficPercent();
      Set<String> allPhaseNewNodes =
          new HashSet<>(context.renderExpressionsForInstanceDetailsForWorkflow(hostNameTemplate, true).getInstances());
      controlNodes = Sets.difference(allNodes, allPhaseNewNodes);
    }
    if (!skipVerification) {
      if (!isEmptyTestNodesAllowed()) {
        // this is part of the contract with CDP team to always have test node if skipVerification is false.
        Preconditions.checkState(isNotEmpty(testNodes), "Could not find newly deployed instances.");
      }
    }
    return CVInstanceApiResponse.builder()
        .controlNodes(controlNodes)
        .skipVerification(skipVerification)
        .testNodes(testNodes)
        .newNodesTrafficShiftPercent(newNodesTrafficShift)
        .build();
  }
  @Value
  @Builder
  protected static class CVInstanceApiResponse {
    private Set<String> controlNodes;
    private Set<String> testNodes;
    private boolean skipVerification;
    private Optional<Integer> newNodesTrafficShiftPercent;
  }

  protected boolean isEmptyTestNodesAllowed() {
    return false;
  }

  protected boolean shouldFailOnEmptyNodes(String accountId) {
    if (failOnEmptyNodes && featureFlagService.isEnabled(FeatureName.CV_FAIL_ON_EMPTY_NODES, accountId)) {
      return true;
    }
    return false;
  }
}
