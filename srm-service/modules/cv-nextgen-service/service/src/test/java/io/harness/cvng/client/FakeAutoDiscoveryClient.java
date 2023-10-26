/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import io.harness.cvng.autodiscovery.services.AutoDiscoveryClient;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.servicediscovery.client.beans.DiscoveredServiceConnectionResponse;
import io.harness.servicediscovery.client.beans.DiscoveredServiceResponse;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public class FakeAutoDiscoveryClient implements AutoDiscoveryClient {
  @Override
  public List<DiscoveredServiceResponse> getDiscoveredServices(ProjectParams projectParams, String agentIdentifier) {
    String responseString = getResource("autodiscovery/discovered-service-response-" + agentIdentifier + ".yaml");
    return new Gson().fromJson(responseString, new TypeToken<List<DiscoveredServiceResponse>>() {}.getType());
  }

  @Override
  public List<DiscoveredServiceConnectionResponse> getDiscoveredServiceConnections(
      ProjectParams projectParams, String agentIdentifier) {
    String responseString =
        getResource("autodiscovery/discovered-service-connection-response-" + agentIdentifier + ".yaml");
    return new Gson().fromJson(responseString, new TypeToken<List<DiscoveredServiceConnectionResponse>>() {}.getType());
  }

  protected String getResource(String filePath) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource(filePath);
    try {
      return Resources.toString(testFile, Charsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
