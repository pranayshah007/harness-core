/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdlicense.impl;

import static io.harness.cdlicense.bean.CgCdLicenseUsageConstants.TIME_PERIOD;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.PERCENTILE;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdlicense.bean.CgActiveServicesUsageInfo;
import io.harness.cdlicense.bean.CgServiceUsage;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CgCdLicenseUsageServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";

  @Mock CgCdLicenseUsageQueryHelper cdLicenseUsageQueryHelper;
  @Mock FeatureFlagService featureFlagService;
  @InjectMocks @Inject private CgCdLicenseUsageServiceImpl cgCdLicenseUsageService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetActiveServiceLicenseUsageNoServiceDeployed() {
    when(cdLicenseUsageQueryHelper.fetchDistinctSvcIdUsedInDeployments(anyString(), anyInt())).thenReturn(emptyList());
    when(cdLicenseUsageQueryHelper.getServicesPercentileInstanceCountAndLicenseUsage(
             anyString(), anyList(), anyInt(), anyDouble()))
        .thenCallRealMethod();
    when(cdLicenseUsageQueryHelper.fetchServicesNames(anyString(), anyList())).thenCallRealMethod();
    when(cdLicenseUsageQueryHelper.fetchAppNames(anyString(), anySet())).thenCallRealMethod();
    CgActiveServicesUsageInfo activeServiceLicenseUsage =
        cgCdLicenseUsageService.getActiveServiceLicenseUsage(ACCOUNT_ID);
    assertThat(activeServiceLicenseUsage.getActiveServiceUsage()).isEmpty();
    assertThat(activeServiceLicenseUsage.getServiceLicenseConsumed()).isZero();
    assertThat(activeServiceLicenseUsage.getServicesConsumed()).isZero();
  }

  @Test
  @Owner(developers = {TATHAGAT, IVAN})
  @Category(UnitTests.class)
  public void testGetActiveServiceLicenseUsageNoInstanceFound() {
    when(cdLicenseUsageQueryHelper.getDeployedServices(anyString(), anyInt())).thenReturn(getActiveServices());
    when(cdLicenseUsageQueryHelper.getServicesPercentileInstanceCountAndLicenseUsage(
             anyString(), anyList(), anyInt(), anyDouble()))
        .thenReturn(emptyMap());

    HashMap<String, Pair<String, String>> svcNames = new HashMap<>();
    svcNames.put("svc1", Pair.of("name1", "app1"));
    svcNames.put("svc2", Pair.of("name2", "app2"));
    when(cdLicenseUsageQueryHelper.fetchServicesNames(anyString(), anyList())).thenReturn(svcNames);

    Map<String, String> appIdsAndNames = new HashMap<>();
    appIdsAndNames.put("appId3", "appName3");
    when(cdLicenseUsageQueryHelper.fetchAppNames(anyString(), anySet())).thenReturn(appIdsAndNames);

    CgActiveServicesUsageInfo activeServiceLicenseUsage =
        cgCdLicenseUsageService.getActiveServiceLicenseUsage(ACCOUNT_ID);
    assertThat(activeServiceLicenseUsage.getActiveServiceUsage()).isNotEmpty();
    assertServiceIdAndName(activeServiceLicenseUsage);
    assertThat(activeServiceLicenseUsage.getServiceLicenseConsumed()).isEqualTo(3);
    assertThat(activeServiceLicenseUsage.getServicesConsumed()).isEqualTo(3);
  }

  @Test
  @Owner(developers = {TATHAGAT, IVAN})
  @Category(UnitTests.class)
  public void testGetActiveServiceLicenseUsage() {
    when(cdLicenseUsageQueryHelper.getDeployedServices(anyString(), anyInt())).thenReturn(getActiveServices());

    Map<String, Pair<String, String>> servicesDetails = new HashMap<>();
    servicesDetails.put("svc1", Pair.of("name1", "appId1"));
    servicesDetails.put("svc2", Pair.of("name2", "appId2"));
    when(cdLicenseUsageQueryHelper.fetchServicesNames(anyString(), anyList())).thenReturn(servicesDetails);

    Map<String, String> appIdsAndNames = new HashMap<>();
    appIdsAndNames.put("appId3", "appName3");
    when(cdLicenseUsageQueryHelper.fetchAppNames(anyString(), anySet())).thenReturn(appIdsAndNames);

    Map<String, Pair<Long, Integer>> cgServiceUsageMap = new HashMap<>();
    cgServiceUsageMap.put("svc1", Pair.of(21L, 2));
    cgServiceUsageMap.put("svc2", Pair.of(41L, 3));
    when(cdLicenseUsageQueryHelper.getServicesPercentileInstanceCountAndLicenseUsage(
             anyString(), anyList(), anyInt(), anyDouble()))
        .thenReturn(cgServiceUsageMap);

    CgActiveServicesUsageInfo activeServiceLicenseUsage =
        cgCdLicenseUsageService.getActiveServiceLicenseUsage(ACCOUNT_ID);
    assertThat(activeServiceLicenseUsage.getActiveServiceUsage()).isNotEmpty();
    assertServiceIdAndName(activeServiceLicenseUsage);
    assertThat(activeServiceLicenseUsage.getActiveServiceUsage()
                   .stream()
                   .map(CgServiceUsage::getInstanceCount)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(21L, 41L, 0L);

    assertThat(activeServiceLicenseUsage.getServiceLicenseConsumed()).isEqualTo(6);
    assertThat(activeServiceLicenseUsage.getServicesConsumed()).isEqualTo(3);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNoSliceWhenQueryPercentileInstanceForServices() {
    List<String> svcIds = createServiceIds(3);
    cgCdLicenseUsageService.queryPercentileInstanceForServices(ACCOUNT_ID, svcIds);

    verify(cdLicenseUsageQueryHelper)
        .getServicesPercentileInstanceCountAndLicenseUsage(ACCOUNT_ID, svcIds, TIME_PERIOD, PERCENTILE);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldUseTwoSlicesWhenQueryPercentileInstanceForServices() {
    List<String> svcIds = createServiceIds(210);
    final List<String> slice1 = svcIds.subList(0, 200);
    final List<String> slice2 = svcIds.subList(200, svcIds.size());

    cgCdLicenseUsageService.queryPercentileInstanceForServices(ACCOUNT_ID, svcIds);

    verify(cdLicenseUsageQueryHelper, times(2))
        .getServicesPercentileInstanceCountAndLicenseUsage(eq(ACCOUNT_ID), any(), eq(TIME_PERIOD), eq(PERCENTILE));
    verify(cdLicenseUsageQueryHelper)
        .getServicesPercentileInstanceCountAndLicenseUsage(ACCOUNT_ID, slice1, TIME_PERIOD, PERCENTILE);
    verify(cdLicenseUsageQueryHelper)
        .getServicesPercentileInstanceCountAndLicenseUsage(ACCOUNT_ID, slice2, TIME_PERIOD, PERCENTILE);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldUseThreeSlicesWhenQueryPercentileInstanceForServices() {
    List<String> svcIds = createServiceIds(420);
    final List<String> slice1 = svcIds.subList(0, 200);
    final List<String> slice2 = svcIds.subList(200, 400);
    final List<String> slice3 = svcIds.subList(400, svcIds.size());

    cgCdLicenseUsageService.queryPercentileInstanceForServices(ACCOUNT_ID, svcIds);

    verify(cdLicenseUsageQueryHelper, times(3))
        .getServicesPercentileInstanceCountAndLicenseUsage(eq(ACCOUNT_ID), any(), eq(TIME_PERIOD), eq(PERCENTILE));
    verify(cdLicenseUsageQueryHelper)
        .getServicesPercentileInstanceCountAndLicenseUsage(ACCOUNT_ID, slice1, TIME_PERIOD, PERCENTILE);
    verify(cdLicenseUsageQueryHelper)
        .getServicesPercentileInstanceCountAndLicenseUsage(ACCOUNT_ID, slice2, TIME_PERIOD, PERCENTILE);
    verify(cdLicenseUsageQueryHelper)
        .getServicesPercentileInstanceCountAndLicenseUsage(ACCOUNT_ID, slice3, TIME_PERIOD, PERCENTILE);
  }

  private List<String> createServiceIds(int max) {
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < max; i++) {
      ids.add("S" + i);
    }
    return ids;
  }

  private void assertServiceIdAndName(CgActiveServicesUsageInfo activeServiceLicenseUsage) {
    assertThat(activeServiceLicenseUsage.getActiveServiceUsage()
                   .stream()
                   .map(CgServiceUsage::getServiceId)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("svc1", "svc2", "svc3");
    assertThat(activeServiceLicenseUsage.getActiveServiceUsage()
                   .stream()
                   .map(CgServiceUsage::getName)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("name1", "name2", "name3");
    assertThat(activeServiceLicenseUsage.getActiveServiceUsage()
                   .stream()
                   .map(CgServiceUsage::getAppName)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("appName1", "appName2", "appName3");
  }

  @NotNull
  private List<CgServiceUsage> getActiveServices() {
    return List.of(CgServiceUsage.builder()
                       .serviceId("svc1")
                       .licensesUsed(1)
                       .instanceCount(0)
                       .appId("appId1")
                       .appName("appName1")
                       .build(),
        CgServiceUsage.builder()
            .serviceId("svc2")
            .licensesUsed(1)
            .instanceCount(0)
            .appId("appId2")
            .appName("appName2")
            .build(),
        CgServiceUsage.builder()
            .serviceId("svc3")
            .name("name3")
            .licensesUsed(1)
            .instanceCount(0)
            .appId("appId3")
            .build());
  }
}
