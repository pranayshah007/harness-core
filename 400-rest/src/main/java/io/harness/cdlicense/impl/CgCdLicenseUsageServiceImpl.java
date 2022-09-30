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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CgCdLicenseUsageServiceImpl implements CgCdLicenseUsageService {
  @Inject private CgCdLicenseUsageQueryHelper cgCdLicenseUsageQueryHelper;

  @Override
  public CgActiveServicesUsageInfo getActiveServiceLicenseUsage(String accountId) {
    List<String> serviceIdsFromDeployments =
        cgCdLicenseUsageQueryHelper.fetchDistinctSvcIdUsedInDeployments(accountId, TIME_PERIOD);
    Map<String, CgServiceUsage> percentileInstanceServicesUsageMap =
        cgCdLicenseUsageQueryHelper.getPercentileInstanceForServices(
            accountId, serviceIdsFromDeployments, 30, INSTANCE_COUNT_PERCENTILE_DISC);
    Map<String, String> servicesNames =
        cgCdLicenseUsageQueryHelper.fetchServicesNames(accountId, serviceIdsFromDeployments);
    return buildCgActiveServicesUsageInfo(serviceIdsFromDeployments, percentileInstanceServicesUsageMap, servicesNames);
  }

  @Override
  public CgServiceInstancesUsageInfo getServiceInstancesUsage(String accountId) {
    return new CgServiceInstancesUsageInfo(cgCdLicenseUsageQueryHelper.fetchServiceInstancesOver30Days(accountId));
  }

  private CgActiveServicesUsageInfo buildCgActiveServicesUsageInfo(@NonNull List<String> serviceIdsFromDeployments,
      @NonNull Map<String, CgServiceUsage> percentileInstanceServicesUsageMap,
      @NonNull Map<String, String> servicesNames) {
    if (isEmpty(serviceIdsFromDeployments)) {
      return new CgActiveServicesUsageInfo();
    }

    List<CgServiceUsage> activeServiceUsageList =
        serviceIdsFromDeployments.stream()
            .map(serviceId -> buildActiveServiceUsageList(serviceId, percentileInstanceServicesUsageMap, servicesNames))
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
      @NonNull Map<String, String> servicesNames) {
    CgServiceUsageBuilder cgServiceUsageBuilder = CgServiceUsage.builder().serviceId(serviceId);
    if (servicesNames.containsKey(serviceId)) {
      cgServiceUsageBuilder.name(servicesNames.get(serviceId));
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
