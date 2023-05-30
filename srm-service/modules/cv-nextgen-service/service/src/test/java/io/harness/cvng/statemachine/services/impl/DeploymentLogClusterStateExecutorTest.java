/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.cvng.statemachine.beans.AnalysisState.StateType.DEPLOYMENT_LOG_ANALYSIS;
import static io.harness.cvng.statemachine.beans.AnalysisState.StateType.DEPLOYMENT_LOG_CLUSTER;
import static io.harness.cvng.statemachine.beans.AnalysisState.StateType.DEPLOYMENT_LOG_HOST_SAMPLING_STATE;
import static io.harness.rule.OwnerRule.NAVEEN;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.analysis.beans.LogClusterLevel;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.entities.DeploymentLogClusterState;
import io.harness.cvng.statemachine.services.api.DeploymentLogClusterStateExecutor;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DeploymentLogClusterStateExecutorTest extends CategoryTest {
  DeploymentLogClusterStateExecutor deploymentLogClusterStateExecutor = new DeploymentLogClusterStateExecutor();

  @Mock VerificationJobInstanceService verificationJobInstanceService;

  BuilderFactory builderFactory;
  @Before
  public void setup() throws IllegalAccessException, IOException {
    MockitoAnnotations.initMocks(this);
    builderFactory = builderFactory.getDefault();
    FieldUtils.writeField(
        deploymentLogClusterStateExecutor, "verificationJobInstanceService", verificationJobInstanceService, true);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandleTransition_L1() {
    DeploymentLogClusterState deploymentLogClusterState = DeploymentLogClusterState.builder().build();
    deploymentLogClusterState.setClusterLevel(LogClusterLevel.L1);
    AnalysisState logClusterState = deploymentLogClusterStateExecutor.handleTransition(deploymentLogClusterState);
    assertEquals(logClusterState.getType(), DEPLOYMENT_LOG_CLUSTER);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandleTransition_L2_loadTest() {
    String verificationJobInstanceId = UUID.randomUUID().toString();
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder().build();
    verificationJobInstance.setUuid(verificationJobInstanceId);
    when(verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId))
        .thenReturn(verificationJobInstance);
    DeploymentLogClusterState deploymentLogClusterState = DeploymentLogClusterState.builder().build();
    deploymentLogClusterState.setClusterLevel(LogClusterLevel.L2);
    deploymentLogClusterState.setInputs(
        AnalysisInput.builder().verificationJobInstanceId(verificationJobInstanceId).build());
    AnalysisState logClusterState = deploymentLogClusterStateExecutor.handleTransition(deploymentLogClusterState);
    assertEquals(logClusterState.getType(), DEPLOYMENT_LOG_ANALYSIS);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandleTransition_L2_hostSampling() {
    String verificationJobInstanceId = UUID.randomUUID().toString();
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder().build();
    verificationJobInstance.setUuid(verificationJobInstanceId);
    verificationJobInstance.setResolvedJob(builderFactory.getDeploymentVerificationJob());
    when(verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId))
        .thenReturn(verificationJobInstance);
    DeploymentLogClusterState deploymentLogClusterState = DeploymentLogClusterState.builder().build();
    deploymentLogClusterState.setClusterLevel(LogClusterLevel.L2);
    deploymentLogClusterState.setInputs(
        AnalysisInput.builder().verificationJobInstanceId(verificationJobInstanceId).build());
    AnalysisState logClusterState = deploymentLogClusterStateExecutor.handleTransition(deploymentLogClusterState);
    assertEquals(logClusterState.getType(), DEPLOYMENT_LOG_HOST_SAMPLING_STATE);
  }
}
