/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.anomaly.url;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.commons.entities.anomaly.EntityInfo;

import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;

@UtilityClass
@Slf4j
@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class HarnessNgUrl {
  private static final String NG_PATH_CONST = "ng/";
  private static final String NG_PERSPECTIVE_URL_FORMAT = "ng/account/%s/ce/perspectives/%s/name/%s";
  private static final String CCM_URL_FORMAT_NG = "/account/%s/ce/overview";
  private static final String FILTER_FORMAT =
      "{\"field\":{\"fieldId\":\"%s\",\"fieldName\":\"%s\",\"identifier\":\"%s\",\"identifierName\":\"%s\"},\"operator\":\"IN\",\"type\":\"VIEW_ID_CONDITION\",\"values\":[\"%s\"]}";
  private static final String GROUP_BY_FORMAT =
      "{\"fieldId\":\"%s\",\"fieldName\":\"%s\",\"identifier\":\"%s\",\"identifierName\":\"%s\"}";
  private static final String DAY = "\"DAY\"";
  private static final String COLUMN = "\"column\"";
  private static final String TIME_RANGE = "{\"from\":\"%s\",\"to\":\"%s\"}";

  public String getPerspectiveUrl(String accountId, String perspectiveId, String perspectiveName, String baseUrl)
      throws URISyntaxException {
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setPath(format(NG_PERSPECTIVE_URL_FORMAT, accountId, perspectiveId, perspectiveName));
    return uriBuilder.toString();
  }

  public String getPerspectiveAnomalyUrl(String accountId, String perspectiveId, String perspectiveName,
      AnomalyData anomaly, String baseUrl) throws URISyntaxException {
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setPath(format(NG_PERSPECTIVE_URL_FORMAT, accountId, perspectiveId, perspectiveName));
    uriBuilder.setParameter(UrlParams.FILTERS.getParam(), generateFilters(anomaly));
    uriBuilder.setParameter(
        UrlParams.GROUP_BY.getParam(), getGroupBy(anomaly.getEntity().getField(), anomaly.getCloudProvider()));
    uriBuilder.setParameter(UrlParams.TIME_RANGE.getParam(), getTimeFilter(anomaly));
    uriBuilder.setParameter(UrlParams.AGGREGATION.getParam(), DAY);
    uriBuilder.setParameter(UrlParams.CHART_TYPE.getParam(), COLUMN);
    return uriBuilder.toString();
  }

  public String getCCMExplorerNGUrl(String accountId, String baseUrl) throws URISyntaxException {
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setPath(NG_PATH_CONST);
    uriBuilder.setFragment(format(CCM_URL_FORMAT_NG, accountId));
    return uriBuilder.toString();
  }

  private String generateFilters(AnomalyData anomaly) {
    EntityInfo anomalyEntity = anomaly.getEntity();
    String cloudProvider = anomaly.getCloudProvider();
    List<String> filters = new ArrayList<>();
    switch (cloudProvider) {
      case "GCP":
        if (StringUtils.isNotEmpty(anomalyEntity.getGcpProduct())) {
          filters.add(getFilter("gcpProduct", anomalyEntity.getGcpProduct(), cloudProvider));
        }
        if (StringUtils.isNotEmpty(anomalyEntity.getGcpProjectId())) {
          filters.add(getFilter("gcpProjectId", anomalyEntity.getGcpProjectId(), cloudProvider));
        }
        if (StringUtils.isNotEmpty(anomalyEntity.getGcpSKUDescription())) {
          filters.add(getFilter("gcpSKUDescription", anomalyEntity.getGcpSKUDescription(), cloudProvider));
        }
        break;
      case "AWS":
        if (StringUtils.isNotEmpty(anomalyEntity.getAwsUsageAccountId())) {
          filters.add(getFilter("awsUsageAccountId", anomalyEntity.getAwsUsageAccountId(), cloudProvider));
        }
        if (StringUtils.isNotEmpty(anomalyEntity.getAwsServiceCode())) {
          filters.add(getFilter("awsServiceCode", anomalyEntity.getAwsServiceCode(), cloudProvider));
        }
        if (StringUtils.isNotEmpty(anomalyEntity.getAwsInstancetype())) {
          filters.add(getFilter("awsInstancetype", anomalyEntity.getAwsInstancetype(), cloudProvider));
        }
        if (StringUtils.isNotEmpty(anomalyEntity.getAwsUsageType())) {
          filters.add(getFilter("awsUsageType", anomalyEntity.getAwsUsageType(), cloudProvider));
        }
        break;
      case "AZURE":
        if (StringUtils.isNotEmpty(anomalyEntity.getAzureSubscriptionGuid())) {
          filters.add(getFilter("azureSubscriptionGuid", anomalyEntity.getAzureSubscriptionGuid(), cloudProvider));
        }
        if (StringUtils.isNotEmpty(anomalyEntity.getAzureMeterCategory())) {
          filters.add(getFilter("azureMeterCategory", anomalyEntity.getAzureMeterCategory(), cloudProvider));
        }
        if (StringUtils.isNotEmpty(anomalyEntity.getAzureResourceGroup())) {
          filters.add(getFilter("azureResourceGroup", anomalyEntity.getAzureResourceGroup(), cloudProvider));
        }
        break;
      case "CLUSTER":
        if (StringUtils.isNotEmpty(anomalyEntity.getClusterName())) {
          filters.add(getFilter("clusterName", anomalyEntity.getClusterName(), cloudProvider));
        }
        if (StringUtils.isNotEmpty(anomalyEntity.getNamespace())) {
          filters.add(getFilter("namespace", anomalyEntity.getNamespace(), cloudProvider));
        }
        if (StringUtils.isNotEmpty(anomalyEntity.getWorkloadName())) {
          filters.add(getFilter("workloadName", anomalyEntity.getWorkloadName(), cloudProvider));
        }
        if (StringUtils.isNotEmpty(anomalyEntity.getWorkloadType())) {
          filters.add(getFilter("workloadType", anomalyEntity.getWorkloadType(), cloudProvider));
        }
        break;
      default:
        throw new InternalError("Invalid cloudProvider");
    }
    return "[" + String.join(",", filters) + "]";
  }

  private String getFilter(String entity, String entityValue, String cloudProvider) {
    return String.format(
        FILTER_FORMAT, entity, fieldToFieldNameMapping(entity), cloudProvider, cloudProvider, entityValue);
  }

  private String getGroupBy(String entity, String cloudProvider) {
    return String.format(GROUP_BY_FORMAT, entity, fieldToFieldNameMapping(entity), cloudProvider, cloudProvider);
  }

  private String getTimeFilter(AnomalyData anomaly) {
    Instant to = Instant.ofEpochMilli(anomaly.getTime());
    Instant from = to.minus(7, ChronoUnit.DAYS);
    Instant anomalyTime = Instant.ofEpochMilli(anomaly.getTime());
    if (anomalyTime.isBefore(from)) {
      from = anomalyTime;
    }
    return String.format(TIME_RANGE, convertInstantToDate(from), convertInstantToDate(to));
  }

  private static String fieldToFieldNameMapping(String field) {
    switch (field.toLowerCase()) {
      case "gcpproduct":
        return "Product";
      case "gcpprojectid":
        return "Project";
      case "gcpskudescription":
        return "SKUs";
      case "clustername":
        return "Cluster Name";
      case "namespace":
        return "Namespace";
      case "workloadname":
        return "Workload";
      case "awsusageaccountid":
        return "Account";
      case "awsservicecode":
        return "Service";
      case "awsinstancetype":
        return "Instance Type";
      case "awsusagetype":
        return "Usage Type";
      case "workloadtype":
        return "Workload Type";
      case "azuresubscriptionguid":
        return "Subscription ID";
      case "azuremetercategory":
        return "Meter category";
      case "azureresourcegroup":
        return "Resource group name";
      default:
        throw new InternalError("Invalid field");
    }
  }

  public String convertInstantToDate(Instant instant) {
    Date myDate = Date.from(instant);
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    return formatter.format(myDate);
  }
}
