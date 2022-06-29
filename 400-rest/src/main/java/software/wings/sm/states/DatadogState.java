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

import static software.wings.common.VerificationConstants.DD_ECS_HOST_NAME;
import static software.wings.common.VerificationConstants.DD_HOST_NAME_EXPRESSION;
import static software.wings.common.VerificationConstants.DD_K8s_HOST_NAME;
import static software.wings.metrics.MetricType.ERROR;
import static software.wings.metrics.MetricType.RESP_TIME;
import static software.wings.metrics.MetricType.THROUGHPUT;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.WingsException;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.YamlUtils;

import software.wings.api.DeploymentType;
import software.wings.beans.DatadogConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.metrics.MetricType;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.DataCollectionCallback;
import software.wings.service.impl.analysis.TimeSeriesMetricGroup;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.apm.APMDataCollectionInfo;
import software.wings.service.impl.apm.APMMetricInfo;
import software.wings.service.impl.apm.APMMetricInfo.APMMetricInfoBuilder;
import software.wings.service.impl.apm.APMMetricInfo.ResponseMapper;
import software.wings.service.impl.datadog.DatadogServiceImpl;
import software.wings.service.intfc.datadog.DatadogService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.stencils.DefaultValue;
import software.wings.verification.VerificationStateAnalysisExecutionData;
import software.wings.verification.datadog.DatadogCVServiceConfiguration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

