/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.ado;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.scmerrorhandling.dtos.ErrorMetadata;
import io.harness.gitsync.common.scmerrorhandling.handlers.ScmApiErrorHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class AdoListFilesScmApiErrorHandler implements ScmApiErrorHandler {
  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {}
}
