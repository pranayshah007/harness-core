/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.service;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.ScmConnector;

import java.util.function.Function;

@OwnedBy(DX)
public interface ScmOrchestratorService {
  // Caller code eg:
  //    processScmRequest( c->c.listFiles(params);
  <R> R processScmRequest(Function<ScmClientFacilitatorService, R> scmRequest, String projectIdentifier,
      String orgIdentifier, String accountId);

  <R> R processScmRequestUsingConnectorSettings(Function<ScmClientFacilitatorService, R> scmRequest,
      String projectIdentifier, String orgIdentifier, String accountId, String connectorIdentifierRef,
      String connectorRepo, String connectorBranch);

  <R> R processScmRequestUsingConnectorSettings(Function<ScmClientFacilitatorService, R> scmRequest,
      String projectIdentifier, String orgIdentifier, String accountId, String connectorIdentifierRef);

  <R> R processScmRequestUsingConnectorSettings(
      Function<ScmClientFacilitatorService, R> scmRequest, ScmConnector scmConnector);

  boolean isExecuteOnDelegate(String projectIdentifier, String orgIdentifier, String accountId);
}
