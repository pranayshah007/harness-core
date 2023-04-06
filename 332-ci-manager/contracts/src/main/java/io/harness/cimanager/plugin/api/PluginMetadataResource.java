/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.plugin.api;

import static io.harness.NGResourceFilterConstants.PAGE_KEY;
import static io.harness.NGResourceFilterConstants.SEARCH_TERM_KEY;
import static io.harness.NGResourceFilterConstants.SIZE_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/**
 * API for plugin metadata
 */

@OwnedBy(HarnessTeam.CI)
@Api("/v1/plugins")
@Path("/v1/plugins")
@NextGenManagerAuth
@Produces({"application/json"})
@Consumes({"application/json"})
@Tag(name = "Plugin schema", description = "Contains APIs related to plugins.")
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
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public interface PluginMetadataResource {
  /**
   * lists the available plugins
   *
   * @param page Page number of navigation
   * @param size Number of entries per page
   * @param searchTerm Filter entries based on plugin name
   */
  @GET
  @Hidden
  @Path("/")
  @ApiOperation(value = "List plugins", nickname = "listPlugins")
  @io.swagger.v3.oas.annotations.Operation(operationId = "listPlugins", summary = "List metadata for available plugins",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "List of available plugins") })
  ResponseDTO<PageResponse<PluginMetadataResponse>>
  list(@Parameter(description = "Page number of navigation. The default value is 0.") @QueryParam(
           PAGE_KEY) @DefaultValue("0") int page,
      @Parameter(description = "Number of entries per page. The default value is 100.") @QueryParam(
          SIZE_KEY) @DefaultValue("20") int size,
      @Parameter(
          description = "This would be used to filter plugins. Any plugin having the specified string in its Name "
              + "will be filtered.") @QueryParam(SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "This would be used to filter plugins. Any plugin having the specified value of kind "
              + "will be filtered.") @QueryParam("kind") String kind);
}
