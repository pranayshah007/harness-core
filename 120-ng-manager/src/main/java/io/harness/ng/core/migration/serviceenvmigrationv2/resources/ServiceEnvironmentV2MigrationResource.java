package io.harness.ng.core.migration.serviceenvmigrationv2.resources;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.migration.serviceenvmigrationv2.ServiceEnvironmentV2MigrationService;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.ServiceEnvironmentRequestDto;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.ServiceEnvironmentResponseDto;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.StageResponseDto;

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
  @ApiOperation(value = "Create/Update Service, Infra v2 and pipeline")
  public ResponseDTO<ServiceEnvironmentResponseDto> migratePipelineWithServiceInfraV2(
          @NotNull @QueryParam("accountIdentifier") String accountId, @Valid ServiceEnvironmentRequestDto
          requestDto) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
            requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(), accountId);

    ServiceEnvironmentResponseDto response = serviceEnvironmentV2MigrationService.migratePipelineWithServiceEnvV2(requestDto, accountId);
    return ResponseDTO.newResponse(response);
  }

}
