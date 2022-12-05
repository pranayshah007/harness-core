/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataBuilder;
import io.harness.data.structure.UUIDGenerator;
import io.harness.security.encryption.common.EncryptedDataParams;
import io.harness.security.encryption.common.EncryptionType;

import software.wings.security.EnvFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.GenericEntityFilter.FilterType;
import software.wings.security.UsageRestrictions;
import software.wings.security.UsageRestrictions.AppEnvRestriction;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.Sets;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SecretTestUtils {
  private static final String ACCOUNT_ID = "accountId";

  private static EncryptedDataBuilder getBaseEncryptedDataBuilder() {
    return EncryptedData.builder()
        .name(UUIDGenerator.generateUuid())
        .accountId(ACCOUNT_ID)
        .usageRestrictions(
            UsageRestrictions.builder()
                .appEnvRestrictions(Sets.newHashSet(
                    AppEnvRestriction.builder()
                        .appFilter(GenericEntityFilter.builder().filterType(FilterType.ALL).build())
                        .envFilter(EnvFilter.builder().filterTypes(Sets.newHashSet(EnvFilter.FilterType.PROD)).build())
                        .build(),
                    AppEnvRestriction.builder()
                        .appFilter(GenericEntityFilter.builder().filterType(FilterType.ALL).build())
                        .envFilter(
                            EnvFilter.builder().filterTypes(Sets.newHashSet(EnvFilter.FilterType.NON_PROD)).build())
                        .build()))
                .build())
        .scopedToAccount(false)
        .type(SettingVariableTypes.SECRET_TEXT)
        .kmsId(ACCOUNT_ID)
        .encryptionType(EncryptionType.LOCAL)
        .hideFromListing(true);
  }

  public static EncryptedData getDummyEncryptedData() {
    return getBaseEncryptedDataBuilder().build();
  }

  public static EncryptedData getScopedEncryptedData(UsageRestrictions usageRestrictions, boolean scopedToAccount) {
    return getBaseEncryptedDataBuilder().usageRestrictions(usageRestrictions).scopedToAccount(scopedToAccount).build();
  }

  public static EncryptedData getInlineSecretText() {
    return getBaseEncryptedDataBuilder()
        .encryptionKey(UUIDGenerator.generateUuid())
        .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
        .build();
  }

  public static EncryptedData getReferencedSecretText() {
    return getBaseEncryptedDataBuilder()
        .path(UUIDGenerator.generateUuid())
        .encryptionType(EncryptionType.VAULT)
        .kmsId(UUIDGenerator.generateUuid())
        .build();
  }

  public static EncryptedData getParameterizedSecretText() {
    return getBaseEncryptedDataBuilder()
        .parameters(Sets.newHashSet(EncryptedDataParams.builder()
                                        .name(UUIDGenerator.generateUuid())
                                        .value(UUIDGenerator.generateUuid())
                                        .build(),
            EncryptedDataParams.builder()
                .name(UUIDGenerator.generateUuid())
                .value(UUIDGenerator.generateUuid())
                .build()))
        .encryptionType(EncryptionType.CUSTOM)
        .kmsId(UUIDGenerator.generateUuid())
        .build();
  }

  public static EncryptedData getSecretFile() {
    return getBaseEncryptedDataBuilder()
        .fileSize(1000)
        .base64Encoded(true)
        .encryptionKey(UUIDGenerator.generateUuid())
        .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
        .type(SettingVariableTypes.CONFIG_FILE)
        .encryptionType(EncryptionType.KMS)
        .kmsId(UUIDGenerator.generateUuid())
        .build();
  }
}
