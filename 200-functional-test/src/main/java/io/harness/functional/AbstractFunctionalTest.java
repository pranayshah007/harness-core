/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional;

import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureFlag;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.WorkflowType;
import io.harness.ff.FeatureFlagService;
import io.harness.multiline.MultilineStringMixin;
import io.harness.rest.RestResponse;
import io.harness.rule.FunctionalTestRule;
import io.harness.rule.LifecycleRule;
import io.harness.testframework.framework.CommandLibraryServiceExecutor;
import io.harness.testframework.framework.DelegateExecutor;
import io.harness.testframework.framework.Setup;
import io.harness.testframework.framework.utils.FileUtils;
import io.harness.testframework.graphql.GraphQLTestMixin;
import io.harness.testframework.restutils.ArtifactStreamRestUtils;
import io.harness.testframework.restutils.PipelineRestUtils;
import io.harness.testframework.restutils.UserRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.api.DeploymentType;
import software.wings.api.PhaseStepExecutionData;
import software.wings.beans.Account;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityKeys;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.ExecutionCredential;
import software.wings.beans.ExecutionCredential.ExecutionType;
import software.wings.beans.Log;
import software.wings.beans.SSHExecutionCredential;
import software.wings.beans.User;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceKeys;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.DataLoaderRegistryHelper;
import software.wings.persistence.artifact.Artifact;
import software.wings.security.UserPermissionInfo;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.AnalysisContext.AnalysisContextKeys;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.instance.ServerlessInstanceService;
import software.wings.sm.PhaseStepExecutionSummary;
import software.wings.sm.StateExecutionInstance;

