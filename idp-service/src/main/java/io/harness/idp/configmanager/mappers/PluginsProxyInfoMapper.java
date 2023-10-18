/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.beans.entity.PluginsProxyInfoEntity;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class PluginsProxyInfoMapper {
  public ProxyHostDetail toDto(PluginsProxyInfoEntity pluginsProxyInfoEntity) {
    ProxyHostDetail proxyHostDetail = new ProxyHostDetail();
    proxyHostDetail.setIdentifier(pluginsProxyInfoEntity.getId());
    proxyHostDetail.setPluginId(pluginsProxyInfoEntity.getPluginId());
    proxyHostDetail.setHost(pluginsProxyInfoEntity.getHost());
    proxyHostDetail.setProxy(pluginsProxyInfoEntity.getProxy());
    proxyHostDetail.setSelectors(pluginsProxyInfoEntity.getDelegateSelectors());
    return proxyHostDetail;
  }
}
