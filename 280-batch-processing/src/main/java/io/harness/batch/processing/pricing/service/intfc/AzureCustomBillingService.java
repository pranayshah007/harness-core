package io.harness.batch.processing.pricing.service.intfc;

import io.harness.batch.processing.pricing.data.VMInstanceBillingData;
import io.harness.ccm.commons.entities.InstanceData;

import java.time.Instant;
import java.util.List;

public interface AzureCustomBillingService {
  VMInstanceBillingData getComputeVMPricingInfo(InstanceData instanceData, Instant startTime, Instant endTime);

  void updateAzureVMBillingDataCache(List<String> resourceIds, Instant startTime, Instant endTime, String dataSetId);
}
