/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.resources;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateListResponse;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.delegate.filter.DelegateFilterPropertiesDTO;
import io.harness.delegate.service.intfc.DelegateInstallationCommandService;
import io.harness.delegate.utilities.DelegateGroupDeleteResponse;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;
import io.harness.service.intfc.DelegateSetupService;

import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.DelegateService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import lombok.extern.slf4j.Slf4j;
import retrofit2.http.Body;

@Api("/delegate-setup/internal")
@Path("/delegate-setup/internal")
@Produces("application/json")
@Scope(DELEGATE)
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@Hidden
@InternalApi
public class DelegateNgSetupInternalResource {
  private final DelegateService delegateService;
  private final SubdomainUrlHelperIntfc subdomainUrlHelper;

  private final DelegateSetupService delegateSetupService;
  private final DelegateInstallationCommandService delegateInstallationCommandService;

  @Inject
  public DelegateNgSetupInternalResource(DelegateService delegateService, SubdomainUrlHelperIntfc subdomainUrlHelper,
      DelegateInstallationCommandService delegateInstallationCommandService,
      DelegateSetupService delegateSetupService) {
    this.delegateService = delegateService;
    this.subdomainUrlHelper = subdomainUrlHelper;
    this.delegateInstallationCommandService = delegateInstallationCommandService;
    this.delegateSetupService = delegateSetupService;
  }

  @POST
  @Path("delegate-helm-values-yaml")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<String> generateNgHelmValuesYaml(@Context HttpServletRequest request,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(
          required = true, description = "Delegate setup details, containing data to populate yaml file values.")
      DelegateSetupDetails delegateSetupDetails) throws IOException {
    File delegateFile = delegateService.generateNgHelmValuesYaml(accountIdentifier, delegateSetupDetails,
        subdomainUrlHelper.getManagerUrl(request, accountIdentifier), getVerificationUrl(request));
    return new RestResponse<>(new String(Files.readAllBytes(Paths.get(delegateFile.getAbsolutePath()))));
  }

  private String getVerificationUrl(HttpServletRequest request) {
    return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
  }

  @GET
  @Path("delegate-terraform-module-file")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<String> generateTerraformExampleModuleFile(@Context HttpServletRequest request,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) throws IOException {
    final String managerUrl = subdomainUrlHelper.getManagerUrl(request, accountIdentifier);
    final DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgIdentifier, projectIdentifier);
    String terraformExampleModuleFile =
        delegateInstallationCommandService.getTerraformExampleModuleFile(managerUrl, accountIdentifier, owner);
    return new RestResponse<>(terraformExampleModuleFile);
  }

  @DELETE
  @Path("delegate")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<DelegateGroupDeleteResponse> deleteDelegateGroup(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.IDENTIFIER_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.DELEGATE_IDENTIFIER_KEY) String delegateGroupIdentifier) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.deleteDelegateGroupV3(
          accountIdentifier, orgIdentifier, projectIdentifier, delegateGroupIdentifier));
    }
  }

  @POST
  @Path("list")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<List<DelegateListResponse>> listV2(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body @RequestBody(description = "Details of the Delegate filter properties to be applied")
      DelegateFilterPropertiesDTO delegateFilterPropertiesDTO) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateSetupService.listDelegates(
          accountIdentifier, orgIdentifier, projectIdentifier, delegateFilterPropertiesDTO));
    }
  }
}
