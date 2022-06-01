/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import static io.harness.rule.OwnerRule.MARKO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.SmtpCapability;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SmtpCapabilityCheckTest {
  private final SmtpCapabilityCheck underTest = new SmtpCapabilityCheck();

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldPerformCapabilityCheck() {
    final SmtpCapability smtpCapability =
        SmtpCapability.builder().host("smtp.harness.com").port(25).username("user").useSSL(true).startTLS(true).build();
    try (MockedStatic<SmtpCapabilityCheck> capabilityCheck = mockStatic(SmtpCapabilityCheck.class)) {
      capabilityCheck.when(() -> SmtpCapabilityCheck.isCapable(true, true, "smtp.harness.com", 25, "user"))
          .thenReturn(true);
      final CapabilityResponse capabilityResponse = underTest.performCapabilityCheck(smtpCapability);
      assertThat(capabilityResponse).isNotNull();
      assertThat(capabilityResponse.isValidated()).isTrue();
    }
  }
}
