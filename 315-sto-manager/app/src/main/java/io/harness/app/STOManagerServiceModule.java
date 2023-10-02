/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import static io.harness.sto.execution.STONotifyEventConsumerRedis.STO_EVENTS_CACHE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.impl.STOYamlSchemaServiceImpl;
import io.harness.app.intfc.STOYamlSchemaService;
import io.harness.cache.HarnessCacheManager;
import io.harness.ci.enforcement.CIBuildEnforcer;
import io.harness.sto.STOBuildEnforcerImpl;
import io.harness.version.VersionInfoManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.STO)
public class STOManagerServiceModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(STOYamlSchemaService.class).to(STOYamlSchemaServiceImpl.class).in(Singleton.class);
    bind(CIBuildEnforcer.class).to(STOBuildEnforcerImpl.class);
  }

  @Provides
  @Singleton
  @Named(STO_EVENTS_CACHE)
  public Cache<String, Integer> sdkEventsCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache(STO_EVENTS_CACHE, String.class, Integer.class,
        AccessedExpiryPolicy.factoryOf(Duration.THIRTY_MINUTES), versionInfoManager.getVersionInfo().getBuildNo(),
        true);
  }
}