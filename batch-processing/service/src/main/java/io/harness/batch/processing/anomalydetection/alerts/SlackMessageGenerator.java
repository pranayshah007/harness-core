/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection.alerts;

import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static org.apache.commons.text.StrSubstitutor.replace;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.ccm.anomaly.entities.AnomalyEntity.AnomalyEntityKeys;
import io.harness.ccm.anomaly.entities.EntityType;
import io.harness.ccm.anomaly.url.HarnessUrl;
import io.harness.ccm.anomaly.utility.AnomalyUtility;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.communication.entities.CommunicationMedium;
import io.harness.ccm.currency.Currency;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;

import com.google.inject.Singleton;
import com.slack.api.model.block.ActionsBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.element.BlockElement;
import com.slack.api.model.block.element.ButtonElement;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class SlackMessageGenerator {
  @Autowired private BatchMainConfig mainConfiguration;

  public List<LayoutBlock> generateDailyReport(List<AnomalyEntity> anomalies, Currency currency) {
    List<LayoutBlock> listBlocks = new ArrayList<>();
    if (!anomalies.isEmpty()) {
      Integer size = anomalies.size();
      Instant date = anomalies.get(0).getAnomalyTime();
      String accountId = anomalies.get(0).getAccountId();
      listBlocks.add(getDailyHeader(size, date));
      listBlocks.addAll(fromAnomaly(anomalies, currency));
      listBlocks.add(actionBar(size, accountId, date));
    } else {
      throw new InvalidArgumentsException("Anomalies list is empty cannot generate slack message");
    }
    return listBlocks;
  }

  public LayoutBlock actionBar(Integer noOfAnomalies, String accountId, Instant date) {
    List<BlockElement> buttonList = new ArrayList<>();

    String viewButtonText;
    if (noOfAnomalies > 3) {
      viewButtonText = "View all";
    } else {
      viewButtonText = "View in Explorer";
    }

    buttonList.add(ButtonElement.builder()
                       .text(plainText(pt -> pt.emoji(true).text(viewButtonText)))
                       .value("v2")
                       .url(HarnessUrl.getOverviewPageUrl(accountId, mainConfiguration.getBaseUrl(), date))
                       .build());

    buttonList.add(ButtonElement.builder()
                       .text(plainText(pt -> pt.emoji(true).text("Notification Settings")))
                       .value("v1")
                       .url(HarnessUrl.getCeSlackCommunicationSettings(accountId, mainConfiguration.getBaseUrl()))
                       .build());

    return ActionsBlock.builder().blockId("1").elements(buttonList).build();
  }

  public LayoutBlock getDailyHeader(Integer noOfAnomalies, Instant date) {
    String content;
    if (noOfAnomalies > 1) {
      content = String.format(":bell: %1$s Cost Anomalies detected today(%2$s) by Harness.", noOfAnomalies,
          AnomalyUtility.convertInstantToDate2(date));
    } else {
      content = String.format(":bell: %1$s Cost Anomaly detected today(%2$s) by Harness.", noOfAnomalies,
          AnomalyUtility.convertInstantToDate2(date));
    }
    return SectionBlock.builder().text(plainText(content, true)).build();
  }

  public List<LayoutBlock> fromAnomaly(List<AnomalyEntity> anomalies, Currency currency) {
    List<LayoutBlock> listLayoutBlock = new ArrayList<>();

    String headerText;
    if (anomalies.size() <= 3) {
      headerText = String.format("The %1$s Anomalies are", anomalies.size());
    } else {
      headerText = "The top 3 Anomalies are";
    }

    listLayoutBlock.add(SectionBlock.builder().text(plainText(String.format(headerText), true)).build());

    int count = 0;
    for (AnomalyEntity anomaly : anomalies) {
      if (count >= 3) {
        break;
      } else {
        count++;
      }
      listLayoutBlock.add(fromAnomaly(anomaly, currency));
    }
    return listLayoutBlock;
  }

  public String addClusterInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getClusterId())) {
      if (anomaly.getEntityType() == EntityType.CLUSTER) {
        templateString = templateString + "\n> *Cluster* : <${CLUSTER_URL}|${" + AnomalyEntityKeys.clusterName + "}> ";
      } else {
        templateString = templateString + "\n> *Cluster* : ${" + AnomalyEntityKeys.clusterName + "}";
      }
    }
    return templateString;
  }

  public String addNamespaceInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getNamespace())) {
      if (anomaly.getEntityType().equals(EntityType.NAMESPACE)) {
        templateString =
            templateString + " \n> *Namespace* : <${NAMESPACE_URL}|${" + AnomalyEntityKeys.namespace + "}>";
      } else {
        templateString = templateString + " \n> *Namespace* : ${" + AnomalyEntityKeys.namespace + "}";
      }
    }
    return templateString;
  }

  public String addWorkloadInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getWorkloadName())) {
      if (anomaly.getEntityType().equals(EntityType.WORKLOAD)) {
        templateString =
            templateString + " \n> *Workload* : <${WORKLOAD_URL}|${" + AnomalyEntityKeys.workloadName + "}>";
      } else {
        templateString = templateString + " \n> *Workload* : ${" + AnomalyEntityKeys.workloadName + "}";
      }
    }
    return templateString;
  }

  public String addServiceInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getServiceName())) {
      if (anomaly.getEntityType().equals(EntityType.SERVICE)) {
        templateString = templateString + " \n> *Service* : <${SERVICE_URL}|${" + AnomalyEntityKeys.serviceName + "}>";
      } else {
        templateString = templateString + " \n> *Service* : ${" + AnomalyEntityKeys.serviceName + "}";
      }
    }
    return templateString;
  }
  public String addGcpProjectInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getGcpProject())) {
      if (anomaly.getEntityType().equals(EntityType.GCP_PROJECT)) {
        templateString =
            templateString + "\n> *Project* : <${GCP_PROJECT_URL}|${" + AnomalyEntityKeys.gcpProject + "}>";
      } else {
        templateString = templateString + "\n> *Project* : ${" + AnomalyEntityKeys.gcpProject + "}";
      }
    }
    return templateString;
  }

  public String addGcpProductInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getGcpProduct())) {
      if (anomaly.getEntityType().equals(EntityType.GCP_PRODUCT)) {
        templateString =
            templateString + " \n> *Product* : <${GCP_PRODUCT_URL}|${" + AnomalyEntityKeys.gcpProduct + "}>";
      } else {
        templateString = templateString + " \n> *Product* : ${" + AnomalyEntityKeys.gcpProduct + "}";
      }
    }
    return templateString;
  }

  public String addGcpSkuInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getGcpSKUDescription())) {
      if (anomaly.getEntityType().equals(EntityType.GCP_SKU_ID)) {
        templateString =
            templateString + " \n> *Sku* : <${GCP_SKU_URL}|${" + AnomalyEntityKeys.gcpSKUDescription + "}>";
      } else {
        templateString = templateString + " \n> *Sku* : ${" + AnomalyEntityKeys.gcpSKUDescription + "}";
      }
    }
    return templateString;
  }

  public String addAwsAccountInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getAwsAccount())) {
      if (anomaly.getEntityType().equals(EntityType.AWS_ACCOUNT)) {
        templateString =
            templateString + "\n> *Account* : <${AWS_ACCOUNT_URL}|${" + AnomalyEntityKeys.awsAccount + "}>";
      } else {
        templateString = templateString + "\n> *Account* : ${" + AnomalyEntityKeys.awsAccount + "}";
      }
    }
    return templateString;
  }

  public String addAwsServiceInfo(String templateString, AnomalyEntity anomaly) {
    if (EmptyPredicate.isNotEmpty(anomaly.getAwsService())) {
      if (anomaly.getEntityType().equals(EntityType.AWS_SERVICE)) {
        templateString =
            templateString + " \n> *Service* : <${AWS_SERVICE_URL}|${" + AnomalyEntityKeys.awsService + "}>";
      } else {
        templateString = templateString + " \n> *Service* : ${" + AnomalyEntityKeys.awsService + "}";
      }
    }
    return templateString;
  }

  public LayoutBlock fromAnomaly(AnomalyEntity anomaly, Currency currency) {
    String templateString = "${ANOMALY_COST}`* (+${ANOMALY_COST_PERCENTAGE}) ";
    templateString = addClusterInfo(templateString, anomaly);
    templateString = addNamespaceInfo(templateString, anomaly);
    templateString = addWorkloadInfo(templateString, anomaly);
    templateString = addServiceInfo(templateString, anomaly);
    templateString = addGcpProjectInfo(templateString, anomaly);
    templateString = addGcpProductInfo(templateString, anomaly);
    templateString = addGcpSkuInfo(templateString, anomaly);
    templateString = addAwsAccountInfo(templateString, anomaly);
    templateString = addAwsServiceInfo(templateString, anomaly);
    templateString = templateString + "\n Total spend of *${" + AnomalyEntityKeys.actualCost
        + "}* detected. Would be typically at *${" + AnomalyEntityKeys.expectedCost + "}*";

    templateString =
        " *`" + replace(templateString, AnomalyUtility.getEntityMap(anomaly, currency, CommunicationMedium.SLACK));
    templateString = replace(templateString, AnomalyUtility.getURLMap(anomaly, mainConfiguration.getBaseUrl()));
    return SectionBlock.builder().text(MarkdownTextObject.builder().text(templateString).build()).build();
  }

  public String getAnomalyDetailsTemplateString(String accountId, String perspectiveId, String perspectiveName,
      AnomalyData anomaly, Currency currency) throws URISyntaxException {
    AnomalyEntity anomalyEntity = convertToAnomalyEntity(anomaly);

    String templateString = "${ANOMALY_COST}`* (+${ANOMALY_COST_PERCENTAGE})  ";
    templateString = addClusterInfo(templateString, anomalyEntity);
    templateString = addNamespaceInfo(templateString, anomalyEntity);
    templateString = addWorkloadInfo(templateString, anomalyEntity);
    templateString = addServiceInfo(templateString, anomalyEntity);
    templateString = addGcpProjectInfo(templateString, anomalyEntity);
    templateString = addGcpProductInfo(templateString, anomalyEntity);
    templateString = addGcpSkuInfo(templateString, anomalyEntity);
    templateString = addAwsAccountInfo(templateString, anomalyEntity);
    templateString = addAwsServiceInfo(templateString, anomalyEntity);
    templateString = templateString + "\n Total spend of *${" + AnomalyEntityKeys.actualCost
        + "}* detected. Would be typically at *${" + AnomalyEntityKeys.expectedCost + "}*\n\n";

    templateString = " *`"
        + replace(templateString, AnomalyUtility.getEntityMap(anomalyEntity, currency, CommunicationMedium.SLACK));
    templateString = replace(templateString,
        AnomalyUtility.getNgURLMap(accountId, perspectiveId, perspectiveName, anomaly, mainConfiguration.getBaseUrl()));
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
