/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.exceptions;

import static io.harness.eraro.ErrorCode.INVALID_INPUT_SET;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;

@OwnedBy(HarnessTeam.PIPELINE)
public class InvalidInputSetException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  /*
  Having this field here is helpful in the case when an already saved input set is invalid (possible for remote input
  sets), so that when this exception is thrown, the saved input set entity is also available from the exception
   */
  InputSetEntity inputSetEntity;

  public InvalidInputSetException(String message, InputSetErrorWrapperDTOPMS errorResponseDTO) {
    super(message, null, INVALID_INPUT_SET, Level.ERROR, null, null, errorResponseDTO);
    super.param(MESSAGE_ARG, message);
  }

  public InvalidInputSetException(String message, InputSetErrorWrapperDTOPMS errorResponseDTO, InputSetEntity entity) {
    super(message, null, INVALID_INPUT_SET, Level.ERROR, null, null, errorResponseDTO);
    super.param(MESSAGE_ARG, message);
    inputSetEntity = entity;
  }
}
