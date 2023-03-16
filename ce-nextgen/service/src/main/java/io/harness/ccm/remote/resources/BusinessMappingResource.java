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
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.dto.CostCategoryDeleteDTO;
import io.harness.ccm.views.dto.CostCategoryDeleteDTO.CostCategoryDeleteDTOBuilder;
import io.harness.ccm.views.dto.LinkedPerspectives;
import io.harness.ccm.views.service.CEViewService;
import io.harness.exception.InvalidRequestException;
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
import io.swagger.v3.oas.annotations.parameters.RequestBody;
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
@Produces("application/json")
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
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
  @ExceptionMetered
  @ApiOperation(value = "Create Business Mapping", nickname = "createBusinessMapping")
  public RestResponse<BusinessMapping> save(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, BusinessMapping businessMapping) {
    rbacHelper.checkCostCategoryEditPermission(accountId, null, null);
    if (!businessMappingService.isNamePresent(businessMapping.getName(), businessMapping.getAccountId())) {
      throw new InvalidRequestException("Cost category name already exists.");
    }
    if (businessMappingService.isInvalidBusinessMappingUnallocatedCostLabel(businessMapping)) {
      throw new InvalidRequestException("Unallocated cost bucket label does not allow Others or Unallocated");
    }
    BusinessMapping costCategory = businessMappingService.save(businessMapping);
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new CostCategoryCreateEvent(accountId, costCategory.toDTO()));
      return true;
    }));
    return new RestResponse<>(costCategory);
  }

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Get List Of Business Mappings", nickname = "getBusinessMappingList")
  public RestResponse<List<BusinessMapping>> list(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId) {
    rbacHelper.checkCostCategoryViewPermission(accountId, null, null);
    return new RestResponse<>(businessMappingService.list(accountId));
  }

  @GET
  @Path("{id}")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Get Business Mapping", nickname = "getBusinessMapping")
  public RestResponse<BusinessMapping> get(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, @PathParam("id") String businessMappingId) {
    rbacHelper.checkCostCategoryViewPermission(accountId, null, null);
    return new RestResponse<>(businessMappingService.get(businessMappingId, accountId));
  }

  @PUT
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Update Business Mapping", nickname = "updateBusinessMapping")
  public RestResponse<String> update(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, BusinessMapping businessMapping) {
    rbacHelper.checkCostCategoryEditPermission(accountId, null, null);
    BusinessMapping oldCostCategory = businessMappingService.get(businessMapping.getUuid(), accountId);
    if (!oldCostCategory.getName().equals(businessMapping.getName())) {
      if (!businessMappingService.isNamePresent(businessMapping.getName(), businessMapping.getAccountId())) {
        throw new InvalidRequestException("Cost category name already exists.");
      }
    }
    if (businessMappingService.isInvalidBusinessMappingUnallocatedCostLabel(businessMapping)) {
      throw new InvalidRequestException("Unallocated cost bucket label does not allow Others or Unallocated");
    }
    BusinessMapping newCostCategory = businessMappingService.update(businessMapping);
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
  @ExceptionMetered
  @ApiOperation(value = "Delete Business Mapping", nickname = "deleteBusinessMapping")
  public RestResponse<CostCategoryDeleteDTO> delete(@NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY)
                                                    String accountId, @PathParam("id") String businessMappingId) {
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
  @Consumes(MediaType.APPLICATION_JSON)
  @ExceptionMetered
  @ApiOperation(value = "Get related Perspectives given Business Mapping Ids",
      nickname = "getPerspectivesGivenBusinessMappingIds")
  @Operation(operationId = "getPerspectivesGivenBusinessMappingIds",
      description =
          "Given Business Mapping Id, find out Perspectives where Business Mapping is present in Rules or Group By",
      summary = "Return list of perspectives corresponding to Business Mapping Id",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Return list of perspectives corresponding to Business Mapping Id",
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
