/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.helper;

import static io.harness.batch.processing.billing.timeseries.service.impl.WeeklyReportServiceImpl.COMMUNICATION_MEDIUM;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.ccm.communication.entities.CommunicationMedium;

import software.wings.dl.WingsPersistence;

import java.net.URISyntaxException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WeeklyReportTemplateHelper {
  @Autowired private WingsPersistence wingsPersistence;
  @Autowired private BatchMainConfig mainConfiguration;

  private static final String CLUSTER_COST_NOT_AVAILABLE = "<span style='color: #DBDCDD;font-size: 40px;'>-</span>";
  private static final String TOTAL_CLUSTER_COST_AVAILABLE =
      "<span style=\"color: %s; font-size: 13px;text-align: center\">%s | %s</span><br><span style=\"color: #00ade4;font-size: 32px;\"><span class=\"clusterCost\">%s</span></span><br><span style=\"font-size: 10px; color: #808080\">Cluster cost</span>";
  private static final String COST_NOT_AVAILABLE = "";
  private static final String APPLICATION_RELATED_COSTS_AVAILABLE =
      "<tr><td align=\"left\" valign=\"top\" style=\"-webkit-font-smoothing: antialiased; text-size-adjust: 100%%; -ms-text-size-adjust: 100%%; -webkit-text-size-adjust: 100%%; mso-table-lspace: 0pt; mso-table-rspace: 0pt; border-spacing: 0; margin: 0; padding: 0; padding-left: 7%%; padding-right: 6.25%%; min-width: 87.5%%; max-width: 87.5%%; font-size: 17px; font-weight: 400; line-height: 160%%; padding-bottom: 25px; color: #000000; font-family: 'Source Sans Pro', Tahoma, Verdana, Segoe, sans-serif; border-collapse: collapse;\" class=\"paragraph\" width=\"87.5%%\"><h1 style=\"font-size: 18px; font-weight: normal;\">Applications</h1><h1 style=\"font-size: 14px; font-weight: normal;\">%s</h1></td></tr>";
  private static final String CLUSTER_RELATED_COSTS_AVAILABLE =
      "<tr><td align=\"left\" valign=\"top\" style=\"-webkit-font-smoothing: antialiased; text-size-adjust: 100%%; -ms-text-size-adjust: 100%%; -webkit-text-size-adjust: 100%%; mso-table-lspace: 0pt; mso-table-rspace: 0pt; border-spacing: 0; margin: 0; padding: 0; padding-left: 7%%; padding-right: 6.25%%; min-width: 87.5%%; max-width: 87.5%%; font-size: 17px; font-weight: 400; line-height: 160%%; padding-bottom: 25px; color: #000000; font-family: 'Source Sans Pro', Tahoma, Verdana, Segoe, sans-serif; border-collapse: collapse;\" class=\"paragraph\" width=\"87.5%%\"><h1 style=\"font-size: 18px; font-weight: normal;\">Clusters</h1><h1 style=\"font-size: 14px; font-weight: normal;\">%s</h1></td></tr>";
  private static final String ENTITY_COST_AVAILABLE =
      "<span>%s: </span><a href=\"%s\" target=\"_blank\" style=\"text-decoration: none;\"><span style=\"color: #00ade4\">%s </span></a>%s <span style=\"color: %s; font-size: 13px;\">(%s | %s)</span><br>";

  public static final String TOTAL_CLUSTER_COST = "TOTAL_CLUSTER";
  public static final String TOTAL_CLUSTER_IDLE_COST = "TOTAL_CLUSTER_IDLE";
  public static final String TOTAL_CLUSTER_UNALLOCATED_COST = "TOTAL_CLUSTER_UNALLOCATED";
  public static final String CLUSTER_RELATED_COSTS = "CLUSTER_RELATED_COSTS";
  public static final String CLUSTER = "Cluster";
  public static final String NAMESPACE = "Namespace";
  public static final String WORKLOAD = "Workload";
  public static final String APPLICATION_RELATED_COSTS = "APPLICATION_RELATED_COSTS";
  public static final String APPLICATION = "Application";
  public static final String SERVICE = "Service";
  public static final String ENVIRONMENT = "Environment";
  public static final String ACCOUNT_ID = "ACCOUNT_ID";
  public static final String FROM = "FROM";
  public static final String TO = "TO";
  public static final String PARENT = "PARENT";

  public static final String NAME = "_NAME";
  public static final String ID = "_ID";
  public static final String COST = "_COST";
  public static final String COST_CHANGE_PERCENT = "_COST_CHANGE_PERCENT";
  public static final String COST_DIFF_AMOUNT = "_COST_DIFF";
  public static final String COST_TREND = "_COST_TREND";
  public static final String COST_AVAILABLE = "_COST_AVAILABLE";
  public static final String SLACK_URL = "_SLACK_URL";

  public static final String DECREASE = "DECREASE";
  public static final String INCREASE = "INCREASE";
  public static final String AVAILABLE = "AVAILABLE";
  public static final String NOT_AVAILABLE = "NOT_AVAILABLE";

  private static final String CLUSTER_EXPLORER_URL_FORMAT =
      "/account/%s/continuous-efficiency/cluster/insights?utm_medium=%s&clusterList=%s&from=%s&groupBy=Cluster&to=%s";
  private static final String NAMESPACE_EXPLORER_URL_TEMPLATE =
      "/account/%s/continuous-efficiency/cluster/insights?utm_medium=%s&clusterList=%s&clusterNamespaceList=%s&from=%s&groupBy=Namespace&to=%s";
  private static final String WORKLOAD_EXPLORER_URL_TEMPLATE =
      "/account/%s/continuous-efficiency/cluster/insights?utm_medium=%s&clusterList=%s&clusterWorkLoadList=%s&from=%s&groupBy=WorkloadName&to=%s";
  private static final String APPLICATION_EXPLORER_URL_FORMAT =
      "/account/%s/continuous-efficiency/cluster/insights?utm_medium=%s&applicationList=%s&from=%s&groupBy=Application&to=%s";
  private static final String SERVICE_EXPLORER_URL_FORMAT =
      "/account/%s/continuous-efficiency/cluster/insights?utm_medium=%s&applicationList=%s&serviceList=%s&from=%s&groupBy=Service&to=%s";
  private static final String ENVIRONMENT_EXPLORER_URL_FORMAT =
      "/account/%s/continuous-efficiency/cluster/insights?utm_medium=%s&applicationList=%s&environmentList=%s&from=%s&groupBy=Environment&to=%s";

  public void populateCostDataForTemplate(Map<String, String> templateModel, Map<String, String> values) {
    templateModel.put("TOTAL_CLUSTER_COST", getTotalCostPopulatedValue(TOTAL_CLUSTER_COST, values));
    templateModel.put("TOTAL_CLUSTER_IDLE_COST", getTotalCostPopulatedValue(TOTAL_CLUSTER_IDLE_COST, values));
    templateModel.put(
        "TOTAL_CLUSTER_UNALLOCATED_COST", getTotalCostPopulatedValue(TOTAL_CLUSTER_UNALLOCATED_COST, values));
    templateModel.put(APPLICATION_RELATED_COSTS, COST_NOT_AVAILABLE);
    templateModel.put(CLUSTER_RELATED_COSTS, COST_NOT_AVAILABLE);
    if (values.get(APPLICATION_RELATED_COSTS).equals(AVAILABLE)) {
      templateModel.put(APPLICATION_RELATED_COSTS,
          String.format(APPLICATION_RELATED_COSTS_AVAILABLE,
              getEntityCostPopulatedValue(APPLICATION, values) + getEntityCostPopulatedValue(SERVICE, values)
                  + getEntityCostPopulatedValue(ENVIRONMENT, values)));
    }
    if (values.get(CLUSTER_RELATED_COSTS).equals(AVAILABLE)) {
      templateModel.put(CLUSTER_RELATED_COSTS,
          String.format(CLUSTER_RELATED_COSTS_AVAILABLE,
              getEntityCostPopulatedValue(CLUSTER, values) + getEntityCostPopulatedValue(NAMESPACE, values)
                  + getEntityCostPopulatedValue(WORKLOAD, values)));
    }
  }

  private String getTotalCostPopulatedValue(String entity, Map<String, String> values) {
    if (values.get(entity + COST_AVAILABLE).equals(AVAILABLE)) {
      String color = "green";
      if (values.get(entity + COST_TREND).equals(INCREASE)) {
        color = "red";
      }
      return String.format(TOTAL_CLUSTER_COST_AVAILABLE, color, values.get(entity + COST_CHANGE_PERCENT),
          values.get(entity + COST_DIFF_AMOUNT), values.get(entity + COST));
    } else {
      return CLUSTER_COST_NOT_AVAILABLE;
    }
  }

  private String getEntityCostPopulatedValue(String entity, Map<String, String> values) {
    if (values.get(entity + COST_AVAILABLE).equals(AVAILABLE)) {
      String color = "green";
      if (values.get(entity + COST_TREND).equals(INCREASE)) {
        color = "red";
      }
      return String.format(ENTITY_COST_AVAILABLE, entity, getEntityDetailsUrl(entity, values),
          values.get(entity + NAME), values.get(entity + COST), color, values.get(entity + COST_CHANGE_PERCENT),
          values.get(entity + COST_DIFF_AMOUNT));
    } else {
      return COST_NOT_AVAILABLE;
    }
  }

  public void populateSlackUrls(Map<String, String> values) {
    values.put(COMMUNICATION_MEDIUM, CommunicationMedium.SLACK.getName());
    values.put(APPLICATION + SLACK_URL, getEntityDetailsUrl(APPLICATION, values));
    values.put(SERVICE + SLACK_URL, getEntityDetailsUrl(SERVICE, values));
    values.put(ENVIRONMENT + SLACK_URL, getEntityDetailsUrl(ENVIRONMENT, values));
    values.put(CLUSTER + SLACK_URL, getEntityDetailsUrl(CLUSTER, values));
    values.put(NAMESPACE + SLACK_URL, getEntityDetailsUrl(NAMESPACE, values));
    values.put(WORKLOAD + SLACK_URL, getEntityDetailsUrl(WORKLOAD, values));
  }

  private String getEntityDetailsUrl(String entity, Map<String, String> values) {
    String url;
    switch (entity) {
      case CLUSTER:
        url = String.format(CLUSTER_EXPLORER_URL_FORMAT, values.get(ACCOUNT_ID), values.get(COMMUNICATION_MEDIUM),
            values.get(CLUSTER + PARENT), values.get(FROM), values.get(TO));
        break;
      case WORKLOAD:
        url = String.format(WORKLOAD_EXPLORER_URL_TEMPLATE, values.get(ACCOUNT_ID), values.get(COMMUNICATION_MEDIUM),
            values.get(WORKLOAD + PARENT), values.get(entity + NAME), values.get(FROM), values.get(TO));
        break;
      case NAMESPACE:
        url = String.format(NAMESPACE_EXPLORER_URL_TEMPLATE, values.get(ACCOUNT_ID), values.get(COMMUNICATION_MEDIUM),
            values.get(NAMESPACE + PARENT), values.get(entity + NAME), values.get(FROM), values.get(TO));
        break;
      case APPLICATION:
        url = String.format(APPLICATION_EXPLORER_URL_FORMAT, values.get(ACCOUNT_ID), values.get(COMMUNICATION_MEDIUM),
            values.get(APPLICATION + PARENT), values.get(FROM), values.get(TO));
        break;
      case SERVICE:
        url = String.format(SERVICE_EXPLORER_URL_FORMAT, values.get(ACCOUNT_ID), values.get(COMMUNICATION_MEDIUM),
            values.get(SERVICE + PARENT), values.get(SERVICE + ID), values.get(FROM), values.get(TO));
        break;
      case ENVIRONMENT:
        url = String.format(ENVIRONMENT_EXPLORER_URL_FORMAT, values.get(ACCOUNT_ID), values.get(COMMUNICATION_MEDIUM),
            values.get(ENVIRONMENT + PARENT), values.get(ENVIRONMENT + ID), values.get(FROM), values.get(TO));
        break;
      default:
        url = "";
    }
    try {
      return buildAbsoluteUrl(url);
    } catch (URISyntaxException e) {
      log.error("Error in forming Explorer URL for Weekly Report", e);
      return url;
    }
  }

  public String buildAbsoluteUrl(String fragment) throws URISyntaxException {
    String baseUrl = mainConfiguration.getBaseUrl();
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setFragment(fragment);
    return uriBuilder.toString();
  }
}
