/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.webhook.errorhandler.handlers;

import static io.harness.annotations.dev.HarnessTeam.SPG;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.WingsException;
import io.harness.ng.webhook.errorhandler.dtos.ErrorMetadata;

@OwnedBy(SPG)
public interface ScmApiErrorHandler {
  void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException;
}
