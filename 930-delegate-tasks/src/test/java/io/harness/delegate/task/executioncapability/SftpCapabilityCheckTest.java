/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import static io.harness.rule.OwnerRule.MATT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.SftpCapability;
import io.harness.rule.Owner;

import net.schmizz.sshj.SSHClient;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SftpCapabilityCheckTest extends CategoryTest {
  private final SftpCapability sftpCapability = SftpCapability.builder().sftpUrl("sftp:\\\\10.0.0.1").build();

  @InjectMocks private SftpCapabilityCheck sftpCapabilityCheck;

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void shouldTestPerformCapabilityCheck() throws Exception {
    try (MockedConstruction<SSHClient> ignored = Mockito.mockConstruction(SSHClient.class)) {
      CapabilityResponse capabilityResponse = sftpCapabilityCheck.performCapabilityCheck(sftpCapability);
      assertThat(capabilityResponse).isNotNull();
      assertThat(capabilityResponse.isValidated()).isTrue();
    }
  }
}
