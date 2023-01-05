/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.sweepingoutput;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SweepingOutput;
import io.harness.beans.SweepingOutputInstance;
import io.harness.deployment.InstanceDetails;

import software.wings.api.InstanceElement;
import software.wings.sm.StateExecutionInstance;

import dev.morphia.query.Query;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface SweepingOutputService {
  SweepingOutputInstance save(@Valid SweepingOutputInstance sweepingOutputInstance);

  void ensure(@Valid SweepingOutputInstance sweepingOutputInstance);

  SweepingOutputInstance find(SweepingOutputInquiry inquiry);

  List<SweepingOutputInstance> findManyWithNamePrefix(
      SweepingOutputInquiry inquiry, SweepingOutputInstance.Scope scope);

  <T extends SweepingOutput> T findSweepingOutput(SweepingOutputInquiry inquiry);

  <T extends SweepingOutput> List<T> findSweepingOutputsWithNamePrefix(
      SweepingOutputInquiry inquiry, SweepingOutputInstance.Scope scope);

  void copyOutputsForAnotherWorkflowExecution(
      String appId, String fromWorkflowExecutionId, String toWorkflowExecutionId);

  Query<SweepingOutputInstance> prepareApprovalStateOutputsQuery(
      String appId, String fromPipelineExecutionId, String fromStateExecutionId);

  Query<SweepingOutputInstance> prepareEnvStateOutputsQuery(
      String appId, String fromPipelineExecutionId, String fromWorkflowExecutionId);

  void cleanForStateExecutionInstance(@NotNull StateExecutionInstance stateExecutionInstance);

  void deleteById(@NotNull String appId, @NotNull String uuid);

  List<InstanceDetails> fetchInstanceDetailsFromSweepingOutput(SweepingOutputInquiry inquiry, boolean newInstancesOnly);

  List<InstanceDetails> findInstanceDetailsForWorkflowExecution(String appId, String workflowExecutionId);

  List<InstanceElement> fetchInstanceElementsFromSweepingOutput(
      SweepingOutputInquiry inquiry, boolean newInstancesOnly);
}
