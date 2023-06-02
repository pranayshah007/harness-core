/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static java.time.Duration.ofSeconds;

import io.harness.beans.MigrateSecretTask;
import io.harness.config.PublisherConfiguration;
import io.harness.mongo.queue.QueueFactory;
import io.harness.persistence.HPersistence;
import io.harness.queue.QueueConsumer;
import io.harness.queue.QueueController;
import io.harness.queue.QueueListener;
import io.harness.queue.QueuePublisher;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.secretmanagers.SecretsManagerRBACService;
import io.harness.secrets.SecretMigrationEventListener;
import io.harness.secrets.SecretService;
import io.harness.secrets.SecretServiceImpl;
import io.harness.secrets.SecretsAuditService;
import io.harness.secrets.SecretsDao;
import io.harness.secrets.SecretsDaoImpl;
import io.harness.secrets.SecretsFileService;
import io.harness.secrets.SecretsRBACService;
import io.harness.secrets.setupusage.SecretSetupUsageService;
import io.harness.secrets.setupusage.SecretSetupUsageServiceImpl;
import io.harness.secrets.validation.BaseSecretValidator;
import io.harness.secrets.validation.SecretValidator;
import io.harness.secrets.validation.SecretValidators;
import io.harness.secrets.validation.validators.AwsSecretManagerValidator;
import io.harness.secrets.validation.validators.AzureSecretManagerValidator;
import io.harness.secrets.validation.validators.GcpSecretManagerValidator;
import io.harness.secrets.validation.validators.VaultSecretManagerValidator;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

public class SecretManagementCoreModule extends AbstractModule {
  private static SecretManagementCoreModule instance;

  public static SecretManagementCoreModule getInstance() {
    if (instance == null) {
      instance = new SecretManagementCoreModule();
    }
    return instance;
  }

  @Provides
  @Singleton
  QueuePublisher<MigrateSecretTask> kmsTransitionQueuePublisher(Injector injector, PublisherConfiguration config) {
    return QueueFactory.createQueuePublisher(injector, MigrateSecretTask.class, null, config);
  }

  @Provides
  @Singleton
  QueueConsumer<MigrateSecretTask> kmsTransitionQueueConsumer(Injector injector, PublisherConfiguration config) {
    return QueueFactory.createQueueConsumer(injector, MigrateSecretTask.class, ofSeconds(30), null, config);
  }

  @Override
  protected void configure() {
    bind(SecretsDao.class).to(SecretsDaoImpl.class);
    bind(SecretSetupUsageService.class).to(SecretSetupUsageServiceImpl.class);
    bind(SecretService.class).to(SecretServiceImpl.class);
    bind(new TypeLiteral<QueueListener<MigrateSecretTask> >() {}).to(SecretMigrationEventListener.class);

    binder()
        .bind(SecretValidator.class)
        .annotatedWith(Names.named(SecretValidators.AWS_SECRET_MANAGAER_VALIDATOR.getName()))
        .to(AwsSecretManagerValidator.class);

    binder()
        .bind(SecretValidator.class)
        .annotatedWith(Names.named(SecretValidators.AZURE_SECRET_MANAGER_VALIDATOR.getName()))
        .to(AzureSecretManagerValidator.class);

    binder()
        .bind(SecretValidator.class)
        .annotatedWith(Names.named(SecretValidators.GCP_SECRET_MANAGER_VALIDATOR.getName()))
        .to(GcpSecretManagerValidator.class);

    binder()
        .bind(SecretValidator.class)
        .annotatedWith(Names.named(SecretValidators.VAULT_SECRET_MANAGER_VALIDATOR.getName()))
        .to(VaultSecretManagerValidator.class);

    binder()
        .bind(SecretValidator.class)
        .annotatedWith(Names.named(SecretValidators.COMMON_SECRET_MANAGER_VALIDATOR.getName()))
        .to(BaseSecretValidator.class);

    registerRequiredBindings();
  }

  private void registerRequiredBindings() {
    requireBinding(HPersistence.class);
    requireBinding(SecretManagerConfigService.class);
    requireBinding(SecretsFileService.class);
    requireBinding(SecretsAuditService.class);
    requireBinding(SecretsRBACService.class);
    requireBinding(QueueController.class);
    requireBinding(SecretsManagerRBACService.class);
  }
}
