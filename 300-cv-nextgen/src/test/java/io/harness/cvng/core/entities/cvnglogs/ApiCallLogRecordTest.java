/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities.cvnglogs;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KANHAIYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO.ApiCallLogDTOField;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.core.entities.cvnglogs.ApiCallLogRecord.ApiCallLogField;
import io.harness.rule.Owner;

import java.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CV)
public class ApiCallLogRecordTest extends CategoryTest {
  private String name;
  private String value;
  private Instant requestTime;
  private Instant responseTime;

  @Before
  public void setup() {
    this.name = generateUuid();
    this.value = generateUuid();
    this.requestTime = TIME_FOR_TESTS.minusSeconds(5);
    this.responseTime = TIME_FOR_TESTS;
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testAddFieldToRequest_withNullField() {
    ApiCallLogRecord apiCallLogRecord = ApiCallLogRecord.builder().build();
    assertThatThrownBy(() -> apiCallLogRecord.addFieldToRequest(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Api call log request field is null.");
  }

  @Test()
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testAddFieldToRequest_withEmptyRequest() {
    ApiCallLogRecord apiCallLogRecord = ApiCallLogRecord.builder().build();
    ApiCallLogField field = ApiCallLogField.builder().name(name).value(value).build();
    apiCallLogRecord.addFieldToRequest(field);
    assertThat(apiCallLogRecord.getRequests()).hasSize(1);
    assertThat(apiCallLogRecord.getRequests().get(0).getName()).isEqualTo(name);
    assertThat(apiCallLogRecord.getRequests().get(0).getValue()).isEqualTo(value);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testAddFieldToResponse_withNullField() {
    ApiCallLogRecord apiCallLogRecord = ApiCallLogRecord.builder().build();
    assertThatThrownBy(() -> apiCallLogRecord.addFieldToResponse(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Api call log response field is null.");
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testAddFieldToResponse_withEmptyResponse() {
    ApiCallLogRecord apiCallLogRecord = ApiCallLogRecord.builder().build();
    ApiCallLogField field = ApiCallLogField.builder().name(name).value(value).build();
    apiCallLogRecord.addFieldToResponse(field);
    assertThat(apiCallLogRecord.getResponses()).hasSize(1);
    assertThat(apiCallLogRecord.getResponses().get(0).getName()).isEqualTo(name);
    assertThat(apiCallLogRecord.getResponses().get(0).getValue()).isEqualTo(value);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testToCVNGLogRecord() {
    ApiCallLogDTOField apiCallLogDTOField = ApiCallLogDTOField.builder().name(name).value(value).build();
    ApiCallLogDTO apiCallLogDTO = ApiCallLogDTO.builder()
                                      .requestTime(requestTime.toEpochMilli())
                                      .responseTime(responseTime.toEpochMilli())
                                      .build();
    apiCallLogDTO.addFieldToRequest(apiCallLogDTOField);
    apiCallLogDTO.addFieldToResponse(apiCallLogDTOField);

    CVNGLogRecord cvngLogRecord = ApiCallLogRecord.toCVNGLogRecord(apiCallLogDTO);
    assertThat(((ApiCallLogRecord) cvngLogRecord).getRequestTime()).isEqualTo(requestTime);
    assertThat(((ApiCallLogRecord) cvngLogRecord).getResponseTime()).isEqualTo(responseTime);
    assertThat(((ApiCallLogRecord) cvngLogRecord).getRequests()).hasSize(1);
    assertThat(((ApiCallLogRecord) cvngLogRecord).getResponses()).hasSize(1);
    assertThat(((ApiCallLogRecord) cvngLogRecord).getRequests().get(0).getName()).isEqualTo(name);
    assertThat(((ApiCallLogRecord) cvngLogRecord).getRequests().get(0).getValue()).isEqualTo(value);
    assertThat(((ApiCallLogRecord) cvngLogRecord).getResponses().get(0).getName()).isEqualTo(name);
    assertThat(((ApiCallLogRecord) cvngLogRecord).getResponses().get(0).getValue()).isEqualTo(value);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testToCVNGLogDTO() {
    ApiCallLogRecord apiCallLogRecord =
        ApiCallLogRecord.builder().requestTime(requestTime).responseTime(responseTime).build();
    ApiCallLogField apiCallLogField = ApiCallLogField.builder().name(name).value(value).build();
    apiCallLogRecord.addFieldToRequest(apiCallLogField);
    apiCallLogRecord.addFieldToResponse(apiCallLogField);
    CVNGLogDTO cvngLogDTO = apiCallLogRecord.toCVNGLogDTO();
    assertThat(((ApiCallLogDTO) cvngLogDTO).getRequestTime()).isEqualTo(requestTime.toEpochMilli());
    assertThat(((ApiCallLogDTO) cvngLogDTO).getResponseTime()).isEqualTo(responseTime.toEpochMilli());
    assertThat(((ApiCallLogDTO) cvngLogDTO).getRequests()).hasSize(1);
    assertThat(((ApiCallLogDTO) cvngLogDTO).getResponses()).hasSize(1);
    assertThat(((ApiCallLogDTO) cvngLogDTO).getRequests().get(0).getName()).isEqualTo(name);
    assertThat(((ApiCallLogDTO) cvngLogDTO).getRequests().get(0).getValue()).isEqualTo(value);
    assertThat(((ApiCallLogDTO) cvngLogDTO).getResponses().get(0).getName()).isEqualTo(name);
    assertThat(((ApiCallLogDTO) cvngLogDTO).getResponses().get(0).getValue()).isEqualTo(value);
  }
}
