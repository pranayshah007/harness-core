/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.resources;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.Team;
import io.harness.notification.remote.dto.TemplateDTO;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.io.InputStream;
import java.util.List;
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
import javax.ws.rs.core.MediaType;
import org.glassfish.jersey.media.multipart.FormDataParam;

@OwnedBy(PL)
@Api("templates")
@Path("templates")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public interface NotificationTemplateResource {
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @POST
  @ApiOperation(value = "Create a template", nickname = "postTemplate")
  ResponseDTO<TemplateDTO> createTemplate(@FormDataParam("file") InputStream inputStream,
      @FormDataParam("team") Team team, @FormDataParam("identifier") String identifier,
      @FormDataParam("harnessManaged") Boolean harnessManaged);

  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @PUT
  @Path("/insertOrUpdate")
  @ApiOperation(value = "Update a template if exists else create", nickname = "insertOrUpdateTemplate")
  ResponseDTO<TemplateDTO> insertOrUpdateTemplate(@FormDataParam("file") InputStream inputStream,
      @QueryParam("team") Team team, @QueryParam("identifier") String identifier,
      @QueryParam("harnessManaged") Boolean harnessManaged);

  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @PUT
  @Path("/{identifier}")
  @ApiOperation(value = "Update a template", nickname = "putTemplate")
  ResponseDTO<TemplateDTO> updateTemplate(@FormDataParam("file") InputStream inputStream,
      @QueryParam("team") @NotNull Team team, @PathParam("identifier") String templateIdentifier,
      @PathParam("harnessManaged") Boolean harnessManaged);

  @DELETE
  @Path("/{identifier}")
  @ApiOperation(value = "Delete a template", nickname = "deleteTemplate")
  ResponseDTO<Boolean> deleteTemplate(
      @PathParam("identifier") String templateIdentifier, @QueryParam("team") @NotNull Team team);

  @GET
  @ApiOperation(value = "Get templates", nickname = "getTemplates")
  ResponseDTO<List<TemplateDTO>> getTemplates(@QueryParam("team") @NotNull Team team);

  @GET
  @Path("/{identifier}")
  @ApiOperation(value = "Get template by identifier", nickname = "getTemplate")
  ResponseDTO<TemplateDTO> getTemplate(@PathParam("identifier") String identifier, @QueryParam("team") Team team);
}
