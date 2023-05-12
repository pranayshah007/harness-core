/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.delegate.resources;

import static io.harness.delegate.utils.RbacConstants.DELEGATE_DELETE_PERMISSION;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_EDIT_PERMISSION;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_RESOURCE_TYPE;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_VIEW_PERMISSION;

import static software.wings.service.impl.DelegateServiceImpl.HARNESS_DELEGATE_VALUES_YAML;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateListResponse;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.delegate.beans.SupportedDelegateVersion;
import io.harness.delegate.filter.DelegateFilterPropertiesDTO;
import io.harness.delegate.utilities.DelegateDeleteResponse;
import io.harness.delegate.utilities.DelegateGroupDeleteResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.delegate.client.DelegateNgManagerCgManagerClient;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.remote.client.CGRestUtils;
import io.harness.rest.RestResponse;

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
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.hibernate.validator.constraints.Range;
import retrofit2.http.Body;

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
  private static final String TF = ".tf";

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
    String yamlString = CGRestUtils.getResponse(delegateNgManagerCgManagerClient.generateHelmValuesFile(
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

  @GET
  @Timed
  @Path("delegate-terraform-module-file")
  @ExceptionMetered
  @ApiOperation(value = "Generate delegate terraform example module file", nickname = "generateTerraformModule")
  @Operation(operationId = "generateTerraformModule",
      summary = "Generates delegate terraform example module file from the account",
      responses = { @ApiResponse(responseCode = "default", description = "Generated terraform module file.") })
  public Response
  generateNgHelmValuesYaml(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) throws IOException {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_EDIT_PERMISSION);
    String fileString = CGRestUtils.getResponse(
        delegateNgManagerCgManagerClient.getTerraformModuleFile(accountIdentifier, orgIdentifier, projectIdentifier));

    // convert String to file and send as response
    File moduleFile = File.createTempFile("main", TF);
    FileUtils.writeStringToFile(moduleFile, fileString, StandardCharsets.UTF_8);
    return Response.ok(moduleFile)
        .header(CONTENT_TRANSFER_ENCODING, BINARY)
        .type("text/plain; charset=UTF-8")
        .header(CONTENT_DISPOSITION, ATTACHMENT_FILENAME + HARNESS_DELEGATE_VALUES_YAML + YAML)
        .build();
  }

  @DELETE
  @Path("delegate/{delegateIdentifier}")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Deletes delegate", nickname = "deleteDelegate")
  @Operation(operationId = "deleteDelegate", summary = "Deletes a Delegate by its identifier.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "200 Ok response if everything successfully deleted delegate")
      })
  public RestResponse<DelegateDeleteResponse>
  deleteDelegateGroup(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.IDENTIFIER_PARAM_MESSAGE) @PathParam(
          NGCommonEntityConstants.DELEGATE_IDENTIFIER_KEY) @NotEmpty String delegateGroupIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(DELEGATE_RESOURCE_TYPE, delegateGroupIdentifier), DELEGATE_DELETE_PERMISSION);

    DelegateGroupDeleteResponse groupDeleteResponse =
        CGRestUtils.getResponse(delegateNgManagerCgManagerClient.deleteDelegateGroup(
            accountIdentifier, orgIdentifier, projectIdentifier, delegateGroupIdentifier));

    if (!groupDeleteResponse.isStatusSuccess()) {
      throw new InvalidRequestException(groupDeleteResponse.getErrorMsg());
    }
    return new RestResponse<>(new DelegateDeleteResponse("Successfully deleted delegate."));
  }

  @GET
  @Timed
  @Path("latest-supported-version")
  @ExceptionMetered
  @ApiOperation(value = "Gets the latest supported delegate version", nickname = "publishedDelegateVersion")
  @Operation(operationId = "publishedDelegateVersion",
      summary =
          "Gets the latest supported delegate version. The version has YY.MM.XXXXX format. You can use any version lower than the returned results(upto 3 months old)",
      responses =
      { @ApiResponse(responseCode = "default", description = "Gets the latest supported delegate version") })
  public RestResponse<SupportedDelegateVersion>
  publishedDelegateVersion(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier) throws IOException {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, null, null),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_EDIT_PERMISSION);
    SupportedDelegateVersion supportedDelegateVersion =
        CGRestUtils.getResponse(delegateNgManagerCgManagerClient.getPublishedDelegateVersion(accountIdentifier));

    return new RestResponse<>(supportedDelegateVersion);
  }

  @PUT
  @Path("/override-delegate-tag")
  @ApiOperation(value = "Overrides delegate image tag for account", nickname = "overrideDelegateImageTag")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "overrideDelegateImageTag", summary = "Overrides delegate image tag for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delegate Image Tag")
      })
  public RestResponse<String>
  setDelegateTagOverride(@NotEmpty @QueryParam("accountIdentifier") final String accountId,
      @NotEmpty @QueryParam("delegateTag") final String delegateTag,
      @QueryParam("validTillNextRelease") @DefaultValue("false") final Boolean validTillNextRelease,
      @Range(max = 90) @QueryParam("validForDays") @DefaultValue("30") final int validForDays) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, null, null), Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_EDIT_PERMISSION);
    String delegateImage = CGRestUtils.getResponse(delegateNgManagerCgManagerClient.overrideDelegateImage(
        accountId, delegateTag, validTillNextRelease, validForDays));
    return new RestResponse<>(String.format("Updated Delegate image tag to %s", delegateImage));
  }

  @POST
  @Timed
  @Path("listDelegates")
  @ExceptionMetered
  @ApiOperation(value = "Lists all delegates in NG", nickname = "listDelegates")
  @Operation(operationId = "listDelegates", summary = "Lists all delegates in NG filtered by provided conditions",
      responses = { @ApiResponse(responseCode = "default", description = "Lists all delegates in NG") })
  public RestResponse<List<DelegateListResponse>>
  listDelegates(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                    NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body @RequestBody(description = "Details of the Delegate filter properties to be applied")
      DelegateFilterPropertiesDTO delegateFilterPropertiesDTO) throws IOException {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);

    return new RestResponse<>(CGRestUtils.getResponse(delegateNgManagerCgManagerClient.getDelegates(
        accountIdentifier, orgIdentifier, projectIdentifier, delegateFilterPropertiesDTO)));
  }
}
