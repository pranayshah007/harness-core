/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.streaming.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.streaming.outgoing.OutgoingAuditMessage;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@OwnedBy(HarnessTeam.PL)
public class AuditBatchDTO {
  @NotBlank String batchId;
  @NotBlank String accountIdentifier;
  @NotBlank String streamingDestinationIdentifier;
  @NotNull Long startTime;
  @NotNull Long endTime;
  @NotNull Integer numberOfRecords;
  @NotEmpty List<OutgoingAuditMessage> outgoingAuditMessages;
  BatchStatus status;
  Integer retryCount;

  @Data
  @SuperBuilder
  public static class BatchStatus {
    BatchState state;
    String message;
  }

  public enum BatchState {
    IN_PROGRESS,
    SUCCESS,
    FAILED;
  }
}
