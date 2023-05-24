/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.remote;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ng.core.dto.ResponseDTO;

import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.PIPELINE)
public interface CiServiceResourceClient {
  String EXECUTION_CONFIG_API_BASE_PATH = "/execution-config";

  @GET(EXECUTION_CONFIG_API_BASE_PATH + "/get-customer-config")
  Call<ResponseDTO<CIExecutionImages>> getCustomersExecutionConfig(
      @NotNull @Query(NGCommonEntityConstants.INFRA) StageInfraDetails.Type infra,
      @NotNull @Query(NGCommonEntityConstants.OVERRIDES_ONLY) @DefaultValue("true") boolean overridesOnly,
      @NotNull @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier);
}
