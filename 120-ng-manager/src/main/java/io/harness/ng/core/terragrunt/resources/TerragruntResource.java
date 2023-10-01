/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.terragrunt.resources;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.TerragruntCommandFlagType;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.HashSet;
import java.util.Set;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Api("terragrunt")
@Path("/terragrunt")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class TerragruntResource {
  @GET
  @Path("terragruntCmdFlags")
  @ApiOperation(value = "Get Command flags based on terragrunt Step Type", nickname = "terragruntCmdFlags")
  public ResponseDTO<Set<TerragruntCommandFlagType>> getTerragruntCommandFlags(
      @QueryParam("stepType") @NotEmpty String stepType) {
    Set<TerragruntCommandFlagType> terragruntCmdFlags = new HashSet<>();
    for (TerragruntCommandFlagType terragruntCommandFlagType : TerragruntCommandFlagType.values()) {
      if (terragruntCommandFlagType.getTerragruntCommandAllowedStep().getStepsAllowed().stream().anyMatch(
              stepType::equalsIgnoreCase)) {
        terragruntCmdFlags.add(terragruntCommandFlagType);
      }
    }
    return ResponseDTO.newResponse(terragruntCmdFlags);
  }
}
