/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.audittrails.events.CostCategoryCreateEvent;
import io.harness.ccm.audittrails.events.CostCategoryDeleteEvent;
import io.harness.ccm.audittrails.events.CostCategoryUpdateEvent;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.BusinessMappingListDTO;
import io.harness.ccm.views.businessmapping.entities.CostCategorySortType;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.dto.CostCategoryDeleteDTO;
import io.harness.ccm.views.dto.CostCategoryDeleteDTO.CostCategoryDeleteDTOBuilder;
import io.harness.ccm.views.dto.LinkedPerspectives;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.fabric8.utils.Lists;
import io.fabric8.utils.Maps;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.List;
import javax.validation.Valid;
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
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Api("business-mapping")
@Path("/business-mapping")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost Cost Categories",
    description = "Allows you to categorize based on business requirements and get a contextual view of your expenses.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class BusinessMappingResource {
  @Inject BusinessMappingService businessMappingService;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private OutboxService outboxService;
  @Inject CCMRbacHelper rbacHelper;
  @Inject CEViewService ceViewService;

  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private static final String COST_CATEGORY_NOT_DELETED = "Cost Category exists in listed perspective(s)";
  private static final String COST_CATEGORY_DELETED = "Cost category is safely deleted";

  @POST
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Create Business Mapping", nickname = "createBusinessMapping")
  @Operation(operationId = "createBusinessMapping",
      description = "Create Cost category that allows you to categorize based on business requirements and "
          + "get a contextual view of your expenses",
      summary = "Create Cost category",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns a created Cost category object with all the cost and shared buckets",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public RestResponse<BusinessMapping>
  save(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, @Valid BusinessMapping businessMapping) {
    rbacHelper.checkCostCategoryEditPermission(accountId, null, null);
    BusinessMapping costCategory = businessMappingService.save(businessMapping);
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new CostCategoryCreateEvent(accountId, costCategory.toDTO()));
      return true;
    }));
    return new RestResponse<>(costCategory);
  }

  @GET
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get List Of Business Mappings", nickname = "getBusinessMappingList")
  @Operation(operationId = "getBusinessMappingList",
      description = "Return details of all the Cost categories for the given account ID.",
      summary = "Return details of all the Cost categories",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns a List of Cost Categories",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public RestResponse<BusinessMappingListDTO>
  list(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.SEARCH_KEY) String searchKey,
      @QueryParam(NGCommonEntityConstants.SORT_TYPE) CostCategorySortType sortType,
      @QueryParam(NGCommonEntityConstants.SORT_ORDER) CCMSortOrder sortOrder,
      @QueryParam(NGCommonEntityConstants.LIMIT) Integer limit,
      @QueryParam(NGCommonEntityConstants.OFFSET) Integer offset) {
    rbacHelper.checkCostCategoryViewPermission(accountId, null, null);
    return new RestResponse<>(businessMappingService.list(accountId, searchKey, sortType, sortOrder, limit, offset));
  }

  @GET
  @Path("{id}")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get Business Mapping", nickname = "getBusinessMapping")
  @Operation(operationId = "getBusinessMapping",
      description = "Fetch details of a Cost category for the given Cost category ID.",
      summary = "Fetch details of a Cost category",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns a Cost category object with all the cost and shared buckets, "
                + "returns null if no Cost category exists for that particular identifier",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public RestResponse<BusinessMapping>
  get(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, @PathParam("id") String businessMappingId) {
    rbacHelper.checkCostCategoryViewPermission(accountId, null, null);
    return new RestResponse<>(businessMappingService.get(businessMappingId, accountId));
  }

  @PUT
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Update Business Mapping", nickname = "updateBusinessMapping")
  @Operation(operationId = "updateBusinessMapping",
      description = "Update a Cost category. "
          + "It accepts a BusinessMapping object and upserts it using the uuid mentioned in the definition.",
      summary = "Update a Cost category",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Successfully updated the Business Mapping",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public RestResponse<String>
  update(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, BusinessMapping businessMapping) {
    rbacHelper.checkCostCategoryEditPermission(accountId, null, null);
    BusinessMapping oldCostCategory = businessMappingService.get(businessMapping.getUuid(), accountId);
    BusinessMapping newCostCategory = businessMappingService.update(businessMapping, oldCostCategory);
    if (!oldCostCategory.getName().equals(newCostCategory.getName())) {
      ceViewService.updateBusinessMappingName(accountId, newCostCategory.getUuid(), newCostCategory.getName());
    }
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new CostCategoryUpdateEvent(accountId, newCostCategory.toDTO(), oldCostCategory.toDTO()));
      return true;
    }));
    return new RestResponse<>("Successfully updated the Business Mapping");
  }

  @DELETE
  @Path("{id}")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Delete Business Mapping", nickname = "deleteBusinessMapping")
  @Operation(operationId = "deleteBusinessMapping",
      description = "Delete a Cost category for the given Cost category ID.", summary = "Delete a Cost category",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "A string text message whether the delete was successful or not. "
                + "If the cost category is used in the perspective, "
                + "the deletion will fail and it will send you the IDs of all the linked perspectives",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public RestResponse<CostCategoryDeleteDTO>
  delete(@NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @PathParam("id") String businessMappingId) {
    rbacHelper.checkCostCategoryDeletePermission(accountId, null, null);
    LinkedPerspectives perspectiveListMessage =
        ceViewService.getViewsByBusinessMapping(accountId, Collections.singletonList(businessMappingId)).get(0);
    CostCategoryDeleteDTOBuilder costCategoryDeleteDTOBuilder =
        CostCategoryDeleteDTO.builder()
            .linkedPerspectives(perspectiveListMessage.getPerspectiveIdAndName())
            .deleted(false)
            .message(COST_CATEGORY_NOT_DELETED);

    // No dependency on cost category(business mapping) exists
    if (Maps.isNullOrEmpty(perspectiveListMessage.getPerspectiveIdAndName())) {
      BusinessMapping costCategory = businessMappingService.get(businessMappingId, accountId);
      businessMappingService.delete(businessMappingId, accountId);
      costCategoryDeleteDTOBuilder.deleted(true).message(COST_CATEGORY_DELETED);
      Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        outboxService.save(new CostCategoryDeleteEvent(accountId, costCategory.toDTO()));
        return true;
      }));
    }
    return new RestResponse<>(costCategoryDeleteDTOBuilder.build());
  }

  @POST
  @Path("linkedPerspectives")
  @Timed
  @Hidden
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(
      value = "Get related Perspectives given Cost Category Ids", nickname = "getPerspectivesGivenBusinessMappingIds")
  @Operation(operationId = "getPerspectivesGivenBusinessMappingIds",
      description = "Given Cost Category Id, find out Perspectives where Cost Category is present in Rules or Group By",
      summary = "Return list of perspectives corresponding to Cost Category Ids",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Return list of perspectives corresponding to Cost Category Ids",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public RestResponse<List<LinkedPerspectives>>
  getPerspectives(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(
          required = true, description = "List of Business Mapping Ids") @Valid List<String> businessMappingIds) {
    rbacHelper.checkCostCategoryViewPermission(accountId, null, null);
    List<LinkedPerspectives> perspectiveListMessages = null;
    if (!Lists.isNullOrEmpty(businessMappingIds)) {
      perspectiveListMessages = ceViewService.getViewsByBusinessMapping(accountId, businessMappingIds);
    }
    return new RestResponse<>(perspectiveListMessages);
  }
}
