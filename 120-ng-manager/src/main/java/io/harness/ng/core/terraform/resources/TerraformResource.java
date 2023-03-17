/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.terraform.resources;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.TerraformCommandFlagType;
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
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Api("terraform")
@Path("/terraform")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class TerraformResource {
  @GET
  @Path("terraformCmdFlags")
  @ApiOperation(
      value = "Get Command flags based on terraform Step Type and Config Type", nickname = "terraformCmdFlags")
  public ResponseDTO<Set<TerraformCommandFlagType>>
  getTerraformCommandFlags(@QueryParam("stepType") @NotEmpty @NotNull String stepType,
      @QueryParam("configType") @NotNull @NotEmpty String configType) {
    Set<TerraformCommandFlagType> terraformCmdFlags = new HashSet<>();
    for (TerraformCommandFlagType terraformCommandFlagType : TerraformCommandFlagType.values()) {
      if (terraformCommandFlagType.getTerraformCommandAllowedStep().getStepsAllowed().stream().anyMatch(
              stepType::equalsIgnoreCase)
          && terraformCommandFlagType.getTerraformYamlConfigType().stream().anyMatch(configType::equalsIgnoreCase)) {
        terraformCmdFlags.add(terraformCommandFlagType);
      }
    }
    return ResponseDTO.newResponse(terraformCmdFlags);
  }
}
