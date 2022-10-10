/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.persistence.HPersistence.returnNewOptions;
import static io.harness.validation.PersistenceValidator.duplicateCheck;

import static software.wings.settings.SettingVariableTypes.AWS_SECRETS_MANAGER;
import static software.wings.settings.SettingVariableTypes.AZURE_VAULT;
import static software.wings.settings.SettingVariableTypes.GCP_KMS;
import static software.wings.settings.SettingVariableTypes.KMS;
import static software.wings.settings.SettingVariableTypes.VAULT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SecretFile;
import io.harness.beans.SecretText;
import io.harness.beans.SecretUpdateData;
import io.harness.exception.SecretManagementException;
import io.harness.persistence.HPersistence;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionType;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import java.util.Set;
import javax.validation.executable.ValidateOnExecution;
import org.mongodb.morphia.query.MorphiaIterator;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@ValidateOnExecution
@Singleton
@OwnedBy(PL)
public class SecretsDaoImpl implements SecretsDao {
  private final HPersistence hPersistence;

  @Inject
  public SecretsDaoImpl(HPersistence hPersistence) {
    this.hPersistence = hPersistence;
  }

  @Override
  public Optional<EncryptedData> getSecretById(String accountId, String secretId) {
    return Optional.ofNullable(hPersistence.createQuery(EncryptedData.class)
                                   .filter(EncryptedDataKeys.accountId, accountId)
                                   .filter(EncryptedDataKeys.ID_KEY, secretId)
                                   .field(EncryptedDataKeys.ngMetadata)
                                   .equal(null)
                                   .get());
  }

  @Override
  public Optional<EncryptedData> getSecretByName(String accountId, String secretName) {
    return Optional.ofNullable(hPersistence.createQuery(EncryptedData.class)
                                   .filter(EncryptedDataKeys.accountId, accountId)
                                   .filter(EncryptedDataKeys.name, secretName)
                                   .field(EncryptedDataKeys.ngMetadata)
                                   .equal(null)
                                   .get());
  }

