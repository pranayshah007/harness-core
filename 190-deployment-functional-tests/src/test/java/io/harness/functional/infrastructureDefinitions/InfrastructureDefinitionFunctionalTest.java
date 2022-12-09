/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.infrastructureDefinitions;

import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.WorkflowType;
import io.harness.category.element.CDFunctionalTests;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.PipelineGenerator;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.ServiceGenerator.Services;
import io.harness.generator.WorkflowGenerator;
import io.harness.rule.Owner;
import io.harness.testframework.framework.utils.PipelineUtils;
import io.harness.testframework.restutils.ArtifactRestUtils;
import io.harness.testframework.restutils.InfrastructureDefinitionRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureType;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.infra.AwsEcsInfrastructure;
import software.wings.infra.AwsInstanceInfrastructure;
import software.wings.infra.AzureInstanceInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class InfrastructureDefinitionFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;

  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private PipelineGenerator pipelineGenerator;
  @Inject private WorkflowUtils workflowUtils;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;

  @Inject private ServiceResourceService serviceResourceService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private PipelineService pipelineService;

  private final Seed seed = new Seed(0);
  private Owners owners;

  private Application application;
  private Environment environment;
  private Service service;
  private InfrastructureDefinition infrastructureDefinition;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST);
    environment = environmentGenerator.ensurePredefined(seed, owners, EnvironmentGenerator.Environments.GENERIC_TEST);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(FunctionalTests.class)
  @Ignore("Enable once feature flag is enabled")
  public void shouldCreateAndRunAwsInstanceWorkflow() {
    service = serviceGenerator.ensurePredefined(seed, owners, Services.GENERIC_TEST);
    resetCache(service.getAccountId());

    final String appId = service.getAppId();
    infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureDefinitions.AWS_SSH_TEST);

    checkScopedService(infrastructureDefinition.getDeploymentType(), service);
    checkListInfraDefinitionByService(service, infrastructureDefinition);
    checkListHosts(infrastructureDefinition);

    AwsInstanceInfrastructure awsInfra = (AwsInstanceInfrastructure) infrastructureDefinition.getInfrastructure();
    List<String> classicLbs = InfrastructureDefinitionRestUtils.listAwsClassicLoadBalancers(
        bearerToken, appId, awsInfra.getCloudProviderId(), awsInfra.getRegion());
    assertThat(classicLbs).isNotEmpty();

    resetCache(service.getAccountId());

    Workflow workflow = workflowUtils.createCanarySshWorkflow("ec2-ssh-", service, infrastructureDefinition);
    workflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), workflow);

    Artifact artifact = getArtifact(service, service.getAppId());
    executeWorkflow(workflow, service, Arrays.asList(artifact), ImmutableMap.<String, String>builder().build());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(CDFunctionalTests.class)
  @Ignore("Enable once feature flag is enabled")
  public void shouldCreateAndRunAwsEcsEc2Workflow() {
    service = serviceGenerator.ensureEcsTest(seed, owners, "ecs-service");

    final String appId = service.getAppId();

    infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureType.AWS_ECS, bearerToken);

    checkScopedService(infrastructureDefinition.getDeploymentType(), service);
    checkListInfraDefinitionByService(service, infrastructureDefinition);

    AwsEcsInfrastructure awsEcsInfrastructure = (AwsEcsInfrastructure) infrastructureDefinition.getInfrastructure();
    List<String> elbTargetGroups = InfrastructureDefinitionRestUtils.listAwsAlbTargetGroups(
        bearerToken, appId, awsEcsInfrastructure.getCloudProviderId(), awsEcsInfrastructure.getRegion());
    assertThat(elbTargetGroups).isNotEmpty();

    Workflow workflow = workflowUtils.getEcsEc2TypeCanaryWorkflow("ecs-ec2-", service, infrastructureDefinition);
    workflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), workflow);

    Artifact artifact = getArtifact(service, service.getAppId());
    executeWorkflow(workflow, service, Arrays.asList(artifact), ImmutableMap.<String, String>builder().build());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(CDFunctionalTests.class)
  @Ignore("Enable once feature flag is enabled")
  public void shouldCreateAndRunAwsLambdaWorkflow() {
    service = serviceGenerator.ensureAwsLambdaGenericTest(seed, owners, "lambda-service");
    infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureType.AWS_LAMBDA, bearerToken);

    checkScopedService(infrastructureDefinition.getDeploymentType(), service);
    checkListInfraDefinitionByService(service, infrastructureDefinition);

    Workflow workflow = workflowUtils.createBasicWorkflow("aws-lamda-", service, infrastructureDefinition);
    workflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), workflow);

    Artifact artifact = getArtifact(service, service.getAppId());
    executeWorkflow(workflow, service, Arrays.asList(artifact), ImmutableMap.<String, String>builder().build());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(FunctionalTests.class)
  @Ignore("Enable once feature flag is enabled")
  public void shouldCreateAndRunGcpK8sWorkflow() {
    service = serviceGenerator.ensureK8sTest(seed, owners, "k8s-service");
    infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureType.GCP_KUBERNETES_ENGINE, bearerToken);

    checkScopedService(infrastructureDefinition.getDeploymentType(), service);
    checkListInfraDefinitionByService(service, infrastructureDefinition);

    Workflow workflow = workflowUtils.getCanaryK8sWorkflow("gcp-k8s-", service, infrastructureDefinition);
    workflow = workflowGenerator.ensureWorkflow(seed, owners, workflow);

    Artifact artifact = getArtifact(service, service.getAppId());
    executeWorkflow(workflow, service, Arrays.asList(artifact), ImmutableMap.<String, String>builder().build());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(FunctionalTests.class)
  @Ignore("Enable once feature flag is enabled")
  public void shouldCreateAndRunGcpK8sTemplatizedWorkflow() {
    service = serviceGenerator.ensureK8sTest(seed, owners, "k8s-service");
    infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureType.GCP_KUBERNETES_ENGINE, bearerToken);

    checkScopedService(infrastructureDefinition.getDeploymentType(), service);
    checkListInfraDefinitionByService(service, infrastructureDefinition);

    Workflow workflow =
        workflowUtils.getRollingK8sTemplatizedWorkflow("gcp-k8s-templatized-", service, infrastructureDefinition);
    ImmutableMap<String, String> workflowVariables =
        ImmutableMap.<String, String>builder()
            .put("Environment", infrastructureDefinition.getEnvId())
            .put("InfraDefinition_Kubernetes", infrastructureDefinition.getUuid())
            .build();

    workflow = workflowGenerator.ensureWorkflow(seed, owners, workflow);

    Artifact artifact = getArtifact(service, service.getAppId());
    executeWorkflow(workflow, service, Arrays.asList(artifact), workflowVariables);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(FunctionalTests.class)
  @Ignore("Enable once feature flag is enabled")
  public void shouldReuseInfraMapping() {
    service = serviceGenerator.ensurePredefined(seed, owners, Services.GENERIC_TEST);
    infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureType.AWS_INSTANCE, bearerToken);
    Workflow workflow_1 = workflowGenerator.ensureWorkflow(
        seed, owners, workflowUtils.createCanarySshWorkflow("aws-ssh-1-", service, infrastructureDefinition));
    Workflow workflow_2 = workflowGenerator.ensureWorkflow(
        seed, owners, workflowUtils.createCanarySshWorkflow("aws-ssh-2-", service, infrastructureDefinition));
    Workflow workflow_3 = workflowGenerator.ensureWorkflow(
        seed, owners, workflowUtils.createCanarySshWorkflow("aws-ssh-3-", service, infrastructureDefinition));

    Artifact artifact = getArtifact(service, service.getAppId());

    Map<String, String> workflowVariables = ImmutableMap.<String, String>builder().build();

    List<PipelineStage> pipelineStages =
        Arrays.asList(PipelineUtils.createPipelineStageWithWorkflow("stage-1", workflow_1, workflowVariables, false),
            PipelineUtils.createPipelineStageWithWorkflow("stage-2", workflow_2, workflowVariables, true),
            PipelineUtils.createPipelineStageWithWorkflow("stage-3", workflow_3, workflowVariables, true));

    final String appId = service.getAppId();
    final String accountId = service.getAccountId();
    Pipeline pipelineWithParallelWorkflows = Pipeline.builder()
                                                 .name("should-reuse-" + System.currentTimeMillis())
                                                 .pipelineStages(pipelineStages)
                                                 .appId(appId)
                                                 .accountId(accountId)
                                                 .build();
    pipelineWithParallelWorkflows = pipelineGenerator.ensurePipeline(seed, owners, pipelineWithParallelWorkflows);

    executePipeline(pipelineWithParallelWorkflows, Arrays.asList(artifact), infrastructureDefinition.getEnvId());

    List<InfrastructureMapping> infrastructureMappings =
        infrastructureMappingService.getInfraMappingLinkedToInfraDefinition(
            infrastructureDefinition.getAppId(), infrastructureDefinition.getUuid());

    assertThat(infrastructureMappings).hasSize(1);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(FunctionalTests.class)
  @Ignore("Enable once feature flag is enabled")
  public void shouldRunAzureWinRmCanaryWorkflow() {
    service = serviceGenerator.ensurePredefined(seed, owners, Services.WINDOWS_TEST);

    final String appId = service.getAppId();
    final String accountId = service.getAccountId();

    resetCache(service.getAccountId());
    infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureType.AZURE_SSH, bearerToken);

    checkScopedService(infrastructureDefinition.getDeploymentType(), service);
    checkListInfraDefinitionByService(service, infrastructureDefinition);
    checkListHosts(infrastructureDefinition);

    AzureInstanceInfrastructure azureInfrastructure =
        (AzureInstanceInfrastructure) infrastructureDefinition.getInfrastructure();
    Map<String, String> azureSubscriptions = InfrastructureDefinitionRestUtils.listAzureSubscriptions(
        bearerToken, accountId, azureInfrastructure.getCloudProviderId());
    Set<String> azureTags = InfrastructureDefinitionRestUtils.listAzureTags(
        bearerToken, appId, azureInfrastructure.getSubscriptionId(), azureInfrastructure.getCloudProviderId());
    Set<String> azureResources = InfrastructureDefinitionRestUtils.listAzureResources(
        bearerToken, appId, azureInfrastructure.getSubscriptionId(), azureInfrastructure.getCloudProviderId());
    assertThat(azureSubscriptions).isNotEmpty();
    assertThat(azureTags).isNotEmpty();
    assertThat(azureResources).isNotEmpty();

    resetCache(service.getAccountId());

    Workflow workflow = workflowUtils.createCanarySshWorkflow("azure-winrm-", service, infrastructureDefinition);
    workflow = workflowGenerator.ensureWorkflow(seed, owners, workflow);

    Artifact artifact = getArtifact(service, service.getAppId());
    executeWorkflow(workflow, service, Arrays.asList(artifact), ImmutableMap.<String, String>builder().build());
  }

  private void executePipeline(final Pipeline pipeline, List<Artifact> artifacts, String someEnvId) {
    resetCache(this.service.getAccountId());

    ExecutionArgs executionArgs =
        prepareExecutionArgs(pipeline, artifacts, ImmutableMap.<String, String>builder().build());

    // Not using PipelineUtils because of json mapping Exception due to
    // WorkflowExecution#serviceExecutionSummaries, refactor
    // once proper json annotations are added for this
    WorkflowExecution workflowExecution =
        workflowExecutionService.triggerEnvExecution(pipeline.getAppId(), someEnvId, executionArgs, null);
    workflowUtils.checkForWorkflowSuccess(workflowExecution);
  }

  private void executeWorkflow(final Workflow workflow, final Service service, final List<Artifact> artifacts,
      ImmutableMap<String, String> workflowVariables) {
    final String appId = service.getAppId();
    final String envId = workflow.getEnvId();

    resetCache(this.service.getAccountId());
    ExecutionArgs executionArgs = prepareExecutionArgs(workflow, artifacts, workflowVariables);
    WorkflowExecution workflowExecution = WorkflowRestUtils.startWorkflow(bearerToken, appId, envId, executionArgs);
    WorkflowExecution finalWorkflowExecution = workflowUtils.checkForWorkflowSuccess(workflowExecution);
    assertInstanceCount(finalWorkflowExecution.getStatus(), appId, finalWorkflowExecution.getInfraMappingIds().get(0),
        finalWorkflowExecution.getInfraDefinitionIds().get(0));
  }

  private Artifact getArtifact(Service service, String appId) {
    return ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, appId, service.getArtifactStreamIds().get(0), 0);
  }

  private void checkScopedService(DeploymentType deploymentType, Service knownService) {
    final String appId = knownService.getAppId();

    List<String> scopedServices = serviceResourceService.listByDeploymentType(appId, deploymentType.toString(), null)
                                      .stream()
                                      .map(Service::getUuid)
                                      .collect(Collectors.toList());

    assertThat(scopedServices).contains(knownService.getUuid());
  }

  private void checkListInfraDefinitionByService(Service service, InfrastructureDefinition expected) {
    List<String> definitions = InfrastructureDefinitionRestUtils.listInfraDefinitionByService(
        bearerToken, service.getAccountId(), service.getAppId(), service.getUuid(), expected.getEnvId());
    assertThat(definitions).contains(expected.getUuid());
  }

  private void checkListHosts(InfrastructureDefinition infrastructureDefinition) {
    List<String> hosts = InfrastructureDefinitionRestUtils.listHosts(bearerToken, infrastructureDefinition.getAppId(),
        infrastructureDefinition.getEnvId(), infrastructureDefinition.getUuid());
    assertThat(hosts).isNotEmpty();
  }

  private ExecutionArgs prepareExecutionArgs(
      Workflow workflow, List<Artifact> artifacts, ImmutableMap<String, String> workflowFlowVariables) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
    executionArgs.setOrchestrationId(workflow.getUuid());
    executionArgs.setArtifacts(artifacts);
    executionArgs.setWorkflowVariables(workflowFlowVariables);
    return executionArgs;
  }

  private ExecutionArgs prepareExecutionArgs(
      Pipeline pipeline, List<Artifact> artifacts, ImmutableMap<String, String> workflowFlowVariables) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.PIPELINE);
    executionArgs.setPipelineId(pipeline.getUuid());
    executionArgs.setArtifacts(artifacts);
    executionArgs.setWorkflowVariables(workflowFlowVariables);
    return executionArgs;
  }

  @After
  public void cleanUp() {}
}
