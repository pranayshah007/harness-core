/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.context.ContextElementType;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContextImpl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@Singleton
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class RepeatStateHelper {
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private WorkflowExecutionService workflowExecutionService;

  public List<ContextElement> filterElementsWithArtifactFromLastDeployment(
      ExecutionContextImpl context, List<ContextElement> repeatElements) {
    WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(
        context.getAppId(), context.getWorkflowExecutionId(), WorkflowExecutionKeys.artifacts);
    if (workflowExecution == null) {
      throw new InvalidRequestException("Execution No longer Exists : " + context.getWorkflowExecutionId());
    }
    List<ContextElement> filteredElements = new ArrayList<>();
    Artifact rollbackArtifact = workflowExecution.getArtifacts().get(0);
    for (ContextElement contextElement : repeatElements) {
      if (ContextElementType.INSTANCE == contextElement.getElementType()) {
        Artifact previousArtifact = serviceResourceService.findPreviousArtifact(
            context.getAppId(), context.getWorkflowExecutionId(), contextElement);
        if (previousArtifact == null || rollbackArtifact == null
            || (rollbackArtifact.getUuid().equals(previousArtifact.getUuid()))) {
          filteredElements.add(contextElement);
        }
      } else {
        filteredElements.add(contextElement);
      }
    }
    return filteredElements;
  }
}
