/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.multiartifact;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions.K8S_BLUE_GREEN_TEST;
import static io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions.K8S_CANARY_TEST;
import static io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions.K8S_ROLLING_TEST;
import static io.harness.rule.OwnerRule.AADITI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.OrchestrationWorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.utils.K8SUtils;
import io.harness.generator.AccountGenerator;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.ArtifactStreamBindingGenerator;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.generator.artifactstream.ArtifactStreamManager;
import io.harness.rule.Owner;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamBinding;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.WorkflowExecutionServiceImpl;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class K8sDeploymentTests extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private WorkflowExecutionServiceImpl workflowExecutionService;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private ArtifactStreamManager artifactStreamManager;
  @Inject private ArtifactStreamBindingGenerator artifactStreamBindingGenerator;
  @Inject private AccountGenerator accountGenerator;
  @Inject private K8SUtils k8SUtils;

  final Randomizer.Seed seed = new Randomizer.Seed(0);
  OwnerManager.Owners owners;
  Application application;
  Account account;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST);
    assertThat(application).isNotNull();
    account = owners.obtainAccount();
    if (account == null) {
      account = accountGenerator.ensurePredefined(seed, owners, AccountGenerator.Accounts.GENERIC_TEST);
    }
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  @Ignore("Enable once feature flag is enabled")
  public void testK8sCanaryWorkflow() {
    testK8sWorkflow(OrchestrationWorkflowType.CANARY);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  @Ignore("Enable once feature flag is enabled")
  public void testK8sBlueGreenWorkflow() {
    testK8sWorkflow(OrchestrationWorkflowType.BLUE_GREEN);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  @Ignore("Enable once feature flag is enabled")
  public void testK8sRollingWorkflow() {
    testK8sWorkflow(OrchestrationWorkflowType.ROLLING);
  }

  private void testK8sWorkflow(OrchestrationWorkflowType workflowType) {
    Service savedService = serviceGenerator.ensurePredefined(seed, owners, ServiceGenerator.Services.K8S_V2_TEST);
    Environment savedEnvironment =
        environmentGenerator.ensurePredefined(seed, owners, EnvironmentGenerator.Environments.GENERIC_TEST);

    InfrastructureDefinitions infraDefinitionType = null;

    // create jenkins artifact stream at ConnectorDisconnectHandler level
    ArtifactStream artifactStream = artifactStreamManager.ensurePredefined(
        seed, owners, ArtifactStreamManager.ArtifactStreams.HARNESS_SAMPLE_DOCKER_AT_CONNECTOR, true);
    assertThat(artifactStream).isNotNull();

    // create artifact stream binding for service
    ArtifactStreamBinding artifactStreamBinding =
        artifactStreamBindingGenerator.ensurePredefined(seed, owners, "artifact", artifactStream.getUuid());
    assertThat(artifactStreamBinding).isNotNull();

    String workflowName = "k8s";
    switch (workflowType) {
      case ROLLING:
        infraDefinitionType = K8S_ROLLING_TEST;
        workflowName = "k8s-rolling-multi-artifact";
        break;

      case CANARY:
        infraDefinitionType = K8S_CANARY_TEST;
        workflowName = "k8s-canary-multi-artifact";
        break;

      case BLUE_GREEN:
        infraDefinitionType = K8S_BLUE_GREEN_TEST;
        workflowName = "k8s-bg-multi-artifact";
        break;

      default:
        assert false;
    }

    workflowName = workflowName + '-' + System.currentTimeMillis();

    InfrastructureDefinition infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, infraDefinitionType);

    // create workflow
    Workflow savedWorkflow =
        K8SUtils.createWorkflow(application.getUuid(), savedEnvironment.getUuid(), savedService.getUuid(),
            infrastructureDefinition.getUuid(), workflowName, workflowType, bearerToken, application.getAccountId());

    resetCache(application.getAccountId());

    // Deploy the workflow
    WorkflowExecution workflowExecution =
        WorkflowRestUtils.startWorkflow(bearerToken, application.getUuid(), savedEnvironment.getUuid(),
            getExecutionArgs(savedWorkflow, savedEnvironment.getUuid(), savedService.getUuid()));
    assertThat(workflowExecution).isNotNull();

    k8SUtils.waitForWorkflowExecution(workflowExecution, 10, application.getUuid());

    Workflow cleanupWorkflow =
        K8SUtils.createK8sCleanupWorkflow(application.getUuid(), savedEnvironment.getUuid(), savedService.getUuid(),
            infrastructureDefinition.getUuid(), workflowName, bearerToken, application.getAccountId());

    // Deploy the workflow
    WorkflowExecution cleanupWorkflowExecution =
        WorkflowRestUtils.startWorkflow(bearerToken, application.getUuid(), savedEnvironment.getUuid(),
            getExecutionArgs(cleanupWorkflow, savedEnvironment.getUuid(), savedService.getUuid()));
    assertThat(cleanupWorkflowExecution).isNotNull();

    k8SUtils.waitForWorkflowExecution(cleanupWorkflowExecution, 5, application.getUuid());
  }

  private ExecutionArgs getExecutionArgs(Workflow workflow, String envId, String serviceId) {
    // get artifact variables from deployment metadata for given workflow
    List<ArtifactVariable> artifactVariables = workflowGenerator.getArtifactVariablesFromDeploymentMetadataForWorkflow(
        application.getUuid(), workflow.getUuid());
    if (isNotEmpty(artifactVariables)) {
      for (ArtifactVariable artifactVariable : artifactVariables) {
        Artifact artifact = MultiArtifactTestUtils.collectArtifact(
            bearerToken, account.getUuid(), artifactVariable.getArtifactStreamSummaries().get(0).getArtifactStreamId());
        if (artifact != null) {
          artifactVariable.setValue(artifact.getUuid());
        }
      }
    }

    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(workflow.getWorkflowType());
    executionArgs.setOrchestrationId(workflow.getUuid());
    executionArgs.setServiceId(serviceId);
    executionArgs.setCommandName("START");
    executionArgs.setArtifactVariables(artifactVariables);

    return executionArgs;
  }
}
