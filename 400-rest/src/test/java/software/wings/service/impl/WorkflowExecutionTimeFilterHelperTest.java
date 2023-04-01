/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.FeatureName.ENABLE_DEFAULT_TIMEFRAME_IN_DEPLOYMENTS;
import static io.harness.beans.FeatureName.SPG_ENFORCE_TIME_RANGE_DEPLOYMENTS_WITHOUT_APP_ID;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.LUCAS_SALES;

import static software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter;
import io.harness.beans.SearchFilter.Operator;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class WorkflowExecutionTimeFilterHelperTest extends CategoryTest {
  private static final Duration ONE_MONTH_DURATION = Duration.ofDays(30);
  private static final Duration THREE_MONTHS_DURATION = Duration.ofDays(90);
  private static final Duration FOUR_MONTHS_DURATION = Duration.ofDays(120);
  private static final Duration SIX_MONTHS_DURATION = Duration.ofDays(180);
  private static final String ACCOUNT_ID = "accountId";
  @Mock FeatureFlagService featureFlagService;
  @InjectMocks @Spy WorkflowExecutionTimeFilterHelper workflowExecutionTimeFilterHelper;

  @Before
  public void runBeforeTest() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testWorkflowExecutionFilter() {
    doReturn(true).when(featureFlagService).isEnabled(eq(ENABLE_DEFAULT_TIMEFRAME_IN_DEPLOYMENTS), any());
    doReturn(false).when(featureFlagService).isEnabled(eq(SPG_ENFORCE_TIME_RANGE_DEPLOYMENTS_WITHOUT_APP_ID), any());
    PageRequest pageRequestSent = aPageRequest().build();

    final PageRequest pageRequest =
        aPageRequest()
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.GT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - SIX_MONTHS_DURATION.toMillis()})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .build();
    doReturn(pageRequest).when(workflowExecutionTimeFilterHelper).populatePageRequestFilters(any());
    workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequestSent, ACCOUNT_ID);
    assertThat(pageRequestSent.getFilters().size()).isEqualTo(1);
    SearchFilter filter = (SearchFilter) pageRequestSent.getFilters().get(0);
    assertThat(filter.getOp()).isEqualTo(Operator.LT);

    pageRequestSent = aPageRequest().build();
    final PageRequest pageRequest1 =
        aPageRequest()
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.GT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - THREE_MONTHS_DURATION.toMillis()})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .build();
    doReturn(pageRequest1).when(workflowExecutionTimeFilterHelper).populatePageRequestFilters(any());
    workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequestSent, ACCOUNT_ID);

    pageRequestSent = aPageRequest().build();
    final PageRequest pageRequest2 =
        aPageRequest()
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.GT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - SIX_MONTHS_DURATION.toMillis()})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .build();
    doReturn(pageRequest2).when(workflowExecutionTimeFilterHelper).populatePageRequestFilters(any());
    workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequestSent, ACCOUNT_ID);
    assertThat(pageRequestSent.getFilters().size()).isEqualTo(1);
    SearchFilter filter2 = (SearchFilter) pageRequestSent.getFilters().get(0);
    assertThat(filter2.getOp()).isEqualTo(Operator.LT);

    pageRequestSent = aPageRequest().build();
    final PageRequest pageRequest3 =
        aPageRequest()
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.GT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - SIX_MONTHS_DURATION.toMillis()})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.LT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - FOUR_MONTHS_DURATION.toMillis()})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .build();
    doReturn(pageRequest3).when(workflowExecutionTimeFilterHelper).populatePageRequestFilters(any());
    workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequestSent, ACCOUNT_ID);

    pageRequestSent = aPageRequest().build();
    final PageRequest pageRequest5 =
        aPageRequest()
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.LT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - SIX_MONTHS_DURATION.toMillis()})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .build();
    doReturn(pageRequest5).when(workflowExecutionTimeFilterHelper).populatePageRequestFilters(any());
    workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequestSent, ACCOUNT_ID);
    SearchFilter filter5 = (SearchFilter) pageRequestSent.getFilters().get(0);
    assertThat(filter5.getOp()).isEqualTo(Operator.GT);
    assertThat(pageRequestSent.getFilters().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testEnforceTimeRangeEnabled_shouldFailWithoutAppId() {
    doReturn(true).when(featureFlagService).isEnabled(eq(ENABLE_DEFAULT_TIMEFRAME_IN_DEPLOYMENTS), any());
    doReturn(true).when(featureFlagService).isEnabled(eq(SPG_ENFORCE_TIME_RANGE_DEPLOYMENTS_WITHOUT_APP_ID), any());
    final PageRequest pageRequestSent = aPageRequest().build();

    final PageRequest pageRequest =
        aPageRequest()
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.GT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - SIX_MONTHS_DURATION.toMillis()})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.LT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - FOUR_MONTHS_DURATION.toMillis()})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .build();
    doReturn(pageRequest).when(workflowExecutionTimeFilterHelper).populatePageRequestFilters(any());
    assertThatThrownBy(
        () -> workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequestSent, ACCOUNT_ID));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testEnforceTimeRangeDisabled_shouldFailRangeMaximum() {
    doReturn(true).when(featureFlagService).isEnabled(eq(ENABLE_DEFAULT_TIMEFRAME_IN_DEPLOYMENTS), any());
    doReturn(false).when(featureFlagService).isEnabled(eq(SPG_ENFORCE_TIME_RANGE_DEPLOYMENTS_WITHOUT_APP_ID), any());
    final PageRequest pageRequestSent = aPageRequest().build();

    final PageRequest pageRequest =
        aPageRequest()
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.GT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - SIX_MONTHS_DURATION.toMillis()})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.LT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - ONE_MONTH_DURATION.toMillis()})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .build();
    doReturn(pageRequest).when(workflowExecutionTimeFilterHelper).populatePageRequestFilters(any());
    assertThatThrownBy(
        () -> workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequestSent, ACCOUNT_ID));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testEnforceTimeRangeDisabled_shouldSucceedWithoutAppId() {
    doReturn(true).when(featureFlagService).isEnabled(eq(ENABLE_DEFAULT_TIMEFRAME_IN_DEPLOYMENTS), any());
    doReturn(false).when(featureFlagService).isEnabled(eq(SPG_ENFORCE_TIME_RANGE_DEPLOYMENTS_WITHOUT_APP_ID), any());
    final PageRequest pageRequestSent = aPageRequest().build();

    final PageRequest pageRequest =
        aPageRequest()
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.GT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - SIX_MONTHS_DURATION.toMillis()})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.LT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - FOUR_MONTHS_DURATION.toMillis()})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .build();
    doReturn(pageRequest).when(workflowExecutionTimeFilterHelper).populatePageRequestFilters(any());
    workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequestSent, ACCOUNT_ID);
    assertThat(pageRequestSent.getFilters().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testEnforceTimeRangeEnabled_shouldWorkWithAppId() {
    doReturn(true).when(featureFlagService).isEnabled(eq(ENABLE_DEFAULT_TIMEFRAME_IN_DEPLOYMENTS), any());
    doReturn(true).when(featureFlagService).isEnabled(eq(SPG_ENFORCE_TIME_RANGE_DEPLOYMENTS_WITHOUT_APP_ID), any());
    final PageRequest pageRequestSent = aPageRequest().build();

    final PageRequest pageRequest =
        aPageRequest()
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.GT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - SIX_MONTHS_DURATION.toMillis()})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.LT)
                           .fieldValues(new Object[] {System.currentTimeMillis() - FOUR_MONTHS_DURATION.toMillis()})
                           .fieldName(WorkflowExecutionKeys.createdAt)
                           .build())
            .addFilter(SearchFilter.builder()
                           .op(SearchFilter.Operator.EQ)
                           .fieldValues(new Object[] {"appId"})
                           .fieldName(WorkflowExecutionKeys.appId)
                           .build())
            .build();
    doReturn(pageRequest).when(workflowExecutionTimeFilterHelper).populatePageRequestFilters(any());
    workflowExecutionTimeFilterHelper.updatePageRequestForTimeFilter(pageRequestSent, ACCOUNT_ID);
    assertThat(pageRequestSent.getFilters().size()).isEqualTo(0);
  }
}