import com.google.inject.Inject;
import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.GraphQLContext;
import io.restassured.RestAssured;
import io.restassured.mapper.ObjectMapperType;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.core.GenericType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.dataloader.DataLoaderRegistry;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public abstract class AbstractFunctionalTest extends CategoryTest implements GraphQLTestMixin, MultilineStringMixin {
  protected static final String ADMIN_USER = "admin@harness.io";

  protected static String bearerToken;
  protected static User adminUser;
  @Rule public LifecycleRule lifecycleRule = new LifecycleRule();
  @Rule public FunctionalTestRule rule = new FunctionalTestRule(lifecycleRule.getClosingFactory());

  @Inject DataLoaderRegistryHelper dataLoaderRegistryHelper;
  @Inject AuthHandler authHandler;
  @Inject private WingsPersistence wingsPersistence;
  @Inject CommandLibraryServiceExecutor commandLibraryServiceExecutor;
  @Inject FeatureFlagService featureFlagService;
  @Inject private InstanceService instanceService;
  @Inject InfrastructureMappingService infrastructureMappingService;
  @Inject ServerlessInstanceService serverlessInstanceService;
  @Inject InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject LogService logService;

  @Override
  public DataLoaderRegistry getDataLoaderRegistry() {
    return dataLoaderRegistryHelper.getDataLoaderRegistry();
  }

  @Override
  public GraphQL getGraphQL() {
    return rule.getGraphQL();
  }

  @BeforeClass
  public static void setup() {
    Setup.portal();
    RestAssured.useRelaxedHTTPSValidation();
  }

  @Inject private DelegateExecutor delegateExecutor;
  @Inject private AccountSetupService accountSetupService;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private UserService userService;

  @Getter static Account account;

  @Before
  public void testSetup() throws IOException, InterruptedException {
    account = accountSetupService.ensureAccount();
    adminUser = Setup.loginUser(ADMIN_USER, "admin");
    bearerToken = adminUser.getToken();
    delegateExecutor.ensureDelegate(account, bearerToken, AbstractFunctionalTest.class);
    if (needCommandLibraryService()) {
      commandLibraryServiceExecutor.ensureCommandLibraryService(AbstractFunctionalTest.class);
    }
    log.info("Basic setup completed.");
  }

  @AfterClass
  public static void cleanup() {
    FileUtils.deleteModifiedConfig(AbstractFunctionalTest.class);
    log.info("All tests exit");
  }

  public void resetCache(String accountId) {
    RestResponse<Void> restResponse =
        Setup.portal()
            .auth()
            .oauth2(bearerToken)
            .queryParam("accountId", accountId)
            //            .body(null, ObjectMapperType.GSON)
            .put("/users/reset-cache")
            .as(new GenericType<RestResponse<Void>>() {}.getType(), ObjectMapperType.GSON);
    log.info(restResponse.toString());
  }

  public static Void updateApiKey(String accountId, String bearerToken) {
    RestResponse<Void> restResponse =
        Setup.portal()
            .auth()
            .oauth2(bearerToken)
            .queryParam("accountId", accountId)
            //            .body(null, ObjectMapperType.GSON)
            .put("/users/reset-cache")
            .as(new GenericType<RestResponse<Void>>() {}.getType(), ObjectMapperType.GSON);
    return restResponse.getResource();
  }

  public WorkflowExecution runWorkflow(
      String bearerToken, String appId, String envId, String orchestrationId, List<Artifact> artifactList) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
    executionArgs.setExecutionCredential(
        SSHExecutionCredential.Builder.aSSHExecutionCredential().withExecutionType(ExecutionType.SSH).build());
    executionArgs.setOrchestrationId(orchestrationId);
    executionArgs.setArtifacts(artifactList);

    return getWorkflowExecution(bearerToken, appId, envId, executionArgs);
  }

  public AnalysisContext runWorkflowWithVerification(
      String bearerToken, String appId, String envId, String orchestrationId, List<Artifact> artifactList) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
    executionArgs.setExecutionCredential(
        SSHExecutionCredential.Builder.aSSHExecutionCredential().withExecutionType(ExecutionType.SSH).build());
    executionArgs.setOrchestrationId(orchestrationId);
    executionArgs.setArtifacts(artifactList);

    return getWorkflowExecutionWithVerification(bearerToken, appId, envId, executionArgs);
  }

  public WorkflowExecution getWorkflowExecution(
      String bearerToken, String appId, String envId, ExecutionArgs executionArgs) {
    WorkflowExecution original = WorkflowRestUtils.startWorkflow(bearerToken, appId, envId, executionArgs);

    Awaitility.await().atMost(15, TimeUnit.MINUTES).pollInterval(5, TimeUnit.SECONDS).until(() -> {
      final WorkflowExecution workflowExecution =
          workflowExecutionService.getWorkflowExecution(appId, original.getUuid());
      return workflowExecution != null && ExecutionStatus.isFinalStatus(workflowExecution.getStatus());
    });

    return workflowExecutionService.getWorkflowExecution(appId, original.getUuid());
  }

  protected void logStateExecutionInstanceErrors(WorkflowExecution workflowExecution) {
    if (workflowExecution != null && workflowExecution.getStatus() != ExecutionStatus.FAILED) {
      log.info("Workflow execution didn't fail, skipping this step");
      return;
    }

    List<StateExecutionInstance> stateExecutionInstances =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.accountId, workflowExecution.getAccountId())
            .filter(StateExecutionInstanceKeys.executionUuid, workflowExecution.getUuid())
            .filter(StateExecutionInstanceKeys.status, ExecutionStatus.FAILED)
            .asList();

    if (isEmpty(stateExecutionInstances)) {
      log.info("No FAILED state execution instances found for workflow {}", workflowExecution.getUuid());
      return;
    }

    stateExecutionInstances.stream()
        .map(stateExecutionInstance -> stateExecutionInstance.getStateExecutionMap().values())
        .flatMap(Collection::stream)
        .filter(stateExecutionData
            -> stateExecutionData.getStatus() == ExecutionStatus.FAILED
                || stateExecutionData.getStatus() == ExecutionStatus.ERROR)
        .forEach(stateExecutionData -> {
          log.info("Analyzing failed state: {}", stateExecutionData.getStateName());
          if (isNotEmpty(stateExecutionData.getErrorMsg())) {
            log.info(
                "State: {} failed with error: {}", stateExecutionData.getStateName(), stateExecutionData.getErrorMsg());
          } else {
            log.info("No error message found for state: {}, checking phase execution summary...",
                stateExecutionData.getStateName());
          }
          if (stateExecutionData instanceof PhaseStepExecutionData) {
            PhaseStepExecutionData phaseStepExecutionData = (PhaseStepExecutionData) stateExecutionData;
            PhaseStepExecutionSummary phaseStepExecutionSummary = phaseStepExecutionData.getPhaseStepExecutionSummary();
            if (phaseStepExecutionSummary != null) {
              phaseStepExecutionSummary.getStepExecutionSummaryList()
                  .stream()
                  .filter(stepExecutionSummary
                      -> stepExecutionSummary.getStatus() == ExecutionStatus.ERROR
                          || stepExecutionSummary.getStatus() == ExecutionStatus.FAILED)
                  .forEach(stepExecutionSummary
                      -> log.info("Phase step execution failed at state: {} and step name: {} with message: {}",
                          stateExecutionData.getStateName(), stepExecutionSummary.getStepName(),
                          stepExecutionSummary.getMessage()));
            }
          } else {
            log.info(
                "No phase step execution summary found for state: {}. ¯\\_(ツ)_/¯", stateExecutionData.getStateName());
          }

          log.info("Analysis completed for failed state: {}", stateExecutionData.getStateName());
        });
  }

  private AnalysisContext getWorkflowExecutionWithVerification(
      String bearerToken, String appId, String envId, ExecutionArgs executionArgs) {
    WorkflowExecution original = WorkflowRestUtils.startWorkflow(bearerToken, appId, envId, executionArgs);

    final AnalysisContext[] analysisContext = new AnalysisContext[1];
    Awaitility.await().atMost(15, TimeUnit.MINUTES).pollInterval(5, TimeUnit.SECONDS).until(() -> {
      analysisContext[0] = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                               .filter(AnalysisContextKeys.workflowExecutionId, original.getUuid())
                               .get();
      return analysisContext[0] != null;
    });

    return analysisContext[0];
  }

  public WorkflowExecution runWorkflow(String bearerToken, String appId, String envId, ExecutionArgs executionArgs) {
    return getWorkflowExecution(bearerToken, appId, envId, executionArgs);
  }

  public WorkflowExecution runPipeline(String bearerToken, String appId, String envId, ExecutionArgs executionArgs) {
    WorkflowExecution original = PipelineRestUtils.startPipeline(bearerToken, appId, envId, executionArgs);

    Awaitility.await().atMost(120, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
      final WorkflowExecution workflowExecution =
          workflowExecutionService.getWorkflowExecution(appId, original.getUuid());
      return workflowExecution != null && ExecutionStatus.isFinalStatus(workflowExecution.getStatus());
    });

    return workflowExecutionService.getWorkflowExecution(appId, original.getUuid());
  }

  @Override
  public ExecutionInput getExecutionInput(String query, String accountId) {
    User user = User.Builder.anUser().uuid("user1Id").build();
    UserGroup userGroup = authHandler.buildDefaultAdminUserGroup(accountId, user);
    UserPermissionInfo userPermissionInfo =
        authHandler.evaluateUserPermissionInfo(accountId, Arrays.asList(userGroup), user);
    return ExecutionInput.newExecutionInput()
        .query(query)
        .dataLoaderRegistry(getDataLoaderRegistry())
        .context(GraphQLContext.newContext().of("accountId", accountId, "permissions", userPermissionInfo))
        .build();
  }

  protected boolean needCommandLibraryService() {
    return false;
  }

  protected void logFeatureFlagsEnabled(String accountId) {
    for (FeatureName featureName : FeatureName.values()) {
      if (featureFlagService.isEnabled(featureName, accountId)) {
        log.info("[ENABLED_FEATURE_FLAG]: {}", featureName);
      }
    }
  }

  protected void assertExecution(Workflow savedWorkflow, String appId, String envId) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(savedWorkflow.getWorkflowType());

    String artifactId = ArtifactStreamRestUtils.getArtifactStreamId(
        bearerToken, appId, savedWorkflow.getEnvId(), savedWorkflow.getServiceId());
    Artifact artifact = new Artifact();
    artifact.setUuid(artifactId);

    executionArgs.setArtifacts(Arrays.asList(artifact));
    executionArgs.setOrchestrationId(savedWorkflow.getUuid());
    executionArgs.setExecutionCredential(SSHExecutionCredential.Builder.aSSHExecutionCredential()
                                             .withExecutionType(ExecutionCredential.ExecutionType.SSH)
                                             .build());

    log.info("Invoking workflow execution");

    WorkflowExecution workflowExecution = runWorkflow(bearerToken, appId, envId, executionArgs);
    logStateExecutionInstanceErrors(workflowExecution);
    assertThat(workflowExecution).isNotNull();
    log.info("Waiting for execution to finish");
    assertInstanceCount(workflowExecution.getStatus(), appId, workflowExecution.getInfraMappingIds().get(0),
        workflowExecution.getInfraDefinitionIds().get(0));

    log.info("ECs Execution status: " + workflowExecution.getStatus());
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  protected void assertInstanceCount(
      ExecutionStatus workflowExecutionStatus, String appId, String infraMappingId, String infraDefinitionId) {
    if (workflowExecutionStatus != ExecutionStatus.SUCCESS) {
      return;
    }
    DeploymentType deploymentType = infrastructureDefinitionService.get(appId, infraDefinitionId).getDeploymentType();
    assertThat(getActiveInstancesConditional(appId, infraMappingId, deploymentType)).isGreaterThanOrEqualTo(1);
  }

  private long getActiveInstancesConditional(String appId, String infraMappingId, DeploymentType deploymentType) {
    Awaitility.await().atMost(5, TimeUnit.MINUTES).pollInterval(5, TimeUnit.SECONDS).until(() -> {
      if (deploymentType == DeploymentType.AWS_LAMBDA) {
        return serverlessInstanceService.list(infraMappingId, appId).size() >= 1;
      } else {
        return instanceService.getInstanceCount(appId, infraMappingId) >= 1;
      }
    });
    if (deploymentType == DeploymentType.AWS_LAMBDA) {
      return serverlessInstanceService.list(infraMappingId, appId).size();
    } else {
      return instanceService.getInstanceCount(appId, infraMappingId);
    }
  }

  protected List<Instance> getActiveInstancesConditional(String appId, String serviceId, String infraMappingId) {
    Awaitility.await()
        .atMost(3, TimeUnit.MINUTES)
        .pollInterval(5, TimeUnit.SECONDS)
        .until(() -> getActiveInstances(appId, serviceId, infraMappingId).size() > 0);
    return getActiveInstances(appId, serviceId, infraMappingId);
  }

  private List<Instance> getActiveInstances(String appId, String serviceId, String infraMappingId) {
    return wingsPersistence.createQuery(Instance.class)
        .filter(InstanceKeys.appId, appId)
        .filter(InstanceKeys.infraMappingId, infraMappingId)
        .filter(InstanceKeys.serviceId, serviceId)
        .filter(InstanceKeys.isDeleted, Boolean.FALSE)
        .asList();
  }

  public void getFailedWorkflowExecutionLogs(WorkflowExecution workflowExecution) {
    if (workflowExecution != null && workflowExecution.getStatus() != ExecutionStatus.FAILED) {
      log.info("Workflow execution didn't fail, skipping fetching logs");
      return;
    }
    List<Activity> activities = wingsPersistence.createQuery(Activity.class)
                                    .filter(ActivityKeys.accountId, workflowExecution.getAccountId())
                                    .filter(ActivityKeys.workflowExecutionId, workflowExecution.getUuid())
                                    .asList();

    for (Activity activity : activities) {
      for (CommandUnit commandUnit : activity.getCommandUnits()) {
        log.info("Logs For {}", commandUnit.getName());
        PageRequest<Log> request = new PageRequest<>();
        request.setLimit(UNLIMITED);
        request.addFilter("activityId", EQ, activity.getUuid());
        request.addFilter("commandUnitName", EQ, commandUnit.getName());
        PageResponse<Log> logPageResponse = logService.list(workflowExecution.getAppId(), request);
        log.info("for activityId : {}", activity.getUuid());
        logPageResponse.forEach(response -> log.info(response.getLogLine()));
      }
    }
  }

  protected void logManagerFeatureFlags(String accountId) {
    Collection<FeatureFlag> managerFeatureFlags = UserRestUtils.listFeatureFlags(accountId, bearerToken);
    StringBuilder featureFlagListAsString = new StringBuilder();
    for (FeatureFlag featureFlag : managerFeatureFlags) {
      featureFlagListAsString.append(String.format("%s: %b\n", featureFlag.getName(), featureFlag.isEnabled()));
    }

    log.info("Feature flags on manager:\n{}", featureFlagListAsString.toString());
  }

  protected Workflow createAndAssertWorkflow(Workflow workflow, String accountId, String appId) {
    Workflow savedWorkflow = WorkflowRestUtils.createWorkflow(bearerToken, accountId, appId, workflow);
    assertThat(savedWorkflow).isNotNull();
    assertThat(savedWorkflow.getUuid()).isNotEmpty();
    assertThat(savedWorkflow.getWorkflowType()).isEqualTo(ORCHESTRATION);
    return savedWorkflow;
  }
}
