/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.vmpricing;

import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.pricing.dto.cloudinfo.ProductDetails;

import com.google.common.collect.ImmutableSet;

public interface VMPricingService {
  static String getSimilarRegionIfNotSupportedByBanzai(String region) {
    // these region names are present in Azure cloud Provider Only
    if (ImmutableSet.of("switzerlandnorth", "switzerlandwest", "germanywestcentral", "norwayeast", "uaenorth")
            .contains(region)) {
      return "uksouth";
    }

    return region;
  }

  ProductDetails getComputeVMPricingInfo(String instanceType, String region, CloudProvider cloudProvider);

  EcsFargatePricingInfo getFargatePricingInfo(String region);
}
