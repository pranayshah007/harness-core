/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.Misc.isLong;
import static io.harness.waiter.OrchestrationNotifyEventListener.ORCHESTRATION;

import static software.wings.common.TemplateExpressionProcessor.checkFieldTemplatized;
import static software.wings.service.impl.analysis.TimeSeriesMlAnalysisType.PREDICTIVE;
import static software.wings.service.impl.newrelic.NewRelicMetricValueDefinition.APP_DYNAMICS_24X7_VALUES_TO_ANALYZE;
import static software.wings.service.impl.newrelic.NewRelicMetricValueDefinition.APP_DYNAMICS_VALUES_TO_ANALYZE;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.WingsException;

import software.wings.api.DeploymentType;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisComparisonStrategyProvider;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.AnalysisTolerance;
import software.wings.service.impl.analysis.AnalysisToleranceProvider;
import software.wings.service.impl.analysis.DataCollectionCallback;
import software.wings.service.impl.analysis.TimeSeriesMetricGroup.TimeSeriesMlAnalysisGroupInfo;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.appdynamics.AppdynamicsDataCollectionInfo;
import software.wings.service.impl.appdynamics.AppdynamicsTier;
import software.wings.service.impl.appdynamics.AppdynamicsTimeSeries;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.intfc.appdynamics.AppdynamicsService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;
import software.wings.stencils.DefaultValue;
import software.wings.stencils.EnumData;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

/**
 * Created by anubhaw on 8/4/16.
 */
