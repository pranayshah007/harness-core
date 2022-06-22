/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry;

import io.harness.CategoryTest;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.core.ci.services.CIOverviewDashboardService;
import io.harness.rule.Owner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Collections;
import java.util.HashMap;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.JAMIE;
import static io.harness.telemetry.Destination.ALL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@OwnedBy(CI)
public class CiTelemetryPublisherTest extends CategoryTest {
    CiTelemetryPublisher telemetryPublisher;
    CIOverviewDashboardService ciOverviewDashboardService = mock(CIOverviewDashboardService.class);
    TelemetryReporter telemetryReporter = mock(TelemetryReporter.class);
    AccountClient accountClient = mock(AccountClient.class);

    String ACCOUNT_ID = "acc";

    @Before
    public void setUp() {
        telemetryPublisher = spy(CiTelemetryPublisher.class);
        telemetryPublisher.ciOverviewDashboardService = ciOverviewDashboardService;
        telemetryPublisher.telemetryReporter = telemetryReporter;
        telemetryPublisher.accountClient = accountClient;
    }

    @Test
    @Owner(developers = JAMIE)
    @Category(UnitTests.class)
    public void testRecordTelemetry() {
        long activeCommitters = 20L;
        doReturn(activeCommitters).when(ciOverviewDashboardService).getActiveCommitterCount(any());
        doReturn(ACCOUNT_ID).when(telemetryPublisher).getAccountId();
        HashMap<String, Object> map = new HashMap<>();
        map.put("group_type", "Account");
        map.put("group_id", ACCOUNT_ID);
        map.put("ci_license_developers_used", activeCommitters);

        telemetryPublisher.recordTelemetry();
        verify(telemetryReporter, times(1))
                .sendGroupEvent(ACCOUNT_ID, null, map, Collections.singletonMap(ALL, true),
                        TelemetryOption.builder().sendForCommunity(true).build());
    }
}
