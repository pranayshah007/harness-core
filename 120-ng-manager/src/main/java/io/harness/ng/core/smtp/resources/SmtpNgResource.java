/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.smtp.resources;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.PL;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.NgSmtpDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.ValidationResultDTO;
import io.harness.ng.core.smtp.SmtpNgService;

import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Api("smtpConfig")
@Path("/smtpConfig")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Scope(PermissionAttribute.ResourceType.SETTING)
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "SMTP", description = "This contains APIs related to SmtpConfig as defined in Harness")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@OwnedBy(PL)
public class SmtpNgResource {
  @Inject private SmtpNgService smtpNgService;

  @POST
  @ApiOperation(value = "Create SMTP config", nickname = "createSmtpConfig")
  @Operation(operationId = "createSmtpConfig", summary = "Creates SMTP config",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created config")
      })
  @Timed
  @ExceptionMetered
  public ResponseDTO<NgSmtpDTO>
  save(@Valid @NotNull NgSmtpDTO variable,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @AccountIdentifier @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) throws IOException {
    variable.setAccountId(accountIdentifier);
    NgSmtpDTO response = smtpNgService.saveSmtpSettings(variable);
    return ResponseDTO.newResponse(response);
  }

  @POST
  @Path("validateName")
  @ApiOperation(value = "Checks whether other connectors exist with the same name", nickname = "validateName")
  @Operation(operationId = "validateName", summary = "Checks whether other connectors exist with the same name",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns validation Result")
      })
  @Timed
  @ExceptionMetered
  public ResponseDTO<ValidationResultDTO>
  validateName(@Parameter(description = "The name of Config") @QueryParam("name") String name,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam("accountId") String accountId)
      throws IOException {
    ValidationResultDTO response = smtpNgService.validateSmtpSettings(name, accountId);
    return ResponseDTO.newResponse(response);
  }

  @PUT
  @ApiOperation(value = "Update SmtpConfig", nickname = "updateSmtp")
  @Operation(operationId = "updateSmtp", summary = "Updates the Smtp Config",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns updated config")
      })
  @Timed
  @ExceptionMetered
  public ResponseDTO<NgSmtpDTO>
  update(@Valid @NotNull NgSmtpDTO variable,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @AccountIdentifier @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) throws IOException {
    variable.setAccountId(accountIdentifier);
    NgSmtpDTO response = smtpNgService.updateSmtpSettings(variable);
    return ResponseDTO.newResponse(response);
  }

  @POST
  @Path("validate-connectivity")
  @ApiOperation(value = "Tests the connectivity", nickname = "validateConnectivity")
  @Operation(operationId = "validateConnectivity", summary = "Tests the config's connectivity by sending a test email",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns validation Result")
      })
  @Timed
  @ExceptionMetered
  public ResponseDTO<ValidationResultDTO>
  validateConnectivity(
      @Parameter(description = "Attribute uuid", required = true) @NotNull @QueryParam("identifier") String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam("accountId") String accountId,
      @NotNull @QueryParam("to") String to, @NotNull @QueryParam("subject") String subject,
      @NotNull @QueryParam("body") String body) throws IOException {
    ValidationResultDTO response =
        smtpNgService.validateConnectivitySmtpSettings(identifier, accountId, to, subject, body);
    return ResponseDTO.newResponse(response);
  }

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete Smtp Config", nickname = "deleteSmtpConfig")
  @Operation(operationId = "deleteSmtpConfig", summary = "Delete Smtp Config by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Boolean status whether"
                + " request was successful or not")
      })
  @Timed
  @ExceptionMetered
  public ResponseDTO<Boolean>
  delete(@Parameter(description = "Config identifier") @PathParam("identifier") String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @AccountIdentifier @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) throws IOException {
    Boolean response = smtpNgService.deleteSmtpSettings(identifier);
    return ResponseDTO.newResponse(response);
  }

  @GET
  @ApiOperation(value = "Gets Smtp config by accountId", nickname = "getSmtpConfig")
  @Operation(operationId = "getSmtpConfig", summary = "Gets Smtp config by accountId",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "returns the SmtpConfig having"
                + " accountId as specified in request")
      })
  @Timed
  @ExceptionMetered
  public ResponseDTO<NgSmtpDTO>
  get(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam("accountId") String accountId) throws IOException {
    NgSmtpDTO response = smtpNgService.getSmtpSettings(accountId);
    return ResponseDTO.newResponse(response);
  }
}
