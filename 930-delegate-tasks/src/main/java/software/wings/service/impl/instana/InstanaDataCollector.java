/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instana;

import static software.wings.delegatetasks.cv.CVConstants.DEFAULT_GROUP_NAME;
import static software.wings.delegatetasks.cv.CVConstants.DUMMY_HOST_NAME;
import static software.wings.delegatetasks.cv.CVConstants.INSTANA_DOCKER_PLUGIN;
import static software.wings.delegatetasks.cv.CVConstants.INSTANA_GROUPBY_TAG_TRACE_NAME;
import static software.wings.delegatetasks.cv.CVConstants.VERIFICATION_HOST_PLACEHOLDER;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.delegatetasks.cv.DataCollectionExecutionContext;
import software.wings.delegatetasks.cv.MetricsDataCollector;
import software.wings.service.impl.analysis.MetricElement;
import software.wings.service.intfc.instana.InstanaDelegateService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class InstanaDataCollector implements MetricsDataCollector<InstanaDataCollectionInfo> {
  private static final String INFRASTRUCTURE = "Infrastructure";

  @Inject private InstanaDelegateService instanaDelegateService;
  private InstanaDataCollectionInfo dataCollectionInfo;
  private DataCollectionExecutionContext dataCollectionExecutionContext;

  @Override
  public void init(DataCollectionExecutionContext dataCollectionExecutionContext,
      InstanaDataCollectionInfo dataCollectionInfo) throws DataCollectionException {
    this.dataCollectionInfo = dataCollectionInfo;
    this.dataCollectionExecutionContext = dataCollectionExecutionContext;
  }

  @Override
  public int getHostBatchSize() {
    return 1;
  }
  private boolean hasInfraParamsDefined() {
    return dataCollectionInfo.getQuery() != null;
  }

  private boolean isApplicationParamsDefined() {
    return dataCollectionInfo.getHostTagFilter() != null;
  }

  @Override
  public List<MetricElement> fetchMetrics(List<String> hostBatch) throws DataCollectionException {
    Preconditions.checkArgument(hostBatch.size() == 1, "size can not be greater than 1");
    List<MetricElement> metricElements = new ArrayList<>();
    if (hasInfraParamsDefined()) {
      Preconditions.checkArgument(dataCollectionInfo.getQuery().contains(VERIFICATION_HOST_PLACEHOLDER),
          "Query should contain %s", VERIFICATION_HOST_PLACEHOLDER);
      InstanaInfraMetricRequest instanaInfraMetricRequest =
          InstanaInfraMetricRequest.builder()
              .metrics(dataCollectionInfo.getMetrics())
              .plugin(INSTANA_DOCKER_PLUGIN)
              .query(
                  dataCollectionInfo.getQuery().replace(VERIFICATION_HOST_PLACEHOLDER, "\"" + hostBatch.get(0)) + "\"")
              .rollup(60)
              .timeframe(getTimeframeForInfraMetric())
              .build();
      InstanaInfraMetrics instanaInfraMetrics = instanaDelegateService.getInfraMetrics(
          dataCollectionInfo.getInstanaConfig(), dataCollectionInfo.getEncryptedDataDetails(),
          instanaInfraMetricRequest, dataCollectionExecutionContext.createApiCallLog());
      metricElements.addAll(getMetricElements(instanaInfraMetrics, hostBatch.get(0)));
    }
    if (isApplicationParamsDefined()) {
      List<InstanaAnalyzeMetricRequest.Metric> metrics = new ArrayList<>();

      InstanaUtils.getApplicationMetricTemplateMap().forEach(
          (metricName, instanaMetricTemplate)
              -> metrics.add(InstanaAnalyzeMetricRequest.Metric.builder()
                                 .metric(instanaMetricTemplate.getMetricName())
                                 .aggregation(instanaMetricTemplate.getAggregation())
                                 .granularity(60)
                                 .build()));

      InstanaAnalyzeMetricRequest.Group group =
          InstanaAnalyzeMetricRequest.Group.builder().groupByTag(INSTANA_GROUPBY_TAG_TRACE_NAME).build();
      List<InstanaTagFilter> tagFilters = new ArrayList<>(dataCollectionInfo.getTagFilters());
      tagFilters.add(InstanaTagFilter.builder()
                         .name(dataCollectionInfo.getHostTagFilter())
                         .value(hostBatch.get(0))
                         .operator(InstanaTagFilter.Operator.EQUALS)
                         .build());
      InstanaAnalyzeMetricRequest instanaAnalyzeMetricRequest = InstanaAnalyzeMetricRequest.builder()
                                                                    .metrics(metrics)
                                                                    .group(group)
                                                                    .tagFilters(tagFilters)
                                                                    .timeFrame(getTimeframeForTraceMetric())
                                                                    .build();

      InstanaAnalyzeMetrics instanaAnalyzeMetrics = instanaDelegateService.getInstanaTraceMetrics(
          dataCollectionInfo.getInstanaConfig(), dataCollectionInfo.getEncryptedDataDetails(),
          instanaAnalyzeMetricRequest, dataCollectionExecutionContext.createApiCallLog());
      metricElements.addAll(getMetricElements(instanaAnalyzeMetrics, hostBatch.get(0)));
    }
    return metricElements;
  }

  private InstanaTimeFrame getTimeframeForTraceMetric() {
    Duration duration = Duration.between(dataCollectionInfo.getStartTime(), dataCollectionInfo.getEndTime());
    return InstanaTimeFrame.builder()
        .to(dataCollectionInfo.getEndTime().toEpochMilli())
        .windowSize(TimeUnit.SECONDS.toMillis(duration.getSeconds()))
        .build();
  }
  private InstanaTimeFrame getTimeframeForInfraMetric() {
    // unfortunately both APIs need different way of creating timeframe.
    Duration duration = Duration.between(dataCollectionInfo.getStartTime(), dataCollectionInfo.getEndTime());
    return InstanaTimeFrame.builder()
        .to(dataCollectionInfo.getEndTime().toEpochMilli() - 1)
        .windowSize(TimeUnit.SECONDS.toMillis(duration.getSeconds() - 1))
        .build();
  }

  private List<MetricElement> getMetricElements(InstanaInfraMetrics instanaInfraMetrics, String host) {
    if (instanaInfraMetrics.getItems().isEmpty()) {
      return Collections.emptyList();
    }
    Map<Long, MetricElement> timestampToMetricMap = new HashMap<>();
    Map<String, InstanaMetricTemplate> metricTemplateMap = InstanaUtils.getInfraMetricTemplateMap();
    instanaInfraMetrics.getItems().get(0).getMetrics().forEach((metricName, values) -> values.forEach(metricValue -> {
      Long timestamp = metricValue.get(0).longValue();
      MetricElement metricElement = timestampToMetricMap.getOrDefault(timestamp,
          MetricElement.builder()
              .groupName(DEFAULT_GROUP_NAME)
              .host(host)
              .name(INFRASTRUCTURE)
              .timestamp(timestamp)
              .build());

      metricElement.getValues().put(
          metricTemplateMap.get(metricName).getDisplayName(), metricValue.get(1).doubleValue());
      timestampToMetricMap.put(timestamp, metricElement);
    }));
    return new ArrayList<>(timestampToMetricMap.values());
  }

  private List<MetricElement> getMetricElements(InstanaAnalyzeMetrics instanaAnalyzeMetrics, String host) {
    List<MetricElement> metricElements = new ArrayList<>();

    instanaAnalyzeMetrics.getItems().forEach(item -> {
      Map<Long, MetricElement> metricElementsMap = new HashMap<>();

      Map<String, InstanaMetricTemplate> metricTemplateMap = InstanaUtils.getApplicationMetricTemplateMap();
      item.getMetrics().forEach((metricStr, values) -> {
        String metricName = metricStr.split("\\.")[0]; // ex: latency.p99.60
        values.forEach(value -> {
          Long timestamp = value.get(0).longValue();
          MetricElement metricElement = metricElementsMap.getOrDefault(timestamp,
              MetricElement.builder()
                  .timestamp(timestamp)
                  .groupName(DEFAULT_GROUP_NAME)
                  .host(host)
                  .name(item.getName())
                  .build());
          metricElement.getValues().put(metricTemplateMap.get(metricName).getDisplayName(), value.get(1).doubleValue());
          metricElementsMap.put(timestamp, metricElement);
        });
      });
      metricElements.addAll(metricElementsMap.values());
    });
    return metricElements;
  }

  @Override
  public List<MetricElement> fetchMetrics() throws DataCollectionException {
    List<InstanaAnalyzeMetricRequest.Metric> metrics = new ArrayList<>();
    InstanaUtils.getApplicationMetricTemplateMap().forEach(
        (metricName, instanaMetricTemplate)
            -> metrics.add(InstanaAnalyzeMetricRequest.Metric.builder()
                               .metric(instanaMetricTemplate.getMetricName())
                               .aggregation(instanaMetricTemplate.getAggregation())
                               .granularity(60)
                               .build()));
    InstanaAnalyzeMetricRequest.Group group =
        InstanaAnalyzeMetricRequest.Group.builder().groupByTag(INSTANA_GROUPBY_TAG_TRACE_NAME).build();

    InstanaAnalyzeMetricRequest instanaAnalyzeMetricRequest = InstanaAnalyzeMetricRequest.builder()
                                                                  .metrics(metrics)
                                                                  .group(group)
                                                                  .tagFilters(dataCollectionInfo.getTagFilters())
                                                                  .timeFrame(getTimeframeForTraceMetric())
                                                                  .build();

    InstanaAnalyzeMetrics instanaAnalyzeMetrics = instanaDelegateService.getInstanaTraceMetrics(
        dataCollectionInfo.getInstanaConfig(), dataCollectionInfo.getEncryptedDataDetails(),
        instanaAnalyzeMetricRequest, dataCollectionExecutionContext.createApiCallLog());
    List<MetricElement> metricElements = new ArrayList<>();
    metricElements.addAll(getMetricElements(instanaAnalyzeMetrics, DUMMY_HOST_NAME));
    return metricElements;
  }
}
