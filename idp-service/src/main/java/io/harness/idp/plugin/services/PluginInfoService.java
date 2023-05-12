/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.services;

import io.harness.idp.plugin.beans.PluginRequestEntity;
import io.harness.spec.server.idp.v1.model.PluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.PluginInfo;
import io.harness.spec.server.idp.v1.model.RequestPlugin;

import java.util.List;
import org.springframework.data.domain.Page;

public interface PluginInfoService {
  List<PluginInfo> getAllPluginsInfo(String harnessAccount);
  PluginDetailedInfo getPluginDetailedInfo(String identifier, String harnessAccount);
  void saveAllPluginInfo();
  void deleteAllPluginInfo();
  RequestPlugin savePluginRequest(String harnessAccount, RequestPlugin pluginRequest);
  Page<PluginRequestEntity> getPluginRequests(String harnessAccount, int page, int limit);
}
