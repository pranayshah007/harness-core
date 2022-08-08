/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.verification.apm;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.service.impl.newrelic.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import io.harness.beans.FeatureName;

import software.wings.beans.ApmMetricCollectionInfo;
import software.wings.metrics.MetricType;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.apm.CustomAPMDataCollectionInfo;
import software.wings.sm.states.APMVerificationState;
import software.wings.verification.CVConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class APMCVServiceConfiguration extends CVConfiguration {
  private List<ApmMetricCollectionInfo> metricCollectionInfos;

  @Override
  public boolean isCVTaskBasedCollectionFeatureFlagged() {
    return true;
  }

  @Override
  public FeatureName getCVTaskBasedCollectionFeatureFlag() {
    return FeatureName.CUSTOM_APM_24_X_7_CV_TASK;
  }

  @Override
  public CVConfiguration deepCopy() {
    APMCVServiceConfiguration clonedConfig = new APMCVServiceConfiguration();
    super.copy(clonedConfig);
    clonedConfig.setMetricCollectionInfos(this.getMetricCollectionInfos());
    return clonedConfig;
  }

  @Override
  public DataCollectionInfoV2 toDataCollectionInfo() {
    Map<String, String> hostsMap = new HashMap<>();
    hostsMap.put("DUMMY_24_7_HOST", DEFAULT_GROUP_NAME);
    CustomAPMDataCollectionInfo customAPMDataCollectionInfo =
        CustomAPMDataCollectionInfo.builder()
            .metricEndpoints(APMVerificationState.buildMetricInfoList(metricCollectionInfos, Optional.empty()))
            .hostsToGroupNameMap(hostsMap)
            .build();
    fillDataCollectionInfoWithCommonFields(customAPMDataCollectionInfo);
    return customAPMDataCollectionInfo;
  }

  public boolean validate() {
    if (isEmpty(metricCollectionInfos) || isAllThroughput()) {
      return false;
    }

    for (ApmMetricCollectionInfo metricCollectionInfo : metricCollectionInfos) {
      if (!metricCollectionInfo.getCollectionUrl().contains("${start_time}")
          && !metricCollectionInfo.getCollectionUrl().contains("${start_time_seconds}")) {
        if (isEmpty(metricCollectionInfo.getCollectionBody())
            || (!metricCollectionInfo.getCollectionBody().contains("${start_time}")
                && !metricCollectionInfo.getCollectionBody().contains("${start_time_seconds}"))) {
          return false;
        }
      }

      if (!metricCollectionInfo.getCollectionUrl().contains("${end_time}")
          && !metricCollectionInfo.getCollectionUrl().contains("${end_time_seconds}")) {
        if (isEmpty(metricCollectionInfo.getCollectionBody())
            || (!metricCollectionInfo.getCollectionBody().contains("${end_time}")
                && !metricCollectionInfo.getCollectionBody().contains("${end_time_seconds}"))) {
          return false;
        }
      }
    }

    return validateErrorOrResponseTime();
  }

  private boolean isAllThroughput() {
    if (isEmpty(metricCollectionInfos)) {
      return false;
    }
    AtomicBoolean isAllThroughput = new AtomicBoolean(true);
    metricCollectionInfos.forEach(metricCollectionInfo -> {
      if (MetricType.THROUGHPUT != metricCollectionInfo.getMetricType()) {
        isAllThroughput.set(false);
        return;
      }
    });
    return isAllThroughput.get();
  }

  public boolean validateUniqueMetricTxnCombination(List<ApmMetricCollectionInfo> metricCollectionInfoList) {
    boolean isValidCombination = true;

    Map<String, String> metricNameTxnNameMap = new HashMap<>();

    if (isNotEmpty(metricCollectionInfoList)) {
      for (ApmMetricCollectionInfo metricCollectionInfo : metricCollectionInfoList) {
        String txnName = metricCollectionInfo.getResponseMapping().getTxnNameFieldValue() == null
            ? "*"
            : metricCollectionInfo.getResponseMapping().getTxnNameFieldValue();

        if (metricNameTxnNameMap.containsKey(metricCollectionInfo.getMetricName())
            && metricNameTxnNameMap.get(metricCollectionInfo.getMetricName()).equals(txnName)) {
          isValidCombination = false;
          break;
        } else {
          metricNameTxnNameMap.put(metricCollectionInfo.getMetricName(), txnName);
        }
      }
      return isValidCombination;
    }
    return false;
  }

  private boolean validateErrorOrResponseTime() {
    AtomicBoolean isValidConfig = new AtomicBoolean(true);
    if (isEmpty(metricCollectionInfos)) {
      // not a valid configuration
      return false;
    }

    // First, basic validation. If there is atleast one error/Response time, there should be atleast one throughput.
    AtomicBoolean hasErrorOrTime = new AtomicBoolean(false), hasAtleastOneThroughput = new AtomicBoolean(false);
    metricCollectionInfos.forEach(metricCollectionInfo -> {
      if (MetricType.THROUGHPUT == metricCollectionInfo.getMetricType()) {
        hasAtleastOneThroughput.set(true);
      }
      if (MetricType.ERROR == metricCollectionInfo.getMetricType()
          || MetricType.RESP_TIME == metricCollectionInfo.getMetricType()) {
        hasErrorOrTime.set(true);
      }
    });

    if (!hasErrorOrTime.get()) {
      return true;
    }
    if (!hasAtleastOneThroughput.get()) {
      return false;
    }

    // Now for some more stringent validation based on txnName if it's available.
    Map<String, List<ApmMetricCollectionInfo>> txnNameInfoMap = new HashMap<>();
    metricCollectionInfos.forEach(metricCollectionInfo -> {
      final String txnName = metricCollectionInfo.getResponseMapping().getTxnNameFieldValue();
      if (isNotEmpty(txnName)) {
        if (!txnNameInfoMap.containsKey(txnName)) {
          txnNameInfoMap.put(txnName, new ArrayList<>());
        }
        txnNameInfoMap.get(txnName).add(metricCollectionInfo);
      }
    });

    if (isNotEmpty(txnNameInfoMap)) {
      txnNameInfoMap.forEach((txnName, metricInfoList) -> {
        // if this metricInfoList has an error/response time, it should have throughput also.
        hasErrorOrTime.set(false);
        hasAtleastOneThroughput.set(false);

        metricInfoList.forEach(metricInfo -> {
          if (MetricType.THROUGHPUT == metricInfo.getMetricType()) {
            hasAtleastOneThroughput.set(true);
          }
          if (MetricType.ERROR == metricInfo.getMetricType() || MetricType.RESP_TIME == metricInfo.getMetricType()) {
            hasErrorOrTime.set(true);
          }
        });

        if (hasErrorOrTime.get() && !hasAtleastOneThroughput.get()) {
          // it has an error/time but no throughput
          isValidConfig.set(false);
          return;
        }
      });
    }
    return isValidConfig.get();
  }
}
