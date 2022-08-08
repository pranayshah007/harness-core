/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.apm;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.exception.WingsException;
import io.harness.time.Timestamp;

import software.wings.delegatetasks.CustomDataCollectionUtils;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;

import com.google.common.collect.Multimap;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class APMResponseParser {
  @Data
  @Builder
  @AllArgsConstructor
  public static class APMResponseData {
    private String hostName;
    private String groupName;
    private String text;
    private List<APMMetricInfo> metricInfos;
  }

  public static Collection<NewRelicMetricDataRecord> extract(List<APMResponseData> apmResponseData) {
    Map<String, NewRelicMetricDataRecord> resultMap = new HashMap<>();
    for (APMResponseData data : apmResponseData) {
      log.info("Response Data is :  {}", data);
      for (APMMetricInfo metricInfo : data.getMetricInfos()) {
        VerificationResponseParser apmResponseParser = new VerificationResponseParser();
        String timestampFormat = null;
        for (APMMetricInfo.ResponseMapper responseMapper : metricInfo.getResponseMappers().values()) {
          if (responseMapper.getTimestampFormat() != null) {
            timestampFormat = responseMapper.getTimestampFormat();
          }
          if (!isEmpty(responseMapper.getJsonPath())) {
            apmResponseParser.put(
                responseMapper.getJsonPath().split("\\."), responseMapper.getFieldName(), responseMapper.getRegexs());
          }
        }
        List<Multimap<String, Object>> output = null;
        try {
          output = apmResponseParser.extract(data.text);
        } catch (Exception ex) {
          log.warn("Unable to extract data in APM ResponseParser {}", data.text);
          continue;
        }
        createRecords(metricInfo.getResponseMappers().get("txnName").getFieldValue(), metricInfo.getMetricName(),
            data.hostName, metricInfo.getTag(), data.groupName, timestampFormat, output, resultMap);
      }
    }
    return resultMap.values();
  }

  private static void createRecords(String txnName, String metricName, String hostName, String tag, String groupName,
      String timestampFormat, List<Multimap<String, Object>> response,
      Map<String, NewRelicMetricDataRecord> resultMap) {
    if (groupName == null) {
      final String errorMsg =
          "Unexpected null groupName received while parsing APMResponse. Please contact Harness Support.";
      log.error(errorMsg);
      throw new WingsException(errorMsg);
    }
    for (Multimap<String, Object> record : response) {
      Iterator<Object> timestamps = record.containsKey("timestamp") ? record.get("timestamp").iterator() : null;
      Iterator<Object> values = record.get("value").iterator();
      Iterator<Object> txnNames = record.containsKey("txnName") ? record.get("txnName").iterator() : null;
      while (values.hasNext()) {
        long timestamp = timestamps != null ? parseTimestamp(timestamps.next(), timestampFormat) : 0;
        if (txnNames != null && record.get("value").size() == record.get("txnName").size()) {
          txnName = (String) txnNames.next();
        } else {
          txnName = record.containsKey("txnName") ? (String) record.get("txnName").iterator().next() : txnName;
        }
        hostName = record.containsKey("host") ? (String) record.get("host").iterator().next() : hostName;
        metricName =
            record.containsKey("metricName") ? (String) record.get("metricName").iterator().next() : metricName;
        String key = timestamp + ":" + txnName + ":" + hostName;

        if (!resultMap.containsKey(key)) {
          resultMap.put(key, new NewRelicMetricDataRecord());
          resultMap.get(key).setTimeStamp(timestamp);
          resultMap.get(key).setValues(new HashMap<>());
          resultMap.get(key).setName(txnName);
          resultMap.get(key).setHost(hostName);
          resultMap.get(key).setTag(tag);
          resultMap.get(key).setGroupName(groupName);
        }

        Object val = values.next();

        resultMap.get(key).getValues().put(metricName, (Double) VerificationResponseParser.cast(val, "value"));
      }
    }
  }

  private static long parseTimestamp(Object timestampObj, String timestampFormat) {
    long timestamp;
    try {
      timestamp = (long) VerificationResponseParser.cast(timestampObj, "timestamp");
    } catch (WingsException w) {
      if (timestampFormat != null) {
        String timestampStr = (String) timestampObj;
        try {
          timestamp = CustomDataCollectionUtils.parseTimestampfield(timestampStr, timestampFormat);
        } catch (ParseException e) {
          throw new DataCollectionException("Unable to parse date during data collection", e);
        }
      } else {
        throw w;
      }
    }
    long now = Timestamp.currentMinuteBoundary();
    if (timestamp != 0 && String.valueOf(timestamp).length() < String.valueOf(now).length()) {
      // Timestamp is in seconds. Convert to millis
      timestamp = timestamp * 1000;
    }
    return timestamp;
  }
}
