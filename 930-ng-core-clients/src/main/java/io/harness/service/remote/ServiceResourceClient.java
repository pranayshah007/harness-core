/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.remote;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.artifact.ArtifactSourceYamlRequestDTO;
import io.harness.ng.core.beans.NGEntityTemplateResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.service.dto.ServiceResponse;

import java.util.List;
import javax.ws.rs.DefaultValue;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(PIPELINE)
public interface ServiceResourceClient {
  String SERVICE_API = "servicesV2";

  @GET(SERVICE_API)
  Call<ResponseDTO<PageResponse<ServiceResponse>>> listServicesForProject(@Query("page") @DefaultValue("0") int page,
      @Query("size") @DefaultValue("100") int size, @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("serviceIdentifiers") List<String> serviceIdentifiers, @Query("sort") List<String> sort);

  @POST(SERVICE_API + "/artifact-source-references")
  Call<ResponseDTO<List<EntityDetailProtoDTO>>> getEntityReferencesForArtifactSourceTemplate(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgId,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @Body ArtifactSourceYamlRequestDTO artifactSourceYamlRequestDTO);

  @GET(SERVICE_API + "/{serviceIdentifier}")
  Call<ResponseDTO<ServiceResponse>> getService(@Path("serviceIdentifier") String serviceIdentifier,
      @Query("accountIdentifier") String accountId, @Query("orgIdentifier") String orgIdentifier,
      @Query("projectIdentifier") String projectIdentifier);

  @GET(SERVICE_API + "/runtimeInputs/{serviceIdentifier}")
  Call<ResponseDTO<NGEntityTemplateResponseDTO>> getServiceRuntimeInputs(
      @Path("serviceIdentifier") String serviceIdentifier, @Query("accountIdentifier") String accountId,
      @Query("orgIdentifier") String orgIdentifier, @Query("projectIdentifier") String projectIdentifier);

  @GET(SERVICE_API + "/all-services")
  Call<ResponseDTO<PageResponse<ServiceResponse>>> getAllServicesList(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Query("page") @DefaultValue("0") int page,
      @Query("size") @DefaultValue("100") int size, @Query("sort") List<String> sort);
}
