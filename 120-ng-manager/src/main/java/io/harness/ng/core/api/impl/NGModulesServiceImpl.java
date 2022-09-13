/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.remote.client.NGRestUtils.getCgResponse;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.modules.remote.ModulesClient;
import io.harness.ng.core.api.NGModulesService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NGModulesServiceImpl implements NGModulesService {
  private final ModulesClient modulesClient;

  @Override
  public List<ModuleType> getEnabledModules(String accountIdentifier) {
    return getCgResponse(modulesClient.getEnabledModules(accountIdentifier));
  }
}
