/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.delegate;

import static io.harness.generator.EnvironmentGenerator.Environments.GENERIC_TEST;
import static io.harness.rule.OwnerRule.GUNA;
import static io.harness.testframework.framework.SshDelegateExecutor.ensureSshDelegateCleanUp;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ExecutionStatus;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.rule.Owner;
import io.harness.testframework.framework.SshDelegateExecutor;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.persistence.artifact.Artifact;

import com.google.inject.Inject;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import java.io.IOException;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class SshDelegateFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private WorkflowUtils workflowUtils;
  @Inject private SshDelegateExecutor sshDelegateExecutor;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private WorkflowGenerator workflowGenerator;

  private final Seed seed = new Seed(0);
  private OwnerManager.Owners owners;
  private Service service;
  private Application application;

  private Environment environment;

  @Before
  public void setUp() throws IOException, InterruptedException, DockerException, DockerCertificateException {
    owners = ownerManager.create();
    application = applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST);
    assertThat(application).isNotNull();
    environment = environmentGenerator.ensurePredefined(seed, owners, GENERIC_TEST);
    assertThat(environment).isNotNull();
    log.info("Ensuring SSH  Delegate");
    sshDelegateExecutor.ensureSshDelegate(getAccount(), bearerToken, SshDelegateFunctionalTest.class);
  }

  @AfterClass
  public static void tearDown() {
    ensureSshDelegateCleanUp();
  }

  @Test
  @Owner(developers = GUNA)
  @Category(FunctionalTests.class)
  @Ignore("Disabled until ensure ssh delegate works on Jenkins builds")
  public void testSshDelegate() {
    assertThat(sshDelegateExecutor.delegateUuid).isNotNull();
    String script = "echo This is run from ssh-delegate";
    Workflow workflow = workflowUtils.createWorkflowWithShellScriptAndDelegateSelector(
        "ssh-delegate-workflow", application.getAppId(), "BASH", script, new String[] {"ssh-delegate"});
    WorkflowExecution workflowExecution = createAndExecuteWorkflow(workflow);
    workflowUtils.validateWorkflowStatus(workflowExecution, ExecutionStatus.SUCCESS);
  }

  private WorkflowExecution createAndExecuteWorkflow(Workflow workflow) {
    Workflow savedWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), workflow);
    assertThat(savedWorkflow).isNotNull();

    return runWorkflow(bearerToken, application.getUuid(), environment.getUuid(), savedWorkflow.getUuid(),
        Collections.<Artifact>emptyList());
  }
}
