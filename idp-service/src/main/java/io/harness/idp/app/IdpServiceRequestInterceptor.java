/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.app;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.license.usage.dto.IDPLicenseUsageUserCaptureDTO;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Inject;
import java.io.IOException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IdpServiceRequestInterceptor implements ContainerRequestFilter {
  @Inject IDPModuleLicenseUsage idpModuleLicenseUsage;

  @Override
  public void filter(ContainerRequestContext containerRequestContext) throws IOException {
    captureLicenseUsageIfApplicable(containerRequestContext);
  }

  private void captureLicenseUsageIfApplicable(ContainerRequestContext containerRequestContext) {
    String urlPath = "";
    UserPrincipal userPrincipal = null;
    try {
      urlPath = containerRequestContext.getUriInfo().getPath();
      if (idpModuleLicenseUsage.checkIfUrlPathCapturesLicenseUsage(urlPath)) {
        userPrincipal = (UserPrincipal) SourcePrincipalContextBuilder.getSourcePrincipal();
        IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCapture =
            buildIdpLicenseUsageUserCaptureDTOFromUserPrincipal(userPrincipal);
        idpModuleLicenseUsage.captureLicenseUsageInRedis(idpLicenseUsageUserCapture);
      }
    } catch (Exception ex) {
      log.error(
          "Error in IDP module capture license usage for accountIdentifier = {} userIdentifier = {} urlPath = {} Error = {}",
          userPrincipal != null ? userPrincipal.getAccountId() : null,
          userPrincipal != null ? userPrincipal.getName() : null, urlPath, ex.getMessage(), ex);
    }
  }

  private IDPLicenseUsageUserCaptureDTO buildIdpLicenseUsageUserCaptureDTOFromUserPrincipal(
      UserPrincipal userPrincipal) {
    return IDPLicenseUsageUserCaptureDTO.builder()
        .accountIdentifier(userPrincipal.getAccountId())
        .userIdentifier(userPrincipal.getName())
        .email(userPrincipal.getEmail())
        .userName(userPrincipal.getUsername())
        .accessedAt(System.currentTimeMillis())
        .build();
  }
}
