/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.noop;
import static io.harness.govern.Switch.unhandled;
import static io.harness.persistence.HPersistence.upToOne;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.Base.ACCOUNT_ID_KEY2;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;
import static software.wings.common.VerificationConstants.DEMO_FAILURE_LOG_STATE_EXECUTION_ID;
import static software.wings.common.VerificationConstants.DEMO_SUCCESS_LOG_STATE_EXECUTION_ID;
import static software.wings.common.VerificationConstants.IGNORED_ERRORS_METRIC_NAME;
import static software.wings.delegatetasks.ElkLogzDataCollectionTask.parseElkResponse;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SortOrder.OrderType;
import io.harness.deployment.InstanceDetails;
import io.harness.eraro.ErrorCode;
import io.harness.event.usagemetrics.UsageMetricsHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.VerificationOperationException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.govern.Switch;
import io.harness.logging.Misc;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.persistence.HIterator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.PhaseElement;
import software.wings.api.jira.JiraExecutionData;
import software.wings.beans.ElkConfig;
import software.wings.beans.InstanaConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SplunkConfig;
import software.wings.beans.SumoConfig;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.config.LogzConfig;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.RiskLevel;
import software.wings.resources.AccountResource;
import software.wings.service.impl.DelegateServiceImpl;
import software.wings.service.impl.JiraHelperService;
import software.wings.service.impl.analysis.AnalysisContext.AnalysisContextKeys;
import software.wings.service.impl.analysis.CVFeedbackRecord.CVFeedbackRecordKeys;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData.ContinuousVerificationExecutionMetaDataKeys;
import software.wings.service.impl.analysis.ExperimentalLogMLAnalysisRecord.ExperimentalLogMLAnalysisRecordKeys;
import software.wings.service.impl.analysis.LogDataRecord.LogDataRecordKeys;
import software.wings.service.impl.analysis.LogMLAnalysisRecord.LogMLAnalysisRecordKeys;
import software.wings.service.impl.elk.ElkDelegateServiceImpl;
import software.wings.service.impl.elk.ElkLogFetchRequest;
import software.wings.service.impl.elk.ElkQueryType;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask.LearningEngineAnalysisTaskKeys;
import software.wings.service.impl.newrelic.LearningEngineExperimentalAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineExperimentalAnalysisTask.LearningEngineExperimentalAnalysisTaskKeys;
import software.wings.service.impl.splunk.LogMLClusterScores;
import software.wings.service.impl.splunk.LogMLClusterScores.LogMLScore;
import software.wings.service.impl.splunk.SplunkAnalysisCluster;
import software.wings.service.impl.splunk.SplunkAnalysisCluster.MessageFrequency;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.analysis.AnalysisService;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.elk.ElkDelegateService;
import software.wings.service.intfc.instana.InstanaDelegateService;
import software.wings.service.intfc.logz.LogzDelegateService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.splunk.SplunkDelegateService;
import software.wings.service.intfc.sumo.SumoDelegateService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.service.intfc.verification.CV24x7DashboardService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.sm.ContextElement;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;
import software.wings.verification.VerificationStateAnalysisExecutionData;
import software.wings.verification.log.LogsCVConfiguration;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * Created by rsingh on 4/17/17.
 */
@ValidateOnExecution
@Slf4j
public class AnalysisServiceImpl implements AnalysisService {
  private static final double HIGH_RISK_THRESHOLD = 50;
  private static final double MEDIUM_RISK_THRESHOLD = 25;

  private static final SecureRandom random = new SecureRandom();

  private static final StateType[] logAnalysisStates = new StateType[] {StateType.SPLUNKV2, StateType.ELK};

  @Inject protected WingsPersistence wingsPersistence;
  @Inject DataStoreService dataStoreService;
  @Inject protected DelegateProxyFactory delegateProxyFactory;
  @Inject protected SettingsService settingsService;
  @Inject protected WorkflowExecutionService workflowExecutionService;
  @Inject protected DelegateServiceImpl delegateService;
  @Inject protected SecretManager secretManager;
  @Inject private AccountResource accountResource;
  @Inject private HarnessMetricRegistry metricRegistry;
  @Inject private UsageMetricsHelper usageMetricsHelper;
  @Inject private CV24x7DashboardService cv24x7DashboardService;
  @Inject private HostService hostService;
  @Inject private CVConfigurationService cvConfigurationService;
  @Inject private StateExecutionService stateExecutionService;
  @Inject private JiraHelperService jiraHelperService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private AppService appService;
  @Inject private SweepingOutputService sweepingOutputService;

  @Override
  public void cleanUpForLogRetry(String stateExecutionId) {
    // delete log data records
    wingsPersistence.delete(wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                                .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId));

    // delete log analysis records
    wingsPersistence.delete(wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
                                .filter(LogMLAnalysisRecordKeys.stateExecutionId, stateExecutionId));

    // delete cv dashboard execution data
    wingsPersistence.delete(
        wingsPersistence.createQuery(ContinuousVerificationExecutionMetaData.class, excludeAuthority)
            .filter(ContinuousVerificationExecutionMetaDataKeys.stateExecutionId, stateExecutionId));

    // delete learning engine tasks
    wingsPersistence.delete(wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
                                .filter(LearningEngineAnalysisTaskKeys.state_execution_id, stateExecutionId));

    // delete experimental learning engine tasks
    wingsPersistence.delete(
        wingsPersistence.createQuery(LearningEngineExperimentalAnalysisTask.class, excludeAuthority)
            .filter(LearningEngineExperimentalAnalysisTaskKeys.state_execution_id, stateExecutionId));

    // delete experimental log analysis records
    wingsPersistence.delete(wingsPersistence.createQuery(ExperimentalLogMLAnalysisRecord.class, excludeAuthority)
                                .filter(ExperimentalLogMLAnalysisRecordKeys.stateExecutionId, stateExecutionId));

