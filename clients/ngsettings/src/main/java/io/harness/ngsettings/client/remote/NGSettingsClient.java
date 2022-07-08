/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.client.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingConstants;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.dto.SettingValueResponseDTO;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(PL)
public interface NGSettingsClient {
  String SETTINGS = "settings";
  @GET(SETTINGS + "/{identifier}")
  Call<ResponseDTO<SettingValueResponseDTO>> getSetting(
      @Path(value = SettingConstants.IDENTIFIER_KEY) String identifier,
      @Query(value = ACCOUNT_KEY) String accountIdentifier, @Query(value = ORG_KEY) String orgIdentifier,
      @Query(value = PROJECT_KEY) String projectIdentifier);
  @GET(SETTINGS + "category"
      + "/{category}")
  Call<ResponseDTO<List<SettingResponseDTO>>>
  listSettings(@Query(value = ACCOUNT_KEY) String accountIdentifier, @Query(value = ORG_KEY) String orgIdentifier,
      @Query(value = PROJECT_KEY) String projectIdentifier,
      @Path(value = SettingConstants.CATEGORY_KEY) SettingCategory category);
  @PUT(SETTINGS)
  Call<ResponseDTO<List<SettingResponseDTO>>> updateSettings(@Query(value = ACCOUNT_KEY) String accountIdentifier,
      @Query(value = ORG_KEY) String orgIdentifier, @Query(value = PROJECT_KEY) String projectIdentifier,
      @Body List<SettingRequestDTO> settingRequestDTOList);
}
