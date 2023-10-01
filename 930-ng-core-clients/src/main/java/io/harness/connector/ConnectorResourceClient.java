/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.delegate.beans.connector.ConnectorValidationParameterResponse;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.serializer.kryo.KryoResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ConnectorResourceClient {
  String CONNECTORS_API = "connectors";

  @GET(CONNECTORS_API + "/{connectorIdentifier}")
  Call<ResponseDTO<Optional<ConnectorDTO>>> get(@Path("connectorIdentifier") String connectorIdentifier,
      @NotEmpty @Query("accountIdentifier") String accountIdentifier, @Query("orgIdentifier") String orgIdentifier,
      @Query("projectIdentifier") String projectIdentifier);

  @POST(CONNECTORS_API + "/listbyfqn")
  Call<ResponseDTO<List<ConnectorResponseDTO>>> listConnectorByFQN(
      @NotEmpty @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Body List<String> connectorsFQN);

  @GET(CONNECTORS_API + "/{identifier}/validation-params")
  @KryoResponse
  Call<ResponseDTO<ConnectorValidationParameterResponse>> getConnectorValidationParams(
      @Path(NGCommonEntityConstants.IDENTIFIER_KEY) String connectorIdentifier,
      @NotNull @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);

  @POST(CONNECTORS_API + "/listV2")
  Call<ResponseDTO<PageResponse<ConnectorResponseDTO>>> listConnectors(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGResourceFilterConstants.PAGE_KEY) int page, @Query(NGResourceFilterConstants.SIZE_KEY) int size,
      @Body ConnectorFilterPropertiesDTO connectorListFilter,
      @Query("getDistinctFromBranches") Boolean getDistinctFromBranches);

  @POST(CONNECTORS_API + "/testConnectionInternal/{identifier}")
  Call<ResponseDTO<ConnectorValidationResult>> testConnectionInternal(
      @Path(NGCommonEntityConstants.IDENTIFIER_KEY) String connectorIdentifier,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);

  @GET(CONNECTORS_API + "/attributes")
  Call<ResponseDTO<List<Map<String, String>>>> getConnectorsAttributes(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("connectorIdentifiers") List<String> connectorIdentifiers);

  @POST(CONNECTORS_API + "/allConnectors")
  Call<ResponseDTO<PageResponse<ConnectorResponseDTO>>> listAllConnectors(
      @Query(NGResourceFilterConstants.PAGE_KEY) int page, @Query(NGResourceFilterConstants.SIZE_KEY) int size,
      @Body ConnectorInternalFilterPropertiesDTO connectorInternalFilterPropertiesDTO);
}
