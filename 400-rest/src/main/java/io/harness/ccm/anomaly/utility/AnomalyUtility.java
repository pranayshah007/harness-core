/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.anomaly.utility;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.ccm.anomaly.entities.AnomalyEntity.AnomalyEntityKeys;
import io.harness.ccm.anomaly.url.HarnessNgUrl;
import io.harness.ccm.anomaly.url.HarnessUrl;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.communication.entities.CommunicationMedium;
import io.harness.ccm.currency.Currency;

import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class AnomalyUtility {
  public Map<String, String> getEntityMap(
      AnomalyEntity anomaly, Currency currency, CommunicationMedium communicationMedium) {
    String currencySymbol = getCurrencySymbol(currency, communicationMedium);
    Map<String, String> substitutes = new HashMap<>();
    substitutes.put(AnomalyEntityKeys.clusterName, anomaly.getClusterName());
    substitutes.put(AnomalyEntityKeys.namespace, anomaly.getNamespace());
    substitutes.put(AnomalyEntityKeys.workloadName, anomaly.getWorkloadName());
    substitutes.put(AnomalyEntityKeys.gcpProject, anomaly.getGcpProject());
    substitutes.put(AnomalyEntityKeys.gcpProduct, anomaly.getGcpProduct());
    substitutes.put(AnomalyEntityKeys.gcpSKUId, anomaly.getGcpSKUId());
    substitutes.put(AnomalyEntityKeys.gcpSKUDescription, anomaly.getGcpSKUDescription());
    substitutes.put(AnomalyEntityKeys.awsAccount, anomaly.getAwsAccount());
    substitutes.put(AnomalyEntityKeys.awsService, anomaly.getAwsService());
    substitutes.put(AnomalyEntityKeys.awsUsageType, anomaly.getAwsUsageType());
    substitutes.put(
        AnomalyEntityKeys.actualCost, currencySymbol + getRoundedDoubleValue(anomaly.getActualCost()).toString());
    substitutes.put(
        AnomalyEntityKeys.expectedCost, currencySymbol + getRoundedDoubleValue(anomaly.getExpectedCost()).toString());
    substitutes.put("ANOMALY_COST", currencySymbol + getAnomalousCost(anomaly).toString());
    substitutes.put("ANOMALY_COST_PERCENTAGE",
        getPercentageRaise(anomaly.getActualCost(), anomaly.getExpectedCost(), true).toString() + "%");
    return substitutes;
  }

  private static String getCurrencySymbol(Currency currency, CommunicationMedium communicationMedium) {
    return CommunicationMedium.EMAIL == communicationMedium ? currency.getUtf8HexSymbol() : currency.getSymbol();
  }

  public Map<String, String> getURLMap(AnomalyEntity anomaly, String baseUrl) {
    Map<String, String> substitutes = new HashMap<>();
    substitutes.put("CLUSTER_URL", HarnessUrl.getK8sUrl(anomaly, baseUrl));
    substitutes.put("NAMESPACE_URL", HarnessUrl.getK8sUrl(anomaly, baseUrl));
    substitutes.put("WORKLOAD_URL", HarnessUrl.getK8sUrl(anomaly, baseUrl));
    substitutes.put("GCP_PROJECT_URL", HarnessUrl.getK8sUrl(anomaly, baseUrl));
    substitutes.put("GCP_PRODUCT_URL", HarnessUrl.getK8sUrl(anomaly, baseUrl));
    substitutes.put("GCP_SKU_URL", HarnessUrl.getK8sUrl(anomaly, baseUrl));
    substitutes.put("AWS_ACCOUNT_URL", HarnessUrl.getK8sUrl(anomaly, baseUrl));
    substitutes.put("AWS_SERVICE_URL", HarnessUrl.getK8sUrl(anomaly, baseUrl));
    return substitutes;
  }

  public Map<String, String> getNgURLMap(String accountId, String perspectiveId, String perspectiveName,
      AnomalyData anomaly, String baseUrl) throws URISyntaxException {
    Map<String, String> substitutes = new HashMap<>();
    substitutes.put("CLUSTER_URL",
        HarnessNgUrl.getPerspectiveAnomalyUrl(accountId, perspectiveId, perspectiveName, anomaly, baseUrl));
    substitutes.put("NAMESPACE_URL",
        HarnessNgUrl.getPerspectiveAnomalyUrl(accountId, perspectiveId, perspectiveName, anomaly, baseUrl));
    substitutes.put("WORKLOAD_URL",
        HarnessNgUrl.getPerspectiveAnomalyUrl(accountId, perspectiveId, perspectiveName, anomaly, baseUrl));
    substitutes.put("GCP_PROJECT_URL",
        HarnessNgUrl.getPerspectiveAnomalyUrl(accountId, perspectiveId, perspectiveName, anomaly, baseUrl));
    substitutes.put("GCP_PRODUCT_URL",
        HarnessNgUrl.getPerspectiveAnomalyUrl(accountId, perspectiveId, perspectiveName, anomaly, baseUrl));
    substitutes.put("GCP_SKU_URL",
        HarnessNgUrl.getPerspectiveAnomalyUrl(accountId, perspectiveId, perspectiveName, anomaly, baseUrl));
    substitutes.put("AWS_ACCOUNT_URL",
        HarnessNgUrl.getPerspectiveAnomalyUrl(accountId, perspectiveId, perspectiveName, anomaly, baseUrl));
    substitutes.put("AWS_SERVICE_URL",
        HarnessNgUrl.getPerspectiveAnomalyUrl(accountId, perspectiveId, perspectiveName, anomaly, baseUrl));
    return substitutes;
  }

  public Double getRoundedDoubleValue(Double value) {
    return Math.round(value * 100D) / 100D;
  }

  public Double getAnomalousCost(double actualcost, double expectedCost, boolean rounded) {
    if (rounded) {
      return getRoundedDoubleValue(actualcost - expectedCost);
    } else {
      return actualcost - expectedCost;
    }
  }

  public Double getAnomalousCost(AnomalyEntity anomaly) {
    return getAnomalousCost(anomaly.getActualCost(), anomaly.getExpectedCost(), true);
  }

  public Double getPercentageRaise(double actualCost, double expectedCost, boolean rounded) {
    double percentageRaise;
    if (expectedCost == 0) {
      percentageRaise = 100D;
    } else {
      percentageRaise = (actualCost - expectedCost) / expectedCost * 100;
    }
    if (rounded) {
      percentageRaise = getRoundedDoubleValue(percentageRaise);
    }
    return percentageRaise;
  }

  public String convertInstantToDate(Instant instant) {
    Date myDate = Date.from(instant);
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    return formatter.format(myDate);
  }

  public String convertInstantToDate2(Instant instant) {
    Date myDate = Date.from(instant);
    SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
    return formatter.format(myDate);
  }
}
