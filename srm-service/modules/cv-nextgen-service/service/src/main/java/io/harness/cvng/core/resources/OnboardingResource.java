/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("onboarding")
@Path("/onboarding")
@Produces("application/json")
@NextGenManagerAuth
@ExposeInternalException
public class OnboardingResource {
  @Inject private OnboardingService onboardingService;

  @POST
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "onboarding api response", nickname = "getOnboardingResponse")
  public RestResponse<OnboardingResponseDTO> getOnboardingResponse(
      @QueryParam("accountId") @NotNull String accountId, OnboardingRequestDTO onboardingRequestDTO) {
    return new RestResponse<>(onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/ConnectorDisconnectHandler")
  @ApiOperation(value = "ConnectorDisconnectHandler api response", nickname = "validateConnector")
  public RestResponse<Void> validateConnector(@NotNull @BeanParam ProjectParams projectParams,
      @QueryParam("connectorIdentifier") @NotNull String connectorIdentifier,
      @QueryParam("tracingId") @NotNull String tracingId,
      @QueryParam("dataSourceType") @NotNull DataSourceType dataSourceType) {
    onboardingService.checkConnectivity(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
        projectParams.getProjectIdentifier(), connectorIdentifier, tracingId, dataSourceType);
    return new RestResponse<>(null);
  }
}
