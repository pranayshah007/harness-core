/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.log;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.exception.WingsException;
import io.harness.time.Timestamp;

import software.wings.delegatetasks.CustomDataCollectionUtils;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.delegatetasks.cv.beans.CustomLogResponseMapper;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.apm.VerificationResponseParser;

import com.google.common.collect.Multimap;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogResponseParser {
  private static final String TIMESTAMP_FIELD = "timestamp";
  private static String DEFAULT_TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss[.SSS][.SS]XXX";
  private static final int MAX_CONCAT_JSON_FIELDS = 100;

  private String timestampFormat = DEFAULT_TIMESTAMP_FORMAT;
  @Data
  @Builder
  @AllArgsConstructor
  public static class LogResponseData {
    private String responseText;
    private Set<String> hostList;
    private boolean shouldDoHostBasedFiltering;
    private boolean fixedHostName;
    private Map<String, CustomLogResponseMapper> responseMappers;
  }

  public List<LogElement> extractLogs(LogResponseData data) {
    Map<String, LogElement> resultMap = new HashMap<>();
    VerificationResponseParser logsResponseParser = new VerificationResponseParser();
    for (CustomLogResponseMapper responseMapper : data.getResponseMappers().values()) {
      if (!isEmpty(responseMapper.getJsonPath()) && !isEmpty(responseMapper.getJsonPath().get(0))) {
        logsResponseParser.put(responseMapper.getJsonPath().get(0).split("\\."), responseMapper.getFieldName(),
            responseMapper.getRegexs());
      }
      if (responseMapper.getJsonPath() != null && responseMapper.getJsonPath().size() > 1) {
        for (int index = 1; index < responseMapper.getJsonPath().size(); index++) {
          String path = responseMapper.getJsonPath().get(index);
          if (!isEmpty(path)) {
            logsResponseParser.put(
                path.split("\\."), responseMapper.getFieldName() + index, responseMapper.getRegexs());
          }
        }
      }

      if (responseMapper.getFieldName().equals(TIMESTAMP_FIELD)) {
        if (isNotEmpty(responseMapper.getTimestampFormat())) {
          timestampFormat = responseMapper.getTimestampFormat();
        }
      }
    }
    List<Multimap<String, Object>> output = null;
    try {
      log.info("Response was {}", data.responseText);
      output = logsResponseParser.extract(data.responseText);
    } catch (Exception ex) {
      log.error("Unable to extract data in LogResponseParser {}", data.responseText);
    }
    createRecords(output, resultMap, timestampFormat, data.fixedHostName);
    // filter only the hosts we care about
    List<LogElement> logs = new ArrayList<>();
    if (resultMap.size() > 0 && data.shouldDoHostBasedFiltering) {
      for (LogElement logElement : resultMap.values()) {
        if (data.hostList.contains(logElement.getHost())) {
          logs.add(logElement);
        }
      }
    } else if (!data.shouldDoHostBasedFiltering) {
      logs.addAll(resultMap.values());
    }
    return logs;
  }

  private void createRecords(List<Multimap<String, Object>> response, Map<String, LogElement> resultMap,
      String timestampFormat, boolean fixedHostName) {
    if (response == null) {
      log.error("Something went wrong during parsing. Response is null.");
      return;
    }
    for (Multimap<String, Object> record : response) {
      Iterator<Object> timestamps = record.get(TIMESTAMP_FIELD).iterator();
      List<Iterator<Object>> logMessages = new ArrayList<>();
      logMessages.add(record.get("logMessage").iterator());
      for (int index = 1; index < MAX_CONCAT_JSON_FIELDS; index++) {
        if (!record.containsKey("logMessage" + index)) {
          break;
        }
        logMessages.add(record.get("logMessage" + index).iterator());
      }
      Iterator<Object> hostnames = record.get("host").iterator();
      String singleHostName = fixedHostName ? (String) hostnames.next() : null;
      while (timestamps.hasNext()) {
        // Figure out which form the timestamp is in
        long timestamp = 0;
        Object nextTimestamp = timestamps.next();
        try {
          timestamp = timestamps != null ? (long) VerificationResponseParser.cast(nextTimestamp, TIMESTAMP_FIELD) : 0;
          long now = Timestamp.currentMinuteBoundary();
          if (timestamp != 0 && String.valueOf(timestamp).length() < String.valueOf(now).length()) {
            // Timestamp is in seconds. Convert to millis
            timestamp = timestamp * 1000;
          } else if (String.valueOf(timestamp).length()
              == String.valueOf(TimeUnit.MILLISECONDS.toNanos(now)).length()) {
            timestamp = TimeUnit.NANOSECONDS.toMillis(timestamp);
          }
        } catch (WingsException ex) {
          String timestampStr = (String) nextTimestamp;
          try {
            timestamp = CustomDataCollectionUtils.parseTimestampfield(timestampStr, timestampFormat);
          } catch (ParseException e) {
            throw new DataCollectionException("Unable to parse date during data collection", e);
          }
        }

        String hostName;
        if (fixedHostName) {
          hostName = singleHostName;
        } else {
          hostName = record.containsKey("host") ? (String) hostnames.next() : null;
        }
        final String key = timestamp + ":" + hostName;
        if (!resultMap.containsKey(key)) {
          resultMap.put(key, new LogElement());
          resultMap.get(key).setTimeStamp(timestamp);
          resultMap.get(key).setCount(1);

          // We do this if we need to concatenate multiple json paths to the log message
          StringBuffer sBuf = new StringBuffer();
          for (Iterator<Object> logIter : logMessages) {
            Object msg = logIter.next();
            String msgStr = "";
            if (!(msg instanceof String)) {
              msgStr = msg.toString();
            } else {
              msgStr = (String) msg;
            }
            sBuf.append(msgStr);
            sBuf.append(", ");
          }
          resultMap.get(key).setLogMessage(sBuf.toString());

          resultMap.get(key).setHost(hostName);
        }
      }
    }
  }
}
