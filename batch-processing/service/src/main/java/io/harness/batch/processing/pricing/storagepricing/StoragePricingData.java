/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.storagepricing;

import static io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData;

import io.harness.batch.processing.pricing.PricingSource;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.perpetualtask.k8s.watch.PVInfo.PVType;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StoragePricingData {
  public static final double GBMONTH_TO_MBHOUR = 1024D * 30D * 24D;
  /**
   * This function parses the type of volume from the metaData, like AWS_EBS, Azure_Disk, GKE_PD and then based on
   * storageClass params (which is dumped into instance metaData) it determines the variation in that specific volume
   * and then returned price based on corresponding cloud provider PricingData
   * @param metaData
   * @return Double
   */
  public static Double getPricePerMbPerHour(final Map<String, String> metaData) {
    PVType pvDiskType = getVolumeType(metaData);

    if (PVType.PV_TYPE_GCE_PERSISTENT_DISK == pvDiskType) {
      return getPriceRateForGoogle(metaData);
    } else if (PVType.PV_TYPE_AWS_EBS == pvDiskType) {
      // TODO: Implement AWS EBS pricing here
      return io.harness.batch.processing.pricing.storagepricing.StorageCustomPricingProvider.Unknown.DEFAULT
                 .getDefaultPrice()
          / GBMONTH_TO_MBHOUR;
    } else if (PVType.PV_TYPE_AZURE_DISK == pvDiskType) {
      // TODO: Implement Azure Disk pricing here
      return io.harness.batch.processing.pricing.storagepricing.StorageCustomPricingProvider.Unknown.DEFAULT
                 .getDefaultPrice()
          / GBMONTH_TO_MBHOUR;
    } else if (PVType.PV_TYPE_NFS == pvDiskType) {
      return io.harness.batch.processing.pricing.storagepricing.StorageCustomPricingProvider.NFS.DEFAULT
                 .getDefaultPrice()
          / GBMONTH_TO_MBHOUR;
    }
    return StorageCustomPricingProvider.Unknown.DEFAULT.getDefaultPrice() / GBMONTH_TO_MBHOUR;
  }

  private static Double getPriceRateForGoogle(final Map<String, String> metaData) {
    final String REGIONAL_PD = "regional-pd";
    final String REPLICATION_TYPE = "replication-type";

    boolean isRegional = REGIONAL_PD.equals(getValueForKeyFromInstanceMetaData(REPLICATION_TYPE, metaData));
    String region = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.REGION, metaData);
    String type = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.GCE_STORAGE_CLASS, metaData);
    if (type == null) {
      type =
          io.harness.batch.processing.pricing.storagepricing.GoogleStoragePricingData.Type.PD_STANDARD.getFieldName();
    }
    if (region == null) {
      region = io.harness.batch.processing.pricing.storagepricing.GoogleStoragePricingData.getDefaultRegion();
    }
    return io.harness.batch.processing.pricing.storagepricing.GoogleStoragePricingData.getPricePerGBMonth(
               GoogleStoragePricingData.Type.get(type), region, isRegional)
        / GBMONTH_TO_MBHOUR;
  }

  public static PVType getVolumeType(final Map<String, String> metaData) {
    String pvDiskType = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.PV_TYPE, metaData);
    if (pvDiskType != null) {
      return PVType.valueOf(pvDiskType);
    }
    return PVType.PV_TYPE_UNSPECIFIED;
  }

  public static PricingSource getPricingSource(final Map<String, String> metaData) {
    PVType pvDiskType = getVolumeType(metaData);
    switch (pvDiskType) {
      case PV_TYPE_GCE_PERSISTENT_DISK:
        return PricingSource.PUBLIC_API;
      case PV_TYPE_AWS_EBS:
      case PV_TYPE_AZURE_DISK:
      case PV_TYPE_NFS:
      case PV_TYPE_UNSPECIFIED:
        return PricingSource.HARDCODED;
      default:
        return PricingSource.HARDCODED;
    }
  }
}
