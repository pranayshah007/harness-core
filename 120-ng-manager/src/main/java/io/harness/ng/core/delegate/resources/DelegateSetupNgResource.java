/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.delegate.resources;

import static io.harness.delegate.utils.RbacConstants.DELEGATE_EDIT_PERMISSION;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_RESOURCE_TYPE;

import static software.wings.service.impl.DelegateServiceImpl.HARNESS_DELEGATE_VALUES_YAML;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateSetupDetails;
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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Api("delegate-setup")
@Path("/delegate-setup")
@Consumes({"application/json"})
@Produces({"application/json"})
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@Tag(name = "Delegate Setup Resource", description = "Contains Delegate Setup APIs")
@ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
public class DelegateSetupNgResource {
  private static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
  private static final String BINARY = "binary";
  private static final String CONTENT_DISPOSITION = "Content-Disposition";
  private static final String ATTACHMENT_FILENAME = "attachment; filename=";
  private static final String YAML = ".yaml";
  private static final String APPLICATION_ZIP_CHARSET_BINARY = "application/zip; charset=binary";
  private static final String TAR_GZ = ".tar.gz";
  private static final String HARNESS_NG_DELEGATE = "harness-ng-delegate";

  private final DelegateNgManagerCgManagerClient delegateNgManagerCgManagerClient;
  private final AccessControlClient accessControlClient;

  @Inject
  public DelegateSetupNgResource(
      DelegateNgManagerCgManagerClient delegateNgManagerCgManagerClient, AccessControlClient accessControlClient) {
    this.delegateNgManagerCgManagerClient = delegateNgManagerCgManagerClient;
    this.accessControlClient = accessControlClient;
  }

  @POST
  @Timed
  @Path("generate-helm-values")
  @ExceptionMetered
  @ApiOperation(value = "Generate helm values yaml file", nickname = "generateNgHelmValuesYaml")
  @Operation(operationId = "generateNgHelmValuesYaml",
      summary = "Generates helm values yaml file from the data specified in request body (Delegate setup details).",
      responses = { @ApiResponse(responseCode = "default", description = "Generated yaml file.") })
  public Response
  generateNgHelmValuesYaml(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(
          required = true, description = "Delegate setup details, containing data to populate yaml file values.")
      DelegateSetupDetails delegateSetupDetails) throws IOException {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_EDIT_PERMISSION);
    String yamlString = RestClientUtils.getResponse(delegateNgManagerCgManagerClient.generateHelmValuesFile(
        accountIdentifier, orgIdentifier, projectIdentifier, delegateSetupDetails));

    // convert String to file and send as response
    File yamlFile = File.createTempFile(HARNESS_DELEGATE_VALUES_YAML, YAML);
    FileUtils.writeStringToFile(yamlFile, yamlString, StandardCharsets.UTF_8);
    return Response.ok(yamlFile)
        .header(CONTENT_TRANSFER_ENCODING, BINARY)
        .type("text/plain; charset=UTF-8")
        .header(CONTENT_DISPOSITION, ATTACHMENT_FILENAME + HARNESS_DELEGATE_VALUES_YAML + YAML)
        .build();
  }
}
