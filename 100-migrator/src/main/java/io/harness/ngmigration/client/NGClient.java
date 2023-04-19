/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.client;

import static io.harness.security.NextGenAuthenticationFilter.X_API_KEY;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.ng.core.dto.ProjectRequest;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideResponseDTO;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.CDC)
public interface NGClient {
  @POST("projects")
  Call<ResponseDTO<ProjectResponse>> createProject(@Header(X_API_KEY) String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @Body ProjectRequest projectRequest);

  @POST("connectors")
  Call<ResponseDTO<ConnectorResponseDTO>> createConnector(@Header(X_API_KEY) String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Body JsonNode connectorDTO);

  @POST("v2/secrets")
  Call<ResponseDTO<SecretResponseWrapper>> createSecret(@Header(X_API_KEY) String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Body JsonNode secretDTO);

  @POST("v2/secrets/yaml")
  @Headers({"Content-Type: application/yaml"})
  Call<ResponseDTO<SecretResponseWrapper>> createSecretUsingYaml(@Header(X_API_KEY) String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Body JsonNode yamlBody);

  @POST("v2/secrets/files")
  @Multipart
  Call<ResponseDTO<SecretResponseWrapper>> createSecretFile(@Header(X_API_KEY) String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Part("file") RequestBody content,
      @Part("spec") RequestBody spec);

  @POST("v2/secrets/filesMigration")
  @Multipart
  Call<ResponseDTO<SecretResponseWrapper>> createSecretFileInternal(@Header(X_API_KEY) String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("encryptionKey") String encryptionKey, @Query("encryptedValue") String encryptedValue,
      @Part("spec") RequestBody spec);

  @POST("servicesV2")
  Call<ResponseDTO<ServiceResponse>> createService(@Header(X_API_KEY) String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Body JsonNode serviceDTO);

  @POST("environmentsV2")
  Call<ResponseDTO<ConnectorResponseDTO>> createEnvironment(@Header(X_API_KEY) String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Body JsonNode environmentDTO);

  @POST("environmentsV2/serviceOverrides")
  Call<ResponseDTO<ServiceOverrideResponseDTO>> upsertServiceOverride(@Header(X_API_KEY) String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Body JsonNode serviceOverrides);

  @POST("infrastructures")
  Call<ResponseDTO<ConnectorResponseDTO>> createInfrastructure(@Header(X_API_KEY) String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Body JsonNode infraDTO);

  @POST("file-store")
  @Multipart
  Call<ResponseDTO<FileDTO>> createFileInFileStore(@Header(X_API_KEY) String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Part("content") RequestBody content,
      @Part("name") RequestBody name, @Part("identifier") RequestBody identifier,
      @Part("fileUsage") RequestBody fileUsage, @Part("type") RequestBody type,
      @Part("parentIdentifier") RequestBody parentIdentifier, @Part("mimeType") RequestBody mimeType);

  @POST("file-store")
  @Multipart
  Call<ResponseDTO<FileDTO>> createFolder(@Header(X_API_KEY) String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Part("name") RequestBody name,
      @Part("identifier") RequestBody identifier, @Part("type") RequestBody type,
      @Part("parentIdentifier") RequestBody parentIdentifier);

  @POST("variables")
  Call<ResponseDTO<ConnectorResponseDTO>> createVariable(@Header(X_API_KEY) String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Body JsonNode variableRequestDTO);

  @POST("user-groups")
  Call<ResponseDTO<UserGroupDTO>> createUserGroup(@Header(X_API_KEY) String auth,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Body UserGroupDTO userGroupDTO);
}
