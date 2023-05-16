/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdlicense.impl;

import static io.harness.cdlicense.bean.CgCdLicenseUsageConstants.INSTANCE_COUNT_PERCENTILE_DISC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.join;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.cdlicense.bean.CgActiveServicesUsageInfo;
import io.harness.cdlicense.bean.CgServiceInstancesUsageInfo;
import io.harness.cdlicense.bean.CgServiceUsage;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

@Slf4j
@Singleton
public class CgCdLicenseUsageServiceImpl implements CgCdLicenseUsageService {
  private static final int SLICE_SIZE = 200;
  private static final int ACTIVE_SERVICES_LICENSE_USAGE_REPORT_PERIOD_DAYS = 30;

  @Inject private CgCdLicenseUsageQueryHelper cgCdLicenseUsageQueryHelper;

  @Override
  public CgActiveServicesUsageInfo getActiveServiceLicenseUsage(String accountId) {
    log.info("Start fetching deployed services for accountId: {}, for last {} days", accountId,
        ACTIVE_SERVICES_LICENSE_USAGE_REPORT_PERIOD_DAYS);
    long fetchActiveServiceStartTime = System.currentTimeMillis();
    List<CgServiceUsage> activeServices =
        cgCdLicenseUsageQueryHelper.getDeployedServices(accountId, ACTIVE_SERVICES_LICENSE_USAGE_REPORT_PERIOD_DAYS);
    log.info(
        "Deployed services fetched successfully for accountId: {}, time taken in ms: {}, total number of active services: {}",
        accountId, System.currentTimeMillis() - fetchActiveServiceStartTime, activeServices.size());
    if (isEmpty(activeServices)) {
      return new CgActiveServicesUsageInfo();
    }
    updateServiceNameIfMissing(accountId, activeServices);
    updateAppNameIfMissing(accountId, activeServices);

    log.info("Start fetching services percentile instances for accountId: {}", accountId);
    long fetchServicesPercentileInstancesStartTime = System.currentTimeMillis();
    Map<String, Pair<Long, Integer>> servicesPercentileInstances =
        queryPercentileInstanceForServices(accountId, getActiveServiceIds(activeServices));
    log.info("Services percentile instances fetched successfully for accountId: {}, time taken in ms: {}", accountId,
        System.currentTimeMillis() - fetchServicesPercentileInstancesStartTime);
    updateActiveServiceWithInstanceCountAndLicenseUsage(activeServices, servicesPercentileInstances);

    return buildCgActiveServicesUsageInfo(activeServices);
  }

  @VisibleForTesting
  Map<String, Pair<Long, Integer>> queryPercentileInstanceForServices(String accountId, List<String> svcIds) {
    if (isEmpty(svcIds)) {
      return Collections.emptyMap();
    }
    Map<String, Pair<Long, Integer>> result = new HashMap<>();

    int fromIndex;
    int toIndex = 0;
    do {
      fromIndex = toIndex;
      toIndex = Math.min(svcIds.size(), toIndex + SLICE_SIZE);

      result.putAll(cgCdLicenseUsageQueryHelper.getServicesPercentileInstanceCountAndLicenseUsage(accountId,
          svcIds.subList(fromIndex, toIndex), ACTIVE_SERVICES_LICENSE_USAGE_REPORT_PERIOD_DAYS,
          INSTANCE_COUNT_PERCENTILE_DISC));

    } while (toIndex < svcIds.size());

    return result;
  }

  @Override
  public CgServiceInstancesUsageInfo getServiceInstancesUsage(String accountId) {
    return new CgServiceInstancesUsageInfo(cgCdLicenseUsageQueryHelper.fetchServiceInstancesOver30Days(accountId));
  }

  @Override
  public int getActiveServiceInTimePeriod(String accountId, int timePeriodInDays) {
    if (timePeriodInDays <= 0) {
      return 0;
    }

    List<String> activeServices =
        cgCdLicenseUsageQueryHelper.fetchDistinctSvcIdUsedInDeployments(accountId, timePeriodInDays);
    return CollectionUtils.isEmpty(activeServices) ? 0 : activeServices.size();
  }

