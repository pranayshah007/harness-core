/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.cvng.CVNGRequestExecutor;
import io.harness.cvng.beans.LogRecordDTO;
import io.harness.datacollection.entity.LogDataRecord;
import io.harness.verificationclient.CVNextGenServiceClient;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class LogRecordDataStoreService {
  @Inject private CVNextGenServiceClient cvNextGenServiceClient;
  @Inject private CVNGRequestExecutor cvngRequestExecutor;

  public void save(String accountId, String verificationTaskId, List<LogDataRecord> logRecords) {
    cvngRequestExecutor
        .executeWithTimeout(cvNextGenServiceClient.saveLogRecords(accountId,
                                logRecords.stream()
                                    .map(logDataRecord -> toLogRecordDTO(accountId, verificationTaskId, logDataRecord))
                                    .collect(Collectors.toList())),
            Duration.ofSeconds(30))
        .getResource();
  }

  private LogRecordDTO toLogRecordDTO(String accountId, String verificationTaskId, LogDataRecord logDataRecord) {
    return LogRecordDTO.builder()
        .accountId(accountId)
        .host(logDataRecord.getHostname())
        .verificationTaskId(verificationTaskId)
        .timestamp(logDataRecord.getTimestamp())
        .log(logDataRecord.getLog())
        .build();
  }
}
