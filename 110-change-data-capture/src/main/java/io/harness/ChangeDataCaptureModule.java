/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.AccountEntity;
import io.harness.entities.CDCEntity;
import io.harness.entities.CDStageExecutionCDCEntity;
import io.harness.entities.CECloudAccountCDCEntity;
import io.harness.entities.ConnectorCDCEntity;
import io.harness.entities.EnvironmentCDCEntity;
import io.harness.entities.InfrastructureEntityTimeScale;
import io.harness.entities.InterruptCDCEntity;
import io.harness.entities.OrganizationEntity;
import io.harness.entities.PipelineCDCEntity;
import io.harness.entities.PipelineExecutionSummaryEntityCDCEntity;
import io.harness.entities.PipelineExecutionSummaryEntityCDCEntityWithDebeziumEnabled;
import io.harness.entities.PipelineStageExecutionCDCEntity;
import io.harness.entities.ProjectEntity;
import io.harness.entities.ServiceCDCEntity;
import io.harness.entities.StepExecutionCDCEntity;
import io.harness.entities.UserEntity;
import io.harness.entities.VerifyStepCDCEntity;
import io.harness.persistence.HPersistence;
import io.harness.threading.ExecutorModule;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.timescaledb.TimeScaleDBServiceImpl;

import software.wings.dl.WingsMongoPersistence;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.security.NoOpSecretManagerImpl;
import software.wings.service.intfc.security.EncryptedSettingAttributes;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.util.Collections;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class ChangeDataCaptureModule extends AbstractModule {
  private final ChangeDataCaptureServiceConfig config;

  public ChangeDataCaptureModule(ChangeDataCaptureServiceConfig config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    bind(ChangeDataCaptureServiceConfig.class).toInstance(config);
    bind(HPersistence.class).to(WingsMongoPersistence.class).in(Singleton.class);
    bind(WingsPersistence.class).to(WingsMongoPersistence.class).in(Singleton.class);
    bind(SecretManager.class).to(NoOpSecretManagerImpl.class);
    bind(EncryptedSettingAttributes.class).to(NoOpSecretManagerImpl.class);
    bind(BigQueryService.class).to(BigQueryServiceImpl.class);

    try {
      bind(TimeScaleDBService.class)
          .toConstructor(TimeScaleDBServiceImpl.class.getConstructor(TimeScaleDBConfig.class));
    } catch (NoSuchMethodException e) {
      log.error("TimeScaleDbServiceImpl Initialization Failed in due to missing constructor", e);
    }
    bind(TimeScaleDBConfig.class)
        .annotatedWith(Names.named("TimeScaleDBConfig"))
        .toInstance(config.getTimeScaleDBConfig() != null ? config.getTimeScaleDBConfig()
                                                          : TimeScaleDBConfig.builder().build());
    bindEntities();
    install(new RegistrarsModule());
    install(ExecutorModule.getInstance());
  }

  private void bindEntities() {
    Multibinder<CDCEntity<?>> cdcEntityMultibinder =
        Multibinder.newSetBinder(binder(), new TypeLiteral<CDCEntity<?>>() {});
    cdcEntityMultibinder.addBinding().to(CECloudAccountCDCEntity.class);
    if (config.isDebeziumEnabled()) {
      cdcEntityMultibinder.addBinding().to(PipelineExecutionSummaryEntityCDCEntityWithDebeziumEnabled.class);
    } else {
      cdcEntityMultibinder.addBinding().to(PipelineExecutionSummaryEntityCDCEntity.class);
    }
    cdcEntityMultibinder.addBinding().to(ProjectEntity.class);
    cdcEntityMultibinder.addBinding().to(OrganizationEntity.class);
    cdcEntityMultibinder.addBinding().to(AccountEntity.class);
    cdcEntityMultibinder.addBinding().to(PipelineCDCEntity.class);
    cdcEntityMultibinder.addBinding().to(ServiceCDCEntity.class);
    cdcEntityMultibinder.addBinding().to(ConnectorCDCEntity.class);
    cdcEntityMultibinder.addBinding().to(EnvironmentCDCEntity.class);
    cdcEntityMultibinder.addBinding().to(InfrastructureEntityTimeScale.class);
    cdcEntityMultibinder.addBinding().to(UserEntity.class);
    cdcEntityMultibinder.addBinding().to(CDStageExecutionCDCEntity.class);
    cdcEntityMultibinder.addBinding().to(VerifyStepCDCEntity.class);
    cdcEntityMultibinder.addBinding().to(InterruptCDCEntity.class);
    cdcEntityMultibinder.addBinding().to(PipelineStageExecutionCDCEntity.class);
    cdcEntityMultibinder.addBinding().to(StepExecutionCDCEntity.class);
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  public Set<Class<?>> morphiaClasses() {
    return Collections.emptySet();
  }
}
