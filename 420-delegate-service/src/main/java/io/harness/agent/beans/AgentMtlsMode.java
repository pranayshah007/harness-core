/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.agent.beans;

/**
 * Specifies the supported modes for agent mTLS endpoints.
 */
public enum AgentMtlsMode {
  /**
   * Allows both mTLS and non-mTLS agents to connect to the manager.
   * This allows customers to seamlessly migrate their harness agent fleet to mTLS.
   */
  LOOSE,

  /**
   * Allows only mTLS agents to connect to the manager - non-mTLS agents are rejected.
   */
  STRICT
}
