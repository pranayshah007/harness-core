/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.LogRecordDTO;
import io.harness.cvng.core.entities.LogRecord;
import io.harness.cvng.core.entities.LogRecord.LogRecordKeys;
import io.harness.cvng.core.services.api.LogRecordService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class LogRecordServiceImplTest extends CvNextGenTestBase {
  @Inject private HPersistence hPersistence;
  @Inject private LogRecordService logRecordService;
  private String accountId;
  private long timestamp;
  private String verificationTaskId;
  @Before
  public void setup() {
    accountId = generateUuid();
    verificationTaskId = generateUuid();
    timestamp = Instant.now().toEpochMilli();
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSave() {
    List<LogRecordDTO> logRecordDTOs = IntStream.range(0, 3).mapToObj(index -> create()).collect(Collectors.toList());
    logRecordService.save(logRecordDTOs);
    List<LogRecord> logRecords = hPersistence.createQuery(LogRecord.class)
                                     .filter(LogRecordKeys.accountId, accountId)
                                     .filter(LogRecordKeys.verificationTaskId, verificationTaskId)
                                     .asList();
    assertThat(logRecords).hasSize(3);
    logRecords.forEach(logRecord -> {
      assertThat(logRecord.getVerificationTaskId()).isEqualTo(verificationTaskId);
      assertThat(logRecord.getAccountId()).isEqualTo(accountId);
      assertThat(logRecord.getHost()).isEqualTo("host");
      assertThat(logRecord.getLog()).isEqualTo("log message");
      assertThat(logRecord.getTimestamp()).isEqualTo(Instant.ofEpochMilli(timestamp));
    });
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLogRecords() {
    List<LogRecordDTO> logRecordDTOs = IntStream.range(0, 3).mapToObj(index -> create()).collect(Collectors.toList());
    logRecordService.save(logRecordDTOs);
    List<LogRecord> logRecords = logRecordService.getLogRecords(verificationTaskId, Instant.ofEpochMilli(timestamp),
        Instant.ofEpochMilli(timestamp).plus(Duration.ofMillis(1)));
    assertThat(logRecords).hasSize(3);
    logRecords.forEach(logRecord -> {
      assertThat(logRecord.getVerificationTaskId()).isEqualTo(verificationTaskId);
      assertThat(logRecord.getAccountId()).isEqualTo(accountId);
      assertThat(logRecord.getHost()).isEqualTo("host");
      assertThat(logRecord.getLog()).isEqualTo("log message");
      assertThat(logRecord.getTimestamp()).isEqualTo(Instant.ofEpochMilli(timestamp));
    });
  }

  private LogRecordDTO create() {
    return LogRecordDTO.builder()
        .verificationTaskId(verificationTaskId)
        .accountId(accountId)
        .timestamp(timestamp)
        .log("log message")
        .host("host")
        .build();
  }
}
