/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.client;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.service.dto.ServiceResponse;

import com.fasterxml.jackson.databind.JsonNode;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.CDC)
public interface NGClient {
  @POST("connectors")
  Call<ResponseDTO<ConnectorResponseDTO>> createConnector(@Header("Authorization") String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Body JsonNode connectorDTO);

  @POST("v2/secrets")
  Call<ResponseDTO<SecretResponseWrapper>> createSecret(@Header("Authorization") String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Body JsonNode secretDTO);

  @POST("servicesV2")
  Call<ResponseDTO<ServiceResponse>> createService(@Header("Authorization") String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Body JsonNode serviceDTO);

  @POST("environmentsV2")
  Call<ResponseDTO<ConnectorResponseDTO>> createEnvironment(@Header("Authorization") String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Body JsonNode environmentDTO);

  @POST("infrastructures")
  Call<ResponseDTO<ConnectorResponseDTO>> createInfrastructure(@Header("Authorization") String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Body JsonNode infraDTO);
}
