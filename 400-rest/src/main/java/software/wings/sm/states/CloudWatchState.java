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

import static software.wings.delegatetasks.cv.CVConstants.CONTROL_HOST_NAME;
import static software.wings.delegatetasks.cv.CVConstants.TEST_HOST_NAME;
import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;
import static software.wings.service.impl.newrelic.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;

import software.wings.api.AwsLambdaContextElement;
import software.wings.api.ContainerServiceElement;
import software.wings.api.DeploymentType;
import software.wings.beans.AwsConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.metrics.MetricType;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.DataCollectionCallback;
import software.wings.service.impl.cloudwatch.AwsNameSpace;
import software.wings.service.impl.cloudwatch.CloudWatchDataCollectionInfo;
import software.wings.service.impl.cloudwatch.CloudWatchMetric;
import software.wings.service.intfc.AwsHelperResourceService;
import software.wings.service.intfc.CloudWatchService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.hazelcast.internal.util.Preconditions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * Created by anubhaw on 12/7/16.
 */
@Slf4j
@FieldNameConstants(innerTypeName = "CloudWatchStateKeys")
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class CloudWatchState extends AbstractMetricAnalysisState {
  @Inject private transient AwsHelperService awsHelperService;
  @Inject private transient CloudWatchService cloudWatchService;
  @Inject private transient AwsHelperResourceService awsHelperResourceService;

  private String analysisServerConfigId;

  private String region = AWS_DEFAULT_REGION;

  private Map<String, List<CloudWatchMetric>> loadBalancerMetrics;

  private List<CloudWatchMetric> ec2Metrics;

  private Map<String, List<CloudWatchMetric>> ecsMetrics;

  @SchemaIgnore private boolean shouldDoLambdaVerification;

  @SchemaIgnore private boolean shouldDoECSClusterVerification;

  /**
   * Instantiates a new state.
   *
   * @param name the name
   */
  public CloudWatchState(String name) {
    super(name, StateType.CLOUD_WATCH);
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> results = new HashMap<>();
    // any new cloudwatch configuration should go with this check.
    if (!shouldDoLambdaVerification && !shouldDoECSClusterVerification && isEmpty(ecsMetrics) && isEmpty(ec2Metrics)
        && isEmpty(loadBalancerMetrics)) {
      results.put("No metrics provided", "No metrics provided for Verification");
    }
    return results;
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
  @Override
  protected boolean isEmptyTestNodesAllowed() {
    return shouldDoLambdaVerification;
  }

  @Override
  protected String triggerAnalysisDataCollection(ExecutionContext context, AnalysisContext analysisContext,
      VerificationStateAnalysisExecutionData executionData, Map<String, String> hosts) {
    Map<String, String> lambdaFunctions = new HashMap<>();
    if (shouldDoLambdaVerification && getDeploymentType(context) == DeploymentType.AWS_LAMBDA) {
      AwsLambdaContextElement elements = context.getContextElement(ContextElementType.PARAM);
      if (isNotEmpty(elements.getFunctionArns())) {
        elements.getFunctionArns().forEach(
            contextElement -> lambdaFunctions.put(contextElement.getFunctionName(), contextElement.getFunctionArn()));
      }
    }

    String envId = getEnvId(context);
    String resolvedAnalysisServerConfigId =
        getResolvedConnectorId(context, CloudWatchStateKeys.analysisServerConfigId, analysisServerConfigId);
    SettingAttribute settingAttribute = settingsService.get(resolvedAnalysisServerConfigId);

    Preconditions.checkNotNull(
        settingAttribute, "No Aws setting with id: " + resolvedAnalysisServerConfigId + " found");

    final AwsConfig awsConfig = (AwsConfig) settingAttribute.getValue();
    final long dataCollectionStartTimeStamp = dataCollectionStartTimestampMillis();
    Map<AwsNameSpace, List<CloudWatchMetric>> cloudWatchMetrics = cloudWatchService.getCloudWatchMetrics();

    metricAnalysisService.saveMetricTemplates(context.getAppId(), StateType.CLOUD_WATCH,
        context.getStateExecutionInstanceId(), null, fetchMetricTemplates(cloudWatchMetrics));

    if (shouldDoECSClusterVerification && getDeploymentType(context) == DeploymentType.ECS) {
      // If shouldDoECSClusterVerification but no ecs metrics map is provided. This is to handle backword compatibility
      // in-case no ecsMetrics provided than fetch cluster details from the context and use default ecs metrics for
      // verification.
      if (isEmpty(ecsMetrics)) {
        ContainerServiceElement containerServiceElement =
            context.getContextElement(ContextElementType.CONTAINER_SERVICE);
        String clusterName = containerServiceElement.getClusterName();
        ecsMetrics = createECSMetrics(clusterName, cloudWatchMetrics);
      }
    }

    if (isNotEmpty(loadBalancerMetrics)) {
      loadBalancerMetrics.forEach((s, metrics) -> cloudWatchService.setStatisticsAndUnit(AwsNameSpace.ELB, metrics));
    }
    if (isNotEmpty(ecsMetrics)) {
      ecsMetrics.forEach((s, metrics) -> cloudWatchService.setStatisticsAndUnit(AwsNameSpace.ECS, metrics));
    }
    final Map<String, List<CloudWatchMetric>> lambdaMetrics =
        createLambdaMetrics(lambdaFunctions.keySet(), cloudWatchMetrics);
    if (isNotEmpty(lambdaMetrics)) {
      lambdaMetrics.forEach((s, metrics) -> cloudWatchService.setStatisticsAndUnit(AwsNameSpace.LAMBDA, metrics));
    }

    if (isNotEmpty(ec2Metrics)) {
      cloudWatchService.setStatisticsAndUnit(AwsNameSpace.EC2, ec2Metrics);
    }
    final CloudWatchDataCollectionInfo dataCollectionInfo =
        CloudWatchDataCollectionInfo.builder()
            .awsConfig(awsConfig)
            .applicationId(context.getAppId())
            .stateExecutionId(context.getStateExecutionInstanceId())
            .workflowId(getWorkflowId(context))
            .workflowExecutionId(context.getWorkflowExecutionId())
            .serviceId(getPhaseServiceId(context))
            .analysisComparisonStrategy(getComparisonStrategy())
            .startTime(dataCollectionStartTimeStamp)
            .collectionTime(Integer.parseInt(getTimeDuration(context)))
            .dataCollectionMinute(0)
            .encryptedDataDetails(
                secretManager.getEncryptionDetails(awsConfig, context.getAppId(), context.getWorkflowExecutionId()))
            .hosts(hosts)
            .region(getRegion(context))
            .loadBalancerMetrics(loadBalancerMetrics)
            .ec2Metrics(ec2Metrics)
            .lambdaFunctionNames(lambdaMetrics)
            .metricsByECSClusterName(ecsMetrics)
            .build();

    analysisContext.getTestNodes().put(TEST_HOST_NAME, DEFAULT_GROUP_NAME);
    if (getComparisonStrategy() == AnalysisComparisonStrategy.COMPARE_WITH_CURRENT) {
      for (int i = 1; i <= CANARY_DAYS_TO_COLLECT; ++i) {
        analysisContext.getControlNodes().put(CONTROL_HOST_NAME + "-" + i, DEFAULT_GROUP_NAME);
      }
    }

    String waitId = generateUuid();
    String infrastructureMappingId = context.fetchInfraMappingId();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(appService.get(context.getAppId()).getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, context.getAppId())
            .waitId(waitId)
            .tags(isNotEmpty(dataCollectionInfo.getAwsConfig().getTag())
                    ? singletonList(dataCollectionInfo.getAwsConfig().getTag())
                    : null)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.CLOUD_WATCH_COLLECT_METRIC_DATA.name())
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

  private Map<String, List<CloudWatchMetric>> createECSMetrics(
      String clusterName, Map<AwsNameSpace, List<CloudWatchMetric>> cloudWatchMetrics) {
    if (isEmpty(clusterName)) {
      return null;
    }
    Map<String, List<CloudWatchMetric>> ecsMetrics = new HashMap<>();
    ecsMetrics.put(clusterName, cloudWatchMetrics.get(AwsNameSpace.ECS));
    return ecsMetrics;
  }

  public static Map<String, TimeSeriesMetricDefinition> fetchMetricTemplates(
      Map<AwsNameSpace, List<CloudWatchMetric>> timeSeriesToCollect) {
    Map<String, TimeSeriesMetricDefinition> rv = new HashMap<>();
    for (Entry<AwsNameSpace, List<CloudWatchMetric>> entry : timeSeriesToCollect.entrySet()) {
      for (CloudWatchMetric timeSeries : entry.getValue()) {
        if (rv.containsKey(timeSeries.getMetricName())) {
          TimeSeriesMetricDefinition timeSeriesMetricDefinition = rv.get(timeSeries.getMetricName());
          // update the tags as for same metric name all the other values will remain same. This tag is used by LE.
          timeSeriesMetricDefinition.getTags().add(entry.getKey().name());
        } else {
          rv.put(timeSeries.getMetricName(),
              TimeSeriesMetricDefinition.builder()
                  .metricName(timeSeries.getMetricName())
                  .metricType(MetricType.valueOf(timeSeries.getMetricType()))
                  .tags(Sets.newHashSet(entry.getKey().name()))
                  .build());
        }
      }
    }
    return rv;
  }

  public static Map<String, List<CloudWatchMetric>> createLambdaMetrics(
      Set<String> functionNames, Map<AwsNameSpace, List<CloudWatchMetric>> cloudWatchMetrics) {
    if (isEmpty(functionNames)) {
      return null;
    }
    Map<String, List<CloudWatchMetric>> lambdaMetrics = new HashMap<>();
    functionNames.forEach(function -> { lambdaMetrics.put(function, cloudWatchMetrics.get(AwsNameSpace.LAMBDA)); });
    return lambdaMetrics;
  }

  @Override
  @Attributes(required = false, title = "Expression for Host/Container name")
  public String getHostnameTemplate() {
    if (isEmpty(hostnameTemplate)) {
      return "${host.ec2Instance.instanceId}";
    }
    return hostnameTemplate;
  }

  @Override
  public void setHostnameTemplate(String hostnameTemplate) {
    this.hostnameTemplate = hostnameTemplate;
  }

  public String getRegion() {
    if (isEmpty(region)) {
      return AWS_DEFAULT_REGION;
    }
    return region;
  }

  private String getRegion(ExecutionContext context) {
    String resolvedRegion = getResolvedFieldValue(context, CloudWatchStateKeys.region, getRegion());
    List<NameValuePair> regions = awsHelperResourceService.getAwsRegions();
    for (NameValuePair nameValuePair : regions) {
      if (nameValuePair.getName().equals(resolvedRegion)) {
        resolvedRegion = nameValuePair.getValue();
        break;
      }
    }
    return resolvedRegion;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public List<CloudWatchMetric> fetchEc2Metrics() {
    return ec2Metrics;
  }

  public void setEc2Metrics(List<CloudWatchMetric> ec2Metrics) {
    this.ec2Metrics = ec2Metrics;
  }

  public Map<String, List<CloudWatchMetric>> fetchLoadBalancerMetrics() {
    return loadBalancerMetrics;
  }

  public void setLoadBalancerMetrics(Map<String, List<CloudWatchMetric>> loadBalancerMetrics) {
    this.loadBalancerMetrics = loadBalancerMetrics;
  }

  public Map<String, List<CloudWatchMetric>> fetchEcsMetrics() {
    return ecsMetrics;
  }

  public void setEcsMetrics(Map<String, List<CloudWatchMetric>> ecsMetrics) {
    this.ecsMetrics = ecsMetrics;
  }

  public boolean isShouldDoLambdaVerification() {
    return shouldDoLambdaVerification;
  }

  public void setShouldDoLambdaVerification(boolean shouldDoLambdaVerification) {
    this.shouldDoLambdaVerification = shouldDoLambdaVerification;
  }

  public void setShouldDoECSClusterVerification(boolean shouldDoECSClusterVerification) {
    this.shouldDoECSClusterVerification = shouldDoECSClusterVerification;
  }

  public boolean isShouldDoECSClusterVerification() {
    return shouldDoECSClusterVerification;
  }
}
