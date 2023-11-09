/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serviceaccount.remote;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.serviceaccount.ServiceAccountDTOInternal;

import java.util.List;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ServiceAccountClient {
  String SERVICE_ACCOUNTS_API = "serviceaccount";

  @GET(SERVICE_ACCOUNTS_API)
  Call<ResponseDTO<List<ServiceAccountDTO>>> listServiceAccounts(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGResourceFilterConstants.IDENTIFIERS) List<String> serviceAccountIdentifiers);

  @GET(SERVICE_ACCOUNTS_API + "/{identifier}")
  Call<ResponseDTO<ServiceAccountDTOInternal>> gerServiceAccountInternal(
      @Path(NGResourceFilterConstants.IDENTIFIER) @NotEmpty String identifier,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);
}
