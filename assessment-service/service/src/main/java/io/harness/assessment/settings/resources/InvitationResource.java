/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.resources;

import io.harness.assessment.settings.beans.dto.AssessmentInviteDTO;
import io.harness.assessment.settings.services.InvitationService;
import io.harness.eraro.ResponseMessage;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Path("/v1/assessment")
@Api("invitation")
public class InvitationResource {
  InvitationService invitationService;
  @POST
  @Consumes({"application/json"})
  @Produces({"application/json"})
  @Path("invite")
  /*  @Operation(
            operationId = "sendAssessmentInvite",
            summary = "Send Invitation of assessments to user email",
            description = "Creates invites in the system with unique sign-up code, which is sent to the respective
    emails", tags = {"Assessment"}
    )
    @ApiResponses({@ApiResponse(
            responseCode = "200",
            description = "Success response",
            content = {@Content(
                    mediaType = "application/json",
                    schema = @Schema(
                            implementation = AssessmentInviteDTO.class
                    )
            )}
    )})*/
  @ApiOperation(
      value = "Creates invites in the system with unique sign-up code, which is sent to the respective emails",
      nickname = "sendAssessmentInvite", response = AssessmentInviteDTO.class)
  public Response
  sendAssessmentInvite(@Valid AssessmentInviteDTO body) {
    // A uniquely generated non guessable link.
    try {
      return Response.status(Response.Status.OK).entity(invitationService.sendAssessmentInvite(body)).build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
