/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.morphia.annotations.Transient;
import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@TargetModule(HarnessModule._815_CG_TRIGGERS)
@Value
@Builder
@JsonTypeName("WorkflowAction")
public class WorkflowAction implements Action {
  @NotEmpty private String workflowId;
  @NotEmpty private ActionType actionType = ActionType.WORKFLOW;
  private TriggerArgs triggerArgs;
  @Transient private String workflowName;
}
