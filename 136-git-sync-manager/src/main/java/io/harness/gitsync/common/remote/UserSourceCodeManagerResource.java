/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_CODE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerRequestDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerResponseDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerResponseDTOList;
import io.harness.gitsync.common.mappers.UserSourceCodeManagerMapper;
import io.harness.gitsync.common.service.UserSourceCodeManagerService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import retrofit2.http.Body;

@Api("/user-source-code-manager")
@Path("/user-source-code-manager")
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = BAD_REQUEST_PARAM_MESSAGE)
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = INTERNAL_SERVER_ERROR_MESSAGE)
    })
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
@Tag(name = "User Source Code Manager", description = "Contains APIs related to User Source Code Manager")
@io.swagger.v3.oas.annotations.responses.
ApiResponse(responseCode = BAD_REQUEST_CODE, description = BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = APPLICATION_JSON_MEDIA_TYPE, schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = APPLICATION_YAML_MEDIA_TYPE, schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.
ApiResponse(responseCode = INTERNAL_SERVER_ERROR_CODE, description = INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = APPLICATION_JSON_MEDIA_TYPE, schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = APPLICATION_YAML_MEDIA_TYPE, schema = @Schema(implementation = ErrorDTO.class))
    })
public class UserSourceCodeManagerResource {
  private Map<SCMType, UserSourceCodeManagerMapper> scmMapBinder;
  @Inject UserSourceCodeManagerService userSourceCodeManagerService;

  @GET
  @Hidden
  @ApiOperation(value = "get user source code manager information", nickname = "getUserSourceCodeManagers")
  @Operation(operationId = "getUserSourceCodeManagers",
      summary = "Lists User Source Code Managers for the given account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Paginated list of Source Code Managers of given account")
      })
  public ResponseDTO<UserSourceCodeManagerResponseDTOList>
  get(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          "accountIdentifier") @NotNull String accountIdentifier,
      @Parameter(description = "userIdentifier") @QueryParam("userIdentifier") @NotNull String userIdentifier,
      @Parameter(description = "Type of Git Provider") @QueryParam("type") String type) {
    List<UserSourceCodeManagerDTO> userSourceCodeManagerDTOs =
        userSourceCodeManagerService.getByType(accountIdentifier, userIdentifier, type);
    List<UserSourceCodeManagerResponseDTO> userSourceCodeManagerResponseDTOs =
        userSourceCodeManagerDTOs.stream()
            .map(scm -> scmMapBinder.get(scm.getType()).toResponseDTO(scm))
            .collect(Collectors.toList());
    return ResponseDTO.newResponse(UserSourceCodeManagerResponseDTOList.builder()
                                       .userSourceCodeManagerResponseDTOList(userSourceCodeManagerResponseDTOs)
                                       .build());
  }

  @POST
  @Hidden
  @ApiOperation(value = "save user source code manager", nickname = "saveUserSourceCodeManager")
  @Operation(operationId = "createUserSourceCodeManager", summary = "Creates User Source Code Manager",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "This contains details of the newly created User Source Code Manager")
      })
  public ResponseDTO<UserSourceCodeManagerResponseDTO>
  save(@RequestBody(description = "This contains details of Source Code Manager") @NotNull
      @Body UserSourceCodeManagerRequestDTO userSourceCodeManagerRequest) {
    UserSourceCodeManagerDTO scmToSave =
        scmMapBinder.get(userSourceCodeManagerRequest.getType()).toServiceDTO(userSourceCodeManagerRequest);
    UserSourceCodeManagerDTO userSourceCodeManagerDTO = userSourceCodeManagerService.save(scmToSave);
    return ResponseDTO.newResponse(
        scmMapBinder.get(userSourceCodeManagerDTO.getType()).toResponseDTO(userSourceCodeManagerDTO));
  }
}
