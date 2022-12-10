/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KANHAIYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO.ApiCallLogDTOField;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.beans.cvnglog.TraceableType;
import io.harness.cvng.core.entities.cvnglogs.ApiCallLogRecord;
import io.harness.cvng.core.entities.cvnglogs.CVNGLogRecord;
import io.harness.rule.Owner;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CV)
public class CVNGLogTest extends CategoryTest {
  private Instant requestTime;
  private Instant responseTime;
  private String accountId;
  private String traceableId;
  private Instant startTime;
  private Instant endTime;

  @Before
  public void setup() {
    accountId = generateUuid();
    traceableId = generateUuid();
    requestTime = TIME_FOR_TESTS;
    responseTime = TIME_FOR_TESTS.minusSeconds(5);
    startTime = TIME_FOR_TESTS;
    endTime = TIME_FOR_TESTS;
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testToCVNGLogDTOs() {
    List<CVNGLogRecord> logRecords =
        IntStream.range(0, 4).mapToObj(index -> createApiCallLogRecord()).collect(Collectors.toList());
    CVNGLog cvngLog = CVNGLog.builder()
                          .accountId(accountId)
                          .traceableId(traceableId)
                          .traceableType(TraceableType.VERIFICATION_TASK)
                          .startTime(startTime)
                          .endTime(endTime)
                          .logRecords(logRecords)
                          .build();
    List<CVNGLogDTO> cvngLogDTOS = cvngLog.toCVNGLogDTOs();
    assertThat(cvngLogDTOS).hasSize(4);
    cvngLogDTOS.forEach(logRecord -> {
      assertThat(logRecord.getAccountId()).isEqualTo(accountId);
      assertThat(((ApiCallLogDTO) logRecord).getRequestTime()).isEqualTo(requestTime.toEpochMilli());
      assertThat(((ApiCallLogDTO) logRecord).getResponseTime()).isEqualTo(responseTime.toEpochMilli());
      assertThat(logRecord.getTraceableId()).isEqualTo(traceableId);
      assertThat(logRecord.getStartTime()).isEqualTo(startTime.toEpochMilli());
      assertThat(logRecord.getEndTime()).isEqualTo(endTime.toEpochMilli());
      assertThat(logRecord.getTraceableType()).isEqualTo(TraceableType.VERIFICATION_TASK);
    });
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testToCVNGLogRecord_fromApiCallLogRecord() {
    String name = "default-name";
    String value = "default-value";
    ApiCallLogDTOField apiCallLogDTOField = ApiCallLogDTOField.builder().name(name).value(value).build();
    ApiCallLogDTO apiCallLogDTO = ApiCallLogDTO.builder()
                                      .requestTime(requestTime.toEpochMilli())
                                      .responseTime(responseTime.toEpochMilli())
                                      .build();
    apiCallLogDTO.addFieldToRequest(apiCallLogDTOField);
    apiCallLogDTO.addFieldToResponse(apiCallLogDTOField);

    CVNGLogRecord cvngLogRecord = CVNGLog.toCVNGLogRecord(apiCallLogDTO);
    assertThat(((ApiCallLogRecord) cvngLogRecord).getRequestTime()).isEqualTo(requestTime);
    assertThat(((ApiCallLogRecord) cvngLogRecord).getRequests().get(0).getName()).isEqualTo(name);
    assertThat(((ApiCallLogRecord) cvngLogRecord).getRequests()).hasSize(1);
    assertThat(((ApiCallLogRecord) cvngLogRecord).getResponses()).hasSize(1);
    assertThat(((ApiCallLogRecord) cvngLogRecord).getResponseTime()).isEqualTo(responseTime);
    assertThat(((ApiCallLogRecord) cvngLogRecord).getRequests().get(0).getValue()).isEqualTo(value);
  }

  private CVNGLogRecord createApiCallLogRecord() {
    return ApiCallLogRecord.builder().requestTime(requestTime).responseTime(responseTime).build();
  }
}
