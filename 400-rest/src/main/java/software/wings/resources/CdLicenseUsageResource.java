/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdlicense.bean.CgActiveServicesUsageInfo;
import io.harness.cdlicense.impl.CgCdLicenseUsageService;
import io.harness.exception.InvalidRequestException;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;

import com.google.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDP)
@Path("/cg/cd/license/usage")
@Slf4j
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@InternalApi
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class CdLicenseUsageResource {
  private final CgCdLicenseUsageService licenseUsageService;

  @GET
  public RestResponse<CgActiveServicesUsageInfo> getActiveServiceUsage(@QueryParam("accountId") String accountId) {
    if (StringUtils.isBlank(accountId)) {
      throw new InvalidRequestException("Empty accountId is not a valid value");
    }

    log.info("Fetching CD license usage info in CG for account: [{}]", accountId);
    return new RestResponse<>(licenseUsageService.getActiveServiceLicenseUsage(accountId));
  }
}
