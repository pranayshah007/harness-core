/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.utils;

import static io.harness.ccm.commons.entities.CCMField.ANOMALOUS_SPEND;

import io.harness.ccm.commons.constants.AnomalyFieldConstants;
import io.harness.ccm.commons.constants.ViewFieldConstants;
import io.harness.ccm.commons.entities.CCMSort;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.commons.entities.anomaly.AnomalyQueryDTO;
import io.harness.ccm.commons.entities.anomaly.EntityInfo;
import io.harness.timescaledb.tables.pojos.Anomalies;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AnomalyUtils {
  private static final String SEPARATOR = "/";
  private static final String RELATIVE_TIME_TEMPLATE = "%s %s ago";
  public static final Integer DEFAULT_LIMIT = 1000;
  public static final Integer DEFAULT_OFFSET = 0;

  public static String getRelativeTime(long anomalyTime, String template) {
    long currentTime = System.currentTimeMillis();
    long timeDiff = currentTime - anomalyTime;
    long days = timeDiff / TimeUtils.ONE_DAY_MILLIS;
    if (days != 0) {
      return days > 1 ? String.format(template, days, "days") : String.format(template, 1, "day");
    }
    long hours = timeDiff / TimeUtils.ONE_HOUR_MILLIS;
    if (hours != 0) {
      return hours > 1 ? String.format(template, days, "hours") : String.format(template, 1, "hour");
    }
    long minutes = timeDiff / TimeUtils.ONE_MINUTE_MILLIS;
    return minutes > 1 ? String.format(template, minutes, "minutes") : String.format(template, 1, "minute");
  }

  public static Double getAnomalyTrend(Double actualCost, Double expectedCost) {
    return expectedCost != 0 ? Math.round(((actualCost - expectedCost) / expectedCost) * 10000D) / 100D : 0;
  }

  public static Double getRoundedOffCost(Double cost) {
    return Math.round(cost * 100D) / 100D;
  }

  public static String getResourceName(Anomalies anomaly) {
    StringBuilder builder = new StringBuilder();
    if (anomaly.getClustername() != null) {
      builder.append(anomaly.getClustername());
      if (anomaly.getNamespace() != null) {
        builder.append(SEPARATOR);
        builder.append(anomaly.getNamespace());
      }
      if (anomaly.getWorkloadname() != null) {
        builder.append(SEPARATOR);
        builder.append(anomaly.getWorkloadname());
      }
    } else if (anomaly.getGcpproject() != null) {
      builder.append(anomaly.getGcpproject());
      if (anomaly.getGcpproduct() != null) {
        builder.append(SEPARATOR);
        builder.append(anomaly.getGcpproduct());
      }
      if (anomaly.getGcpskudescription() != null) {
        builder.append(SEPARATOR);
        builder.append(anomaly.getGcpskudescription());
      }
    } else if (anomaly.getAwsaccount() != null) {
      builder.append(anomaly.getAwsaccount());
      if (anomaly.getAwsservice() != null) {
        builder.append(SEPARATOR);
        builder.append(anomaly.getAwsservice());
      }
      if (anomaly.getAwsusagetype() != null) {
        builder.append(SEPARATOR);
        builder.append(anomaly.getAwsusagetype());
      }
    } else if (anomaly.getAzuresubscriptionguid() != null) {
      builder.append(anomaly.getAzuresubscriptionguid());
      if (anomaly.getAzureresourcegroup() != null) {
        builder.append(SEPARATOR);
        builder.append(anomaly.getAzureresourcegroup());
      }
      if (anomaly.getAzuremetercategory() != null) {
        builder.append(SEPARATOR);
        builder.append(anomaly.getAzuremetercategory());
      }
    }
    return builder.toString();
  }

  public static String getResourceInfo(Anomalies anomaly) {
    StringBuilder builder = new StringBuilder();
    if (anomaly.getClustername() != null) {
      builder.append(ViewFieldConstants.CLUSTER_NAME_FIELD_ID);
      if (anomaly.getNamespace() != null) {
        builder.append(SEPARATOR);
        builder.append(ViewFieldConstants.NAMESPACE_FIELD_ID);
      }
      if (anomaly.getWorkloadname() != null) {
        builder.append(SEPARATOR);
        builder.append(ViewFieldConstants.WORKLOAD_NAME_FIELD_ID);
      }
      return builder.toString();
    } else if (anomaly.getGcpproject() != null) {
      builder.append(ViewFieldConstants.GCP_PROJECT_FIELD_ID);
      if (anomaly.getGcpproduct() != null) {
        builder.append(SEPARATOR);
        builder.append(ViewFieldConstants.GCP_PRODUCT_FIELD_ID);
      }
      if (anomaly.getGcpskuid() != null) {
        builder.append(SEPARATOR);
        builder.append(ViewFieldConstants.GCP_SKU_DESCRIPTION_FIELD_ID);
      }
      return builder.toString();
    } else if (anomaly.getAwsaccount() != null) {
      builder.append(ViewFieldConstants.AWS_ACCOUNT_FIELD_ID);
      if (anomaly.getAwsservice() != null) {
        builder.append(SEPARATOR);
        builder.append(ViewFieldConstants.AWS_SERVICE_FIELD_ID);
      }
      if (anomaly.getAwsusagetype() != null) {
        builder.append(SEPARATOR);
        builder.append(ViewFieldConstants.AWS_USAGE_TYPE_ID);
      }
      return builder.toString();
    } else if (anomaly.getAzuresubscriptionguid() != null) {
      builder.append(ViewFieldConstants.AZURE_SUBSCRIPTION_GUID);
      if (anomaly.getAzureresourcegroup() != null) {
        builder.append(SEPARATOR);
        builder.append(ViewFieldConstants.AZURE_RESOURCE_GROUP);
      }
      if (anomaly.getAzuremetercategory() != null) {
        builder.append(SEPARATOR);
        builder.append(ViewFieldConstants.AZURE_METER_CATEGORY);
      }
      return builder.toString();
    }

    return "";
  }

  public static String getGroupByField(Anomalies anomaly) {
    if (anomaly.getClustername() != null) {
      if (anomaly.getWorkloadname() != null) {
        return ViewFieldConstants.WORKLOAD_NAME_FIELD_ID;
      }
      if (anomaly.getNamespace() != null) {
        return ViewFieldConstants.NAMESPACE_FIELD_ID;
      }
      return ViewFieldConstants.CLUSTER_NAME_FIELD_ID;
    } else if (anomaly.getGcpproject() != null) {
      if (anomaly.getGcpskudescription() != null) {
        return ViewFieldConstants.GCP_SKU_DESCRIPTION_FIELD_ID;
      }
      if (anomaly.getGcpproduct() != null) {
        return ViewFieldConstants.GCP_PRODUCT_FIELD_ID;
      }
      return ViewFieldConstants.GCP_PROJECT_FIELD_ID;
    } else if (anomaly.getAwsaccount() != null) {
      if (anomaly.getAwsusagetype() != null) {
        return ViewFieldConstants.AWS_USAGE_TYPE_ID;
      }
      if (anomaly.getAwsservice() != null) {
        return ViewFieldConstants.AWS_SERVICE_FIELD_ID;
      }
      return ViewFieldConstants.AWS_ACCOUNT_FIELD_ID;
    } else if (anomaly.getAzuresubscriptionguid() != null) {
      if (anomaly.getAzuremetercategory() != null) {
        return ViewFieldConstants.AZURE_METER_CATEGORY;
      }
      if (anomaly.getAzureresourcegroup() != null) {
        return ViewFieldConstants.AZURE_RESOURCE_GROUP;
      }
      return ViewFieldConstants.AZURE_SUBSCRIPTION_GUID;
    }
    return null;
  }

  public static String getCloudProvider(Anomalies anomaly) {
    if (anomaly.getClustername() != null) {
      return AnomalyFieldConstants.CLUSTER;
    } else if (anomaly.getAwsaccount() != null) {
      return AnomalyFieldConstants.AWS;
    } else if (anomaly.getGcpproject() != null) {
      return AnomalyFieldConstants.GCP;
    } else if (anomaly.getAzuresubscriptionguid() != null) {
      return AnomalyFieldConstants.AZURE;
    }
    return "";
  }

  public static AnomalyData buildAnomalyData(Anomalies anomaly) {
    return buildAnomalyData(anomaly, Collections.emptyMap());
  }

  public static AnomalyData buildAnomalyData(Anomalies anomaly, Map<String, String> entityIdToNameMapping) {
    long anomalyTime = anomaly.getAnomalytime().toEpochSecond() * 1000;
    return AnomalyData.builder()
        .id(anomaly.getId())
        .time(anomalyTime)
        .anomalyRelativeTime(AnomalyUtils.getRelativeTime(anomalyTime, RELATIVE_TIME_TEMPLATE))
        .actualAmount(AnomalyUtils.getRoundedOffCost(anomaly.getActualcost()))
        .expectedAmount(AnomalyUtils.getRoundedOffCost(anomaly.getExpectedcost()))
        .anomalousSpend(AnomalyUtils.getRoundedOffCost(anomaly.getActualcost() - anomaly.getExpectedcost()))
        .anomalousSpendPercentage(AnomalyUtils.getAnomalyTrend(anomaly.getActualcost(), anomaly.getExpectedcost()))
        .entity(getEntityInfo(anomaly, entityIdToNameMapping))
        .resourceName(AnomalyUtils.getResourceName(anomaly))
        .resourceInfo(AnomalyUtils.getResourceInfo(anomaly))
        // Todo : Remove default assignment when status column is added to anomaly table
        .status("Open")
        .statusRelativeTime(AnomalyUtils.getRelativeTime(anomalyTime, RELATIVE_TIME_TEMPLATE))
        .cloudProvider(AnomalyUtils.getCloudProvider(anomaly))
        .build();
  }

  private static EntityInfo getEntityInfo(Anomalies anomaly, Map<String, String> entityIdToNameMapping) {
    return EntityInfo.builder()
        .field(AnomalyUtils.getGroupByField(anomaly))
        .clusterId(anomaly.getClusterid())
        .clusterName(anomaly.getClustername())
        .namespace(anomaly.getNamespace())
        .workloadName(anomaly.getWorkloadname())
        .workloadType(anomaly.getWorkloadtype())
        .gcpProjectId(anomaly.getGcpproject())
        .gcpSKUId(anomaly.getGcpskuid())
        .gcpSKUDescription(anomaly.getGcpskudescription())
        .gcpProduct(anomaly.getGcpproduct())
        .awsUsageAccountId(
            mergeAwsAccountIdAndName(anomaly.getAwsaccount(), entityIdToNameMapping.get(anomaly.getAwsaccount())))
        .awsServiceCode(anomaly.getAwsservice())
        .awsUsageType(anomaly.getAwsusagetype())
        .awsInstancetype(anomaly.getAwsinstancetype())
        .azureSubscriptionGuid(anomaly.getAzuresubscriptionguid())
        .azureResourceGroup(anomaly.getAzureresourcegroup())
        .azureMeterCategory(anomaly.getAzuremetercategory())
        .build();
  }

  public static AnomalyQueryDTO getDefaultAnomalyQuery() {
    return AnomalyQueryDTO.builder()
        .filter(null)
        .groupBy(new ArrayList<>())
        .orderBy(new ArrayList<>())
        .limit(DEFAULT_LIMIT)
        .offset(DEFAULT_OFFSET)
        .build();
  }

  public static List<AnomalyData> sortDataByNonTableFields(List<AnomalyData> anomalyData, List<CCMSort> sortByList) {
    for (CCMSort sortBy : sortByList) {
      if (sortBy.getField() == ANOMALOUS_SPEND) {
        switch (sortBy.getOrder()) {
          case ASCENDING:
            anomalyData.sort(Comparator.comparing(AnomalyData::getAnomalousSpend));
            break;
          case DESCENDING:
            anomalyData.sort(Comparator.comparing(AnomalyData::getAnomalousSpend).reversed());
            break;
          default:
        }
      }
    }
    return anomalyData;
  }

  public static List<String> collectAwsAccountIds(List<Anomalies> anomalies) {
    return anomalies.stream().map(Anomalies::getAwsaccount).collect(Collectors.toList());
  }

  private static String mergeAwsAccountIdAndName(final String accountId, final String accountName) {
    String accountDetails = accountId;
    if (!Strings.isNullOrEmpty(accountName)) {
      accountDetails = accountName + " (" + accountId + ")";
    }
    return accountDetails;
  }
}
