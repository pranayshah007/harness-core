/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.service;

import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import java.util.List;
import java.util.concurrent.ExecutionException;

public interface PluginsProxyInfoService {
  List<ProxyHostDetail> insertProxyHostDetailsForPlugin(AppConfig appConfig, String accountIdentifier)
      throws ExecutionException;

  List<ProxyHostDetail> updateProxyHostDetailsForPlugin(AppConfig appConfig, String accountIdentifier)
      throws ExecutionException;

  void deleteProxyHostDetailsForPlugin(String accountIdentifier, String pluginId) throws ExecutionException;

  List<ProxyHostDetail> getProxyHostDetailsForMultiplePluginIds(String accountIdentifier, List<String> pluginIds);
  List<ProxyHostDetail> updateProxyHostDetailsForHostValues(
      List<ProxyHostDetail> proxyHostDetails, String accountIdentifier);
  List<ProxyHostDetail> getProxyHostDetailsForPluginId(String accountIdentifier, String pluginId);
}
