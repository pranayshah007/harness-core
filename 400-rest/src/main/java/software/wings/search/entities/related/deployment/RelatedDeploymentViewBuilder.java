/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.entities.related.deployment;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.WorkflowExecution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Singleton;
import java.util.Map;

@OwnedBy(PL)
@Singleton
public class RelatedDeploymentViewBuilder {
  public Map<String, Object> getDeploymentRelatedEntityViewMap(WorkflowExecution workflowExecution) {
    RelatedDeploymentView relatedDeploymentView = new RelatedDeploymentView(workflowExecution.getUuid(),
        workflowExecution.getStatus(), workflowExecution.getName(), workflowExecution.getCreatedAt(),
        workflowExecution.getPipelineExecutionId(), workflowExecution.getWorkflowId(),
        workflowExecution.getWorkflowType().name(), workflowExecution.getEnvId());
    ObjectMapper mapper = new ObjectMapper();
    return mapper.convertValue(relatedDeploymentView, new TypeReference<Map<String, Object>>() {});
  }
}
