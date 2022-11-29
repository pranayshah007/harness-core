/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.cvng.core.services.CVNextGenConstants.CVNG_LOG_RESOURCE_PATH;

import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.beans.cvnglog.CVNGLogType;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api(CVNG_LOG_RESOURCE_PATH)
@Path(CVNG_LOG_RESOURCE_PATH)
@Produces("application/json")
@ExposeInternalException
@OwnedBy(CV)
public class CVNGLogResource {
  @Inject private CVNGLogService cvngLogService;

  @POST
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "saves cvng log data", nickname = "saveCVNGLogRecords")
  public RestResponse<Void> saveCVNGLogRecords(
      @QueryParam("accountId") @NotNull String accountId, @NotNull @Valid @Body List<CVNGLogDTO> cvngLogDTORecords) {
    cvngLogService.save(cvngLogDTORecords);
    return new RestResponse<>(null);
  }

  @GET
  @Timed
  @ExceptionMetered
  @NextGenManagerAuth
  @ApiOperation(value = "gets onboarding api call logs", nickname = "getOnboardingLogs")
  public ResponseDTO<PageResponse<CVNGLogDTO>> getOnboardingLogs(@QueryParam("accountId") @NotNull String accountId,
      @QueryParam("traceableId") @NotNull String traceableId, @QueryParam("offset") @NotNull Integer offset,
      @QueryParam("pageSize") @NotNull Integer pageSize) {
    return ResponseDTO.newResponse(
        cvngLogService.getOnboardingLogs(accountId, traceableId, CVNGLogType.API_CALL_LOG, offset, pageSize));
  }
}
