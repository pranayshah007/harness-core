/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.resources;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateFeedbackDTO;
import io.harness.delegate.service.intfc.DelegateFeedbacksService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.rest.RestResponse;

import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.AuthRule;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.RequiredArgsConstructor;
import retrofit2.http.Body;

@Api("/delegate-feedbacks")
@Path("/delegate-feedbacks")
@Produces("application/json")
@Consumes({"application/json"})
@OwnedBy(HarnessTeam.DEL)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class DelegateFeedbacksResource {
  private final DelegateFeedbacksService delegateFeedbacksService;
  @POST
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<Boolean> addFeedback(
      @QueryParam("accountId") @NotEmpty String accountId, @Body @NotNull DelegateFeedbackDTO delegateFeedbackDTO) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      delegateFeedbacksService.persistFeedback(accountId, delegateFeedbackDTO);
      return new RestResponse<>(true);
    }
  }
}
