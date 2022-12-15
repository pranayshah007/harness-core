/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.threading.Morpheus.sleep;

import static software.wings.settings.SettingVariableTypes.AWS_SECRETS_MANAGER;
import static software.wings.settings.SettingVariableTypes.AZURE_VAULT;
import static software.wings.settings.SettingVariableTypes.GCP_SECRETS_MANAGER;
import static software.wings.settings.SettingVariableTypes.KMS;
import static software.wings.settings.SettingVariableTypes.VAULT;
import static software.wings.settings.SettingVariableTypes.VAULT_SSH;

import static java.time.Duration.ofMillis;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.configuration.DeployMode;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.secrets.SecretService;
import io.harness.security.encryption.EncryptionType;

import software.wings.app.MainConfiguration;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.security.AbstractSecretServiceImpl;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.mongodb.morphia.query.Query;

@Slf4j
public class MigrationSMCredentialsFromLocalToGlobalKMS implements Migration {
  public static final String DEBUG_LINE = "[MigrationSMCredentialsFromLocalToGlobalKMS] ";
  public static final int BATCH_SIZE = 100;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private SecretService secretService;
  @Inject private MainConfiguration mainConfiguration;
  @Inject protected SecretManagerConfigService secretManagerConfigService;

  @Override
  public void migrate() {
    String deployMode = mainConfiguration.getDeployMode().name();
    if (DeployMode.isOnPrem(deployMode)) {
      log.info(DEBUG_LINE + "Not running because current migration should be run only for SaaS");
      return;
    } else {
      int success_ctr = 0;
      int failed_ctr = 0;
      AbstractSecretServiceImpl secretServiceImpl = getSecretServiceImpl();
      List<SettingVariableTypes> listOfTypes =
          Arrays.asList(KMS, GCP_SECRETS_MANAGER, VAULT_SSH, VAULT, AZURE_VAULT, AWS_SECRETS_MANAGER);

      Query<EncryptedData> encryptedDataQuery = wingsPersistence.createQuery(EncryptedData.class)
                                                    .filter(EncryptedDataKeys.encryptionType, EncryptionType.LOCAL)
                                                    .field(EncryptedDataKeys.type)
                                                    .in(listOfTypes);
      log.info(DEBUG_LINE + "Found total {} secrets to migrate from LOCAL to GLOBAL", encryptedDataQuery.count());

      EncryptedData encryptedData = null;
      try (HIterator<EncryptedData> records = new HIterator<>(encryptedDataQuery.fetch())) {
        while (records.hasNext()) {
          try {
            encryptedData = records.next();
            log.info(DEBUG_LINE + "Starting migration for: " + encryptedData.getUuid());
            char[] decryptedValue = secretServiceImpl.decryptUsingAlgoOfSecret(encryptedData);
            log.info(DEBUG_LINE + "Decryption completed for: " + encryptedData.getUuid());
            EncryptedData encryptedDataUsingBaseAlgo =
                secretServiceImpl.encryptUsingBaseAlgo(encryptedData.getAccountId(), decryptedValue);
            log.info(DEBUG_LINE + "Encryption using Global KMS completed for: " + encryptedData.getUuid());
            encryptedData.setEncryptionKey(encryptedDataUsingBaseAlgo.getEncryptionKey());
            encryptedData.setEncryptedValue(encryptedDataUsingBaseAlgo.getEncryptedValue());
            encryptedData.setEncryptionType(encryptedDataUsingBaseAlgo.getEncryptionType());
            encryptedData.setKmsId(encryptedDataUsingBaseAlgo.getKmsId());
            encryptedDataUsingBaseAlgo = encryptedData;
            wingsPersistence.save(encryptedDataUsingBaseAlgo);
            log.info(DEBUG_LINE + "migrated successfully: " + encryptedDataUsingBaseAlgo);
            success_ctr++;

            if (success_ctr % BATCH_SIZE == 0) {
              sleep(ofMillis(2000));
            }
          } catch (Exception exception) {
            failed_ctr++;
            log.error(DEBUG_LINE + "Error in  migrating {}. Skipping ", encryptedData.getUuid());
            log.error(DEBUG_LINE + "Exception: ", exception);
          }
        }
      }
      log.info(DEBUG_LINE + "Successfully migrated {} secrets from LOCAL to GLOBAL", success_ctr);
      log.info(DEBUG_LINE + "Failed to migrate {} secrets from LOCAL to GLOBAL", failed_ctr);
    }
  }

  @NotNull
  private AbstractSecretServiceImpl getSecretServiceImpl() {
    AbstractSecretServiceImpl abstractSecretService = new AbstractSecretServiceImpl() {};
    abstractSecretService.setSecretService(secretService);
    abstractSecretService.setSecretManagerConfigService(secretManagerConfigService);
    abstractSecretService.setMainConfiguration(mainConfiguration);
    return abstractSecretService;
  }
}
