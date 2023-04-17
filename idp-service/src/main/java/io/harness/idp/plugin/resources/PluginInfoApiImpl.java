/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.resources;

import static io.harness.idp.common.Constants.IDP_PERMISSION;
import static io.harness.idp.common.Constants.IDP_RESOURCE_TYPE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.annotations.IdpServiceAuthIfHasApiKey;
import io.harness.idp.plugin.mappers.PluginInfoMapper;
import io.harness.idp.plugin.services.PluginInfoService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.PluginInfoApi;
import io.harness.spec.server.idp.v1.model.PluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.PluginDetailedInfoResponse;
import io.harness.spec.server.idp.v1.model.PluginInfo;

import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class PluginInfoApiImpl implements PluginInfoApi {
  private PluginInfoService pluginInfoService;
  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response getPlugins(@AccountIdentifier String harnessAccount) {
    List<PluginInfo> plugins = pluginInfoService.getAllPluginsInfo(harnessAccount);
    return Response.status(Response.Status.OK).entity(PluginInfoMapper.toResponseList(plugins)).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response getPluginsInfoPluginId(String pluginId, @AccountIdentifier String harnessAccount) {
    try {
      PluginDetailedInfo pluginDetailedInfo = pluginInfoService.getPluginDetailedInfo(pluginId, harnessAccount);
      PluginDetailedInfoResponse response = new PluginDetailedInfoResponse();
      response.setPlugin(pluginDetailedInfo);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @IdpServiceAuthIfHasApiKey
  public Response getBackstagePlugins(String harnessAccount) {
    List<PluginInfo> plugins = pluginInfoService.getAllPluginsInfo(harnessAccount);
    return Response.status(Response.Status.OK).entity(PluginInfoMapper.toResponseList(plugins)).build();
  }
}
