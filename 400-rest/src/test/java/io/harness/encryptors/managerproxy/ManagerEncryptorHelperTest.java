/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.managerproxy;

import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.rule.OwnerRule.ARVIND;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.delegatetasks.FetchSecretTaskResponse;
import io.harness.delegatetasks.ValidateSecretManagerConfigurationTaskParameters;
import io.harness.delegatetasks.ValidateSecretManagerConfigurationTaskResponse;
import io.harness.delegatetasks.ValidateSecretReferenceTaskParameters;
import io.harness.delegatetasks.ValidateSecretReferenceTaskResponse;
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

public class ManagerEncryptorHelperTest extends CategoryTest {
  @Mock private DelegateService delegateService;
  @Mock private TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  @Inject @InjectMocks private ManagerEncryptorHelper managerEncryptorHelper;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final String projectIdentifier = "PROJECT_IDENTIFIER";
  private static final String orgIdentifier = "ORG_IDENTIFIER";
  private static final String accountId = "ACCOUNT_ID";

  private static final String projectOwner = "ORG_IDENTIFIER/PROJECT_IDENTIFIER";
  private static final String orgOwner = "ORG_IDENTIFIER";

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void fetchSecretValueTest() throws Exception {
    doReturn(projectOwner).when(taskSetupAbstractionHelper).getOwner(accountId, orgIdentifier, projectIdentifier);
    NGSecretManagerMetadata metadata =
        NGSecretManagerMetadata.builder().orgIdentifier(orgIdentifier).projectIdentifier(projectIdentifier).build();
    VaultConfig vaultConfig = VaultConfig.builder().accountId(accountId).ngMetadata(metadata).build();

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    doReturn(FetchSecretTaskResponse.builder().build())
        .when(delegateService)
        .executeTaskV2(delegateTaskArgumentCaptor.capture());

    managerEncryptorHelper.fetchSecretValue(accountId, null, vaultConfig);
    DelegateTask task = delegateTaskArgumentCaptor.getValue();
    assertThat(task.getSetupAbstractions().get(OWNER)).isEqualTo(projectOwner);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void validateReferenceTest() throws Exception {
    doReturn(projectOwner).when(taskSetupAbstractionHelper).getOwner(accountId, orgIdentifier, projectIdentifier);
    NGSecretManagerMetadata metadata =
        NGSecretManagerMetadata.builder().orgIdentifier(orgIdentifier).projectIdentifier(projectIdentifier).build();
    VaultConfig vaultConfig = VaultConfig.builder().accountId(accountId).ngMetadata(metadata).build();

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    doReturn(ValidateSecretReferenceTaskResponse.builder().build())
        .when(delegateService)
        .executeTaskV2(delegateTaskArgumentCaptor.capture());

    managerEncryptorHelper.validateReference(
        accountId, ValidateSecretReferenceTaskParameters.builder().encryptionConfig(vaultConfig).build());
    DelegateTask task = delegateTaskArgumentCaptor.getValue();
    assertThat(task.getSetupAbstractions().get(OWNER)).isEqualTo(projectOwner);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void validateConfigurationTest() throws Exception {
    doReturn(projectOwner).when(taskSetupAbstractionHelper).getOwner(accountId, orgIdentifier, projectIdentifier);
    NGSecretManagerMetadata metadata =
        NGSecretManagerMetadata.builder().orgIdentifier(orgIdentifier).projectIdentifier(projectIdentifier).build();
    VaultConfig vaultConfig = VaultConfig.builder().accountId(accountId).ngMetadata(metadata).build();

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    doReturn(ValidateSecretManagerConfigurationTaskResponse.builder().build())
        .when(delegateService)
        .executeTaskV2(delegateTaskArgumentCaptor.capture());

    managerEncryptorHelper.validateConfiguration(
        accountId, ValidateSecretManagerConfigurationTaskParameters.builder().encryptionConfig(vaultConfig).build());
    DelegateTask task = delegateTaskArgumentCaptor.getValue();
    assertThat(task.getSetupAbstractions().get(OWNER)).isEqualTo(projectOwner);
  }
}
