/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import io.harness.favorites.services.FavoritesService;
import io.harness.favorites.services.impl.FavoritesServiceImpl;

import com.google.inject.AbstractModule;

public class NGFavoriteModule extends AbstractModule {
  private final NextGenConfiguration appConfig;

  public NGFavoriteModule(NextGenConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  protected void configure() {
    bind(NextGenConfiguration.class).toInstance(appConfig);
    bind(FavoritesService.class).to(FavoritesServiceImpl.class);
  }
}
