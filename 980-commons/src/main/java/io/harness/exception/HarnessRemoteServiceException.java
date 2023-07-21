/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.ErrorCode.INVALID_REQUEST;

import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ngexception.ErrorMetadataDTO;

import java.util.ArrayList;
import java.util.List;

public class HarnessRemoteServiceException extends WingsException implements HasResponseMessages {
  private List<ResponseMessage> responseMessages = new ArrayList<>();
  private static final String MESSAGE_ARG = "message";

  public HarnessRemoteServiceException(
      String message, ErrorMetadataDTO metadata, List<ResponseMessage> responseMessages) {
    super(message, null, INVALID_REQUEST, Level.ERROR, null, null, metadata);
    super.param(MESSAGE_ARG, message);
    this.responseMessages = responseMessages;
  }

  @Override
  public List<ResponseMessage> getResponseMessages() {
    if (EmptyPredicate.isEmpty(responseMessages)) {
      return new ArrayList<>(0);
    }
    return responseMessages;
  }
}
