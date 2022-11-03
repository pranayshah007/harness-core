/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import io.harness.eraro.ErrorCode;
import io.harness.ng.core.CorrelationContext;
import io.harness.ng.core.Status;
import io.harness.ng.core.ValidationError;

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel(value = "Failure")
@Schema(name = "Failure", description = "This is Failure entity as defied in Harness")
public class FailureDTO implements ErrorDTOBase {
  Status status = Status.FAILURE; // we won't rely on http codes, clients will figure out error/success with this field
  ErrorCode code; // enum representing what kind of an error this is (e.g.- secret management error)
  String message; // Short message, something which UI can display directly
  String correlationId;
  List<ValidationError> errors; // used for denoting which fields of the request have errors

  private FailureDTO() {}

  public static FailureDTO toBody(
      Status status, ErrorCode code, String message, List<ValidationError> validationErrors) {
    FailureDTO failureDto = new FailureDTO();
    failureDto.setStatus(status);
    failureDto.setCode(code);
    failureDto.setMessage(message);
    failureDto.setCorrelationId(CorrelationContext.getCorrelationId());
    failureDto.setErrors(validationErrors);
    return failureDto;
  }
}
