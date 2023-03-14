/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.PL_MIGRATE_SECRETS_FROM_LOCAL_TO_GCP_KMS_PROD3;

import static software.wings.settings.SettingVariableTypes.CONFIG_FILE;
import static software.wings.settings.SettingVariableTypes.SECRET_TEXT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.encoding.EncodingUtils;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.dao.NGEncryptedDataDao;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretFileSpecDTO;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.ng.core.entities.NGEncryptedData.NGEncryptedDataKeys;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secrets.SecretsFileService;
import io.harness.security.SimpleEncryption;
import io.harness.security.encryption.EncryptionType;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(PL)
@Slf4j
public class NgLocalToGlobalSecretsMigrationService implements Runnable {
  public static final int BATCH_SIZE = 100;
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  private final MongoTemplate mongoTemplate;
  private final SecretsFileService secretsFileService;
  private final NGEncryptedDataDao ngEncryptedDataDao;
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;
  private final PersistentLocker persistentLocker;
  private final String DEBUG_MESSAGE = "NgLocalToGlobalSecretsMigrationJob: ";
  private static final String LOCK_NAME = "NgLocalToGlobalSecretsMigrationJobLock";

  private final NGEncryptedDataService ngEncryptedDataService;
  @Inject
  public NgLocalToGlobalSecretsMigrationService(MongoTemplate mongoTemplate, SecretsFileService secretsFileService,
      NGEncryptedDataService ngEncryptedDataService, NGEncryptedDataDao ngEncryptedDataDao,
      NGFeatureFlagHelperService ngFeatureFlagHelperService, PersistentLocker persistentLocker) {
    this.mongoTemplate = mongoTemplate;
    this.secretsFileService = secretsFileService;
    this.ngEncryptedDataService = ngEncryptedDataService;
    this.ngEncryptedDataDao = ngEncryptedDataDao;
    this.ngFeatureFlagHelperService = ngFeatureFlagHelperService;
    this.persistentLocker = persistentLocker;
  }

  @Override
  public void run() {
    log.info(DEBUG_MESSAGE + "started...");
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(DEBUG_MESSAGE + "failed to acquire lock");
        return;
      }
      execute();
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " failed to acquire lock", ex);
    }
    log.info(DEBUG_MESSAGE + " completed...");
  }

  private void execute() {
    log.info("Started migration of Ng secrets from local to Gcp_kms");
    CloseableIterator<NGEncryptedData> iterator = runQueryWithBatch(getAllNgEncryptedRecords());
    while (iterator.hasNext()) {
      NGEncryptedData ngEncryptedData = iterator.next();
      log.info("Migrating secret with id {} and accountId {} from local to gcp", ngEncryptedData.getId(),
          ngEncryptedData.getAccountIdentifier());
      try {
        processRecord(ngEncryptedData);
        log.info("Successfully migrated secret with id {} and accountId {} from local to gcp", ngEncryptedData.getId(),
            ngEncryptedData.getAccountIdentifier());
      } catch (Exception ex) {
        log.error("migration failed for encrypted record with id {} and accountId {} with this exception {}",
            ngEncryptedData.getId(), ngEncryptedData.getAccountIdentifier(), ex);
      }
    }
  }

  private Criteria getAllNgEncryptedRecords() {
    return Criteria.where(NGEncryptedDataKeys.encryptionType).is("LOCAL");
  }

  private CloseableIterator<NGEncryptedData> runQueryWithBatch(Criteria criteria) {
    Query query = new Query(criteria);
    query.cursorBatchSize(NgLocalToGlobalSecretsMigrationService.BATCH_SIZE);
    return mongoTemplate.stream(query, NGEncryptedData.class, "ngEncryptedRecords");
  }

  private void processRecord(NGEncryptedData ngEncryptedData) {
    if (GLOBAL_ACCOUNT_ID.equals(ngEncryptedData.getAccountIdentifier())) {
      return;
    }
    if (!ngFeatureFlagHelperService.isEnabled(
            ngEncryptedData.getAccountIdentifier(), PL_MIGRATE_SECRETS_FROM_LOCAL_TO_GCP_KMS_PROD3)) {
      return;
    }
    final SimpleEncryption simpleEncryption = new SimpleEncryption(ngEncryptedData.getEncryptionKey());
    NGEncryptedData updatedRecord;
    if (SECRET_TEXT.equals(ngEncryptedData.getType())) {
      char[] decryptedValue = simpleEncryption.decryptChars(ngEncryptedData.getEncryptedValue());
      SecretTextSpecDTO secretTextSpecDTO = SecretTextSpecDTO.builder()
                                                .value(String.valueOf(decryptedValue))
                                                .valueType(ValueType.Inline)
                                                .secretManagerIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                                .build();
      SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                    .type(SecretType.SecretText)
                                    .identifier(ngEncryptedData.getIdentifier())
                                    .projectIdentifier(ngEncryptedData.getProjectIdentifier())
                                    .orgIdentifier(ngEncryptedData.getOrgIdentifier())
                                    .name(ngEncryptedData.getName())
                                    .spec(secretTextSpecDTO)
                                    .build();
      updatedRecord = ngEncryptedDataService.updateSecretText(ngEncryptedData.getAccountIdentifier(), secretDTOV2);
      updatedRecord.setEncryptionType(EncryptionType.GCP_KMS);
      ngEncryptedDataDao.save(updatedRecord);
    } else if (CONFIG_FILE.equals(ngEncryptedData.getType())) {
      char[] fileContent = secretsFileService.getFileContents(String.valueOf(ngEncryptedData.getEncryptedValue()));
      ngEncryptedData.setEncryptedValue(fileContent);
      char[] decryptedValue = simpleEncryption.decryptChars(ngEncryptedData.getEncryptedValue());
      InputStream inputStream = new ByteArrayInputStream(EncodingUtils.decodeBase64(decryptedValue));
      SecretDTOV2 secretDTOV2 =
          SecretDTOV2.builder()
              .identifier(ngEncryptedData.getIdentifier())
              .projectIdentifier(ngEncryptedData.getProjectIdentifier())
              .orgIdentifier(ngEncryptedData.getOrgIdentifier())
              .name(ngEncryptedData.getName())
              .type(SecretType.SecretFile)
              .spec(SecretFileSpecDTO.builder().secretManagerIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER).build())
              .build();
      updatedRecord =
          ngEncryptedDataService.updateSecretFile(ngEncryptedData.getAccountIdentifier(), secretDTOV2, inputStream);
      updatedRecord.setEncryptionType(EncryptionType.GCP_KMS);
      ngEncryptedDataDao.save(updatedRecord);
    }
  }
}
