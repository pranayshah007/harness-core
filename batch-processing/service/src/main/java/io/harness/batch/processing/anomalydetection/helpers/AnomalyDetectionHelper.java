/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection.helpers;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.batch.processing.anomalydetection.AnomalyDetectionTimeSeries;
import io.harness.batch.processing.service.impl.AnomalyDetectionLogContext;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.ccm.anomaly.entities.EntityType;
import io.harness.logging.AutoLogContext;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AnomalyDetectionHelper {
  private AnomalyDetectionHelper() {}

  public static String generateHash(String originalString) {
    return Hashing.sha256().hashString(originalString, StandardCharsets.UTF_8).toString();
  }

  public static void logInvalidTimeSeries(AnomalyDetectionTimeSeries timeSeries) {
    if (timeSeries == null) {
      return;
    }
    try (AutoLogContext ignore = new AnomalyDetectionLogContext(timeSeries.getId(), OVERRIDE_ERROR)) {
      Instant time = timeSeries.getTestTimePointsList().get(0);
      EntityType type = timeSeries.getEntityType();
      switch (type) {
        case CLUSTER:
          log.warn("Invalid Data for TimeSeries :: AccountId : {} , time : {} , clusterName : {} , clusterId : {} ",
              timeSeries.getAccountId(), time.toString(), timeSeries.getClusterName(), timeSeries.getClusterId());
          break;
        case NAMESPACE:
          log.warn(
              "Invalid Data for TimeSeries :: AccountId : {} , time : {} , clusterName : {} , clusterId : {} , namespace : {} ",
              timeSeries.getAccountId(), time.toString(), timeSeries.getClusterName(), timeSeries.getClusterId(),
              timeSeries.getNamespace());
          break;
        case GCP_PROJECT:
          log.warn("Invalid Data for TimeSeries :: AccountId : {} , time : {} , gcpProject : {}  ",
              timeSeries.getAccountId(), time.toString(), timeSeries.getGcpProject());
          break;
        case GCP_PRODUCT:
          log.warn("Invalid Data for TimeSeries :: AccountId : {} , time : {} , gcpProject : {} , gcpProduct : {} ",
              timeSeries.getAccountId(), time.toString(), timeSeries.getGcpProject(), timeSeries.getGcpProduct());
          break;
        case GCP_SKU_ID:
          log.warn(
              "Invalid Data for TimeSeries :: AccountId : {} , time : {} , gcpProject : {} , gcpProduct : {} , gcpSkuDescription : {} ",
              timeSeries.getAccountId(), time.toString(), timeSeries.getGcpProject(), timeSeries.getGcpProduct(),
              timeSeries.getGcpSKUDescription());
          break;
        case AWS_ACCOUNT:
          log.warn("Invalid Data for TimeSeries :: AccountId : {} , time : {} , awsAccount : {}",
              timeSeries.getAccountId(), time.toString(), timeSeries.getAwsAccount());
          break;
        case AWS_SERVICE:
          log.warn("Invalid Data for TimeSeries :: AccountId : {} , time : {} , awsAccount : {} , awsService : {} ",
              timeSeries.getAccountId(), time.toString(), timeSeries.getAwsAccount(), timeSeries.getAwsService());
          break;
        case AWS_USAGE_TYPE:
          log.warn(
              "Invalid Data for TimeSeries :: AccountId : {} , time : {} , awsAccount : {} , awsService : {} , awsUsageType : {} ",
              timeSeries.getAccountId(), time.toString(), timeSeries.getAwsAccount(), timeSeries.getAwsService(),
              timeSeries.getAwsUsageType());
          break;
        case AZURE_SUBSCRIPTION:
          log.warn("Invalid Data for TimeSeries :: Azure subs : {} , time : {}  ", timeSeries.getAzureSubscription(),
              time.toString());
          break;
        case AZURE_RESOURCE_GROUP:
          log.warn("Invalid Data for TimeSeries :: Azure subs : {} , time : {} , resource gp : {} ",
              timeSeries.getAzureSubscription(), time.toString(), timeSeries.getAzureResourceGroup());
          break;
        case AZURE_METER_CATEGORY:
          log.warn(
              "Invalid Data for TimeSeries :: Azure subs : {} , time : {} , resource gp : {} , meter category : {} ",
              timeSeries.getAzureSubscription(), time.toString(), timeSeries.getAzureResourceGroup(),
              timeSeries.getAzureMeterCategory());
          break;
        case GCP_REGION:
        case AWS_INSTANCE_TYPE:
        default:
          break;
      }
    }
  }

  public static void logValidTimeSeries(AnomalyDetectionTimeSeries timeSeries) {
    if (timeSeries == null) {
      return;
    }

    try (AutoLogContext ignore = new AnomalyDetectionLogContext(timeSeries.getId(), OVERRIDE_ERROR)) {
      Instant time = timeSeries.getTestTimePointsList().get(0);
      EntityType type = timeSeries.getEntityType();
      switch (type) {
        case CLUSTER:
          log.debug("Valid Data for TimeSeries :: AccountId : {} , time : {} , clusterName : {} , clusterId : {} ",
              timeSeries.getAccountId(), time.toString(), timeSeries.getClusterName(), timeSeries.getClusterId());
          break;
        case NAMESPACE:
          log.debug(
              "Valid Data for TimeSeries :: AccountId : {} , time : {} , clusterName : {} , clusterId : {} , namespace : {} ",
              timeSeries.getAccountId(), time.toString(), timeSeries.getClusterName(), timeSeries.getClusterId(),
              timeSeries.getNamespace());
          break;
        case GCP_PROJECT:
          log.debug("Valid Data for TimeSeries :: AccountId : {} , time : {} , gcpProject : {}  ",
              timeSeries.getAccountId(), time.toString(), timeSeries.getGcpProject());
          break;
        case GCP_PRODUCT:
          log.debug("Valid Data for TimeSeries :: AccountId : {} , time : {} , gcpProject : {} , gcpProduct : {} ",
              timeSeries.getAccountId(), time.toString(), timeSeries.getGcpProject(), timeSeries.getGcpProduct());
          break;
        case GCP_SKU_ID:
          log.debug(
              "Valid Data for TimeSeries :: AccountId : {} , time : {} , gcpProject : {} , gcpProduct : {} , gcpSkuDescription : {} ",
              timeSeries.getAccountId(), time.toString(), timeSeries.getGcpProject(), timeSeries.getGcpProduct(),
              timeSeries.getGcpSKUDescription());
          break;
        case AWS_ACCOUNT:
          log.debug("Valid Data for TimeSeries :: AccountId : {} , time : {} , awsAccount : {}",
              timeSeries.getAccountId(), time.toString(), timeSeries.getAwsAccount());
          break;
        case AWS_SERVICE:
          log.debug("Valid Data for TimeSeries :: AccountId : {} , time : {} , awsAccount : {} , awsService : {} ",
              timeSeries.getAccountId(), time.toString(), timeSeries.getAwsAccount(), timeSeries.getAwsService());
          break;
        case AWS_USAGE_TYPE:
          log.debug(
              "Valid Data for TimeSeries :: AccountId : {} , time : {} , awsAccount : {} , awsService : {} , awsUsageType : {} ",
              timeSeries.getAccountId(), time.toString(), timeSeries.getAwsAccount(), timeSeries.getAwsService(),
              timeSeries.getAwsUsageType());
          break;
        case AZURE_SUBSCRIPTION:
          log.debug("Valid Data for TimeSeries :: Azure subs : {} , time : {}  ", timeSeries.getAzureSubscription(),
              time.toString());
          break;
        case AZURE_RESOURCE_GROUP:
          log.debug("Valid Data for TimeSeries :: Azure subs : {} , time : {} , resource gp : {} ",
              timeSeries.getAzureSubscription(), time.toString(), timeSeries.getAzureResourceGroup());
          break;
        case AZURE_METER_CATEGORY:
          log.debug(
              "Valid Data for TimeSeries :: Azure subs : {} , time : {} , resource gp : {} , meter category : {} ",
              timeSeries.getAzureSubscription(), time.toString(), timeSeries.getAzureResourceGroup(),
              timeSeries.getAzureMeterCategory());
          break;
        case GCP_REGION:
        case AWS_INSTANCE_TYPE:
        default:
          break;
      }
    }
  }

  public static void logProcessingTimeSeries(String model) {
    log.debug("Processing time series using {}", model);
  }

  public static void logUnsuccessfulHttpCall(Integer code, String error) {
    log.info("unsuccessful http request from python server , error code {}", code);
    log.error(error);
  }

  public static String getHash(AnomalyEntity anomaly, boolean includeTime) {
    if (includeTime) {
      return AnomalyDetectionHelper.generateHash(
          String.join(",", anomaly.getAnomalyTime().toString(), anomaly.getClusterId(), anomaly.getNamespace(),
              anomaly.getWorkloadName(), anomaly.getGcpProject(), anomaly.getGcpProduct(), anomaly.getGcpSKUId(),
              anomaly.getAwsAccount(), anomaly.getAwsService(), anomaly.getAwsUsageType(),
              anomaly.getAzureSubscription(), anomaly.getAzureResourceGroup(), anomaly.getAzureMeterCategory()));
    } else {
      return AnomalyDetectionHelper.generateHash(String.join(",", anomaly.getClusterId(), anomaly.getNamespace(),
          anomaly.getWorkloadName(), anomaly.getGcpProject(), anomaly.getGcpProduct(), anomaly.getGcpSKUId(),
          anomaly.getAwsAccount(), anomaly.getAwsService(), anomaly.getAwsUsageType(), anomaly.getAzureSubscription(),
          anomaly.getAzureResourceGroup(), anomaly.getAzureMeterCategory()));
    }
  }
}
