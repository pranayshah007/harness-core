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
import io.harness.configuration.DeployMode;
import io.harness.credit.services.CreditService;
import io.harness.credit.services.impl.CreditServiceImpl;
import io.harness.licensing.checks.LicenseComplianceResolver;
import io.harness.licensing.checks.LicenseEditionChecker;
import io.harness.licensing.checks.impl.DefaultLicenseComplianceResolver;
import io.harness.licensing.checks.impl.EnterpriseChecker;
import io.harness.licensing.checks.impl.FreeChecker;
import io.harness.licensing.checks.impl.TeamChecker;
import io.harness.licensing.interfaces.ModuleLicenseImpl;
import io.harness.licensing.interfaces.ModuleLicenseInterface;
import io.harness.licensing.interfaces.clients.ModuleLicenseClient;
import io.harness.licensing.jobs.LicenseValidationTask;
import io.harness.licensing.jobs.SMPLicenseValidationJob;
import io.harness.licensing.jobs.SMPLicenseValidationJobImpl;
import io.harness.licensing.jobs.SMPLicenseValidationTask;
import io.harness.licensing.jobs.SMPLicenseValidationTaskFactory;
import io.harness.licensing.mappers.LicenseObjectConverter;
import io.harness.licensing.mappers.LicenseObjectMapper;
import io.harness.licensing.services.DefaultLicenseServiceImpl;
import io.harness.licensing.services.LicenseService;
import io.harness.licensing.services.SMPLicenseServiceImpl;
import io.harness.smp.license.SMPLicenseModule;
import io.harness.user.remote.UserClient;
import io.harness.version.VersionInfoManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import jodd.util.concurrent.ThreadFactoryBuilder;

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
    requireBinding(UserClient.class);

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
    if (DeployMode.isOnPrem(System.getenv(DeployMode.DEPLOY_MODE))) {
      bind(LicenseService.class).to(SMPLicenseServiceImpl.class);
    } else {
      bind(LicenseService.class).to(DefaultLicenseServiceImpl.class);
    }
    bind(CreditService.class).to(CreditServiceImpl.class);
    bind(LicenseComplianceResolver.class).to(DefaultLicenseComplianceResolver.class);

    bind(SMPLicenseValidationJob.class).to(SMPLicenseValidationJobImpl.class);
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("SMP_EXECUTOR_SERVICE"))
        .toInstance(
            new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("smp-validation-pool").get()));
    install(new FactoryModuleBuilder()
                .implement(LicenseValidationTask.class, SMPLicenseValidationTask.class)
                .build(SMPLicenseValidationTaskFactory.class));
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
