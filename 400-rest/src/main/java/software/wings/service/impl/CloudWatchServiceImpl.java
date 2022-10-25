/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.common.VerificationConstants.DEFAULT_GROUP_NAME;
import static software.wings.common.VerificationConstants.STATIC_CLOUD_WATCH_METRIC_URL;
import static software.wings.service.impl.ThirdPartyApiCallLog.createApiCallLog;

import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.YamlUtils;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.apm.MLServiceUtils;
import software.wings.service.impl.cloudwatch.AwsNameSpace;
import software.wings.service.impl.cloudwatch.CloudWatchMetric;
import software.wings.service.impl.cloudwatch.CloudWatchSetupTestNodeData;
import software.wings.service.intfc.CloudWatchService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.cloudwatch.CloudWatchDelegateService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.verification.cloudwatch.CloudWatchCVServiceConfiguration;

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.DimensionFilter;
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest;
import com.amazonaws.services.cloudwatch.model.Metric;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by anubhaw on 12/14/16.
 */
@Singleton
@Slf4j
public class CloudWatchServiceImpl implements CloudWatchService {
  private static final URL CLOUDWATCH_METRICS_URL = CloudWatchService.class.getResource(STATIC_CLOUD_WATCH_METRIC_URL);
  private static final String CLOUDWATCH_YAML;
  static {
    String tmpCloudwatchYaml = "";
    try {
      tmpCloudwatchYaml = Resources.toString(CLOUDWATCH_METRICS_URL, Charsets.UTF_8);
    } catch (IOException ex) {
      log.info("Exception while reading cloudwatch yaml", ex);
    }
    CLOUDWATCH_YAML = tmpCloudwatchYaml;
  }
  @Inject private SettingsService settingsService;
  @Inject private AwsHelperService awsHelperService;
  @Inject private SecretManager secretManager;
  @Inject private AwsInfrastructureProvider awsInfrastructureProvider;
  @Inject private MLServiceUtils mlServiceUtils;
  @Inject private DelegateProxyFactory delegateProxyFactory;

  private final Map<AwsNameSpace, List<CloudWatchMetric>> cloudWatchMetrics;

  @Override
  public Map<AwsNameSpace, List<CloudWatchMetric>> getCloudWatchMetrics() {
    return cloudWatchMetrics;
  }

  @Inject
  public CloudWatchServiceImpl() {
    cloudWatchMetrics = fetchMetrics();
  }

  @Override
  public List<String> listNamespaces(String settingId, String region) {
    AwsConfig awsConfig = getAwsConfig(settingId);
    return awsHelperService
        .getCloudWatchMetrics(awsConfig, secretManager.getEncryptionDetails(awsConfig, null, null), region)
        .stream()
        .map(Metric::getNamespace)
        .distinct()
        .collect(Collectors.toList());
  }

  @Override
  public List<String> listMetrics(String settingId, String region, String namespace) {
    DimensionFilter dimensionFilter = new DimensionFilter();
    dimensionFilter.withName(UUID.randomUUID().toString()).withValue(UUID.randomUUID().toString());
    ListMetricsRequest listMetricsRequest = new ListMetricsRequest();
    listMetricsRequest.withNamespace(namespace);
    AwsConfig awsConfig = getAwsConfig(settingId);
    List<Metric> metrics = awsHelperService.getCloudWatchMetrics(
        awsConfig, secretManager.getEncryptionDetails(awsConfig, null, null), region, listMetricsRequest);
    return metrics.stream().map(Metric::getMetricName).distinct().collect(Collectors.toList());
  }

  @Override
  public List<String> listDimensions(String settingId, String region, String namespace, String metricName) {
    ListMetricsRequest listMetricsRequest = new ListMetricsRequest();
    listMetricsRequest.withNamespace(namespace).withMetricName(metricName);
    AwsConfig awsConfig = getAwsConfig(settingId);
    List<Metric> metrics = awsHelperService.getCloudWatchMetrics(
        awsConfig, secretManager.getEncryptionDetails(awsConfig, null, null), region, listMetricsRequest);
    return metrics.stream()
        .flatMap(metric -> metric.getDimensions().stream().map(Dimension::getName))
        .distinct()
        .collect(Collectors.toList());
  }

  @Override
  public Set<String> getLoadBalancerNames(String settingId, String region) {
    final Set<String> loadBalancers = new HashSet<>();
    SettingAttribute settingAttribute = settingsService.get(settingId);
    if (settingAttribute == null || !(settingAttribute.getValue() instanceof AwsConfig)) {
      throw new WingsException("AWS account setting not found " + settingId);
    }
    loadBalancers.addAll(awsInfrastructureProvider.listClassicLoadBalancers(settingAttribute, region, ""));
    loadBalancers.addAll(awsInfrastructureProvider.listElasticBalancers(settingAttribute, region, ""));
    return loadBalancers;
  }

