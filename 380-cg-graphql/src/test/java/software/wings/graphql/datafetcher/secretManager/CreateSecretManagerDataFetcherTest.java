/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.secretManager;

import static io.harness.rule.OwnerRule.VOJIN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.VaultConfig;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.secretManager.QLCreateSecretManagerInput;
import software.wings.graphql.schema.mutation.secretManager.QLHashicorpVaultAuthDetails;
import software.wings.graphql.schema.mutation.secretManager.QLHashicorpVaultSecretManagerInput;
import software.wings.graphql.schema.mutation.secretManager.QLUpsertSecretManagerPayload;
import software.wings.graphql.schema.type.secretManagers.QLSecretManagerType;
import software.wings.service.intfc.security.VaultService;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class CreateSecretManagerDataFetcherTest extends AbstractDataFetcherTestBase {
  static final String ACCOUNT_ID = "ACCOUNT_ID";
  static final String VAULT_ID = "VAULT_ID";
  static final String VAULT_URL = "VAULT_URL";
  static final String VAULT_NAME = "VAULT_NAME";
  static final String BASE_PATH = "BASE_PATH";
  static final String AUTH_TOKEN = "AUTH_TOKEN";
  static final String ENGINE_NAME = "ENGINE_NAME";

  @Mock private VaultService vaultService;

  @Mock SecretManagerDataFetcherRegistry secretManagerDataFetcherRegistry;

  @Inject @InjectMocks private HashicorpVaultDataFetcher hashicorpVaultDataFetcher;

  @Inject @InjectMocks private CreateSecretManagerDataFetcher dataFetcher = new CreateSecretManagerDataFetcher();

  @Before
  public void setup() {
    doReturn(hashicorpVaultDataFetcher).when(secretManagerDataFetcherRegistry).getDataFetcher(EncryptionType.VAULT);
  }

  @Test
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void createVaultTest() {
    QLHashicorpVaultSecretManagerInput vaultInput = createVaultInput();

    QLCreateSecretManagerInput input = QLCreateSecretManagerInput.builder()
                                           .secretManagerType(QLSecretManagerType.HASHICORP_VAULT)
                                           .hashicorpVaultConfigInput(vaultInput)
                                           .build();

    VaultConfig vaultConfig = VaultConfig.builder()
                                  .name(VAULT_NAME)
                                  .uuid(VAULT_ID)
                                  .accountId(ACCOUNT_ID)
                                  .authToken(AUTH_TOKEN)
                                  .encryptionType(EncryptionType.VAULT)
                                  .basePath(BASE_PATH)
                                  .isReadOnly(false)
                                  .build();

    doReturn(VAULT_ID)
        .when(vaultService)
        .saveOrUpdateVaultConfig(isA(String.class), isA(VaultConfig.class), isA(Boolean.class));
    doReturn(vaultConfig).when(vaultService).getVaultConfig(isA(String.class), isA(String.class));

    QLUpsertSecretManagerPayload payload =
        dataFetcher.mutateAndFetch(input, MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(vaultService, times(1))
        .saveOrUpdateVaultConfig(isA(String.class), isA(VaultConfig.class), isA(Boolean.class));
    verify(vaultService, times(1)).getVaultConfig(isA(String.class), isA(String.class));

    assertThat(payload.getSecretManager()).isNotNull();
    assertThat(payload.getSecretManager().getName()).isEqualTo(VAULT_NAME);
    assertThat(payload.getSecretManager().getId()).isEqualTo(VAULT_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void createSecretManagerWithoutConfig() {
    QLCreateSecretManagerInput input =
        QLCreateSecretManagerInput.builder().secretManagerType(QLSecretManagerType.HASHICORP_VAULT).build();

    dataFetcher.mutateAndFetch(input, MutationContext.builder().accountId(ACCOUNT_ID).build());
  }

  private QLHashicorpVaultSecretManagerInput createVaultInput() {
    return QLHashicorpVaultSecretManagerInput.builder()
        .vaultUrl(VAULT_URL)
        .name(VAULT_NAME)
        .basePath(BASE_PATH)
        .authDetails(QLHashicorpVaultAuthDetails.builder().authToken(AUTH_TOKEN).build())
        .secretEngineName(ENGINE_NAME)
        .secretEngineVersion(1)
        .secretEngineRenewalInterval(60)
        .isReadOnly(false)
        .build();
  }
}
