/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.metadata;

import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.USER_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.CreatedByType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.NameValuePair;
import software.wings.beans.WorkflowExecution;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class WorkflowExecutionMetadataTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testAccept() {
    SimpleVisitor simpleVisitor = new SimpleVisitor();
    WorkflowExecutionMetadata.builder()
        .executionGraph(
            asList(GraphNodeMetadata.builder().id("id1").build(), GraphNodeMetadata.builder().id("id2").build()))
        .build()
        .accept(simpleVisitor);
    assertThat(simpleVisitor.getVisited()).containsExactly("id1", "id2");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFromWorkflowExecutions() {
    assertThat(WorkflowExecutionMetadata.fromWorkflowExecutions(null)).isNull();

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    List<WorkflowExecutionMetadata> workflowExecutionMetadataList =
        WorkflowExecutionMetadata.fromWorkflowExecutions(asList(null, MetadataTestUtils.prepareWorkflowExecution(now)));
    assertThat(workflowExecutionMetadataList).isNotNull();
    assertThat(workflowExecutionMetadataList.size()).isEqualTo(1);
    validateWorkflowExecutionMetadata(workflowExecutionMetadataList.get(0), now, false);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFromWorkflowExecution() {
    assertThat(WorkflowExecutionMetadata.fromWorkflowExecution(null)).isNull();
    assertThat(WorkflowExecutionMetadata.fromWorkflowExecution(
                   WorkflowExecution.builder().workflowType(WorkflowType.PIPELINE).build()))
        .isNull();

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    WorkflowExecutionMetadata workflowExecutionMetadata =
        WorkflowExecutionMetadata.fromWorkflowExecution(MetadataTestUtils.prepareWorkflowExecution(now));
    validateWorkflowExecutionMetadata(workflowExecutionMetadata, now, true);
  }

  public void validateWorkflowExecutionMetadata(
      WorkflowExecutionMetadata workflowExecutionMetadata, Instant now, boolean shouldHaveTriggeredBy) {
    assertThat(workflowExecutionMetadata.getId()).isEqualTo(WORKFLOW_EXECUTION_ID);
    assertThat(workflowExecutionMetadata.getAppId()).isEqualTo(APP_ID);
    assertThat(workflowExecutionMetadata.getExecutionType()).isEqualTo("Workflow");
    assertThat(workflowExecutionMetadata.getApplication()).isEqualTo(APP_NAME);
    assertThat(workflowExecutionMetadata.getEntityName()).isEqualTo(WORKFLOW_NAME);
    assertThat(workflowExecutionMetadata.getEnvironment()).isNotNull();
    assertThat(workflowExecutionMetadata.getEnvironment().getName()).isEqualTo(ENV_NAME);
    assertThat(workflowExecutionMetadata.getServiceInfrastructures()).isNotNull();
    assertThat(workflowExecutionMetadata.getServiceInfrastructures().size()).isEqualTo(1);
    assertThat(workflowExecutionMetadata.getServiceInfrastructures().get(0).getService()).isEqualTo("s");
    assertThat(workflowExecutionMetadata.getInputArtifacts()).isNotNull();
    assertThat(workflowExecutionMetadata.getInputArtifacts().size()).isEqualTo(1);
    assertThat(workflowExecutionMetadata.getInputArtifacts().get(0).getBuildNo()).isEqualTo("dn1");
    assertThat(workflowExecutionMetadata.getCollectedArtifacts()).isNull();
    assertThat(workflowExecutionMetadata.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(workflowExecutionMetadata.getExecutionGraph()).isNotNull();
    assertThat(workflowExecutionMetadata.getExecutionGraph().size()).isEqualTo(1);
    assertThat(workflowExecutionMetadata.getExecutionGraph().get(0).getId()).isEqualTo("id");
    assertThat(workflowExecutionMetadata.isOnDemandRollback()).isFalse();
    assertThat(workflowExecutionMetadata.getTags()).isNotNull();
    assertThat(workflowExecutionMetadata.getTags().stream().map(NameValuePair::getName).collect(Collectors.toList()))
        .containsExactly("n1", "n2");
    assertThat(workflowExecutionMetadata.getTiming()).isNotNull();
    assertThat(workflowExecutionMetadata.getTiming().getStartTime().toInstant())
        .isEqualTo(now.minus(1, ChronoUnit.MINUTES));
    assertThat(workflowExecutionMetadata.getTiming().getEndTime().toInstant()).isEqualTo(now);
    assertThat(workflowExecutionMetadata.getTiming().getDuration().toMinutes()).isEqualTo(1);
    if (shouldHaveTriggeredBy) {
      assertThat(workflowExecutionMetadata.getTriggeredBy()).isNotNull();
      assertThat(workflowExecutionMetadata.getTriggeredBy().getType()).isEqualTo(CreatedByType.USER);
      assertThat(workflowExecutionMetadata.getTriggeredBy().getName()).isEqualTo(USER_NAME);
      assertThat(workflowExecutionMetadata.getTriggeredBy().getEmail()).isEqualTo(USER_EMAIL);
    } else {
      assertThat(workflowExecutionMetadata.getTriggeredBy()).isNull();
    }
  }
}
