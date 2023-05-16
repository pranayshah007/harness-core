/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.JAMIE;
import static io.harness.telemetry.Destination.ALL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.core.ci.services.CIOverviewDashboardService;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.repositories.CITelemetryStatusRepository;
import io.harness.repositories.ModuleLicenseRepository;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class CiTelemetryPublisherTest extends CategoryTest {
  CiTelemetryPublisher telemetryPublisher;
  CIOverviewDashboardService ciOverviewDashboardService = mock(CIOverviewDashboardService.class);
  TelemetryReporter telemetryReporter = mock(TelemetryReporter.class);
  AccountUtils accountUtils = mock(AccountUtils.class);
  CITelemetryStatusRepository ciTelemetryStatusRepository = mock(CITelemetryStatusRepository.class);
  ModuleLicenseRepository moduleLicenseRepository = mock(ModuleLicenseRepository.class);

  @Before
  public void setUp() {
    telemetryPublisher = spy(CiTelemetryPublisher.class);
    telemetryPublisher.ciOverviewDashboardService = ciOverviewDashboardService;
    telemetryPublisher.telemetryReporter = telemetryReporter;
    telemetryPublisher.accountUtils = accountUtils;
    telemetryPublisher.ciTelemetryStatusRepository = ciTelemetryStatusRepository;
    telemetryPublisher.moduleLicenseRepository = moduleLicenseRepository;
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testRecordTelemetry() {
    long activeCommitters = 20L;
    doReturn(activeCommitters).when(ciOverviewDashboardService).getActiveCommitterCount(any());
    doReturn(true).when(ciTelemetryStatusRepository).updateTimestampIfOlderThan(anyString(), anyLong(), anyLong());
    ModuleLicense moduleLicense = null;
    List<ModuleLicense> moduleLicenses = Collections.singletonList(moduleLicense);
    doReturn(moduleLicenses).when(moduleLicenseRepository).findByAccountIdentifierAndModuleType(any(), any());
    List<String> accountList = new ArrayList<>();
    accountList.add("acc1");
    accountList.add("acc2");
    doReturn(accountList).when(accountUtils).getAllNGAccountIds();
    HashMap<String, Object> firstAccountExpectedMap = new HashMap<>();
    firstAccountExpectedMap.put("group_type", "Account");
    firstAccountExpectedMap.put("group_id", "acc1");
    firstAccountExpectedMap.put("ci_license_developers_used", activeCommitters);
    firstAccountExpectedMap.put("account_deploy_type", null);

    HashMap<String, Object> secondAccountExpectedMap = new HashMap<>();
    secondAccountExpectedMap.put("group_type", "Account");
    secondAccountExpectedMap.put("group_id", "acc2");
    secondAccountExpectedMap.put("ci_license_developers_used", activeCommitters);
    secondAccountExpectedMap.put("account_deploy_type", null);

    telemetryPublisher.recordTelemetry();
    verify(telemetryReporter, times(1))
        .sendGroupEvent("acc1", null, firstAccountExpectedMap, Collections.singletonMap(ALL, true),
            TelemetryOption.builder().sendForCommunity(true).build());
    verify(telemetryReporter, times(1))
        .sendGroupEvent("acc2", null, secondAccountExpectedMap, Collections.singletonMap(ALL, true),
            TelemetryOption.builder().sendForCommunity(true).build());
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testRecordTelemetryNoActiveCI() {
    long activeCommitters = 0L;
    doReturn(activeCommitters).when(ciOverviewDashboardService).getActiveCommitterCount(any());
    doReturn(true).when(ciTelemetryStatusRepository).updateTimestampIfOlderThan(anyString(), anyLong(), anyLong());
    List<ModuleLicense> moduleLicenses = Collections.emptyList();
    doReturn(moduleLicenses).when(moduleLicenseRepository).findByAccountIdentifierAndModuleType(any(), any());
    List<String> accountList = new ArrayList<>();
    accountList.add("acc1");
    accountList.add("acc2");
    doReturn(accountList).when(accountUtils).getAllNGAccountIds();
    HashMap<String, Object> firstAccountExpectedMap = new HashMap<>();
    firstAccountExpectedMap.put("group_type", "Account");
    firstAccountExpectedMap.put("group_id", "acc1");
    firstAccountExpectedMap.put("ci_license_developers_used", null);
    firstAccountExpectedMap.put("account_deploy_type", null);

    HashMap<String, Object> secondAccountExpectedMap = new HashMap<>();
    secondAccountExpectedMap.put("group_type", "Account");
    secondAccountExpectedMap.put("group_id", "acc2");
    secondAccountExpectedMap.put("ci_license_developers_used", null);
    secondAccountExpectedMap.put("account_deploy_type", null);

    telemetryPublisher.recordTelemetry();
    verify(telemetryReporter, times(1))
        .sendGroupEvent("acc1", null, firstAccountExpectedMap, Collections.singletonMap(ALL, true),
            TelemetryOption.builder().sendForCommunity(true).build());
    verify(telemetryReporter, times(1))
        .sendGroupEvent("acc2", null, secondAccountExpectedMap, Collections.singletonMap(ALL, true),
            TelemetryOption.builder().sendForCommunity(true).build());
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testRecordTelemetryNoActiveCIButHaveExecHistory() {
    long activeCommitters = 20L;
    doReturn(activeCommitters).when(ciOverviewDashboardService).getActiveCommitterCount(any());
    doReturn(true).when(ciTelemetryStatusRepository).updateTimestampIfOlderThan(anyString(), anyLong(), anyLong());
    List<ModuleLicense> moduleLicenses = Collections.emptyList();
    doReturn(moduleLicenses).when(moduleLicenseRepository).findByAccountIdentifierAndModuleType(any(), any());
    List<String> accountList = new ArrayList<>();
    accountList.add("acc1");
    accountList.add("acc2");
    doReturn(accountList).when(accountUtils).getAllNGAccountIds();
    HashMap<String, Object> firstAccountExpectedMap = new HashMap<>();
    firstAccountExpectedMap.put("group_type", "Account");
    firstAccountExpectedMap.put("group_id", "acc1");
    firstAccountExpectedMap.put("ci_license_developers_used", activeCommitters);
    firstAccountExpectedMap.put("account_deploy_type", null);

    HashMap<String, Object> secondAccountExpectedMap = new HashMap<>();
    secondAccountExpectedMap.put("group_type", "Account");
    secondAccountExpectedMap.put("group_id", "acc2");
    secondAccountExpectedMap.put("ci_license_developers_used", activeCommitters);
    secondAccountExpectedMap.put("account_deploy_type", null);

    telemetryPublisher.recordTelemetry();
    verify(telemetryReporter, times(1))
        .sendGroupEvent("acc1", null, firstAccountExpectedMap, Collections.singletonMap(ALL, true),
            TelemetryOption.builder().sendForCommunity(true).build());
    verify(telemetryReporter, times(1))
        .sendGroupEvent("acc2", null, secondAccountExpectedMap, Collections.singletonMap(ALL, true),
            TelemetryOption.builder().sendForCommunity(true).build());
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testRecordSkipTelemetry() {
    long activeCommitters = 20L;
    doReturn(activeCommitters).when(ciOverviewDashboardService).getActiveCommitterCount(any());
    doReturn(false).when(ciTelemetryStatusRepository).updateTimestampIfOlderThan(anyString(), anyLong(), anyLong());
    ModuleLicense moduleLicense = null;
    List<ModuleLicense> moduleLicenses = Collections.singletonList(moduleLicense);
    doReturn(moduleLicenses).when(moduleLicenseRepository).findByAccountIdentifierAndModuleType(any(), any());
    List<String> accountList = new ArrayList<>();
    accountList.add("acc1");
    accountList.add("acc2");
    doReturn(accountList).when(accountUtils).getAllNGAccountIds();

    telemetryPublisher.recordTelemetry();
    verify(telemetryReporter, times(0)).sendGroupEvent(anyString(), anyString(), any(), anyMap(), any());
  }
}
