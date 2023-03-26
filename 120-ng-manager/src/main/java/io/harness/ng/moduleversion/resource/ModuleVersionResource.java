/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.moduleversion.resource;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;
import static software.wings.security.PermissionAttribute.ResourceType.APPLICATION;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.cdng.moduleversioninfo.entity.MicroservicesVersionInfo;
import io.harness.cdng.moduleversioninfo.entity.ModuleVersionInfo;
import io.harness.ng.moduleversioninfo.service.ModuleVersionInfoService;
import io.harness.spec.server.ng.v1.ModuleVersionsApi;
import io.harness.spec.server.ng.v1.model.MicroserviceVersionInfo;
import io.harness.spec.server.ng.v1.model.ModuleVersionsResponse;
import io.harness.utils.ApiUtils;

import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.stream.Collectors;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

/**
 * @author rajendrabaviskar
 */
@Produces("application/json")
@Scope(APPLICATION)
@AuthRule(permissionType = LOGGED_IN)
public class ModuleVersionResource implements ModuleVersionsApi {
  private ModuleVersionInfoService moduleVersionInfoService;
  /**
   * Instantiates a new module version resource.
   *
   * @param moduleVersionInfoService the moduleVersionInfoService service
   */
  @Inject
  public ModuleVersionResource(ModuleVersionInfoService moduleVersionInfoService) {
    this.moduleVersionInfoService = moduleVersionInfoService;
  }

  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public Response listModuleVersions(Integer page, Integer limit) {
    PageRequest<ModuleVersionInfo> moduleVersionInfoPageRequest =
        aPageRequest().withOffset(String.valueOf(page)).withLimit(String.valueOf(limit)).build();
    PageResponse<ModuleVersionInfo> pageResponse =
        moduleVersionInfoService.getCurrentVersionOfAllModules(moduleVersionInfoPageRequest);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, pageResponse.getTotal(), page, limit);
    return responseBuilderWithLinks
        .entity(pageResponse.getResponse().stream().map(this::getModuleVersionsResponse).collect(Collectors.toList()))
        .build();
  }

  private ModuleVersionsResponse getModuleVersionsResponse(ModuleVersionInfo versioningInfo) {
    ModuleVersionsResponse moduleVersionsResponse = new ModuleVersionsResponse();
    moduleVersionsResponse.setName(versioningInfo.getModuleName());
    moduleVersionsResponse.setVersion(versioningInfo.getVersion());
    moduleVersionsResponse.setDisplayName(versioningInfo.getDisplayName());
    moduleVersionsResponse.setVersionUrl(versioningInfo.getVersionUrl());
    moduleVersionsResponse.setUpdated(versioningInfo.getLastModifiedAt());
    moduleVersionsResponse.setReleaseNotesLink(versioningInfo.getReleaseNotesLink());
    if (versioningInfo.getMicroservicesVersionInfo() == null) {
      return moduleVersionsResponse;
    }
    moduleVersionsResponse.setMicroservicesVersionInfo(versioningInfo.getMicroservicesVersionInfo()
                                                           .stream()
                                                           .map(this::getMicroserviceVersionInfo)
                                                           .collect(Collectors.toList()));
    return moduleVersionsResponse;
  }

  private MicroserviceVersionInfo getMicroserviceVersionInfo(MicroservicesVersionInfo entity) {
    MicroserviceVersionInfo dto = new MicroserviceVersionInfo();
    dto.setName(entity.getName());
    dto.setVersion(entity.getVersion());
    dto.setVersionUrl(entity.getVersionUrl());
    return dto;
  }
}
