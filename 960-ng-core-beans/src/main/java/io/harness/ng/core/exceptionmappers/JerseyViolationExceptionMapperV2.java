/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.exceptionmappers;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.ng.core.Status;
import io.harness.ng.core.ValidationError;
import io.harness.ng.core.dto.FailureDTO;

import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Path;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class JerseyViolationExceptionMapperV2 implements ExceptionMapper<JerseyViolationException> {
  @Context ResourceInfo resourceInfo;

  @Override
  public Response toResponse(JerseyViolationException exception) {
    log.error("Exception occurred: " + ExceptionUtils.getMessage(exception), exception);
    Set<ConstraintViolation<?>> constraintViolations = exception.getConstraintViolations();

    List<ValidationError> validationErrors = new ArrayList<>();
    constraintViolations.forEach(constraintViolation -> {
      String field = null;
      for (Path.Node node : constraintViolation.getPropertyPath()) {
        field = node.getName();
      }
      validationErrors.add(ValidationError.of(field, constraintViolation.getMessage()));
    });
    String exceptionMessage =
        validationErrors.stream()
            .map(validationError -> validationError.getFieldId() + ": " + validationError.getError())
            .collect(Collectors.joining(", "));
    FailureDTO failureDto =
        FailureDTO.toBody(Status.FAILURE, ErrorCode.INVALID_REQUEST, exceptionMessage, validationErrors);
    return Response.status(Response.Status.BAD_REQUEST).entity(failureDto).type(MediaType.APPLICATION_JSON).build();
  }
}
