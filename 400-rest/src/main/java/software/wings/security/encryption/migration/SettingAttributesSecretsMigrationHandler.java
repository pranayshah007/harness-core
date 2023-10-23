/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.encryption.migration;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.persistence.HPersistence.returnNewOptions;
import static io.harness.persistence.UpdatedAtAware.LAST_UPDATED_AT_KEY;

import static software.wings.beans.Base.ID_KEY2;
import static software.wings.service.impl.SettingServiceHelper.ATTRIBUTES_USING_REFERENCES;
import static software.wings.settings.SettingVariableTypes.APM_VERIFICATION;
import static software.wings.settings.SettingVariableTypes.SECRET_TEXT;

import static java.time.Duration.ofHours;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.EncryptedDataParent;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.encryption.Encryptable;
import io.harness.encryption.EncryptionReflectUtils;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpAndRedisModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.APMVerificationConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.settings.SettingValue;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class SettingAttributesSecretsMigrationHandler
    extends IteratorPumpAndRedisModeHandler implements Handler<SettingAttribute> {
  private static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofHours(1);

  @Inject private AccountService accountService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private MorphiaPersistenceProvider<SettingAttribute> persistenceProvider;

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<SettingAttribute, MorphiaFilterExpander<SettingAttribute>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       SettingAttribute.class,
                       MongoPersistenceIterator.<SettingAttribute, MorphiaFilterExpander<SettingAttribute>>builder()
                           .clazz(SettingAttribute.class)
                           .fieldName(SettingAttributeKeys.nextSecretMigrationIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .handler(this)
                           .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                           .filterExpander(this::createQuery)
                           .schedulingType(REGULAR)
                           .persistenceProvider(persistenceProvider)
                           .redistribute(true));
  }

  @Override
  public void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<SettingAttribute, MorphiaFilterExpander<SettingAttribute>>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       SettingAttribute.class,
                       MongoPersistenceIterator.<SettingAttribute, MorphiaFilterExpander<SettingAttribute>>builder()
                           .clazz(SettingAttribute.class)
                           .fieldName(SettingAttributeKeys.nextSecretMigrationIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .handler(this)
                           .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                           .filterExpander(this::createQuery)
                           .persistenceProvider(persistenceProvider));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "SettingAttributesSecretsMigrationHandler";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @VisibleForTesting
  public void createQuery(@NonNull Query<SettingAttribute> query) {
    query.field(SettingAttributeKeys.value_type).in(ATTRIBUTES_USING_REFERENCES);
  }

  public void handle(@NonNull SettingAttribute settingAttribute) {
    try {
      if (settingAttribute.isSecretsMigrated()) {
        return;
      }

      boolean isMigrationSuccess;
      if (settingAttribute.getValue().getSettingType() == APM_VERIFICATION) {
        isMigrationSuccess = migrateApmConnector(settingAttribute);
      } else {
        isMigrationSuccess = migrateSecrets(settingAttribute);
      }
      if (isMigrationSuccess) {
        isMigrationSuccess = updateSettingAttribute(settingAttribute);
      }
      if (!isMigrationSuccess) {
        log.error("Could not migrate the setting attribute {} secrets.", settingAttribute.getUuid());
      }
    } catch (Exception e) {
      log.error("Could not migrate the setting attribute {} due to error", settingAttribute.getUuid(), e);
    }
  }

  private boolean updateSettingAttribute(@NonNull SettingAttribute settingAttribute) {
    Query<SettingAttribute> query = wingsPersistence.createQuery(SettingAttribute.class)
                                        .field(ID_KEY2)
                                        .equal(settingAttribute.getUuid())
                                        .field(LAST_UPDATED_AT_KEY)
                                        .equal(settingAttribute.getLastUpdatedAt());

    UpdateOperations<SettingAttribute> updateOperations =
        wingsPersistence.createUpdateOperations(SettingAttribute.class)
            .set(SettingAttributeKeys.secretsMigrated, Boolean.TRUE);

    return wingsPersistence.findAndModify(query, updateOperations, returnNewOptions) != null;
  }

  private boolean migrateSecrets(@NonNull SettingAttribute settingAttribute) {
    SettingValue settingValue = settingAttribute.getValue();
    if (!(settingValue instanceof EncryptableSetting)) {
      return true;
    }

    List<Field> encryptedFields = EncryptionReflectUtils.getEncryptedFields(settingValue.getClass());
    if (EmptyPredicate.isEmpty(encryptedFields)) {
      return true;
    }
    boolean isMigrationSuccess = true;
    for (Field encryptedField : encryptedFields) {
      Field encryptedRefField = EncryptionReflectUtils.getEncryptedRefField(encryptedField, (Encryptable) settingValue);
      encryptedRefField.setAccessible(true);
      try {
        String secretId = (String) encryptedRefField.get(settingValue);
        if (secretId != null) {
          isMigrationSuccess = isMigrationSuccess
              && migrateSecret(secretId, EncryptionReflectUtils.getEncryptedFieldTag(encryptedField), settingAttribute);
        }
      } catch (IllegalAccessException e) {
        log.error("Unable to access encrypted field {} for settingAttribute {}", encryptedField.getName(),
            settingAttribute.getUuid(), e);
      }
    }
    return isMigrationSuccess;
  }

  private boolean migrateSecret(
      @NonNull String secretId, @NonNull String encryptedFieldName, @NonNull SettingAttribute settingAttribute) {
    EncryptedData secret = wingsPersistence.get(EncryptedData.class, secretId);
    if (secret == null || (secret.getType() == SECRET_TEXT && !secret.isHideFromListing())) {
      return true;
    }

    SettingValue settingValue = settingAttribute.getValue();
    String secretName = Optional.ofNullable(settingAttribute.getName()).orElse(UUIDGenerator.generateUuid());
    secretName = secretName.concat("_").concat(settingValue.getType()).concat("_").concat(encryptedFieldName);
    secret.addParent(
        new EncryptedDataParent(settingAttribute.getUuid(), settingValue.getSettingType(), encryptedFieldName));

    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class)
                                     .field(ID_KEY2)
                                     .equal(secret.getUuid())
                                     .field(LAST_UPDATED_AT_KEY)
                                     .equal(secret.getLastUpdatedAt());

    UpdateOperations<EncryptedData> updateOperations = wingsPersistence.createUpdateOperations(EncryptedData.class)
                                                           .set(EncryptedDataKeys.type, SECRET_TEXT)
                                                           .set(EncryptedDataKeys.name, secretName)
                                                           .set(EncryptedDataKeys.hideFromListing, false)
                                                           .set(EncryptedDataKeys.parents, secret.getParents());

    if (settingAttribute.getUsageRestrictions() != null) {
      updateOperations.set(EncryptedDataKeys.usageRestrictions, settingAttribute.getUsageRestrictions());
    }

    return wingsPersistence.findAndModify(query, updateOperations, returnNewOptions) != null;
  }

  private boolean migrateApmConnector(SettingAttribute settingAttribute) {
    log.info("migrating apm ConnectorDisconnectHandler {}", settingAttribute.getUuid());
    APMVerificationConfig apmVerificationConfig = (APMVerificationConfig) settingAttribute.getValue();
    final AtomicBoolean isMigrated = new AtomicBoolean(true);
    if (apmVerificationConfig.getHeadersList() != null) {
      apmVerificationConfig.getHeadersList()
          .stream()
          .filter(keyValues
              -> keyValues.isEncrypted() && APMVerificationConfig.MASKED_STRING.equals(keyValues.getValue())
                  && isNotEmpty(keyValues.getEncryptedValue()))
          .forEach(keyValues -> {
            isMigrated.compareAndSet(
                true, migrateSecret(keyValues.getEncryptedValue(), "header." + keyValues.getKey(), settingAttribute));
            keyValues.setValue(keyValues.getEncryptedValue());
          });
    }

    if (apmVerificationConfig.getOptionsList() != null) {
      apmVerificationConfig.getOptionsList()
          .stream()
          .filter(keyValues
              -> keyValues.isEncrypted() && APMVerificationConfig.MASKED_STRING.equals(keyValues.getValue())
                  && isNotEmpty(keyValues.getEncryptedValue()))
          .forEach(keyValues -> {
            isMigrated.compareAndSet(
                true, migrateSecret(keyValues.getEncryptedValue(), "option." + keyValues.getKey(), settingAttribute));
            keyValues.setValue(keyValues.getEncryptedValue());
          });
    }
    wingsPersistence.save(settingAttribute);
    return isMigrated.get();
  }
}
