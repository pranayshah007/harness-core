/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.core.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.CVNG_RISK_CATEGORY_PATH;

import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.monitoredService.RiskCategoryDTO;
import io.harness.cvng.core.services.api.RiskCategoryService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Api(CVNG_RISK_CATEGORY_PATH)
@Path(CVNG_RISK_CATEGORY_PATH)
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
@OwnedBy(HarnessTeam.CV)
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class RiskCategoryResource {
  @Inject private RiskCategoryService riskCategoryService;

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(
      value = "get risk category for a custom health metric", nickname = "getRiskCategoryForCustomHealthMetric")
  public RestResponse<List<RiskCategoryDTO>>
  getOverAllHealthScore() {
    return new RestResponse<>(riskCategoryService.getRiskCategoriesDTO());
  }
}
