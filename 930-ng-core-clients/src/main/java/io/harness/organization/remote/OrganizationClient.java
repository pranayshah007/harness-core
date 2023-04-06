/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.organization.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static javax.ws.rs.core.HttpHeaders.IF_MATCH;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationRequest;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ResponseDTO;

import java.util.List;
import java.util.Optional;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(PL)
public interface OrganizationClient {
  String ORGANIZATIONS_API = "organizations";

  @POST(ORGANIZATIONS_API)
  Call<ResponseDTO<OrganizationResponse>> createOrganization(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Body OrganizationRequest organizationDTO);

  @GET(ORGANIZATIONS_API + "/{identifier}")
  Call<ResponseDTO<Optional<OrganizationResponse>>> getOrganization(
      @Path(value = NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier);

  @GET(ORGANIZATIONS_API)
  Call<ResponseDTO<PageResponse<OrganizationResponse>>> listOrganization(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGResourceFilterConstants.IDENTIFIERS) List<String> identifiers,
      @Query(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Query(value = NGResourceFilterConstants.PAGE_KEY) int page,
      @Query(value = NGResourceFilterConstants.SIZE_KEY) int size,
      @Query(value = NGResourceFilterConstants.SORT_KEY) List<String> sort);

  @GET(ORGANIZATIONS_API)
  Call<ResponseDTO<PageResponse<OrganizationResponse>>> listOrganizations(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGResourceFilterConstants.IDENTIFIERS) List<String> identifiers);

  @PUT(ORGANIZATIONS_API + "/{identifier}")
  Call<ResponseDTO<Optional<OrganizationResponse>>> updateOrganization(
      @Path(value = NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Body OrganizationRequest organizationDTO);

  @DELETE(ORGANIZATIONS_API + "/{identifier}")
  Call<ResponseDTO<Boolean>> deleteOrganization(@Header(IF_MATCH) Long ifMatch,
      @Path(value = NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier);

  @POST(ORGANIZATIONS_API + "/all-organizations")
  Call<ResponseDTO<PageResponse<OrganizationResponse>>> listAllOrganizations(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Body List<String> identifiers);
}
