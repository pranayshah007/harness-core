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
import io.harness.ng.core.dto.AccountDTO;
import io.harness.remote.client.RestClientUtils;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import retrofit2.Call;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.JAMIE;
import static io.harness.telemetry.Destination.ALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@OwnedBy(CI)
@PrepareForTest({RestClientUtils.class})
public class CiTelemetryPublisherTest extends CategoryTest {
    @InjectMocks CiTelemetryPublisher telemetryPublisher;
    @Mock
    CIOverviewDashboardService ciOverviewDashboardService;
    @Mock TelemetryReporter telemetryReporter;
    @Mock AccountClient accountClient;

    String acc = "acc";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @Owner(developers = JAMIE)
    @Category(UnitTests.class)
    public void testRecordTelemetry() {
        List<AccountDTO> accounts = Collections.singletonList(AccountDTO.builder().identifier(acc).build());

        Call<RestResponse<List<AccountDTO>>> requestCall = mock(Call.class);
        doReturn(requestCall).when(accountClient).getAllAccounts();

        long activeCommitters = 20L;
        doReturn(JAMIE).when(ciOverviewDashboardService).getActiveCommitterCount(any());

        HashMap<String, Object> map = new HashMap<>();
        map.put("group_type", "Account");
        map.put("group_id", acc);
        map.put("ci_license_developers_used", activeCommitters);

        try (MockedStatic<RestClientUtils> mockStatic = Mockito.mockStatic(RestClientUtils.class)) {
            mockStatic.when(() -> RestClientUtils.getResponse(requestCall)).thenReturn(accounts);
            telemetryPublisher.recordTelemetry();
            verify(telemetryReporter, times(1))
                    .sendGroupEvent(acc, null, map, Collections.singletonMap(ALL, true),
                            TelemetryOption.builder().sendForCommunity(true).build());
        }
    }

    @Test
    @Owner(developers = JAMIE)
    @Category(UnitTests.class)
    public void testGetAccountId() {
        List<AccountDTO> accounts = Collections.singletonList(AccountDTO.builder().identifier(acc).build());

        Call<RestResponse<List<AccountDTO>>> requestCall = mock(Call.class);
        doReturn(requestCall).when(accountClient).getAllAccounts();
        try (MockedStatic<RestClientUtils> mockStatic = Mockito.mockStatic(RestClientUtils.class)) {
            mockStatic.when(() -> RestClientUtils.getResponse(requestCall)).thenReturn(accounts);
            String accountId = telemetryPublisher.getAccountId();
            assertThat(accountId).isEqualTo(acc);
        }
    }
}
