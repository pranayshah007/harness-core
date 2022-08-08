/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.smtp;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MANKRIT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.noInteractions;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.NgSmtpDTO;
import io.harness.ng.core.smtp.resources.SmtpNgResource;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(PL)
@RunWith(MockitoJUnitRunner.class)
public class SmtpNgResourceTest extends CategoryTest {
  public static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  SmtpNgResource smtpNgResource;
  @Mock private SmtpNgService smtpNgService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private NgSmtpDTO ngSmtpDTO;

  @Before
  public void setup() {
    smtpNgResource = new SmtpNgResource(smtpNgService, accessControlClient);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testSaveAccessForSMTPSuccess() throws IOException {
    Mockito.doThrow(RuntimeException.class).when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    smtpNgResource.save(ngSmtpDTO, ACCOUNT_IDENTIFIER);
    verify(ngSmtpDTO, noInteractions()).setAccountId(any());
    verify(smtpNgService, noInteractions()).saveSmtpSettings(any());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testSaveAccessForSMTPFail() throws IOException {
    smtpNgResource.save(ngSmtpDTO, ACCOUNT_IDENTIFIER);
    verify(ngSmtpDTO, times(1)).setAccountId(ACCOUNT_IDENTIFIER);
    verify(smtpNgService, times(1)).saveSmtpSettings(any());
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testUpdateAccessForSMTPSuccess() throws IOException {
    Mockito.doThrow(RuntimeException.class).when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    smtpNgResource.update(ngSmtpDTO, ACCOUNT_IDENTIFIER);
    verify(ngSmtpDTO, noInteractions()).setAccountId(any());
    verify(smtpNgService, noInteractions()).updateSmtpSettings(any());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testUpdateAccessForSMTPFail() throws IOException {
    smtpNgResource.update(ngSmtpDTO, ACCOUNT_IDENTIFIER);
    verify(ngSmtpDTO, times(1)).setAccountId(ACCOUNT_IDENTIFIER);
    verify(smtpNgService, times(1)).updateSmtpSettings(any());
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testDeleteAccessForSMTPSuccess() throws IOException {
    Mockito.doThrow(RuntimeException.class).when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    smtpNgResource.delete("I", ACCOUNT_IDENTIFIER);
    verify(ngSmtpDTO, noInteractions()).setAccountId(any());
    verify(smtpNgService, noInteractions()).deleteSmtpSettings(any());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testDeleteAccessForSMTPFail() throws IOException {
    smtpNgResource.delete("I", ACCOUNT_IDENTIFIER);
    verify(smtpNgService, times(1)).deleteSmtpSettings(any());
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetAccessForSMTPSuccess() throws IOException {
    Mockito.doThrow(RuntimeException.class).when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    smtpNgResource.get(ACCOUNT_IDENTIFIER);
    verify(ngSmtpDTO, noInteractions()).setAccountId(any());
    verify(smtpNgService, noInteractions()).getSmtpSettings(any());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetAccessForSMTPFail() throws IOException {
    smtpNgResource.get(ACCOUNT_IDENTIFIER);
    verify(smtpNgService, times(1)).getSmtpSettings(any());
  }
}
