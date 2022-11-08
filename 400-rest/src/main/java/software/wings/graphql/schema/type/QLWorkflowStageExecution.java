/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.type;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@OwnedBy(CDC)
@Value
@Builder
@FieldNameConstants(innerTypeName = "QLWorkflowStageExecutionKeys")
@Scope(PermissionAttribute.ResourceType.APPLICATION)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLWorkflowStageExecution implements QLPipelineStageExecution {
  private String pipelineStageElementId;
  private String pipelineStepName;
  private String pipelineStageName;

  // Workflows executions list which is one element. Check the list for empty or NULL
  private String workflowExecutionId;
  // Runtime is only there when above workflowExecutionId is NULL
  private List<QLVariable> runtimeInputVariables;
  private QLExecutionStatus status;
  private List<QLInputVariable> inputVariables;
}
