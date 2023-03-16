/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.resources;

import static io.harness.idp.common.Constants.IDP_PERMISSION;
import static io.harness.idp.common.Constants.IDP_RESOURCE_TYPE;
import static io.harness.idp.onboarding.utils.Constants.UI_DEFAULT_PAGE;
import static io.harness.idp.onboarding.utils.Constants.UI_DEFAULT_PAGE_LIMIT;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.onboarding.services.OnboardingService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.dto.PrincipalType;
import io.harness.spec.server.idp.v1.OnboardingResourceApi;
import io.harness.spec.server.idp.v1.model.HarnessEntitiesResponse;
import io.harness.spec.server.idp.v1.model.ImportEntitiesResponse;
import io.harness.spec.server.idp.v1.model.ImportHarnessEntitiesRequest;
import io.harness.spec.server.idp.v1.model.ManualImportEntityRequest;
import io.harness.spec.server.idp.v1.model.OnboardingAccessCheckResponse;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class OnboardingResourceApiImpl implements OnboardingResourceApi {
  private OnboardingService onboardingService;

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response onboardingAccessCheck(@AccountIdentifier String harnessAccount) {
    log.info("Request received to check idp onboarding access for account = {}", harnessAccount);
    if (SecurityContextBuilder.getPrincipal().getType() != PrincipalType.USER) {
      log.error("Principal type is not USER, cannot perform idp onboarding");
      throw new InvalidRequestException("Harness IDP Onboarding allowed only for User Type");
    }
    OnboardingAccessCheckResponse onboardingAccessCheckResponse =
        onboardingService.accessCheck(harnessAccount, SecurityContextBuilder.getPrincipal().getName());
    return Response.status(Response.Status.OK).entity(onboardingAccessCheckResponse).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response getHarnessEntities(@AccountIdentifier String harnessAccount, Integer page, Integer limit, String sort,
      String order, String searchTerm) {
    log.info("Request received to get harness entities for idp import. Account = {}", harnessAccount);
    int pageIndex = page == null ? UI_DEFAULT_PAGE : page;
    int pageLimit = limit == null ? UI_DEFAULT_PAGE_LIMIT : limit;
    HarnessEntitiesResponse harnessEntities =
        onboardingService.getHarnessEntities(harnessAccount, pageIndex, pageLimit, sort, order, searchTerm);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks = ApiUtils.addLinksHeader(responseBuilder,
        harnessEntities.getOrgCount() + harnessEntities.getProjectCount() + harnessEntities.getServiceCount(),
        pageIndex, pageLimit);
    return responseBuilderWithLinks.entity(harnessEntities).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response importHarnessEntities(
      @Valid ImportHarnessEntitiesRequest importHarnessEntitiesRequest, @AccountIdentifier String harnessAccount) {
    log.info("Request received to import harness entities to IDP. Account = {}, Request = {}", harnessAccount,
        importHarnessEntitiesRequest);
    ImportEntitiesResponse importHarnessEntities =
        onboardingService.importHarnessEntities(harnessAccount, importHarnessEntitiesRequest);
    return Response.status(Response.Status.OK).entity(importHarnessEntities).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response manualImportEntity(
      @Valid ManualImportEntityRequest manualImportEntityRequest, @AccountIdentifier String harnessAccount) {
    log.info("Request received to import entity manually to IDP. Account = {}, Request = {}", harnessAccount,
        manualImportEntityRequest);
    ImportEntitiesResponse importHarnessEntities =
        onboardingService.manualImportEntity(harnessAccount, manualImportEntityRequest);
    return Response.status(Response.Status.OK).entity(importHarnessEntities).build();
  }
}
