/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.entities.budget.BudgetData;
import io.harness.ccm.graphql.core.budget.BudgetService;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryReporter;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("budgets")
@Path("budgets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost Budgets",
    description = "Manage Budgets and receive alerts when your costs exceed (or are forecasted to exceed) your budget.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class BudgetResource {
  @Inject private BudgetService budgetService;
  @Inject private CEViewService ceViewService;
  @Inject private TelemetryReporter telemetryReporter;

  private static final String BUDGET_CREATED = "Budget Created";
  private static final String MODULE = "module";
  private static final String MODULE_NAME = "CCM";
  private static final String BUDGET_PERIOD = "budget_period";
  private static final String BUDGET_TYPE = "budget_type";
  private static final String ALERTS_COUNT = "alerts_count";

  @POST
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Create budget", nickname = "createBudget")
  @Operation(operationId = "createBudget",
      description =
          "Create a Budget to set and receive alerts when your costs exceed (or are forecasted to exceed) your budget amount.",
      summary = "Create a Budget",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the ID string of the new Budget created",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<String>
  save(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Budget definition") @NotNull @Valid Budget budget) {
    budget.setAccountId(accountId);
    budget.setNgBudget(true);
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(BUDGET_PERIOD, budget.getPeriod());
    properties.put(BUDGET_TYPE, budget.getType());
    properties.put(ALERTS_COUNT, budget.getAlertThresholds().length);
    String createCall = budgetService.create(budget);
    telemetryReporter.sendTrackEvent(
        BUDGET_CREATED, null, accountId, properties, Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    return ResponseDTO.newResponse(createCall);
  }

  @POST
  @Path("{id}")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Clone budget", nickname = "cloneBudget")
  @Operation(operationId = "cloneBudget", description = "Clone a Cloud Cost Budget using the given Budget ID.",
      summary = "Clone a budget",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the identifier string of the new Budget created using clone operation",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<String>
  clone(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @PathParam("id") @Parameter(required = true, description = "Unique identifier for the budget") String budgetId,
      @QueryParam("cloneName") @Parameter(required = true, description = "Name of the new budget") String budgetName) {
    return ResponseDTO.newResponse(budgetService.clone(budgetId, budgetName, accountId));
  }

  @GET
  @Path("{id}")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get budget", nickname = "getBudget")
  @Operation(operationId = "getBudget", description = "Fetch details of a Cloud Cost Budget for the given Budget ID.",
      summary = "Fetch Budget details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Get a Budget by it's identifier",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Budget>
  get(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Unique identifier for the budget") @PathParam("id") String budgetId) {
    return ResponseDTO.newResponse(budgetService.get(budgetId, accountId));
  }

  @GET
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "List Budgets for account", nickname = "listBudgetsForAccount")
  @Operation(operationId = "listBudgets", description = "List all the Cloud Cost Budgets.",
      summary = "List all the Budgets",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of all Budgets",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<Budget>>
  list(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId) {
    return ResponseDTO.newResponse(budgetService.list(accountId));
  }

  @GET
  @Path("perspectiveBudgets")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "List Budgets for Perspective", nickname = "listBudgetsForPerspective")
  @Operation(operationId = "listBudgetsForPerspective",
      description = "List all the Cloud Cost Budgets associated for the given Perspective ID.",
      summary = "List all the Budgets associated with a Perspective",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the list of Budgets", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<Budget>>
  list(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Unique identifier for the Perspective") @QueryParam(
          "perspectiveId") String perspectiveId) {
    return ResponseDTO.newResponse(budgetService.list(accountId, perspectiveId));
  }

  @PUT
  @Path("{id}")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Update budget", nickname = "updateBudget")
  @Operation(operationId = "updateBudget",
      description = "Update an existing Cloud Cost Budget for the given Budget ID.",
      summary = "Update an existing budget",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns a generic string message when the operation is successful",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<String>
  update(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Valid @NotNull @Parameter(required = true, description = "Unique identifier for the budget") @PathParam("id")
      String budgetId, @RequestBody(required = true, description = "The Budget object") @NotNull @Valid Budget budget) {
    budgetService.update(budgetId, budget);
    return ResponseDTO.newResponse("Successfully updated the budget");
  }

  @DELETE
  @Path("{id}")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Delete budget", nickname = "deleteBudget")
  @Operation(operationId = "deleteBudget", description = "Delete a Cloud Cost Budget for the given Budget ID.",
      summary = "Delete a budget",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns a text message whether the operation was successful",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<String>
  delete(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @NotNull @Valid @Parameter(required = true, description = "Unique identifier for the budget") @PathParam(
          "id") String budgetId) {
    budgetService.delete(budgetId, accountId);
    return ResponseDTO.newResponse("Successfully deleted the budget");
  }

  @GET
  @Path("lastMonthCost")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Deprecated use /perspective/lastMonthCost instead, Get last month cost for Perspective.",
      nickname = "getLastMonthCost")
  @Operation(hidden = true)
  @Deprecated
  public ResponseDTO<Double>
  getLastMonthCost(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                       NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @QueryParam("perspectiveId") @Parameter(
          required = true, description = "The identifier of the Perspective") String perspectiveId) {
    return ResponseDTO.newResponse(ceViewService.getLastMonthCostForPerspective(accountId, perspectiveId));
  }

  @GET
  @Path("forecastCost")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Deprecated use /perspective/forecastCost instead, Get forecast cost for Perspective.",
      nickname = "getForecastCost")
  @Operation(hidden = true)
  @Deprecated
  public ResponseDTO<Double>
  getForecastCost(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                      NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam("perspectiveId") String perspectiveId) {
    return ResponseDTO.newResponse(ceViewService.getForecastCostForPerspective(accountId, perspectiveId));
  }

  @GET
  @Path("{id}/costDetails")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get cost details for budget", nickname = "getCostDetails")
  @Operation(operationId = "getCostDetails",
      description = "Fetch the cost details of a Cloud Cost Budget for the given Budget ID.",
      summary = "Fetch the cost details of a Budget",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the cost data of a Budget",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<BudgetData>
  getCostDetails(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                     NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Unique identifier for the Budget") @PathParam("id") String budgetId) {
    return ResponseDTO.newResponse(budgetService.getBudgetTimeSeriesStats(budgetService.get(budgetId, accountId)));
  }
}
