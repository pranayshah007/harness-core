/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdlicense.impl;

import static io.harness.cdlicense.bean.CgCdLicenseUsageConstants.CG_LICENSE_INSTANCE_LIMIT;
import static io.harness.cdlicense.bean.CgCdLicenseUsageConstants.INSTANCE_COUNT_PERCENTILE_DISC;
import static io.harness.cdlicense.bean.CgCdLicenseUsageConstants.TIME_PERIOD;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.stream.Collectors.toList;

import io.harness.cdlicense.bean.CgActiveServicesUsageInfo;
import io.harness.cdlicense.bean.CgServiceInstancesUsageInfo;
import io.harness.cdlicense.bean.CgServiceUsage;
import io.harness.cdlicense.bean.CgServiceUsage.CgServiceUsageBuilder;

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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@Singleton
public class CgCdLicenseUsageServiceImpl implements CgCdLicenseUsageService {
  private static final int SLICE_SIZE = 200;

  @Inject private CgCdLicenseUsageQueryHelper cgCdLicenseUsageQueryHelper;

  @Override
  public CgActiveServicesUsageInfo getActiveServiceLicenseUsage(String accountId) {
    List<String> serviceIdsFromDeployments =
        cgCdLicenseUsageQueryHelper.fetchDistinctSvcIdUsedInDeployments(accountId, TIME_PERIOD);
    Map<String, CgServiceUsage> percentileInstanceServicesUsageMap =
        queryPercentileInstanceForServices(accountId, serviceIdsFromDeployments);
    Map<String, Pair<String, String>> servicesDetails =
        cgCdLicenseUsageQueryHelper.fetchServicesNames(accountId, serviceIdsFromDeployments);
    Set<String> appIds = servicesDetails.values().parallelStream().map(Pair::getRight).collect(Collectors.toSet());
    Map<String, String> appNames = cgCdLicenseUsageQueryHelper.fetchAppNames(accountId, appIds);
    return buildCgActiveServicesUsageInfo(
        serviceIdsFromDeployments, percentileInstanceServicesUsageMap, servicesDetails, appNames);
  }

  @VisibleForTesting
  Map<String, CgServiceUsage> queryPercentileInstanceForServices(String accountId, List<String> svcIds) {
    if (isEmpty(svcIds)) {
      return Collections.emptyMap();
    }
    Map<String, CgServiceUsage> result = new HashMap<>();

    int fromIndex;
    int toIndex = 0;
    do {
      fromIndex = toIndex;
      toIndex = Math.min(svcIds.size(), toIndex + SLICE_SIZE);

      result.putAll(cgCdLicenseUsageQueryHelper.getPercentileInstanceForServices(
          accountId, svcIds.subList(fromIndex, toIndex), 30, INSTANCE_COUNT_PERCENTILE_DISC));

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

  private CgActiveServicesUsageInfo buildCgActiveServicesUsageInfo(@NonNull List<String> serviceIdsFromDeployments,
      @NonNull Map<String, CgServiceUsage> percentileInstanceServicesUsageMap,
      Map<String, Pair<String, String>> servicesNames, Map<String, String> appNames) {
    if (isEmpty(serviceIdsFromDeployments)) {
      return new CgActiveServicesUsageInfo();
    }

    List<CgServiceUsage> activeServiceUsageList =
        serviceIdsFromDeployments.stream()
            .map(serviceId
                -> buildActiveServiceUsageList(serviceId, percentileInstanceServicesUsageMap, servicesNames, appNames))
            .collect(toList());
    Long cumulativeServiceLicenseConsumed =
        activeServiceUsageList.stream().map(CgServiceUsage::getLicensesUsed).reduce(0L, Long::sum);
    return CgActiveServicesUsageInfo.builder()
        .activeServiceUsage(activeServiceUsageList)
        .serviceLicenseConsumed(cumulativeServiceLicenseConsumed)
        .servicesConsumed(serviceIdsFromDeployments.size())
        .build();
  }

  private CgServiceUsage buildActiveServiceUsageList(@NonNull String serviceId,
      @NonNull Map<String, CgServiceUsage> percentileInstanceServicesUsageMap,
      Map<String, Pair<String, String>> servicesNames, Map<String, String> appNames) {
    CgServiceUsageBuilder cgServiceUsageBuilder = CgServiceUsage.builder().serviceId(serviceId);
    if (servicesNames.containsKey(serviceId)) {
      cgServiceUsageBuilder.name(servicesNames.get(serviceId).getLeft());
      cgServiceUsageBuilder.appId(servicesNames.get(serviceId).getRight());
      cgServiceUsageBuilder.appName(appNames.getOrDefault(servicesNames.get(serviceId).getRight(), StringUtils.EMPTY));
    }
    if (percentileInstanceServicesUsageMap.containsKey(serviceId)) {
      cgServiceUsageBuilder.instanceCount(percentileInstanceServicesUsageMap.get(serviceId).getInstanceCount());
      cgServiceUsageBuilder.licensesUsed(
          computeServiceLicenseUsed(percentileInstanceServicesUsageMap.get(serviceId).getInstanceCount()));
    } else {
      cgServiceUsageBuilder.instanceCount(0);
      cgServiceUsageBuilder.licensesUsed(1);
    }
    return cgServiceUsageBuilder.build();
  }

  private long computeServiceLicenseUsed(long instanceCount) {
    return instanceCount == 0L ? 1L : (instanceCount + CG_LICENSE_INSTANCE_LIMIT - 1) / CG_LICENSE_INSTANCE_LIMIT;
  }
}
