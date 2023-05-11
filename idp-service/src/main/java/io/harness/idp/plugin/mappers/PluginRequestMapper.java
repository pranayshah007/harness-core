/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.plugin.beans.PluginRequestEntity;
import io.harness.spec.server.idp.v1.model.RequestPlugin;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class PluginRequestMapper {
  public PluginRequestEntity fromDTO(String accountIdentifier, RequestPlugin pluginRequest) {
    PluginRequestEntity pluginRequestEntity = new PluginRequestEntity();
    pluginRequestEntity.setAccountIdentifier(accountIdentifier);
    pluginRequestEntity.setName(pluginRequest.getName());
    pluginRequestEntity.setCreator(pluginRequest.getCreator());
    pluginRequestEntity.setPackageLink(pluginRequest.getPackageLink());
    pluginRequestEntity.setDocLink(pluginRequest.getDocLink());
    return pluginRequestEntity;
  }

  public RequestPlugin toDTO(PluginRequestEntity pluginRequestEntity) {
    RequestPlugin pluginRequest = new RequestPlugin();
    pluginRequest.setName(pluginRequestEntity.getName());
    pluginRequest.setCreator(pluginRequestEntity.getCreator());
    pluginRequest.setPackageLink(pluginRequestEntity.getPackageLink());
    pluginRequest.setDocLink(pluginRequestEntity.getDocLink());
    return pluginRequest;
  }
}
