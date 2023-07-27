/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.quickFilters;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.filter.FilterType.PIPELINEEXECUTION;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.service.FilterService;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionFilterPropertiesDTO;
import io.harness.pms.plan.execution.service.PMSExecutionServiceImpl;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public class QuickFilterTest extends CategoryTest {
  @Mock private TriggeredByHelper triggeredByHelper;
  @Mock private FilterService filterService;
  @Mock private GitSyncSdkService gitSyncSdkService;

  @InjectMocks private PMSExecutionServiceImpl pmsExecutionServiceImpl;

  String accountId = "acc";
  String orgId = "org";
  String projId = "pro";
  String pipelineId = "pip";
  private final List<String> pipelineIdList = Arrays.asList(pipelineId);
  String moduleName = "mod";
  String searchTerm = "sear";

  @Before
  public void init() {
    MockitoAnnotations.initMocks(this);
    when(triggeredByHelper.getFromSecurityContext()).thenReturn(TriggeredBy.newBuilder().build());
  }
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFormCriteriaQuickFilters() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);
    // testing pipelineIdentifier,status and myDeployments values
    Criteria form = pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null, null, moduleName,
        searchTerm, Arrays.asList(ExecutionStatus.FAILED, ExecutionStatus.ABORTED), true, false, true);

    // status
    assertThat(form.getCriteriaObject().get("status").toString()).isEqualTo("Document{{$in=[FAILED, ABORTED]}}");

    // myDeployments
    assertThat(form.getCriteriaObject().containsKey("executionTriggerInfo.triggeredBy")).isEqualTo(true);
    assertThat(form.getCriteriaObject().containsKey("executionTriggerInfo.triggerType")).isEqualTo(true);

    // pipelineIdentifier
    assertThat(form.getCriteriaObject().get("pipelineIdentifier").toString().contentEquals(pipelineId)).isEqualTo(true);

    // pipelineDeleted
    assertThat(form.getCriteriaObject().get("pipelineDeleted")).isNotEqualTo(true);

    // making myDeployments = false
    Criteria allDeploymentsForm = pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null, null,
        moduleName, searchTerm, Collections.singletonList(ExecutionStatus.FAILED), false, false, true);
    // allDeployment -> myDeployments = false
    assertThat(allDeploymentsForm.getCriteriaObject().containsKey("executionTriggerInfo.triggeredBy")).isEqualTo(false);
    assertThat(allDeploymentsForm.getCriteriaObject().containsKey("executionTriggerInfo.triggerType")).isEqualTo(false);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFormCriteriaQuickFiltersWithBothStatusFilters() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);
    // testing pipelineIdentifier,status and myDeployments values
    Criteria form = pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .status(Arrays.asList(ExecutionStatus.ABORTED, ExecutionStatus.PAUSED))
            .build(),
        null, null, Arrays.asList(ExecutionStatus.FAILED, ExecutionStatus.ABORTED), false, false, true);

    // status
    assertThat(form.getCriteriaObject().get("status").toString()).isEqualTo("Document{{$in=[FAILED, ABORTED]}}");
    assertThat(form.getCriteriaObject().get("$and").toString())
        .isEqualTo("[Document{{status=Document{{$in=[ABORTED, PAUSED]}}}}]");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFormCriteriaFilterProperties() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);
    // making a filterProperties object with a status value
    Criteria form = pmsExecutionServiceImpl.formCriteria(null, null, null, null, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .status(Collections.singletonList(ExecutionStatus.ABORTED))
            .build(),
        null, null, null, true, false, true);
    String documentString = "[Document{{status=Document{{$in=[ABORTED]}}}}]";
    assertThat(form.getCriteriaObject().get("$and").toString()).isEqualTo(documentString);

    // filterProperties and filterIdentifier as not null
    assertThatThrownBy(
        ()
            -> pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, "filterIdentifierDummy",
                PipelineExecutionFilterPropertiesDTO.builder()
                    .status(Collections.singletonList(ExecutionStatus.ABORTED))
                    .build(),
                moduleName, searchTerm, null, true, false, true))
        .isInstanceOf(InvalidRequestException.class);

    // giving random name to filterIdentifier and filterProperties as null
    String randomFilterIdentifier = RandomStringUtils.randomAlphabetic(10);
    when(filterService.get(accountId, orgId, projId, randomFilterIdentifier, PIPELINEEXECUTION)).thenReturn(null);
    assertThatThrownBy(()
                           -> pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId,
                               randomFilterIdentifier, null, moduleName, searchTerm, null, true, false, true))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFormCriteriaOROperatorOnModulesFilterProperties() {
    // making a filterProperties object with a status value
    Criteria form = pmsExecutionServiceImpl.formCriteriaOROperatorOnModules(null, null, null, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .status(Collections.singletonList(ExecutionStatus.ABORTED))
            .build(),
        null);
    String documentString = "[Document{{status=Document{{$in=[ABORTED]}}}}]";
    assertThat(form.getCriteriaObject().get("$and").toString()).isEqualTo(documentString);

    // filterProperties and filterIdentifier as not null
    assertThatThrownBy(
        ()
            -> pmsExecutionServiceImpl.formCriteriaOROperatorOnModules(accountId, orgId, projId, pipelineIdList,
                PipelineExecutionFilterPropertiesDTO.builder()
                    .status(Collections.singletonList(ExecutionStatus.ABORTED))
                    .build(),
                "filterIdentifierDummy"))
        .isInstanceOf(InvalidRequestException.class);

    // giving random name to filterIdentifier and filterProperties as null
    String randomFilterIdentifier = RandomStringUtils.randomAlphabetic(10);
    when(filterService.get(accountId, orgId, projId, randomFilterIdentifier, PIPELINEEXECUTION)).thenReturn(null);
    assertThatThrownBy(()
                           -> pmsExecutionServiceImpl.formCriteriaOROperatorOnModules(
                               accountId, orgId, projId, pipelineIdList, null, randomFilterIdentifier))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFormCriteriaTimeRangeFilter() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);
    // Testing the execution in Time Range.
    Criteria form = pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .timeRange(TimeRange.builder().startTime(1651480019931L).endTime(1651480019931L).build())
            .build(),
        null, null, null, false, false, true);

    // Verify that the time range is present in criteria.
    assertEquals(form.getCriteriaObject().get("$and").toString(),
        "[Document{{startTs=Document{{$gte=1651480019931, $lte=1651480019931}}}}]");

    // ENdTime not provided in filter. Should throw exception.

    assertThatThrownBy(()
                           -> pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null,
                               PipelineExecutionFilterPropertiesDTO.builder()
                                   .timeRange(TimeRange.builder().startTime(1651480019931L).build())
                                   .build(),
                               null, null, null, false, false, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "startTime or endTime is not provided in TimeRange filter. Either add the missing field or remove the timeRange filter.");

    // StartTime not provided in filter. Should throw exception.
    assertThatThrownBy(()
                           -> pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null,
                               PipelineExecutionFilterPropertiesDTO.builder()
                                   .timeRange(TimeRange.builder().endTime(1651480019931L).build())
                                   .build(),
                               null, null, null, false, false, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "startTime or endTime is not provided in TimeRange filter. Either add the missing field or remove the timeRange filter.");

    form = pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null,
        PipelineExecutionFilterPropertiesDTO.builder().timeRange(TimeRange.builder().build()).build(), null, null, null,
        false, false, true);

    // TimeRange Filter should not be present in Criteria.
    assertNull(form.getCriteriaObject().get("$and"));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFormCriteriaOROperatorOnModulesTimeRangeFilter() {
    // Testing the execution in Time Range.
    Criteria form = pmsExecutionServiceImpl.formCriteriaOROperatorOnModules(accountId, orgId, projId, pipelineIdList,
        PipelineExecutionFilterPropertiesDTO.builder()
            .timeRange(TimeRange.builder().startTime(1651480019931L).endTime(1651480019931L).build())
            .build(),
        null);

    // Verify that the time range is present in criteria.
    assertEquals(form.getCriteriaObject().get("$and").toString(),
        "[Document{{startTs=Document{{$gte=1651480019931, $lte=1651480019931}}}}]");

    // ENdTime not provided in filter. Should throw exception.

    assertThatThrownBy(
        ()
            -> pmsExecutionServiceImpl.formCriteriaOROperatorOnModules(accountId, orgId, projId, pipelineIdList,
                PipelineExecutionFilterPropertiesDTO.builder()
                    .timeRange(TimeRange.builder().startTime(1651480019931L).build())
                    .build(),
                null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "startTime or endTime is not provided in TimeRange filter. Either add the missing field or remove the timeRange filter.");

    // StartTime not provided in filter. Should throw exception.
    assertThatThrownBy(
        ()
            -> pmsExecutionServiceImpl.formCriteriaOROperatorOnModules(accountId, orgId, projId, pipelineIdList,
                PipelineExecutionFilterPropertiesDTO.builder()
                    .timeRange(TimeRange.builder().endTime(1651480019931L).build())
                    .build(),
                null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "startTime or endTime is not provided in TimeRange filter. Either add the missing field or remove the timeRange filter.");

    form = pmsExecutionServiceImpl.formCriteriaOROperatorOnModules(accountId, orgId, projId, pipelineIdList,
        PipelineExecutionFilterPropertiesDTO.builder().timeRange(TimeRange.builder().build()).build(), null);

    // TimeRange Filter should not be present in Criteria.
    assertNull(form.getCriteriaObject().get("$and"));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFormCriteriaTagsFilter() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);
    // Testing the execution list by tags
    Criteria form = pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTags(List.of(
                NGTag.builder().key("key1").value("val1").build(), NGTag.builder().key("key1").value(null).build()))
            .build(),
        null, null, null, false, false, true);

    // Verify that tags are present in criteria.
    assertEquals(form.getCriteriaObject().get("$and").toString(),
        "[Document{{$and=[Document{{$or=[Document{{tags.key=Document{{$in=[key1]}}}}, Document{{tags.value=Document{{$in=[key1]}}}}]}}], tags=Document{{$in=[NGTag(key=key1, value=val1)]}}}}]");
    assertThatThrownBy(()
                           -> pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null,
                               PipelineExecutionFilterPropertiesDTO.builder()
                                   .pipelineTags(List.of(NGTag.builder().key("key1").value("val1").build(),
                                       NGTag.builder().key(null).value("val1").build()))
                                   .build(),
                               null, null, null, false, false, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Key in Pipeline Tags filter cannot be null");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFormCriteriaWithRepoNameAndNoBranch() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(false);
    setupGitContext(GitEntityInfo.builder().repoName("testRepo").build());
    // Testing the execution list by tags
    Criteria form = pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTags(Collections.singletonList(NGTag.builder().key("key1").value("val1").build()))
            .build(),
        null, null, null, false, false, true);

    // Verify that tags are present in criteria.
    assertTrue(
        (form.getCriteriaObject().get("$and").toString()).contains("Document{{entityGitDetails.repoName=testRepo}}"));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFormCriteriaWithEmptyRepoNameAndEmptyBranch() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(false);
    setupGitContext(GitEntityInfo.builder().repoName("").branch("").build());
    // Testing the execution list by tags
    Criteria form = pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTags(Collections.singletonList(NGTag.builder().key("key1").value("val1").build()))
            .build(),
        null, null, null, false, false, true);

    // Verify that tags are present in criteria.
    assertFalse((form.getCriteriaObject().get("$and").toString()).contains("Document{{entityGitDetails.repoName=}}"));
    assertFalse((form.getCriteriaObject().get("$and").toString()).contains("Document{{entityGitDetails.branch=}}"));
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }
}
