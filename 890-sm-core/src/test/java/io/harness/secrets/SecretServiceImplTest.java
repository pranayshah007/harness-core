/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import static io.harness.beans.PageRequest.PageRequestBuilder;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.PIYUSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.SMCoreTestBase;
import io.harness.SecretTestUtils;
import io.harness.beans.EncryptedData;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SecretMetadata;
import io.harness.beans.SecretScopeMetadata;
import io.harness.beans.SecretState;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SecretServiceImplTest extends SMCoreTestBase {
  @Inject SecretService secretService;
  @Inject SecretsRBACService secretsRBACService;
  @Inject SecretsDao secretsDao;

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldReturnOnlySecretsWithReadPermissionTest() {
    List<String> secretIds = new ArrayList<>(saveDummySerectsInDB());
    secretIds.add("notExistingSecretId");
    List<SecretScopeMetadata> mockSecretScopeMetaData = new ArrayList<>();
    mockSecretScopeMetaData.add(SecretScopeMetadata.builder().secretId("secretThatCanbeRead").build());
    doReturn(mockSecretScopeMetaData)
        .when(secretsRBACService)
        .filterSecretsByReadPermission(anyString(), any(List.class), anyString(), anyString(), anyBoolean());
    List<SecretMetadata> secretIdsResponse =
        secretService.filterSecretIdsByReadPermission(new HashSet<>(secretIds), "accountId", "dummy", "dummmy", false);
    assertThat(secretIdsResponse.size()).isEqualTo(3);
    for (SecretMetadata metadata : secretIdsResponse) {
      switch (metadata.getSecretId()) {
        case "notExistingSecretId":
          assertThat(metadata.getSecretState()).isEqualTo(SecretState.NOT_FOUND);
          break;
        case "secretThatCannotBeRead":
          assertThat(metadata.getSecretState()).isEqualTo(SecretState.CANNOT_READ);
          break;
        case "secretThatCanbeRead":
          assertThat(metadata.getSecretState()).isEqualTo(SecretState.CAN_READ);
          break;
        default:
          fail("Unexpected SecretId returned");
          break;
      }
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testListSecrets() {
    List<EncryptedData> secrets = createSecrets(1005);
    // when page 1 is requested
    PageRequest<EncryptedData> pageRequest = PageRequestBuilder.aPageRequest().withOffset("0").withLimit("50").build();

    List<SecretScopeMetadata> mockSecretScopeMetaData = new ArrayList<>();
    secrets.forEach(secret -> {
      mockSecretScopeMetaData.add(SecretScopeMetadata.builder()
                                      .secretId(secret.getUuid())
                                      .secretScopes(secret)
                                      .inheritScopesFromSM(secret.isInheritScopesFromSM())
                                      .build());
    });
    when(secretsRBACService.filterSecretsByReadPermission(
             anyString(), any(List.class), anyString(), anyString(), anyBoolean()))
        .thenReturn(mockSecretScopeMetaData.subList(0, 1000))
        .thenReturn(mockSecretScopeMetaData.subList(1000, secrets.size()));

    PageResponse<EncryptedData> response = secretService.listSecrets("accountId", pageRequest, "", "");
    List<EncryptedData> encryptedDataList = response.getResponse();
    assertThat(encryptedDataList).isNotEmpty().hasSize(50);
    assertThat(response.getTotal()).isEqualTo(1001);

    // when last page is requested
    pageRequest.setOffset("1000");
    when(secretsRBACService.filterSecretsByReadPermission(
             anyString(), any(List.class), anyString(), anyString(), anyBoolean()))
        .thenReturn(mockSecretScopeMetaData.subList(0, 1000))
        .thenReturn(mockSecretScopeMetaData.subList(1000, secrets.size()));
    response = secretService.listSecrets("accountId", pageRequest, "", "");
    encryptedDataList = response.getResponse();
    assertThat(encryptedDataList).isNotEmpty().hasSize(5);
    assertThat(response.getTotal()).isEqualTo(1005);
  }

  private List<EncryptedData> createSecrets(int count) {
    List<EncryptedData> secrets = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      EncryptedData secret = SecretTestUtils.getInlineSecretText();
      secretsDao.saveSecret(secret);
      secrets.add(secret);
    }
    return secrets;
  }

  private List<String> saveDummySerectsInDB() {
    EncryptedData secretThatCannotBeRead = SecretTestUtils.getInlineSecretText();
    secretThatCannotBeRead.setUuid("secretThatCannotBeRead");
    EncryptedData secretThatCanbeRead = SecretTestUtils.getInlineSecretText();
    secretThatCanbeRead.setUuid("secretThatCanbeRead");
    secretsDao.saveSecret(secretThatCannotBeRead);
    secretsDao.saveSecret(secretThatCanbeRead);
    return Arrays.asList(secretThatCanbeRead.getUuid(), secretThatCannotBeRead.getUuid());
  }
}
