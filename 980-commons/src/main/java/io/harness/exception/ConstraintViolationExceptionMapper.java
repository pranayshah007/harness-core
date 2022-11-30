/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.rest.RestResponse.Builder.aRestResponse;

import static java.util.stream.Collectors.toList;

import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.rest.RestResponse;
import io.harness.utils.constraintviolation.ConstraintViolationHandlerUtils;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
  @Override
  public Response toResponse(ConstraintViolationException exception) {
    ImmutableList<String> errors = ConstraintViolationHandlerUtils.getErrorMessages(exception);

    if (errors.isEmpty()) {
      errors = ImmutableList.of(Strings.nullToEmpty(exception.getMessage()));
    }

    log.info(toRestResponse(errors).toString());

    return Response.status(Status.BAD_REQUEST).entity(toRestResponse(errors)).build();
  }

  private RestResponse toRestResponse(ImmutableList<String> errors) {
    return aRestResponse()
        .withResponseMessages(errors.stream().map(this::errorMessageToResponseMessage).collect(toList()))
        .build();
  }

  private ResponseMessage errorMessageToResponseMessage(String s) {
    return ResponseMessage.builder().code(INVALID_ARGUMENT).level(Level.ERROR).message(s).build();
  }
}
