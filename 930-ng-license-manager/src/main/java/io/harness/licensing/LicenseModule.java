/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.HarnessCacheManager;
import io.harness.licensing.checks.LicenseComplianceResolver;
import io.harness.licensing.checks.LicenseEditionChecker;
import io.harness.licensing.checks.impl.DefaultLicenseComplianceResolver;
import io.harness.licensing.checks.impl.EnterpriseChecker;
import io.harness.licensing.checks.impl.FreeChecker;
import io.harness.licensing.checks.impl.TeamChecker;
import io.harness.licensing.interfaces.ModuleLicenseImpl;
import io.harness.licensing.interfaces.ModuleLicenseInterface;
import io.harness.licensing.interfaces.clients.ModuleLicenseClient;
import io.harness.licensing.mappers.LicenseObjectConverter;
import io.harness.licensing.mappers.LicenseObjectMapper;
import io.harness.licensing.services.DefaultLicenseServiceImpl;
import io.harness.licensing.services.LicenseService;
import io.harness.smp.license.SMPLicenseModule;
import io.harness.version.VersionInfoManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import java.util.List;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;

@OwnedBy(HarnessTeam.GTM)
public class LicenseModule extends AbstractModule {
  private static LicenseModule instance;
  public static final String LICENSE_CACHE_NAMESPACE = "NGLicense";

  public static LicenseModule getInstance() {
    if (instance == null) {
      instance = new LicenseModule();
    }
    return instance;
  }

  private LicenseModule() {}

  @Override
  protected void configure() {
    install(new SMPLicenseModule());

    MapBinder<ModuleType, LicenseObjectMapper> objectMapperMapBinder =
        MapBinder.newMapBinder(binder(), ModuleType.class, LicenseObjectMapper.class);
    MapBinder<ModuleType, ModuleLicenseClient> interfaceMapBinder =
        MapBinder.newMapBinder(binder(), ModuleType.class, ModuleLicenseClient.class);

    for (ModuleType moduleType : ModuleLicenseRegistrarFactory.getSupportedModuleTypes()) {
      objectMapperMapBinder.addBinding(moduleType).to(ModuleLicenseRegistrarFactory.getLicenseObjectMapper(moduleType));
      interfaceMapBinder.addBinding(moduleType).to(ModuleLicenseRegistrarFactory.getModuleLicenseClient(moduleType));
    }

    MapBinder<Edition, LicenseEditionChecker> editionCheckerMapBinder =
        MapBinder.newMapBinder(binder(), Edition.class, LicenseEditionChecker.class);
    editionCheckerMapBinder.addBinding(Edition.FREE).to(FreeChecker.class);
    editionCheckerMapBinder.addBinding(Edition.TEAM).to(TeamChecker.class);
    editionCheckerMapBinder.addBinding(Edition.ENTERPRISE).to(EnterpriseChecker.class);

    bind(LicenseObjectConverter.class);
    bind(ModuleLicenseInterface.class).to(ModuleLicenseImpl.class);
    bind(LicenseService.class).to(DefaultLicenseServiceImpl.class);
    bind(LicenseComplianceResolver.class).to(DefaultLicenseComplianceResolver.class);
  }

  @Provides
  @Named(LICENSE_CACHE_NAMESPACE)
  @Singleton
  public Cache<String, List> getLicenseCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache(LICENSE_CACHE_NAMESPACE, String.class, List.class,
        AccessedExpiryPolicy.factoryOf(Duration.THIRTY_MINUTES), versionInfoManager.getVersionInfo().getBuildNo());
  }
}
