/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.customDeployment.remote;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableResponseDTO;
import io.harness.ng.core.customDeployment.CustomDeploymentYamlRequestDTO;
import io.harness.ng.core.dto.ResponseDTO;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.CDP)
public interface CustomDeploymentResourceClient {
  String CUSTOM_DEPLOYMENT_ENDPOINT = "customDeployment/";

  // get expression variables
  @POST(CUSTOM_DEPLOYMENT_ENDPOINT + "expression-variables")
  Call<ResponseDTO<CustomDeploymentVariableResponseDTO>> getExpressionVariables(
      @Body CustomDeploymentYamlRequestDTO customDeploymentYamlRequestDTO);

  // get entity references
  @POST(CUSTOM_DEPLOYMENT_ENDPOINT + "get-references")
  Call<ResponseDTO<List<EntityDetailProtoDTO>>> getEntityReferences(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgId,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @Body CustomDeploymentYamlRequestDTO customDeploymentYamlRequestDTO);
}
