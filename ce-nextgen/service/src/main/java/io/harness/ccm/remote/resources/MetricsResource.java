/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.timescaledb.metrics.QueryStatsPrinter;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Api("metrics")
@Path("/metrics")
@Produces({MediaType.APPLICATION_JSON})
@Consumes({MediaType.APPLICATION_JSON})
@Slf4j
@NextGenManagerAuth
@OwnedBy(CE)
public class MetricsResource {
  private static final Set<String> internalAccountIds =
      ImmutableSet.of("kmpySmUISimoRrJL6NL73w", "wFHXHD0RRQWoO8tIZT5YVw", "vpCkHKsDSxK9_KYfjCTMKA");

  @Inject private QueryStatsPrinter queryStatsPrinter;

  @GET
  @Path("timescale")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "timescale", nickname = "timescale sql queries stats")
  @LogAccountIdentifier
  public ResponseDTO<Map<String, QueryStatsPrinter.QueryStat>> timescale(
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId) throws Exception {
    if (!internalAccountIds.contains(accountId)) {
      throw new InvalidRequestException("Not Allowed");
    }

    return ResponseDTO.newResponse(queryStatsPrinter.get());
  }
}
