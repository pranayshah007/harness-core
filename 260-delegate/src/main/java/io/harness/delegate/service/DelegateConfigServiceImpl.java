/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.network.SafeHttpCall.execute;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.rest.RestResponse;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.ConfigFileDto;
import software.wings.delegatetasks.DelegateConfigService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class DelegateConfigServiceImpl implements DelegateConfigService {
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public List<ConfigFileDto> getConfigFiles(String appId, String envId, String uuid, String hostId, String accountId)
      throws IOException {
    RestResponse<List<ConfigFileDto>> executeResult =
        execute(delegateAgentManagerClient.getConfigFiles(uuid, accountId, appId, envId, hostId));

    if (executeResult == null) {
      String errorMessage = String.format("The call to the DelegateAgentManagerClient to get the config files for "
              + "appId=%s, envId=%s, uuid=%s, hostId=%s and accountId=%s failed.",
          appId, envId, uuid, hostId, accountId);

      throw new IOException(errorMessage);
    }

    log.debug("Successfully acquired the config files. Attempting to deserialized them");
    return executeResult.getResource();
  }
}
