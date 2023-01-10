/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.core.services.CVNextGenConstants.VERIFICATION_SERVICE_SECRET;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import io.harness.CvNextGenCommonsTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.core.services.api.VerificationServiceSecretManager;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class VerificationServiceSecretManagerImplTest extends CvNextGenCommonsTestBase {
  @Inject private VerificationServiceSecretManager verificationServiceSecretManager;
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetVerificationServiceSecretKey_whenEnvVariableDefined() {
    String verificationServiceSecret = generateUuid();
    // TODO: We need a separate service for env variables which we can mock.
    VerificationServiceSecretManagerImpl spy =
        (VerificationServiceSecretManagerImpl) spy(verificationServiceSecretManager);
    doReturn(verificationServiceSecret).when(spy).getEnv(VERIFICATION_SERVICE_SECRET);
    assertThat(spy.getVerificationServiceSecretKey()).isEqualTo(verificationServiceSecret);
  }
}
