/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.persistence.HPersistence.upToOne;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.SecretManagerConfig.SecretManagerConfigKeys;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.exception.SecretManagementException;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.common.EncryptionType;

import software.wings.beans.LocalEncryptionConfig;
import software.wings.dl.WingsPersistence;
import software.wings.security.EnvFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.UsageRestrictions;
import software.wings.service.intfc.security.LocalSecretManagerService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * @author marklu on 2019-05-14
 */
@OwnedBy(PL)
@Singleton
@Slf4j
public class LocalSecretManagerServiceImpl extends AbstractSecretServiceImpl implements LocalSecretManagerService {
  @Inject protected WingsPersistence wingsPersistence;
  @Inject private SecretManagerConfigService secretManagerConfigService;
  @Inject private KmsEncryptorsRegistry kmsEncryptorsRegistry;

  @Override
  public LocalEncryptionConfig getEncryptionConfig(String accountId) {
    return LocalEncryptionConfig.builder()
        .uuid(accountId)
        .accountId(accountId)
        .scopedToAccount(false)
        .usageRestrictions(
            UsageRestrictions.builder()
                .appEnvRestrictions(Sets.newHashSet(
                    UsageRestrictions.AppEnvRestriction.builder()
                        .appFilter(GenericEntityFilter.builder().filterType(GenericEntityFilter.FilterType.ALL).build())
                        .envFilter(EnvFilter.builder().filterTypes(Sets.newHashSet(EnvFilter.FilterType.PROD)).build())
                        .build(),
                    UsageRestrictions.AppEnvRestriction.builder()
                        .appFilter(GenericEntityFilter.builder().filterType(GenericEntityFilter.FilterType.ALL).build())
                        .envFilter(
                            EnvFilter.builder().filterTypes(Sets.newHashSet(EnvFilter.FilterType.NON_PROD)).build())
                        .build()))
                .build())
        .build();
    // As LOCAL secret manager is HIDDEN right now. The following 'numOfEncryptedValues' field is not needed
    // Therefore commenting out the code below.
    //    Query<EncryptedData> encryptedDataQuery = wingsPersistence.createQuery(EncryptedData.class)
    //                                                  .filter(EncryptedDataKeys.accountId, accountId)
    //                                                  .filter(EncryptedDataKeys.kmsId, accountId)
    //                                                  .filter(EncryptedDataKeys.encryptionType, LOCAL);
    //    encryptionConfig.setNumOfEncryptedValue(encryptedDataQuery.asKeyList().size());
  }

  @Override
  public String saveLocalEncryptionConfig(String accountId, LocalEncryptionConfig localEncryptionConfig) {
    localEncryptionConfig.setAccountId(accountId);
    return secretManagerConfigService.save(localEncryptionConfig);
  }

  @Override
  public void validateLocalEncryptionConfig(String accountId, LocalEncryptionConfig localEncryptionConfig) {
    String randomString = generateUuid();
    try {
      kmsEncryptorsRegistry.getKmsEncryptor(localEncryptionConfig)
          .encryptSecret(accountId, randomString, localEncryptionConfig);
    } catch (Exception e) {
      String message = "Contact Harness Support, not able to encrypt using the local secret manager.";
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, message, e, USER);
    }
  }

  @Override
  public boolean deleteLocalEncryptionConfig(String accountId, String configId) {
    long count = wingsPersistence.createQuery(EncryptedData.class)
                     .filter(EncryptedDataKeys.accountId, accountId)
                     .filter(EncryptedDataKeys.kmsId, configId)
                     .filter(EncryptedDataKeys.encryptionType, EncryptionType.LOCAL)
                     .count(upToOne);
    if (count > 0) {
      String message =
          "Cannot delete the Local Secret Manager configuration since there are secrets encrypted with it. "
          + "Please transition your secrets to another secret manager and try again.";
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, message, USER);
    }

    LocalEncryptionConfig localEncryptionConfig = wingsPersistence.createQuery(LocalEncryptionConfig.class)
                                                      .field(ID_KEY)
                                                      .equal(configId)
                                                      .field(SecretManagerConfigKeys.accountId)
                                                      .equal(accountId)
                                                      .get();
    checkNotNull(localEncryptionConfig, "No Local Secret Manager configuration found with id " + configId);

    if (GLOBAL_ACCOUNT_ID.equals(localEncryptionConfig.getAccountId())) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Can not delete global secret manager", USER);
    }

    return deleteSecretManagerAndGenerateAudit(accountId, localEncryptionConfig);
  }
}
