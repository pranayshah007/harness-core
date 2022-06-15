/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.beans.EnvironmentType.NON_PROD;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static software.wings.service.impl.instance.InstanceSyncFlow.ITERATOR;
import static software.wings.service.impl.instance.InstanceSyncFlow.MANUAL;
import static software.wings.service.impl.instance.InstanceSyncFlow.NEW_DEPLOYMENT;
import static software.wings.service.impl.instance.InstanceSyncFlow.PERPETUAL_TASK;
import static software.wings.utils.WingsTestConstants.*;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;
import software.wings.api.CustomDeploymentTypeInfo;
import software.wings.api.DeploymentSummary;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.info.PhysicalHostInstanceInfo;
import software.wings.beans.infrastructure.instance.key.HostInstanceKey;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class InstanceHandlerTest extends WingsBaseTest {
  @Spy InstanceUtils instanceUtil;
  @InjectMocks InstanceHandler instanceHandler = mock(InstanceHandler.class, Mockito.CALLS_REAL_METHODS);
  @Mock private WorkflowExecutionService workflowExecutionService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void validateInstanceType_valid_inframappings() {
    instanceHandler.validateInstanceType(InfrastructureMappingType.DIRECT_KUBERNETES.name());
    instanceHandler.validateInstanceType(InfrastructureMappingType.AWS_SSH.name());
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void validateInstanceType_invalid_or_not_supported_infra() {
    instanceHandler.validateInstanceType("abc");
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void canUpdateInstancesInDb() {
    InstanceHandler handler = spy(InstanceHandler.class);

    assertTrue(handler.canUpdateInstancesInDb(MANUAL, "ACCOUNT_ID"));
    assertTrue(handler.canUpdateInstancesInDb(NEW_DEPLOYMENT, "ACCOUNT_ID"));

    assertTrue(handler.canUpdateInstancesInDb(ITERATOR, "ACCOUNT_ID"));
    assertFalse(handler.canUpdateInstancesInDb(PERPETUAL_TASK, "ACCOUNT_ID"));
  }
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void shouldReturnSameDeploymentSummaryInRollbackPhase_FirstDeploy() {
    final String artifactId = "artifact-id";
    final String artifactName = "hello-artifact";
    List<DeploymentSummary> deploymentSummary =
        singletonList(buildDeploymentSummary(buildSampleInstancesJson(1, 2, 3), artifactId, artifactName));
    List<Instance> instancesInDb = new ArrayList<>();
    DeploymentSummary newDeploymentSummary =
        instanceHandler.getDeploymentSummaryForInstanceCreation(instancesInDb, deploymentSummary.get(0), true);
    assertThat(newDeploymentSummary).isEqualTo(deploymentSummary.get(0));
    verify(workflowExecutionService, times(0))
        .getLastSuccessfulWorkflowExecution(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void shouldUpdateCorrectArtifactInRollbackPhase_MultipleServicesAndPhases() {
    final String lastArtifactId = "last-success-artifact-id";
    final String lastArtifactName = "hello-last-success-artifact";
    final String newArtifactId = "new-artifact-id";
    final String newArtifactName = "hello-new-artifact";
    final String otherServiceArtifactId = "other-service-artifact-id";
    final String otherServiceArtifactName = "hello-other-service-artifact";

    List<DeploymentSummary> deploymentSummary =
        singletonList(buildDeploymentSummary(buildSampleInstancesJson(1, 2, 3), newArtifactId, newArtifactName));
    List<DeploymentSummary> wantedNewDeploymentSummary =
        singletonList(buildDeploymentSummary(buildSampleInstancesJson(1, 2, 3), lastArtifactId, lastArtifactName));
    List<Artifact> artifacts = new ArrayList<>();
    artifacts.add(Artifact.Builder.anArtifact()
                      .withAppId(APP_ID)
                      .withUuid(lastArtifactId)
                      .withDisplayName(lastArtifactName)
                      .withServiceIds(asList(SERVICE1_ID))
                      .build());
    artifacts.add(Artifact.Builder.anArtifact()
                      .withAppId(APP_ID)
                      .withUuid(otherServiceArtifactId)
                      .withDisplayName(otherServiceArtifactName)
                      .withServiceIds(asList(SERVICE2_ID))
                      .build());

    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .appId(APP_ID)
                                              .status(ExecutionStatus.SUCCESS)
                                              .envIds(asList(ENV_ID))
                                              .serviceIds(asList(SERVICE1_ID, SERVICE2_ID))
                                              .infraMappingIds(asList(INFRA_MAPPING_ID))
                                              .workflowId(WORKFLOW_ID)
                                              .uuid(WORKFLOW_EXECUTION_ID)
                                              .name(WORKFLOW_NAME)
                                              .artifacts(artifacts)
                                              .startTs(System.currentTimeMillis() - 10000)
                                              .build();
    doReturn(workflowExecution)
        .when(workflowExecutionService)
        .getLastSuccessfulWorkflowExecution(any(), any(), any(), any(), any(), any());

    List<Instance> instancesInDb = buildSampleInstances(newArtifactId, newArtifactName, SERVICE1_ID, 1, 2, 3);
    DeploymentSummary newDeploymentSummary =
        instanceHandler.getDeploymentSummaryForInstanceCreation(instancesInDb, deploymentSummary.get(0), true);
    assertThat(newDeploymentSummary).isEqualTo(wantedNewDeploymentSummary.get(0));
    verify(workflowExecutionService, times(1))
        .getLastSuccessfulWorkflowExecution(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void shouldUpdateCorrectArtifactInRollbackPhase() {
    final String lastArtifactId = "last-success-artifact-id";
    final String lastArtifactName = "hello-last-success-artifact";
    final String newArtifactId = "new-artifact-id";
    final String newArtifactName = "hello-new-artifact";
    List<DeploymentSummary> deploymentSummary =
        singletonList(buildDeploymentSummary(buildSampleInstancesJson(1, 2, 3), newArtifactId, newArtifactName));
    List<DeploymentSummary> wantedNewDeploymentSummary =
        singletonList(buildDeploymentSummary(buildSampleInstancesJson(1, 2, 3), lastArtifactId, lastArtifactName));
    Artifact artifact = Artifact.Builder.anArtifact()
                            .withAppId(APP_ID)
                            .withUuid(lastArtifactId)
                            .withDisplayName(lastArtifactName)
                            .withServiceIds(asList(SERVICE_ID))
                            .build();
    List<Artifact> artifacts = new ArrayList<>();
    artifacts.add(artifact);

    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .appId(APP_ID)
                                              .status(ExecutionStatus.SUCCESS)
                                              .envIds(asList(ENV_ID))
                                              .serviceIds(asList(SERVICE_ID))
                                              .infraMappingIds(asList(INFRA_MAPPING_ID))
                                              .workflowId(WORKFLOW_ID)
                                              .uuid(WORKFLOW_EXECUTION_ID)
                                              .name(WORKFLOW_NAME)
                                              .artifacts(artifacts)
                                              .startTs(System.currentTimeMillis() - 10000)
                                              .build();
    doReturn(workflowExecution)
        .when(workflowExecutionService)
        .getLastSuccessfulWorkflowExecution(any(), any(), any(), any(), any(), any());

    List<Instance> instancesInDb = buildSampleInstances(newArtifactId, newArtifactName, SERVICE_ID, 1, 2, 3);
    DeploymentSummary newDeploymentSummary =
        instanceHandler.getDeploymentSummaryForInstanceCreation(instancesInDb, deploymentSummary.get(0), true);
    assertThat(newDeploymentSummary).isEqualTo(wantedNewDeploymentSummary.get(0));
    verify(workflowExecutionService, times(1))
        .getLastSuccessfulWorkflowExecution(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void shouldReturnSameDeploymentSummaryInDeployPhase() {
    final String artifactId = "artifact-id";
    final String artifactName = "hello-artifact";
    List<DeploymentSummary> deploymentSummary =
        singletonList(buildDeploymentSummary(buildSampleInstancesJson(1, 2, 3), artifactId, artifactName));
    List<Instance> instancesInDb = new ArrayList<>();
    DeploymentSummary newDeploymentSummary =
        instanceHandler.getDeploymentSummaryForInstanceCreation(instancesInDb, deploymentSummary.get(0), false);
    assertThat(newDeploymentSummary).isEqualTo(deploymentSummary.get(0));
    verify(workflowExecutionService, times(0))
        .getLastSuccessfulWorkflowExecution(any(), any(), any(), any(), any(), any());
  }

  private DeploymentSummary buildDeploymentSummary(String scriptOutput, String artifactId, String artifactName) {
    return DeploymentSummary.builder()
        .appId(APP_ID)
        .accountId(ACCOUNT_ID)
        .infraMappingId(INFRA_MAPPING_ID)
        .artifactId(artifactId)
        .artifactName(artifactName)
        .deploymentInfo(CustomDeploymentTypeInfo.builder().scriptOutput(scriptOutput).build())
        .build();
  }

  private List<Instance> buildSampleInstances(
      String artifactId, String artifactName, String serviceId, int... indexes) {
    List<Instance> instances = new ArrayList<>();
    for (int n : indexes) {
      String hostName = String.valueOf(n);
      instances.add(
          Instance.builder()
              .appId(APP_ID)
              .uuid(hostName)
              .accountId(ACCOUNT_ID)
              .infraMappingId(INFRA_MAPPING_ID)
              .serviceId(serviceId)
              .instanceType(InstanceType.PHYSICAL_HOST_INSTANCE)
              .infraMappingType(InfrastructureMappingType.CUSTOM.name())
              .envId(ENV_ID)
              .envType(NON_PROD)
              .lastArtifactId(artifactId)
              .lastArtifactName(artifactName)
              .hostInstanceKey(HostInstanceKey.builder().hostName(hostName).infraMappingId(INFRA_MAPPING_ID).build())
              .instanceInfo(PhysicalHostInstanceInfo.builder()
                                .hostName(hostName)
                                .hostId(hostName)
                                .properties(ImmutableMap.of("hostname", hostName))
                                .build())
              .build());
    }
    return instances;
  }

  private String buildSampleInstancesJson(int... indexes) {
    List<Map<String, Object>> object = new ArrayList<>();
    for (int n : indexes) {
      String hostName = String.valueOf(n);
      object.add(ImmutableMap.of("ip", hostName));
    }
    return JsonUtils.asJson(ImmutableMap.of("Instances", object));
  }
}
