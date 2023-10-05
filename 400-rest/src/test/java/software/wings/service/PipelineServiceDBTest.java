/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.rule.OwnerRule.GEORGE;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.BuildWorkflow.BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow;
import static software.wings.beans.Graph.Builder.aGraph;
import static software.wings.beans.GraphLink.Builder.aLink;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.sm.StateType.ENV_STATE;
import static software.wings.utils.WingsTestConstants.PIPELINE_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.RepairActionCode;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.Application;
import software.wings.beans.BuildWorkflow;
import software.wings.beans.FailureStrategy;
import software.wings.beans.Graph;
import software.wings.beans.GraphNode;
import software.wings.beans.LicenseInfo;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.Workflow;
import software.wings.beans.account.AccountStatus;
import software.wings.dl.WingsPersistence;
import software.wings.scheduler.BackgroundJobScheduler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.StateType;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PipelineServiceDBTest extends WingsBaseTest {
  @Mock private BackgroundJobScheduler jobScheduler;

  @Inject @InjectMocks private AccountService accountService;
  @Inject @InjectMocks private AppService appService;
  @Inject @InjectMocks private WorkflowService workflowService;
  @Inject @InjectMocks private PipelineService pipelineService;
  @Inject @InjectMocks private WingsPersistence wingsPersistence;

  Account createAccount() {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setAccountType(AccountType.PAID);
    licenseInfo.setLicenseUnits(10);
    Account account =
        anAccount().withAccountName("test-account").withCompanyName("Harness").withLicenseInfo(licenseInfo).build();
    return accountService.save(account, false);
  }

  private Application createApplication(Account account) {
    Application application = anApplication().name("test-application").accountId(account.getUuid()).build();
    return appService.save(application);
  }

  private Workflow createWorkflow(Application application) {
    Graph graph =
        aGraph()
            .addNodes(GraphNode.builder().id("n1").name("stop").type(StateType.ENV_STATE.name()).origin(true).build())
            .addLinks(aLink().withId("l1").withFrom("n1").withTo("n2").withType("success").build())
            .addLinks(aLink().withId("l2").withFrom("n2").withTo("n3").withType("success").build())
            .build();

    BuildWorkflow orchestrationWorkflow = aBuildOrchestrationWorkflow().withGraph(graph).build();
    Workflow workflow = aWorkflow()
                            .appId(application.getUuid())
                            .name("workflow1")
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .orchestrationWorkflow(orchestrationWorkflow)
                            .build();

    return workflowService.createWorkflow(workflow);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldUpdatePipeline() {
    final Account account = createAccount();
    final Application application = createApplication(account);
    final Workflow workflow = createWorkflow(application);

    Map<String, Object> properties = new HashMap<>();
    properties.put("workflowId", workflow.getUuid());

    PipelineStage pipelineStage =
        PipelineStage.builder()
            .pipelineStageElements(asList(
                PipelineStageElement.builder().name("STAGE1").type(ENV_STATE.name()).properties(properties).build()))
            .build();

    Pipeline pipeline = Pipeline.builder()
                            .name("pipeline1")
                            .appId(application.getUuid())
                            .uuid(PIPELINE_ID)
                            .pipelineStages(asList(pipelineStage))
                            .build();

    pipelineService.save(pipeline);

    List<FailureStrategy> failureStrategies =
        asList(FailureStrategy.builder().repairActionCode(RepairActionCode.MANUAL_INTERVENTION).build());

    pipelineService.updateFailureStrategies(application.getUuid(), pipeline.getUuid(), failureStrategies);

    Pipeline updated = wingsPersistence.getWithAppId(Pipeline.class, application.getUuid(), pipeline.getUuid());

    assertThat(updated.getFailureStrategies()).isEqualTo(failureStrategies);
  }
}
