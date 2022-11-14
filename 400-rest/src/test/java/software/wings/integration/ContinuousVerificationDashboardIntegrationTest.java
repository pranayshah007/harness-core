/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration;

import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.SRIRAM;

import static org.apache.cxf.ws.addressing.ContextUtils.generateUUID;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.Service;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.security.AppPermissionSummary;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.analysis.CVDeploymentData;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData;
import software.wings.service.impl.analysis.ContinuousVerificationService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.sm.PipelineSummary;
import software.wings.sm.StateType;
import software.wings.utils.WingsIntegrationTestConstants;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class ContinuousVerificationDashboardIntegrationTest extends IntegrationTestBase {
  @Inject ContinuousVerificationService continuousVerificationService;
  @Inject UserService userService;
  @Inject AuthService authService;
  @Inject AppService appService;
  @Inject CVActivityLogService cvActivityLogService;
  @Inject CVConfigurationService cvConfigurationService;
  @Mock FeatureFlagService featureFlagService;

  private String appId;
  private String stateExecutionId;
  private String workflowId;
  private String workflowExecutionId;
  private String serviceId;
  private String envId;

  @Override
  @Before
  public void setUp() throws Exception {
    loginAdminUser();
    User user = userService.getUserByEmail(WingsIntegrationTestConstants.adminUserEmail);
    UserThreadLocal.set(user);
    stateExecutionId = UUID.randomUUID().toString();

    envId = UUID.randomUUID().toString();

    workflowExecutionId = UUID.randomUUID().toString();

    Map<String, AppPermissionSummary> appsMap =
        authService.getUserPermissionInfo(accountId, user, false).getAppPermissionMapInternal();

    // we need to user the testApplication, it has workflows defined under it by Datagen.
    appId = appService.getAppByName(accountId, "Test Application").getUuid();
    serviceId = appsMap.get(appId).getServicePermissions().get(Action.READ).iterator().next();
    workflowId = appsMap.get(appId).getWorkflowPermissions().get(Action.READ).iterator().next();
    FieldUtils.writeField(continuousVerificationService, "featureFlagService", featureFlagService, true);
  }

  private void saveExecutions() {
    long now = System.currentTimeMillis();
    continuousVerificationService.saveCVExecutionMetaData(ContinuousVerificationExecutionMetaData.builder()
                                                              .accountId(accountId)
                                                              .applicationId(appId)
                                                              .appName("dummy")
                                                              .artifactName("cv dummy artifact")
                                                              .envName("cv dummy env")
                                                              .phaseName("dummy phase")
                                                              .pipelineName("dummy pipeline")
                                                              .workflowName("dummy workflow")
                                                              .pipelineStartTs(now)
                                                              .workflowStartTs(now)
                                                              .serviceId(serviceId)
                                                              .serviceName("dummy service")
                                                              .stateType(StateType.ELK)
                                                              .workflowId(workflowId)
                                                              .workflowExecutionId(workflowExecutionId)
                                                              .build());

    continuousVerificationService.saveCVExecutionMetaData(ContinuousVerificationExecutionMetaData.builder()
                                                              .accountId(accountId)
                                                              .applicationId(appId)
                                                              .appName("dummy")
                                                              .artifactName("cv dummy artifact")
                                                              .envName("cv dummy env")
                                                              .phaseName("dummy phase")
                                                              .pipelineName("dummy pipeline")
                                                              .workflowName("dummy workflow")
                                                              .pipelineStartTs(now)
                                                              .workflowStartTs(now)
                                                              .serviceId(serviceId)
                                                              .serviceName("dummy service")
                                                              .stateType(StateType.NEW_RELIC)
                                                              .workflowId(workflowId)
                                                              .workflowExecutionId(workflowExecutionId)
                                                              .build());

    continuousVerificationService.saveCVExecutionMetaData(ContinuousVerificationExecutionMetaData.builder()
                                                              .accountId(accountId + "123")
                                                              .applicationId(appId)
                                                              .appName("dummy")
                                                              .artifactName("cv dummy artifact")
                                                              .envName("cv dummy env")
                                                              .phaseName("dummy phase")
                                                              .pipelineName("dummy pipeline")
                                                              .workflowName("dummy workflow")
                                                              .pipelineStartTs(now)
                                                              .workflowStartTs(now)
                                                              .serviceId(serviceId)
                                                              .serviceName("dummy service")
                                                              .stateType(StateType.NEW_RELIC)
                                                              .workflowId(workflowId + "123")
                                                              .workflowExecutionId(workflowExecutionId)
                                                              .build());
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void getRecords() throws Exception {
    saveExecutions();
    long now = System.currentTimeMillis();

    long before = now - TimeUnit.MINUTES.toMillis(1), after = now + TimeUnit.MINUTES.toMillis(5);
    WebTarget getTarget = client.target(
        API_BASE + "/cvdash/get-records?accountId=" + accountId + "&beginEpochTs=" + before + "&endEpochTs=" + after);

    RestResponse<Map<Long,
        TreeMap<String, Map<String, Map<String, Map<String, List<ContinuousVerificationExecutionMetaData>>>>>>>
        response = getRequestBuilderWithAuthHeader(getTarget).get(new GenericType<RestResponse<Map<Long,
                TreeMap<String,
                    Map<String, Map<String, Map<String, List<ContinuousVerificationExecutionMetaData>>>>>>>>() {});

    assertThat(response.getResource().isEmpty()).isFalse();

    long start = Instant.ofEpochMilli(now).truncatedTo(ChronoUnit.DAYS).toEpochMilli();

    Map<Long, TreeMap<String, Map<String, Map<String, Map<String, List<ContinuousVerificationExecutionMetaData>>>>>>
        map = response.getResource();
    List<ContinuousVerificationExecutionMetaData> cvList = map.get(start)
                                                               .get("cv dummy artifact")
                                                               .get("cv dummy env/dummy workflow")
                                                               .values()
                                                               .iterator()
                                                               .next()
                                                               .get("dummy phase");
    ContinuousVerificationExecutionMetaData continuousVerificationExecutionMetaData1 = cvList.get(0);
    assertThat(accountId).isEqualTo(continuousVerificationExecutionMetaData1.getAccountId());
    assertThat("cv dummy artifact").isEqualTo(continuousVerificationExecutionMetaData1.getArtifactName());

    // validate it doesnt contain info from other account.
    for (ContinuousVerificationExecutionMetaData cv : cvList) {
      assertThat(cv.getAccountId().equals(accountId + "123")).isFalse();
    }
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void getAllCVDeploymentRecords() {
    // Setup
    long now = System.currentTimeMillis();
    continuousVerificationService.saveCVExecutionMetaData(ContinuousVerificationExecutionMetaData.builder()
                                                              .accountId(accountId)
                                                              .applicationId(appId)
                                                              .appName("dummy")
                                                              .artifactName("cv dummy artifact")
                                                              .envName("cv dummy env")
                                                              .phaseName("dummy phase")
                                                              .pipelineName("dummy pipeline")
                                                              .workflowName("dummy workflow")
                                                              .pipelineStartTs(now)
                                                              .workflowStartTs(now)
                                                              .serviceId(serviceId)
                                                              .stateExecutionId(stateExecutionId)
                                                              .envId(envId)
                                                              .serviceName("dummy service")
                                                              .stateType(StateType.ELK)
                                                              .workflowId(workflowId)
                                                              .workflowExecutionId(workflowExecutionId)
                                                              .build());

    WorkflowExecution execution1 =
        WorkflowExecution.builder().appId(appId).uuid(workflowExecutionId).status(ExecutionStatus.SUCCESS).build();
    wingsPersistence.save(execution1);

    // Call

    long before = now - TimeUnit.MINUTES.toMillis(1), after = now + TimeUnit.MINUTES.toMillis(5);
    List<CVDeploymentData> workflowExecutionList = continuousVerificationService.getCVDeploymentData(
        accountId, before, after, userService.getUserByEmail(WingsIntegrationTestConstants.adminUserEmail), serviceId);

    // Verify
    assertThat(workflowExecutionList.size() > 0).isTrue();
    assertThat(workflowExecutionList.get(0).getWorkflowExecutionId()).isEqualTo(workflowExecutionId);
    assertThat(workflowExecutionList.get(0).getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void getAllDeploymentRecords() {
    // Setup
    long now = System.currentTimeMillis();

    WorkflowExecution execution1 =
        WorkflowExecution.builder()
            .appId(appId)
            .uuid(workflowExecutionId)
            .envId(envId)
            .status(ExecutionStatus.SUCCESS)
            .startTs(now)
            .serviceIds(Arrays.asList(serviceId))
            .pipelineSummary(PipelineSummary.builder().pipelineId("pipelineId").pipelineName("pipelineName").build())
            .build();
    wingsPersistence.save(execution1);

    Service service = Service.builder().appId(appId).name(generateUUID()).uuid(serviceId).build();
    wingsPersistence.save(service);
    // Call

    long before = now - TimeUnit.MINUTES.toMillis(1), after = now + TimeUnit.MINUTES.toMillis(5);
    List<WorkflowExecution> workflowExecutionList = continuousVerificationService.getDeploymentsForService(
        accountId, before, after, userService.getUserByEmail(WingsIntegrationTestConstants.adminUserEmail), serviceId);

    // Verify
    boolean executionFound = false;
    assertThat(workflowExecutionList.size() > 0).isTrue();
    for (WorkflowExecution execution : workflowExecutionList) {
      if (execution.getUuid().equals(workflowExecutionId)) {
        executionFound = true;
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(execution.getPipelineSummary().getPipelineId()).isEqualTo("pipelineId");
        assertThat(execution.getPipelineSummary().getPipelineName()).isEqualTo("pipelineName");
        assertThat(execution.getEnvId()).isEqualTo(envId);
      }
    }
    assertThat(executionFound).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void getAllDeploymentRecordsWFWithoutServiceIds() {
    // Setup
    long now = System.currentTimeMillis();

    WorkflowExecution execution1 =
        WorkflowExecution.builder()
            .appId(appId)
            .envId(envId)
            .uuid(workflowExecutionId)
            .status(ExecutionStatus.SUCCESS)
            .startTs(now)
            .serviceIds(Arrays.asList(serviceId))
            .pipelineSummary(PipelineSummary.builder().pipelineId("pipelineId").pipelineName("pipelineName").build())
            .build();
    wingsPersistence.save(execution1);

    WorkflowExecution execution2 =
        WorkflowExecution.builder()
            .appId(appId)
            .envId(envId)
            .uuid(workflowExecutionId + "2")
            .status(ExecutionStatus.SUCCESS)
            .startTs(now)
            .pipelineSummary(PipelineSummary.builder().pipelineId("pipelineId").pipelineName("pipelineName").build())
            .build();
    wingsPersistence.save(execution2);

    Service service = Service.builder().appId(appId).uuid(serviceId).build();
    wingsPersistence.save(service);
    // Call

    long before = now - TimeUnit.MINUTES.toMillis(1), after = now + TimeUnit.MINUTES.toMillis(5);
    List<WorkflowExecution> workflowExecutionList = continuousVerificationService.getDeploymentsForService(
        accountId, before, after, userService.getUserByEmail(WingsIntegrationTestConstants.adminUserEmail), serviceId);

    // Verify
    assertThat(workflowExecutionList.size() > 0).isTrue();
    assertThat(workflowExecutionList.get(0).getUuid()).isEqualTo(workflowExecutionId);
    assertThat(workflowExecutionList.get(0).getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat("pipelineId").isEqualTo(workflowExecutionList.get(0).getPipelineSummary().getPipelineId());
    assertThat("pipelineName").isEqualTo(workflowExecutionList.get(0).getPipelineSummary().getPipelineName());
    assertThat(workflowExecutionList.get(0).getEnvId()).isEqualTo(envId);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void getAllCVRecordsHarnessAccount() {
    saveExecutions();
    long now = System.currentTimeMillis();

    long before = now - TimeUnit.MINUTES.toMillis(1), after = now + TimeUnit.MINUTES.toMillis(5);

    PageResponse<ContinuousVerificationExecutionMetaData> cvList =
        continuousVerificationService.getAllCVExecutionsForTime(
            accountId, before, after, true, PageRequestBuilder.aPageRequest().build());
    assertThat(cvList.size() >= 2).isTrue();
    // verify if both the accounts we put in are present
    boolean account1Present = false, account2Present = false;
    for (ContinuousVerificationExecutionMetaData cvData : cvList) {
      if (cvData.getAccountId().equals(accountId)) {
        account1Present = true;
      }
      if (cvData.getAccountId().equals(accountId + "123")) {
        account2Present = true;
      }
    }
    assertThat(account1Present && account2Present).isTrue();
    PageResponse<ContinuousVerificationExecutionMetaData> cvLogsList =
        continuousVerificationService.getAllCVExecutionsForTime(
            accountId, before, after, true, PageRequestBuilder.aPageRequest().build());
    assertThat(cvLogsList.size() > 0).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void getAllCVRecordsNonHarnessAccount() {
    saveExecutions();
    long now = System.currentTimeMillis();

    long before = now - TimeUnit.MINUTES.toMillis(1), after = now + TimeUnit.MINUTES.toMillis(5);

    PageResponse<ContinuousVerificationExecutionMetaData> cvList =
        continuousVerificationService.getAllCVExecutionsForTime(
            "badAccount", before, after, true, PageRequestBuilder.aPageRequest().build());
    assertThat(cvList.size() == 0).isTrue();

    PageResponse<ContinuousVerificationExecutionMetaData> cvLogsList =
        continuousVerificationService.getAllCVExecutionsForTime(
            "badAccount", before, after, true, PageRequestBuilder.aPageRequest().build());
    assertThat(cvLogsList.size() == 0).isTrue();
  }
}
