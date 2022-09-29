/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.exceptionmappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.FailureDTO;

import javax.ws.rs.NotSupportedException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class NotSupportedExceptionMapper implements ExceptionMapper<NotSupportedException> {
  @Override
  public Response toResponse(NotSupportedException exception) {
    log.error("Exception occurred: " + ExceptionUtils.getMessage(exception), exception);
    FailureDTO failureDTO =
        FailureDTO.toBody(Status.FAILURE, ErrorCode.MEDIA_NOT_SUPPORTED, exception.getMessage(), null);
    return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE)
        .entity(failureDTO)
        .type(MediaType.APPLICATION_JSON)
        .build();
  }
}