/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.execution.utils;

import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.exception.FailureType.DELEGATE_RESTART;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.PIPELINE)
public class EngineExceptionUtils {
  public static EnumSet<FailureType> getOrchestrationFailureTypes(Throwable throwable) {
    EnumSet<io.harness.exception.FailureType> hFailureTypes = ExceptionUtils.getFailureTypes(throwable);
    return transformToOrchestrationFailureTypes(hFailureTypes);
  }

  public static EnumSet<FailureType> transformToOrchestrationFailureTypes(
      Collection<io.harness.exception.FailureType> hFailureTypes) {
    if (hFailureTypes == null) {
      hFailureTypes = Collections.emptyList();
    }
    EnumSet<FailureType> orchestrationFailureTypes = EnumSet.noneOf(FailureType.class);
    if (hFailureTypes.isEmpty()) {
      return orchestrationFailureTypes;
    }

    for (io.harness.exception.FailureType hFailureType : hFailureTypes) {
      orchestrationFailureTypes.add(mapToOrchestrationFailureType(hFailureType));
    }
    return orchestrationFailureTypes;
  }

  public static EnumSet<io.harness.exception.FailureType> transformToWingsFailureTypes(
      Collection<FailureType> oFailureTypes) {
    EnumSet<io.harness.exception.FailureType> wingsFailureType = EnumSet.noneOf(io.harness.exception.FailureType.class);
    if (oFailureTypes.isEmpty()) {
      return wingsFailureType;
    }

    for (FailureType oFailureType : oFailureTypes) {
      wingsFailureType.add(mapToWingsFailureType(oFailureType));
    }
    return wingsFailureType;
  }

  public static FailureInfo transformResponseMessagesToFailureInfo(List<ResponseMessage> responseMessages) {
    List<FailureData> failureDataList =
        responseMessages.stream()
            .map(rm
                -> FailureData.newBuilder()
                       .setCode(rm.getCode().name())
                       .setLevel(rm.getLevel().name())
                       .setMessage(emptyIfNull(rm.getMessage()))
                       .addAllFailureTypes(transformToOrchestrationFailureTypes(rm.getFailureTypes()))
                       .build())
            .collect(Collectors.toList());
    FailureInfo.Builder failureInfoBuilder = FailureInfo.newBuilder().addAllFailureData(failureDataList);
    if (!EmptyPredicate.isEmpty(failureDataList)) {
      FailureData failureData = failureDataList.get(failureDataList.size() - 1);
      failureInfoBuilder.setErrorMessage(emptyIfNull(failureData.getMessage()))
          .addAllFailureTypes(failureData.getFailureTypesList());
    }
    return failureInfoBuilder.build();
  }

  @VisibleForTesting
  static io.harness.exception.FailureType mapToWingsFailureType(FailureType oFailureType) {
    switch (oFailureType) {
      case TIMEOUT_FAILURE:
        return io.harness.exception.FailureType.EXPIRED;
      case UNRECOGNIZED:
      case UNKNOWN_FAILURE:
      case SKIPPING_FAILURE:
      case APPLICATION_FAILURE:
      case FREEZE_ACTIVE_FAILURE:
        return io.harness.exception.FailureType.APPLICATION_ERROR;
      case CONNECTIVITY_FAILURE:
        return io.harness.exception.FailureType.CONNECTIVITY;
      case VERIFICATION_FAILURE:
        return io.harness.exception.FailureType.VERIFICATION_FAILURE;
      case AUTHORIZATION_FAILURE:
        return io.harness.exception.FailureType.AUTHORIZATION_ERROR;
      case AUTHENTICATION_FAILURE:
        return io.harness.exception.FailureType.AUTHENTICATION;
      case DELEGATE_PROVISIONING_FAILURE:
        return io.harness.exception.FailureType.DELEGATE_PROVISIONING;
      case POLICY_EVALUATION_FAILURE:
        return io.harness.exception.FailureType.POLICY_EVALUATION_FAILURE;
      case INPUT_TIMEOUT_FAILURE:
        return io.harness.exception.FailureType.INPUT_TIMEOUT_FAILURE;
      case APPROVAL_REJECTION:
        return io.harness.exception.FailureType.APPROVAL_REJECTION;
      case DELEGATE_RESTART:
        return DELEGATE_RESTART;
      case USER_MARKED_FAILURE:
        return io.harness.exception.FailureType.USER_MARKED_FAILURE;
      default:
        throw new InvalidRequestException("No failure mapped to " + oFailureType.name());
    }
  }

  @VisibleForTesting
  static FailureType mapToOrchestrationFailureType(io.harness.exception.FailureType hFailureType) {
    switch (hFailureType) {
      case DELEGATE_PROVISIONING:
        return FailureType.DELEGATE_PROVISIONING_FAILURE;
      case CONNECTIVITY:
        return FailureType.CONNECTIVITY_FAILURE;
      case AUTHENTICATION:
        return FailureType.AUTHENTICATION_FAILURE;
      case VERIFICATION_FAILURE:
        return FailureType.VERIFICATION_FAILURE;
      case APPLICATION_ERROR:
        return FailureType.APPLICATION_FAILURE;
      case AUTHORIZATION_ERROR:
        return FailureType.AUTHORIZATION_FAILURE;
      case EXPIRED:
        return FailureType.TIMEOUT_FAILURE;
      case DELEGATE_RESTART:
        return FailureType.DELEGATE_RESTART;
      case USER_MARKED_FAILURE:
        return FailureType.USER_MARKED_FAILURE;
      default:
        return FailureType.UNKNOWN_FAILURE;
    }
  }
}
