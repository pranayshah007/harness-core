/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.filter;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.ng.core.dto.ApiResponseDTO;
import io.harness.ng.core.dto.ErrorDTO;
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
public class ApiResponseFilter implements ContainerResponseFilter {
  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    if (!requestContext.getUriInfo().getPath().startsWith("v1")) {
      return;
    }
    ApiResponseDTO apiResponseDTO = new ApiResponseDTO();
    switch (responseContext.getStatus()) {
      case 400:
        if (isaBadRequest(responseContext)) {
          FailureDTO failureDTO = (FailureDTO) responseContext.getEntity();
          List<ApiResponseDTO.ApiResponseDetailDTO> details =
              failureDTO.getErrors()
                  .stream()
                  .map(failure -> new ApiResponseDTO.ApiResponseDetailDTO(failure.getError(), failure.getFieldId()))
                  .collect(Collectors.toList());
          apiResponseDTO.setError("Validation Error.");
          apiResponseDTO.setDetail(details);
        } else if (isaResourceNotFound(responseContext)) {
          FailureDTO failureDTO = (FailureDTO) responseContext.getEntity();
          apiResponseDTO.setError(String.format("Not Found. %s", failureDTO.getMessage()));
          responseContext.setStatus(404);
        } else {
          return;
        }
        break;
      case 401:
        if (!(responseContext.getEntity() instanceof ErrorDTO)) {
          return;
        }
        ErrorDTO errorDTO = (ErrorDTO) responseContext.getEntity();
        apiResponseDTO.setError(String.format("Unauthorized. %s", errorDTO.getMessage()));
        break;
      case 403:
        if (!(responseContext.getEntity() instanceof ErrorDTO)) {
          return;
        }
        errorDTO = (ErrorDTO) responseContext.getEntity();
        apiResponseDTO.setError(String.format("Forbidden Request. %s", errorDTO.getMessage()));
        break;
      case 404:
        apiResponseDTO.setError("Not Found.");
        break;
      case 412:
        apiResponseDTO.setError("Precondition Failed.");
        break;
      case 415:
        apiResponseDTO.setError("Unsupported Media Type.");
        break;
      case 500:
        apiResponseDTO.setError("Oops, something went wrong on our end. Please contact Harness Support.");
        break;
      default:
        return;
    }
    responseContext.setEntity(apiResponseDTO);
  }

  private boolean isaBadRequest(ContainerResponseContext responseContext) {
    if (!(responseContext.getEntity() instanceof FailureDTO)) {
      return false;
    }
    FailureDTO failureDTO = (FailureDTO) responseContext.getEntity();
    return ErrorCode.INVALID_REQUEST.equals(failureDTO.getCode());
  }

  private boolean isaResourceNotFound(ContainerResponseContext responseContext) {
    if (!(responseContext.getEntity() instanceof FailureDTO)) {
      return false;
    }
    FailureDTO failureDTO = (FailureDTO) responseContext.getEntity();
    return ErrorCode.RESOURCE_NOT_FOUND_EXCEPTION.equals(failureDTO.getCode());
  }
}