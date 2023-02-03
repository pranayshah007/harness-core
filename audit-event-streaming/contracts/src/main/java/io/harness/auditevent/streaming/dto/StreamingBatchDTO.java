/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.dto;

import io.harness.auditevent.streaming.beans.BatchFailureInfo;
import io.harness.auditevent.streaming.beans.BatchStatus;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "StreamingBatchDTOKeys")
public class StreamingBatchDTO {
  String id;
  @NotBlank String streamingDestinationIdentifier;
  @NotBlank String accountIdentifier;
  @NotNull Long startTime;
  @NotNull Long endTime;
  Long lastSuccessfulRecordTimestamp;
  Long numberOfRecords;
  Long numberOfRecordsPublished;
  Long lastStreamedAt;
  @NotNull BatchStatus status;
  int retryCount;
  @Valid BatchFailureInfo failureInfo;
  long createdAt;
  long lastModifiedAt;
}
