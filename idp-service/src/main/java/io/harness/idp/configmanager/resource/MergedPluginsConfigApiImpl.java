/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.resource;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.spec.server.idp.v1.MergedPluginsConfigApi;
import io.harness.spec.server.idp.v1.model.MergedPluginConfigResponse;
import io.harness.spec.server.idp.v1.model.MergedPluginConfigs;

import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
public class MergedPluginsConfigApiImpl implements MergedPluginsConfigApi {
  private ConfigManagerService configManagerService;

  @Override
  public Response getMergedPluginsConfig(String accountIdentifier) {
    try {
      MergedPluginConfigs mergedEnabledPluginAppConfigsForAccount =
          configManagerService.mergeEnabledPluginConfigsForAccount(accountIdentifier);
      MergedPluginConfigResponse mergedPluginConfigResponse = new MergedPluginConfigResponse();
      mergedPluginConfigResponse.setMergedConfig(mergedEnabledPluginAppConfigsForAccount);
      return Response.status(Response.Status.OK).entity(mergedPluginConfigResponse).build();
    } catch (Exception e) {
      log.error(e.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
