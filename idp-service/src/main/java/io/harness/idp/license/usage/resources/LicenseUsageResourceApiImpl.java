/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.resources;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.annotations.IdpServiceAuthIfHasApiKey;
import io.harness.idp.license.usage.dto.IDPLicenseUsageUserCaptureDTO;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.LicenseUsageResourceApi;
import io.harness.spec.server.idp.v1.model.LicenseUsageSaveRequest;
import io.harness.spec.server.idp.v1.model.LicenseUsageSaveResponse;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class LicenseUsageResourceApiImpl implements LicenseUsageResourceApi {
  private IDPModuleLicenseUsage idpModuleLicenseUsage;

  @Override
  @IdpServiceAuthIfHasApiKey
  public Response idpLicenseUsageSave(@Valid LicenseUsageSaveRequest licenseUsageSaveRequest, String harnessAccount) {
    log.info("Request received to save IDP license usage. Account = {}, Request = {}", harnessAccount,
        licenseUsageSaveRequest);
    idpModuleLicenseUsage.captureLicenseUsageInRedis(
        IDPLicenseUsageUserCaptureDTO.fromLicenseUsageSaveRequest(harnessAccount, licenseUsageSaveRequest));
    return Response.status(Response.Status.OK).entity(new LicenseUsageSaveResponse().status("Saved")).build();
  }
}
