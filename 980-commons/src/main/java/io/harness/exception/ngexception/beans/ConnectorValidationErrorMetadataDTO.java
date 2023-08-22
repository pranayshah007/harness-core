/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.ngexception.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.ngexception.ErrorMetadataConstants.CONNECTOR_VALIDATION_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ngexception.ErrorMetadataDTO;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(PL)
@JsonTypeName(CONNECTOR_VALIDATION_ERROR)
public class ConnectorValidationErrorMetadataDTO implements ErrorMetadataDTO {
  String taskId;

  @Builder
  public ConnectorValidationErrorMetadataDTO(String taskId) {
    this.taskId = taskId;
  }

  @Override
  public String getType() {
    return CONNECTOR_VALIDATION_ERROR;
  }
}
