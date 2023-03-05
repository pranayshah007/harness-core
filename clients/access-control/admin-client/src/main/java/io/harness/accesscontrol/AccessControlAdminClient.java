/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.accesscontrol;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentAggregateResponseDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentCreateRequestDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.PL)
public interface AccessControlAdminClient {
  String ROLE_ASSIGNMENTS_API = "roleassignments";
  String ACL_PREFERENCES_API = "aclPreferences";
  String ROLES_API = "roles";

  @POST(ROLE_ASSIGNMENTS_API + "/filter")
  Call<ResponseDTO<PageResponse<RoleAssignmentResponseDTO>>> getFilteredRoleAssignments(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGResourceFilterConstants.PAGE_KEY) int page, @Query(NGResourceFilterConstants.SIZE_KEY) int size,
      @Body RoleAssignmentFilterDTO roleAssignmentFilterDTO);

  @POST(ROLE_ASSIGNMENTS_API + "/filter/internal")
  Call<ResponseDTO<PageResponse<RoleAssignmentResponseDTO>>> getFilteredRoleAssignmentsWithInternalRoles(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGResourceFilterConstants.PAGE_KEY) int page, @Query(NGResourceFilterConstants.SIZE_KEY) int size,
      @Body RoleAssignmentFilterDTO roleAssignmentFilterDTO);

  @POST(ROLE_ASSIGNMENTS_API + "/aggregate")
  Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> getAggregatedFilteredRoleAssignments(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body RoleAssignmentFilterDTO roleAssignmentFilterDTO);

  @POST(ROLE_ASSIGNMENTS_API + "/filter/internal/childscopes")
  Call<ResponseDTO<List<RoleAssignmentResponseDTO>>> getFilteredRoleAssignmentsIncludingChildScopes(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body RoleAssignmentFilterDTO roleAssignmentFilterDTO);

  @POST(ROLE_ASSIGNMENTS_API + "/multi/internal")
  Call<ResponseDTO<List<RoleAssignmentResponseDTO>>> createMultiRoleAssignment(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Query("managed") Boolean managed,
      @Body RoleAssignmentCreateRequestDTO roleAssignmentCreateRequestDTO);

  @PUT(ACL_PREFERENCES_API)
  Call<ResponseDTO<Boolean>> updateAccessControlPreference(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Query("enabled") boolean enabled);

  @GET(ROLES_API)
  Call<ResponseDTO<PageResponse<RoleResponseDTO>>> getRoles(@Query(NGResourceFilterConstants.PAGE_KEY) int page,
      @Query(NGResourceFilterConstants.SIZE_KEY) int size,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm);

  @PUT(ROLES_API + "/{identifier}")
  Call<ResponseDTO<RoleResponseDTO>> updateRole(@Path(NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Body RoleDTO roleDTO);
}
