/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraform.cleanup;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.TerraformSecretCleanupFailureException;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.security.encryption.EncryptedRecordData;

import software.wings.beans.VaultConfig;

import java.io.IOException;
import java.util.List;
import org.jose4j.lang.JoseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class TerraformSecretCleanupTaskNGTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock EncryptDecryptHelper encryptDecryptHelper;

  private final DelegateTaskPackage delegateTaskPackage =
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build();

  @InjectMocks
  TerraformSecretCleanupTaskNG terraformSecretCleanupTaskNG =
      new TerraformSecretCleanupTaskNG(delegateTaskPackage, null, response -> {}, () -> true);

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testRunArrayParameters() {
    assertThatThrownBy(() -> terraformSecretCleanupTaskNG.run(new Object[] {null}))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testRunTask() throws Exception {
    TerraformSecretCleanupTaskParameters params =
        TerraformSecretCleanupTaskParameters.builder()
            .cleanupUuid("testCleanupUuid")
            .encryptedRecordDataList(
                List.of(EncryptedRecordData.builder().build(), EncryptedRecordData.builder().build()))
            .encryptionConfig(VaultConfig.builder().build())
            .build();

    doReturn(true).when(encryptDecryptHelper).deleteEncryptedRecord(any(), any());
    TerraformSecretCleanupTaskResponse response =
        (TerraformSecretCleanupTaskResponse) terraformSecretCleanupTaskNG.run(params);
    assertThat(response.getResponseDataUuid()).isEqualTo("testCleanupUuid");
    verify(encryptDecryptHelper, times(2)).deleteEncryptedRecord(any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testRunTaskEmptyConfigIsThrownException() {
    TerraformSecretCleanupTaskParameters params =
        TerraformSecretCleanupTaskParameters.builder()
            .cleanupUuid("testCleanupUuid-1")
            .encryptedRecordDataList(List.of(EncryptedRecordData.builder().build()))
            .encryptionConfig(null)
            .build();
    doThrow(new RuntimeException()).when(encryptDecryptHelper).deleteEncryptedRecord(any(), any());

    assertThatThrownBy(() -> terraformSecretCleanupTaskNG.run(params))
        .isInstanceOf(TerraformSecretCleanupFailureException.class)
        .matches(exception -> {
          assertThat(exception.getMessage())
              .isEqualTo("EncryptionConfig should not be null when deleting terraform vault secret");
          return true;
        });
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testRunTaskOneSecretFailedToDelete() throws JoseException, IOException {
    TerraformSecretCleanupTaskParameters params =
        TerraformSecretCleanupTaskParameters.builder()
            .cleanupUuid("testCleanupUuid")
            .encryptedRecordDataList(
                List.of(EncryptedRecordData.builder().build(), EncryptedRecordData.builder().build()))
            .encryptionConfig(VaultConfig.builder().build())
            .build();

    doReturn(true)
        .doThrow(new RuntimeException(new Throwable("test-exception-cause-message")))
        .when(encryptDecryptHelper)
        .deleteEncryptedRecord(any(), any());

    TerraformSecretCleanupTaskResponse response =
        (TerraformSecretCleanupTaskResponse) terraformSecretCleanupTaskNG.run(params);
    assertThat(response.getResponseDataUuid()).isEqualTo("testCleanupUuid");
    verify(encryptDecryptHelper, times(2)).deleteEncryptedRecord(any(), any());
    assertThat(response.getSecretCleanupFailureDetailsList()).isNotEmpty();
    assertThat(response.getSecretCleanupFailureDetailsList().get(0).getExceptionMessage())
        .isEqualTo("test-exception-cause-message");
  }
}
