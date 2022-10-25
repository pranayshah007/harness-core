/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.resources;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.grpc.IdentifierKeys.DELEGATE_ID;

import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.event.PublishRequest;
import io.harness.event.PublishResponse;
import io.harness.event.service.intfc.EventPublisherService;
import io.harness.security.annotations.PublicApi;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.dropwizard.jersey.protobuf.ProtocolBufferMediaType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Api("/k8sevent")
@Path("/k8sevent")
@Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
@PublicApi
@Slf4j
@ExposeInternalException
@OwnedBy(CE)
public class K8sEventResource {
  private final EventPublisherService eventPublisherService;

  @Inject
  public K8sEventResource(EventPublisherService eventPublisherService) {
    this.eventPublisherService = eventPublisherService;
  }

  @POST
  @Path("/publish")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "publish", nickname = "publish")
  public Response publish(
      @QueryParam("accountId") String accountId, @RequestBody(description = "Publish Request") PublishRequest request) {
    log.info(
        "Received publish request with {} messages via rest for accountId: {}", request.getMessagesCount(), accountId);
    String delegateId = request.getMessages(0).getAttributesMap().getOrDefault(DELEGATE_ID, "");
    try {
      eventPublisherService.publish(accountId, delegateId, request.getMessagesList(), request.getMessagesCount());
      return Response.ok(PublishResponse.newBuilder().build()).build();
    } catch (Exception e) {
      log.error("Exception in Event Publisher Service", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(PublishResponse.newBuilder().build())
          .build();
    }
  }
}
