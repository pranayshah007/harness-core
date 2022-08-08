/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.rule.OwnerRule.ABHINAV;

import static software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class WorkflowExecutionTimeFilterHelperTest extends CategoryTest {
  private final Long THREE_MONTHS_MILLIS = 7889400000L;
  private final Long FOUR_MONTHS_MILLIS = 10519200000L;
  private final Long TWO_MONTHS_MILLIS = 5259600000L;
  private final Long SIX_MONTHS_MILLIS = 15778800000L;

  private final String ACCOUNT_ID = "accountId";

  @Mock FeatureFlagService featureFlagService;
  @InjectMocks @Spy WorkflowExecutionTimeFilterHelper workflowExecutionTimeFilterHelper;

  @Before
  public void runBeforeTest() {
    MockitoAnnotations.initMocks(this);
    doReturn(true).when(featureFlagService).isEnabled(any(), any());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testWorkflowExecutionFilter() {
    final PageRequest pageRequest =
        aPageRequest()
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.GT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - SIX_MONTHS_MILLIS})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .build();
    doReturn(pageRequest).when(workflowExecutionTimeFilterHelper).populatePageRequestFilters(any());
    workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequest, ACCOUNT_ID);
    assertThat(pageRequest.getFilters().size()).isEqualTo(2);

    final PageRequest pageRequest1 =
        aPageRequest()
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.GT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - THREE_MONTHS_MILLIS})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .build();
    doReturn(pageRequest1).when(workflowExecutionTimeFilterHelper).populatePageRequestFilters(any());
    workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequest1, ACCOUNT_ID);

    final PageRequest pageRequest2 =
        aPageRequest()
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.GT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - SIX_MONTHS_MILLIS})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .build();
    doReturn(pageRequest2).when(workflowExecutionTimeFilterHelper).populatePageRequestFilters(any());
    workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequest2, ACCOUNT_ID);
    assertThat(pageRequest2.getFilters().size()).isEqualTo(2);

    final PageRequest pageRequest3 =
        aPageRequest()
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.GT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - SIX_MONTHS_MILLIS})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.LT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - FOUR_MONTHS_MILLIS})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .build();
    doReturn(pageRequest3).when(workflowExecutionTimeFilterHelper).populatePageRequestFilters(any());
    workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequest3, ACCOUNT_ID);

    final PageRequest pageRequest4 =
        aPageRequest()
            .addFilter(SearchFilter.builder()
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .op(SearchFilter.Operator.GT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - SIX_MONTHS_MILLIS})
                           .build())
            .addFilter(SearchFilter.builder()
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .op(SearchFilter.Operator.LT)
                           .fieldValues(new Object[] {System.currentTimeMillis()})
                           .build())
            .build();
    doReturn(pageRequest4).when(workflowExecutionTimeFilterHelper).populatePageRequestFilters(any());
    assertThatThrownBy(
        () -> workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequest4, ACCOUNT_ID));

    final PageRequest pageRequest5 =
        aPageRequest()
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.LT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - SIX_MONTHS_MILLIS})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .build();
    doReturn(pageRequest5).when(workflowExecutionTimeFilterHelper).populatePageRequestFilters(any());
    workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequest5, ACCOUNT_ID);
    assertThat(pageRequest5.getFilters().size()).isEqualTo(2);
  }
}
