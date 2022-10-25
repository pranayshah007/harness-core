/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.persistence.LogKeyUtils;

import software.wings.beans.WorkflowExecution;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowExecutionLogContext extends AutoLogContext {
  public static final String ID = LogKeyUtils.calculateLogKeyForId(WorkflowExecution.class);

  public WorkflowExecutionLogContext(String workflowExecutionId, OverrideBehavior behavior) {
    super(ID, workflowExecutionId, behavior);
  }
}