  @Override
  public Optional<EncryptedData> getSecretByKeyOrPath(
      String accountId, EncryptionType encryptionType, String key, String path) {
    if (isEmpty(key) && isEmpty(path)) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "Both key and path cannot be empty when trying to access secrets", USER);
    }
    Query<EncryptedData> query = hPersistence.createQuery(EncryptedData.class);
    query.criteria(EncryptedDataKeys.accountId)
        .equal(accountId)
        .criteria(EncryptedDataKeys.encryptionType)
        .equal(encryptionType)
        .criteria(EncryptedDataKeys.ngMetadata)
        .equal(null);
    if (isNotEmpty(key) && isNotEmpty(path)) {
      query.and(query.or(query.criteria(EncryptedDataKeys.encryptionKey).equal(key),
          query.criteria(EncryptedDataKeys.path).equal(path)));
    } else if (isNotEmpty(key)) {
      query.criteria(EncryptedDataKeys.encryptionKey).equal(key);
    } else if (isNotEmpty(path)) {
      query.criteria(EncryptedDataKeys.path).equal(path);
    }
    return Optional.ofNullable(query.get());
  }

  @Override
  public String saveSecret(EncryptedData encryptedData) {
    return duplicateCheck(() -> hPersistence.save(encryptedData), EncryptedDataKeys.name, encryptedData.getName());
  }

  @Override
  public EncryptedData updateSecret(SecretUpdateData secretUpdateData, EncryptedRecord updatedEncryptedData) {
    Query<EncryptedData> query =
        hPersistence.createQuery(EncryptedData.class)
            .filter(EncryptedDataKeys.accountId, secretUpdateData.getExistingRecord().getAccountId())
            .filter(EncryptedDataKeys.ID_KEY, secretUpdateData.getExistingRecord().getUuid())
            .field(EncryptedDataKeys.ngMetadata)
            .equal(null);
    UpdateOperations<EncryptedData> updateOperations = hPersistence.createUpdateOperations(EncryptedData.class);
    if (secretUpdateData.isNameChanged()) {
      String newName = secretUpdateData.getUpdatedSecret().getName();
      updateOperations.set(EncryptedDataKeys.name, newName);
      updateOperations.set(EncryptedDataKeys.searchTags.concat(".").concat(newName), 1);
      updateOperations.unset(
          EncryptedDataKeys.searchTags.concat(".").concat(secretUpdateData.getExistingRecord().getName()));
    }
    if (secretUpdateData.isReferenceChanged()) {
      updateOperations.set(EncryptedDataKeys.path, ((SecretText) secretUpdateData.getUpdatedSecret()).getPath());
      updateOperations.unset(EncryptedDataKeys.encryptionKey);
      updateOperations.unset(EncryptedDataKeys.encryptedValue);
    }
    if (secretUpdateData.isParametersChanged()) {
      updateOperations.set(
          EncryptedDataKeys.parameters, ((SecretText) secretUpdateData.getUpdatedSecret()).getParameters());
      updateOperations.unset(EncryptedDataKeys.encryptionKey);
      updateOperations.unset(EncryptedDataKeys.encryptedValue);
    }
    if (secretUpdateData.isUsageScopeChanged()) {
      if (secretUpdateData.getUpdatedSecret().getUsageRestrictions() != null) {
        updateOperations.set(
            EncryptedDataKeys.usageRestrictions, secretUpdateData.getUpdatedSecret().getUsageRestrictions());
      } else {
        updateOperations.unset(EncryptedDataKeys.usageRestrictions);
      }
      updateOperations.set(
          EncryptedDataKeys.inheritScopesFromSM, secretUpdateData.getUpdatedSecret().isInheritScopesFromSM());
      updateOperations.set(EncryptedDataKeys.scopedToAccount, secretUpdateData.getUpdatedSecret().isScopedToAccount());
    }
    if (secretUpdateData.isAdditonalMetadataChanged()) {
      updateOperations.set(
          EncryptedDataKeys.additionalMetadata, secretUpdateData.getUpdatedSecret().getAdditionalMetadata());
    }
    if (updatedEncryptedData != null) {
      updateOperations.set(EncryptedDataKeys.encryptionKey, updatedEncryptedData.getEncryptionKey());
      updateOperations.set(EncryptedDataKeys.encryptedValue, updatedEncryptedData.getEncryptedValue());
      if (secretUpdateData.getUpdatedSecret() instanceof SecretFile && secretUpdateData.isValueChanged()) {
        updateOperations.set(
            EncryptedDataKeys.fileSize, ((SecretFile) secretUpdateData.getUpdatedSecret()).getFileContent().length);
      }
    }
    updateOperations.set(EncryptedDataKeys.kmsId, secretUpdateData.getUpdatedSecret().getKmsId());
    return hPersistence.findAndModify(query, updateOperations, returnNewOptions);
  }

  @Override
  public EncryptedData migrateSecret(String accountId, String secretId, EncryptedRecord encryptedRecord) {
    Query<EncryptedData> query = hPersistence.createQuery(EncryptedData.class)
                                     .filter(EncryptedDataKeys.accountId, accountId)
                                     .filter(EncryptedDataKeys.ID_KEY, secretId)
                                     .field(EncryptedDataKeys.ngMetadata)
                                     .equal(null);
    UpdateOperations<EncryptedData> updateOperations = hPersistence.createUpdateOperations(EncryptedData.class);
    updateOperations.set(EncryptedDataKeys.kmsId, encryptedRecord.getKmsId());
    updateOperations.set(EncryptedDataKeys.encryptionType, encryptedRecord.getEncryptionType());
    updateOperations.set(EncryptedDataKeys.encryptionKey, encryptedRecord.getEncryptionKey());
    updateOperations.set(EncryptedDataKeys.encryptedValue, encryptedRecord.getEncryptedValue());
    return hPersistence.findAndModify(query, updateOperations, returnNewOptions);
  }

  @Override
  public boolean deleteSecret(String accountId, String secretId) {
    return hPersistence.delete(EncryptedData.class, secretId);
  }

  @Override
  public PageResponse<EncryptedData> listSecrets(PageRequest<EncryptedData> pageRequest) {
    Object[] allowedNgMetaDataValues = {null};
    pageRequest.addFilter(SearchFilter.builder()
                              .fieldName(EncryptedDataKeys.ngMetadata)
                              .op(EQ)
                              .fieldValues(allowedNgMetaDataValues)
                              .build());
    return hPersistence.query(EncryptedData.class, pageRequest);
  }

  @Override
  public MorphiaIterator<EncryptedData, EncryptedData> listSecretsBySecretManager(
      String accountId, String secretManagerId, boolean shouldIncludeSecretManagerSecrets) {
    Query<EncryptedData> query = hPersistence.createQuery(EncryptedData.class)
                                     .filter(EncryptedDataKeys.accountId, accountId)
                                     .filter(EncryptedDataKeys.kmsId, secretManagerId)
                                     .field(EncryptedDataKeys.ngMetadata)
                                     .equal(null);

    if (!shouldIncludeSecretManagerSecrets) {
      query.field(EncryptedDataKeys.type)
          .notIn(Lists.newArrayList(VAULT, KMS, GCP_KMS, AZURE_VAULT, AWS_SECRETS_MANAGER));
    }
    return query.fetch();
  }

  @Override
  public MorphiaIterator<EncryptedData, EncryptedData> listSecretsBySecretIds(String accountId, Set<String> secretIds) {
    return hPersistence.createQuery(EncryptedData.class)
        .field(EncryptedDataKeys.ID_KEY)
        .in(secretIds)
        .field(EncryptedDataKeys.accountId)
        .equal(accountId)
        .field(EncryptedDataKeys.ngMetadata)
        .equal(null)
        .fetch();
  }

  @Override
  public void updateSecret(EncryptedData encryptedData, UpdateOperations<EncryptedData> updateOperations) {
    hPersistence.update(encryptedData, updateOperations);
  }

  @Override
  public UpdateOperations<EncryptedData> getUpdateOperations() {
    return hPersistence.createUpdateOperations(EncryptedData.class);
  }
}
