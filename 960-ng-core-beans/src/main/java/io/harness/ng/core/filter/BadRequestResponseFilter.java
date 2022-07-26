/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.filter;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.BadRequestDTO;
import io.harness.ng.core.dto.FailureDTO;

import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

@OwnedBy(PL)
@Provider
@Priority(Priorities.USER)
@Singleton
public class BadRequestResponseFilter implements ContainerResponseFilter {
  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    if (BAD_REQUEST.getStatusCode() == responseContext.getStatus() && responseContext.getEntity() instanceof FailureDTO
        && requestContext.getUriInfo().getPath().startsWith("v1")) {
      FailureDTO failureDTO = (FailureDTO) responseContext.getEntity();

      BadRequestDTO badRequestDTO = new BadRequestDTO();
      badRequestDTO.setError("Validation error");

      List<BadRequestDTO.BadRequestDetailDTO> details =
          failureDTO.getErrors()
              .stream()
              .map(failure -> new BadRequestDTO.BadRequestDetailDTO(failure.getError(), failure.getFieldId()))
              .collect(Collectors.toList());
      badRequestDTO.setDetail(details);

      responseContext.setEntity(badRequestDTO);
    }
  }
}
