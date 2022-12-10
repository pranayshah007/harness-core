/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.cvng;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.change.HarnessCDCurrentGenEventMetadata;
import io.harness.persistence.HPersistence;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.cvng.CDChangeSourceIntegrationService;

import com.google.inject.Inject;
import com.mongodb.ReadPreference;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

@OwnedBy(CV)
public class CDChangeSourceIntegrationServiceImpl implements CDChangeSourceIntegrationService {
  @Inject private HPersistence hPersistence;
  @Inject private WorkflowExecutionService workflowExecutionService;

  @Override
  public List<HarnessCDCurrentGenEventMetadata> getCurrentGenEventsBetween(
      String accountId, String appId, String serviceId, String environmentId, Instant startTime, Instant endTime) {
    Query<WorkflowExecution> query = hPersistence.createAnalyticsQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.accountId, accountId)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .field(WorkflowExecutionKeys.serviceIds)
                                         .contains(serviceId)
                                         .field(WorkflowExecutionKeys.envIds)
                                         .contains(environmentId)
                                         .field(WorkflowExecutionKeys.endTs)
                                         .greaterThanOrEq(startTime.toEpochMilli())
                                         .field(WorkflowExecutionKeys.endTs)
                                         .lessThanOrEq(endTime.toEpochMilli());
    FindOptions findOptions = new FindOptions().readPreference(ReadPreference.secondaryPreferred());
    List<WorkflowExecution> workflowExecutions =
        workflowExecutionService.listExecutionsUsingQuery(query, findOptions, false);
    List<HarnessCDCurrentGenEventMetadata> changeEvents = new ArrayList<>();
    for (WorkflowExecution execution : workflowExecutions) {
      for (int index = 0; index < execution.getServiceIds().size(); index++) {
        if (execution.getServiceIds().get(index).equals(serviceId)
            && execution.getEnvIds().get(index).equals(environmentId)) {
          changeEvents.add(HarnessCDCurrentGenEventMetadata.builder()
                               .accountId(execution.getAccountId())
                               .appId(execution.getAppId())
                               .serviceId(execution.getServiceIds().get(index))
                               .environmentId(execution.getEnvIds().get(index))
                               .workflowId(execution.getWorkflowId())
                               .workflowStartTime(execution.getStartTs())
                               .workflowEndTime(execution.getEndTs())
                               .workflowExecutionId(execution.getUuid())
                               .name(execution.getName())
                               .artifactType(execution.getArtifacts().get(index).getArtifactStreamType())
                               .artifactName(execution.getArtifacts().get(index).getDisplayName())
                               .status(execution.getStatus().name())
                               .build());
        }
      }
    }

    return changeEvents;
  }
}
