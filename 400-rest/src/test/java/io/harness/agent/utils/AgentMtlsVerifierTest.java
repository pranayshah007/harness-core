/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.agent.utils;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.rule.OwnerRule.JOHANNES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.agent.beans.AgentMtlsEndpointDetails;
import io.harness.agent.beans.AgentMtlsMode;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.service.intfc.AgentMtlsEndpointService;

import software.wings.WingsBaseTest;

import java.nio.file.InvalidPathException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(DEL)
public class AgentMtlsVerifierTest extends WingsBaseTest {
  private static final String ACCOUNT_ID_0 = "account0";
  private static final String ACCOUNT_ID_1 = "account1";

  private static final String FQDN_0 = "fqdn0";
  private static final String FQDN_1 = "fqdn1";

  private AgentMtlsEndpointService agentMtlsEndpointServiceMock;
  private AgentMtlsVerifier agentMtlsVerifier;

  @Before
  public void setUp() {
    this.agentMtlsEndpointServiceMock = mock(AgentMtlsEndpointService.class);
    this.agentMtlsVerifier = new AgentMtlsVerifier(this.agentMtlsEndpointServiceMock);
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testAccountIdNull() {
    this.agentMtlsVerifier.isValidRequest(null, null);
  }

  @Test(expected = InvalidPathException.class)
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testMtlsEndpointServiceThrowsException() {
    when(this.agentMtlsEndpointServiceMock.getEndpointForAccountOrNull(ACCOUNT_ID_0))
        .thenThrow(new InvalidPathException("Thrown by UT", "Thrown by UT"));
    this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, null);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testNoMtlsEntry() {
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, null)).isTrue();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, "")).isTrue();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, FQDN_0)).isFalse();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, FQDN_1)).isFalse();

    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_1, null)).isTrue();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_1, "")).isTrue();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_1, FQDN_0)).isFalse();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_1, FQDN_1)).isFalse();
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testLooseMode() {
    when(this.agentMtlsEndpointServiceMock.getEndpointForAccountOrNull(ACCOUNT_ID_0))
        .thenReturn(
            AgentMtlsEndpointDetails.builder().accountId(ACCOUNT_ID_0).mode(AgentMtlsMode.LOOSE).fqdn(FQDN_0).build());
    when(this.agentMtlsEndpointServiceMock.getEndpointForAccountOrNull(ACCOUNT_ID_1))
        .thenReturn(
            AgentMtlsEndpointDetails.builder().accountId(ACCOUNT_ID_1).mode(AgentMtlsMode.LOOSE).fqdn(FQDN_1).build());

    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, null)).isTrue();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, "")).isTrue();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, FQDN_0)).isTrue();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, FQDN_1)).isFalse();

    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_1, null)).isTrue();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_1, "")).isTrue();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_1, FQDN_0)).isFalse();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_1, FQDN_1)).isTrue();
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testStrictMode() {
    when(this.agentMtlsEndpointServiceMock.getEndpointForAccountOrNull(ACCOUNT_ID_0))
        .thenReturn(
            AgentMtlsEndpointDetails.builder().accountId(ACCOUNT_ID_0).mode(AgentMtlsMode.STRICT).fqdn(FQDN_0).build());
    when(this.agentMtlsEndpointServiceMock.getEndpointForAccountOrNull(ACCOUNT_ID_1))
        .thenReturn(
            AgentMtlsEndpointDetails.builder().accountId(ACCOUNT_ID_1).mode(AgentMtlsMode.STRICT).fqdn(FQDN_1).build());

    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, null)).isFalse();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, "")).isFalse();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, FQDN_0)).isTrue();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, FQDN_1)).isFalse();

    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_1, null)).isFalse();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_1, "")).isFalse();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_1, FQDN_0)).isFalse();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_1, FQDN_1)).isTrue();
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testCaching() {
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, null)).isTrue();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_1, null)).isTrue();

    // change mTLS endpoint for account0 and try again (should still be cached)
    when(this.agentMtlsEndpointServiceMock.getEndpointForAccountOrNull(ACCOUNT_ID_0))
        .thenReturn(
            AgentMtlsEndpointDetails.builder().accountId(ACCOUNT_ID_0).mode(AgentMtlsMode.STRICT).fqdn(FQDN_0).build());

    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, null)).isTrue();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_1, null)).isTrue();

    // invalidate cache for account0 and try again
    this.agentMtlsVerifier.invalidateCacheFor(ACCOUNT_ID_0);

    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_0, null)).isFalse();
    assertThat(this.agentMtlsVerifier.isValidRequest(ACCOUNT_ID_1, null)).isTrue();
  }
}
