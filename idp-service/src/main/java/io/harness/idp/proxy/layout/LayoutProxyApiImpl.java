/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.layout;

import static io.harness.idp.constants.Constants.IDP_SETTINGS;
import static io.harness.idp.constants.Constants.MANAGE_PERMISSION;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.clients.BackstageResourceClient;
import io.harness.spec.server.idp.v1.LayoutProxyApi;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class LayoutProxyApiImpl implements LayoutProxyApi {
  static final String BEARER_TOKEN_FORMAT = "Bearer %s";
  @Inject @Named("backstageServiceSecret") private String backstageServiceSecret;
  @Inject BackstageResourceClient backstageResourceClient;

  @Override
  @NGAccessControlCheck(resourceType = IDP_SETTINGS, permission = MANAGE_PERMISSION)
  public Response createLayout(String harnessAccount) {
    Object entity = getGeneralResponse(backstageResourceClient.createLayout(
        String.format(BEARER_TOKEN_FORMAT, backstageServiceSecret), harnessAccount));
    return Response.ok(entity).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_SETTINGS, permission = MANAGE_PERMISSION)
  public Response deleteLayout(String harnessAccount) {
    Object entity = getGeneralResponse(backstageResourceClient.deleteLayout(
        String.format(BEARER_TOKEN_FORMAT, backstageServiceSecret), harnessAccount));
    return Response.ok(entity).build();
  }

  @Override
  public Response getAllLayouts(String harnessAccount) {
    Object entity = getGeneralResponse(backstageResourceClient.getAllLayouts(
        String.format(BEARER_TOKEN_FORMAT, backstageServiceSecret), harnessAccount));
    return Response.ok(entity).build();
  }

  @Override
  public Response getLayout(String layoutIdentifier, String harnessAccount) {
    Object entity = getGeneralResponse(backstageResourceClient.getLayout(
        String.format(BEARER_TOKEN_FORMAT, backstageServiceSecret), harnessAccount, layoutIdentifier));
    return Response.ok(entity).build();
  }

  @Override
  public Response getLayoutHealth(String harnessAccount) {
    Object entity = getGeneralResponse(
        backstageResourceClient.getHealth(String.format(BEARER_TOKEN_FORMAT, backstageServiceSecret), harnessAccount));
    return Response.ok(entity).build();
  }
}
