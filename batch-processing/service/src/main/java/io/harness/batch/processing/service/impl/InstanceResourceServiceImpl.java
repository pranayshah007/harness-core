/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import io.harness.batch.processing.pricing.service.support.GCPCustomInstanceDetailProvider;
import io.harness.batch.processing.pricing.vmpricing.VMPricingService;
import io.harness.batch.processing.service.intfc.InstanceResourceService;
import io.harness.batch.processing.tasklet.util.K8sResourceUtils;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.pricing.dto.cloudinfo.ProductDetails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InstanceResourceServiceImpl implements InstanceResourceService {
  @Autowired private VMPricingService vmPricingService;

  @Override
  public Resource getComputeVMResource(String instanceType, String region, CloudProvider cloudProvider) {
    double cpu, memory;
    if (GCPCustomInstanceDetailProvider.isCustomGCPInstance(instanceType, cloudProvider)) {
      Resource gcpInstanceResource = GCPCustomInstanceDetailProvider.getCustomGcpInstanceResource(instanceType);
      cpu = gcpInstanceResource.getCpuUnits() / 1024.0;
      memory = gcpInstanceResource.getMemoryMb() / 1024.0;
    } else {
      ProductDetails computeVMPricingInfo =
          vmPricingService.getComputeVMPricingInfo(instanceType, region, cloudProvider);
      if (null == computeVMPricingInfo) {
        log.info("Instance detail for null resource {} {} {}", instanceType, region, cloudProvider);
        return null;
      }
      cpu = computeVMPricingInfo.getCpusPerVm();
      memory = computeVMPricingInfo.getMemPerVm();
    }
    return K8sResourceUtils.getResource(cpu, memory);
  }
}