    // delete verification service tasks
    wingsPersistence.delete(wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                                .filter(AnalysisContextKeys.stateExecutionId, stateExecutionId));
  }

  private boolean deleteFeedbackHelper(String feedbackId) {
    dataStoreService.delete(LogMLFeedbackRecord.class, feedbackId);
    return true;
  }

  @Override
  public boolean deleteFeedback(String feedbackId) {
    Preconditions.checkNotNull(feedbackId, "empty or null feedback id set ");
    return deleteFeedbackHelper(feedbackId);
  }

  @Override
  public LogMLAnalysisSummary getAnalysisSummaryForDemo(
      String stateExecutionId, String applicationId, StateType stateType) {
    log.info("Creating log analysis summary for demo {}", stateExecutionId);
    StateExecutionInstance stateExecutionInstance =
        workflowExecutionService.getStateExecutionData(applicationId, stateExecutionId);
    if (stateExecutionInstance == null) {
      log.error("State execution instance not found for {}", stateExecutionId);
      return null;
    }

    SettingAttribute settingAttribute =
        settingsService.get(((VerificationStateAnalysisExecutionData) stateExecutionInstance.fetchStateExecutionData())
                                .getServerConfigId());

    if (settingAttribute.getName().toLowerCase().endsWith("dev")
        || settingAttribute.getName().toLowerCase().endsWith("prod")) {
      if (stateExecutionInstance.getStatus() == ExecutionStatus.SUCCESS) {
        return getAnalysisSummary(DEMO_SUCCESS_LOG_STATE_EXECUTION_ID + stateType.getName(), applicationId, stateType);
      } else {
        return getAnalysisSummary(DEMO_FAILURE_LOG_STATE_EXECUTION_ID + stateType.getName(), applicationId, stateType);
      }
    }
    return getAnalysisSummary(stateExecutionId, applicationId, stateType);
  }

  @Override
  public List<LogMLFeedbackRecord> getMLFeedback(
      String appId, String serviceId, String workflowId, String workflowExecutionId) {
    PageRequest<LogMLFeedbackRecord> feedbackRecordPageRequest =
        PageRequestBuilder.aPageRequest().withLimit(UNLIMITED).build();

    feedbackRecordPageRequest.addFilter("serviceId", Operator.EQ, serviceId);
    feedbackRecordPageRequest.addFilter("workflowId", Operator.EQ, workflowId);
    feedbackRecordPageRequest.addFilter("workflowExecutionId", Operator.EQ, workflowExecutionId);

    List<LogMLFeedbackRecord> records = dataStoreService.list(LogMLFeedbackRecord.class, feedbackRecordPageRequest);

    PageRequest<LogMLFeedbackRecord> feedbackRecordPageRequestServiceOnly =
        PageRequestBuilder.aPageRequest().withLimit(UNLIMITED).addFilter("serviceId", Operator.EQ, serviceId).build();

    List<LogMLFeedbackRecord> recordsServiceOnlyFilter =
        dataStoreService.list(LogMLFeedbackRecord.class, feedbackRecordPageRequestServiceOnly);

    Set<LogMLFeedbackRecord> recordSet = new HashSet<>();

    records.forEach(recordSet::add);
    recordsServiceOnlyFilter.forEach(recordSet::add);

    return new ArrayList<>(recordSet);
  }

  @Override
  public List<LogMLFeedbackRecord> get24x7MLFeedback(String cvConfigId) {
    PageRequest<LogMLFeedbackRecord> feedbackPageRequest =
        PageRequestBuilder.aPageRequest().withLimit(UNLIMITED).addFilter("cvConfigId", Operator.EQ, cvConfigId).build();
    return dataStoreService.list(LogMLFeedbackRecord.class, feedbackPageRequest);
  }

  @Override
  public boolean saveFeedback(LogMLFeedback feedback, StateType stateType) {
    if (!isEmpty(feedback.getLogMLFeedbackId())) {
      deleteFeedbackHelper(feedback.getLogMLFeedbackId());
    }

    StateExecutionInstance stateExecutionInstance = wingsPersistence.getWithAppId(
        StateExecutionInstance.class, feedback.getAppId(), feedback.getStateExecutionId());

    Preconditions.checkNotNull(
        stateExecutionInstance, "Unable to find state execution for id {}", feedback.getStateExecutionId());
    LogMLAnalysisSummary analysisSummary =
        getAnalysisSummary(feedback.getStateExecutionId(), feedback.getAppId(), stateType);

    String logText = getLogTextFromAnalysisSummary(analysisSummary, feedback);

    String logmd5Hash = DigestUtils.md5Hex(logText);

    Optional<ContextElement> optionalElement = stateExecutionInstance.getContextElements()
                                                   .stream()
                                                   .filter(contextElement -> contextElement instanceof PhaseElement)
                                                   .findFirst();
    Preconditions.checkState(optionalElement.isPresent(), "Unable to find phase element for state execution id {}",
        stateExecutionInstance.getUuid());

    PhaseElement phaseElement = (PhaseElement) optionalElement.get();

    LogMLFeedbackRecord mlFeedbackRecord = LogMLFeedbackRecord.builder()
                                               .appId(feedback.getAppId())
                                               .serviceId(phaseElement.getServiceElement().getUuid())
                                               .workflowId(stateExecutionInstance.getWorkflowId())
                                               .workflowExecutionId(stateExecutionInstance.getExecutionUuid())
                                               .stateExecutionId(feedback.getStateExecutionId())
                                               .logMessage(logText)
                                               .logMLFeedbackType(feedback.getLogMLFeedbackType())
                                               .clusterLabel(feedback.getClusterLabel())
                                               .clusterType(feedback.getClusterType())
                                               .logMD5Hash(logmd5Hash)
                                               .stateType(stateType)
                                               .comment(feedback.getComment())
                                               .build();

    dataStoreService.save(LogMLFeedbackRecord.class, Arrays.asList(mlFeedbackRecord), true);

    // also "add to baseline" for new CVFeedbacks

    CVFeedbackRecord record = CVFeedbackRecord.builder()
                                  .clusterLabel(feedback.getClusterLabel())
                                  .logMessage(logText)
                                  .stateExecutionId(feedback.getStateExecutionId())
                                  .clusterType(feedback.getClusterType())
                                  .build();
    addToBaseline(appService.getAccountIdByAppId(feedback.getAppId()), null, feedback.getStateExecutionId(), record);

    metricRegistry.recordGaugeInc(IGNORED_ERRORS_METRIC_NAME,
        new String[] {feedback.getLogMLFeedbackType().toString(), stateType.toString(), feedback.getAppId(),
            stateExecutionInstance.getWorkflowId()});

    return true;
  }

  @Override
  public boolean addToBaseline(
      String accountId, String cvConfigId, String stateExecutionId, CVFeedbackRecord feedbackRecord) {
    CVFeedbackRecord feedbackRecordFromDataStore = feedbackRecord;
    if (isNotEmpty(feedbackRecord.getUuid())) {
      feedbackRecordFromDataStore = dataStoreService.getEntity(CVFeedbackRecord.class, feedbackRecord.getUuid());
      checkIfActionIsAllowed(feedbackRecordFromDataStore, FeedbackAction.ADD_TO_BASELINE);
    }
    feedbackRecordFromDataStore.setPriority(FeedbackPriority.BASELINE);
    feedbackRecordFromDataStore.setFeedbackNote(feedbackRecord.getFeedbackNote());
    return saveLogFeedback(
        accountId, cvConfigId, stateExecutionId, feedbackRecordFromDataStore, FeedbackAction.ADD_TO_BASELINE);
  }

  @Override
  public boolean removeFromBaseline(
      String accountId, String cvConfigId, String stateExecutionId, CVFeedbackRecord feedbackRecord) {
    CVFeedbackRecord feedbackRecordFromDataStore = feedbackRecord;
    if (isNotEmpty(feedbackRecord.getUuid())) {
      feedbackRecordFromDataStore = dataStoreService.getEntity(CVFeedbackRecord.class, feedbackRecord.getUuid());
      checkIfActionIsAllowed(feedbackRecordFromDataStore, FeedbackAction.REMOVE_FROM_BASELINE);
    }
    feedbackRecordFromDataStore.setPriority(feedbackRecord.getPriority());
    feedbackRecordFromDataStore.setFeedbackNote(feedbackRecord.getFeedbackNote());
    return saveLogFeedback(
        accountId, cvConfigId, stateExecutionId, feedbackRecordFromDataStore, FeedbackAction.REMOVE_FROM_BASELINE);
  }

  @Override
  public boolean updateFeedbackPriority(
      String accountId, String cvConfigId, String stateExecutionId, CVFeedbackRecord feedbackRecord) {
    CVFeedbackRecord feedbackRecordFromDataStore = feedbackRecord;
    if (isNotEmpty(feedbackRecord.getUuid())) {
      feedbackRecordFromDataStore = dataStoreService.getEntity(CVFeedbackRecord.class, feedbackRecord.getUuid());
      checkIfActionIsAllowed(feedbackRecordFromDataStore, FeedbackAction.UPDATE_PRIORITY);
    }
    feedbackRecordFromDataStore.setPriority(feedbackRecord.getPriority());
    feedbackRecordFromDataStore.setFeedbackNote(feedbackRecord.getFeedbackNote());
    return saveLogFeedback(
        accountId, cvConfigId, stateExecutionId, feedbackRecordFromDataStore, FeedbackAction.UPDATE_PRIORITY);
  }

  private boolean checkIfActionIsAllowed(CVFeedbackRecord feedbackRecordFromDataStore, FeedbackAction nextAction) {
    if (feedbackRecordFromDataStore != null) {
      Map<FeedbackAction, List<FeedbackAction>> nextActions = getNextFeedbackActions();
      if (nextActions.containsKey(feedbackRecordFromDataStore.getActionTaken())) {
        if (!nextActions.get(feedbackRecordFromDataStore.getActionTaken()).contains(nextAction)) {
          throw new WingsException(ErrorCode.GENERAL_ERROR, USER)
              .addParam("reason", nextAction + " is not allowed on this feedback record");
        }
      }
    }
    return true;
  }

  @Override
  public Map<FeedbackAction, List<FeedbackAction>> getNextFeedbackActions() {
    Map<FeedbackAction, List<FeedbackAction>> feedbackActionListMap = new HashMap<>();
    feedbackActionListMap.put(FeedbackAction.ADD_TO_BASELINE, Arrays.asList(FeedbackAction.REMOVE_FROM_BASELINE));
    feedbackActionListMap.put(
        FeedbackAction.UPDATE_PRIORITY, Arrays.asList(FeedbackAction.UPDATE_PRIORITY, FeedbackAction.ADD_TO_BASELINE));
    feedbackActionListMap.put(FeedbackAction.REMOVE_FROM_BASELINE,
        Arrays.asList(FeedbackAction.UPDATE_PRIORITY, FeedbackAction.ADD_TO_BASELINE));

    return feedbackActionListMap;
  }

  private boolean saveLogFeedback(String accountId, String cvConfigId, String stateExecutionId,
      CVFeedbackRecord feedbackRecord, FeedbackAction feedbackAction) {
    Preconditions.checkState(
        isNotEmpty(feedbackRecord.getLogMessage()), "Log Message cannot be empty while saving a feedback");
    if (isNotEmpty(cvConfigId)) {
      CVConfiguration cvConfiguration = cvConfigurationService.getConfiguration(cvConfigId);
      feedbackRecord.setServiceId(cvConfiguration.getServiceId());
      feedbackRecord.setEnvId(cvConfiguration.getEnvId());
      feedbackRecord.setCvConfigId(cvConfigId);
    } else if (isNotEmpty(stateExecutionId)) {
      List<StateExecutionInstance> stateExecutionInstances =
          wingsPersistence.createQuery(StateExecutionInstance.class, excludeAuthority)
              .filter(StateExecutionInstanceKeys.uuid, stateExecutionId)
              .asList();
      if (isNotEmpty(stateExecutionInstances) && stateExecutionInstances.size() == 1) {
        StateExecutionInstance stateExecutionInstance = stateExecutionInstances.get(0);
        feedbackRecord.setStateExecutionId(stateExecutionId);

        feedbackRecord.setServiceId(getServiceIdFromStateExecutionInstance(stateExecutionInstance));
        feedbackRecord.setEnvId(
            getEnvIdForStateExecutionId(stateExecutionInstance.getAppId(), stateExecutionInstance.getExecutionUuid()));
      } else {
        throw new WingsException("Incorrect stateExecutionId. No valid instances available");
      }
      feedbackRecord.setStateExecutionId(stateExecutionId);
    } else {
      throw new WingsException("Missing cvConfigId or stateExecutionId to create/modify a feedback");
    }
    feedbackRecord.setAccountId(accountId);
    feedbackRecord.setActionTaken(feedbackAction);
    dataStoreService.save(CVFeedbackRecord.class, Arrays.asList(feedbackRecord), false);
    return true;
  }

  private String getEnvIdForStateExecutionId(String appId, String workflowExecutionId) {
    WorkflowExecution execution = workflowExecutionService.getWorkflowExecution(appId, workflowExecutionId);
    Preconditions.checkNotNull(execution, "This stateExecutionId does not correspond to a valid workflow");
    return execution.getEnvId();
  }

  private String getServiceIdFromStateExecutionInstance(StateExecutionInstance instance) {
    PhaseElement phaseElement = null;
    for (ContextElement element : instance.getContextElements()) {
      if (element instanceof PhaseElement) {
        phaseElement = (PhaseElement) element;
        break;
      }
    }
    if (phaseElement != null) {
      return phaseElement.getServiceElement().getUuid();
    }
    throw new WingsException("There is no serviceID associated with the stateExecutionId: " + instance.getUuid());
  }

  @Override
  public String createCollaborationFeedbackTicket(String accountId, String appId, String cvConfigId,
      String stateExecutionId, CVCollaborationProviderParameters cvJiraParameters) {
    if (cvJiraParameters == null || isEmpty(cvJiraParameters.getCollaborationProviderConfigId())
        || cvJiraParameters.getJiraTaskParameters() == null) {
      String errMsg = String.format(
          "Empty Jira parameters sent in createCollaborationFeedbackTicket for cvConfigId/StateExecutionId %s, %s",
          cvConfigId, stateExecutionId);
      log.error(errMsg);
      throw new WingsException(ErrorCode.GENERAL_ERROR).addParam("reason", errMsg);
    }

    JiraExecutionData jiraExecutionData = jiraHelperService.createJira(accountId, appId,
        cvJiraParameters.getCollaborationProviderConfigId(), cvJiraParameters.getJiraTaskParameters());
    if (jiraExecutionData == null || jiraExecutionData.getExecutionStatus() != ExecutionStatus.SUCCESS) {
      log.error("Unable to create jira ticket for cvConfigId/stateExecutionId {}/{}", cvConfigId, stateExecutionId);
      throw new WingsException("Unable to create Jira ticket");
    }
    String jiraLink = jiraExecutionData.getIssueUrl();

    if (isEmpty(cvJiraParameters.getCvFeedbackRecord().getUuid())) {
      log.error(
          "Creating a jira before giving user feedback or priority. This is not allowed. cvCOnfigId: {}", cvConfigId);
      throw new VerificationOperationException(
          ErrorCode.DEFAULT_ERROR_CODE, "Cannot create jira without a user feedback");
    }
    CVFeedbackRecord feedbackRecord =
        dataStoreService.getEntity(CVFeedbackRecord.class, cvJiraParameters.getCvFeedbackRecord().getUuid());
    feedbackRecord.setJiraLink(jiraLink);
    dataStoreService.save(CVFeedbackRecord.class, Arrays.asList(feedbackRecord), false);
    return jiraLink;
  }

  @Override
  public List<CVFeedbackRecord> getFeedbacks(String cvConfigId, String stateExecutionId, boolean isDemoPath) {
    PageRequest<CVFeedbackRecord> feedbackRecordPageRequest = PageRequestBuilder.aPageRequest().build();
    String serviceId = null, envId = null;
    if (isNotEmpty(cvConfigId)) {
      CVConfiguration cvConfiguration = cvConfigurationService.getConfiguration(cvConfigId);
      serviceId = cvConfiguration.getServiceId();
      envId = cvConfiguration.getEnvId();

    } else if (isNotEmpty(stateExecutionId)) {
      List<StateExecutionInstance> stateExecutionInstances =
          wingsPersistence.createQuery(StateExecutionInstance.class, excludeAuthority)
              .filter(StateExecutionInstanceKeys.uuid, stateExecutionId)
              .asList();

      if (isNotEmpty(stateExecutionInstances) && stateExecutionInstances.size() == 1) {
        StateExecutionInstance instance = stateExecutionInstances.get(0);
        serviceId = getServiceIdFromStateExecutionInstance(instance);
        WorkflowExecution execution =
            workflowExecutionService.getWorkflowExecution(instance.getAppId(), instance.getExecutionUuid());
        envId = execution.getEnvId();
      }
    } else {
      throw new WingsException("Missing cvConfigId or stateExecutionId to create/modify a feedback");
    }

    if (isDemoPath) {
      feedbackRecordPageRequest.addFilter(CVFeedbackRecordKeys.stateExecutionId, Operator.EQ, stateExecutionId);
    } else {
      feedbackRecordPageRequest.addFilter(CVFeedbackRecordKeys.serviceId, Operator.EQ, serviceId);
      feedbackRecordPageRequest.addFilter(CVFeedbackRecordKeys.envId, Operator.EQ, envId);
    }
    return dataStoreService.list(CVFeedbackRecord.class, feedbackRecordPageRequest).getResponse();
  }

  @Override
  public boolean save24x7Feedback(LogMLFeedback feedback, String cvConfigId) {
    if (!isEmpty(feedback.getLogMLFeedbackId())) {
      deleteFeedbackHelper(feedback.getLogMLFeedbackId());
    }
    LogMLAnalysisSummary analysisSummary =
        cv24x7DashboardService.getAnalysisSummary(cvConfigId, null, null, feedback.getAppId());

    String logText = getLogTextFromAnalysisSummary(analysisSummary, feedback);

    String logmd5Hash = DigestUtils.md5Hex(logText);

    LogMLFeedbackRecord mlFeedbackRecord = LogMLFeedbackRecord.builder()
                                               .appId(feedback.getAppId())
                                               .logMessage(logText)
                                               .logMLFeedbackType(feedback.getLogMLFeedbackType())
                                               .clusterLabel(feedback.getClusterLabel())
                                               .clusterType(feedback.getClusterType())
                                               .logMD5Hash(logmd5Hash)
                                               .comment(feedback.getComment())
                                               .cvConfigId(cvConfigId)
                                               .build();

    dataStoreService.save(LogMLFeedbackRecord.class, Arrays.asList(mlFeedbackRecord), true);

    return true;
  }

  private String getLogTextFromAnalysisSummary(LogMLAnalysisSummary analysisSummary, LogMLFeedback feedback) {
    Preconditions.checkNotNull(analysisSummary, "Unable to find analysisSummary for feedback " + feedback);

    String logText = "";
    List<LogMLClusterSummary> logMLClusterSummaryList = new ArrayList<>();
    switch (feedback.getClusterType()) {
      case CONTROL:
        logMLClusterSummaryList = analysisSummary.getControlClusters();
        break;
      case TEST:
        logMLClusterSummaryList = analysisSummary.getTestClusters();
        break;
      case UNKNOWN:
        logMLClusterSummaryList = analysisSummary.getUnknownClusters();
        break;
      default:
        Switch.unhandled(feedback.getClusterType());
    }

    for (LogMLClusterSummary clusterSummary : logMLClusterSummaryList) {
      if (clusterSummary.getClusterLabel() == feedback.getClusterLabel()) {
        logText = clusterSummary.getLogText();
      }
    }

    Preconditions.checkNotNull(logText, "Unable to find logText for feedback {}", feedback);
    return logText;
  }

  @Override
  public String getLastSuccessfulWorkflowExecutionIdWithLogs(String stateExecutionId, StateType stateType, String appId,
      String serviceId, String workflowId, String query, String infraMappingId, String envId) {
    try (HIterator<ContinuousVerificationExecutionMetaData> cvMetaDateIterator = new HIterator<>(
             wingsPersistence.createQuery(ContinuousVerificationExecutionMetaData.class, excludeAuthority)
                 .filter(ContinuousVerificationExecutionMetaDataKeys.workflowId, workflowId)
                 .filter(ContinuousVerificationExecutionMetaDataKeys.stateType, stateType)
                 .filter(ContinuousVerificationExecutionMetaDataKeys.executionStatus, ExecutionStatus.SUCCESS)
                 .order(Sort.descending(ContinuousVerificationExecutionMetaDataKeys.workflowStartTs))
                 .fetch())) {
      boolean hasSuccessfulExecution = false;
      String lastSuccessFulExecution = null;
      while (cvMetaDateIterator.hasNext()) {
        ContinuousVerificationExecutionMetaData cvMetaData = cvMetaDateIterator.next();
        String workflowExecutionId = cvMetaData.getWorkflowExecutionId();
        WorkflowExecution execution = workflowExecutionService.getWorkflowExecution(appId, workflowExecutionId);
        if (execution != null) {
          if (!execution.getInfraMappingIds().contains(infraMappingId) || !execution.getEnvId().equals(envId)
              || !execution.getServiceIds().contains(serviceId)) {
            // infra mapping ID should also match, for us to call it a potential baseline.
            log.info("Execution {} does not have infraMappingID {} or envId {}. So moving on.", execution.getUuid(),
                infraMappingId, envId);
            continue;
          }
        }
        if (!hasSuccessfulExecution) {
          hasSuccessfulExecution = true;
          lastSuccessFulExecution = cvMetaData.getWorkflowExecutionId();
        }
        if (wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                .filter(LogDataRecordKeys.workflowExecutionId, cvMetaData.getWorkflowExecutionId())
                .filter(LogDataRecordKeys.clusterLevel, ClusterLevel.L2)
                .filter(LogDataRecordKeys.query, query)
                .count(upToOne)
            > 0) {
          log.info("Found an execution for auto baseline. WorkflowExecutionId {}, stateExecutionId {}",
              cvMetaData.getWorkflowExecutionId(), stateExecutionId);
          return cvMetaData.getWorkflowExecutionId();
        }
      }

      if (hasSuccessfulExecution) {
        log.info(
            "We did not find any execution with data. Returning workflowExecution {} as baseline for stateExecutionId {}",
            lastSuccessFulExecution, stateExecutionId);
        return lastSuccessFulExecution;
      }
      log.warn("Could not get a successful workflow to find control nodes for stateExecutionId: {}", stateExecutionId);
      return null;
    }
  }

  private Map<CLUSTER_TYPE, Map<Integer, LogMLFeedbackRecord>> get24x7MLUserFeedbacks(String cvConfigId, String appId) {
    Map<CLUSTER_TYPE, Map<Integer, LogMLFeedbackRecord>> userFeedbackMap = new HashMap<>();
    userFeedbackMap.put(CLUSTER_TYPE.CONTROL, new HashMap<>());
    userFeedbackMap.put(CLUSTER_TYPE.TEST, new HashMap<>());
    userFeedbackMap.put(CLUSTER_TYPE.UNKNOWN, new HashMap<>());

    PageRequest<LogMLFeedbackRecord> feedbackRecordPageRequest =
        PageRequestBuilder.aPageRequest().withLimit(UNLIMITED).addFilter("cvConfigId", Operator.EQ, cvConfigId).build();
    List<LogMLFeedbackRecord> logMLFeedbackRecords =
        dataStoreService.list(LogMLFeedbackRecord.class, feedbackRecordPageRequest);

    if (logMLFeedbackRecords == null) {
      return userFeedbackMap;
    }

    for (LogMLFeedbackRecord logMLFeedbackRecord : logMLFeedbackRecords) {
      Map<Integer, LogMLFeedbackRecord> feedbackRecordMap = userFeedbackMap.get(logMLFeedbackRecord.getClusterType());

      if (null != feedbackRecordMap) {
        feedbackRecordMap.put(logMLFeedbackRecord.getClusterLabel(), logMLFeedbackRecord);
      } else {
        log.error("feedbackRecordMap is null for key: {}", logMLFeedbackRecord);
      }
    }

    return userFeedbackMap;
  }

  private Map<CLUSTER_TYPE, Map<Integer, LogMLFeedbackRecord>> getMLUserFeedbacks(
      String stateExecutionId, String appId) {
    Map<CLUSTER_TYPE, Map<Integer, LogMLFeedbackRecord>> userFeedbackMap = new HashMap<>();
    userFeedbackMap.put(CLUSTER_TYPE.CONTROL, new HashMap<>());
    userFeedbackMap.put(CLUSTER_TYPE.TEST, new HashMap<>());
    userFeedbackMap.put(CLUSTER_TYPE.UNKNOWN, new HashMap<>());

    PageRequest<LogMLFeedbackRecord> feedbackRecordPageRequest =
        PageRequestBuilder.aPageRequest()
            .withLimit(UNLIMITED)
            .addFilter("stateExecutionId", Operator.EQ, stateExecutionId)
            .build();
    List<LogMLFeedbackRecord> logMLFeedbackRecords =
        dataStoreService.list(LogMLFeedbackRecord.class, feedbackRecordPageRequest);

    if (logMLFeedbackRecords == null) {
      return userFeedbackMap;
    }

    for (LogMLFeedbackRecord logMLFeedbackRecord : logMLFeedbackRecords) {
      if (userFeedbackMap.get(logMLFeedbackRecord.getClusterType()) != null) {
        userFeedbackMap.get(logMLFeedbackRecord.getClusterType())
            .put(logMLFeedbackRecord.getClusterLabel(), logMLFeedbackRecord);
      }
    }

    return userFeedbackMap;
  }

  private void assignUserFeedback(
      LogMLAnalysisSummary analysisSummary, Map<CLUSTER_TYPE, Map<Integer, LogMLFeedbackRecord>> mlUserFeedbacks) {
    for (LogMLClusterSummary summary : analysisSummary.getControlClusters()) {
      if (mlUserFeedbacks.get(CLUSTER_TYPE.CONTROL).containsKey(summary.getClusterLabel())) {
        summary.setLogMLFeedbackType(
            mlUserFeedbacks.get(CLUSTER_TYPE.CONTROL).get(summary.getClusterLabel()).getLogMLFeedbackType());
        summary.setLogMLFeedbackId(mlUserFeedbacks.get(CLUSTER_TYPE.CONTROL).get(summary.getClusterLabel()).getUuid());
      }
    }

    for (LogMLClusterSummary summary : analysisSummary.getTestClusters()) {
      if (mlUserFeedbacks.get(CLUSTER_TYPE.TEST).containsKey(summary.getClusterLabel())) {
        summary.setLogMLFeedbackType(
            mlUserFeedbacks.get(CLUSTER_TYPE.TEST).get(summary.getClusterLabel()).getLogMLFeedbackType());
        summary.setLogMLFeedbackId(mlUserFeedbacks.get(CLUSTER_TYPE.TEST).get(summary.getClusterLabel()).getUuid());
      }
    }

    for (LogMLClusterSummary summary : analysisSummary.getUnknownClusters()) {
      if (mlUserFeedbacks.get(CLUSTER_TYPE.UNKNOWN).containsKey(summary.getClusterLabel())) {
        summary.setLogMLFeedbackType(
            mlUserFeedbacks.get(CLUSTER_TYPE.UNKNOWN).get(summary.getClusterLabel()).getLogMLFeedbackType());
        summary.setLogMLFeedbackId(mlUserFeedbacks.get(CLUSTER_TYPE.UNKNOWN).get(summary.getClusterLabel()).getUuid());
      }
    }
  }

  @Override
  public LogMLAnalysisSummary getAnalysisSummary(String stateExecutionId, String appId, StateType stateType) {
    LogMLAnalysisRecord recordsForThisExecution =
        wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
            .filter(LogMLAnalysisRecordKeys.stateExecutionId, stateExecutionId)
            .filter(LogMLAnalysisRecordKeys.analysisStatus, LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE)
            .get();

    Query<LogMLAnalysisRecord> analysisRecordQuery =
        wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
            .filter(LogMLAnalysisRecordKeys.stateExecutionId, stateExecutionId)
            .order(Sort.descending(LogMLAnalysisRecordKeys.logCollectionMinute), Sort.descending("lastUpdatedAt"));

    if (recordsForThisExecution == null) {
      analysisRecordQuery =
          analysisRecordQuery.filter(LogMLAnalysisRecordKeys.analysisStatus, LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);
    } else {
      analysisRecordQuery = analysisRecordQuery.filter(
          LogMLAnalysisRecordKeys.analysisStatus, LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE);
    }

    String accountId = appService.getAccountIdByAppId(appId);

    LogMLAnalysisRecord analysisRecord = analysisRecordQuery.get();
    if (analysisRecord == null) {
      return null;
    }

    analysisRecord.decompressLogAnalysisRecord();
    Map<CLUSTER_TYPE, Map<Integer, LogMLFeedbackRecord>> mlUserFeedbacks = getMLUserFeedbacks(stateExecutionId, appId);

    LogMLClusterScores logMLClusterScores =
        analysisRecord.getCluster_scores() != null ? analysisRecord.getCluster_scores() : new LogMLClusterScores();

    LogMLAnalysisSummary analysisSummary =
        LogMLAnalysisSummary.builder()
            .query(analysisRecord.getQuery())
            .stateType(stateType)
            .analysisMinute(analysisRecord.getLogCollectionMinute())
            .score(analysisRecord.getScore() * 100)
            .baseLineExecutionId(analysisRecord.getBaseLineExecutionId())
            .controlClusters(
                computeCluster(analysisRecord.getControl_clusters(), Collections.emptyMap(), CLUSTER_TYPE.CONTROL))
            .testClusters(
                computeCluster(analysisRecord.getTest_clusters(), logMLClusterScores.getTest(), CLUSTER_TYPE.TEST))
            .unknownClusters(computeCluster(
                analysisRecord.getUnknown_clusters(), logMLClusterScores.getUnknown(), CLUSTER_TYPE.UNKNOWN))
            .ignoreClusters(
                computeCluster(analysisRecord.getIgnore_clusters(), Collections.emptyMap(), CLUSTER_TYPE.IGNORE))
            .build();

    AnalysisContext analysisContext = wingsPersistence.createQuery(AnalysisContext.class)
                                          .filter("appId", appId)
                                          .filter(AnalysisContextKeys.stateExecutionId, stateExecutionId)
                                          .get();

    if (analysisContext != null) {
      analysisSummary.setAnalysisComparisonStrategy(analysisContext.getComparisonStrategy());
      analysisSummary.setTimeDuration(analysisContext.getTimeDuration());
      analysisSummary.setNewVersionNodes(
          isEmpty(analysisContext.getTestNodes()) ? Collections.emptySet() : analysisContext.getTestNodes().keySet());
      analysisSummary.setPreviousVersionNodes(isEmpty(analysisContext.getControlNodes())
              ? Collections.emptySet()
              : analysisContext.getControlNodes().keySet());
    }
    if (!analysisRecord.isBaseLineCreated()) {
      analysisSummary.setTestClusters(analysisSummary.getControlClusters());
      analysisSummary.setControlClusters(new ArrayList<>());
    }

    assignUserFeedback(analysisSummary, mlUserFeedbacks);

    RiskLevel riskLevel = RiskLevel.NA;
    String analysisSummaryMsg = isEmpty(analysisRecord.getAnalysisSummaryMessage())
        ? analysisSummary.getControlClusters().isEmpty() ? "No baseline data for the given query was found."
            : analysisSummary.getTestClusters().isEmpty()
            ? "No new data for the given queries. Showing baseline data if any."
            : "No anomaly found"
        : analysisRecord.getAnalysisSummaryMessage();

    // Update with the feedback clusters
    if (!featureFlagService.isEnabled(FeatureName.DISABLE_LOGML_NEURAL_NET, accountId)) {
      boolean isDemoPath = featureFlagService.isEnabled(FeatureName.CV_DEMO, accountId);
      List<CVFeedbackRecord> feedbackRecords = getFeedbacks(null, stateExecutionId, isDemoPath);
      Map<CLUSTER_TYPE, Map<Integer, CVFeedbackRecord>> clusterTypeRecordMap = new HashMap<>();
      feedbackRecords.forEach(cvFeedbackRecord -> {
        if (isNotEmpty(cvFeedbackRecord.getStateExecutionId())
            && cvFeedbackRecord.getStateExecutionId().equals(stateExecutionId)) {
          CLUSTER_TYPE type = cvFeedbackRecord.getClusterType();
          if (!clusterTypeRecordMap.containsKey(type)) {
            clusterTypeRecordMap.put(type, new HashMap<>());
          }

          clusterTypeRecordMap.get(cvFeedbackRecord.getClusterType())
              .put(cvFeedbackRecord.getClusterLabel(), cvFeedbackRecord);
        }
      });

      updateClustersWithFeedback(clusterTypeRecordMap, CLUSTER_TYPE.CONTROL, analysisSummary.getControlClusters());
      updateClustersWithFeedback(clusterTypeRecordMap, CLUSTER_TYPE.TEST, analysisSummary.getTestClusters());
      updateClustersWithFeedback(clusterTypeRecordMap, CLUSTER_TYPE.UNKNOWN, analysisSummary.getUnknownClusters());
    }
    //----------------------------
    int unknownClusters = 0;
    int highRiskClusters = 0;
    int mediumRiskCluster = 0;
    int lowRiskClusters = 0;
    if (isNotEmpty(analysisSummary.getUnknownClusters())) {
      for (LogMLClusterSummary clusterSummary : analysisSummary.getUnknownClusters()) {
        if (clusterSummary.getScore() > HIGH_RISK_THRESHOLD) {
          ++highRiskClusters;
        } else if (clusterSummary.getScore() > MEDIUM_RISK_THRESHOLD) {
          ++mediumRiskCluster;
        } else if (clusterSummary.getScore() > 0) {
          ++lowRiskClusters;
        }
      }
      riskLevel = highRiskClusters > 0 ? RiskLevel.HIGH
          : mediumRiskCluster > 0      ? RiskLevel.MEDIUM
          : lowRiskClusters > 0        ? RiskLevel.LOW
                                       : RiskLevel.HIGH;

      unknownClusters = analysisSummary.getUnknownClusters().size();
      analysisSummary.setHighRiskClusters(highRiskClusters);
      analysisSummary.setMediumRiskClusters(mediumRiskCluster);
      analysisSummary.setLowRiskClusters(lowRiskClusters);
    }

    int unknownFrequency = getUnexpectedFrequency(analysisRecord.getTest_clusters());
    if (unknownFrequency > 0) {
      analysisSummary.setHighRiskClusters(analysisSummary.getHighRiskClusters() + unknownFrequency);
      riskLevel = RiskLevel.HIGH;
    }

    if (highRiskClusters > 0 || mediumRiskCluster > 0 || lowRiskClusters > 0) {
      analysisSummaryMsg = analysisSummary.getHighRiskClusters() + " high risk, "
          + analysisSummary.getMediumRiskClusters() + " medium risk, " + analysisSummary.getLowRiskClusters()
          + " low risk anomalous cluster(s) found";
    } else if (unknownClusters > 0 || unknownFrequency > 0) {
      int totalAnomalies = unknownClusters + unknownFrequency;
      analysisSummaryMsg = totalAnomalies == 1 ? totalAnomalies + " anomalous cluster found"
                                               : totalAnomalies + " anomalous clusters found";
    }

    analysisSummary.setRiskLevel(riskLevel);
    analysisSummary.setAnalysisSummaryMessage(analysisSummaryMsg);
    analysisSummary.setStateType(stateType);
    populateWorkflowDetails(analysisSummary, analysisContext);
    return analysisSummary;
  }

  @Override
  public void updateClustersWithFeedback(Map<CLUSTER_TYPE, Map<Integer, CVFeedbackRecord>> clusterTypeRecordMap,
      CLUSTER_TYPE type, List<LogMLClusterSummary> clusterList) {
    // first set all the feedback data for which the logMLFeedbackId is already present.
    if (isNotEmpty(clusterList)) {
      clusterList.forEach(cluster -> {
        if (isNotEmpty(cluster.getLogMLFeedbackId())) {
          CVFeedbackRecord feedbackRecord =
              dataStoreService.getEntity(CVFeedbackRecord.class, cluster.getLogMLFeedbackId());
          addFeedbackDataToCluster(cluster, feedbackRecord);
        }
      });
    }

    // update the clusters for which feedback was given in this state
    if (clusterTypeRecordMap.containsKey(type)) {
      Map<Integer, CVFeedbackRecord> labelMap = clusterTypeRecordMap.get(type);
      clusterList.forEach(cluster -> {
        if (labelMap.containsKey(cluster.getClusterLabel())) {
          CVFeedbackRecord feedbackRecord = labelMap.get(cluster.getClusterLabel());
          addFeedbackDataToCluster(cluster, feedbackRecord);
        }
      });
    }
  }

  private void addFeedbackDataToCluster(LogMLClusterSummary cluster, CVFeedbackRecord record) {
    cluster.setJiraLink(record.getJiraLink());
    cluster.setPriority(record.getPriority());
    cluster.setLogMLFeedbackId(record.getUuid());
    LogMLFeedbackSummary feedbackSummary = LogMLFeedbackSummary.builder()
                                               .logMLFeedbackId(record.getUuid())
                                               .jiraLink(record.getJiraLink())
                                               .priority(record.getPriority())
                                               .feedbackNote(record.getFeedbackNote())
                                               .lastUpdatedAt(record.getLastUpdatedAt())
                                               .lastUpdatedBy(record.getLastUpdatedBy())
                                               .build();
    cluster.setFeedbackSummary(feedbackSummary);
  }

  private void populateWorkflowDetails(LogMLAnalysisSummary analysisSummary, AnalysisContext analysisContext) {
    if (analysisSummary == null || analysisContext == null) {
      return;
    }

    long minAnalyzedMinute = getLogRecordMinute(
        analysisContext.getAppId(), analysisContext.getStateExecutionId(), ClusterLevel.HF, OrderType.ASC);

    if (minAnalyzedMinute < 0) {
      // no analysis yet
      return;
    }

    long maxAnalyzedMinute = getLogRecordMinute(
        analysisContext.getAppId(), analysisContext.getStateExecutionId(), ClusterLevel.HF, OrderType.DESC);
    if (AnalysisComparisonStrategy.PREDICTIVE == analysisContext.getComparisonStrategy()) {
      if (isNotEmpty(analysisContext.getPredictiveCvConfigId())) {
        LogsCVConfiguration logsCVConfiguration =
            wingsPersistence.get(LogsCVConfiguration.class, analysisContext.getPredictiveCvConfigId());
        analysisSummary.setBaselineStartTime(TimeUnit.MINUTES.toMillis(logsCVConfiguration.getBaselineStartMinute()));
        analysisSummary.setBaselineEndTime(TimeUnit.MINUTES.toMillis(logsCVConfiguration.getBaselineEndMinute()));
        if (maxAnalyzedMinute > 0) {
          if (maxAnalyzedMinute > logsCVConfiguration.getBaselineEndMinute()) {
            maxAnalyzedMinute -=
                logsCVConfiguration.getBaselineEndMinute() - logsCVConfiguration.getBaselineStartMinute();
          } else {
            maxAnalyzedMinute = minAnalyzedMinute;
          }
        }
      }
    } else {
      // since both min and max are inclusive
      maxAnalyzedMinute += 1;
    }

    int duration = analysisContext.getTimeDuration();
    int progressedMinutes = (int) (maxAnalyzedMinute - minAnalyzedMinute);
    analysisSummary.setProgress(Math.min(100, progressedMinutes * 100 / duration));
  }

  private long getLogRecordMinute(
      String appId, String stateExecutionId, ClusterLevel clusterLevel, OrderType orderType) {
    LogDataRecord logDataRecord =
        wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
            .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
            .filter(LogDataRecordKeys.clusterLevel, clusterLevel)
            .order(orderType == OrderType.DESC ? Sort.descending(LogDataRecordKeys.logCollectionMinute)
                                               : Sort.ascending(LogDataRecordKeys.logCollectionMinute))
            .get();

    return logDataRecord == null ? -1 : logDataRecord.getLogCollectionMinute();
  }

  @Override
  public void validateConfig(
      SettingAttribute settingAttribute, StateType stateType, List<EncryptedDataDetail> encryptedDataDetails) {
    SyncTaskContext taskContext = SyncTaskContext.builder()
                                      .accountId(settingAttribute.getAccountId())
                                      .appId(GLOBAL_APP_ID)
                                      .timeout(DEFAULT_SYNC_CALL_TIMEOUT * 2)
                                      .build();
    switch (stateType) {
      case SPLUNKV2:
        delegateProxyFactory.getV2(SplunkDelegateService.class, taskContext)
            .validateConfig((SplunkConfig) settingAttribute.getValue(), encryptedDataDetails);
        return;
      case ELK:
        delegateProxyFactory.getV2(ElkDelegateService.class, taskContext)
            .validateConfig((ElkConfig) settingAttribute.getValue(), encryptedDataDetails);
        return;
      case LOGZ:
        delegateProxyFactory.getV2(LogzDelegateService.class, taskContext)
            .validateConfig((LogzConfig) settingAttribute.getValue(), encryptedDataDetails);
        return;
      case SUMO:
        delegateProxyFactory.getV2(SumoDelegateService.class, taskContext)
            .validateConfig((SumoConfig) settingAttribute.getValue(), encryptedDataDetails);
        return;
      case INSTANA:
        delegateProxyFactory.getV2(InstanaDelegateService.class, taskContext)
            .validateConfig((InstanaConfig) settingAttribute.getValue(), encryptedDataDetails);
        return;
      default:
        unhandled(stateType);
    }
  }

  @Override
  public Object getLogSample(
      String accountId, String analysisServerConfigId, String index, StateType stateType, int duration) {
    SettingAttribute settingAttribute = settingsService.get(analysisServerConfigId);
    Preconditions.checkNotNull(settingAttribute, "No {} setting with id: {} found", stateType, analysisServerConfigId);

    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
    ErrorCode errorCode = null;
    try {
      switch (stateType) {
        case ELK:
          errorCode = ErrorCode.ELK_CONFIGURATION_ERROR;
          SyncTaskContext elkTaskContext = SyncTaskContext.builder()
                                               .accountId(accountId)
                                               .appId(GLOBAL_APP_ID)
                                               .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                               .build();
          return delegateProxyFactory.getV2(ElkDelegateService.class, elkTaskContext)
              .getLogSample((ElkConfig) settingAttribute.getValue(), index, true, encryptedDataDetails);
        case LOGZ:
          errorCode = ErrorCode.LOGZ_CONFIGURATION_ERROR;
          SyncTaskContext logzTaskContext = SyncTaskContext.builder()
                                                .accountId(settingAttribute.getAccountId())
                                                .appId(GLOBAL_APP_ID)
                                                .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                                .build();
          return delegateProxyFactory.getV2(LogzDelegateService.class, logzTaskContext)
              .getLogSample((LogzConfig) settingAttribute.getValue(), encryptedDataDetails);
        case SUMO:
          errorCode = ErrorCode.SUMO_CONFIGURATION_ERROR;
          SyncTaskContext sumoTaskContext = SyncTaskContext.builder()
                                                .accountId(accountId)
                                                .appId(GLOBAL_APP_ID)
                                                .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                                .build();
          return delegateProxyFactory.getV2(SumoDelegateService.class, sumoTaskContext)
              .getLogSample((SumoConfig) settingAttribute.getValue(), index, encryptedDataDetails, duration);
        default:
          errorCode = ErrorCode.DEFAULT_ERROR_CODE;
          throw new IllegalStateException("Invalid state type: " + stateType);
      }
    } catch (Exception e) {
      throw new WingsException(errorCode, e).addParam("reason", ExceptionUtils.getMessage(e));
    }
  }

  @Override
  public Object getHostLogRecords(String accountId, String analysisServerConfigId, String index, ElkQueryType queryType,
      String query, String timeStampField, String timeStampFieldFormat, String messageField, String hostNameField,
      String hostName, StateType stateType) {
    SettingAttribute settingAttribute = settingsService.get(analysisServerConfigId);
    Preconditions.checkNotNull(settingAttribute, "No {} setting with id: {} found", stateType, analysisServerConfigId);

    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
    ErrorCode errorCode = null;
    ElkLogFetchRequest elkFetchRequest =
        ElkLogFetchRequest.builder()
            .query(query)
            .indices(index)
            .hostnameField(hostNameField)
            .messageField(messageField)
            .timestampField(timeStampField)
            .hosts(Sets.newHashSet(hostName))
            .startTime(TimeUnit.SECONDS.toMillis(OffsetDateTime.now().minusMinutes(15).toEpochSecond()))
            .endTime(TimeUnit.SECONDS.toMillis(OffsetDateTime.now().toEpochSecond()))
            .queryType(queryType)
            .build();
    Object searchResponse;
    try {
      switch (stateType) {
        case ELK:
          errorCode = ErrorCode.ELK_CONFIGURATION_ERROR;
          SyncTaskContext elkTaskContext = SyncTaskContext.builder()
                                               .accountId(accountId)
                                               .appId(GLOBAL_APP_ID)
                                               .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                               .build();
          searchResponse = delegateProxyFactory.getV2(ElkDelegateService.class, elkTaskContext)
                               .search((ElkConfig) settingAttribute.getValue(), encryptedDataDetails, elkFetchRequest,
                                   createApiCallLog(accountId, null), ElkDelegateServiceImpl.MAX_RECORDS);
          break;
        case LOGZ:
          errorCode = ErrorCode.LOGZ_CONFIGURATION_ERROR;
          SyncTaskContext logzTaskContext = SyncTaskContext.builder()
                                                .accountId(settingAttribute.getAccountId())
                                                .appId(GLOBAL_APP_ID)
                                                .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                                .build();
          searchResponse = delegateProxyFactory.getV2(LogzDelegateService.class, logzTaskContext)
                               .search((LogzConfig) settingAttribute.getValue(), encryptedDataDetails, elkFetchRequest,
                                   createApiCallLog(accountId, null));
          break;
        default:
          errorCode = ErrorCode.DEFAULT_ERROR_CODE;
          throw new IllegalStateException("Invalid state type: " + stateType);
      }
    } catch (Exception e) {
      throw new WingsException(errorCode, e).addParam("reason", ExceptionUtils.getMessage(e));
    }

    try {
      parseElkResponse(searchResponse, query, timeStampField, timeStampFieldFormat, hostNameField, hostName,
          messageField, 0, false, -1, -1);
    } catch (Exception e) {
      throw new WingsException("Data fetch successful but date parsing failed.", e);
    }
    return searchResponse;
  }

  @Override
  public List<LogMLClusterSummary> computeCluster(Map<String, Map<String, SplunkAnalysisCluster>> cluster,
      Map<String, LogMLScore> clusterScores, CLUSTER_TYPE cluster_type) {
    if (cluster == null) {
      return Collections.emptyList();
    }
    List<LogMLClusterSummary> analysisSummaries = new ArrayList<>();
    for (Entry<String, Map<String, SplunkAnalysisCluster>> labelEntry : cluster.entrySet()) {
      LogMLClusterSummary clusterSummary = new LogMLClusterSummary();
      clusterSummary.setHostSummary(new HashMap<>());
      for (Entry<String, SplunkAnalysisCluster> hostEntry : labelEntry.getValue().entrySet()) {
        LogMLHostSummary hostSummary = new LogMLHostSummary();
        SplunkAnalysisCluster analysisCluster = hostEntry.getValue();
        hostSummary.setXCordinate(sprinkalizedCordinate(analysisCluster.getX()));
        hostSummary.setYCordinate(sprinkalizedCordinate(analysisCluster.getY()));
        hostSummary.setUnexpectedFreq(analysisCluster.isUnexpected_freq());
        hostSummary.setCount(computeCountFromFrequencies(analysisCluster));
        hostSummary.setFrequencies(getFrequencies(analysisCluster));
        hostSummary.setFrequencyMap(getFrequencyMap(analysisCluster));
        clusterSummary.setLogText(analysisCluster.getText());
        clusterSummary.setPriority(analysisCluster.getPriority());
        clusterSummary.setTags(analysisCluster.getTags());
        clusterSummary.setClusterLabel(analysisCluster.getCluster_label());
        clusterSummary.getHostSummary().put(Misc.replaceUnicodeWithDot(hostEntry.getKey()), hostSummary);

        clusterSummary.setLogMLFeedbackId(analysisCluster.getFeedback_id());

        double score;
        if (clusterScores != null && clusterScores.containsKey(labelEntry.getKey())) {
          switch (cluster_type) {
            case CONTROL:
              noop();
              break;
            case TEST:
              score = clusterScores.get(labelEntry.getKey()).getFreq_score() * 100;
              clusterSummary.setScore(score);
              clusterSummary.setRiskLevel(RiskLevel.HIGH);
              break;
            case UNKNOWN:
              score = clusterScores.get(labelEntry.getKey()).getTest_score() * 100;
              clusterSummary.setScore(score);
              clusterSummary.setRiskLevel(score > HIGH_RISK_THRESHOLD ? RiskLevel.HIGH
                      : score > MEDIUM_RISK_THRESHOLD                 ? RiskLevel.MEDIUM
                                                                      : RiskLevel.LOW);
              break;
            default:
              unhandled(cluster_type);
          }
        }
      }
      analysisSummaries.add(clusterSummary);
    }

    return analysisSummaries;
  }

  private Map<Integer, Integer> getFrequencyMap(SplunkAnalysisCluster analysisCluster) {
    Map<Integer, Integer> frequencyMap = new HashMap<>();
    if (isEmpty(analysisCluster.getMessage_frequencies())) {
      return frequencyMap;
    }
    int count;
    for (MessageFrequency frequency : analysisCluster.getMessage_frequencies()) {
      count = frequency.getCount();
      frequencyMap.put(count, frequencyMap.getOrDefault(count, 0) + 1);
    }
    return frequencyMap;
  }

  @Override
  public void updateClustersFrequencyMapV2(List<LogMLClusterSummary> clusterList) {
    clusterList.forEach(logMLClusterSummary -> {
      logMLClusterSummary.getHostSummary().forEach((host, logMlHostSummary) -> {
        List<Integer> frequency = logMlHostSummary.getFrequencies();
        logMlHostSummary.setFrequencyMap(getFrequencyMapV2(frequency));
      });
    });
  }

  private Map<Integer, Integer> getFrequencyMapV2(List<Integer> frequencies) {
    Map<Integer, Integer> frequencyMap = new HashMap<>();
    if (isEmpty(frequencies)) {
      return frequencyMap;
    }
    AtomicInteger counter = new AtomicInteger(0);
    frequencies.forEach(frequency -> frequencyMap.put(counter.incrementAndGet(), frequency));
    return frequencyMap;
  }

  private int computeCountFromFrequencies(SplunkAnalysisCluster analysisCluster) {
    int count = 0;
    if (isEmpty(analysisCluster.getMessage_frequencies())) {
      return count;
    }
    for (MessageFrequency frequency : analysisCluster.getMessage_frequencies()) {
      count += frequency.getCount();
    }

    return count;
  }

  private List<Integer> getFrequencies(SplunkAnalysisCluster analysisCluster) {
    List<Integer> counts = new ArrayList<>();
    if (isEmpty(analysisCluster.getMessage_frequencies())) {
      return counts;
    }
    for (MessageFrequency frequency : analysisCluster.getMessage_frequencies()) {
      counts.add(frequency.getCount());
    }

    return counts;
  }

  @Override
  public int getUnexpectedFrequency(Map<String, Map<String, SplunkAnalysisCluster>> testClusters) {
    int unexpectedFrequency = 0;
    if (isEmpty(testClusters)) {
      return unexpectedFrequency;
    }
    for (Entry<String, Map<String, SplunkAnalysisCluster>> labelEntry : testClusters.entrySet()) {
      for (Entry<String, SplunkAnalysisCluster> hostEntry : labelEntry.getValue().entrySet()) {
        SplunkAnalysisCluster analysisCluster = hostEntry.getValue();
        if (analysisCluster.isUnexpected_freq()) {
          unexpectedFrequency++;
          break;
        }
      }
    }

    return unexpectedFrequency;
  }

  private double sprinkalizedCordinate(double coordinate) {
    int sprinkleRatio = random.nextInt() % 8;
    double adjustmentBase = coordinate - Math.floor(coordinate);
    return coordinate + (adjustmentBase * sprinkleRatio) / 100;
  }

  @Override
  public void createAndSaveSummary(
      StateType stateType, String appId, String stateExecutionId, String query, String message, String accountId) {
    LogMLAnalysisRecord analysisRecord = LogMLAnalysisRecord.builder()
                                             .logCollectionMinute(-1)
                                             .stateType(stateType)
                                             .accountId(accountId)
                                             .appId(appId)
                                             .stateExecutionId(stateExecutionId)
                                             .query(query)
                                             .analysisSummaryMessage(message)
                                             .control_events(Collections.emptyMap())
                                             .test_events(Collections.emptyMap())
                                             .build();
    analysisRecord.setAnalysisStatus(LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);
    wingsPersistence.saveIgnoringDuplicateKeys(Lists.newArrayList(analysisRecord));
  }

  @Override
  public boolean isStateValid(String appId, String stateExecutionID) {
    StateExecutionInstance stateExecutionInstance =
        workflowExecutionService.getStateExecutionData(appId, stateExecutionID);
    return stateExecutionInstance != null && !ExecutionStatus.isFinalStatus(stateExecutionInstance.getStatus());
  }

  @Override
  public Map<String, Map<String, InstanceDetails>> getLastExecutionNodes(String appId, String workflowId) {
    WorkflowExecution workflowExecution = wingsPersistence.createQuery(WorkflowExecution.class)
                                              .filter(WorkflowExecutionKeys.appId, appId)
                                              .filter(WorkflowExecutionKeys.workflowId, workflowId)
                                              .filter(WorkflowExecutionKeys.status, SUCCESS)
                                              .order(Sort.descending(WorkflowExecutionKeys.createdAt))
                                              .get();

    if (workflowExecution == null) {
      throw new WingsException(ErrorCode.APM_CONFIGURATION_ERROR, USER)
          .addParam("reason", "No successful execution exists for the workflow.");
    }

    List<InstanceDetails> instanceDetails =
        sweepingOutputService.findInstanceDetailsForWorkflowExecution(appId, workflowExecution.getUuid());

    return instanceDetails.stream().collect(Collectors.toMap(
        InstanceDetails::getHostName, instanceDetail -> Collections.singletonMap("instanceDetails", instanceDetail)));
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(
        wingsPersistence.createQuery(LearningEngineExperimentalAnalysisTask.class).filter(ACCOUNT_ID_KEY2, accountId));
  }

  public enum CLUSTER_TYPE { CONTROL, TEST, UNKNOWN, IGNORE }

  public enum LogMLFeedbackType {
    IGNORE_SERVICE,
    IGNORE_WORKFLOW,
    IGNORE_WORKFLOW_EXECUTION,
    IGNORE_ALWAYS,
    DISMISS,
    PRIORITIZE,
    THUMBS_UP,
    THUMBS_DOWN,
    UNDO_IGNORE
  }
}
