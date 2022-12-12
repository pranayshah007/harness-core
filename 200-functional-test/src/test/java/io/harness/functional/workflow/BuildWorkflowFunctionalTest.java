/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.workflow;

import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.rule.Owner;

import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.persistence.artifact.Artifact;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class BuildWorkflowFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private WorkflowUtils workflowUtils;

  @Inject private ServiceGenerator serviceGenerator;
  @Inject private WorkflowGenerator workflowGenerator;
  private final Seed seed = new Seed(0);

  private Owners owners;
  private Service service;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    service = serviceGenerator.ensureK8sTest(seed, owners, "k8s-service");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void shouldRunBuildWorkflow() {
    final String appId = service.getAppId();
    final String accountId = service.getAccountId();

    resetCache(accountId);

    Workflow buildWorkflow = workflowUtils.createBuildWorkflow("build-workflow", appId, service.getArtifactStreamIds());
    buildWorkflow = workflowGenerator.ensureWorkflow(seed, owners, buildWorkflow);
    WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, appId, null, buildWorkflow.getUuid(), Collections.<Artifact>emptyList());
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }
}
