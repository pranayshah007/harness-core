/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.beans.LogRecordDTO;
import io.harness.cvng.core.beans.demo.DemoTemplate;
import io.harness.cvng.core.entities.LogCVConfig;
import io.harness.cvng.core.entities.LogRecord;
import io.harness.cvng.core.entities.LogRecord.LogRecordKeys;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.LogRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.demo.CVNGDemoDataIndexService;
import io.harness.persistence.HPersistence;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterators;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
@Singleton
public class LogRecordServiceImpl implements LogRecordService {
  @Inject private HPersistence hPersistence;
  @Inject private CVNGDemoDataIndexService cvngDemoDataIndexService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private CVConfigService cvConfigService;
  @Override
  public void save(List<LogRecordDTO> logRecords) {
    saveRecords(logRecords.stream().map(this::toLogRecord).collect(Collectors.toList()));
  }

  @Override
  public List<LogRecord> getLogRecords(String verificationTaskId, Instant startTime, Instant endTime) {
    return hPersistence.createQuery(LogRecord.class, excludeAuthority)
        .filter(LogRecordKeys.verificationTaskId, verificationTaskId)
        .field(LogRecordKeys.timestamp)
        .greaterThanOrEq(startTime)
        .field(LogRecordKeys.timestamp)
        .lessThan(endTime)
        .asList();
  }

  private void saveRecords(List<LogRecord> logRecords) {
    hPersistence.saveBatch(logRecords);
  }
  private LogRecord toLogRecord(LogRecordDTO logRecordDTO) {
    return LogRecord.builder()
        .accountId(logRecordDTO.getAccountId())
        .verificationTaskId(logRecordDTO.getVerificationTaskId())
        .host(logRecordDTO.getHost())
        .log(logRecordDTO.getLog())
        .timestamp(Instant.ofEpochMilli(logRecordDTO.getTimestamp()))
        .build();
  }

  @Override
  public void createDemoAnalysisData(String accountId, String verificationTaskId, String dataCollectionWorkerId,
      DemoTemplate demoTemplate, Instant startTime, Instant endTime) throws IOException {
    List<LogRecordDTO> logRecordsToBeSaved = new ArrayList<>();
    Instant time = startTime;

    String demoTemplatePath = getDemoTemplate(verificationTaskId, endTime, demoTemplate);
    List<List<LogRecordDTO>> logRecordsList =
        JsonUtils.asObject(demoTemplatePath, new TypeReference<List<List<LogRecordDTO>>>() {});
    int index = cvngDemoDataIndexService.readIndexForDemoData(accountId, dataCollectionWorkerId, verificationTaskId);
    while (time.compareTo(endTime) < 0) {
      if (index >= logRecordsList.size()) {
        index = index % logRecordsList.size();
      }
      List<LogRecordDTO> logRecordsDTOAtTime = logRecordsList.get(index);
      for (LogRecordDTO logRecordDTO : logRecordsDTOAtTime) {
        logRecordDTO.setAccountId(accountId);
        logRecordDTO.setVerificationTaskId(verificationTaskId);
        logRecordDTO.setTimestamp(time.toEpochMilli());
      }
      if (demoTemplate.isHighRisk()) {
        List<String> newLogMessages = new ArrayList<>();
        Map<Thread, StackTraceElement[]> stacktraces = Thread.getAllStackTraces();
        if (!stacktraces.isEmpty()) {
          int randomStacktraceIndex = new Random().nextInt(stacktraces.size());
          Map.Entry<Thread, StackTraceElement[]> stacktrace =
              Iterators.get(stacktraces.entrySet().iterator(), randomStacktraceIndex);

          newLogMessages.add("java.lang.RuntimeException: \n"
              + String.join("\n",
                  Arrays.stream(stacktrace.getValue())
                      .map(stackTraceElement -> stackTraceElement.toString())
                      .collect(Collectors.toList())));
        }
        newLogMessages.add("java.lang.RuntimeException: \n" + UUID.randomUUID().toString()
            + " Method throws runtime exception " + UUID.randomUUID().toString());

        for (Instant instant = startTime; instant.isBefore(endTime); instant = instant.plus(Duration.ofMinutes(1))) {
          int freq = new Random().nextInt(5) + 1;
          for (int i = 0; i < freq; i++) {
            for (String newLogMessage : newLogMessages) {
              logRecordsDTOAtTime.add(LogRecordDTO.builder()
                                          .accountId(accountId)
                                          .verificationTaskId(verificationTaskId)
                                          .host("verification-svc-canary-58589fd55f")
                                          .timestamp(instant.toEpochMilli())
                                          .log(newLogMessage)
                                          .build());
            }
          }
        }
      }
      logRecordsToBeSaved.addAll(logRecordsDTOAtTime);
      index++;
      time = time.plus(1, ChronoUnit.MINUTES);
    }
    cvngDemoDataIndexService.saveIndexForDemoData(accountId, dataCollectionWorkerId, verificationTaskId, index);
    save(logRecordsToBeSaved);
  }

  private String getDemoTemplate(String verificationTaskId, Instant endTime, DemoTemplate demoTemplate)
      throws IOException {
    String demoTemplateIdentifier = demoTemplate.getDemoTemplateIdentifier();
    if (verificationTaskService.isServiceGuardId(verificationTaskId)) {
      LogCVConfig cvConfig =
          (LogCVConfig) cvConfigService.get(verificationTaskService.getCVConfigId(verificationTaskId));
      if (cvConfig.getBaseline().getEndTime().isAfter(endTime)) {
        // use default template if time range is before baseline.
        demoTemplateIdentifier = "default";
      }
    }
    String path = "/io/harness/cvng/analysis/liveMonitoring/logs/$template_demo_template.json";
    path = path.replace("$template", demoTemplateIdentifier);
    return Resources.toString(this.getClass().getResource(path), Charsets.UTF_8);
  }
}
