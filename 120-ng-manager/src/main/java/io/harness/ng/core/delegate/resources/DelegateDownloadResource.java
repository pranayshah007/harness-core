/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.delegate.resources;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_EDIT_PERMISSION;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_RESOURCE_TYPE;

import static software.wings.service.impl.DelegateServiceImpl.KUBERNETES_DELEGATE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.DelegateDownloadResponse;
import io.harness.delegate.beans.DelegateDownloadRequest;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.delegate.client.DelegateNgManagerCgManagerClient;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.remote.client.RestClientUtils;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
import org.hibernate.validator.constraints.NotEmpty;

@Api("download-delegates")
@Path("/download-delegates")
@Produces("application/json")
@Consumes({"application/json"})
@OwnedBy(HarnessTeam.DEL)
@Tag(name = "Delegate Download Resource", description = "Contains APIs related to Downloading Delegates")
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
public class DelegateDownloadResource {
  private static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
  private static final String BINARY = "binary";
  public static final String YAML = ".yaml";
  private static final String CONTENT_DISPOSITION = "Content-Disposition";
  private static final String ATTACHMENT_FILENAME = "attachment; filename=";
  private static final String DOCKER_COMPOSE = "docker-compose";
  private static final String HARNESS_DELEGATE = "harness-delegate";

  private final AccessControlClient accessControlClient;
  private final DelegateNgManagerCgManagerClient delegateNgManagerCgManagerClient;

  @Inject
  public DelegateDownloadResource(
      AccessControlClient accessControlClient, DelegateNgManagerCgManagerClient delegateNgManagerCgManagerClient) {
    this.accessControlClient = accessControlClient;
    this.delegateNgManagerCgManagerClient = delegateNgManagerCgManagerClient;
  }

  @POST
  @Path("/kubernetes")
  @ApiOperation(value = "Downloads a kubernetes delegate yaml file.", nickname = "downloadKubernetesDelegateYaml")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "downloadKubernetesDelegateYaml", summary = "Downloads a kubernetes delegate yaml file.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delegate Yaml File.")
      })
  public Response
  downloadKubernetesDelegate(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                 NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(required = true, description = "Parameters needed for downloading kubernetes delegate yaml")
      DelegateDownloadRequest delegateDownloadRequest) throws IOException {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_EDIT_PERMISSION);
    DelegateDownloadResponse delegateDownloadResponse =
        RestClientUtils.getResponse(delegateNgManagerCgManagerClient.downloadKubernetesDelegate(
            accountIdentifier, orgIdentifier, projectIdentifier, delegateDownloadRequest));
    if (isNotEmpty(delegateDownloadResponse.getErrorMsg())) {
      throw new InvalidRequestException(delegateDownloadResponse.getErrorMsg());
    }

    String yamlString = delegateDownloadResponse.getDelegateYaml();
    File yamlFile = File.createTempFile(HARNESS_DELEGATE, YAML);
    FileUtils.writeStringToFile(yamlFile, yamlString, StandardCharsets.UTF_8);

    return Response.ok(yamlFile)
        .header(CONTENT_TRANSFER_ENCODING, BINARY)
        .type("text/plain; charset=UTF-8")
        .header(CONTENT_DISPOSITION, ATTACHMENT_FILENAME + KUBERNETES_DELEGATE + YAML)
        .build();
  }

  @POST
  @Path("/docker")
  @ApiOperation(value = "Downloads a docker delegate yaml file.", nickname = "downloadDockerDelegateYaml")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "downloadDockerDelegateYaml", summary = "Downloads a docker delegate yaml file.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delegate Yaml File.")
      })
  public Response
  downloadDockerDelegate(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                             NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(required = true, description = "Parameters needed for downloading docker delegate yaml")
      DelegateDownloadRequest delegateDownloadRequest) throws IOException {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_EDIT_PERMISSION);
    DelegateDownloadResponse delegateDownloadResponse =
        RestClientUtils.getResponse(delegateNgManagerCgManagerClient.downloadDockerDelegate(
            accountIdentifier, orgIdentifier, projectIdentifier, delegateDownloadRequest));
    if (isNotEmpty(delegateDownloadResponse.getErrorMsg())) {
      throw new InvalidRequestException(delegateDownloadResponse.getErrorMsg());
    }

    String yamlString = delegateDownloadResponse.getDelegateYaml();
    File yamlFile = File.createTempFile(HARNESS_DELEGATE, YAML);
    FileUtils.writeStringToFile(yamlFile, yamlString, StandardCharsets.UTF_8);

    return Response.ok(yamlFile)
        .header(CONTENT_TRANSFER_ENCODING, BINARY)
        .type("text/plain; charset=UTF-8")
        .header(CONTENT_DISPOSITION, ATTACHMENT_FILENAME + DOCKER_COMPOSE + YAML)
        .build();
  }
}
