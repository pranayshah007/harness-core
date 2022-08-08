/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.quickFilters;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.filter.FilterType.PIPELINEEXECUTION;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.service.FilterService;
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

  @InjectMocks private PMSExecutionServiceImpl pmsExecutionServiceImpl;

  String accountId = "acc";
  String orgId = "org";
  String projId = "pro";
  String pipelineId = "pip";
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
    // testing pipelineIdentifier,status and myDeployments values
    Criteria form = pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null, null, moduleName,
        searchTerm, Arrays.asList(ExecutionStatus.FAILED, ExecutionStatus.ABORTED), true, false, null, true);

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
        moduleName, searchTerm, Collections.singletonList(ExecutionStatus.FAILED), false, false, null, true);
    // allDeployment -> myDeployments = false
    assertThat(allDeploymentsForm.getCriteriaObject().containsKey("executionTriggerInfo.triggeredBy")).isEqualTo(false);
    assertThat(allDeploymentsForm.getCriteriaObject().containsKey("executionTriggerInfo.triggerType")).isEqualTo(false);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFormCriteriaQuickFiltersWithBothStatusFilters() {
    // testing pipelineIdentifier,status and myDeployments values
    Criteria form = pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .status(Arrays.asList(ExecutionStatus.ABORTED, ExecutionStatus.PAUSED))
            .build(),
        null, null, Arrays.asList(ExecutionStatus.FAILED, ExecutionStatus.ABORTED), false, false, null, true);

    // status
    assertThat(form.getCriteriaObject().get("status").toString()).isEqualTo("Document{{$in=[FAILED, ABORTED]}}");
    assertThat(form.getCriteriaObject().get("$and").toString())
        .isEqualTo("[Document{{status=Document{{$in=[ABORTED, PAUSED]}}}}, Document{{}}, Document{{}}, Document{{}}]");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFormCriteriaFilterProperties() {
    // making a filterProperties object with a status value
    Criteria form = pmsExecutionServiceImpl.formCriteria(null, null, null, null, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .status(Collections.singletonList(ExecutionStatus.ABORTED))
            .build(),
        null, null, null, true, false, null, true);
    String documentString = "[Document{{status=Document{{$in=[ABORTED]}}}}, Document{{}}, Document{{}}, Document{{}}]";
    assertThat(form.getCriteriaObject().get("$and").toString()).isEqualTo(documentString);

    // filterProperties and filterIdentifier as not null
    assertThatThrownBy(
        ()
            -> pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, "filterIdentifierDummy",
                PipelineExecutionFilterPropertiesDTO.builder()
                    .status(Collections.singletonList(ExecutionStatus.ABORTED))
                    .build(),
                moduleName, searchTerm, null, true, false, null, true))
        .isInstanceOf(InvalidRequestException.class);

    // giving random name to filterIdentifier and filterProperties as null
    String randomFilterIdentifier = RandomStringUtils.randomAlphabetic(10);
    when(filterService.get(accountId, orgId, projId, randomFilterIdentifier, PIPELINEEXECUTION)).thenReturn(null);
    assertThatThrownBy(()
                           -> pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId,
                               randomFilterIdentifier, null, moduleName, searchTerm, null, true, false, null, true))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFormCriteriaTimeRangeFilter() {
    // Testing the execution in Time Range.
    Criteria form = pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .timeRange(TimeRange.builder().startTime(1651480019931L).endTime(1651480019931L).build())
            .build(),
        null, null, null, false, false, null, true);

    // Verify that the time range is present in criteria.
    assertEquals(form.getCriteriaObject().get("$and").toString(),
        "[Document{{createdAt=Document{{$gte=1651480019931, $lte=1651480019931}}}}, Document{{}}, Document{{}}, Document{{}}]");

    // ENdTime not provided in filter. Should throw exception.

    assertThatThrownBy(()
                           -> pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null,
                               PipelineExecutionFilterPropertiesDTO.builder()
                                   .timeRange(TimeRange.builder().startTime(1651480019931L).build())
                                   .build(),
                               null, null, null, false, false, null, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "startTime or endTime is not provided in TimeRange filter. Either add the missing field or remove the timeRange filter.");

    // StartTime not provided in filter. Should throw exception.
    assertThatThrownBy(()
                           -> pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null,
                               PipelineExecutionFilterPropertiesDTO.builder()
                                   .timeRange(TimeRange.builder().endTime(1651480019931L).build())
                                   .build(),
                               null, null, null, false, false, null, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "startTime or endTime is not provided in TimeRange filter. Either add the missing field or remove the timeRange filter.");

    form = pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null,
        PipelineExecutionFilterPropertiesDTO.builder().timeRange(TimeRange.builder().build()).build(), null, null, null,
        false, false, null, true);

    // TimeRange Filter should not be present in Criteria.
    assertEquals(
        form.getCriteriaObject().get("$and").toString(), "[Document{{}}, Document{{}}, Document{{}}, Document{{}}]");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFormCriteriaTagsFilter() {
    // Testing the execution list by tags
    Criteria form = pmsExecutionServiceImpl.formCriteria(accountId, orgId, projId, pipelineId, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTags(Collections.singletonList(NGTag.builder().key("key1").value("val1").build()))
            .build(),
        null, null, null, false, false, null, true);

    // Verify that tags are present in criteria.
    assertEquals(form.getCriteriaObject().get("$and").toString(),
        "[Document{{$and=[Document{{$or=[Document{{tags.key=Document{{$in=[key1, val1]}}}}, Document{{tags.value=Document{{$in=[key1, val1]}}}}]}}]}}, Document{{}}, Document{{}}, Document{{}}]");
  }
}
