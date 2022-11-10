/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.remote.admin;

import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SMPEncLicenseDTO;
import io.harness.licensing.beans.modules.SMPLicenseRequestDTO;
import io.harness.licensing.beans.modules.SMPValidationResultDTO;
import io.harness.ng.core.dto.ResponseDTO;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface AdminLicenseHttpClient {
  String ADMIN_LICENSE_API = "admin/licenses";
  String SMP_LICENSE_API = "smp/licenses";

  @GET(ADMIN_LICENSE_API + "/{accountIdentifier}")
  Call<ResponseDTO<AccountLicenseDTO>> getAccountLicense(@Path("accountIdentifier") String accountIdentifier);

  @POST(ADMIN_LICENSE_API)
  Call<ResponseDTO<ModuleLicenseDTO>> createAccountLicense(
      @Query("accountIdentifier") String accountIdentifier, @Body ModuleLicenseDTO moduleLicenseDTO);

  @PUT(ADMIN_LICENSE_API + "/{identifier}")
  Call<ResponseDTO<ModuleLicenseDTO>> updateModuleLicense(@Path("identifier") String identifier,
      @Query("accountIdentifier") String accountIdentifier, @Body ModuleLicenseDTO moduleLicenseDTO);

  @DELETE(ADMIN_LICENSE_API + "/{identifier}")
  Call<ResponseDTO<Void>> deleteModuleLicense(
      @Path("identifier") String identifier, @Query("accountIdentifier") String accountIdentifier);

  @POST(SMP_LICENSE_API + "/generate/{accountIdentifier}")
  Call<ResponseDTO<SMPEncLicenseDTO>> generateSMPLicense(
      @Path("accountIdentifier") String accountIdentifier, @Body SMPLicenseRequestDTO licenseRequestDTO);

  @POST(SMP_LICENSE_API + "/validate")
  Call<ResponseDTO<SMPValidationResultDTO>> validateSMPLicense(@Body SMPEncLicenseDTO licenseDTO);
}
