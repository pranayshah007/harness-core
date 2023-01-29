/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.generator;

import software.wings.dl.WingsPersistence;
import software.wings.persistence.AppContainer;
import software.wings.service.intfc.AppContainerService;

import com.google.inject.Inject;

/**
 * Created by sgurubelli on 9/10/18.
 */
public class AppContainerGenerator {
  @Inject AccountGenerator accountGenerator;

  @Inject AppContainerService appContainerService;
  @Inject WingsPersistence wingsPersistence;

  public AppContainer ensureAppContainer(AppContainer appContainer) {
    AppContainer existing = exists(appContainer);
    if (existing != null) {
      return existing;
    }

    return GeneratorUtils.suppressDuplicateException(
        () -> appContainerService.save(appContainer), () -> exists(appContainer));
  }

  public AppContainer exists(AppContainer appContainer) {
    return appContainerService.get(appContainer.getAccountId(), appContainer.getName());
  }
}
