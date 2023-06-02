/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.resource;

import static io.harness.NGConstants.REFERRED_BY_ENTITY_FQN;
import static io.harness.NGConstants.REFERRED_BY_ENTITY_TYPE;
import static io.harness.NGConstants.REFERRED_ENTITY_FQN;
import static io.harness.NGConstants.REFERRED_ENTITY_FQN1;
import static io.harness.NGConstants.REFERRED_ENTITY_FQN2;
import static io.harness.NGConstants.REFERRED_ENTITY_TYPE;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitysetupusage.dto.EntityReferencesDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Body;

@Api("/entitySetupUsage")
@Path("entitySetupUsage")
@Produces({"application/json"})
@Consumes({"application/json"})
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(DX)
public class EntitySetupUsageResource {
  EntitySetupUsageService entitySetupUsageService;
  private static final int MAX_LIMIT = 1000;

  @GET
  @ApiOperation(value = "Get Entities referring this resource", nickname = "listReferredByEntities")
  public ResponseDTO<PageResponse<EntitySetupUsageDTO>> list(
      @QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType entityType,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @BeanParam GitEntityFindInfoDTO gitFindInfoDTO) {
    return ResponseDTO.newResponse(getNGPageResponse(entitySetupUsageService.list(
        page, size, accountIdentifier, orgIdentifier, projectIdentifier, identifier, entityType, searchTerm)));
  }

  @GET
  @Path("v2")
  @ApiOperation(value = "Get Entities referring this resource if fqn is given", nickname = "listAllEntityUsageByFqn")
  public ResponseDTO<PageResponse<EntitySetupUsageDTO>> listAllEntityUsageV2(
      @QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(REFERRED_ENTITY_FQN) String referredEntityFQN,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType entityType,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    return ResponseDTO.newResponse(getNGPageResponse(entitySetupUsageService.listAllEntityUsage(
        page, size, accountIdentifier, referredEntityFQN, entityType, searchTerm)));
  }

  @GET
  @Path("internal/listAllEntityUsageV2With2Fqn")
  @ApiOperation(value = "Get Entities referring this resource if fqns are provided",
      nickname = "listAllEntityUsageWithTwoFqns", hidden = true)
  public ResponseDTO<PageResponse<EntitySetupUsageDTO>>
  listAllEntityUsageWith2Fqns(@QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(REFERRED_ENTITY_FQN1) String referredEntityFQN1,
      @NotNull @QueryParam(REFERRED_ENTITY_FQN2) String referredEntityFQN2,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType entityType,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    return ResponseDTO.newResponse(
        getNGPageResponse(entitySetupUsageService.listAllEntityUsageWithSupportForTwoFqnForASingleEntity(
            page, size, accountIdentifier, referredEntityFQN1, referredEntityFQN2, entityType, searchTerm)));
  }

  @GET
  @Path("internal")
  @ApiOperation(
      value = "Get Entities referring this resource if fqn is given", nickname = "listAllEntityUsage", hidden = true)
  public ResponseDTO<PageResponse<EntitySetupUsageDTO>>
  listAllEntityUsage(@QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(REFERRED_ENTITY_FQN) String referredEntityFQN,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType entityType,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    return ResponseDTO.newResponse(getNGPageResponse(entitySetupUsageService.listAllEntityUsage(
        page, size, accountIdentifier, referredEntityFQN, entityType, searchTerm)));
  }

  @GET
  @Path("internal/listAllReferredUsages")
  @ApiOperation(value = "Get Entities referred by this resource", nickname = "listAllReferredUsages", hidden = true)
  public ResponseDTO<List<EntitySetupUsageDTO>> listAllReferredUsages(
      @QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(REFERRED_BY_ENTITY_FQN) String referredByEntityFQN,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType referredEntityType,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    // todo: Just fqn is not sufficient here, we should have referredBy entity type also here
    return ResponseDTO.newResponse(entitySetupUsageService.listAllReferredUsages(
        page, size, accountIdentifier, referredByEntityFQN, referredEntityType, searchTerm));
  }

  @POST
  @Path("internal/listAllReferredUsagesBatch")
  @ApiOperation(
      value = "Get Entities referred by list of resources", nickname = "listAllReferredUsagesBatch", hidden = true)
  public ResponseDTO<EntityReferencesDTO>
  listAllReferredUsagesBatch(@NotNull @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY)
                             String accountIdentifier, @Size(max = 50) @Body List<String> referredByEntityFQNList,
      @NotNull @QueryParam(REFERRED_BY_ENTITY_TYPE) EntityType referredByEntityType,
      @NotNull @QueryParam(REFERRED_ENTITY_TYPE) EntityType referredEntityType) {
    // todo @deepak: Will have to add branch and repo, which might be a breaking change
    return ResponseDTO.newResponse(entitySetupUsageService.listAllReferredUsagesBatch(
        accountIdentifier, referredByEntityFQNList, referredByEntityType, referredEntityType));
  }

  @GET
  @Path("/internal/isEntityReferenced")
  @ApiOperation(value = "Returns true if the entity is referenced by other resource", nickname = "isEntityReferenced",
      hidden = true)
  public ResponseDTO<Boolean>
  isEntityReferenced(@NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(REFERRED_ENTITY_FQN) String referredEntityFQN,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType entityType) {
    return ResponseDTO.newResponse(
        entitySetupUsageService.isEntityReferenced(accountIdentifier, referredEntityFQN, entityType));
  }

  // use eveent fmwk
  @POST
  @Path("internal")
  @ApiOperation(value = "Saves the entity reference", nickname = "postEntitySetupUsage", hidden = true)
  @Deprecated
  public ResponseDTO<EntitySetupUsageDTO> save(EntitySetupUsageDTO entitySetupUsageDTO) {
    return ResponseDTO.newResponse(entitySetupUsageService.save(entitySetupUsageDTO));
  }

  // use event fmwk
  // We no longer support this api, the branching support is also not their for this api
  // for any crud of setup usage use the event framework
  @DELETE
  @Path("internal")
  @ApiOperation(value = "Deletes the entity reference record", nickname = "deleteEntitySetupUsage", hidden = true)
  @Deprecated
  public ResponseDTO<Boolean> delete(
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(REFERRED_ENTITY_FQN) String referredEntityFQN,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType referredEntityType,
      @QueryParam(REFERRED_BY_ENTITY_FQN) String referredByEntityFQN,
      @QueryParam(REFERRED_BY_ENTITY_TYPE) EntityType referredByEntityType) {
    return ResponseDTO.newResponse(entitySetupUsageService.delete(
        accountIdentifier, referredEntityFQN, referredEntityType, referredByEntityFQN, referredByEntityType));
  }
}
