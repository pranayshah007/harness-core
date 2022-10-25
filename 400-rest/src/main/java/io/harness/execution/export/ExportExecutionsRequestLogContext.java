/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.export.request.ExportExecutionsRequest;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.persistence.LogKeyUtils;

@OwnedBy(CDC)
public class ExportExecutionsRequestLogContext extends AutoLogContext {
  public static final String ID = LogKeyUtils.calculateLogKeyForId(ExportExecutionsRequest.class);

  public ExportExecutionsRequestLogContext(String requestId, OverrideBehavior behavior) {
    super(ID, requestId, behavior);
  }
}
