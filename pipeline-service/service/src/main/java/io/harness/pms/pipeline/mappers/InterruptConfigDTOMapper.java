/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import io.harness.data.structure.EmptyPredicate;
import io.harness.interrupts.AdviserIssuer;
import io.harness.interrupts.InterruptConfig;
import io.harness.interrupts.InterruptEffect;
import io.harness.interrupts.InterruptEffectDTO;
import io.harness.interrupts.IssuedBy;
import io.harness.interrupts.ManualIssuer;
import io.harness.interrupts.RetryInterruptConfig;
import io.harness.interrupts.TimeoutIssuer;
import io.harness.interrupts.TriggerIssuer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;

@UtilityClass
public class InterruptConfigDTOMapper {
  public static List<InterruptEffectDTO> toInterruptEffectDTOList(List<InterruptEffect> interruptHistories) {
    if (EmptyPredicate.isEmpty(interruptHistories)) {
      return new ArrayList<>();
    }
    List<InterruptEffectDTO> interruptEffectDTOList = new ArrayList<>();
    for (InterruptEffect interruptHistory : interruptHistories) {
      InterruptEffectDTO interruptEffectDTO = toInterruptEffectDTO(interruptHistory);
      interruptEffectDTOList.add(interruptEffectDTO);
    }
    return interruptEffectDTOList;
  }

  private static InterruptEffectDTO toInterruptEffectDTO(InterruptEffect interruptHistory) {
    if (interruptHistory == null) {
      return null;
    }

    return InterruptEffectDTO.builder()
        .interruptId(interruptHistory.getInterruptId())
        .interruptType(interruptHistory.getInterruptType())
        .tookEffectAt(interruptHistory.getTookEffectAt())
        .interruptConfig(toInterruptConfig(interruptHistory.getInterruptConfig()))
        .build();
  }

  private static InterruptConfig toInterruptConfig(
      io.harness.pms.contracts.interrupts.InterruptConfig interruptConfig) {
    if (interruptConfig == null) {
      return null;
    }

    return InterruptConfig.builder()
        .issuedBy(getIssuedBy(interruptConfig.getIssuedBy()))
        .retryInterruptConfig(getRetryInterruptConfig(interruptConfig.getRetryInterruptConfig()))
        .build();
  }

  private static IssuedBy getIssuedBy(io.harness.pms.contracts.interrupts.IssuedBy issuedBy) {
    if (issuedBy == null) {
      return null;
    }

    if (issuedBy.hasManualIssuer()) {
      return IssuedBy.builder()
          .issueTime(TimeUnit.SECONDS.toMillis(issuedBy.getIssueTime().getSeconds()))
          .manualIssuer(getManualIssuer(issuedBy.getManualIssuer()))
          .build();
    } else if (issuedBy.hasAdviserIssuer()) {
      return IssuedBy.builder()
          .issueTime(TimeUnit.SECONDS.toMillis(issuedBy.getIssueTime().getSeconds()))
          .adviserIssuer(getAdviserIssue(issuedBy.getAdviserIssuer()))
          .build();
    } else if (issuedBy.hasTimeoutIssuer()) {
      return IssuedBy.builder()
          .issueTime(TimeUnit.SECONDS.toMillis(issuedBy.getIssueTime().getSeconds()))
          .timeoutIssuer(getTimeoutIssuer(issuedBy.getTimeoutIssuer()))
          .build();
    } else {
      return IssuedBy.builder()
          .issueTime(TimeUnit.SECONDS.toMillis(issuedBy.getIssueTime().getSeconds()))
          .triggerIssuer(getTriggerIssuer(issuedBy.getTriggerIssuer()))
          .build();
    }
  }

  private static TriggerIssuer getTriggerIssuer(io.harness.pms.contracts.interrupts.TriggerIssuer triggerIssuer) {
    if (triggerIssuer == null) {
      return null;
    }
    return TriggerIssuer.builder()
        .abortPrevConcurrentExecution(triggerIssuer.getAbortPrevConcurrentExecution())
        .triggerRef(triggerIssuer.getTriggerRef())
        .build();
  }

  private static TimeoutIssuer getTimeoutIssuer(io.harness.pms.contracts.interrupts.TimeoutIssuer timeoutIssuer) {
    if (timeoutIssuer == null) {
      return null;
    }
    return TimeoutIssuer.builder().timeoutInstanceId(timeoutIssuer.getTimeoutInstanceId()).build();
  }

  private static ManualIssuer getManualIssuer(io.harness.pms.contracts.interrupts.ManualIssuer manualIssuer) {
    if (manualIssuer == null) {
      return null;
    }
    return ManualIssuer.builder()
        .identifier(manualIssuer.getIdentifier())
        .type(manualIssuer.getType())
        .email_id(manualIssuer.getEmailId())
        .user_id(manualIssuer.getUserId())
        .build();
  }

  private static AdviserIssuer getAdviserIssue(io.harness.pms.contracts.interrupts.AdviserIssuer adviserIssuer) {
    if (adviserIssuer == null) {
      return null;
    }
    return AdviserIssuer.builder().adviseType(adviserIssuer.getAdviserType()).build();
  }

  private static RetryInterruptConfig getRetryInterruptConfig(
      io.harness.pms.contracts.interrupts.RetryInterruptConfig retryInterruptConfig) {
    if (retryInterruptConfig == null) {
      return null;
    }
    return RetryInterruptConfig.builder().retryId(retryInterruptConfig.getRetryId()).build();
  }
}
