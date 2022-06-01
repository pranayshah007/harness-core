/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.remote;

import io.harness.ModuleType;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.response.CheckExpiryResultDTO;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.ng.core.dto.ResponseDTO;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface NgLicenseHttpClient {
  String CHECK_NG_LICENSE_EXPIRY_API = "licenses/{accountId}/check-expiry";
  String SOFT_DELETE_API = "licenses/{accountId}/soft-delete";
  String LICENSE_SUMMARY_API = "licenses/{accountId}/summary";
  String MODULE_LICENSE_EXPIRY_API = "licenses/{moduleType}/enabled";
  @GET(CHECK_NG_LICENSE_EXPIRY_API)
  Call<ResponseDTO<CheckExpiryResultDTO>> checkExpiry(@Path("accountId") String accountId);

  @GET(SOFT_DELETE_API) Call<ResponseDTO<Boolean>> softDelete(@Path("accountId") String accountId);

  @GET(LICENSE_SUMMARY_API)
  Call<ResponseDTO<LicensesWithSummaryDTO>> getLicenseSummary(
      @Path("accountId") String accountId, @Query("moduleType") String moduleType);

  @GET(MODULE_LICENSE_EXPIRY_API)
  Call<ResponseDTO<List<ModuleLicenseDTO>>> getModuleLicensesByModuleType(
      @Path("moduleType") ModuleType moduleType, @Query("expiryTime") long expiryTime);
}
