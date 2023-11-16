/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import static io.harness.rule.OwnerRule.SARTHAK_KASAT;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.SshInfraDelegateConfig;
import io.harness.ng.core.dto.secrets.SSHAuthDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SSHAuthScheme;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SshConnectivityExecutionCapabilityTest extends CategoryTest {
  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetCapabilityToString() {
    SshInfraDelegateConfig sshInfraDelegateConfig =
        PdcSshInfraDelegateConfig.builder()
            .sshKeySpecDto(
                SSHKeySpecDTO.builder().port(1234).auth(SSHAuthDTO.builder().type(SSHAuthScheme.SSH).build()).build())
            .build();
    SshConnectivityExecutionCapability sshConnectivityExecutionCapability =
        new SshConnectivityExecutionCapability(sshInfraDelegateConfig, "host1");

    String capabilityResponse = sshConnectivityExecutionCapability.getCapabilityToString();
    assertEquals(
        "Capability to connect host1:1234 with SSH may have failed due to incorrect host/port provided or socket connection error, possibly caused by network/firewall issues.",
        capabilityResponse);
  }
}
