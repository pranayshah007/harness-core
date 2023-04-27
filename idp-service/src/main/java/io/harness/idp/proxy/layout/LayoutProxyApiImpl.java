/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.layout;

import static io.harness.idp.common.Constants.IDP_PERMISSION;
import static io.harness.idp.common.Constants.IDP_RESOURCE_TYPE;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.clients.BackstageResourceClient;
import io.harness.eraro.ResponseMessage;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.LayoutProxyApi;
import io.harness.spec.server.idp.v1.model.LayoutIngestRequest;
import io.harness.spec.server.idp.v1.model.LayoutRequest;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class LayoutProxyApiImpl implements LayoutProxyApi {
  BackstageResourceClient backstageResourceClient;

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response createLayout(@Valid LayoutRequest body, @AccountIdentifier String harnessAccount) {
    try {
      Object entity = getGeneralResponse(backstageResourceClient.createLayout(body, harnessAccount));
      return Response.ok(entity).build();
    } catch (Exception ex) {
      log.error("Error in createLayout - account = {}, error = {}", harnessAccount, ex.getMessage(), ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(ex.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response deleteLayout(@Valid LayoutRequest body, @AccountIdentifier String harnessAccount) {
    try {
      Object entity = getGeneralResponse(backstageResourceClient.deleteLayout(body, harnessAccount));
      return Response.ok(entity).build();
    } catch (Exception ex) {
      log.error("Error in deleteLayout - account = {}, error = {}", harnessAccount, ex.getMessage(), ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(ex.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getAllLayouts(String harnessAccount) {
    try {
      Object entity = getGeneralResponse(backstageResourceClient.getAllLayouts(harnessAccount));
      return Response.ok(entity).build();
    } catch (Exception ex) {
      log.error("Error in getAllLayouts - account = {}, error = {}", harnessAccount, ex.getMessage(), ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(ex.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getLayout(String layoutIdentifier, String harnessAccount) {
    try {
      Object entity = getGeneralResponse(backstageResourceClient.getLayout(harnessAccount, layoutIdentifier));
      return Response.ok(entity).build();
    } catch (Exception ex) {
      log.error("Error in getLayout - account = {}, error = {}", harnessAccount, ex.getMessage(), ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(ex.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getLayoutHealth(String harnessAccount) {
    try {
      Object entity = getGeneralResponse(backstageResourceClient.getHealth(harnessAccount));
      return Response.ok(entity).build();
    } catch (Exception ex) {
      log.error("Error in getLayoutHealth - account = {}, error = {}", harnessAccount, ex.getMessage(), ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(ex.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response layoutIngest(@Valid LayoutIngestRequest body, @AccountIdentifier String harnessAccount) {
    try {
      Object entity = getGeneralResponse(backstageResourceClient.ingestLayout(body, harnessAccount));
      return Response.ok(entity).build();
    } catch (Exception ex) {
      log.error("Error in layoutIngest - account = {}, error = {}", harnessAccount, ex.getMessage(), ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(ex.getMessage()).build())
          .build();
    }
  }
}