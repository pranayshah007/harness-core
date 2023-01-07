/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.WorkflowType;

import software.wings.beans.Application;
import software.wings.beans.BuildWorkflow;
import software.wings.beans.Environment;
import software.wings.beans.Environment.Builder;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowBuilder;
import software.wings.dl.WingsPersistence;
import software.wings.search.framework.SearchDistributedLock.SearchDistributedLockKeys;

import dev.morphia.query.Query;
import lombok.AllArgsConstructor;

@OwnedBy(PL)
@AllArgsConstructor
public class SearchSyncHeartbeat implements Runnable {
  private WingsPersistence wingsPersistence;
  private String lockName;
  private String uuid;

  @Override
  public void run() {
    Query<SearchDistributedLock> query = wingsPersistence.createQuery(SearchDistributedLock.class)
                                             .field(SearchDistributedLockKeys.name)
                                             .equal(lockName)
                                             .field(SearchDistributedLockKeys.uuid)
                                             .equal(uuid);

    SearchDistributedLock searchDistributedLock = query.get();

    if (searchDistributedLock != null) {
      String dummyAccountId = "6b2bc4d2f49c11e9b82c5a1";
      EmbeddedUser embeddedUser = new EmbeddedUser("6b2bc4d2f49c11e9b82c5a", "Search Job", "search@harness.io", "");

      Application application = Application.Builder.anApplication()
                                    .name("Dummy application")
                                    .accountId(dummyAccountId)
                                    .createdBy(embeddedUser)
                                    .build();
      String applicationId = wingsPersistence.save(application);
      application.setUuid(applicationId);

      Service service = Service.builder()
                            .name("Dummy service")
                            .appId(applicationId)
                            .accountId(dummyAccountId)
                            .createdBy(embeddedUser)
                            .build();
      String serviceId = wingsPersistence.save(service);
      service.setUuid(serviceId);

      Environment environment = Builder.anEnvironment()
                                    .name("Dummy environment")
                                    .appId(dummyAccountId)
                                    .environmentType(EnvironmentType.NON_PROD)
                                    .createdBy(embeddedUser)
                                    .accountId(dummyAccountId)
                                    .build();
      String environmentId = wingsPersistence.save(environment);
      environment.setUuid(environmentId);

      OrchestrationWorkflow orchestrationWorkflow = new BuildWorkflow();
      Workflow workflow = WorkflowBuilder.aWorkflow()
                              .name("Dummy workflow")
                              .workflowType(WorkflowType.ORCHESTRATION)
                              .appId(applicationId)
                              .orchestrationWorkflow(orchestrationWorkflow)
                              .createdBy(embeddedUser)
                              .accountId(dummyAccountId)
                              .build();
      String workflowId = wingsPersistence.save(workflow);
      workflow.setUuid(workflowId);

      Pipeline pipeline = Pipeline.builder()
                              .name("Dummy pipeline")
                              .appId(applicationId)
                              .accountId(dummyAccountId)
                              .createdBy(embeddedUser)
                              .build();
      String pipelineId = wingsPersistence.save(pipeline);
      pipeline.setUuid(pipelineId);

      wingsPersistence.delete(service);
      wingsPersistence.delete(environment);
      wingsPersistence.delete(workflow);
      wingsPersistence.delete(pipeline);
      wingsPersistence.delete(application);
    }
  }
}
