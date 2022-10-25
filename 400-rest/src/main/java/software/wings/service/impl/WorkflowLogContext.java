/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.persistence.LogKeyUtils;

import software.wings.beans.Workflow;

@OwnedBy(CDC)
public class WorkflowLogContext extends AutoLogContext {
  public static final String ID = LogKeyUtils.calculateLogKeyForId(Workflow.class);

  public WorkflowLogContext(String workflowId, OverrideBehavior behavior) {
    super(ID, workflowId, behavior);
  }
}
