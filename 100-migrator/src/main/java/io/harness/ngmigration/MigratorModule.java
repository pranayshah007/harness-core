/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration;

import static io.harness.authorization.AuthorizationServiceHeader.MIGRATOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.service.ServiceResourceClientModule;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class MigratorModule extends AbstractModule {
  private MigratorConfig migratorConfig;

  public MigratorModule(MigratorConfig migratorConfig) {
    this.migratorConfig = migratorConfig;
  }

  @Provides
  @Singleton
  @Named("ngClientConfig")
  public ServiceHttpClientConfig ngClientConfig() {
    return migratorConfig.getNgClientConfig();
  }

  @Provides
  @Singleton
  @Named("pipelineServiceClientConfig")
  public ServiceHttpClientConfig pmsClientConfig() {
    return migratorConfig.getPipelineServiceClientConfig();
  }

  @Provides
  @Singleton
  @Named("templateServiceClientConfig")
  public ServiceHttpClientConfig templateServiceClientConfig() {
    return migratorConfig.getTemplateServiceClientConfig();
  }

  @Override
  public void configure() {
    try {
      install(new ServiceResourceClientModule(migratorConfig.getNgClientConfig(),
          migratorConfig.getCg().getPortal().getJwtNextGenManagerSecret(), MIGRATOR.getServiceId()));
    } catch (Exception ex) {
      log.info("Could not create the service resource client module", ex);
    }
  }
}
