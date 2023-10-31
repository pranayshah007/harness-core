/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngmigration.dto;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.MigrationTrackRespPayload;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ErrorDTOBase;

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(CDC)
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel(value = "Error")
@Schema(name = "Error", description = "This is Error entity as defined in Harness")
public class ErrorDTO extends MigrationTrackRespPayload implements ErrorDTOBase {
  Status status = Status.ERROR; // we won't rely on http codes, clients will figure out error/success with this field
  ErrorCode code; // enum representing what kind of an error this is (e.g.- secret management error)
  String message; // Short message, something which UI can display directly
  String correlationId; // for distributed tracing
  List<ResponseMessage> responseMessages; // for sending detailed list of response messages

  private ErrorDTO() {}

  protected ErrorDTO(Status status, ErrorCode code, String message) {
    this.status = status;
    this.code = code;
    this.message = message;
  }

  public static ErrorDTO newError(Status status, ErrorCode code, String message) {
    return new ErrorDTO(status, code, message);
  }
}
