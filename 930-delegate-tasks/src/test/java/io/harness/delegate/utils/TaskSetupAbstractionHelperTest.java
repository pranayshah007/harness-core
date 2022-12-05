/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ARVIND;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.encryption.common.EncryptionConfig;

import com.google.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(PL)
public class TaskSetupAbstractionHelperTest extends CategoryTest {
  @Inject @InjectMocks private TaskSetupAbstractionHelper taskSetupAbstractionHelper;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final String projectIdentifier = "PROJECT_IDENTIFIER";
  private static final String orgIdentifier = "ORG_IDENTIFIER";
  private static final String accountId = "ACCOUNT_ID";

  private static final String projectOwner = "ORG_IDENTIFIER/PROJECT_IDENTIFIER";
  private static final String orgOwner = "ORG_IDENTIFIER";

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void getOwnerTest() {
    validateOwner(true, orgOwner, projectOwner);
  }

  private void validateOwner(boolean featureFlagEnabled, String orgOwner, String projectOwner) {
    EncryptionConfig encryptionConfig = Mockito.mock(EncryptionConfig.class);
    // FF: <input>; Account level
    doReturn(accountId).when(encryptionConfig).getAccountId();
    assertThat(taskSetupAbstractionHelper.getOwner(accountId, null, null)).isNull();

    // FF: <input>; Org level
    assertThat(taskSetupAbstractionHelper.getOwner(accountId, orgIdentifier, null)).isEqualTo(orgOwner);

    // FF: <input>; Project level
    assertThat(taskSetupAbstractionHelper.getOwner(accountId, orgIdentifier, projectIdentifier))
        .isEqualTo(projectOwner);

    // FF: <input>; Org missing
    assertThat(taskSetupAbstractionHelper.getOwner(accountId, null, projectIdentifier)).isNull();
  }
}