  @Override
  public List<String> getLambdaFunctionsNames(String settingId, String region) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    if (settingAttribute == null || !(settingAttribute.getValue() instanceof AwsConfig)) {
      throw new WingsException("AWS account setting not found " + settingId);
    }
    return awsInfrastructureProvider.listLambdaFunctions(settingAttribute, region);
  }

  @Override
  public Map<String, List<CloudWatchMetric>> createLambdaFunctionNames(List<String> lambdaFunctions) {
    if (isEmpty(lambdaFunctions)) {
      return null;
    }
    Map<String, List<CloudWatchMetric>> lambdaMetrics = new HashMap<>();
    lambdaFunctions.forEach(function -> { lambdaMetrics.put(function, cloudWatchMetrics.get(AwsNameSpace.LAMBDA)); });
    return lambdaMetrics;
  }

  @Override
  public Map<String, String> getGroupNameByHost(List<String> ec2InstanceNames) {
    Map<String, String> groupNameByHost = new HashMap<>();
    if (isEmpty(ec2InstanceNames)) {
      return groupNameByHost;
    }
    ec2InstanceNames.forEach(ec2InstanceName -> { groupNameByHost.put(ec2InstanceName, DEFAULT_GROUP_NAME); });
    return groupNameByHost;
  }

  @Override
  public Map<String, String> getEC2Instances(String settingId, String region) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    if (settingAttribute == null || !(settingAttribute.getValue() instanceof AwsConfig)) {
      throw new WingsException("AWS account setting not found " + settingId);
    }
    List<Instance> instances = awsInfrastructureProvider.listEc2Instances(settingAttribute, region);
    return instances.stream()
        .filter(instance -> !instance.getPublicDnsName().equals(""))
        .collect(Collectors.toMap(Instance::getPrivateDnsName, Instance::getInstanceId));
  }

  @Override
  public List<String> getECSClusterNames(String settingId, String region) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    if (settingAttribute == null || !(settingAttribute.getValue() instanceof AwsConfig)) {
      throw new WingsException("AWS account setting not found " + settingId);
    }
    return awsInfrastructureProvider.listECSClusterNames(settingAttribute, region);
  }

  @Override
  public List<Service> getECSClusterServices(String settingId, String region, String clusterName) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    if (settingAttribute == null || !(settingAttribute.getValue() instanceof AwsConfig)) {
      throw new WingsException("AWS account setting not found " + settingId);
    }
    return awsInfrastructureProvider.listECSClusterServiceNames(settingAttribute, region, clusterName);
  }

  @Override
  public VerificationNodeDataSetupResponse getMetricsWithDataForNode(CloudWatchSetupTestNodeData setupTestNodeData) {
    try {
      final SettingAttribute settingAttribute = settingsService.get(setupTestNodeData.getSettingId());
      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(settingAttribute.getAccountId())
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT * 3)
                                            .build();
      String hostName = null;
      if (!setupTestNodeData.isServiceLevel()) {
        hostName = mlServiceUtils.getHostName(setupTestNodeData);
      }
      return delegateProxyFactory.get(CloudWatchDelegateService.class, syncTaskContext)
          .getMetricsWithDataForNode((AwsConfig) settingAttribute.getValue(), encryptionDetails, setupTestNodeData,
              createApiCallLog(settingAttribute.getAccountId(), setupTestNodeData.getGuid()), hostName);
    } catch (Exception e) {
      log.info("error getting metric data for node", e);
      throw new WingsException(ErrorCode.CLOUDWATCH_ERROR)
          .addParam("message", "Error in getting metric data for the node. " + e.getMessage());
    }
  }

  private AwsConfig getAwsConfig(String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    if (settingAttribute == null || !(settingAttribute.getValue() instanceof AwsConfig)) {
      throw new WingsException("AWS account setting not found");
    }
    return (AwsConfig) settingAttribute.getValue();
  }

  public static Map<AwsNameSpace, List<CloudWatchMetric>> fetchMetrics() {
    Map<AwsNameSpace, List<CloudWatchMetric>> cloudWatchMetrics;
    YamlUtils yamlUtils = new YamlUtils();
    try {
      cloudWatchMetrics =
          yamlUtils.read(CLOUDWATCH_YAML, new TypeReference<Map<AwsNameSpace, List<CloudWatchMetric>>>() {});
      cloudWatchMetrics.forEach((awsNameSpace, metrics) -> metrics.forEach(cloudWatchMetric -> {
        Preconditions.checkState(isNotEmpty(cloudWatchMetric.getStatistics()),
            awsNameSpace + ":" + cloudWatchMetric.getMetricName() + " does not have statistics field defined");
        Preconditions.checkState(cloudWatchMetric.getUnit() != null,
            awsNameSpace + ":" + cloudWatchMetric.getMetricName() + " does not have unit field defined");
      }));
    } catch (Exception e) {
      throw new WingsException(e);
    }
    return cloudWatchMetrics;
  }

  private static List<CloudWatchMetric> getMetricsForTemplate(
      List<CloudWatchMetric> userSelectedMetrics, List<CloudWatchMetric> metricList) {
    Map<String, CloudWatchMetric> elbMetricMap = new HashMap<>();
    List<CloudWatchMetric> metricsForTemplate = new ArrayList<>();
    metricList.forEach(metric -> elbMetricMap.put(metric.getMetricName(), metric));

    userSelectedMetrics.forEach(metric -> metricsForTemplate.add(elbMetricMap.get(metric.getMetricName())));
    return metricsForTemplate;
  }

  public static Map<AwsNameSpace, List<CloudWatchMetric>> fetchMetrics(
      CloudWatchCVServiceConfiguration cloudwatchConfig) {
    Map<AwsNameSpace, List<CloudWatchMetric>> cloudWatchMetrics, metricsTemplate = new HashMap<>();
    cloudWatchMetrics = fetchMetrics();

    if (isNotEmpty(cloudwatchConfig.getLoadBalancerMetrics())) {
      List<CloudWatchMetric> elbMetrics = cloudWatchMetrics.get(AwsNameSpace.ELB),
                             metricsForTemplate = new ArrayList<>();

      cloudwatchConfig.getLoadBalancerMetrics().forEach(
          (lb, metrics) -> { metricsForTemplate.addAll(getMetricsForTemplate(metrics, elbMetrics)); });

      metricsTemplate.put(AwsNameSpace.ELB, metricsForTemplate);
    }

    if (isNotEmpty(cloudwatchConfig.getEcsMetrics())) {
      List<CloudWatchMetric> ecsMetrics = cloudWatchMetrics.get(AwsNameSpace.ECS),
                             metricsForTemplate = new ArrayList<>();

      cloudwatchConfig.getEcsMetrics().forEach(
          (lb, metrics) -> { metricsForTemplate.addAll(getMetricsForTemplate(metrics, ecsMetrics)); });

      metricsTemplate.put(AwsNameSpace.ECS, metricsForTemplate);
    }

    if (isNotEmpty(cloudwatchConfig.getEc2Metrics())) {
      List<CloudWatchMetric> ec2Metrics = cloudWatchMetrics.get(AwsNameSpace.EC2),
                             metricsForTemplate = new ArrayList<>();
      metricsForTemplate.addAll(getMetricsForTemplate(cloudwatchConfig.getEc2Metrics(), ec2Metrics));
      metricsTemplate.put(AwsNameSpace.EC2, metricsForTemplate);
    }

    if (isNotEmpty(cloudwatchConfig.getLambdaFunctionsMetrics())) {
      metricsTemplate.put(AwsNameSpace.LAMBDA, cloudWatchMetrics.get(AwsNameSpace.LAMBDA));
    }

    return metricsTemplate;
  }

  @Override
  public void setStatisticsAndUnit(AwsNameSpace awsNameSpace, List<CloudWatchMetric> metrics) {
    if (isNotEmpty(metrics)) {
      metrics.stream()
          .filter(metric -> isEmpty(metric.getStatistics()) || metric.getUnit() == null)
          .forEach(cloudWatchMetric -> {
            cloudWatchMetric.setStatistics(getStatistics(awsNameSpace, cloudWatchMetric));
            cloudWatchMetric.setUnit(getUnit(awsNameSpace, cloudWatchMetric));
          });
    }
  }

  private String getStatistics(AwsNameSpace awsNameSpace, CloudWatchMetric cloudWatchMetric) {
    for (CloudWatchMetric metric : getCloudWatchMetrics().get(awsNameSpace)) {
      if (metric.getMetricName().equals(cloudWatchMetric.getMetricName())) {
        return metric.getStatistics();
      }
    }

    log.error("No statistics found for {} metric {}", awsNameSpace, cloudWatchMetric);
    return null;
  }

  private StandardUnit getUnit(AwsNameSpace awsNameSpace, CloudWatchMetric cloudWatchMetric) {
    for (CloudWatchMetric metric : getCloudWatchMetrics().get(awsNameSpace)) {
      if (metric.getMetricName().equals(cloudWatchMetric.getMetricName())) {
        return metric.getUnit();
      }
    }

    log.error("No unit found for {} metric {}", awsNameSpace, cloudWatchMetric);
    return null;
  }
}
