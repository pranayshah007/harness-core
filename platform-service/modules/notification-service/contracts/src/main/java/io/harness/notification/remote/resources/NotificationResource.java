/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.resources;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.Team;
import io.harness.notification.remote.dto.NotificationDTO;

import software.wings.beans.notification.BotQuestion;
import software.wings.beans.notification.BotResponse;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@OwnedBy(PL)
@Api("notifications")
@Path("notifications")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public interface NotificationResource {
  @GET
  @Path("/{id}")
  @ApiOperation(value = "Get details of a notification", nickname = "getNotification")
  ResponseDTO<NotificationDTO> get(@PathParam("id") String id);

  @GET
  @ApiOperation(value = "List notifications", nickname = "listNotifications")
  ResponseDTO<PageResponse<NotificationDTO>> list(@QueryParam("team") Team team, @BeanParam PageRequest pageRequest);

  @POST
  @Path("/harness-bot")
  @ApiOperation(value = "Get response from Harness Support Bot", nickname = "harnessSupportBot")
  ResponseDTO<BotResponse> answer(BotQuestion question);
}
