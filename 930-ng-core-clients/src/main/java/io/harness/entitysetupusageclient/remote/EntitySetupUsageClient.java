/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entitysetupusageclient.remote;

import static io.harness.NGConstants.REFERRED_BY_ENTITY_FQN;
import static io.harness.NGConstants.REFERRED_BY_ENTITY_TYPE;
import static io.harness.NGConstants.REFERRED_ENTITY_FQN;
import static io.harness.NGConstants.REFERRED_ENTITY_TYPE;

import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitysetupusage.dto.EntityReferencesDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;

import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.domain.Page;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * All this apis are internal and won't be exposed to the customers. The APIs takes the FQN as input, FQN is fully
 * qualified Name of the entity. It is the unique key with which we can identify the resource.
 * for eg: For a project level connector it will be
 *      accountIdentifier/orgIdentifier/projectIdentifier/identifier
 *  For a input set it will be
 *    accountIdentifier/orgIdentifier/projectIdentifier/pipelineIdentifier/identifier
 */
@OwnedBy(HarnessTeam.DX)
public interface EntitySetupUsageClient {
  String INTERNAL_ENTITY_REFERENCE_API = "entitySetupUsage/internal";

  @GET(INTERNAL_ENTITY_REFERENCE_API)
  Call<ResponseDTO<Page<EntitySetupUsageDTO>>> listAllEntityUsage(@Query(NGResourceFilterConstants.PAGE_KEY) int page,
      @Query(NGResourceFilterConstants.SIZE_KEY) int size,
      @NotEmpty @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(REFERRED_ENTITY_FQN) String referredEntityFQN, @Query(REFERRED_ENTITY_TYPE) EntityType referredEntityType,
      @Query(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm);

  @GET(INTERNAL_ENTITY_REFERENCE_API + "/listAllReferredUsages")
  Call<ResponseDTO<List<EntitySetupUsageDTO>>> listAllReferredUsages(
      @Query(NGResourceFilterConstants.PAGE_KEY) int page, @Query(NGResourceFilterConstants.SIZE_KEY) int size,
      @NotEmpty @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(REFERRED_BY_ENTITY_FQN) String referredByEntityFQN,
      @Query(REFERRED_ENTITY_TYPE) EntityType referredEntityType,
      @Query(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm);

  /*
   * This function is created for the rbac use case and thus it doesn't support git branches filter
   */
  @POST(INTERNAL_ENTITY_REFERENCE_API + "/listAllReferredUsagesBatch")
  Call<ResponseDTO<EntityReferencesDTO>> listAllReferredUsagesBatch(
      @NotNull @NotEmpty @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Size(max = 50) @Body List<String> referredByEntityFQNList,
      @NotNull @Query(REFERRED_BY_ENTITY_TYPE) EntityType referredByEntityType,
      @NotNull @Query(REFERRED_ENTITY_TYPE) EntityType referredEntityType);

  @POST(INTERNAL_ENTITY_REFERENCE_API)
  @Deprecated
  Call<ResponseDTO<EntitySetupUsageDTO>> save(@Body EntitySetupUsageDTO entitySetupUsageDTO);

  // This is depreceated, please use this event framework, we no longer support
  @DELETE(INTERNAL_ENTITY_REFERENCE_API)
  @Deprecated
  Call<ResponseDTO<Boolean>> delete(@NotEmpty @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(REFERRED_ENTITY_FQN) String referredEntityFQN, @Query(REFERRED_ENTITY_TYPE) EntityType referredEntityType,
      @Query(REFERRED_BY_ENTITY_FQN) String referredByEntityFQN,
      @Query(REFERRED_BY_ENTITY_TYPE) EntityType referredByEntityType);

  @GET(INTERNAL_ENTITY_REFERENCE_API + "/isEntityReferenced")
  Call<ResponseDTO<Boolean>> isEntityReferenced(
      @NotEmpty @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(REFERRED_ENTITY_FQN) String referredEntityFQN, @Query(REFERRED_ENTITY_TYPE) EntityType referredEntityType);
}
