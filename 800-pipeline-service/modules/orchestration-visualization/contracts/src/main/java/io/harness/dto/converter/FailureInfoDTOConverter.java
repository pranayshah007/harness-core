/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dto.converter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dto.FailureInfoDTO;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.execution.utils.EngineExceptionUtils;

import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class FailureInfoDTOConverter {
  public static FailureInfoDTO toFailureInfoDTO(FailureInfo failureInfo) {
    if (failureInfo == null) {
      return null;
    }
    return FailureInfoDTO.builder()
        .message(failureInfo.getErrorMessage())
        .failureTypeList(EngineExceptionUtils.transformToWingsFailureTypes(failureInfo.getFailureTypesList()))
        .responseMessages(
            failureInfo.getFailureDataList()
                .stream()
                .map(fd
                    -> ResponseMessage.builder()
                           .code(ErrorCode.valueOf(fd.getCode()))
                           .level(Level.valueOf(fd.getLevel()))
                           .failureTypes(EngineExceptionUtils.transformToWingsFailureTypes(fd.getFailureTypesList()))
                           .message(fd.getMessage())
                           .build())
                .collect(Collectors.toList()))
        .build();
  }
}
