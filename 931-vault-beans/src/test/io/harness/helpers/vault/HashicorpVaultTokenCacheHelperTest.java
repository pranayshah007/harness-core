/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helpers.vault;

import static io.harness.helpers.vault.HashicorpVaultTokenCacheHelper.MAX_CACHE_EXPIRY_DURATION_IN_SECONDS;
import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.helpers.ext.vault.VaultAppRoleLoginResult;
import io.harness.helpers.vault.HashicorpVaultTokenCacheHelper;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.VaultConfig;

import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HashicorpVaultTokenCacheHelperTest extends CategoryTest {
  @Before
  public void setup() {}

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testAppRoleTokenCache_putInCache() {
    String token = UUIDGenerator.generateUuid();
    VaultAppRoleLoginResult vaultAppRoleLoginResult =
        VaultAppRoleLoginResult.builder().clientToken(token).leaseDuration(100).build();
    VaultConfig vaultConfig = VaultConfig.builder()
                                  .uuid(UUIDGenerator.generateUuid())
                                  .name(UUIDGenerator.generateUuid())
                                  .accountId(UUIDGenerator.generateUuid())
                                  .vaultUrl(UUIDGenerator.generateUuid())
                                  .authToken(UUIDGenerator.generateUuid())
                                  .encryptionType(EncryptionType.VAULT)
                                  .isDefault(false)
                                  .basePath(UUIDGenerator.generateUuid())
                                  .secretEngineName(UUIDGenerator.generateUuid())
                                  .secretEngineVersion(1)
                                  .namespace(UUIDGenerator.generateUuid())
                                  .ngMetadata(NGSecretManagerMetadata.builder()
                                                  .accountIdentifier("accountId")
                                                  .identifier(UUIDGenerator.generateUuid())
                                                  .build())
                                  .build();
    HashicorpVaultTokenCacheHelper.putInAppRoleTokenCache(vaultConfig, vaultAppRoleLoginResult);
    assertThat(HashicorpVaultTokenCacheHelper.getAppRoleToken(vaultConfig)).isEqualTo(token);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetExpiryWithLeewayInNanos_whenExpiryIsNotSet() {
    assertThat(HashicorpVaultTokenCacheHelper.getExpiryWithLeewayInNanos(0))
        .isEqualTo(TimeUnit.SECONDS.toNanos(MAX_CACHE_EXPIRY_DURATION_IN_SECONDS));
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetExpiryWithLeewayInNanos_whenExpiryIsSet() {
    assertThat(HashicorpVaultTokenCacheHelper.getExpiryWithLeewayInNanos(100L)).isEqualTo(TimeUnit.SECONDS.toNanos(99));
  }
}
