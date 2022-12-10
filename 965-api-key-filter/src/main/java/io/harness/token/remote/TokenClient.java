/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.token.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.serviceaccount.ServiceAccountDTO;

import java.util.List;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(PL)
public interface TokenClient {
  @GET("token")
  Call<ResponseDTO<TokenDTO>> getToken(@Query(NGCommonEntityConstants.TOKEN_KEY) @NotEmpty String tokenId);

  @GET("settings")
  Call<ResponseDTO<List<SettingResponseDTO>>> listSettings(@Query(value = "accountIdentifier") String accountIdentifier,
      @Query(value = "orgIdentifier") String orgIdentifier,
      @Query(value = "projectIdentifier") String projectIdentifier, @Query(value = "category") SettingCategory category,
      @Query(value = "group") String groupIdentifier);

  @GET("serviceaccount/internal/{identifier}")
  Call<ResponseDTO<ServiceAccountDTO>> getServiceAccount(
      @Path(value = "identifier") String identifier, @Query(value = "accountIdentifier") String accountIdentifier);
}
