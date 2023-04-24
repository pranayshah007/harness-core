/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.managerproxy;

import static io.harness.rule.OwnerRule.ARVIND;

import static java.util.Collections.EMPTY_MAP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegatetasks.EncryptSecretTaskResponse;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;

import software.wings.beans.VaultConfig;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ManagerKmsEncryptorTest extends CategoryTest {
  @Mock private DelegateService delegateService;
  @Mock private ManagerEncryptorHelper managerEncryptorHelper;
  @Inject @InjectMocks private ManagerKmsEncryptor managerKmsEncryptor;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final String projectIdentifier = "PROJECT_IDENTIFIER";
  private static final String orgIdentifier = "ORG_IDENTIFIER";
  private static final String accountId = "ACCOUNT_ID";
  private static final String ownerValue = "PROJECT_IDENTIFIER/ORG_IDENTIFIER";

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void encryptSecretTest() throws Exception {
    NGSecretManagerMetadata metadata =
        NGSecretManagerMetadata.builder().orgIdentifier(orgIdentifier).projectIdentifier(projectIdentifier).build();
    VaultConfig vaultConfig = VaultConfig.builder().ngMetadata(metadata).build();

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    doReturn(EncryptSecretTaskResponse.builder().build())
        .when(delegateService)
        .executeTaskV2(delegateTaskArgumentCaptor.capture());

    doReturn(EMPTY_MAP).when(managerEncryptorHelper).buildAbstractions(any());
    managerKmsEncryptor.encryptSecret("key", "value", vaultConfig);
    DelegateTask task = delegateTaskArgumentCaptor.getValue();
    assertThat(task.getSetupAbstractions()).isEmpty();
  }
}
