/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.utils;

import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.core.beans.change.MSHealthReport;
import io.harness.cvng.core.beans.monitoredService.RiskData;
import io.harness.rule.Owner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NotificationRuleCommonUtilsTest extends CvNextGenTestBase {
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void getServiceHealthMessageForReportWhenSwitchCurrentHealthScoreGetRiskStatusCaseHEALTHY() {
    RiskData currentHealthScoreMock = mock(RiskData.class);
    doReturn(Risk.HEALTHY).when(currentHealthScoreMock).getRiskStatus();
    doReturn(95).when(currentHealthScoreMock).getHealthScore();
    String result = NotificationRuleCommonUtils.getServiceHealthMessageForReport(currentHealthScoreMock);
    assertThat(result).isEqualTo("The service health remained healthy with a score of 95%");
    verify(currentHealthScoreMock).getRiskStatus();
    verify(currentHealthScoreMock).getHealthScore();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void getServiceHealthMessageForReportWhenSwitchCurrentHealthScoreGetRiskStatusCaseNEED_ATTENTION() {
    RiskData currentHealthScoreMock = mock(RiskData.class);
    doReturn(Risk.NEED_ATTENTION).when(currentHealthScoreMock).getRiskStatus();
    doReturn(50).when(currentHealthScoreMock).getHealthScore();
    String result = NotificationRuleCommonUtils.getServiceHealthMessageForReport(currentHealthScoreMock);
    assertThat(result).isEqualTo("The service health needs attention. It has a score of 50%");
    verify(currentHealthScoreMock).getRiskStatus();
    verify(currentHealthScoreMock).getHealthScore();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void getServiceHealthMessageForReportWhenSwitchCurrentHealthScoreGetRiskStatusCaseNO_DATA() {
    RiskData currentHealthScoreMock = mock(RiskData.class);
    doReturn(Risk.NO_DATA).when(currentHealthScoreMock).getRiskStatus();
    String result = NotificationRuleCommonUtils.getServiceHealthMessageForReport(currentHealthScoreMock);
    assertThat(result).isEqualTo("No health score data available for the period");
    verify(currentHealthScoreMock).getRiskStatus();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void getServiceHealthMessageForReportWhenSwitchCurrentHealthScoreGetRiskStatusCaseOBSERVE() {
    RiskData currentHealthScoreMock = mock(RiskData.class);
    doReturn(Risk.OBSERVE).when(currentHealthScoreMock).getRiskStatus();
    doReturn(80).when(currentHealthScoreMock).getHealthScore();
    String result = NotificationRuleCommonUtils.getServiceHealthMessageForReport(currentHealthScoreMock);
    assertThat(result).isEqualTo("The service health needs to be observed. It has a score of 80%");
    verify(currentHealthScoreMock).getRiskStatus();
    verify(currentHealthScoreMock).getHealthScore();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void getServiceHealthMessageForReportWhenSwitchCurrentHealthScoreGetRiskStatusCaseUNHEALTHY() {
    RiskData currentHealthScoreMock = mock(RiskData.class);
    doReturn(Risk.UNHEALTHY).when(currentHealthScoreMock).getRiskStatus();
    doReturn(50).when(currentHealthScoreMock).getHealthScore();
    String result = NotificationRuleCommonUtils.getServiceHealthMessageForReport(currentHealthScoreMock);
    assertThat(result).isEqualTo("The service health remained unhealthy with a score of 50%");
    verify(currentHealthScoreMock).getRiskStatus();
    verify(currentHealthScoreMock).getHealthScore();
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void getSloPerformanceDetailsForReportWhenAssociatedSLOsDetailsSizeEquals0() {
    List<MSHealthReport.AssociatedSLOsDetails> mSHealthReportAssociatedSLOsDetailsList = new ArrayList<>();
    Instant instantMock = mock(Instant.class);
    String result = NotificationRuleCommonUtils.getSloPerformanceDetailsForReport(
        mSHealthReportAssociatedSLOsDetailsList, instantMock, "baseUrl1", "SLOPerformanceSection1");
    assertThat(result).isEqualTo("No SLO has been associated with this monitored service.\n");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSLOSummaryForReportWithNoSLOs() {
    List<MSHealthReport.AssociatedSLOsDetails> mSHealthReportAssociatedSLOsDetailsList = new ArrayList<>();
    String summary = NotificationRuleCommonUtils.getSLOSummaryForReport(mSHealthReportAssociatedSLOsDetailsList);
    assertThat(summary).isEqualTo("No SLO has been associated with this monitored service.\n");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSLOSummaryForReportWithAtleastOneRemainingIntact() {
    List<MSHealthReport.AssociatedSLOsDetails> mSHealthReportAssociatedSLOsDetailsList = new ArrayList<>();
    mSHealthReportAssociatedSLOsDetailsList.add(
        MSHealthReport.AssociatedSLOsDetails.builder().errorBudgetRemaining(20.0).build());
    String summary = NotificationRuleCommonUtils.getSLOSummaryForReport(mSHealthReportAssociatedSLOsDetailsList);
    assertThat(summary).isEqualTo(
        "1 SLO configured for this service successfully maintained their error budget intact.\n");
    mSHealthReportAssociatedSLOsDetailsList.add(
        MSHealthReport.AssociatedSLOsDetails.builder().errorBudgetRemaining(20.0).build());
    summary = NotificationRuleCommonUtils.getSLOSummaryForReport(mSHealthReportAssociatedSLOsDetailsList);
    assertThat(summary).isEqualTo(
        "All 2 SLOs configured for this service successfully maintained their error budgets intact.\n");
    mSHealthReportAssociatedSLOsDetailsList.add(
        MSHealthReport.AssociatedSLOsDetails.builder().errorBudgetRemaining(-20.0).build());
    summary = NotificationRuleCommonUtils.getSLOSummaryForReport(mSHealthReportAssociatedSLOsDetailsList);
    assertThat(summary).isEqualTo(
        "Out of the 3 SLOs configured for this service, 1 of them have exceeded their respective error budget.\n");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSLOSummaryForReportWithAtleastOneFailedToMaintainErrorBudget() {
    List<MSHealthReport.AssociatedSLOsDetails> mSHealthReportAssociatedSLOsDetailsList = new ArrayList<>();
    mSHealthReportAssociatedSLOsDetailsList.add(
        MSHealthReport.AssociatedSLOsDetails.builder().errorBudgetRemaining(-20.0).build());
    String summary = NotificationRuleCommonUtils.getSLOSummaryForReport(mSHealthReportAssociatedSLOsDetailsList);
    assertThat(summary).isEqualTo(
        "1 SLO configured for this service couldn't maintain their respective error budget.\n");
    mSHealthReportAssociatedSLOsDetailsList.add(
        MSHealthReport.AssociatedSLOsDetails.builder().errorBudgetRemaining(-20.0).build());
    summary = NotificationRuleCommonUtils.getSLOSummaryForReport(mSHealthReportAssociatedSLOsDetailsList);
    assertThat(summary).isEqualTo(
        "All 2 SLOs configured for this service couldn't maintain their respective error budgets.\n");
    mSHealthReportAssociatedSLOsDetailsList.add(
        MSHealthReport.AssociatedSLOsDetails.builder().errorBudgetRemaining(20.0).build());
    summary = NotificationRuleCommonUtils.getSLOSummaryForReport(mSHealthReportAssociatedSLOsDetailsList);
    assertThat(summary).isEqualTo(
        "Out of the 3 SLOs configured for this service, 2 of them have exceeded their respective error budgets.\n");
  }
}
