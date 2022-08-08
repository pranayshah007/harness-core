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

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.WingsException;

import software.wings.beans.BugsnagConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.beans.CustomLogResponseMapper;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisComparisonStrategyProvider;
import software.wings.service.impl.analysis.AnalysisTolerance;
import software.wings.service.impl.analysis.AnalysisToleranceProvider;
import software.wings.service.impl.analysis.CustomLogDataCollectionInfo;
import software.wings.service.impl.analysis.DataCollectionCallback;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;
import software.wings.stencils.DefaultValue;
import software.wings.stencils.EnumData;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;
import org.slf4j.Logger;

@Slf4j
@FieldNameConstants(innerTypeName = "BugsnagStateKeys")
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class BugsnagState extends AbstractLogAnalysisState {
  @SchemaIgnore
  @Transient
  private static final String FETCH_EVENTS_URL =
      "projects/:projectId:/events?filters[event.since][][value]=${iso_start_time}&filters[event.before][][value]=${iso_end_time}&full_reports=true&per_page=1000";
  @SchemaIgnore @Transient @Inject private EncryptionService encryptionService;

  public BugsnagState(String name) {
    super(name, StateType.BUG_SNAG.getType());
  }

  public BugsnagState(String name, String type) {
    super(name, type);
  }

  @Attributes(required = true, title = "Bugsnag Server") protected String analysisServerConfigId;

  @Attributes(required = true, title = "Bugsnag Organization") protected String orgId;

  @Attributes(required = true, title = "Bugsnag Project") protected String projectId;

  @Attributes(title = "Bugsnag Release Stage") protected String releaseStage;

  @Attributes(required = true, title = "Bugsnag Project") protected boolean browserApplication;

  @EnumData(enumDataProvider = AnalysisToleranceProvider.class)
  @Attributes(required = true, title = "Algorithm Sensitivity")
  @DefaultValue("MEDIUM")
  @Override
  public AnalysisTolerance getAnalysisTolerance() {
    if (isBlank(tolerance)) {
      return AnalysisTolerance.LOW;
    }
    return AnalysisTolerance.valueOf(tolerance);
  }

  @Override
  @SchemaIgnore
  public String getQuery() {
    return query;
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

  public String getReleaseStage() {
    return releaseStage;
  }

  public void setReleaseStage(String releaseStage) {
    this.releaseStage = releaseStage;
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

  public String getProjectId() {
    return projectId;
  }

  public boolean isBrowserApplication() {
    return browserApplication;
  }

  public String getOrgId() {
    return orgId;
  }

  public void setBrowserApplication(Boolean browserApplication) {
    this.browserApplication = browserApplication;
  }

  public void setOrgId(String orgId) {
    this.orgId = orgId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
    this.query = "";
  }

  @Override
  public void setAnalysisServerConfigId(String analysisServerConfigId) {
    this.analysisServerConfigId = analysisServerConfigId;
  }

  @Attributes(title = "Execute with previous steps")
  public boolean getExecuteWithPreviousSteps() {
    return super.isExecuteWithPreviousSteps();
  }

  @Override
  @Attributes(required = true, title = "Include nodes from previous phases")
  public boolean getIncludePreviousPhaseNodes() {
    return includePreviousPhaseNodes;
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
  protected String triggerAnalysisDataCollection(
      ExecutionContext context, VerificationStateAnalysisExecutionData executionData, Set<String> hosts) {
    String envId = getEnvId(context);

    String serverConfigId =
        getResolvedConnectorId(context, BugsnagStateKeys.analysisServerConfigId, analysisServerConfigId);
    SettingAttribute settingAttribute = getSettingAttribute(serverConfigId);

    final BugsnagConfig config = (BugsnagConfig) settingAttribute.getValue();
    final long dataCollectionStartTimeStamp = dataCollectionStartTimestampMillis();
    String accountId = appService.get(context.getAppId()).getAccountId();

    // Form the dataCollectionInfo
    CustomLogDataCollectionInfo dataCollectionInfo =
        CustomLogDataCollectionInfo.builder()
            .baseUrl(config.getUrl())
            .validationUrl(BugsnagConfig.validationUrl)
            .headers(config.headersMap())
            .options(config.optionsMap())
            .query(getRenderedQuery())
            .encryptedDataDetails(
                secretManager.getEncryptionDetails(config, context.getAppId(), context.getWorkflowExecutionId()))
            .hosts(hosts)
            .stateType(DelegateStateType.BUG_SNAG)
            .applicationId(context.getAppId())
            .stateExecutionId(context.getStateExecutionInstanceId())
            .workflowId(getWorkflowId(context))
            .workflowExecutionId(context.getWorkflowExecutionId())
            .serviceId(getPhaseServiceId(context))
            .startTime(dataCollectionStartTimeStamp)
            .startMinute(0)
            .responseDefinition(
                constructLogDefinitions(getResolvedFieldValue(context, BugsnagStateKeys.projectId, projectId),
                    getResolvedFieldValue(context, BugsnagStateKeys.releaseStage, releaseStage)))
            .shouldDoHostBasedFiltering(!isBrowserApplication())
            .collectionFrequency(1)
            .collectionTime(Integer.parseInt(getTimeDuration(context)))
            .accountId(accountId)
            .build();

    // Create the delegate task and send it over.

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
            .executionData(executionData)
            .build(),
        waitId);
    return delegateService.queueTask(delegateTask);
  }

  public static Map<String, Map<String, CustomLogResponseMapper>> constructLogDefinitions(
      String projectId, String releaseStage) {
    Map<String, Map<String, CustomLogResponseMapper>> logDefinition = new HashMap<>();
    if (isEmpty(projectId)) {
      throw new WingsException("ProjectID is empty in Bugsnag State. Unable to fetch data");
    }
    String eventsUrl = FETCH_EVENTS_URL.replace(":projectId:", projectId);
    if (isNotEmpty(releaseStage)) {
      eventsUrl += "&filters[app.release_stage][][type]=eq&filters[app.release_stage][][value]=" + releaseStage;
    }
    logDefinition.put(eventsUrl, new HashMap<>());
    Map<String, CustomLogResponseMapper> responseMappers = new HashMap<>();
    List<String> pathList = new ArrayList<>();
    pathList.add("[*].received_at");
    responseMappers.put("timestamp",
        CustomLogResponseMapper.builder().fieldName("timestamp").jsonPath(pathList).timestampFormat("").build());
    List<String> pathList2 = new ArrayList<>();
    pathList2.add("[*].context");
    pathList2.add("[*].request");
    pathList2.add("[*].metaData");
    pathList2.add("[*].exceptions[0]");
    responseMappers.put(
        "logMessage", CustomLogResponseMapper.builder().fieldName("logMessage").jsonPath(pathList2).build());
    List<String> pathList3 = new ArrayList<>();
    pathList3.add("[*].device.browserName");
    responseMappers.put("host", CustomLogResponseMapper.builder().fieldName("host").jsonPath(pathList3).build());
    logDefinition.put(eventsUrl, responseMappers);
    return logDefinition;
  }
}
