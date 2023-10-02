/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.InstanceElement;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContextImpl;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class RepeatStateHelperTest extends WingsBaseTest {
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private ServiceResourceService serviceResourceService;
  @Inject @InjectMocks RepeatStateHelper repeatStateHelper;
  private static final String appId = generateUuid();
  private static final String workflowExecutionId = generateUuid();
  private static final String artifactId = generateUuid();
  private static final String wrongArtifactId = generateUuid();
  private static final String artifactStreamId = generateUuid();
  private Artifact artifact;
  private Artifact wrongArtifact;
  private WorkflowExecution workflowExecution;

  private ExecutionArgs buildExecutionArgs() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
    executionArgs.setArtifacts(Collections.singletonList(artifact));
    return executionArgs;
  }

  @Before
  public void setup() throws Exception {
    artifact = Artifact.Builder.anArtifact()
                   .withUuid(artifactId)
                   .withAppId(appId)
                   .withArtifactStreamId(artifactStreamId)
                   .withMetadata(new ArtifactMetadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "1.2")))
                   .withDisplayName("Some artifact")
                   .build();

    wrongArtifact = Artifact.Builder.anArtifact()
                        .withUuid(wrongArtifactId)
                        .withAppId(appId)
                        .withArtifactStreamId(artifactStreamId)
                        .withMetadata(new ArtifactMetadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "1.3")))
                        .withDisplayName("Wrong artifact")
                        .build();
    ExecutionArgs executionArgs = buildExecutionArgs();
    workflowExecution = WorkflowExecution.builder()
                            .uuid(workflowExecutionId)
                            .appId(appId)
                            .envType(PROD)
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .executionArgs(executionArgs)
                            .artifacts(Collections.singletonList(artifact))
                            .build();
    when(workflowExecutionService.getWorkflowExecution(eq(appId), eq(workflowExecutionId), any(String[].class)))
        .thenReturn(workflowExecution);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFilterInstancesWithCorrectArtifact() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    when(context.getWorkflowExecutionId()).thenReturn(workflowExecutionId);
    when(context.getAppId()).thenReturn(appId);
    String id1 = generateUuid();
    String id2 = generateUuid();
    String id3 = generateUuid();
    InstanceElement i1 = anInstanceElement().uuid(id1).build();
    InstanceElement i2 = anInstanceElement().uuid(id2).build();
    InstanceElement i3 = anInstanceElement().uuid(id3).build();
    List<ContextElement> contextElements = Arrays.asList(i1, i2, i3);
    when(serviceResourceService.findPreviousArtifact(appId, workflowExecutionId, i1)).thenReturn(artifact);
    when(serviceResourceService.findPreviousArtifact(appId, workflowExecutionId, i2)).thenReturn(artifact);
    when(serviceResourceService.findPreviousArtifact(appId, workflowExecutionId, i3)).thenReturn(wrongArtifact);

    List<ContextElement> filteredList =
        repeatStateHelper.filterElementsWithArtifactFromLastDeployment(context, contextElements);

    assertThat(filteredList).isNotEmpty().hasSize(2);
    assertThat(filteredList).containsExactly(i1, i2);
  }
}
