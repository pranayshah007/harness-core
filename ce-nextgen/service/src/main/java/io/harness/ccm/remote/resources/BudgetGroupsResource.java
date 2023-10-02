/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.audittrails.events.BudgetGroup.BudgetGroupCreateEvent;
import io.harness.ccm.audittrails.events.BudgetGroup.BudgetGroupDeleteEvent;
import io.harness.ccm.audittrails.events.BudgetGroup.BudgetGroupUpdateEvent;
import io.harness.ccm.budget.BudgetSummary;
import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.ccm.budgetGroup.BudgetGroupSortType;
import io.harness.ccm.budgetGroup.service.BudgetGroupService;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.security.annotations.NextGenManagerAuth;

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
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Api("budgetGroups")
@Path("budgetGroups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost Budget Groups",
    description =
        "Manage Budget Groups and receive alerts when your costs exceed (or are forecasted to exceed) your configured budget group.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class BudgetGroupsResource {
  @Inject private BudgetGroupService budgetGroupService;
  @Inject private CCMRbacHelper rbacHelper;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private OutboxService outboxService;

  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @POST
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Create budget group", nickname = "createBudgetGroup")
  @Operation(operationId = "createBudgetGroup",
      description =
          "Create a Budget group to set and receive alerts when your costs exceed (or are forecasted to exceed) your budget group amount.",
      summary = "Create a Budget Group",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the ID string of the new Budget group created",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<String>
  save(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Budget Group definition") @NotNull @Valid BudgetGroup budgetGroup) {
    rbacHelper.checkBudgetEditPermission(accountId, null, null, null);
    String saveBudgetGroup = budgetGroupService.save(budgetGroup);
    BudgetGroup budgetGroupSaved = budgetGroupService.get(saveBudgetGroup, accountId);
    return ResponseDTO.newResponse(
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          outboxService.save(new BudgetGroupCreateEvent(accountId, budgetGroupSaved.toDTO()));
          return saveBudgetGroup;
        })));
  }

  @GET
  @Path("{id}")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get budget group", nickname = "getBudgetGroup")
  @Operation(operationId = "getBudgetGroup",
      description = "Fetch details of a Cloud Cost Budget group for the given Budget group ID.",
      summary = "Fetch Budget group details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Get a Budget group by it's identifier",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<BudgetGroup>
  get(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Unique identifier for the budget") @PathParam(
          "id") String budgetGroupId) {
    rbacHelper.checkBudgetViewPermission(accountId, null, null, null);
    return ResponseDTO.newResponse(budgetGroupService.get(budgetGroupId, accountId));
  }

  @GET
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "List Budget groups for account", nickname = "listBudgetGroups")
  @Operation(operationId = "listBudgetGroups", description = "List all the Cloud Cost Budget Groups for an account.",
      summary = "List all the Budget groups",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of all Budget groups",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<BudgetGroup>>
  list(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(description = "Budget Group List Sort Type") @QueryParam(
          "budgetGroupSortType") BudgetGroupSortType budgetGroupSortType,
      @Parameter(description = "Budget Group List Sort Order") @QueryParam("sortOrder") CCMSortOrder ccmSortOrder) {
    rbacHelper.checkBudgetViewPermission(accountId, null, null, null);
    return ResponseDTO.newResponse(budgetGroupService.list(accountId, budgetGroupSortType, ccmSortOrder));
  }

  @PUT
  @Path("{id}")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Update budget group", nickname = "updateBudgetGroup")
  @Operation(operationId = "updateBudgetGroup",
      description = "Update an existing Cloud Cost Budget group for the given Budget group ID.",
      summary = "Update an existing budget group",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns a generic string message when the operation is successful",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<String>
  update(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Valid @NotNull @Parameter(required = true, description = "Unique identifier for the budget group") @PathParam(
          "id") String budgetGroupId,
      @RequestBody(required = true, description = "The Budget object") @NotNull @Valid BudgetGroup budgetGroup) {
    rbacHelper.checkBudgetEditPermission(accountId, null, null, null);
    BudgetGroup oldBudgetGroup = budgetGroupService.get(budgetGroupId, accountId);
    budgetGroupService.update(budgetGroupId, accountId, budgetGroup);
    BudgetGroup updatedBudgetGroup = budgetGroupService.get(budgetGroupId, accountId);
    return ResponseDTO.newResponse(
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          outboxService.save(new BudgetGroupUpdateEvent(accountId, updatedBudgetGroup.toDTO(), oldBudgetGroup.toDTO()));
          return "Successfully updated the Budget group";
        })));
  }

  @DELETE
  @Path("{id}")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Delete budget group", nickname = "deleteBudgetGroup")
  @Operation(operationId = "deleteBudgetGroup",
      description = "Delete a Cloud Cost Budget group for the given Budget group ID.",
      summary = "Delete a budget group",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a boolean whether the operation was successful",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Boolean>
  delete(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @NotNull @Valid @Parameter(required = true, description = "Unique identifier for the budget") @PathParam(
          "id") String budgetGroupId) {
    rbacHelper.checkBudgetDeletePermission(accountId, null, null, null);
    BudgetGroup budgetGroup = budgetGroupService.get(budgetGroupId, accountId);
    boolean isBudgetGroupDeleted = budgetGroupService.delete(budgetGroupId, accountId);
    if (isBudgetGroupDeleted) {
      return ResponseDTO.newResponse(
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            outboxService.save(new BudgetGroupDeleteEvent(accountId, budgetGroup.toDTO()));
            return isBudgetGroupDeleted;
          })));
    }
    return ResponseDTO.newResponse(isBudgetGroupDeleted);
  }

  @POST
  @Path("aggregatedAmount")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get aggregated amount for given budget groups/budgets", nickname = "aggregatedAmount")
  @Operation(operationId = "getLastPeriodCost",
      description = "Returns list of value dataPoints specifying aggregated amount",
      summary = "Get aggregated amount for given budget groups/budgets",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns list of value dataPoints specifying aggregated amount",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<ValueDataPoint>>
  getAggregatedAmount(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @QueryParam("areChildEntitiesBudgets") @NotNull @Valid boolean areChildEntitiesBudgets,
      @RequestBody(required = true,
          description = "List of child budgets/budget groups") @NotNull @Valid List<String> childEntityIds) {
    rbacHelper.checkBudgetViewPermission(accountId, null, null, null);
    return ResponseDTO.newResponse(
        budgetGroupService.getAggregatedAmount(accountId, areChildEntitiesBudgets, childEntityIds));
  }

  @GET
  @Path("summary")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get list of budget and budget group summaries", nickname = "budgetAndBudgetGroupsList")
  @Operation(operationId = "getBudgetAndBudgetGroupsList", description = "Returns list of budgetSummary",
      summary = "Get list of budget and budget group summaries",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns list of value dataPoints specifying cost",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<BudgetSummary>>
  getBudgetAndBudgetGroupsList(
      @Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY)
      @AccountIdentifier @NotNull @Valid String accountId, @QueryParam("budgetGroupId") @Valid String budgetGroupId,
      @QueryParam("showAllEntities") @NotNull @Valid boolean showAllEntities,
      @Parameter(description = "Budget Group List Sort Type") @QueryParam(
          "budgetGroupSortType") BudgetGroupSortType budgetGroupSortType,
      @Parameter(description = "Budget Group List Sort Order") @QueryParam("sortOrder") CCMSortOrder ccmSortOrder) {
    rbacHelper.checkBudgetViewPermission(accountId, null, null, null);
    List<BudgetSummary> summaryList = showAllEntities
        ? budgetGroupService.listAllEntities(accountId, budgetGroupSortType, ccmSortOrder)
        : budgetGroupService.listBudgetsAndBudgetGroupsSummary(
            accountId, budgetGroupId, budgetGroupSortType, ccmSortOrder);
    return ResponseDTO.newResponse(summaryList);
  }
}
