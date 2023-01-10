/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.PHASE_STEP;
import static software.wings.utils.WingsTestConstants.SERVICE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.Service;
import software.wings.beans.WorkflowExecution;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.utils.WingsTestConstants;

import java.util.Arrays;
import java.util.Collections;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class PhaseSubWorkflowTest extends WingsBaseTest {
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  private Service service = Service.builder().name(SERVICE).build();
  private PhaseSubWorkflow phaseSubWorkflow = new PhaseSubWorkflow(PHASE_STEP);

  @Before
  public void setUp() throws Exception {
    Reflect.on(phaseSubWorkflow).set("artifactStreamServiceBindingService", artifactStreamServiceBindingService);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldFetchCorrectArtifactFromService() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    Artifact artifact = anArtifact()
                            .withDisplayName(WingsTestConstants.ARTIFACT_NAME)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withUuid(ARTIFACT_ID)
                            .build();
    executionArgs.setArtifacts(Collections.singletonList(artifact));
    WorkflowExecution workflowExecution = WorkflowExecution.builder().executionArgs(executionArgs).build();
    doReturn(Arrays.asList(ARTIFACT_STREAM_ID, ARTIFACT_STREAM_ID + "2"))
        .when(artifactStreamServiceBindingService)
        .listArtifactStreamIds(service);
    assertThat(phaseSubWorkflow.findRollbackArtifactId(service, workflowExecution)).isEqualTo(ARTIFACT_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotFetchArtifactFromServiceIfUnavailable() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    Artifact artifact = anArtifact()
                            .withDisplayName(WingsTestConstants.ARTIFACT_NAME)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID + "3")
                            .withUuid(ARTIFACT_ID)
                            .build();
    executionArgs.setArtifacts(Collections.singletonList(artifact));
    WorkflowExecution workflowExecution = WorkflowExecution.builder().executionArgs(executionArgs).build();
    doReturn(Arrays.asList(ARTIFACT_STREAM_ID, ARTIFACT_STREAM_ID + "2"))
        .when(artifactStreamServiceBindingService)
        .listArtifactStreamIds(service);
    assertThat(phaseSubWorkflow.findRollbackArtifactId(service, workflowExecution)).isNull();
  }
}
