/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.processor;

import static io.harness.rule.OwnerRule.GARVIT;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.execution.export.metadata.ApprovalMetadata;
import io.harness.execution.export.metadata.PipelineExecutionMetadata;
import io.harness.execution.export.metadata.PipelineStageExecutionMetadata;
import io.harness.execution.export.metadata.WorkflowExecutionMetadata;
import io.harness.rule.Owner;

import software.wings.beans.security.UserGroup;
import software.wings.service.intfc.UserGroupService;
import software.wings.sm.StateType;

import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class UserGroupsProcessorTest extends CategoryTest {
  @Mock private UserGroupService userGroupService;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testVisitExecutionMetadata() {
    UserGroupsProcessor userGroupsProcessor = new UserGroupsProcessor();

    userGroupsProcessor.visitExecutionMetadata(WorkflowExecutionMetadata.builder().id("id").build());
    assertThat(userGroupsProcessor.getUserGroupIdToApprovalMetadata().isEmpty()).isTrue();
    userGroupsProcessor.visitExecutionMetadata(PipelineExecutionMetadata.builder().id("id").build());
    assertThat(userGroupsProcessor.getUserGroupIdToApprovalMetadata().isEmpty()).isTrue();
    userGroupsProcessor.visitExecutionMetadata(
        PipelineExecutionMetadata.builder()
            .id("id")
            .stages(asList(null, PipelineStageExecutionMetadata.builder().type(StateType.ENV_STATE.name()).build()))
            .build());
    assertThat(userGroupsProcessor.getUserGroupIdToApprovalMetadata().isEmpty()).isTrue();
    userGroupsProcessor.visitExecutionMetadata(
        PipelineExecutionMetadata.builder()
            .id("id")
            .stages(Collections.singletonList(PipelineStageExecutionMetadata.builder()
                                                  .type(StateType.APPROVAL.name())
                                                  .approvalData(ApprovalMetadata.builder().build())
                                                  .build()))
            .build());
    assertThat(userGroupsProcessor.getUserGroupIdToApprovalMetadata().isEmpty()).isTrue();

    userGroupsProcessor.visitExecutionMetadata(
        PipelineExecutionMetadata.builder()
            .id("id1")
            .stages(Collections.singletonList(
                PipelineStageExecutionMetadata.builder()
                    .type(StateType.APPROVAL.name())
                    .approvalData(ApprovalMetadata.builder().userGroupIds(asList("ug1", "ug2")).build())
                    .build()))
            .build());
    assertThat(userGroupsProcessor.getUserGroupIdToApprovalMetadata().isEmpty()).isFalse();
    assertThat(userGroupsProcessor.getUserGroupIdToApprovalMetadata().keySet()).containsExactlyInAnyOrder("ug1", "ug2");
    assertThat(userGroupsProcessor.getUserGroupIdToApprovalMetadata().get("ug1").size()).isEqualTo(1);
    assertThat(userGroupsProcessor.getUserGroupIdToApprovalMetadata().get("ug2").size()).isEqualTo(1);

    userGroupsProcessor.visitExecutionMetadata(
        PipelineExecutionMetadata.builder()
            .id("id2")
            .stages(Collections.singletonList(
                PipelineStageExecutionMetadata.builder()
                    .type(StateType.APPROVAL.name())
                    .approvalData(ApprovalMetadata.builder().userGroupIds(Collections.singletonList("ug1")).build())
                    .build()))
            .build());
    assertThat(userGroupsProcessor.getUserGroupIdToApprovalMetadata().keySet()).containsExactlyInAnyOrder("ug1", "ug2");
    assertThat(userGroupsProcessor.getUserGroupIdToApprovalMetadata().get("ug1").size()).isEqualTo(2);
    assertThat(userGroupsProcessor.getUserGroupIdToApprovalMetadata().get("ug2").size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testProcess() {
    UserGroupsProcessor userGroupsProcessor = new UserGroupsProcessor();
    userGroupsProcessor.setUserGroupService(userGroupService);

    userGroupsProcessor.process();
    verify(userGroupService, never()).fetchUserGroupNamesFromIdsUsingSecondary(any());

    ApprovalMetadata approvalMetadata1 = ApprovalMetadata.builder().userGroupIds(asList("ug1", "ug2")).build();
    ApprovalMetadata approvalMetadata2 =
        ApprovalMetadata.builder().userGroupIds(Collections.singletonList("ug1")).build();
    userGroupsProcessor.visitExecutionMetadata(
        PipelineExecutionMetadata.builder()
            .id("id1")
            .stages(Collections.singletonList(PipelineStageExecutionMetadata.builder()
                                                  .type(StateType.APPROVAL.name())
                                                  .approvalData(approvalMetadata1)
                                                  .build()))
            .build());
    userGroupsProcessor.visitExecutionMetadata(
        PipelineExecutionMetadata.builder()
            .id("id2")
            .stages(Collections.singletonList(PipelineStageExecutionMetadata.builder()
                                                  .type(StateType.APPROVAL.name())
                                                  .approvalData(approvalMetadata2)
                                                  .build()))
            .build());

    when(userGroupService.fetchUserGroupNamesFromIdsUsingSecondary(any())).thenReturn(Collections.emptyList());
    userGroupsProcessor.process();
    verify(userGroupService, times(1)).fetchUserGroupNamesFromIdsUsingSecondary(any());

    when(userGroupService.fetchUserGroupNamesFromIdsUsingSecondary(any()))
        .thenReturn(asList(UserGroup.builder().uuid("ug1").name("ugn1").build(),
            UserGroup.builder().uuid("ug2").name("ugn2").build()));
    userGroupsProcessor.process();
    verify(userGroupService, times(2)).fetchUserGroupNamesFromIdsUsingSecondary(any());
    assertThat(approvalMetadata1.getUserGroups()).containsExactlyInAnyOrder("ugn1", "ugn2");
    assertThat(approvalMetadata2.getUserGroups()).containsExactlyInAnyOrder("ugn1");
  }
}
