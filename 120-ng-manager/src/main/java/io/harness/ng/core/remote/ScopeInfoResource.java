/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.beans.ScopeInfo;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Api(value = "scope-info", hidden = true)
@Path("scope-info")
@Produces({"application/json"})
@Consumes({"application/json"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class ScopeInfoResource {
  private final ScopeInfoService scopeResolverService;

  @GET
  @Timed
  @ApiOperation(
      hidden = true, value = "Resolve scope info from given identifiers", nickname = "resolveScopeInfoFromIdentifiers")
  @InternalApi
  @ExceptionMetered
  @Hidden
  public ResponseDTO<Optional<ScopeInfo>>
  resolveScopeInfo(@NotNull @QueryParam(ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(ORG_KEY) String orgIdentifier, @QueryParam(PROJECT_KEY) String projectIdentifier) {
    return ResponseDTO.newResponse(
        scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));
  }
}
