/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.secretManager;

import static io.harness.rule.OwnerRule.VOJIN;

import static software.wings.graphql.datafetcher.secretManager.CreateSecretManagerDataFetcherTest.ACCOUNT_ID;
import static software.wings.graphql.datafetcher.secretManager.CreateSecretManagerDataFetcherTest.VAULT_ID;
import static software.wings.graphql.datafetcher.secretManager.CreateSecretManagerDataFetcherTest.VAULT_NAME;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.VaultConfig;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.secretManager.QLDeleteSecretManagerInput;
import software.wings.service.intfc.security.VaultService;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class DeleteSecretManagerDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock private VaultService vaultService;

  @Mock SecretManagerConfigService secretManagerConfigService;

  @Mock SecretManagerDataFetcherRegistry secretManagerDataFetcherRegistry;

  @Inject @InjectMocks private HashicorpVaultDataFetcher hashicorpVaultDataFetcher;

  @InjectMocks DeleteSecretManagerDataFetcher dataFetcher = spy(DeleteSecretManagerDataFetcher.class);

  @Before
  public void setup() {
    doReturn(hashicorpVaultDataFetcher).when(secretManagerDataFetcherRegistry).getDataFetcher(EncryptionType.VAULT);
  }

  @Test
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void deleteSecretManagerTest() {
    doReturn(VaultConfig.builder()
                 .name(VAULT_NAME)
                 .uuid(VAULT_ID)
                 .accountId(ACCOUNT_ID)
                 .encryptionType(EncryptionType.VAULT)
                 .build())
        .when(secretManagerConfigService)
        .getSecretManager(any(), any());

    doReturn(Boolean.TRUE).when(vaultService).deleteVaultConfig(any(), any());

    dataFetcher.mutateAndFetch(QLDeleteSecretManagerInput.builder().secretManagerId(VAULT_ID).build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(vaultService, times(1)).deleteVaultConfig(ACCOUNT_ID, VAULT_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void deleteWithoutId() {
    doReturn(null).when(secretManagerConfigService).getSecretManager(any(), any());

    dataFetcher.mutateAndFetch(
        QLDeleteSecretManagerInput.builder().build(), MutationContext.builder().accountId(ACCOUNT_ID).build());
  }
}
