/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import static io.harness.ccm.commons.constants.CloudProvider.AWS;
import static io.harness.ccm.commons.constants.CloudProvider.AZURE;
import static io.harness.ccm.commons.constants.CloudProvider.GCP;
import static io.harness.ccm.commons.constants.CloudProvider.ON_PREM;

import io.harness.batch.processing.service.intfc.CloudProviderService;
import io.harness.ccm.commons.constants.CloudProvider;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;
import software.wings.settings.SettingValue;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CloudProviderServiceImpl implements CloudProviderService {
  private final CloudToHarnessMappingService cloudToHarnessMappingService;

  private static final String GCP_SEARCH_STRING = "gce:";
  private static final String AWS_SEARCH_STRING = "aws:";
  private static final String AZURE_SEARCH_STRING = "azure:";
  private static final String IBM_SEARCH_STRING = "ibm:";

  private static final CloudProvider DEFAULT_CLOUD_PROVIDER = ON_PREM;

  @Value
  @AllArgsConstructor
  private static class CacheKey {
    private String cloudProviderId;
    private String providerId;
  }

  private final Cache<CacheKey, CloudProvider> cloudProviderInfoCache =
      Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).build();

  @Autowired
  public CloudProviderServiceImpl(CloudToHarnessMappingService cloudToHarnessMappingService) {
    this.cloudToHarnessMappingService = cloudToHarnessMappingService;
  }

  @Override
  public CloudProvider getK8SCloudProvider(String cloudProviderId, String providerId) {
    if (null == providerId) {
      return DEFAULT_CLOUD_PROVIDER;
    }
    return cloudProviderInfoCache.get(new CacheKey(cloudProviderId, providerId),
        key -> getK8SCloudProviderFromProviderId(key.cloudProviderId, key.providerId));
  }

  @Override
  public List<CloudProvider> getFirstClassSupportedCloudProviders() {
    return ImmutableList.of(AWS, AZURE, GCP);
  }

  private CloudProvider getK8SCloudProviderFromProviderId(String cloudProviderId, String providerId) {
    CloudProvider cloudProvider = getCloudProviderForK8SCluster(providerId);
    Optional<SettingAttribute> settingAttributeMaybe =
        cloudToHarnessMappingService.getSettingAttribute(cloudProviderId);
    if (settingAttributeMaybe.isPresent()) {
      SettingAttribute settingAttribute = settingAttributeMaybe.get();
      if (settingAttribute.getCategory() == SettingCategory.CLOUD_PROVIDER) {
        SettingValue value = settingAttribute.getValue();
        String cloudProviderType = value.getType();
        switch (cloudProviderType) {
          case "KUBERNETES_CLUSTER":
            return getCloudProviderForK8SCluster(providerId);
          default:
            return DEFAULT_CLOUD_PROVIDER;
        }
      }
    }
    return cloudProvider;
  }

  private CloudProvider getCloudProviderForK8SCluster(String providerId) {
    if (checkGCPCloudProvider(providerId)) {
      return GCP;
    } else if (checkAWSCloudProvider(providerId)) {
      return AWS;
    } else if (checkAzureCloudProvider(providerId)) {
      return AZURE;
    } else if (checkIbmCloudProvider(providerId)) {
      return CloudProvider.IBM;
    }
    return DEFAULT_CLOUD_PROVIDER;
  }

  private boolean checkGCPCloudProvider(String providerId) {
    return providerId.contains(GCP_SEARCH_STRING);
  }

  private boolean checkAWSCloudProvider(String providerId) {
    return providerId.contains(AWS_SEARCH_STRING);
  }

  private boolean checkAzureCloudProvider(String providerId) {
    return providerId.contains(AZURE_SEARCH_STRING);
  }

  private boolean checkIbmCloudProvider(String providerId) {
    return providerId.contains(IBM_SEARCH_STRING);
  }
}
