/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.settings.resources;

import static io.harness.idp.constants.Constants.IDP_SETTINGS;
import static io.harness.idp.constants.Constants.MANAGE_PERMISSION;

import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.spec.server.idp.v1.BackstagePermissionsApi;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;
import io.harness.spec.server.idp.v1.model.BackstagePermissionsRequest;
import io.harness.spec.server.idp.v1.model.BackstagePermissionsResponse;

import com.google.inject.Inject;
import java.util.Optional;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class BackstagePermissionsApiImpl implements BackstagePermissionsApi {
  private BackstagePermissionsService backstagePermissionsService;
  @Override
  public Response getBackstagePermissions(String harnessAccount) {
    Optional<BackstagePermissions> backstagePermissions =
        backstagePermissionsService.findByAccountIdentifier(harnessAccount);
    BackstagePermissionsResponse backstagePermissionsResponseResponse = new BackstagePermissionsResponse();
    if (backstagePermissions.isEmpty()) {
      log.warn("Could not fetch permissions for the account {}", harnessAccount);
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    backstagePermissionsResponseResponse.setData(backstagePermissions.get());
    return Response.status(Response.Status.OK).entity(backstagePermissionsResponseResponse).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_SETTINGS, permission = MANAGE_PERMISSION)
  public Response createBackstagePermissions(@Valid BackstagePermissionsRequest body, String harnessAccount) {
    BackstagePermissions backstagePermissions;
    try {
      backstagePermissions = backstagePermissionsService.createPermissions(body.getData(), harnessAccount);
    } catch (Exception e) {
      log.error("Could not create permissions", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    BackstagePermissionsResponse backstagePermissionsResponse = new BackstagePermissionsResponse();
    backstagePermissionsResponse.setData(backstagePermissions);
    return Response.status(Response.Status.CREATED).entity(backstagePermissionsResponse).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_SETTINGS, permission = MANAGE_PERMISSION)
  public Response updateBackstagePermissions(@Valid BackstagePermissionsRequest body, String harnessAccount) {
    BackstagePermissions backstagePermissions;
    try {
      backstagePermissions = backstagePermissionsService.updatePermissions(body.getData(), harnessAccount);
    } catch (Exception e) {
      log.error("Could not update permissions", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    BackstagePermissionsResponse backstagePermissionsResponse = new BackstagePermissionsResponse();
    backstagePermissionsResponse.setData(backstagePermissions);
    return Response.status(Response.Status.OK).entity(backstagePermissionsResponse).build();
  }
}
