/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.multiartifact;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rule.OwnerRule.AADITI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.generator.AccountGenerator;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.ArtifactStreamBindingGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.TemplateFolderGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.generator.artifactstream.ArtifactStreamManager;
import io.harness.rule.Owner;
import io.harness.testframework.framework.Setup;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.ExecutionCredential;
import software.wings.beans.SSHExecutionCredential;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamBinding;
import software.wings.persistence.artifact.Artifact;

import com.google.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SshDeploymentE2ETests extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private AccountGenerator accountGenerator;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private TemplateFolderGenerator templateFolderGenerator;
  @Inject private ArtifactStreamManager artifactStreamManager;
  @Inject private ArtifactStreamBindingGenerator artifactStreamBindingGenerator;
  @Inject private ServiceGenerator serviceGenerator;

  final Randomizer.Seed seed = new Randomizer.Seed(0);
  OwnerManager.Owners owners;
  Application application;
  Account account;
  Service service;
  Workflow basicWorkflow;

  @Before
  public void setUp() {
    owners = ownerManager.create();

    application =
        applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.FUNCTIONAL_TEST);
    assertThat(application).isNotNull();

    service = serviceGenerator.ensurePredefined(seed, owners, ServiceGenerator.Services.MULTI_ARTIFACT_FUNCTIONAL_TEST);
    assertThat(service).isNotNull();

    account = owners.obtainAccount();
    if (account == null) {
      account = accountGenerator.ensurePredefined(seed, owners, AccountGenerator.Accounts.GENERIC_TEST);
    }
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  @Ignore("Enable once feature flag is enabled")
  public void runBasicWorkflowWithServiceCommandLinkedToWorkflow() {
    // create jenkins artifact stream at ConnectorDisconnectHandler level
    ArtifactStream artifactStream = artifactStreamManager.ensurePredefined(
        seed, owners, ArtifactStreamManager.ArtifactStreams.HARNESS_SAMPLE_ECHO_WAR_AT_CONNECTOR, true);
    assertThat(artifactStream).isNotNull();

    // create artifact stream binding for service
    ArtifactStreamBinding artifactStreamBinding =
        artifactStreamBindingGenerator.ensurePredefined(seed, owners, "artifact1", artifactStream.getUuid());
    assertThat(artifactStreamBinding).isNotNull();

    artifactStreamBinding =
        artifactStreamBindingGenerator.ensurePredefined(seed, owners, "artifact2", artifactStream.getUuid());
    assertThat(artifactStreamBinding).isNotNull();

    // create a service command template with artifact variable references
    // link template to workflow
    basicWorkflow =
        workflowGenerator.ensurePredefined(seed, owners, WorkflowGenerator.Workflows.BASIC_SIMPLE_MULTI_ARTIFACT);

    assertThat(basicWorkflow).isNotNull();

    resetCache(application.getAccountId());

    // get artifact variables from deployment metadata for given workflow
    List<ArtifactVariable> artifactVariables = workflowGenerator.getArtifactVariablesFromDeploymentMetadataForWorkflow(
        application.getUuid(), basicWorkflow.getUuid());
    if (isNotEmpty(artifactVariables)) {
      for (ArtifactVariable artifactVariable : artifactVariables) {
        Artifact artifact = MultiArtifactTestUtils.collectArtifact(
            bearerToken, account.getUuid(), artifactVariable.getArtifactStreamSummaries().get(0).getArtifactStreamId());
        if (artifact != null) {
          artifactVariable.setValue(artifact.getUuid());
        }
      }
    }
    // execute workflow
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
    executionArgs.setExecutionCredential(SSHExecutionCredential.Builder.aSSHExecutionCredential()
                                             .withExecutionType(ExecutionCredential.ExecutionType.SSH)
                                             .build());
    executionArgs.setOrchestrationId(basicWorkflow.getUuid());
    executionArgs.setArtifactVariables(artifactVariables);
    WorkflowExecution workflowExecution = runWorkflow(bearerToken, application.getUuid(), null,
        executionArgs); // workflowExecutionRestResponse.getResource();
    assertThat(workflowExecution).isNotNull();
    Awaitility.await()
        .atMost(120, TimeUnit.SECONDS)
        .pollInterval(5, TimeUnit.SECONDS)
        .until(()
                   -> Setup.portal()
                          .auth()
                          .oauth2(bearerToken)
                          .queryParam("appId", application.getUuid())
                          .get("/executions/" + workflowExecution.getUuid())
                          .jsonPath()
                          .<String>getJsonObject("resource.status")
                          .equals(ExecutionStatus.SUCCESS.name()));
  }
}
