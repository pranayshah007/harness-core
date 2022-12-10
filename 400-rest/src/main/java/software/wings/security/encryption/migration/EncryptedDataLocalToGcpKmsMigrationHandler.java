/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.encryption.migration;

import static io.harness.beans.FeatureName.ACTIVE_MIGRATION_FROM_LOCAL_TO_GCP_KMS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.security.encryption.EncryptionType.GCP_KMS;
import static io.harness.security.encryption.EncryptionType.LOCAL;

import static software.wings.settings.SettingVariableTypes.APM_VERIFICATION;
import static software.wings.settings.SettingVariableTypes.CONFIG_FILE;
import static software.wings.settings.SettingVariableTypes.SECRET_TEXT;

import static java.time.Duration.ofHours;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.MigrateSecretTask;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.persistence.HPersistence;
import io.harness.secrets.SecretService;

import software.wings.beans.GcpKmsConfig;
import software.wings.beans.LocalEncryptionConfig;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.security.GcpSecretsManagerService;
import software.wings.service.intfc.security.LocalSecretManagerService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Singleton
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EncryptedDataLocalToGcpKmsMigrationHandler
    extends IteratorPumpModeHandler implements Handler<EncryptedData> {
  public static final int MAX_RETRY_COUNT = 3;
  private final List<SettingVariableTypes> secretTypes;
  private final WingsPersistence wingsPersistence;
  private final FeatureFlagService featureFlagService;
  private final PersistenceIteratorFactory persistenceIteratorFactory;
  private final GcpSecretsManagerService gcpSecretsManagerService;
  private final LocalSecretManagerService localSecretManagerService;
  private final MorphiaPersistenceProvider<EncryptedData> persistenceProvider;
  private final SecretService secretService;
  private GcpKmsConfig gcpKmsConfig;

  @Inject
  public EncryptedDataLocalToGcpKmsMigrationHandler(WingsPersistence wingsPersistence,
      FeatureFlagService featureFlagService, PersistenceIteratorFactory persistenceIteratorFactory,
      GcpSecretsManagerService gcpSecretsManagerService, LocalSecretManagerService localSecretManagerService,
      MorphiaPersistenceProvider<EncryptedData> persistenceProvider, SecretService secretService) {
    this.wingsPersistence = wingsPersistence;
    this.featureFlagService = featureFlagService;
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.gcpSecretsManagerService = gcpSecretsManagerService;
    this.localSecretManagerService = localSecretManagerService;
    this.persistenceProvider = persistenceProvider;
    this.secretService = secretService;
    this.secretTypes = new ArrayList<>();
    secretTypes.add(SECRET_TEXT);
    secretTypes.add(CONFIG_FILE);
    secretTypes.add(APM_VERIFICATION);
  }

  private MorphiaFilterExpander<EncryptedData> getFilterExpander() {
    this.gcpKmsConfig = gcpSecretsManagerService.getGlobalKmsConfig();
    if (gcpKmsConfig == null) {
      log.error(
          "Global GCP KMS config found to be null hence not registering EncryptedDataLocalToGcpKmsMigrationHandler iterators");
      return null;
    }

    Set<String> accountIds = featureFlagService.getAccountIds(ACTIVE_MIGRATION_FROM_LOCAL_TO_GCP_KMS);
    if (isEmpty(accountIds)) {
      log.info(
          "Feature flag {} is not enabled hence not registering EncryptedDataLocalToGcpKmsMigrationHandler iterators",
          ACTIVE_MIGRATION_FROM_LOCAL_TO_GCP_KMS);
    } else {
      log.info(
          "Feature flag {} is enabled for accounts {} hence registering EncryptedDataLocalToGcpKmsMigrationHandler iterators",
          ACTIVE_MIGRATION_FROM_LOCAL_TO_GCP_KMS, accountIds.toString());
      return getFilterQueryWithAccountIdsFilter(accountIds);
    }

    return null;
  }

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    MorphiaFilterExpander<EncryptedData> filterExpander = getFilterExpander();

    if (filterExpander == null) {
      log.warn("Iterator {} not started since the Morphia Filter is NULL", iteratorName);
      return;
    }

    iterator =
        (MongoPersistenceIterator<EncryptedData, MorphiaFilterExpander<EncryptedData>>)
            persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions, EncryptedData.class,
                MongoPersistenceIterator.<EncryptedData, MorphiaFilterExpander<EncryptedData>>builder()
                    .clazz(EncryptedData.class)
                    .fieldName(EncryptedDataKeys.nextLocalToGcpKmsMigrationIteration)
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(ofHours(40))
                    .handler(this)
                    .filterExpander(filterExpander)
                    .schedulingType(REGULAR)
                    .persistenceProvider(persistenceProvider)
                    .redistribute(true));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "EncryptedDataLocalToGcpKmsMigrationHandler";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  private MorphiaFilterExpander<EncryptedData> getFilterQueryWithAccountIdsFilter(Set<String> accountIds) {
    return query
        -> query.field(EncryptedDataKeys.accountId)
               .hasAnyOf(accountIds)
               .field(EncryptedDataKeys.type)
               .in(secretTypes)
               .field(EncryptedDataKeys.encryptionType)
               .equal(LOCAL)
               .field(EncryptedDataKeys.ngMetadata)
               .equal(null);
  }

  @Override
  public void handle(@NotNull EncryptedData encryptedData) {
    if (!featureFlagService.isEnabled(ACTIVE_MIGRATION_FROM_LOCAL_TO_GCP_KMS, encryptedData.getAccountId())) {
      log.info(
          "Feature flag {} is not enabled hence not processing encryptedData {} for accountId {} for Local Secret Manager to GCP KMS migration ",
          ACTIVE_MIGRATION_FROM_LOCAL_TO_GCP_KMS, encryptedData.getUuid(), encryptedData.getAccountId());
      return;
    }
    int retryCount = 0;
    boolean isMigrationSuccessful = false;
    while (!isMigrationSuccessful && retryCount < MAX_RETRY_COUNT) {
      if (encryptedData.getEncryptedValue() == null) {
        log.info("EncryptedValue value was null for encrypted record {} hence just updating encryption type info only",
            encryptedData.getUuid());
        isMigrationSuccessful = updateEncryptionInfo(encryptedData);
      } else {
        log.info(
            "Executing Local Secret Manager to GCP KMS migration for encrypted record {}", encryptedData.getUuid());
        isMigrationSuccessful = migrateToGcpKMS(encryptedData);
      }
      retryCount++;
    }
    if (!isMigrationSuccessful) {
      log.error("Could not migrate encrypted record {} from Local Secret Manager to GCP KMS for after 3 retries",
          encryptedData.getUuid());
    }
  }

  private boolean updateEncryptionInfo(@NotNull EncryptedData encryptedData) {
    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class)
                                     .field(EncryptedDataKeys.ID_KEY)
                                     .equal(encryptedData.getUuid())
                                     .field(EncryptedDataKeys.lastUpdatedAt)
                                     .equal(encryptedData.getLastUpdatedAt());

    UpdateOperations<EncryptedData> updateOperations =
        wingsPersistence.createUpdateOperations(EncryptedData.class)
            .set(EncryptedDataKeys.encryptionType, GCP_KMS)
            .set(EncryptedDataKeys.kmsId, gcpKmsConfig.getUuid())
            .set(EncryptedDataKeys.backupEncryptionType, LOCAL)
            .set(EncryptedDataKeys.backupKmsId, encryptedData.getAccountId())
            .set(EncryptedDataKeys.backupEncryptionKey, encryptedData.getEncryptionKey());

    EncryptedData savedEncryptedData =
        wingsPersistence.findAndModify(query, updateOperations, HPersistence.returnNewOptions);

    if (savedEncryptedData == null) {
      log.error("Failed to save encrypted record {} during Local Secret Manager to GCP KMS migration",
          encryptedData.getUuid());
      return false;
    }
    return true;
  }

  protected boolean migrateToGcpKMS(@NotNull EncryptedData encryptedData) {
    try {
      LocalEncryptionConfig localEncryptionConfig =
          localSecretManagerService.getEncryptionConfig(encryptedData.getAccountId());
      MigrateSecretTask migrateSecretTask = MigrateSecretTask.builder()
                                                .accountId(encryptedData.getAccountId())
                                                .secretId(encryptedData.getUuid())
                                                .fromConfig(localEncryptionConfig)
                                                .toConfig(gcpKmsConfig)
                                                .build();
      secretService.migrateSecret(migrateSecretTask);
      return true;
    } catch (Exception ex) {
      log.error("Exception occurred for encrypted record {} while Local Secret Manager to GCP KMS migration",
          encryptedData.getUuid(), ex);
      return false;
    }
  }
}
