/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.delegate.utils.DelegateServiceConstants.ACCOUNT_ID;
import static io.harness.rule.OwnerRule.ANUPAM;

import static org.mockito.Mockito.verify;

import io.harness.DelegateServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.DelegateTokenAuthenticator;
import io.harness.service.intfc.DelegateAuthService;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(DEL)
@RunWith(MockitoJUnitRunner.class)
public class DelegateAuthServiceTest extends DelegateServiceTestBase {
  private final String VALID_TOKEN = "HEADER.PAYLOAD.ENCODED_DATA";

  @Mock private DelegateTokenAuthenticator delegateTokenAuthenticator;

  @InjectMocks @Inject private DelegateAuthService delegateAuthService;

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void testValidateDelegateToken() {
    delegateAuthService.validateDelegateToken(ACCOUNT_ID, VALID_TOKEN, null, null, null, false);
    verify(delegateTokenAuthenticator).validateDelegateToken(ACCOUNT_ID, VALID_TOKEN, null, null, null, false);
  }
}
