/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.harnesscli;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.generator.EnvironmentGenerator.Environments.FUNCTIONAL_TEST;
import static io.harness.generator.ServiceGenerator.Services.K8S_V2_TEST;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.CliFunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.rule.Owner;
import io.harness.testframework.framework.Setup;
import io.harness.testframework.framework.utils.PipelineUtils;
import io.harness.testframework.restutils.ArtifactRestUtils;
import io.harness.testframework.restutils.PipelineRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.Service;
import software.wings.beans.TemplateExpression;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.yaml.snakeyaml.Yaml;

@OwnedBy(CDC)
@Slf4j
public class DeployFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private WorkflowUtils workflowUtils;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject HarnesscliHelper harnesscliHelper;
  private final Seed seed = new Seed(0);

  public enum Commands { DEPLOY_WORKFLOW_TEST, DEPLOY_TEMPLATE_WORKFLOW, DEPLOY_USING_CONFIG, DEPLOY_PIPELINE_TEST }

  private Owners owners;
  private String appId;
  private String accountId;
  private String artifactId;
  private Service service;
  private InfrastructureDefinition infrastructureDefinition;
  private List<String> artifactStreamIds;
  private ArtifactStream artifactStream;
  private Environment environment;
  private Artifact artifact;
  private List<Artifact> artifacts;

  @Before
  public void setUp() throws IOException {
    owners = ownerManager.create();

    service = serviceGenerator.ensurePredefined(seed, owners, K8S_V2_TEST);
    appId = service.getAppId();
    accountId = getAccount().getUuid();
    environment = environmentGenerator.ensurePredefined(seed, owners, FUNCTIONAL_TEST);
    artifactStreamIds = service.getArtifactStreamIds();
    artifactStream = artifactStreamService.get(artifactStreamIds.get(0));
    artifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, service.getAppId(), artifactStream.getUuid(), 0);
    artifacts = ArtifactRestUtils.fetchArtifactByArtifactStream(bearerToken, appId, artifactStream.getUuid());
    artifactId = artifact.getUuid();
    // TODO: Uncomment this along with the Removing Ignore when InfraDefs FF is turned ON
    //        infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(seed, owners,
    //        "GCP_KUBERNETES", bearerToken);
    infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureDefinitions.K8S_ROLLING_TEST);
    Runtime.getRuntime().exec("harness login -u admin@harness.io -p admin -d localhost:9090");
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(CliFunctionalTests.class)
  @Ignore("This test is skipping through maven command. Skipping for bazel")
  public void deployWorkflowWithInfraMapping() throws IOException {
    Workflow rollingWorkflow = workflowUtils.createRollingWorkflowInfraDefinition(
        "Test-Rolling-CLI-Deployment", service, infrastructureDefinition);

    String workflowId = createWorkflowAndReturnId(rollingWorkflow);

    String command =
        "harness deploy --application " + appId + " --workflow " + workflowId + " --artifacts " + artifactId;
    assertThat(deployAndCheckStatus(getCommand(Commands.DEPLOY_WORKFLOW_TEST, command), ExecutionStatus.RUNNING.name()))
        .isTrue();

    log.info("Deleting the workflow");
    WorkflowRestUtils.deleteWorkflow(bearerToken, workflowId, appId);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(CliFunctionalTests.class)
  @Ignore("Will be enabled along with InfraDefs Feature Flag")
  public void deployWithWrongApplicationId() throws IOException {
    Workflow rollingWorkflow =
        workflowUtils.createRollingWorkflow("Test-Rolling-CLI-Deployment", service, infrastructureDefinition);
    String workflowId = createWorkflowAndReturnId(rollingWorkflow);

    List<String> deployOutput = harnesscliHelper.getCLICommandError(
        "harness deploy --application wrongAppId --workflow " + workflowId + " --artifacts " + artifactId);
    assertThat(deployOutput.get(0).contains("User not authorized")).isTrue();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(CliFunctionalTests.class)
  @Ignore("Will be enabled along with InfraDefs Feature Flag")
  public void deployWithWrongWorkflowId() throws IOException {
    List<String> deployOutput =
        harnesscliHelper.getCLICommandError("harness deploy --application " + appId + " --workflow"
            + " wrongWorkflowId"
            + " --artifacts " + artifactId);
    assertThat(deployOutput.get(0).contains("User not authorized")).isTrue();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(CliFunctionalTests.class)
  @Ignore("Will be enabled along with InfraDefs Feature Flag")
  public void deployWithWrongPipelineId() throws IOException {
    List<String> deployOutput =
        harnesscliHelper.getCLICommandError("harness deploy --application " + appId + " --pipeline"
            + " wrongPipelineId"
            + " --artifacts " + artifactId);
    assertThat(deployOutput.get(0).contains("User not authorized")).isTrue();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(CliFunctionalTests.class)
  @Ignore("Will be enabled along with InfraDefs Feature Flag")
  public void deployWithWrongArtifactId() throws IOException {
    Workflow rollingWorkflow =
        workflowUtils.createRollingWorkflow("Test-Rolling-CLI-Deployment", service, infrastructureDefinition);
    String workflowId = createWorkflowAndReturnId(rollingWorkflow);

    List<String> deployOutput = harnesscliHelper.getCLICommandError("harness deploy --application " + appId
        + " --workflow " + workflowId + " --artifacts "
        + "wrongArtifactId");
    assertThat(deployOutput.get(0).contains("Invalid request: Invalid artifact")).isTrue();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(CliFunctionalTests.class)
  @Ignore("Will be enabled along with InfraDefs Feature Flag")
  public void deployWithoutParams() throws IOException {
    List<String> deployOutput = harnesscliHelper.getCLICommandError("harness deploy");
    assertThat(
        deployOutput.get(0).contains(
            "Please provide an Application ID. Use harness get applications to see a list of all Application IDs."))
        .isTrue();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(CliFunctionalTests.class)
  @Ignore("Will be enabled along with InfraDefs Feature Flag")
  public void shouldDeployWorkflow() throws IOException {
    Workflow rollingWorkflow =
        workflowUtils.createRollingWorkflow("Test-Rolling-CLI-Deployment", service, infrastructureDefinition);
    String workflowId = createWorkflowAndReturnId(rollingWorkflow);

    String command =
        "harness deploy --application " + appId + " --workflow " + workflowId + " --artifacts " + artifactId;
    assertThat(deployAndCheckStatus(getCommand(Commands.DEPLOY_WORKFLOW_TEST, command), ExecutionStatus.RUNNING.name()))
        .isTrue();

    log.info("Deleting the workflow");
    WorkflowRestUtils.deleteWorkflow(bearerToken, workflowId, appId);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(CliFunctionalTests.class)
  @Ignore("Will be enabled along with InfraDefs Feature Flag")
  public void shouldDeployTemplateWorkflow() throws IOException {
    Workflow rollingWorkflow =
        workflowUtils.createRollingWorkflow("Test-Rolling-Template-CLI-Deployment", service, infrastructureDefinition);
    Workflow testWorkflow = WorkflowRestUtils.createWorkflow(bearerToken, accountId, appId, rollingWorkflow);
    testWorkflow.setTemplateExpressions(getTemplateExpressions());
    WorkflowRestUtils.updateWorkflow(bearerToken, accountId, appId, testWorkflow);
    String workflowId = testWorkflow.getUuid();

    String command =
        "harness deploy --application " + appId + " --workflow " + workflowId + " --artifacts " + artifactId;
    assertThat(
        deployAndCheckStatus(getCommand(Commands.DEPLOY_TEMPLATE_WORKFLOW, command), ExecutionStatus.RUNNING.name()))
        .isTrue();

    // Negative Case, Variables are not provided
    List<String> deployOutput =
        harnesscliHelper.getCLICommandError(command + " --variables service:" + service.getUuid());
    log.info(deployOutput.get(0));
    assertThat(
        deployOutput.get(0).contains("Invalid request: Workflow variable [Infra_Def] is mandatory for execution"))
        .isTrue();

    log.info("Deleting the workflow");
    WorkflowRestUtils.deleteWorkflow(bearerToken, workflowId, appId);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(CliFunctionalTests.class)
  @Ignore("Will be enabled along with InfraDefs Feature Flag")
  public void shouldDeployWorkflowUsingConfigFile() throws IOException {
    Workflow rollingWorkflow =
        workflowUtils.createRollingWorkflow("Test-Rolling-CLI-Deployment", service, infrastructureDefinition);
    String workflowId = createWorkflowAndReturnId(rollingWorkflow);

    Map<String, String> data = new HashMap<>();
    data.put("application", appId);
    data.put("workflow", workflowId);
    data.put("artifacts", artifactId);

    Yaml yaml = new Yaml();
    FileWriter writer = new FileWriter("./deployConfig.yaml");
    yaml.dump(data, writer);

    assertThat(deployAndCheckStatus(getCommand(Commands.DEPLOY_USING_CONFIG, ""), ExecutionStatus.RUNNING.name()))
        .isTrue();

    log.info("Deleting the workflow");
    WorkflowRestUtils.deleteWorkflow(bearerToken, workflowId, appId);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(CliFunctionalTests.class)
  @Ignore("Will be enabled along with InfraDefs Feature Flag")
  public void shouldDeployPipeline() throws IOException {
    Pipeline pipeline = getPipelineWithTwoStages(getPipelineStages(), appId, accountId);
    String pipelineId = pipeline.getUuid();

    String command = "harness deploy --application " + appId + " --pipeline " + pipelineId + " --artifacts "
        + artifacts.get(0).getUuid() + " --artifacts " + artifacts.get(1).getUuid();
    assertThat(deployAndCheckStatus(getCommand(Commands.DEPLOY_PIPELINE_TEST, command), ExecutionStatus.RUNNING.name()))
        .isTrue();

    // Negative Case, Variables are not provided
    assertThat(deployAndCheckStatus(getCommand(Commands.DEPLOY_WORKFLOW_TEST, command + " --variables wrongVar:Value"),
                   ExecutionStatus.FAILED.name()))
        .isTrue();

    log.info("Deleting the pipeline");
    PipelineRestUtils.deletePipeline(bearerToken, pipeline.getUuid(), appId);
  }

  private boolean deployAndCheckStatus(String command, String status) throws IOException {
    List<String> deployOutput = harnesscliHelper.executeCLICommand(command);
    String executionId = deployOutput.get(0).split(" ")[2];

    // Add more testing around the output and poll the status
    assertThat(deployOutput).isNotEmpty();
    Awaitility.await()
        .atMost(30, TimeUnit.SECONDS)
        .pollInterval(1, TimeUnit.SECONDS)
        .until(()
                   -> Setup.portal()
                          .auth()
                          .oauth2(bearerToken)
                          .queryParam("appId", appId)
                          .get("/executions/" + executionId)
                          .jsonPath()
                          .<String>getJsonObject("resource.status")
                          .equals(status));

    WorkflowExecution runningWorkflowExecution =
        workflowExecutionService.getExecutionDetails(appId, executionId, true, false);
    log.info("Execution Status : " + runningWorkflowExecution.getStatus());
    assertThat(ExecutionStatus.RUNNING).isEqualTo(runningWorkflowExecution.getStatus());

    return true;
  }

  public static Pipeline getPipelineWithTwoStages(List<PipelineStage> pipelineStages, String appId, String accountId) {
    Pipeline pipeline = new Pipeline();
    String pipelineName = "CLI-PIPELINE-" + System.currentTimeMillis();
    log.info("Generated unique pipeline name : " + pipelineName);
    pipeline.setName(pipelineName);
    pipeline.setDescription("description");
    pipeline.setTemplatized(true);
    pipeline.setPipelineStages(pipelineStages);

    log.info("Creating the pipeline");
    Pipeline createdPipeline = PipelineRestUtils.createPipeline(appId, pipeline, accountId, bearerToken);
    assertThat(createdPipeline).isNotNull();

    return createdPipeline;
  }

  public List<PipelineStage> getPipelineStages() {
    List<PipelineStage> pipelineStages = new ArrayList<>();
    // Templatize the a workflow
    Workflow rollingWorkflowBeforeTemplatizing = workflowUtils.createRollingWorkflow(
        "Test-Rolling-Templatized-CLI-Deployment", service, infrastructureDefinition);
    Workflow testWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, accountId, appId, rollingWorkflowBeforeTemplatizing);
    testWorkflow.setTemplateExpressions(getTemplateExpressions());
    Workflow templateRollingWorkflow = WorkflowRestUtils.updateWorkflow(bearerToken, accountId, appId, testWorkflow);
    String templateRollingWorkflowId = templateRollingWorkflow.getUuid();

    Map<String, String> variables = new HashMap<>();
    variables.put("service", "${service}");
    variables.put("Infra_Def", "${Infra_Def}");
    PipelineStage executionStage =
        PipelineUtils.prepareExecutionStage(infrastructureDefinition.getEnvId(), templateRollingWorkflowId, variables);
    pipelineStages.add(executionStage);

    Workflow rollingWorkflow =
        workflowUtils.createRollingWorkflow("Test-Rolling-CLI-Deployment", service, infrastructureDefinition);
    String rollingWorkflowId = createWorkflowAndReturnId(rollingWorkflow);
    executionStage = PipelineUtils.prepareExecutionStage(
        infrastructureDefinition.getEnvId(), rollingWorkflowId, Collections.emptyMap());
    pipelineStages.add(executionStage);

    return pipelineStages;
  }

  private String getCommand(Commands predefined, String commandTemplate) {
    switch (predefined) {
      case DEPLOY_USING_CONFIG:
        return "harness deploy --configFile ./deployConfig.yaml";
      case DEPLOY_WORKFLOW_TEST:
        return commandTemplate;
      case DEPLOY_PIPELINE_TEST:
      case DEPLOY_TEMPLATE_WORKFLOW:
        return commandTemplate + " --variables service:" + service.getUuid()
            + " --variables Infra_Def:" + infrastructureDefinition.getUuid();
      default:
        return "harness deploy";
    }
  }

  private String createWorkflowAndReturnId(Workflow workflow) {
    Workflow testWorkflow = WorkflowRestUtils.createWorkflow(bearerToken, accountId, appId, workflow);
    Workflow ensuredTestWorkflow = workflowGenerator.ensureWorkflow(seed, owners, testWorkflow);
    return ensuredTestWorkflow.getUuid();
  }

  public static List<TemplateExpression> getTemplateExpressions() {
    List<TemplateExpression> templateExpressionList = new ArrayList<>();
    templateExpressionList.add(getTemplateExpressionsForService("${service}"));
    templateExpressionList.add(getTemplateExpressionsForInfraDefinition("${Infra_Def}"));
    return templateExpressionList;
  }

  private static TemplateExpression getTemplateExpressionsForInfraDefinition(String expression) {
    Map<String, Object> metaData =
        ImmutableMap.<String, Object>builder().put("entityType", EntityType.INFRASTRUCTURE_DEFINITION.name()).build();
    return TemplateExpression.builder()
        .fieldName("infraDefinitionId")
        .expression(expression)
        .mandatory(true)
        .expressionAllowed(false)
        .metadata(metaData)
        .build();
  }

  private static TemplateExpression getTemplateExpressionsForService(String expression) {
    Map<String, Object> metaData =
        ImmutableMap.<String, Object>builder().put("entityType", EntityType.SERVICE.name()).build();
    return TemplateExpression.builder()
        .fieldName("serviceId")
        .expression(expression)
        .mandatory(true)
        .expressionAllowed(false)
        .metadata(metaData)
        .build();
  }
}
