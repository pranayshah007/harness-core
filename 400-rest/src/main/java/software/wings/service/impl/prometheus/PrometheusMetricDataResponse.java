/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.prometheus;

import io.harness.exception.WingsException;

import software.wings.delegatetasks.cv.DelegateCVActivityLogService.Logger;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.intfc.analysis.MetricCollectionResponse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.TreeBasedTable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rsingh on 3/16/18.
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class PrometheusMetricDataResponse implements MetricCollectionResponse {
  private String status;
  private PrometheusMetricData data;

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PrometheusMetricData {
    private String resultType;
    private List<PrometheusMetricDataResult> result;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PrometheusMetricDataResult {
    private PrometheusMetric metric;
    private List<Object> value;
    private List<List<Object>> values;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PrometheusMetric {
    private String __name__;
    private String instance;
    private String job;
  }

  @Override
  public TreeBasedTable<String, Long, NewRelicMetricDataRecord> getMetricRecords(String transactionName,
      String metricName, String appId, String workflowId, String workflowExecutionId, String stateExecutionId,
      String serviceId, String host, String groupName, long collectionStartTime, String cvConfigId, boolean is247Task,
      String url, Logger activityLogger) {
    TreeBasedTable<String, Long, NewRelicMetricDataRecord> rv = TreeBasedTable.create();
    if (!status.equals("success")) {
      return rv;
    }

    if (data == null) {
      return rv;
    }

    if (data.getResult() == null) {
      return rv;
    }
    if (data.getResult().size() > 1) {
      String msg = "Multiple time series values are returned for metric name " + metricName + " and group name "
          + transactionName + ". Please add more filters to your query to return only one time series.";
      log.error("Validation failed for state {} appId: {} error message: {}", stateExecutionId, appId, msg);
      activityLogger.error(msg);
      // TODO: Once all the customers with this problem are identified and notified by CS team we need to throw the
      // exception.
    }
    for (PrometheusMetricDataResult dataResult : data.getResult()) {
      if (dataResult.getMetric() == null) {
        continue;
      }

      if (dataResult.getValues() == null) {
        continue;
      }

      for (List<Object> metricValues : dataResult.getValues()) {
        long timeStamp = parseTimeStamp(metricValues.get(0));
        Double value = Double.valueOf((String) metricValues.get(1));

        NewRelicMetricDataRecord metricDataRecord = rv.get(transactionName, timeStamp);
        if (metricDataRecord == null) {
          metricDataRecord =
              NewRelicMetricDataRecord.builder()
                  .name(transactionName)
                  .appId(appId)
                  .workflowId(workflowId)
                  .workflowExecutionId(workflowExecutionId)
                  .stateExecutionId(stateExecutionId)
                  .serviceId(serviceId)
                  .cvConfigId(cvConfigId)
                  .dataCollectionMinute(getDataCollectionMinute(timeStamp, collectionStartTime, is247Task))
                  .timeStamp(timeStamp)
                  .stateType(DelegateStateType.PROMETHEUS)
                  .host(host)
                  .groupName(groupName)
                  .build();
          metricDataRecord.setAppId(appId);
          if (metricDataRecord.getTimeStamp() >= collectionStartTime) {
            rv.put(transactionName, timeStamp, metricDataRecord);
          } else {
            log.info("Ignoring a record that was before dataCollectionStartTime.");
          }
        }

        Map<String, Double> values = metricDataRecord.getValues();
        if (values == null) {
          values = new HashMap<>();
          metricDataRecord.setValues(values);
        }
        values.put(metricName, value);

        Map<String, String> deepLinkByMetricName = metricDataRecord.getDeeplinkMetadata();
        if (deepLinkByMetricName == null) {
          deepLinkByMetricName = new HashMap<>();
          metricDataRecord.setDeeplinkMetadata(deepLinkByMetricName);
        }
        deepLinkByMetricName.put(metricName, getDeeplinkString(url));
      }
    }
    return rv;
  }

  private long parseTimeStamp(Object timeStamp) {
    if (timeStamp instanceof Double) {
      return ((Double) timeStamp).longValue() * TimeUnit.SECONDS.toMillis(1);
    } else if (timeStamp instanceof Long) {
      return (long) timeStamp * TimeUnit.SECONDS.toMillis(1);
    } else if (timeStamp instanceof Integer) {
      return (int) timeStamp * TimeUnit.SECONDS.toMillis(1);
    } else {
      throw new WingsException("Timestamp value in the metric is not valid. Received timestamp " + timeStamp);
    }
  }

  private String getDeeplinkString(String url) {
    // get Metric String from the url
    // eg: for URL
    // /api/v1/query_range?start=3245678&end=456789&step=60s&query=software_wings_resources_AccountResource_getAccounts_count
    // the DeeplinkSting will be software_wings_resources_AccountResource_getAccounts_count
    return url.substring(url.lastIndexOf('=') + 1, url.length());
  }

  private int getDataCollectionMinute(long metricTimeStamp, long collectionStartTime, boolean is247Task) {
    if (is247Task) {
      return (int) TimeUnit.MILLISECONDS.toMinutes(metricTimeStamp);
    } else {
      return (int) (TimeUnit.MILLISECONDS.toMinutes(metricTimeStamp - collectionStartTime));
    }
  }
}
