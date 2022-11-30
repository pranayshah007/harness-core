/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.serviceenvmigrationv2.resources;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.migration.serviceenvmigrationv2.ServiceEnvironmentV2MigrationService;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.SvcEnvMigrationRequestDto;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.SvcEnvMigrationResponseDto;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("/service-env-migration")
@Path("/service-env-migration")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ServiceEnvironmentV2MigrationResource {
  private final ServiceEnvironmentV2MigrationService serviceEnvironmentV2MigrationService;
  @Inject private OrgAndProjectValidationHelper orgAndProjectValidationHelper;

  @POST
  @Path("/pipeline")
  @ApiOperation(value = "Migrate a pipeline to new service and environment framework")
  public ResponseDTO<SvcEnvMigrationResponseDto> migratePipelineWithServiceInfraV2(
      @NotNull @QueryParam("accountIdentifier") String accountId, @Valid SvcEnvMigrationRequestDto requestDto) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(), accountId);

    SvcEnvMigrationResponseDto response = serviceEnvironmentV2MigrationService.migratePipeline(requestDto, accountId);
    return ResponseDTO.newResponse(response);
  }
}
