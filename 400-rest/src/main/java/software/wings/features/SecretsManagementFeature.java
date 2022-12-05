/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.features;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.common.EncryptionConfig;
import io.harness.security.encryption.common.EncryptionType;

import software.wings.beans.Account;
import software.wings.features.api.AbstractPremiumFeature;
import software.wings.features.api.ComplianceByRemovingUsage;
import software.wings.features.api.FeatureRestrictions;
import software.wings.features.api.Usage;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Slf4j
@TargetModule(HarnessModule._890_SM_CORE)
public class SecretsManagementFeature extends AbstractPremiumFeature implements ComplianceByRemovingUsage {
  public static final String FEATURE_NAME = "SECRET_MANAGEMENT";

  @Inject private SecretManager secretManager;

  @Inject
  public SecretsManagementFeature(
      AccountService accountService, FeatureRestrictions featureRestrictions, SecretManager secretManager) {
    super(accountService, featureRestrictions);
    this.secretManager = secretManager;
  }

  @Override
  public boolean removeUsageForCompliance(String accountId, String targetAccountType) {
    if (isUsageCompliantWithRestrictions(accountId, targetAccountType)) {
      return true;
    }

    secretManager.clearDefaultFlagOfSecretManagers(accountId);
    if (getAccountType(accountId).equals(targetAccountType)) {
      secretManager.transitionAllSecretsToHarnessSecretManager(accountId);
    }

    return true;
  }

  @Override
  public boolean isAvailable(String accountType) {
    boolean result = (boolean) getRestrictions(accountType).getOrDefault("isCustomSecretManagerAllowed", true);

    log.info("Is custom secret manager usage allowed for account type {}? {}", accountType, result);
    return result;
  }

  @Override
  public boolean isBeingUsed(String accountId) {
    return !getUsages(accountId).isEmpty();
  }

  @Override
  public Collection<Usage> getDisallowedUsages(String accountId, String targetAccountType) {
    if (isAvailable(targetAccountType)) {
      return Collections.emptyList();
    }

    return getUsages(accountId);
  }

  private Collection<Usage> getUsages(String accountId) {
    Collection<Usage> usages = getCustomerSecretManagers(accountId)
                                   .stream()
                                   .map(SecretsManagementFeature::toUsage)
                                   .collect(Collectors.toList());

    log.info("Secret manager usages in account {} are: {}", accountId, usages);
    return usages;
  }

  private static Usage toUsage(EncryptionConfig encryptionConfig) {
    return Usage.builder()
        .entityId(encryptionConfig.getUuid())
        .entityName(encryptionConfig.getName())
        .entityType(encryptionConfig.getEncryptionType().name())
        .build();
  }

  @Override
  public String getFeatureName() {
    return FEATURE_NAME;
  }

  private List<EncryptionConfig> getCustomerSecretManagers(String accountId) {
    return secretManager.listSecretManagers(accountId)
        .stream()
        .filter(secretManagerConfig -> !isHarnessSecretManager(secretManagerConfig))
        .collect(Collectors.toList());
  }

  private static boolean isHarnessSecretManager(EncryptionConfig secretManagerConfig) {
    if (secretManagerConfig.getEncryptionType() == EncryptionType.KMS
        || secretManagerConfig.getEncryptionType() == EncryptionType.GCP_KMS) {
      return Account.GLOBAL_ACCOUNT_ID.equals(secretManagerConfig.getAccountId());
    }

    return false;
  }
}
