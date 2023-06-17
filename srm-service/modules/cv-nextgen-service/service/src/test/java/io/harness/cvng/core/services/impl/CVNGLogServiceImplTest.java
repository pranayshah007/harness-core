/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANSUMAN;
import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.KAPIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO.ApiCallLogDTOField;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.beans.cvnglog.CVNGLogType;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO.LogLevel;
import io.harness.cvng.beans.cvnglog.TraceableType;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.logsFilterParams.DeploymentLogsFilter;
import io.harness.cvng.core.beans.params.logsFilterParams.SLILogsFilter;
import io.harness.cvng.core.entities.CVNGLog;
import io.harness.cvng.core.entities.CVNGLog.CVNGLogKeys;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CV)
public class CVNGLogServiceImplTest extends CvNextGenTestBase {
  @Inject private HPersistence hPersistence;
  @Inject private CVNGLogService cvngLogService;

  private String accountId;
  private String traceableId;
  private long createdAt;
  private Instant requestTime;
  private Instant responseTime;
  private Instant startTime;
  private Instant endTime;

  @Before
  public void setup() throws IllegalAccessException {
    accountId = generateUuid();
    traceableId = generateUuid();
    requestTime = Instant.now();
    responseTime = Instant.now();
    startTime = TIME_FOR_TESTS.minusSeconds(5);
    endTime = TIME_FOR_TESTS;
    createdAt = Instant.now().toEpochMilli();
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testSave_verificationApiCallLogMultipleTraceableId() {
    List<CVNGLogDTO> cvngLogRecordsDTO =
        IntStream.range(0, 7).mapToObj(index -> createApiCallLogDTOVerification("200")).collect(Collectors.toList());
    traceableId = generateUuid();
    cvngLogRecordsDTO.addAll(
        IntStream.range(0, 3).mapToObj(index -> createApiCallLogDTOVerification("200")).collect(Collectors.toList()));
    cvngLogService.save(cvngLogRecordsDTO);
    List<CVNGLog> cvngLogs = hPersistence.createQuery(CVNGLog.class).filter(CVNGLogKeys.accountId, accountId).asList();
    assertThat(cvngLogs).hasSize(2);
    assertThat(cvngLogs.stream()
                   .filter(cvngLog -> !cvngLog.getTraceableId().equals(traceableId))
                   .findAny()
                   .get()
                   .getLogRecords())
        .hasSize(7);
    assertThat(cvngLogs.stream()
                   .filter(cvngLog -> cvngLog.getTraceableId().equals(traceableId))
                   .findAny()
                   .get()
                   .getLogRecords())
        .hasSize(3);
    cvngLogs.forEach(logRecord -> {
      assertThat(logRecord.getAccountId()).isEqualTo(accountId);
      assertThat(logRecord.getStartTime()).isEqualTo(startTime);
      assertThat(logRecord.getEndTime()).isEqualTo(endTime);
      assertThat(logRecord.getTraceableType()).isEqualTo(TraceableType.VERIFICATION_TASK);
    });
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testSave_verificationApiCallWithNullResponse() {
    ApiCallLogDTO apiCallLogDTONoResponse = ApiCallLogDTO.builder()
                                                .accountId(accountId)
                                                .traceableId(traceableId)
                                                .requestTime(requestTime.toEpochMilli())
                                                .requests(Arrays.asList(ApiCallLogDTOField.builder()
                                                                            .name("url")
                                                                            .value("http:/appd.com")
                                                                            .type(ApiCallLogDTO.FieldType.URL)
                                                                            .build()))
                                                .responses(Arrays.asList())
                                                .responseTime(responseTime.toEpochMilli())
                                                .startTime(startTime.toEpochMilli())
                                                .endTime(endTime.toEpochMilli())
                                                .createdAt(createdAt)
                                                .traceableType(TraceableType.VERIFICATION_TASK)
                                                .build();
    ApiCallLogDTO apiCallLogDTOOnlyStatus = ApiCallLogDTO.builder()
                                                .accountId(accountId)
                                                .traceableId(traceableId)
                                                .requestTime(requestTime.toEpochMilli())
                                                .requests(Arrays.asList(ApiCallLogDTOField.builder()
                                                                            .name("url")
                                                                            .value("http:/appd.com")
                                                                            .type(ApiCallLogDTO.FieldType.URL)
                                                                            .build()))
                                                .responses(Arrays.asList(ApiCallLogDTOField.builder()
                                                                             .name("Status Code")
                                                                             .value("500")
                                                                             .type(ApiCallLogDTO.FieldType.NUMBER)
                                                                             .build()))
                                                .responseTime(responseTime.toEpochMilli())
                                                .startTime(startTime.toEpochMilli())
                                                .endTime(endTime.toEpochMilli())
                                                .createdAt(createdAt)
                                                .traceableType(TraceableType.VERIFICATION_TASK)
                                                .build();
    cvngLogService.save(List.of(apiCallLogDTONoResponse, apiCallLogDTOOnlyStatus));
    List<CVNGLog> cvngLogs = hPersistence.createQuery(CVNGLog.class).filter(CVNGLogKeys.accountId, accountId).asList();
    assertThat(cvngLogs).hasSize(1);
    assertThat(cvngLogs.stream()
                   .filter(cvngLog -> cvngLog.getTraceableId().equals(traceableId))
                   .findAny()
                   .get()
                   .getLogRecords())
        .hasSize(2);
    cvngLogs.forEach(logRecord -> {
      assertThat(logRecord.getAccountId()).isEqualTo(accountId);
      assertThat(logRecord.getStartTime()).isEqualTo(startTime);
      assertThat(logRecord.getEndTime()).isEqualTo(endTime);
      assertThat(logRecord.getTraceableType()).isEqualTo(TraceableType.VERIFICATION_TASK);
    });
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testSave_onboardingApiCallLog() {
    List<CVNGLogDTO> cvngLogRecordsDTO =
        IntStream.range(0, 7).mapToObj(index -> createApiCallLogDTOOnboarding()).collect(Collectors.toList());
    cvngLogService.save(cvngLogRecordsDTO);
    List<CVNGLog> cvngLogs = hPersistence.createQuery(CVNGLog.class).filter(CVNGLogKeys.accountId, accountId).asList();
    assertThat(cvngLogs).hasSize(1);
    assertThat(cvngLogs.get(0).getLogRecords()).hasSize(7);
    cvngLogs.forEach(logRecord -> {
      assertThat(logRecord.getAccountId()).isEqualTo(accountId);
      assertThat(logRecord.getTraceableId()).isEqualTo(traceableId);
      assertThat(logRecord.getStartTime()).isEqualTo(startTime);
      assertThat(logRecord.getEndTime()).isEqualTo(endTime);
      assertThat(logRecord.getTraceableType()).isEqualTo(TraceableType.ONBOARDING);
    });
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testSave_addsAllFields() {
    List<CVNGLogDTO> cvngLogRecordsDTO =
        IntStream.range(0, 7).mapToObj(index -> createApiCallLogDTOOnboarding()).collect(Collectors.toList());
    cvngLogService.save(cvngLogRecordsDTO);
    List<CVNGLog> cvngLogs = hPersistence.createQuery(CVNGLog.class).filter(CVNGLogKeys.accountId, accountId).asList();
    cvngLogs.forEach(logRecord -> {
      List<Field> fields = ReflectionUtils.getAllDeclaredAndInheritedFields(CVNGLog.class);
      fields.forEach(field -> {
        try {
          field.setAccessible(true);
          assertThat(field.get(logRecord)).withFailMessage("field %s is null", field.getName()).isNotNull();
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      });
    });
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetOnboardingLogs() {
    List<CVNGLogDTO> cvngLogRecordsDTO =
        IntStream.range(0, 7).mapToObj(index -> createApiCallLogDTOOnboarding()).collect(Collectors.toList());
    cvngLogService.save(cvngLogRecordsDTO);
    PageResponse<CVNGLogDTO> cvngLogs =
        cvngLogService.getOnboardingLogs(accountId, traceableId, CVNGLogType.API_CALL_LOG, 0, 7);
    assertThat(cvngLogs.getContent()).hasSize(7);
    final int[] timeCounter = {0};
    final Long[] lastCreatedAt = {Long.MAX_VALUE};
    cvngLogs.getContent().sort(Comparator.comparing(log -> ((ApiCallLogDTO) log).getRequestTime()).reversed());
    cvngLogs.getContent().forEach(logRecord -> {
      assertThat(logRecord.getAccountId()).isEqualTo(accountId);
      assertThat(logRecord.getTraceableId()).isEqualTo(traceableId);
      assertThat(((ApiCallLogDTO) logRecord).getResponseTime())
          .isEqualTo(responseTime.minusSeconds(timeCounter[0]).toEpochMilli());
      assertThat(((ApiCallLogDTO) logRecord).getRequestTime())
          .isEqualTo(requestTime.minusSeconds(timeCounter[0]).toEpochMilli());
      assertThat(logRecord.getStartTime()).isEqualTo(startTime.toEpochMilli());
      assertThat(logRecord.getEndTime()).isEqualTo(endTime.toEpochMilli());
      assertThat(logRecord.getTraceableType()).isEqualTo(TraceableType.ONBOARDING);
      assertThat(logRecord.getType()).isEqualTo(CVNGLogType.API_CALL_LOG);
      assertThat(logRecord.getCreatedAt()).isLessThanOrEqualTo(lastCreatedAt[0]);
      lastCreatedAt[0] = logRecord.getCreatedAt();
      timeCounter[0] += 10;
    });
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCVNGLogs_forAPICallLogWithNoFilters() throws IllegalAccessException {
    List<CVNGLogDTO> cvngLogDTOs =
        IntStream.range(0, 3).mapToObj(index -> createApiCallLogDTOVerification("200")).collect(Collectors.toList());
    cvngLogService.save(cvngLogDTOs);
    Set<String> traceableIds =
        cvngLogDTOs.stream().map(cvngLogDTO -> cvngLogDTO.getTraceableId()).collect(Collectors.toSet());
    VerificationTaskService verificationTaskService = mock(VerificationTaskServiceImpl.class);
    FieldUtils.writeField(cvngLogService, "verificationTaskService", verificationTaskService, true);
    when(verificationTaskService.maybeGetVerificationTaskIds(any(), any())).thenReturn(traceableIds);

    DeploymentLogsFilter deploymentLogsFilter =
        DeploymentLogsFilter.builder().logType(CVNGLogType.API_CALL_LOG).errorLogsOnly(false).build();
    PageResponse<CVNGLogDTO> cvngLogDTOResponse = cvngLogService.getCVNGLogs(accountId,
        verificationTaskService.getVerificationJobInstanceId(traceableIds.iterator().next()), deploymentLogsFilter,
        PageParams.builder().page(0).size(10).build());

    assertThat(cvngLogDTOResponse.getContent().size()).isEqualTo(3);
    assertThat(cvngLogDTOResponse.getPageIndex()).isEqualTo(0);
    assertThat(cvngLogDTOResponse.getPageSize()).isEqualTo(10);

    List<CVNGLogDTO> cvngLogDTOsResult = cvngLogDTOResponse.getContent();
    List<ApiCallLogDTO> apiCallLogDTOS = new ArrayList<>();
    cvngLogDTOsResult.forEach(cvngLogDTO -> apiCallLogDTOS.add((ApiCallLogDTO) cvngLogDTO));

    assertThat(apiCallLogDTOS.size()).isEqualTo(3);
    apiCallLogDTOS.forEach(apiCallLogRecord -> {
      assertThat(apiCallLogRecord.getResponses().get(0).getValue()).isEqualTo("200");
      assertThat(apiCallLogRecord.getType()).isEqualTo(CVNGLogType.API_CALL_LOG);
      assertThat(apiCallLogRecord.getTraceableType()).isEqualTo(TraceableType.VERIFICATION_TASK);
    });
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCVNGLogs_forAPICallLogWithErrorLogsOnlyFilter() throws IllegalAccessException {
    List<CVNGLogDTO> cvngLogDTOs =
        IntStream.range(0, 2).mapToObj(index -> createApiCallLogDTOVerification("200")).collect(Collectors.toList());
    cvngLogDTOs.add(createApiCallLogDTOVerification("400"));
    cvngLogService.save(cvngLogDTOs);
    Set<String> traceableIds =
        cvngLogDTOs.stream().map(cvngLogDTO -> cvngLogDTO.getTraceableId()).collect(Collectors.toSet());
    VerificationTaskService verificationTaskService = mock(VerificationTaskServiceImpl.class);
    FieldUtils.writeField(cvngLogService, "verificationTaskService", verificationTaskService, true);
    when(verificationTaskService.maybeGetVerificationTaskIds(any(), any())).thenReturn(traceableIds);

    DeploymentLogsFilter deploymentLogsFilter =
        DeploymentLogsFilter.builder().logType(CVNGLogType.API_CALL_LOG).errorLogsOnly(true).build();
    PageResponse<CVNGLogDTO> cvngLogDTOResponse = cvngLogService.getCVNGLogs(accountId,
        verificationTaskService.getVerificationJobInstanceId(traceableIds.iterator().next()), deploymentLogsFilter,
        PageParams.builder().page(0).size(10).build());

    assertThat(cvngLogDTOResponse.getContent().size()).isEqualTo(1);
    assertThat(cvngLogDTOResponse.getPageIndex()).isEqualTo(0);
    assertThat(cvngLogDTOResponse.getPageSize()).isEqualTo(10);

    List<CVNGLogDTO> cvngLogDTOsResult = cvngLogDTOResponse.getContent();
    List<ApiCallLogDTO> apiCallLogDTOS = new ArrayList<>();
    cvngLogDTOsResult.forEach(cvngLogDTO -> apiCallLogDTOS.add((ApiCallLogDTO) cvngLogDTO));

    assertThat(apiCallLogDTOS.size()).isEqualTo(1);
    apiCallLogDTOS.forEach(apiCallLogRecord -> {
      assertThat(apiCallLogRecord.getResponses().get(0).getValue()).isNotEqualTo("200");
      assertThat(apiCallLogRecord.getType()).isEqualTo(CVNGLogType.API_CALL_LOG);
      assertThat(apiCallLogRecord.getTraceableType()).isEqualTo(TraceableType.VERIFICATION_TASK);
    });
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCVNGLogs_forExecutionLogWithNoFilters() throws IllegalAccessException {
    List<CVNGLogDTO> cvngLogDTOs = IntStream.range(0, 3)
                                       .mapToObj(index -> createExecutionLogDTOVerification(LogLevel.INFO))
                                       .collect(Collectors.toList());
    cvngLogService.save(cvngLogDTOs);
    Set<String> traceableIds =
        cvngLogDTOs.stream().map(cvngLogDTO -> cvngLogDTO.getTraceableId()).collect(Collectors.toSet());
    VerificationTaskService verificationTaskService = mock(VerificationTaskServiceImpl.class);
    FieldUtils.writeField(cvngLogService, "verificationTaskService", verificationTaskService, true);
    when(verificationTaskService.maybeGetVerificationTaskIds(any(), any())).thenReturn(traceableIds);

    DeploymentLogsFilter deploymentLogsFilter =
        DeploymentLogsFilter.builder().logType(CVNGLogType.EXECUTION_LOG).errorLogsOnly(false).build();
    PageResponse<CVNGLogDTO> cvngLogDTOResponse = cvngLogService.getCVNGLogs(accountId,
        verificationTaskService.getVerificationJobInstanceId(traceableIds.iterator().next()), deploymentLogsFilter,
        PageParams.builder().page(0).size(10).build());

    assertThat(cvngLogDTOResponse.getContent().size()).isEqualTo(3);
    assertThat(cvngLogDTOResponse.getPageIndex()).isEqualTo(0);
    assertThat(cvngLogDTOResponse.getPageSize()).isEqualTo(10);

    List<CVNGLogDTO> cvngLogDTOsResult = cvngLogDTOResponse.getContent();
    List<ExecutionLogDTO> executionLogDTOS = new ArrayList<>();
    cvngLogDTOsResult.forEach(cvngLogDTO -> executionLogDTOS.add((ExecutionLogDTO) cvngLogDTO));

    assertThat(executionLogDTOS.size()).isEqualTo(3);
    executionLogDTOS.forEach(executionLogDTO -> {
      assertThat(executionLogDTO.getLogLevel()).isEqualTo(LogLevel.INFO);
      assertThat(executionLogDTO.getType()).isEqualTo(CVNGLogType.EXECUTION_LOG);
      assertThat(executionLogDTO.getTraceableType()).isEqualTo(TraceableType.VERIFICATION_TASK);
    });
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCVNGLogs_forExecutionLogWithErrorLogsOnlyFilter() throws IllegalAccessException {
    List<CVNGLogDTO> cvngLogDTOs = IntStream.range(0, 2)
                                       .mapToObj(index -> createExecutionLogDTOVerification(LogLevel.INFO))
                                       .collect(Collectors.toList());
    cvngLogDTOs.add(createExecutionLogDTOVerification(LogLevel.ERROR));
    cvngLogService.save(cvngLogDTOs);
    Set<String> traceableIds =
        cvngLogDTOs.stream().map(cvngLogDTO -> cvngLogDTO.getTraceableId()).collect(Collectors.toSet());
    VerificationTaskService verificationTaskService = mock(VerificationTaskServiceImpl.class);
    FieldUtils.writeField(cvngLogService, "verificationTaskService", verificationTaskService, true);
    when(verificationTaskService.maybeGetVerificationTaskIds(any(), any())).thenReturn(traceableIds);

    DeploymentLogsFilter deploymentLogsFilter =
        DeploymentLogsFilter.builder().logType(CVNGLogType.EXECUTION_LOG).errorLogsOnly(true).build();
    PageResponse<CVNGLogDTO> cvngLogDTOResponse = cvngLogService.getCVNGLogs(accountId,
        verificationTaskService.getVerificationJobInstanceId(traceableIds.iterator().next()), deploymentLogsFilter,
        PageParams.builder().page(0).size(10).build());

    assertThat(cvngLogDTOResponse.getContent().size()).isEqualTo(1);
    assertThat(cvngLogDTOResponse.getPageIndex()).isEqualTo(0);
    assertThat(cvngLogDTOResponse.getPageSize()).isEqualTo(10);

    List<CVNGLogDTO> cvngLogDTOsResult = cvngLogDTOResponse.getContent();
    List<ExecutionLogDTO> executionLogDTOS = new ArrayList<>();
    cvngLogDTOsResult.forEach(cvngLogDTO -> executionLogDTOS.add((ExecutionLogDTO) cvngLogDTO));

    assertThat(executionLogDTOS.size()).isEqualTo(1);
    executionLogDTOS.forEach(executionLogDTO -> {
      assertThat(executionLogDTO.getLogLevel()).isEqualTo(LogLevel.ERROR);
      assertThat(executionLogDTO.getType()).isEqualTo(CVNGLogType.EXECUTION_LOG);
      assertThat(executionLogDTO.getTraceableType()).isEqualTo(TraceableType.VERIFICATION_TASK);
    });
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCVNGLogs_forAPICallLogWithTimeRangeFilter() throws IllegalAccessException {
    List<CVNGLogDTO> cvngLogDTOs = IntStream.range(0, 3)
                                       .mapToObj(index -> createApiCallLogDTOVerificationForTimeRangeFilter("200"))
                                       .collect(Collectors.toList());
    cvngLogService.save(cvngLogDTOs);
    Set<String> traceableIds =
        cvngLogDTOs.stream().map(cvngLogDTO -> cvngLogDTO.getTraceableId()).collect(Collectors.toSet());
    SLILogsFilter sliLogsFilter = SLILogsFilter.builder()
                                      .logType(CVNGLogType.API_CALL_LOG)
                                      .errorLogsOnly(false)
                                      .startTime(startTime.toEpochMilli())
                                      .endTime(endTime.toEpochMilli())
                                      .build();
    PageResponse<CVNGLogDTO> cvngLogDTOResponse =
        cvngLogService.getCVNGLogs(accountId, traceableIds.stream().collect(Collectors.toList()), sliLogsFilter,
            PageParams.builder().page(0).size(10).build());

    assertThat(cvngLogDTOResponse.getContent().size()).isEqualTo(1);
    assertThat(cvngLogDTOResponse.getPageIndex()).isEqualTo(0);
    assertThat(cvngLogDTOResponse.getPageSize()).isEqualTo(10);

    List<CVNGLogDTO> cvngLogDTOsResult = cvngLogDTOResponse.getContent();
    List<ApiCallLogDTO> apiCallLogDTOS = new ArrayList<>();
    cvngLogDTOsResult.forEach(cvngLogDTO -> apiCallLogDTOS.add((ApiCallLogDTO) cvngLogDTO));

    assertThat(apiCallLogDTOS.size()).isEqualTo(1);
    apiCallLogDTOS.forEach(apiCallLogRecord -> {
      assertThat(apiCallLogRecord.getResponses().get(1).getValue()).isEqualTo("200");
      assertThat(apiCallLogRecord.getType()).isEqualTo(CVNGLogType.API_CALL_LOG);
      assertThat(apiCallLogRecord.getTraceableType()).isEqualTo(TraceableType.VERIFICATION_TASK);
    });
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCVNGLogs_forAPICallLogWithErrorLogsOnlyFilterAndTimeRangeFilter() throws IllegalAccessException {
    List<CVNGLogDTO> cvngLogDTOs = IntStream.range(0, 2)
                                       .mapToObj(index -> createApiCallLogDTOVerificationForTimeRangeFilter("200"))
                                       .collect(Collectors.toList());
    cvngLogDTOs.add(createApiCallLogDTOVerification("400"));
    cvngLogService.save(cvngLogDTOs);
    Set<String> traceableIds =
        cvngLogDTOs.stream().map(cvngLogDTO -> cvngLogDTO.getTraceableId()).collect(Collectors.toSet());
    SLILogsFilter sliLogsFilter = SLILogsFilter.builder()
                                      .logType(CVNGLogType.API_CALL_LOG)
                                      .errorLogsOnly(true)
                                      .startTime(startTime.toEpochMilli())
                                      .endTime(endTime.toEpochMilli())
                                      .build();
    PageResponse<CVNGLogDTO> cvngLogDTOResponse =
        cvngLogService.getCVNGLogs(accountId, traceableIds.stream().collect(Collectors.toList()), sliLogsFilter,
            PageParams.builder().page(0).size(10).build());

    assertThat(cvngLogDTOResponse.getContent().size()).isEqualTo(1);
    assertThat(cvngLogDTOResponse.getPageIndex()).isEqualTo(0);
    assertThat(cvngLogDTOResponse.getPageSize()).isEqualTo(10);

    List<CVNGLogDTO> cvngLogDTOsResult = cvngLogDTOResponse.getContent();
    List<ApiCallLogDTO> apiCallLogDTOS = new ArrayList<>();
    cvngLogDTOsResult.forEach(cvngLogDTO -> apiCallLogDTOS.add((ApiCallLogDTO) cvngLogDTO));

    assertThat(apiCallLogDTOS.size()).isEqualTo(1);
    apiCallLogDTOS.forEach(apiCallLogRecord -> {
      assertThat(apiCallLogRecord.getResponses().get(1).getValue()).isNotEqualTo("200");
      assertThat(apiCallLogRecord.getType()).isEqualTo(CVNGLogType.API_CALL_LOG);
      assertThat(apiCallLogRecord.getTraceableType()).isEqualTo(TraceableType.VERIFICATION_TASK);
    });
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCVNGLogs_forExecutionLogWithTimeRangeFilter() throws IllegalAccessException {
    List<CVNGLogDTO> cvngLogDTOs = IntStream.range(0, 3)
                                       .mapToObj(index -> createExecutionLogDTOVerification(LogLevel.INFO))
                                       .collect(Collectors.toList());
    cvngLogService.save(cvngLogDTOs);
    Set<String> traceableIds =
        cvngLogDTOs.stream().map(cvngLogDTO -> cvngLogDTO.getTraceableId()).collect(Collectors.toSet());
    SLILogsFilter sliLogsFilter = SLILogsFilter.builder()
                                      .logType(CVNGLogType.EXECUTION_LOG)
                                      .errorLogsOnly(false)
                                      .startTime(startTime.toEpochMilli())
                                      .endTime(endTime.toEpochMilli())
                                      .build();
    PageResponse<CVNGLogDTO> cvngLogDTOResponse =
        cvngLogService.getCVNGLogs(accountId, traceableIds.stream().collect(Collectors.toList()), sliLogsFilter,
            PageParams.builder().page(0).size(10).build());

    assertThat(cvngLogDTOResponse.getContent().size()).isEqualTo(1);
    assertThat(cvngLogDTOResponse.getPageIndex()).isEqualTo(0);
    assertThat(cvngLogDTOResponse.getPageSize()).isEqualTo(10);

    List<CVNGLogDTO> cvngLogDTOsResult = cvngLogDTOResponse.getContent();
    List<ExecutionLogDTO> executionLogDTOS = new ArrayList<>();
    cvngLogDTOsResult.forEach(cvngLogDTO -> executionLogDTOS.add((ExecutionLogDTO) cvngLogDTO));

    assertThat(executionLogDTOS.size()).isEqualTo(1);
    executionLogDTOS.forEach(executionLogDTO -> {
      assertThat(executionLogDTO.getLogLevel()).isEqualTo(LogLevel.INFO);
      assertThat(executionLogDTO.getType()).isEqualTo(CVNGLogType.EXECUTION_LOG);
      assertThat(executionLogDTO.getTraceableType()).isEqualTo(TraceableType.VERIFICATION_TASK);
    });
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCVNGLogs_forExecutionLogWithErrorLogsOnlyFilterAndTimeRangeFilter() throws IllegalAccessException {
    List<CVNGLogDTO> cvngLogDTOs = IntStream.range(0, 2)
                                       .mapToObj(index -> createExecutionLogDTOVerification(LogLevel.INFO))
                                       .collect(Collectors.toList());
    cvngLogDTOs.add(createExecutionLogDTOVerification(LogLevel.ERROR));
    cvngLogService.save(cvngLogDTOs);
    Set<String> traceableIds =
        cvngLogDTOs.stream().map(cvngLogDTO -> cvngLogDTO.getTraceableId()).collect(Collectors.toSet());
    SLILogsFilter sliLogsFilter = SLILogsFilter.builder()
                                      .logType(CVNGLogType.EXECUTION_LOG)
                                      .errorLogsOnly(true)
                                      .startTime(startTime.toEpochMilli())
                                      .endTime(endTime.toEpochMilli())
                                      .build();
    PageResponse<CVNGLogDTO> cvngLogDTOResponse =
        cvngLogService.getCVNGLogs(accountId, traceableIds.stream().collect(Collectors.toList()), sliLogsFilter,
            PageParams.builder().page(0).size(10).build());

    assertThat(cvngLogDTOResponse.getContent().size()).isEqualTo(1);
    assertThat(cvngLogDTOResponse.getPageIndex()).isEqualTo(0);
    assertThat(cvngLogDTOResponse.getPageSize()).isEqualTo(10);

    List<CVNGLogDTO> cvngLogDTOsResult = cvngLogDTOResponse.getContent();
    List<ExecutionLogDTO> executionLogDTOS = new ArrayList<>();
    cvngLogDTOsResult.forEach(cvngLogDTO -> executionLogDTOS.add((ExecutionLogDTO) cvngLogDTO));

    assertThat(executionLogDTOS.size()).isEqualTo(1);
    executionLogDTOS.forEach(executionLogDTO -> {
      assertThat(executionLogDTO.getLogLevel()).isEqualTo(LogLevel.ERROR);
      assertThat(executionLogDTO.getType()).isEqualTo(CVNGLogType.EXECUTION_LOG);
      assertThat(executionLogDTO.getTraceableType()).isEqualTo(TraceableType.VERIFICATION_TASK);
    });
  }

  private CVNGLogDTO createApiCallLogDTOVerification(String responseCode) {
    requestTime = requestTime.plusSeconds(10);
    responseTime = responseTime.plusSeconds(10);
    return ApiCallLogDTO.builder()
        .accountId(accountId)
        .traceableId(traceableId)
        .requestTime(requestTime.toEpochMilli())
        .requests(Arrays.asList(
            ApiCallLogDTOField.builder().name("url").value("http:/appd.com").type(ApiCallLogDTO.FieldType.URL).build()))
        .responses(Arrays.asList(ApiCallLogDTOField.builder()
                                     .name("Status Code")
                                     .value(responseCode)
                                     .type(ApiCallLogDTO.FieldType.NUMBER)
                                     .build(),
            ApiCallLogDTOField.builder()
                .name("response body")
                .value("success")
                .type(ApiCallLogDTO.FieldType.JSON)
                .build()))
        .responseTime(responseTime.toEpochMilli())
        .startTime(startTime.toEpochMilli())
        .endTime(endTime.toEpochMilli())
        .createdAt(createdAt)
        .traceableType(TraceableType.VERIFICATION_TASK)
        .build();
  }

  private CVNGLogDTO createApiCallLogDTOVerificationForTimeRangeFilter(String responseCode) {
    requestTime = requestTime.plusSeconds(10);
    responseTime = responseTime.plusSeconds(10);
    startTime = startTime.plusSeconds(10);
    endTime = endTime.plusSeconds(10);
    return ApiCallLogDTO.builder()
        .accountId(accountId)
        .traceableId(traceableId)
        .requestTime(requestTime.toEpochMilli())
        .requests(Arrays.asList(
            ApiCallLogDTOField.builder().name("url").value("http:/appd.com").type(ApiCallLogDTO.FieldType.URL).build()))
        .responses(Arrays.asList(ApiCallLogDTOField.builder()
                                     .name("response body")
                                     .value("success")
                                     .type(ApiCallLogDTO.FieldType.JSON)
                                     .build(),
            ApiCallLogDTOField.builder()
                .name("Status Code")
                .value(responseCode)
                .type(ApiCallLogDTO.FieldType.NUMBER)
                .build()))
        .responseTime(responseTime.toEpochMilli())
        .startTime(startTime.toEpochMilli())
        .endTime(endTime.toEpochMilli())
        .createdAt(createdAt)
        .traceableType(TraceableType.VERIFICATION_TASK)
        .build();
  }

  private CVNGLogDTO createExecutionLogDTOVerification(LogLevel logLevel) {
    startTime = startTime.plusSeconds(10);
    endTime = endTime.plusSeconds(10);
    return ExecutionLogDTO.builder()
        .accountId(accountId)
        .traceableId(traceableId)
        .log("Data collection successful")
        .logLevel(logLevel)
        .startTime(startTime.toEpochMilli())
        .endTime(endTime.toEpochMilli())
        .createdAt(createdAt)
        .traceableType(TraceableType.VERIFICATION_TASK)
        .build();
  }

  private CVNGLogDTO createApiCallLogDTOOnboarding() {
    requestTime = requestTime.plusSeconds(10);
    responseTime = responseTime.plusSeconds(10);
    return ApiCallLogDTO.builder()
        .accountId(accountId)
        .traceableId(traceableId)
        .requestTime(requestTime.toEpochMilli())
        .responseTime(responseTime.toEpochMilli())
        .requests(Arrays.asList(
            ApiCallLogDTOField.builder().name("url").value("http:/appd.com").type(ApiCallLogDTO.FieldType.URL).build()))
        .responses(Arrays.asList(
            ApiCallLogDTOField.builder().name("Status Code").value("200").type(ApiCallLogDTO.FieldType.NUMBER).build(),
            ApiCallLogDTOField.builder()
                .name("response body")
                .value("success")
                .type(ApiCallLogDTO.FieldType.JSON)
                .build()))
        .startTime(startTime.toEpochMilli())
        .endTime(endTime.toEpochMilli())
        .createdAt(createdAt)
        .traceableType(TraceableType.ONBOARDING)
        .build();
  }
}
