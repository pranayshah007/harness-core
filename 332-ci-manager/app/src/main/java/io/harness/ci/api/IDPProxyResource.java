/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.resources;

import com.google.inject.Inject;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.delegate.task.http.HttpTaskParametersNg;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.http.HttpHeaderConfig;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.waiter.WaitNotifyEngine;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import software.wings.beans.TaskType;

import javax.ws.rs.*;
import javax.ws.rs.core.*;

import java.util.ArrayList;
import java.util.List;

import static io.harness.annotations.dev.HarnessTeam.CI;

@Slf4j
@OwnedBy(CI)
@Api("/idp-proxy")
@Path("/idp-proxy")
@Produces({"application/json", "text/yaml", "text/html", "text/plain"})
@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
@AllArgsConstructor(onConstructor = @__({ @Inject}))
@ApiResponses(value =
        {
                @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
                , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
        })
public class IDPProxyResource {
    DelegateProxyRequestForwarder delegateProxyRequestForwarder;

    @GET

    @Path("/accountId/{accountId}/url/{url: .+}")
    public Response GetForwardProxyRequest(@Context UriInfo info, @Context javax.ws.rs.core.HttpHeaders headers, @PathParam("accountId") String accountId, @PathParam("url") String url) {
        log.info("Starting the proxy task");

        UriBuilder uriBuilder = delegateProxyRequestForwarder.CreateUrlWithQueryParameters(url,info.getQueryParameters());

        List<HttpHeaderConfig> headerList = delegateProxyRequestForwarder.CreateHeaderConfig(headers);

        HttpStepResponse httpResponse = delegateProxyRequestForwarder.ForwardRequestToDelegate(
                accountId,
                uriBuilder.toString(),
                headerList,
                "",
                "GET");

        // This is for async callback
    /*try {
      String taskId = delegateGrpcClientWrapper.submitAsyncTask(delegateTaskRequest, Duration.ZERO);
      log.info("Task Successfully queued with taskId: {}", taskId);
      waitNotifyEngine.waitForAllOn(NG_ORCHESTRATION, new HttpTaskNotifyCallback(), taskId);
    } catch (DelegateServiceDriverException ex) {
      log.error("Delegate error: ", ex);
    }*/

        return Response.status(httpResponse.getHttpResponseCode()).entity(httpResponse.getHttpResponseBody()).build();

    }


    @POST
    @Path("/accountId/{accountId}/url/{url: .+}")
    public Response PostForwardProxyRequest(@Context UriInfo info, @Context javax.ws.rs.core.HttpHeaders headers, @PathParam("accountId") String accountId, @PathParam("url") String url, String body) {
        log.info("Starting the proxy task");

        UriBuilder uriBuilder = delegateProxyRequestForwarder.CreateUrlWithQueryParameters(url,info.getQueryParameters());

        List<HttpHeaderConfig> headerList = delegateProxyRequestForwarder.CreateHeaderConfig(headers);

        HttpStepResponse httpResponse = delegateProxyRequestForwarder.ForwardRequestToDelegate(
                accountId,
                uriBuilder.toString(),
                headerList,
                body,
                "POST");

        return Response.status(httpResponse.getHttpResponseCode())
                .entity(httpResponse.getHttpResponseBody())
                .build();

    }

    @PUT
    @Path("/accountId/{accountId}/url/{url: .+}")
    public Response PutForwardProxyRequest(@Context UriInfo info, @Context javax.ws.rs.core.HttpHeaders headers, @PathParam("accountId") String accountId, @PathParam("url") String url, String body) {
        log.info("Starting the proxy task");

        UriBuilder uriBuilder = delegateProxyRequestForwarder.CreateUrlWithQueryParameters(url,info.getQueryParameters());

        List<HttpHeaderConfig> headerList = delegateProxyRequestForwarder.CreateHeaderConfig(headers);

        HttpStepResponse httpResponse = delegateProxyRequestForwarder.ForwardRequestToDelegate(
                accountId,
                uriBuilder.toString(),
                headerList,
                body,
                "PUT");

        return Response.status(httpResponse.getHttpResponseCode())
                .entity(httpResponse.getHttpResponseBody())
                .build();

    }

    @DELETE
    @Path("/accountId/{accountId}/url/{url: .+}")
    public Response DeleteForwardProxyRequest(@Context UriInfo info, @Context javax.ws.rs.core.HttpHeaders headers, @PathParam("accountId") String accountId, @PathParam("url") String url, String body) {
        log.info("Starting the proxy task");

        UriBuilder uriBuilder = delegateProxyRequestForwarder.CreateUrlWithQueryParameters(url,info.getQueryParameters());

        List<HttpHeaderConfig> headerList = delegateProxyRequestForwarder.CreateHeaderConfig(headers);

        HttpStepResponse httpResponse = delegateProxyRequestForwarder.ForwardRequestToDelegate(
                accountId,
                uriBuilder.toString(),
                headerList,
                body,
                "DELETE");

        return Response.status(httpResponse.getHttpResponseCode())
                .entity(httpResponse.getHttpResponseBody())
                .build();

    }

}
