/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection.alerts;

import static org.apache.commons.text.StrSubstitutor.replace;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.ccm.anomaly.entities.AnomalyEntity.AnomalyEntityKeys;
import io.harness.ccm.anomaly.entities.EntityType;
import io.harness.ccm.anomaly.utility.AnomalyUtility;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.communication.entities.CommunicationMedium;
import io.harness.ccm.currency.Currency;
import io.harness.data.structure.EmptyPredicate;

import com.google.inject.Singleton;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class EmailMessageGenerator {
  @Autowired private BatchMainConfig mainConfiguration;
  private static final String LIST_OF_ANOMALIES_FORMAT = "<ol style=\"font-size: 17px;\">%s</ol>";
  private static final String ANOMALY_LIST_ITEM_FORMAT =
      "<li style=\"margin-bottom: 20; font-size: 17px;\"> <code style=\"background-color: #EFEFF1;\">${ANOMALY_COST}</code>&nbsp; <code>(+${ANOMALY_COST_PERCENTAGE})</code> <br/>\n<table> %s </table> Total spend of <b>${"
      + AnomalyEntityKeys.actualCost + "}</b> detected. Would be typically at <b>${" + AnomalyEntityKeys.expectedCost
      + "}</b>";
  private static final String ANOMALY_DETAILS_ITEM_FORMAT =
      "<tr><td style=\"padding-right: 20; font-size: 14px;\"><b>%s:</b></td><td style=\"font-size: 14px;\">%s</td></tr>";
  private static final String ANOMALY_DETAILS_ITEM_FORMAT_WITH_LINK =
      "<tr><td style=\"padding-right: 20; font-size: 14px;\"><b>%s:</b></td><td style=\"font-size: 14px;\"><a href=\"%s\">%s</a></td></tr>";

  public String getAnomalyDetailsString(String accountId, String perspectiveId, String perspectiveName,
      List<AnomalyData> anomalies, Currency currency) throws URISyntaxException {
    StringBuilder anomaliesList = new StringBuilder();
    for (AnomalyData anomaly : anomalies) {
      AnomalyEntity anomalyEntity = convertToAnomalyEntity(anomaly);
      String templateString = "";
      templateString = addClusterInfo(templateString, anomalyEntity);
      templateString = addNamespaceInfo(templateString, anomalyEntity);
      templateString = addWorkloadInfo(templateString, anomalyEntity);
      templateString = addServiceInfo(templateString, anomalyEntity);
      templateString = addGcpProjectInfo(templateString, anomalyEntity);
      templateString = addGcpProductInfo(templateString, anomalyEntity);
      templateString = addGcpSkuInfo(templateString, anomalyEntity);
      templateString = addAwsAccountInfo(templateString, anomalyEntity);
      templateString = addAwsServiceInfo(templateString, anomalyEntity);
      templateString = String.format(ANOMALY_LIST_ITEM_FORMAT, templateString);
      templateString =
          replace(templateString, AnomalyUtility.getEntityMap(anomalyEntity, currency, CommunicationMedium.EMAIL));
      templateString = replace(templateString,
          AnomalyUtility.getNgURLMap(
              accountId, perspectiveId, perspectiveName, anomaly, mainConfiguration.getBaseUrl()));
      anomaliesList.append(templateString);
    }
    return String.format(LIST_OF_ANOMALIES_FORMAT, anomaliesList.toString());
  }

  private String addClusterInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getClusterId())) {
      if (anomaly.getEntityType() == EntityType.CLUSTER) {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT_WITH_LINK, "Cluster", "${CLUSTER_URL}",
                "${" + AnomalyEntityKeys.clusterName + "}");
      } else {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT, "Cluster", "${" + AnomalyEntityKeys.clusterName + "}");
      }
    }
    return templateString;
  }

  private String addNamespaceInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getNamespace())) {
      if (anomaly.getEntityType().equals(EntityType.NAMESPACE)) {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT_WITH_LINK, "Namespace", "${NAMESPACE_URL}",
                "${" + AnomalyEntityKeys.namespace + "}");
      } else {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT, "Namespace", "${" + AnomalyEntityKeys.namespace + "}");
      }
    }
    return templateString;
  }

  private String addWorkloadInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getWorkloadName())) {
      if (anomaly.getEntityType().equals(EntityType.WORKLOAD)) {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT_WITH_LINK, "Workload", "${WORKLOAD_URL}",
                "${" + AnomalyEntityKeys.workloadName + "}");
      } else {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT, "Workload", "${" + AnomalyEntityKeys.workloadName + "}");
      }
    }
    return templateString;
  }

  private String addServiceInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getServiceName())) {
      if (anomaly.getEntityType().equals(EntityType.SERVICE)) {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT_WITH_LINK, "Service", "${SERVICE_URL}",
                "${" + AnomalyEntityKeys.serviceName + "}");
      } else {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT, "Service", "${" + AnomalyEntityKeys.serviceName + "}");
      }
    }
    return templateString;
  }
  private String addGcpProjectInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getGcpProject())) {
      if (anomaly.getEntityType().equals(EntityType.GCP_PROJECT)) {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT_WITH_LINK, "Project", "${GCP_PROJECT_URL}",
                "${" + AnomalyEntityKeys.gcpProject + "}");
      } else {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT, "Project", "${" + AnomalyEntityKeys.gcpProject + "}");
      }
    }
    return templateString;
  }

  private String addGcpProductInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getGcpProduct())) {
      if (anomaly.getEntityType().equals(EntityType.GCP_PRODUCT)) {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT_WITH_LINK, "Product", "${GCP_PRODUCT_URL}",
                "${" + AnomalyEntityKeys.gcpProduct + "}");
      } else {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT, "Product", "${" + AnomalyEntityKeys.gcpProduct + "}");
      }
    }
    return templateString;
  }

  private String addGcpSkuInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getGcpSKUDescription())) {
      if (anomaly.getEntityType().equals(EntityType.GCP_SKU_ID)) {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT_WITH_LINK, "Sku", "${GCP_SKU_URL}",
                "${" + AnomalyEntityKeys.gcpSKUDescription + "}");
      } else {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT, "Sku", "${" + AnomalyEntityKeys.gcpSKUDescription + "}");
      }
    }
    return templateString;
  }

  private String addAwsAccountInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getAwsAccount())) {
      if (anomaly.getEntityType().equals(EntityType.AWS_ACCOUNT)) {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT_WITH_LINK, "Account", "${AWS_ACCOUNT_URL}",
                "${" + AnomalyEntityKeys.awsAccount + "}");
      } else {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT, "Account", "${" + AnomalyEntityKeys.awsAccount + "}");
      }
    }
    return templateString;
  }

  private String addAwsServiceInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getAwsService())) {
      if (anomaly.getEntityType().equals(EntityType.AWS_SERVICE)) {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT_WITH_LINK, "Service", "${AWS_SERVICE_URL}",
                "${" + AnomalyEntityKeys.awsService + "}");
      } else {
        templateString = templateString
            + String.format(ANOMALY_DETAILS_ITEM_FORMAT, "Service", "${" + AnomalyEntityKeys.awsService + "}");
      }
    }
    return templateString;
  }

  private AnomalyEntity convertToAnomalyEntity(AnomalyData anomaly) {
    return AnomalyEntity.builder()
        .anomalyTime(Instant.ofEpochMilli(anomaly.getTime()))
        .awsAccount(anomaly.getEntity().getAwsUsageAccountId())
        .awsInstanceType(anomaly.getEntity().getAwsInstancetype())
        .awsService(anomaly.getEntity().getAwsServiceCode())
        .awsUsageType(anomaly.getEntity().getAwsUsageType())
        .gcpProduct(anomaly.getEntity().getGcpProduct())
        .gcpProject(anomaly.getEntity().getGcpProjectId())
        .gcpSKUDescription(anomaly.getEntity().getGcpSKUDescription())
        .gcpSKUId(anomaly.getEntity().getGcpSKUId())
        .clusterId(anomaly.getEntity().getClusterId())
        .clusterName(anomaly.getEntity().getClusterName())
        .workloadName(anomaly.getEntity().getWorkloadName())
        .workloadType(anomaly.getEntity().getWorkloadType())
        .namespace(anomaly.getEntity().getNamespace())
        .service(anomaly.getEntity().getService())
        .serviceName(anomaly.getEntity().getServiceName())
        .actualCost(anomaly.getActualAmount())
        .expectedCost(anomaly.getExpectedAmount())
        .build();
  }
}
