/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.agent;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

/**
 * Provides helper methods for agent gateway related functionality.
 */
@UtilityClass
@OwnedBy(DEL)
public class AgentGatewayUtils {
  /**
   * Determines whether an agent is connected using mTLS by analyzing the
   * AgentGatewayConstants.HEADER_AGENT_MTLS_AUTHORITY header.
   * @param agentMtlsAuthorityHeader AgentGatewayConstants.HEADER_AGENT_MTLS_AUTHORITY header, or null if it doesn't
   *     exist.
   * @return true if the agent is connected using mTLS.
   */
  public boolean isAgentConnectedUsingMtls(String agentMtlsAuthorityHeader) {
    return isNotEmpty(agentMtlsAuthorityHeader);
  }
}
