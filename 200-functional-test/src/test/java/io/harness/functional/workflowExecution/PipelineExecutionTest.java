/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.workflowExecution;

import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.event.model.EventConstants.WORKFLOW_NAME;
import static io.harness.generator.SettingGenerator.Settings.AWS_TEST_CLOUD_PROVIDER;
import static io.harness.rule.OwnerRule.HARSH;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.WRAP_UP;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.sm.StateType.ENV_STATE;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.AccountGenerator;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.ApplicationGenerator.Applications;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.EnvironmentGenerator.Environments;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.ServiceGenerator.Services;
import io.harness.generator.SettingGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.generator.WorkflowGenerator.Workflows;
import io.harness.generator.artifactstream.ArtifactStreamManager;
import io.harness.generator.artifactstream.ArtifactStreamManager.ArtifactStreams;
import io.harness.rule.Owner;
import io.harness.testframework.framework.utils.TestUtils;
import io.harness.testframework.restutils.ArtifactRestUtils;
import io.harness.testframework.restutils.ExecutionRestUtils;
import io.harness.testframework.restutils.PipelineRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineExecutionTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private WorkflowExecutionService workflowExecutionService;
  private static final long TIMEOUT = 1200000; // 20 minutes
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private SettingGenerator settingGenerator;
  @Inject private WorkflowService workflowService;
  @Inject private PipelineService pipelineService;
  @Inject private ArtifactStreamManager artifactStreamManager;
  @Inject private WorkflowUtils workflowUtils;

  private Application application;
  private Service service;
  private Artifact artifact;
  private ArtifactStream artifactStream;
  private Environment environment;
  private InfrastructureDefinition infrastructureDefinition;
  private SettingAttribute awsSettingAttribute;
  final Randomizer.Seed seed = new Randomizer.Seed(0);
  OwnerManager.Owners owners;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST);
    assertThat(application).isNotNull();

    service = serviceGenerator.ensurePredefined(seed, owners, Services.GENERIC_TEST);
    assertThat(service).isNotNull();

    Workflow buildWorkflow = workflowGenerator.ensurePredefined(seed, owners, Workflows.BUILD_JENKINS);
    assertThat(buildWorkflow).isNotNull();

    environment = environmentGenerator.ensurePredefined(seed, owners, Environments.GENERIC_TEST);
    assertThat(environment).isNotNull();

    infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureDefinitions.AWS_SSH_TEST);
    assertThat(infrastructureDefinition).isNotNull();

    awsSettingAttribute = settingGenerator.ensurePredefined(seed, owners, AWS_TEST_CLOUD_PROVIDER);

    artifactStream = artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.AWS_AMI);
    assertThat(artifactStream).isNotNull();

    artifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, application.getUuid(), artifactStream.getUuid(), 0);
  }

  private Workflow constructCanaryOrchestrationWorkflow() {
    List<PhaseStep> phaseSteps = Lists.newArrayList();
    phaseSteps.add(aPhaseStep(PhaseStepType.SELECT_NODE, PhaseStepType.SELECT_NODE.name()).build());
    phaseSteps.add(aPhaseStep(PhaseStepType.DISABLE_SERVICE, PhaseStepType.DISABLE_SERVICE.name()).build());
    phaseSteps.add(aPhaseStep(PhaseStepType.DEPLOY_SERVICE, PhaseStepType.DEPLOY_SERVICE.name()).build());
    phaseSteps.add(aPhaseStep(PhaseStepType.ENABLE_SERVICE, PhaseStepType.ENABLE_SERVICE.name()).build());
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, PhaseStepType.VERIFY_SERVICE.name()).build());
    phaseSteps.add(aPhaseStep(WRAP_UP, "Wrap Up").build());

    Workflow workflow = aWorkflow()
                            .name(WORKFLOW_NAME + System.currentTimeMillis())
                            .appId(application.getUuid())
                            .envId(environment.getUuid())
                            .serviceId(service.getUuid())
                            .infraDefinitionId(infrastructureDefinition.getUuid())
                            .workflowType(ORCHESTRATION)
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().build())
                            .build();

    Workflow savedWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, AccountGenerator.ACCOUNT_ID, application.getUuid(), workflow);
    assertThat(savedWorkflow).isNotNull();
    assertThat(savedWorkflow.getUuid()).isNotEmpty();
    assertThat(savedWorkflow.getWorkflowType()).isEqualTo(ORCHESTRATION);

    WorkflowPhase workflowPhase = workflowService.createWorkflowPhase(application.getUuid(), savedWorkflow.getUuid(),
        aWorkflowPhase()
            .phaseSteps(phaseSteps)
            .serviceId(service.getUuid())
            .infraDefinitionId(infrastructureDefinition.getUuid())
            .build());

    workflowService.updateWorkflowPhase(application.getUuid(), savedWorkflow.getUuid(), workflowPhase);

    return savedWorkflow;
  }

  @Test
  @Owner(developers = HARSH, intermittent = true)
  @Category(FunctionalTests.class)
  @Ignore("TODO: It is failing due to env issue, will change logic")
  public void shouldExecutePipeline() {
    Workflow workflow = constructCanaryOrchestrationWorkflow();
    Workflow workflow1 = constructCanaryOrchestrationWorkflow();
    Map<String, String> workflowVariables = new HashMap<>();
    List<PipelineStage> pipelineStages =
        asList(getPipelineStage(environment.getUuid(), workflow.getUuid(), workflowVariables, "Dev stage1"),
            getPipelineStage(environment.getUuid(), workflow1.getUuid(), workflowVariables, "Dev stage2"));

    Pipeline pipeline = Pipeline.builder()
                            .appId(application.getUuid())
                            .name("pipeline1" + System.currentTimeMillis())
                            .description("Sample Pipeline")
                            .pipelineStages(pipelineStages)
                            .accountId(application.getAccountId())
                            .build();

    pipeline = PipelineRestUtils.createPipeline(application.getAppId(), pipeline, getAccount().getUuid(), bearerToken);
    assertThat(pipeline).isNotNull();
    assertThat(pipeline.getUuid()).isNotNull();

    ExecutionArgs executionArgs = setExecutionArgs(pipeline, asList(artifact), null);
    executionArgs.setOrchestrationId(pipeline.getUuid());
    Map<String, Object> pipelineExecution = ExecutionRestUtils.runPipeline(
        bearerToken, application.getAppId(), environment.getUuid(), pipeline.getUuid(), executionArgs);

    assertThat(pipelineExecution).isNotNull();

    // Execute non templatize pipeline
    WorkflowExecution workflowExecution =
        workflowExecutionService.triggerEnvExecution(pipeline.getAppId(), environment.getUuid(), executionArgs, null);
    workflowUtils.checkForWorkflowSuccess(workflowExecution);

    // Templatize workflow
    templatizeWorkflow(workflow);
    WorkflowRestUtils.updateWorkflow(bearerToken, AccountGenerator.ACCOUNT_ID, application.getUuid(), workflow);

    // Templatize pipeline
    templatizePipeline(pipeline, workflow, workflow1);
    PipelineRestUtils.updatePipeline(application.getAppId(), pipeline, bearerToken);

    // Execute templatize pipeline
    executePipelineAndCheckStatus(pipeline);

    PipelineRestUtils.deletePipeline(application.getAppId(), pipeline.getUuid(), bearerToken);
  }

  private void executePipelineAndCheckStatus(Pipeline pipeline) {
    ExecutionArgs executionArgs = setExecutionArgs(pipeline, asList(artifact),
        ImmutableMap.of("Environment", environment.getUuid(), "Service", service.getUuid(), "ServiceInfra_Ssh",
            infrastructureDefinition.getUuid()));

    executionArgs.setOrchestrationId(pipeline.getUuid());

    WorkflowExecution workflowExecution =
        workflowExecutionService.triggerEnvExecution(pipeline.getAppId(), environment.getUuid(), executionArgs, null);

    assertThat(workflowExecution).isNotNull();
    workflowUtils.checkForWorkflowSuccess(workflowExecution);
  }

  private void templatizeWorkflow(Workflow workflow) {
    workflow.setTemplateExpressions(asList(TemplateExpression.builder()
                                               .fieldName("envId")
                                               .expression("${Environment}")
                                               .metadata(ImmutableMap.of("entityType", "ENVIRONMENT"))
                                               .build(),
        TemplateExpression.builder()
            .fieldName("serviceId")
            .expression("${Service}")
            .metadata(ImmutableMap.of("entityType", "SERVICE"))
            .build(),
        TemplateExpression.builder()
            .fieldName("infraMappingId")
            .expression("${ServiceInfra_Ssh}")
            .metadata(ImmutableMap.of("entityType", "INFRASTRUCTURE_MAPPING"))
            .build()));
  }

  private void templatizePipeline(Pipeline pipeline, Workflow workflow, Workflow workflow1) {
    Map<String, String> workflowVariables = new HashMap<>();

    workflowVariables.put("Environment", "${Environment}");
    workflowVariables.put("Service", "${Service} ");
    workflowVariables.put("ServiceInfra_Ssh", "${ServiceInfra_Ssh}");
    List<PipelineStage> pipelineStages =
        asList(getPipelineStage("${Environment}", workflow.getUuid(), workflowVariables, "Dev stage1"),
            getPipelineStage(environment.getUuid(), workflow1.getUuid(), null, "Dev stage2"));

    pipeline.setPipelineStages(pipelineStages);
  }

  private PipelineStage getPipelineStage(
      String envId, String workflowId, Map<String, String> workflowVariables, String name) {
    return PipelineStage.builder()
        .name(TestUtils.generateRandomUUID())
        .pipelineStageElements(asList(PipelineStageElement.builder()
                                          .uuid(TestUtils.generateRandomUUID())
                                          .name(name)
                                          .type(ENV_STATE.name())
                                          .properties(ImmutableMap.of("envId", envId, "workflowId", workflowId))
                                          .workflowVariables(workflowVariables)
                                          .build()))
        .build();
  }

  private ExecutionArgs setExecutionArgs(
      Pipeline pipeline, List<Artifact> artifacts, ImmutableMap<String, String> workflowFlowVariables) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.PIPELINE);
    executionArgs.setPipelineId(pipeline.getUuid());
    executionArgs.setArtifacts(artifacts);
    executionArgs.setNotifyTriggeredUserOnly(false);

    executionArgs.setExecutionCredential(null);
    executionArgs.setExcludeHostsWithSameArtifact(false);
    executionArgs.setWorkflowVariables(workflowFlowVariables);
    return executionArgs;
  }
}
