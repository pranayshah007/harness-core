/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.validator;

import static io.harness.eraro.ErrorCode.SCHEMA_VALIDATION_FAILED;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;

import lombok.Getter;
import lombok.Setter;

@OwnedBy(HarnessTeam.PIPELINE)
public class InvalidYamlException extends WingsException {
  private static final String MESSAGE_ARG = "message";
  @Getter @Setter String yaml;

  public InvalidYamlException(String message, YamlSchemaErrorWrapperDTO errorResponseDTO) {
    super(message, null, SCHEMA_VALIDATION_FAILED, Level.ERROR, null, null, errorResponseDTO);
    super.param(MESSAGE_ARG, message);
  }

  public InvalidYamlException(String message, Throwable cause, YamlSchemaErrorWrapperDTO errorResponseDTO) {
    super(message, cause, SCHEMA_VALIDATION_FAILED, Level.ERROR, null, null, errorResponseDTO);
    super.param(MESSAGE_ARG, message);
  }

  public InvalidYamlException(
      String message, Throwable cause, YamlSchemaErrorWrapperDTO errorResponseDTO, String invalidYaml) {
    super(message, cause, SCHEMA_VALIDATION_FAILED, Level.ERROR, null, null, errorResponseDTO);
    super.param(MESSAGE_ARG, message);
    yaml = invalidYaml;
  }
}
