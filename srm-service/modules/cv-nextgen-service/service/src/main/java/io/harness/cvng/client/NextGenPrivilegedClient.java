/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.CDStageMetaDataDTO;
import io.harness.ng.core.dto.CdDeployStageMetadataRequestDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.service.dto.ServiceResponse;

import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
@OwnedBy(HarnessTeam.CV)
public interface NextGenPrivilegedClient {
  String CONNECTOR_BASE_PATH = "connectors";

  @POST(CONNECTOR_BASE_PATH)
  Call<ResponseDTO<ConnectorResponseDTO>> create(
      @Body ConnectorDTO connector, @Path("accountIdentifier") String accountIdentifier);

  @GET(CONNECTOR_BASE_PATH + "/{connectorIdentifier}")
  Call<ResponseDTO<ConnectorResponseDTO>> get(@Path("connectorIdentifier") String connectorIdentifier,
      @Query("accountIdentifier") String accountIdentifier, @Query("orgIdentifier") @NotNull String orgIdentifier,
      @Query("projectIdentifier") @NotNull String projectIdentifier);

  @GET("environmentsV2/{environmentIdentifier}")
  Call<ResponseDTO<EnvironmentResponse>> getEnvironment(@Path("environmentIdentifier") String environmentIdentifier,
      @Query("accountIdentifier") String accountId, @Query("orgIdentifier") String orgIdentifier,
      @Query("projectIdentifier") String projectIdentifier);

  @GET("servicesV2/{serviceIdentifier}")
  Call<ResponseDTO<ServiceResponse>> getService(@Path("serviceIdentifier") String serviceIdentifier,
      @Query("accountIdentifier") String accountId, @Query("orgIdentifier") String orgIdentifier,
      @Query("projectIdentifier") String projectIdentifier);

  @GET("servicesV2/list/scoped")
  Call<ResponseDTO<PageResponse<ServiceResponse>>> listService(@Query("accountIdentifier") String accountId,
      @Query("orgIdentifier") String orgIdentifier, @Query("projectIdentifier") String projectIdentifier,
      @Query("serviceIdentifiers") List<String> serviceIdentifiers);

  @GET("environmentsV2/list/scoped")
  Call<ResponseDTO<PageResponse<EnvironmentResponse>>> listEnvironment(@Query("accountIdentifier") String accountId,
      @Query("orgIdentifier") String orgIdentifier, @Query("projectIdentifier") String projectIdentifier,
      @Query("envIdentifiers") List<String> environmentIdentifier);

  @GET("servicesV2")
  Call<ResponseDTO<PageResponse<ServiceResponse>>> listServicesForProject(@Query("page") int page,
      @Query("size") int size, @Query("accountIdentifier") String accountId,
      @Query("orgIdentifier") String orgIdentifier, @Query("projectIdentifier") String projectIdentifier,
      @Query("serviceIdentifiers") Set<String> serviceIdentifiers);

  @GET("environmentsV2")
  Call<ResponseDTO<PageResponse<EnvironmentResponse>>> listEnvironmentsForProject(@Query("page") int page,
      @Query("size") int size, @Query("accountIdentifier") String accountId,
      @Query("orgIdentifier") String orgIdentifier, @Query("projectIdentifier") String projectIdentifier,
      @Query("envIdentifiers") Set<String> envIdentifiers, @Query("sort") List<String> sort);

  @GET("projects/{projectIdentifier}")
  Call<ResponseDTO<ProjectResponse>> getProject(@Path("projectIdentifier") String projectIdentifier,
      @Query("accountIdentifier") String accountId, @Query("orgIdentifier") String orgIdentifier);

  @GET("organizations/{orgIdentifier}")
  Call<ResponseDTO<OrganizationResponse>> getOrganization(
      @Path("orgIdentifier") String orgIdentifier, @Query("accountIdentifier") String accountId);

  @POST("cdStage/metadata")
  Call<ResponseDTO<CDStageMetaDataDTO>> getCDStageMetaData(@Body CdDeployStageMetadataRequestDTO requestDTO);
}
