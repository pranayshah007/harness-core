/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.*;
import io.harness.ccm.service.intf.BigQueryOrchestratorService;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.PublicApi;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import java.util.List;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;

@Api("big-query")
@Path("big-query")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PublicApi
@Slf4j
@Service
@OwnedBy(CE)
public class BigQueryResource {
    @Inject
    BigQueryOrchestratorService bigQueryOrchestratorService;

    @GET
    @Path("total-cost")
    @Timed
    @LogAccountIdentifier
    @ExceptionMetered
    public ResponseDTO<Double>
    getTotalCost(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId) {
        return ResponseDTO.newResponse(bigQueryOrchestratorService.getTotalCost());
    }

    @GET
    @Path("scanned-bytes")
    @Timed
    @LogAccountIdentifier
    @ExceptionMetered
    public ResponseDTO<Double>
    getScannedBytes(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId) {
        return ResponseDTO.newResponse(bigQueryOrchestratorService.getBytesScanned());
    }

    @GET
    @Path("successful-queries")
    @Timed
    @LogAccountIdentifier
    @ExceptionMetered
    public ResponseDTO<Double>
    getSuccessfulQueries(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId) {
        return ResponseDTO.newResponse(bigQueryOrchestratorService.getSuccessfulQueries());
    }

    @GET
    @Path("failed-queries")
    @Timed
    @LogAccountIdentifier
    @ExceptionMetered
    public ResponseDTO<Double>
    getFailedQueries(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId) {
        return ResponseDTO.newResponse(bigQueryOrchestratorService.getFailedQueries());
    }

    @GET
    @Path("time-series-visibility")
    @Timed
    @LogAccountIdentifier
    @ExceptionMetered
    public ResponseDTO<List<BQOrchestratorVisibilityDataPoint>>
    getTimeSeries(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId) {
        return ResponseDTO.newResponse(bigQueryOrchestratorService.getVisibilityTimeSeries());
    }

    @GET
    @Path("expensive-queries")
    @Timed
    @LogAccountIdentifier
    @ExceptionMetered
    public ResponseDTO<List<BQOrchestratorExpensiveQueryPoint>>
    getExpensiveQueries(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId) {
        return ResponseDTO.newResponse(bigQueryOrchestratorService.getExpensiveQueries());
    }

    @GET
    @Path("slot-usage")
    @Timed
    @LogAccountIdentifier
    @ExceptionMetered
    public ResponseDTO<List<BQOrchestratorSlotsDataPoint>>
    getSlotData(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId) {
        return ResponseDTO.newResponse(bigQueryOrchestratorService.getSlotData());
    }

    @GET
    @Path("slot-stats")
    @Timed
    @LogAccountIdentifier
    @ExceptionMetered
    public ResponseDTO<BQOrchestratorSlotUsageStats>
    getSlotStats(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId, @QueryParam("optimizationType")
            BQOrchestratorOptimizationType optimizationType, @QueryParam("commitmentDuration") BQOrchestratorCommitmentDuration commitmentDuration, @QueryParam("slotCount") Double slotCount) {
        return ResponseDTO.newResponse(bigQueryOrchestratorService.getSlotUsageStats(optimizationType, commitmentDuration, slotCount));
    }
}
