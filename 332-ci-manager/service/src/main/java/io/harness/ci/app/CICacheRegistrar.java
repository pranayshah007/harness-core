/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static javax.cache.expiry.Duration.THIRTY_MINUTES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.HarnessCacheManager;
import io.harness.ci.beans.entities.EncryptedDataDetails;
import io.harness.version.VersionInfoManager;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import javax.cache.Cache;
import javax.cache.expiry.CreatedExpiryPolicy;

@OwnedBy(PL)
public class CICacheRegistrar extends AbstractModule {
  public static final String SECRET_CACHE = "secretCache";

  @Provides
  @Named(SECRET_CACHE)
  @Singleton
  public Cache<String, EncryptedDataDetails> getSecretTokenCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache(SECRET_CACHE, String.class, EncryptedDataDetails.class,
        CreatedExpiryPolicy.factoryOf(THIRTY_MINUTES), versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Override
  protected void configure() {
    registerRequiredBindings();
    bindCaches();
  }

  private void bindCaches() {
    MapBinder<String, Cache<?, ?>> mapBinder =
        MapBinder.newMapBinder(binder(), TypeLiteral.get(String.class), new TypeLiteral<Cache<?, ?>>() {});

    mapBinder.addBinding(SECRET_CACHE).to(Key.get(new TypeLiteral<Cache<String, EncryptedDataDetails>>() {
    }, Names.named(SECRET_CACHE)));
  }

  private void registerRequiredBindings() {
    requireBinding(HarnessCacheManager.class);
    requireBinding(VersionInfoManager.class);
  }
}