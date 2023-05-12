/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.infrastructure;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.beans.NGEntityTemplateResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.CDC)
public interface InfrastructureResourceClient {
  String INFRA_API = "infrastructures";

  @GET(INFRA_API + "/{infraIdentifier}")
  Call<ResponseDTO<InfrastructureResponse>> getInfra(@Path("infraIdentifier") String infraIdentifier,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String envIdentifier);

  @GET(INFRA_API + "/runtimeInputs")
  Call<ResponseDTO<NGEntityTemplateResponseDTO>> getInfrastructureInputs(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String envIdentifier,
      @Query(NGCommonEntityConstants.INFRA_IDENTIFIERS) String infraIdentifier);
}
