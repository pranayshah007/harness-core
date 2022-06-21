/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.services.NGTemplateSchemaService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;

import static io.harness.NGCommonEntityConstants.*;
import static io.harness.annotations.dev.HarnessTeam.CDC;

@OwnedBy(CDC)
@Api("templates")
@Path("templates/schema")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject}))
@ApiResponses(value =
        {
                @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
                , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
        })
@Tag(name = "Template Schemas", description = "This contains a list of APIs specific to the Template Schemas")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
        description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
        content =
                {
                        @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
                                schema = @Schema(implementation = FailureDTO.class))
                        ,
                        @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
                                schema = @Schema(implementation = FailureDTO.class))
                })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
        description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
        content =
                {
                        @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
                                schema = @Schema(implementation = ErrorDTO.class))
                        ,
                        @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
                                schema = @Schema(implementation = ErrorDTO.class))
                })
@NextGenManagerAuth
@Slf4j
public class NGTemplateSchemaResource {

    private final NGTemplateSchemaService ngTemplateSchemaService;

    @GET
    @Path("/templateSchema")
    @ApiOperation(value = "Get Template Schema", nickname = "getTemplateSchema")
    @Operation(operationId = "getTemplateSchema", summary = "Get Template Schema",
            responses =
                    {
                            @io.swagger.v3.oas.annotations.responses.
                                    ApiResponse(responseCode = "default", description = "Returns the Template schema")
                    })
    public ResponseDTO<JsonNode>
    getTemplateSchema(@QueryParam("templateEntityType") @NotNull TemplateEntityType templateEntityType,
                      @QueryParam(PROJECT_KEY) String projectIdentifier, @QueryParam(ORG_KEY) String orgIdentifier,
                      @QueryParam("scope") Scope scope, @QueryParam(IDENTIFIER_KEY) String identifier,
                      @NotNull @QueryParam(ACCOUNT_KEY) String accountIdentifier, @QueryParam(ENTITY_TYPE) @NotNull EntityType entityType,
                      @NotNull @QueryParam("yamlGroup")String yamlGroup) {
        JsonNode schema = null;
        schema = ngTemplateSchemaService.getTemplateSchema(accountIdentifier, projectIdentifier, orgIdentifier, yamlGroup, scope, entityType, templateEntityType);
        return ResponseDTO.newResponse(schema);
    }
}
