/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ngexception.ErrorMetadataDTO;
import io.harness.ng.core.CorrelationContext;
import io.harness.ng.core.Status;

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@OwnedBy(PL)
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel(value = "Error")
@Schema(name = "Error", description = "This is Error entity as defined in Harness")
public class ErrorDTO implements ErrorDTOBase {
  Status status = Status.ERROR; // we won't rely on http codes, clients will figure out error/success with this field
  ErrorCode code; // enum representing what kind of an error this is (e.g.- secret management error)
  String message; // Short message, something which UI can display directly
  String correlationId; // for distributed tracing
  String detailedMessage; // used to send detailed message in case of an error from Harness end for debugging
  List<ResponseMessage> responseMessages; // for sending detailed list of response messages
  ErrorMetadataDTO metadata;

  private ErrorDTO() {}

  protected ErrorDTO(Status status, ErrorCode code, String message, String detailedMessage) {
    this.status = status;
    this.code = code;
    this.message = message;
    this.detailedMessage = detailedMessage;
  }

  public static ErrorDTO newError(
      Status status, ErrorCode code, String message, String detailedMessage, ErrorMetadataDTO metadata) {
    ErrorDTO errorDto = new ErrorDTO();
    errorDto.setStatus(status);
    errorDto.setCode(code);
    errorDto.setMessage(message);
    errorDto.setDetailedMessage(detailedMessage);
    errorDto.setCorrelationId(CorrelationContext.getCorrelationId());
    errorDto.setResponseMessages(new ArrayList<>());
    errorDto.setMetadata(metadata);
    return errorDto;
  }

  public static ErrorDTO newError(Status status, ErrorCode code, String message) {
    return newError(status, code, message, null, null);
  }
}
