package io.harness.batch.processing.pricing.gcp.bigquery;

import io.harness.batch.processing.pricing.data.VMInstanceBillingData;
import io.harness.ccm.commons.entities.CEMetadataRecord.CEMetadataRecordBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface BigQueryHelperService {
  Map<String, VMInstanceBillingData> getAwsEC2BillingData(
      List<String> resourceId, Instant startTime, Instant endTime, String dataSetId);

  Map<String, VMInstanceBillingData> getEKSFargateBillingData(
      List<String> resourceIds, Instant startTime, Instant endTime, String dataSetId);

  Map<String, VMInstanceBillingData> getAwsBillingData(Instant startTime, Instant endTime, String dataSetId);

  void updateCloudProviderMetaData(String accountId, CEMetadataRecordBuilder ceMetadataRecordBuilder);

  Map<String, VMInstanceBillingData> getAzureVMBillingData(
      List<String> resourceIds, Instant startTime, Instant endTime, String dataSetId);
}
