/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.cvnglog.CVNGLogTag;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO.LogLevel;
import io.harness.cvng.beans.cvnglog.TraceableType;
import io.harness.cvng.core.entities.VerificationTaskExecutionInstance;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.ExecutionLogService;
import io.harness.cvng.core.services.api.ExecutionLogger;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;

public class ExecutionLogServiceImpl implements ExecutionLogService {
  @Inject private CVNGLogService cvngLogService;

  @Override
  public ExecutionLogger getLogger(VerificationTaskExecutionInstance verificationTaskExecutionInstance) {
    return ExecutionLoggerImpl.builder()
        .cvngLogService(cvngLogService)
        .accountId(verificationTaskExecutionInstance.getAccountId())
        .verificationTaskId(verificationTaskExecutionInstance.getVerificationTaskId())
        .startTime(verificationTaskExecutionInstance.getStartTime())
        .endTime(verificationTaskExecutionInstance.getEndTime())
        .build();
  }

  @Value
  @Builder
  private static class ExecutionLoggerImpl implements ExecutionLogger {
    CVNGLogService cvngLogService;
    String accountId;
    String verificationTaskId;
    Instant startTime;
    Instant endTime;

    @Override
    public void log(LogLevel logLevel, String message, String... messages) {
      ExecutionLogDTO executionLogDTO = getExecutionLogDTO(logLevel, message, messages);
      cvngLogService.save(Collections.singletonList(executionLogDTO));
    }

    private ExecutionLogDTO getExecutionLogDTO(LogLevel logLevel, String message, String[] messages) {
      String extraMessages = Arrays.stream(messages).filter(Objects::nonNull).collect(Collectors.joining(", "));
      return ExecutionLogDTO.builder()
          .accountId(accountId)
          .traceableId(verificationTaskId)
          .startTime(startTime.toEpochMilli())
          .endTime(endTime.toEpochMilli())
          .traceableType(TraceableType.VERIFICATION_TASK)
          .log(message + extraMessages)
          .logLevel(logLevel)
          .build();
    }

    @Override
    public void log(LogLevel logLevel, List<CVNGLogTag> cvngLogTags, String message, String... messages) {
      ExecutionLogDTO executionLogDTO = getExecutionLogDTO(logLevel, message, messages);
      List<CVNGLogTag> logTags = new ArrayList<>(CollectionUtils.emptyIfNull(executionLogDTO.getTags()));
      logTags.addAll(cvngLogTags);
      executionLogDTO.setTags(logTags);
      cvngLogService.save(Collections.singletonList(executionLogDTO));
    }
  }
}