  private void updateServiceNameIfMissing(String accountId, List<CgServiceUsage> activeServices) {
    List<CgServiceUsage> servicesEmptyName =
        activeServices.stream()
            .filter(activeServiceDetails -> isEmpty(activeServiceDetails.getName()))
            .collect(toList());
    List<String> serviceIdsEmptyName = servicesEmptyName.stream().map(CgServiceUsage::getServiceId).collect(toList());
    log.info("Updating missing service names for {} services, accountId: {}", serviceIdsEmptyName.size(), accountId);
    Map<String, Pair<String, String>> servicesDetails =
        cgCdLicenseUsageQueryHelper.fetchServicesNames(accountId, serviceIdsEmptyName);

    servicesEmptyName.forEach(activeServiceDetails -> {
      Pair<String, String> serviceNameAndAppId = servicesDetails.get(activeServiceDetails.getServiceId());
      // Used EMPTY value instead of null because of UI backward compatibility. UI logic is around string.
      String serviceName = serviceNameAndAppId != null ? serviceNameAndAppId.getLeft() : EMPTY;
      activeServiceDetails.setName(serviceName);
    });
  }

  private void updateAppNameIfMissing(String accountId, List<CgServiceUsage> activeServices) {
    List<CgServiceUsage> servicesEmptyAppName =
        activeServices.stream()
            .filter(activeServiceDetails -> isEmpty(activeServiceDetails.getAppName()))
            .collect(toList());
    Set<String> appIds = servicesEmptyAppName.stream().map(CgServiceUsage::getAppId).collect(Collectors.toSet());
    log.info("Updating missing app names, accountId: {}, appIds: {}", accountId, join(",", appIds));
    Map<String, String> appIdsAndNames = cgCdLicenseUsageQueryHelper.fetchAppNames(accountId, appIds);

    servicesEmptyAppName.forEach(activeServiceDetails -> {
      String appName = appIdsAndNames.get(activeServiceDetails.getAppId());
      // Used EMPTY value instead of null because of UI backward compatibility. UI logic is around string.
      String fixedAppName = isNotEmpty(appName) ? appName : EMPTY;
      activeServiceDetails.setAppName(fixedAppName);
    });
  }

  @NotNull
  private List<String> getActiveServiceIds(List<CgServiceUsage> activeServices) {
    return activeServices.stream().map(CgServiceUsage::getServiceId).collect(toList());
  }

  private void updateActiveServiceWithInstanceCountAndLicenseUsage(
      List<CgServiceUsage> activeServices, Map<String, Pair<Long, Integer>> activeServicesLicenseUsage) {
    if (isEmpty(activeServicesLicenseUsage)) {
      return;
    }

    activeServices.forEach(activeServiceDetails -> {
      Pair<Long, Integer> instanceCountAndLicenseUsage =
          activeServicesLicenseUsage.get(activeServiceDetails.getServiceId());
      if (instanceCountAndLicenseUsage != null) {
        activeServiceDetails.setInstanceCount(instanceCountAndLicenseUsage.getLeft());
        activeServiceDetails.setLicensesUsed(instanceCountAndLicenseUsage.getRight());
      }
    });
  }

  private CgActiveServicesUsageInfo buildCgActiveServicesUsageInfo(
      @NonNull List<CgServiceUsage> activeServiceUsageList) {
    Long cumulativeServiceLicenseConsumed =
        activeServiceUsageList.stream().map(CgServiceUsage::getLicensesUsed).reduce(0L, Long::sum);
    return CgActiveServicesUsageInfo.builder()
        .activeServiceUsage(activeServiceUsageList)
        .serviceLicenseConsumed(cumulativeServiceLicenseConsumed)
        .servicesConsumed(activeServiceUsageList.size())
        .build();
  }
}
