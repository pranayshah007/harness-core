/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.exceptionmappers;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.utils.ExceptionUtils;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ErrorDTO;

import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class GenericExceptionMapperV2 implements ExceptionMapper<Throwable> {
  @Context private ResourceInfo resourceInfo;
  private static final String message = "Oops, something went wrong on our end. Please contact Harness Support.";

  private boolean hasExposeExceptionAnnotation() {
    return resourceInfo != null
        && ((resourceInfo.getResourceClass() != null
                && resourceInfo.getResourceClass().isAnnotationPresent(ExposeInternalException.class))
            || (resourceInfo.getResourceMethod() != null
                && resourceInfo.getResourceMethod().isAnnotationPresent(ExposeInternalException.class)));
  }

  @Override
  public Response toResponse(Throwable exception) {
    log.error("Exception occurred: " + ExceptionUtils.getMessage(exception), exception);
    ErrorDTO errorDto = ErrorDTO.newError(Status.ERROR, ErrorCode.UNKNOWN_ERROR, message);
    if (hasExposeExceptionAnnotation()) {
      errorDto.setDetailedMessage(exception.getMessage());
    }
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(errorDto)
        .type(MediaType.APPLICATION_JSON)
        .build();
  }
}