@Slf4j
@FieldNameConstants(innerTypeName = "AppDynamicsStateKeys")
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class AppDynamicsState extends AbstractMetricAnalysisState {
  @Transient @Inject protected AppdynamicsService appdynamicsService;

  @Attributes(required = true, title = "AppDynamics Server") private String analysisServerConfigId;

  @Attributes(required = true, title = "Application Name") private String applicationId;

  @Attributes(required = true, title = "Tier Name") private String tierId;

  private List<AppdynamicsTier> dependentTiersToAnalyze;

  /**
   * Create a new Http State with given name.
   *
   * @param name name of the state.
   */
  public AppDynamicsState(String name) {
    super(name, StateType.APP_DYNAMICS);
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
  @Attributes(title = "Analysis Time duration (in minutes)", description = "Default 15 minutes")
  @DefaultValue("15")
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

  /**
   * Gets application identifier.
   *
   * @return the application identifier
   */
  public String getApplicationId() {
    return applicationId;
  }

  /**
   * Sets application identifier.
   *
   * @param applicationId the application identifier
   */
  public void setApplicationId(String applicationId) {
    this.applicationId = applicationId;
  }

  public String getTierId() {
    return tierId;
  }

  public void setTierId(String tierId) {
    this.tierId = tierId;
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> results = new HashMap<>();
    if (isEmpty(getTemplateExpressions())) {
      if (isEmpty(applicationId) || isEmpty(analysisServerConfigId) || isEmpty(tierId)) {
        results.put("Required Fields missing", "Connector, Application and tier should be provided");
        return results;
      }
      return results;
    }

    if (checkFieldTemplatized(AppDynamicsStateKeys.analysisServerConfigId, getTemplateExpressions())) {
      if (!checkFieldTemplatized(AppDynamicsStateKeys.applicationId, getTemplateExpressions())
          && !isExpression(AppDynamicsStateKeys.applicationId, applicationId, getTemplateExpressions())) {
        results.put("Invalid templatization for application",
            "If connector is templatized then application should be either templatized or should be an expression");
      }
    }

    if (checkFieldTemplatized(AppDynamicsStateKeys.applicationId, getTemplateExpressions())) {
      if (!checkFieldTemplatized(AppDynamicsStateKeys.tierId, getTemplateExpressions())
          && !isExpression(AppDynamicsStateKeys.tierId, tierId, getTemplateExpressions())) {
        results.put("Invalid templatization for tier",
            "If application is templatized then tier should be either templatized or should be an expression");
      }
    }

    if (isExpression(AppDynamicsStateKeys.applicationId, applicationId, getTemplateExpressions())
        && !isExpression(AppDynamicsStateKeys.tierId, tierId, getTemplateExpressions())) {
      results.put(
          "Invalid expression for tier", "If application is an expression then tier should be an expression as well");
    }

    log.info("AppDynamics State Validated");
    return results;
  }

  @VisibleForTesting
  void updateHostToGroupNameMap(String connectorId, String appDApplicationId, String tierId,
      Map<String, String> hostsToGroupName, ExecutionContext context) {
    ThirdPartyApiCallLog apiCallLog = ThirdPartyApiCallLog.createApiCallLog(
        appService.getAccountIdByAppId(context.getAppId()), context.getStateExecutionInstanceId());

    // Update host to group name map with corresponding tier name
    final AppdynamicsTier tier = appdynamicsService.getTier(connectorId, Long.parseLong(appDApplicationId), tierId,
        context.getAppId(), context.getWorkflowExecutionId(), apiCallLog);
    Map<String, TimeSeriesMlAnalysisGroupInfo> metricGroups = new HashMap<>();
    hostsToGroupName.keySet().forEach(key -> hostsToGroupName.put(key, tier.getName()));

    TimeSeriesMlAnalysisType analysisType = getComparisonStrategy() == AnalysisComparisonStrategy.PREDICTIVE
        ? PREDICTIVE
        : TimeSeriesMlAnalysisType.COMPARATIVE;
    metricGroups.put(tier.getName(),
        TimeSeriesMlAnalysisGroupInfo.builder().groupName(tier.getName()).mlAnalysisType(analysisType).build());

    metricAnalysisService.saveMetricGroups(
        context.getAppId(), StateType.APP_DYNAMICS, context.getStateExecutionInstanceId(), metricGroups);
  }

  @Override
  protected String triggerAnalysisDataCollection(ExecutionContext context, AnalysisContext analysisContext,
      VerificationStateAnalysisExecutionData executionData, Map<String, String> hosts) {
    String envId = getEnvId(context);
    metricAnalysisService.saveMetricTemplates(context.getAppId(), StateType.APP_DYNAMICS,
        context.getStateExecutionInstanceId(), null, APP_DYNAMICS_VALUES_TO_ANALYZE);

    analysisServerConfigId =
        getResolvedConnectorId(context, AppDynamicsStateKeys.analysisServerConfigId, analysisServerConfigId);
    applicationId = getResolvedFieldValue(context, AppDynamicsStateKeys.applicationId, applicationId);
    tierId = getResolvedFieldValue(context, AppDynamicsStateKeys.tierId, tierId);

    SettingAttribute settingAttribute = getSettingAttribute(analysisServerConfigId);

    AppDynamicsConfig appDynamicsConfig = (AppDynamicsConfig) settingAttribute.getValue();

    List<AppdynamicsTier> dependentTiers = new ArrayList<>();
    Map<String, TimeSeriesMlAnalysisGroupInfo> metricGroups = new HashMap<>();
    AppdynamicsTier analyzedTier = AppdynamicsTier.builder().build();
    TimeSeriesMlAnalysisType analyzedTierAnalysisType = getComparisonStrategy() == AnalysisComparisonStrategy.PREDICTIVE
        ? PREDICTIVE
        : TimeSeriesMlAnalysisType.COMPARATIVE;
    renderExpressions(context);
    Set<AppdynamicsTier> tiers = appdynamicsService.getTiers(analysisServerConfigId, Long.parseLong(applicationId),
        context.getAppId(), context.getWorkflowExecutionId(),
        ThirdPartyApiCallLog.createApiCallLog(
            appService.getAccountIdByAppId(context.getAppId()), context.getStateExecutionInstanceId()));
    tiers.stream().filter(tier -> tier.getId() == Long.parseLong(tierId)).forEach(tier -> {
      metricGroups.put(tier.getName(),
          TimeSeriesMlAnalysisGroupInfo.builder()
              .groupName(tier.getName())
              .mlAnalysisType(analyzedTierAnalysisType)
              .build());
      analyzedTier.setName(tier.getName());
      analyzedTier.setId(Long.parseLong(tierId));
    });
    Preconditions.checkState(!isEmpty(analyzedTier.getName()), "failed for " + analyzedTier);

    final long dataCollectionStartTimeStamp = dataCollectionStartTimestampMillis();
    List<DelegateTask> delegateTasks = new ArrayList<>();
    String[] waitIds = new String[dependentTiers.size() + 1];
    log.info("Creating AppDynamics Delegate Task for AppD applicationId : {} Tier Id : {}", applicationId, tierId);
    waitIds[0] = createDelegateTask(context, analyzedTierAnalysisType == PREDICTIVE ? Collections.emptyMap() : hosts,
        envId, applicationId, Long.parseLong(tierId), appDynamicsConfig, dataCollectionStartTimeStamp,
        analyzedTierAnalysisType, delegateTasks);

    for (int i = 0; i < dependentTiers.size(); i++) {
      waitIds[i + 1] = createDelegateTask(context, Collections.emptyMap(), envId, applicationId,
          dependentTiers.get(i).getId(), appDynamicsConfig, dataCollectionStartTimeStamp, PREDICTIVE, delegateTasks);
      metricGroups.put(dependentTiers.get(i).getName(),
          TimeSeriesMlAnalysisGroupInfo.builder()
              .groupName(dependentTiers.get(i).getName())
              .dependencyPath(dependentTiers.get(i).getDependencyPath())
              .mlAnalysisType(PREDICTIVE)
              .build());
    }
    waitNotifyEngine.waitForAllOn(ORCHESTRATION,
        DataCollectionCallback.builder()
            .appId(context.getAppId())
            .stateExecutionId(context.getStateExecutionInstanceId())
            .dataCollectionStartTime(dataCollectionStartTimeStamp)
            .dataCollectionEndTime(
                dataCollectionStartTimeStamp + TimeUnit.MINUTES.toMillis(Integer.parseInt(getTimeDuration(context))))
            .executionData(executionData)
            .build(),
        waitIds);
    InfrastructureMapping infrastructureMapping = getInfrastructureMapping(context);
    DeploymentType deploymentType =
        serviceResourceService.getDeploymentType(infrastructureMapping, null, infrastructureMapping.getServiceId());

    if (DeploymentType.HELM == deploymentType) {
      super.createAndSaveMetricGroups(context, hosts);
    } else {
      metricAnalysisService.saveMetricGroups(
          context.getAppId(), StateType.APP_DYNAMICS, context.getStateExecutionInstanceId(), metricGroups);
    }
    List<String> delegateTaskIds = new ArrayList<>();
    for (DelegateTask task : delegateTasks) {
      delegateTaskIds.add(delegateService.queueTaskV2(task));
    }
    return StringUtils.join(delegateTaskIds, ",");
  }

  private String getTierByName(ExecutionContext context, String tierName) {
    return appdynamicsService.getTierByName(analysisServerConfigId, applicationId, tierName, context.getAppId(),
        context.getWorkflowExecutionId(),
        ThirdPartyApiCallLog.createApiCallLog(
            appService.getAccountIdByAppId(context.getAppId()), context.getStateExecutionInstanceId()));
  }

  private void renderExpressions(ExecutionContext context) {
    if (!isLong(applicationId)) {
      String applicationName = context.renderExpression(applicationId);
      applicationId = appdynamicsService.getAppDynamicsApplicationByName(
          analysisServerConfigId, applicationName, context.getAppId(), context.getWorkflowExecutionId());
      Preconditions.checkState(isLong(applicationId),
          "Not able to resolve applicationId for application name %s. Please check your expression or application name",
          applicationName);
    }
    if (!isLong(tierId)) {
      String tierName = context.renderExpression(tierId);
      tierId = getTierByName(context, tierName);
      Preconditions.checkState(isLong(tierId),
          "Not able to resolve  tier ID for tier name %s. Please check your expression or tier name", tierName);
    }
  }

  private String createDelegateTask(ExecutionContext context, Map<String, String> hosts, String envId,
      String finalApplicationId, long finalTierId, AppDynamicsConfig appDynamicsConfig,
      long dataCollectionStartTimeStamp, TimeSeriesMlAnalysisType mlAnalysisType, List<DelegateTask> delegateTasks) {
    final AppdynamicsDataCollectionInfo dataCollectionInfo =
        AppdynamicsDataCollectionInfo.builder()
            .appDynamicsConfig(appDynamicsConfig)
            .applicationId(context.getAppId())
            .stateExecutionId(context.getStateExecutionInstanceId())
            .workflowId(getWorkflowId(context))
            .workflowExecutionId(context.getWorkflowExecutionId())
            .serviceId(getPhaseServiceId(context))
            .startTime(dataCollectionStartTimeStamp)
            .collectionTime(Integer.parseInt(getTimeDuration(context)))
            .appId(Long.parseLong(finalApplicationId))
            .tierId(finalTierId)
            .dataCollectionMinute(0)
            .encryptedDataDetails(secretManager.getEncryptionDetails(
                appDynamicsConfig, context.getAppId(), context.getWorkflowExecutionId()))
            .hosts(hosts)
            .timeSeriesMlAnalysisType(mlAnalysisType)
            .build();

    String waitId = generateUuid();
    delegateTasks.add(
        DelegateTask.builder()
            .accountId(appService.get(context.getAppId()).getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, context.getAppId())
            .waitId(waitId)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.APPDYNAMICS_COLLECT_METRIC_DATA.name())
                      .parameters(new Object[] {dataCollectionInfo})
                      .timeout(TimeUnit.MINUTES.toMillis(Integer.parseInt(getTimeDuration(context)) + 120))
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
            .build());
    return waitId;
  }

  @Override
  protected void createAndSaveMetricGroups(ExecutionContext context, Map<String, String> hostsToCollect) {
    if (!isEmpty(dependentTiersToAnalyze)) {
      InfrastructureMapping infrastructureMapping = getInfrastructureMapping(context);
      if (DeploymentType.HELM
          == serviceResourceService.getDeploymentType(
              infrastructureMapping, null, infrastructureMapping.getServiceId())) {
        throw new WingsException("can not analyze dependent tiers for helm type deployment");
      }
    }
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

  public List<AppdynamicsTier> getDependentTiersToAnalyze() {
    return dependentTiersToAnalyze;
  }

  public void setDependentTiersToAnalyze(List<AppdynamicsTier> dependentTiersToAnalyze) {
    this.dependentTiersToAnalyze = dependentTiersToAnalyze;
  }

  @Override
  public Map<String, String> parentTemplateFields(String fieldName) {
    Map<String, String> parentTemplateFields = new LinkedHashMap<>();
    if (fieldName.equals(AppDynamicsStateKeys.applicationId)) {
      if (!checkFieldTemplatized(AppDynamicsStateKeys.analysisServerConfigId, getTemplateExpressions())) {
        parentTemplateFields.put(AppDynamicsStateKeys.analysisServerConfigId, analysisServerConfigId);
      }
    } else if (fieldName.equals(AppDynamicsStateKeys.tierId)) {
      if (!checkFieldTemplatized(AppDynamicsStateKeys.analysisServerConfigId, getTemplateExpressions())) {
        parentTemplateFields.put(AppDynamicsStateKeys.analysisServerConfigId, analysisServerConfigId);
        if (!checkFieldTemplatized(AppDynamicsStateKeys.applicationId, getTemplateExpressions())) {
          parentTemplateFields.put(AppDynamicsStateKeys.applicationId, applicationId);
        }
      }
    }
    return parentTemplateFields;
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

  public static Double getNormalizedValue(String metricName, NewRelicMetricDataRecord metricDataRecord) {
    if (metricDataRecord != null) {
      Map<String, Double> values = metricDataRecord.getValues();
      if (values.containsKey(AppdynamicsTimeSeries.CALLS_PER_MINUTE.getMetricName())
          && AppdynamicsTimeSeries.getErrorMetrics().contains(metricName) && values.containsKey(metricName)) {
        double errorCount = values.get(metricName);
        double callsCount = values.get(AppdynamicsTimeSeries.CALLS_PER_MINUTE.getMetricName());

        if (callsCount != 0.0) {
          DecimalFormat twoDForm = new DecimalFormat("#.00");
          return Double.valueOf(twoDForm.format(errorCount / callsCount * 100));
        } else {
          return 0.0;
        }
      }
      return metricDataRecord.getValues().get(metricName);
    }
    return null;
  }

  public static String getMetricTypeForMetric(String metricName) {
    if (isEmpty(metricName)) {
      return null;
    }
    Map<String, TimeSeriesMetricDefinition> appDMetrics = new HashMap<>(APP_DYNAMICS_VALUES_TO_ANALYZE);
    appDMetrics.putAll(APP_DYNAMICS_24X7_VALUES_TO_ANALYZE);

    if (appDMetrics.containsKey(metricName)) {
      return appDMetrics.get(metricName).getMetricType().name();
    }
    log.error("Invalid metricName in AppDynamics {}", metricName);
    return null;
  }
}
