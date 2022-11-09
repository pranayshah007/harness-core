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
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.ErrorDTOBase;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.spec.server.commons.model.ErrorResponse;
import io.harness.spec.server.commons.model.FieldError;

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
    ErrorResponse errorResponse = new ErrorResponse();
    switch (responseContext.getStatus()) {
      case 400:
        if (isaBadRequest(responseContext)) {
          if (responseContext.getEntity() instanceof FailureDTO) {
            FailureDTO failureDTO = (FailureDTO) responseContext.getEntity();
            List<FieldError> fieldErrors = failureDTO.getErrors()
                                               .stream()
                                               .map(failure -> {
                                                 FieldError fieldError = new FieldError();
                                                 fieldError.setFieldName(failure.getFieldId());
                                                 fieldError.setMessage(failure.getError());
                                                 return fieldError;
                                               })
                                               .collect(Collectors.toList());
            errorResponse.setMessage("Validation Error.");
            errorResponse.setErrors(fieldErrors);
          } else if (responseContext.getEntity() instanceof ErrorDTO) {
            ErrorDTO errorDTO = (ErrorDTO) responseContext.getEntity();
            errorResponse.setMessage(errorDTO.getMessage());
          }
        } else if (isaResourceNotFound(responseContext)) {
          ErrorDTOBase errorDTOBase = (ErrorDTOBase) responseContext.getEntity();
          errorResponse.setMessage(String.format("Not Found. %s", errorDTOBase.getMessage()));
          responseContext.setStatus(404);
        } else if (isaDuplicateField(responseContext)) {
          ErrorDTOBase errorDTOBase = (ErrorDTOBase) responseContext.getEntity();
          errorResponse.setMessage(String.format("Duplicate Field. %s", errorDTOBase.getMessage()));
          responseContext.setStatus(409);
        } else {
          return;
        }
        break;
      case 401:
        if (!(responseContext.getEntity() instanceof ErrorDTO)) {
          return;
        }
        ErrorDTO errorDTO = (ErrorDTO) responseContext.getEntity();
        errorResponse.setMessage(String.format("Unauthorized. %s", errorDTO.getMessage()));
        break;
      case 403:
        if (!(responseContext.getEntity() instanceof ErrorDTO)) {
          return;
        }
        errorDTO = (ErrorDTO) responseContext.getEntity();
        errorResponse.setMessage(String.format("Forbidden Request. %s", errorDTO.getMessage()));
        break;
      case 404:
        errorResponse.setMessage("Not Found.");
        break;
      case 412:
        errorResponse.setMessage("Precondition Failed.");
        break;
      case 415:
        errorResponse.setMessage("Unsupported Media Type.");
        break;
      case 500:
        errorResponse.setMessage("Oops, something went wrong on our end. Please contact Harness Support.");
        break;
      default:
        return;
    }
    responseContext.setEntity(errorResponse);
  }

  private boolean isaBadRequest(ContainerResponseContext responseContext) {
    return responseContext.getEntity() instanceof ErrorDTOBase
        && ErrorCode.INVALID_REQUEST.equals(((ErrorDTOBase) responseContext.getEntity()).getCode());
  }

  private boolean isaResourceNotFound(ContainerResponseContext responseContext) {
    return responseContext.getEntity() instanceof ErrorDTOBase
        && ErrorCode.RESOURCE_NOT_FOUND_EXCEPTION.equals(((ErrorDTOBase) responseContext.getEntity()).getCode());
  }

  private boolean isaDuplicateField(ContainerResponseContext responseContext) {
    return responseContext.getEntity() instanceof ErrorDTOBase
        && ErrorCode.DUPLICATE_FIELD.equals(((ErrorDTOBase) responseContext.getEntity()).getCode());
  }
}