@Slf4j
@FieldNameConstants(innerTypeName = "DatadogStateKeys")
@OwnedBy(HarnessTeam.CV)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class DatadogState extends AbstractMetricAnalysisState {
  @Inject @SchemaIgnore private transient DatadogService datadogService;
  @Inject @SchemaIgnore private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  private static final int DATA_COLLECTION_RATE_MINS = 5;
  private static final URL DATADOG_URL = DatadogState.class.getResource("/apm/datadog.yml");
  private static final URL DATADOG_METRICS_URL = DatadogState.class.getResource("/apm/datadog_metrics.yml");
  private static final Map<String, List<Metric>> metricsMap;
  private static final Map<String, MetricInfo> metricInfos;
  static {
    String tmpDatadogMetricsYaml = "", tmpDatadogYaml = "";
    Map<String, List<Metric>> tmpMetricsMap = new HashMap<>();
    Map<String, MetricInfo> tmpMetricInfos = new HashMap<>();
    try {
      tmpDatadogMetricsYaml = Resources.toString(DATADOG_METRICS_URL, Charsets.UTF_8);
      tmpDatadogYaml = Resources.toString(DATADOG_URL, Charsets.UTF_8);
      YamlUtils yamlUtils = new YamlUtils();
      tmpMetricsMap = yamlUtils.read(tmpDatadogMetricsYaml, new TypeReference<Map<String, List<Metric>>>() {});
      tmpMetricInfos = yamlUtils.read(tmpDatadogYaml, new TypeReference<Map<String, MetricInfo>>() {});
    } catch (IOException ex) {
      log.error("Unable to initialize datadog metrics yaml");
    }
    metricsMap = tmpMetricsMap;
    metricInfos = tmpMetricInfos;
  }

  public DatadogState(String name) {
    super(name, StateType.DATA_DOG);
  }

  @Override
  public Logger getLogger() {
    return log;
  }

  @Attributes(required = true, title = "Datadog Server") private String analysisServerConfigId;

  @Attributes(required = false, title = "Datadog Service Name") private String datadogServiceName;

  @Attributes(required = false, title = "Metrics") private String metrics;

  @Attributes(required = false, title = "Custom Metrics") private Map<String, Set<Metric>> customMetrics;

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

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> validateFields = new HashMap<>();
    if (isNotEmpty(customMetrics)) {
      validateFields.putAll(validateDatadogCustomMetrics(customMetrics));
    }
    if (isNotEmpty(metrics)) {
      List<String> metricList = Arrays.asList(metrics.split(","));
      metricList.forEach(metric -> {
        if (metric.startsWith("trace.")) {
          validateFields.put(metric, "Unsupported metric type for workflow verification");
        }
      });
    }
    validateFields.putAll(DatadogServiceImpl.validateNameClashInCustomMetrics(customMetrics, metrics));
    return validateFields;
  }

  @Override
  protected String triggerAnalysisDataCollection(ExecutionContext context, AnalysisContext analysisContext,
      VerificationStateAnalysisExecutionData executionData, Map<String, String> hosts) {
    List<String> metricNames = metrics != null ? Arrays.asList(metrics.split(",")) : Collections.EMPTY_LIST;
    String hostFilter = getDeploymentType(context) == DeploymentType.ECS ? DD_ECS_HOST_NAME : DD_K8s_HOST_NAME;
    metricAnalysisService.saveMetricTemplates(context.getAppId(), StateType.DATA_DOG,
        context.getStateExecutionInstanceId(), null,
        metricDefinitions(metrics(Optional.of(metricNames), Optional.ofNullable(datadogServiceName),
            Optional.ofNullable(customMetrics), Optional.empty(), Optional.of(hostFilter))
                              .values()));

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String envId = workflowStandardParams == null
        ? null
        : workflowStandardParamsExtensionService.getEnv(workflowStandardParams).getUuid();
    String serverConfigId =
        getResolvedConnectorId(context, DatadogStateKeys.analysisServerConfigId, analysisServerConfigId);
    String serviceName = this.datadogServiceName;

    SettingAttribute settingAttribute = settingsService.get(serverConfigId);
    if (settingAttribute == null) {
      throw new DataCollectionException("No Datadog setting with id: " + analysisServerConfigId + " found");
    }

    final DatadogConfig datadogConfig = (DatadogConfig) settingAttribute.getValue();
    final long dataCollectionStartTimeStamp = dataCollectionStartTimestampMillis();
    String accountId = appService.get(context.getAppId()).getAccountId();
    int timeDurationInInteger = Integer.parseInt(getTimeDuration(context));
    final APMDataCollectionInfo dataCollectionInfo =
        APMDataCollectionInfo.builder()
            .baseUrl(datadogConfig.getUrl())
            .validationUrl(DatadogConfig.validationUrl)
            .encryptedDataDetails(
                secretManager.getEncryptionDetails(datadogConfig, context.getAppId(), context.getWorkflowExecutionId()))
            .hosts(hosts)
            .stateType(DelegateStateType.DATA_DOG)
            .applicationId(context.getAppId())
            .stateExecutionId(context.getStateExecutionInstanceId())
            .workflowId(getWorkflowId(context))
            .workflowExecutionId(context.getWorkflowExecutionId())
            .serviceId(getPhaseServiceId(context))
            .startTime(dataCollectionStartTimeStamp)
            .dataCollectionMinute(0)
            .metricEndpoints(metricEndpointsInfo(Optional.ofNullable(serviceName), Optional.of(metricNames),
                Optional.empty(), Optional.ofNullable(customMetrics), Optional.ofNullable(getDeploymentType(context))))
            .accountId(accountId)
            .strategy(getComparisonStrategy())
            .dataCollectionFrequency(DATA_COLLECTION_RATE_MINS)
            .dataCollectionTotalTime(timeDurationInInteger)
            .initialDelaySeconds(getDelaySeconds(initialAnalysisDelay))
            .validateCert(accountService.isCertValidationRequired(accountId))
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
                      .taskType(TaskType.APM_METRIC_DATA_COLLECTION_TASK.name())
                      .parameters(new Object[] {dataCollectionInfo})
                      .timeout(TimeUnit.MINUTES.toMillis(timeDurationInInteger + 120))
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

  @SchemaIgnore
  protected String getStateBaseUrl() {
    return "datadog";
  }

  @Override
  public String getAnalysisServerConfigId() {
    return analysisServerConfigId;
  }

  @Override
  public void setAnalysisServerConfigId(String analysisServerConfigId) {
    this.analysisServerConfigId = analysisServerConfigId;
  }

  public String getDatadogServiceName() {
    return datadogServiceName;
  }

  public void setDatadogServiceName(String datadogServiceName) {
    this.datadogServiceName = datadogServiceName;
  }

  public Map<String, Set<Metric>> fetchCustomMetrics() {
    return customMetrics;
  }

  public void setCustomMetrics(Map<String, Set<Metric>> customMetrics) {
    this.customMetrics = customMetrics;
  }

  public static String getMetricTypeForMetric(String metricName, DatadogCVServiceConfiguration cvConfig) {
    try {
      List<Metric> metrics = clonedMetricsMap().values().stream().flatMap(List::stream).collect(Collectors.toList());
      Optional<Metric> matchedMetric =
          metrics.stream().filter(metric -> metric.getMetricName().equals(metricName)).findAny();
      if (matchedMetric.isPresent()) {
        return matchedMetric.get().getMlMetricType();
      }
      if (cvConfig != null && isNotEmpty(cvConfig.getCustomMetrics())) {
        for (Entry<String, Set<Metric>> customMetricEntry : cvConfig.getCustomMetrics().entrySet()) {
          if (isNotEmpty(customMetricEntry.getValue())) {
            for (Metric metric : customMetricEntry.getValue()) {
              if (metricName.equals(metric.getDisplayName())) {
                return metric.getMlMetricType();
              }
            }
          }
        }
      }
    } catch (Exception e) {
      log.error("Exception occurred while calculating metric type for name: {}", metricName, e);
    }
    return null;
  }

  public static Map<String, List<APMMetricInfo>> metricEndpointsInfo(Optional<String> datadogServiceName,
      Optional<List<String>> metricNames, Optional<String> applicationFilter,
      Optional<Map<String, Set<Metric>>> customMetrics, Optional<DeploymentType> deploymentType) {
    Map<String, MetricInfo> cloneMetricInfoMap = clonedMetricInfos();
    if (!metricNames.isPresent()) {
      metricNames = Optional.of(new ArrayList<>());
    }

    String hostFilter;
    if (deploymentType.isPresent() && deploymentType.get() == DeploymentType.ECS) {
      parseMetricInfo(cloneMetricInfoMap.get("Docker"), DD_ECS_HOST_NAME);
      hostFilter = DD_ECS_HOST_NAME;
    } else {
      parseMetricInfo(cloneMetricInfoMap.get("Docker"), DD_K8s_HOST_NAME);
      hostFilter = DD_K8s_HOST_NAME;
    }
    Map<String, Metric> metricMap =
        metrics(metricNames, datadogServiceName, customMetrics, applicationFilter, Optional.of(hostFilter));

    // metrics list will have list of metric objects for given MetricNames
    List<Metric> metrics = new ArrayList<>();
    for (String metricName : metricNames.get()) {
      if (!metricMap.containsKey(metricName)) {
        throw new WingsException("metric name not found" + metricName);
      }
      metrics.add(metricMap.get(metricName));
    }

    Map<String, List<APMMetricInfo>> result = new HashMap<>();
    for (Metric metric : metrics) {
      APMMetricInfoBuilder newMetricInfoBuilder = APMMetricInfo.builder();
      MetricInfo metricInfo = cloneMetricInfoMap.get(metric.getDatadogMetricType());

      String metricUrl = getMetricURL(metricInfo, metric.getDatadogMetricType(), deploymentType);
      newMetricInfoBuilder.responseMappers(metricInfo.responseMapperMap());
      newMetricInfoBuilder.metricType(MetricType.valueOf(metric.getMlMetricType()));
      newMetricInfoBuilder.tag(metric.getDatadogMetricType());
      newMetricInfoBuilder.responseMappers(metricInfo.responseMapperMap());
      newMetricInfoBuilder.metricName(metric.getDisplayName());

      if (Arrays.asList("System", "Kubernetes", "Docker", "ECS").contains(metric.getDatadogMetricType())) {
        metricUrl = metricUrl.replace("${query}", metric.getMetricName());
        if (applicationFilter.isPresent()) {
          metricUrl = metricUrl.replace("${applicationFilter}", applicationFilter.get());
        }

        metricUrl = parseTransformationUnit(metricUrl, deploymentType, metric);

        if (!result.containsKey(metricUrl)) {
          result.put(metricUrl, new ArrayList<>());
        }
        result.get(metricUrl).add(newMetricInfoBuilder.build());
      } else if (metric.getDatadogMetricType().equals("Servlet")) {
        if (datadogServiceName.isPresent()) {
          metricUrl = metricUrl.replace("${datadogServiceName}", datadogServiceName.get());
        }
        metricUrl = metricUrl.replace("${query}", metric.getMetricName());

        if (!applicationFilter.isPresent()) {
          applicationFilter = Optional.of("");
        }
        metricUrl = metricUrl.replace("${applicationFilter}", applicationFilter.get());

        if (!result.containsKey(metricUrl)) {
          result.put(metricUrl, new ArrayList<>());
        }
        result.get(metricUrl).add(newMetricInfoBuilder.build());
      } else {
        throw new WingsException("Unsupported template type for" + metric);
      }
    }

    if (customMetrics.isPresent()) {
      for (String identifier : customMetrics.get().keySet()) {
        // identifier can be host_identifier or application filter
        if (deploymentType.isPresent()) {
          parseMetricInfo(cloneMetricInfoMap.get("Custom"), identifier);
        } else {
          parseMetricInfo(cloneMetricInfoMap.get("Custom"), DD_K8s_HOST_NAME);
        }
        Set<Metric> metricSet = customMetrics.get().get(identifier);
        metricSet.forEach(metric -> {
          MetricInfo metricInfo = cloneMetricInfoMap.get(metric.getDatadogMetricType());

          APMMetricInfoBuilder newMetricInfoBuilder = APMMetricInfo.builder();
          // update the response mapper with the transaction/group name.
          Map<String, ResponseMapper> responseMapperMap = metricInfo.responseMapperMap();
          String txnName = "Transaction Group 1";
          if (isNotEmpty(metric.getTxnName())) {
            txnName = metric.getTxnName();
          }
          responseMapperMap.put("txnName", ResponseMapper.builder().fieldName("txnName").fieldValue(txnName).build());
          newMetricInfoBuilder.responseMappers(responseMapperMap);
          newMetricInfoBuilder.metricType(MetricType.valueOf(metric.getMlMetricType()));
          newMetricInfoBuilder.tag(metric.getDatadogMetricType());
          newMetricInfoBuilder.metricName(metric.getDisplayName());

          String metricUrl = getMetricURL(metricInfo, metric.getDatadogMetricType(), deploymentType);
          metricUrl = metricUrl.replace("${query}", metric.getMetricName());
          if (deploymentType.isPresent()) {
            metricUrl = metricUrl.replace("${host_identifier}", identifier);
          } else {
            metricUrl = metricUrl.replace("${applicationFilter}", identifier);
          }
          if (!result.containsKey(metricUrl)) {
            result.put(metricUrl, new ArrayList<>());
          }
          result.get(metricUrl).add(newMetricInfoBuilder.build());
        });
      }
    }
    return result;
  }

  private static String parseTransformationUnit(
      String metricUrl, Optional<DeploymentType> deploymentType, Metric metric) {
    if (deploymentType.isPresent()) {
      // workflow based deployment
      if (isEmpty(metric.getTransformation())) {
        metricUrl = metricUrl.replace("${transformUnits}", "");
      } else {
        metricUrl = metricUrl.replace("${transformUnits}", metric.getTransformation());
      }
    } else {
      if (isEmpty(metric.getTransformation24x7())) {
        metricUrl = metricUrl.replace("${transformUnits}", "");
      } else {
        metricUrl = metricUrl.replace("${transformUnits}", metric.getTransformation24x7());
      }
    }
    return metricUrl;
  }

  private static String getMetricURL(
      MetricInfo metricInfo, String datadogMetricType, Optional<DeploymentType> deploymentType) {
    String metricUrl;
    if (deploymentType.isPresent()) {
      metricUrl = deploymentType.get() == DeploymentType.ECS && datadogMetricType.equals("Docker")
          ? metricInfo.getUrlEcs()
          : metricInfo.getUrl();
    } else {
      metricUrl = metricInfo.getUrl24x7();
    }
    return metricUrl;
  }

  private static void parseMetricInfo(MetricInfo metricInfo, String hostname) {
    for (ResponseMapper responseMapper : metricInfo.getResponseMappers()) {
      if (responseMapper.getFieldName().equals("host")) {
        responseMapper.getRegexs().replaceAll(regex -> regex.replace(DD_HOST_NAME_EXPRESSION, hostname));
      }
    }
  }

  public static Map<String, TimeSeriesMetricDefinition> metricDefinitions(Collection<Metric> metrics) {
    Map<String, TimeSeriesMetricDefinition> metricTypeMap = new HashMap<>();
    for (Metric metric : metrics) {
      metricTypeMap.put(metric.getDisplayName(),
          TimeSeriesMetricDefinition.builder()
              .metricName(metric.getDisplayName())
              .metricType(MetricType.valueOf(metric.getMlMetricType()))
              .tags(metric.getTags())
              .build());
    }
    return metricTypeMap;
  }

  public static Map<String, Metric> metrics(Optional<List<String>> metricNames, Optional<String> datadogServiceName,
      Optional<Map<String, Set<Metric>>> customMetricsByTag, Optional<String> applicationFilter,
      Optional<String> hostFilter) {
    Map<String, List<Metric>> clonedMetricsMap = clonedMetricsMap();
    try {
      if (!metricNames.isPresent()) {
        metricNames = Optional.of(new ArrayList<>());
      }
      // if datadog service name provided then analysis will be done for servlet metrics
      if (datadogServiceName.isPresent()) {
        // add the servlet metrics to this list.
        List<String> servletMetrics = new ArrayList<>();
        clonedMetricsMap.get("Servlet").forEach(servletMetric -> servletMetrics.add(servletMetric.getMetricName()));
        metricNames.get().addAll(servletMetrics);
      }

      Map<String, Metric> metricMap = new HashMap<>();
      Set<String> metricNamesSet = Sets.newHashSet(metricNames.get());

      // add servlet, docker, ecs metrics to the map
      for (Map.Entry<String, List<Metric>> entry : clonedMetricsMap.entrySet()) {
        entry.getValue().forEach(metric -> {
          if (metricNamesSet.contains(metric.getMetricName())) {
            if (metric.getTags() == null) {
              metric.setTags(new HashSet());
            }
            metric.getTags().add(entry.getKey());

            // transformation24x7 needs to use application filter in transformation metric as well.
            if (applicationFilter.isPresent() && isNotEmpty(metric.getTransformation24x7())) {
              metric.setTransformation24x7(
                  metric.getTransformation24x7().replace("${applicationFilter}", applicationFilter.get()));
            }
            if (hostFilter.isPresent() && metric.getDatadogMetricType().equals("Docker")
                && isNotEmpty(metric.getTransformation())) {
              metric.setTransformation(metric.getTransformation().replace("${hostFilter}", hostFilter.get()));
            }

            metricMap.put(metric.getMetricName(), metric);
          }
        });
      }

      // add custom metrics to the map
      if (customMetricsByTag.isPresent()) {
        for (Entry<String, Set<Metric>> entry : customMetricsByTag.get().entrySet()) {
          entry.getValue().forEach(metric -> {
            metric.setTags(new HashSet<>());
            metric.getTags().add("Custom");
            metricMap.put(metric.getMetricName(), metric);
          });
        }
      }
      return metricMap;
    } catch (Exception ex) {
      throw new WingsException("Unable to load datadog metrics", ex);
    }
  }

  public static List<Metric> metricNames() {
    return clonedMetricsMap().values().stream().flatMap(Collection::stream).collect(Collectors.toList());
  }

  public String getMetrics() {
    return metrics;
  }

  public void setMetrics(String metrics) {
    this.metrics = metrics;
  }

  /**
   * Validate the fields for custom metrics - for each txn, there should be only one throughput
   * If error/response time is present, throughput should be present too.
   * If only throughput is present, we won't analyze it.
   * @param customMetrics
   * @return
   */
  public static Map<String, String> validateDatadogCustomMetrics(Map<String, Set<Metric>> customMetrics) {
    if (isNotEmpty(customMetrics)) {
      Map<String, String> invalidFields = new HashMap<>();
      // group the metrics by txn.
      Map<String, Set<Metric>> txnMetricMap = new HashMap<>();
      customMetrics.forEach((filter, metricSet) -> {
        List<Metric> metricList = new ArrayList<>();
        for (Object metricObj : metricSet) {
          Metric metric = JsonUtils.asObject(JsonUtils.asJson(metricObj), Metric.class);
          metricList.add(metric);
        }
        metricList.forEach(metric -> {
          String txnFilter = filter + "-" + metric.getTxnName();
          if (!txnMetricMap.containsKey(txnFilter)) {
            txnMetricMap.put(txnFilter, new HashSet<>());
          }
          txnMetricMap.get(txnFilter).add(metric);
        });
      });

      // validate the txnMetricMap for the ones mentioned above.
      txnMetricMap.forEach((txnName, metricSet) -> {
        AtomicInteger throughputCount = new AtomicInteger(0);
        AtomicInteger otherMetricsCount = new AtomicInteger(0);
        AtomicInteger errorResponseCount = new AtomicInteger(0);
        metricSet.forEach(metric -> {
          if (metric.getMlMetricType().equals(THROUGHPUT.name())) {
            throughputCount.incrementAndGet();
          } else {
            otherMetricsCount.incrementAndGet();
            if (metric.getMlMetricType().equals(RESP_TIME.name()) || metric.getMlMetricType().equals(ERROR.name())) {
              errorResponseCount.incrementAndGet();
            }
          }
        });

        if (throughputCount.get() > 1) {
          invalidFields.put("Incorrect throughput configuration for group: " + txnName,
              "There are more than one throughput metrics defined.");
        }
        if (otherMetricsCount.get() == 0 && throughputCount.get() != 0) {
          invalidFields.put("Invalid metric configuration for group: " + txnName,
              "It has only throughput metrics. Throughput metrics is used to analyze other metrics and is not analyzed.");
        }
        if (errorResponseCount.get() > 0 && throughputCount.get() == 0) {
          invalidFields.put("Incorrect configuration for group: " + txnName,
              "Error or Response metrics have been defined for " + txnName
                  + " but there is no definition for a throughput metric.");
        }
      });

      return invalidFields;
    }

    return new HashMap<>();
  }

  private static Map<String, List<Metric>> clonedMetricsMap() {
    Map<String, List<Metric>> clonedMetricsMap = new HashMap<>();
    metricsMap.forEach((name, metrics) -> {
      List<Metric> clonedMetrics = new ArrayList<>();
      metrics.forEach(metric -> clonedMetrics.add(metric.clone()));
      clonedMetricsMap.put(name, clonedMetrics);
    });
    return clonedMetricsMap;
  }

  private static Map<String, MetricInfo> clonedMetricInfos() {
    Map<String, MetricInfo> clonedMetricInfos = new HashMap<>();
    metricInfos.forEach((name, metricInfo) -> clonedMetricInfos.put(name, metricInfo.clone()));
    return clonedMetricInfos;
  }

  @Data
  @Builder
  public static class Metric implements Cloneable {
    private String metricName;
    private String mlMetricType;
    private String datadogMetricType;
    private String displayName;
    private String transformation;
    private String transformation24x7;
    private Set<String> tags;
    private String txnName; // this field is optional. It can be extracted from the response

    @Override
    public Metric clone() {
      return Metric.builder()
          .metricName(metricName)
          .mlMetricType(mlMetricType)
          .datadogMetricType(datadogMetricType)
          .displayName(displayName)
          .transformation(transformation)
          .transformation24x7(transformation24x7)
          .tags(isEmpty(tags) ? tags : Sets.newHashSet(tags))
          .txnName(txnName)
          .build();
    }
  }

  @Data
  @Builder
  public static class MetricInfo implements Cloneable {
    private String url;
    private String urlEcs;
    private String url24x7;
    private List<ResponseMapper> responseMappers;
    public Map<String, ResponseMapper> responseMapperMap() {
      Map<String, ResponseMapper> result = new HashMap<>();
      for (ResponseMapper responseMapper : responseMappers) {
        result.put(responseMapper.getFieldName(), responseMapper);
      }
      return result;
    }

    @Override
    public MetricInfo clone() {
      List<ResponseMapper> clonedResponseMappers = new ArrayList<>();
      if (isNotEmpty(responseMappers)) {
        responseMappers.forEach(responseMapper -> clonedResponseMappers.add(responseMapper.clone()));
      }

      return MetricInfo.builder()
          .url(url)
          .urlEcs(urlEcs)
          .url24x7(url24x7)
          .responseMappers(clonedResponseMappers)
          .build();
    }
  }
}
