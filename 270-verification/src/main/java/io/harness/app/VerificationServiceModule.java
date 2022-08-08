/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import io.harness.concurrent.HTimeLimiter;
import io.harness.cvng.core.services.api.VerificationServiceSecretManager;
import io.harness.cvng.core.services.impl.VerificationServiceSecretManagerImpl;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagModule;
import io.harness.metrics.impl.DelegateMetricsServiceImpl;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.metrics.modules.MetricsModule;
import io.harness.persistence.HPersistence;
import io.harness.service.ContinuousVerificationServiceImpl;
import io.harness.service.LearningEngineAnalysisServiceImpl;
import io.harness.service.LogAnalysisServiceImpl;
import io.harness.service.NoOpAlertService;
import io.harness.service.NoOpCvValidationServiceImpl;
import io.harness.service.NoOpYamlPushService;
import io.harness.service.TimeSeriesAnalysisServiceImpl;
import io.harness.service.VerificationMigrationServiceImpl;
import io.harness.service.intfc.ContinuousVerificationService;
import io.harness.service.intfc.LearningEngineService;
import io.harness.service.intfc.LogAnalysisService;
import io.harness.service.intfc.TimeSeriesAnalysisService;
import io.harness.threading.ThreadPool;

import software.wings.DataStorageMode;
import software.wings.alerts.AlertModule;
import software.wings.dl.WingsMongoPersistence;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.GoogleDataStoreServiceImpl;
import software.wings.service.impl.MongoDataStoreServiceImpl;
import software.wings.service.impl.analysis.VerificationServiceImpl;
import software.wings.service.impl.datadog.DatadogServiceImpl;
import software.wings.service.impl.security.NoOpSecretManagerImpl;
import software.wings.service.impl.verification.CVActivityLogServiceImpl;
import software.wings.service.impl.verification.CVConfigurationServiceImpl;
import software.wings.service.impl.verification.CVTaskServiceImpl;
import software.wings.service.impl.verification.CvValidationService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.MigrationService;
import software.wings.service.intfc.VerificationService;
import software.wings.service.intfc.datadog.DatadogService;
import software.wings.service.intfc.security.EncryptedSettingAttributes;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.service.intfc.verification.CVTaskService;
import software.wings.service.intfc.yaml.YamlPushService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Guice Module for initializing all beans.
 *
 * @author Raghu
 */
public class VerificationServiceModule extends AbstractModule {
  private VerificationServiceConfiguration configuration;

  /**
   * Creates a guice module for portal app.
   *
   * @param configuration Dropwizard configuration
   */
  public VerificationServiceModule(VerificationServiceConfiguration configuration) {
    this.configuration = configuration;
  }

  /* (non-Javadoc)
   * @see com.google.inject.AbstractModule#configure()
   */
  @Override
  protected void configure() {
    install(FeatureFlagModule.getInstance());
    install(AlertModule.getInstance());
    install(PrimaryVersionManagerModule.getInstance());
    install(new MetricsModule());

    bind(VerificationServiceConfiguration.class).toInstance(configuration);
    bind(HPersistence.class).to(WingsMongoPersistence.class);
    bind(WingsPersistence.class).to(WingsMongoPersistence.class);
    bind(LearningEngineService.class).to(LearningEngineAnalysisServiceImpl.class);
    bind(SecretManager.class).to(NoOpSecretManagerImpl.class);
    bind(EncryptedSettingAttributes.class).to(NoOpSecretManagerImpl.class);
    bind(MigrationService.class).to(VerificationMigrationServiceImpl.class);
    bind(Clock.class).toInstance(Clock.systemUTC());

    bind(TimeLimiter.class).toInstance(HTimeLimiter.create());
    bind(TimeSeriesAnalysisService.class).to(TimeSeriesAnalysisServiceImpl.class);
    bind(LogAnalysisService.class).to(LogAnalysisServiceImpl.class);
    bind(CVTaskService.class).to(CVTaskServiceImpl.class);
    bind(CVActivityLogService.class).to(CVActivityLogServiceImpl.class);
    bind(CVConfigurationService.class).to(CVConfigurationServiceImpl.class);
    bind(DatadogService.class).to(DatadogServiceImpl.class);
    bind(ContinuousVerificationService.class).to(ContinuousVerificationServiceImpl.class);
    bind(VerificationService.class).to(VerificationServiceImpl.class);
    bind(CvValidationService.class).to(NoOpCvValidationServiceImpl.class);
    bind(YamlPushService.class).to(NoOpYamlPushService.class);
    bind(AlertService.class).to(NoOpAlertService.class);
    bind(VerificationServiceSecretManager.class).to(VerificationServiceSecretManagerImpl.class);
    bind(DelegateMetricsService.class).to(DelegateMetricsServiceImpl.class);

    bind(ExecutorService.class)
        .toInstance(ThreadPool.create(1, 20, 5, TimeUnit.SECONDS,
            new ThreadFactoryBuilder()
                .setNameFormat("Default-Verification-Executor-%d")
                .setPriority(Thread.MIN_PRIORITY)
                .build()));

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("verificationServiceExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("Verification-service-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));

    bind(ExecutorService.class)
        .annotatedWith(Names.named("verificationDataCollectorExecutor"))
        .toInstance(ThreadPool.create(1, 20, 5, TimeUnit.SECONDS,
            new ThreadFactoryBuilder()
                .setNameFormat("Verification-Data-Collector-%d")
                .setPriority(Thread.MIN_PRIORITY)
                .build()));

    bind(ExecutorService.class)
        .annotatedWith(Names.named("alertsCreationExecutor"))
        .toInstance(ThreadPool.create(1, 10, 5, TimeUnit.SECONDS,
            new ThreadFactoryBuilder().setNameFormat("Alerts-creator-%d").setPriority(Thread.MIN_PRIORITY).build()));

    if (configuration.getDataStorageMode() == null) {
      configuration.setDataStorageMode(DataStorageMode.MONGO);
    }

    switch (configuration.getDataStorageMode()) {
      case GOOGLE_CLOUD_DATA_STORE:
        bind(DataStoreService.class).to(GoogleDataStoreServiceImpl.class);
        break;
      case MONGO:
        bind(DataStoreService.class).to(MongoDataStoreServiceImpl.class);
        break;
      default:
        throw new WingsException("Invalid execution log data storage mode: " + configuration.getDataStorageMode());
    }
  }
}
