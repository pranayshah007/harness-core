/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.rbac.CCMRbacHelperImpl.PERMISSION_MISSING_MESSAGE;
import static io.harness.ccm.rbac.CCMRbacHelperImpl.RESOURCE_FOLDER;
import static io.harness.ccm.rbac.CCMRbacPermissions.BUDGET_VIEW;
import static io.harness.ccm.rbac.CCMResources.BUDGET;
import static io.harness.ccm.remote.resources.TelemetryConstants.ALERTS_COUNT;
import static io.harness.ccm.remote.resources.TelemetryConstants.BUDGET_CREATED;
import static io.harness.ccm.remote.resources.TelemetryConstants.BUDGET_PERIOD;
import static io.harness.ccm.remote.resources.TelemetryConstants.BUDGET_TYPE;
import static io.harness.ccm.remote.resources.TelemetryConstants.MODULE;
import static io.harness.ccm.remote.resources.TelemetryConstants.MODULE_NAME;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.audittrails.events.BudgetCreateEvent;
import io.harness.ccm.audittrails.events.BudgetDeleteEvent;
import io.harness.ccm.audittrails.events.BudgetUpdateEvent;
import io.harness.ccm.budget.BudgetBreakdown;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.entities.budget.BudgetData;
import io.harness.ccm.graphql.core.budget.BudgetService;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.service.CEViewService;
import io.harness.exception.WingsException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryReporter;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.name.Named;
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
import java.util.Set;
import java.util.stream.Collectors;
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
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private OutboxService outboxService;
  @Inject CCMRbacHelper rbacHelper;

  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

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
    rbacHelper.checkBudgetEditPermission(
        accountId, null, null, ceViewService.get(BudgetUtils.getPerspectiveIdForBudget(budget)).getFolderId());
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
    return ResponseDTO.newResponse(
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          outboxService.save(new BudgetCreateEvent(accountId, budget.toDTO()));
          return createCall;
        })));
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
    Budget budget = budgetService.get(budgetId, accountId);
    rbacHelper.checkBudgetEditPermission(
        accountId, null, null, ceViewService.get(BudgetUtils.getPerspectiveIdForBudget(budget)).getFolderId());
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
    Budget budget = budgetService.get(budgetId, accountId);
    rbacHelper.checkBudgetViewPermission(
        accountId, null, null, ceViewService.get(BudgetUtils.getPerspectiveIdForBudget(budget)).getFolderId());
    return ResponseDTO.newResponse(budget);
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
    List<Budget> allBudgets = budgetService.list(accountId);
    List<String> perspectiveIds = allBudgets.stream()
                                      .filter(BudgetUtils::isPerspectiveBudget)
                                      .map(BudgetUtils::getPerspectiveIdForBudget)
                                      .collect(Collectors.toList());
    Set<String> folderIds = ceViewService.getPerspectiveFolderIds(accountId, perspectiveIds);
    HashMap<String, String> perspectiveIdAndFolderIds =
        ceViewService.getPerspectiveIdAndFolderId(accountId, perspectiveIds);

    List<Budget> allowedBudgets = null;
    if (folderIds != null) {
      Set<String> allowedFolderIds =
          rbacHelper.checkFolderIdsGivenPermission(accountId, null, null, folderIds, BUDGET_VIEW);
      allowedBudgets = allBudgets.stream()
                           .filter(budget
                               -> BudgetUtils.isPerspectiveBudget(budget)
                                   && allowedFolderIds.contains(
                                       perspectiveIdAndFolderIds.get(BudgetUtils.getPerspectiveIdForBudget(budget))))
                           .collect(Collectors.toList());
    }

    if ((allowedBudgets == null || allowedBudgets.size() == 0) && allBudgets.size() > 0) {
      throw new NGAccessDeniedException(
          String.format(PERMISSION_MISSING_MESSAGE, BUDGET_VIEW, RESOURCE_FOLDER), WingsException.USER, null);
    }
    return ResponseDTO.newResponse(allowedBudgets);
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
    CEView perspective = ceViewService.get(perspectiveId);
    List<Budget> budgets = null;
    if (perspective != null) {
      rbacHelper.checkBudgetViewPermission(accountId, null, null, perspective.getFolderId());
      budgets = budgetService.list(accountId, perspectiveId);
    }
    return ResponseDTO.newResponse(budgets);
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
    rbacHelper.checkBudgetEditPermission(
        accountId, null, null, ceViewService.get(BudgetUtils.getPerspectiveIdForBudget(budget)).getFolderId());
    Budget oldBudget = budgetService.get(budgetId, accountId);
    budget.setParentBudgetGroupId(oldBudget.getParentBudgetGroupId());
    budgetService.update(budgetId, budget, oldBudget);
    Budget newBudget = budgetService.get(budgetId, accountId);
    return ResponseDTO.newResponse(
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          outboxService.save(new BudgetUpdateEvent(accountId, newBudget.toDTO(), oldBudget.toDTO()));
          return "Successfully updated the budget";
        })));
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
    Budget budget = budgetService.get(budgetId, accountId);
    rbacHelper.checkBudgetDeletePermission(
        accountId, null, null, ceViewService.get(BudgetUtils.getPerspectiveIdForBudget(budget)).getFolderId());
    budgetService.delete(budgetId, accountId);
    return ResponseDTO.newResponse(
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          outboxService.save(new BudgetDeleteEvent(accountId, budget.toDTO()));
          return "Successfully deleted the budget";
        })));
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
    rbacHelper.checkBudgetViewPermission(accountId, null, null, ceViewService.get(perspectiveId).getFolderId());
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
    rbacHelper.checkBudgetViewPermission(accountId, null, null, ceViewService.get(perspectiveId).getFolderId());
    return ResponseDTO.newResponse(ceViewService.getForecastCostForPerspective(accountId, perspectiveId));
  }

  @GET
  @Path("{id}/costDetails")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @NGAccessControlCheck(resourceType = BUDGET, permission = BUDGET_VIEW)
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
      @Parameter(required = true, description = "Unique identifier for the Budget") @PathParam("id") String budgetId,
      @Parameter(description = "MONTHLY/YEARLY breakdown. The default value is YEARLY") @QueryParam(
          "breakdown") BudgetBreakdown breakdown) {
    Budget budget = budgetService.get(budgetId, accountId);
    rbacHelper.checkBudgetViewPermission(
        accountId, null, null, ceViewService.get(BudgetUtils.getPerspectiveIdForBudget(budget)).getFolderId());
    return ResponseDTO.newResponse(budgetService.getBudgetTimeSeriesStats(
        budgetService.get(budgetId, accountId), breakdown == null ? BudgetBreakdown.YEARLY : breakdown));
  }
}
