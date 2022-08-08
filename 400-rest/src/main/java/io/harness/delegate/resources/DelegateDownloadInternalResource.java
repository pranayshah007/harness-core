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
import io.harness.delegate.DelegateDownloadResponse;
import io.harness.delegate.beans.DelegateDownloadRequest;
import io.harness.delegate.service.intfc.DelegateDownloadService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;

import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.security.annotations.Scope;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;

@Api("/delegate-download")
@Path("/delegate-download")
@Produces("application/json")
@Scope(DELEGATE)
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@Hidden
@InternalApi
public class DelegateDownloadInternalResource {
  private final DelegateDownloadService delegateDownloadService;
  private final SubdomainUrlHelperIntfc subdomainUrlHelper;

  @Inject
  public DelegateDownloadInternalResource(
      DelegateDownloadService delegateDownloadService, SubdomainUrlHelperIntfc subdomainUrlHelper) {
    this.delegateDownloadService = delegateDownloadService;
    this.subdomainUrlHelper = subdomainUrlHelper;
  }

  @POST
  @Path("/kubernetes")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<DelegateDownloadResponse> downloadKubernetesDelegate(@Context HttpServletRequest request,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody() DelegateDownloadRequest delegateDownloadRequest) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR)) {
      String managerUrl = subdomainUrlHelper.getManagerUrl(request, accountIdentifier);
      DelegateDownloadResponse delegateDownloadResponse =
          delegateDownloadService.downloadKubernetesDelegate(accountIdentifier, orgIdentifier, projectIdentifier,
              delegateDownloadRequest, managerUrl, getVerificationServiceUrl(request));
      return new RestResponse<>(delegateDownloadResponse);
    }
  }

  @POST
  @Path("/docker")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<DelegateDownloadResponse> downloadDockerDelegate(@Context HttpServletRequest request,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody() DelegateDownloadRequest delegateDownloadRequest) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR)) {
      String managerUrl = subdomainUrlHelper.getManagerUrl(request, accountIdentifier);
      DelegateDownloadResponse delegateDownloadResponse =
          delegateDownloadService.downloadDockerDelegate(accountIdentifier, orgIdentifier, projectIdentifier,
              delegateDownloadRequest, managerUrl, getVerificationServiceUrl(request));
      return new RestResponse<>(delegateDownloadResponse);
    }
  }

  private String getVerificationServiceUrl(HttpServletRequest request) {
    return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
  }
}
