/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.client;

import io.harness.idp.proxy.envvariable.ProxyEnvVariableServiceWrapper;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;

@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class DslClientFactory {
  ProxyEnvVariableServiceWrapper proxyEnvVariableServiceWrapper;
  DirectDslClient directDslClient;
  DelegateDslClient delegateDslClient;

  public DslClient getClient(String accountIdentifier, String host) {
    JSONObject hostProxyMap = proxyEnvVariableServiceWrapper.getHostProxyMap(accountIdentifier);
    try {
      if (hostProxyMap.getBoolean(host)) {
        return delegateDslClient;
      }
    } catch (JSONException e) {
      log.warn("HOST_PROXY_MAP does not have host {} for account {}", host, accountIdentifier);
    }
    return directDslClient;
  }
}
