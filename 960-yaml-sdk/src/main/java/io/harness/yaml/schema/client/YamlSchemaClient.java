/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.schema.client;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

@OwnedBy(DX)
public interface YamlSchemaClient {
  @GET("partial-yaml-schema/details")
  Call<ResponseDTO<YamlSchemaDetailsWrapper>> getSchemaDetails(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @Query("scope") Scope scope);
  @POST("partial-yaml-schema/merged")
  Call<ResponseDTO<List<PartialSchemaDTO>>> get(@Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @Query("scope") Scope scope,
      @Body YamlSchemaDetailsWrapper yamlSchemaDetailsWrapper);
  @POST("partial-yaml-schema")
  Call<ResponseDTO<JsonNode>> getStepSchema(@Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @Query("scope") Scope scope,
      @Query(NGCommonEntityConstants.ENTITY_TYPE) EntityType entityType, @Query("yamlGroup") String yamlGroup,
      @Body YamlSchemaDetailsWrapper yamlSchemaDetailsWrapper);
  @GET("partial-yaml-schema/entityYaml")
  Call<ResponseDTO<JsonNode>> getEntityYaml(@Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.ENTITY_TYPE) EntityType entityType, @Query("scope") Scope scope);
}
