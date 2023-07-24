/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection;

import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.ccm.anomaly.service.itfc.AnomalyService;
import io.harness.ccm.commons.beans.JobConstants;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class RemoveDuplicateAnomaliesTasklet implements Tasklet {
  @Autowired @Inject private AnomalyService anomalyService;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    JobConstants jobConstants = new CCMJobConstants(chunkContext);
    Instant startTime = Instant.ofEpochMilli(jobConstants.getJobStartTime());
    log.info("[RDA] Executing delete step {} {}", jobConstants.getAccountId(), startTime);

    List<AnomalyEntity> anomalyList = anomalyService.list(jobConstants.getAccountId(), startTime);
    log.info("[RDA] anomaly list {}", anomalyList.size());
    List<String> duplicateIds = getDuplicates(anomalyList);
    log.info("[RDA] deleting anomalies {}", duplicateIds);

    if (!duplicateIds.isEmpty()) {
      removeDuplicates(duplicateIds, startTime);
    }
    return null;
  }

  private void removeDuplicates(List<String> ids, Instant startTime) {
    anomalyService.delete(ids, startTime);
  }

  private List<String> getDuplicates(List<AnomalyEntity> anomaliesList) {
    List<AnomalyEntity> sorted = anomaliesList.stream()
                                     .sorted(Comparator.comparing(this::getConcatString).reversed())
                                     .collect(Collectors.toList());
    List<String> idList = new ArrayList<>();

    int currentDepth = -1;
    AnomalyEntity previousAnomaly = null;
    AnomalyEntity currentAnomaly = null;

    for (AnomalyEntity anomaly : sorted) {
      currentAnomaly = anomaly;
      /*Near by duplicates are also removed*/
      if ((anomaly.getActualCost() < 0)
          || (getDepth(anomaly) < currentDepth && isParent(currentAnomaly, previousAnomaly))) {
        idList.add(anomaly.getId());
      }
      currentDepth = getDepth(anomaly);
      previousAnomaly = currentAnomaly;
    }
    return idList;
  }

  private boolean isParent(AnomalyEntity parent, AnomalyEntity child) {
    if (child == null) {
      return false;
    }
    return checkStrings(parent.getClusterId(), child.getClusterId())
        && checkStrings(parent.getNamespace(), child.getNamespace())
        && checkStrings(parent.getWorkloadName(), child.getWorkloadName())
        && checkStrings(parent.getGcpProject(), child.getGcpProject())
        && checkStrings(parent.getGcpProduct(), child.getGcpProduct())
        && checkStrings(parent.getGcpSKUId(), child.getGcpSKUId())
        && checkStrings(parent.getAwsAccount(), child.getAwsAccount())
        && checkStrings(parent.getAwsService(), child.getAwsService())
        && checkStrings(parent.getAwsUsageType(), child.getAwsUsageType())
        && checkStrings(parent.getAzureSubscription(), child.getAzureSubscription())
        && checkStrings(parent.getAzureResourceGroup(), child.getAzureResourceGroup())
        && checkStrings(parent.getAzureMeterCategory(), child.getAzureMeterCategory());
  }
  private boolean checkStrings(String parent, String child) {
    if (parent != null && child != null) {
      return parent.equals(child);
    } else if (parent != null && child == null) {
      return false;
    } else if (parent == null && child != null) {
      return true;
    } else {
      return true;
    }
  }

  private int getDepth(AnomalyEntity anomaly) {
    List<String> list = new ArrayList<>(Arrays.asList(anomaly.getClusterId(), anomaly.getNamespace(),
        anomaly.getWorkloadName(), anomaly.getGcpProject(), anomaly.getGcpProduct(), anomaly.getGcpSKUId(),
        anomaly.getAwsAccount(), anomaly.getAwsService(), anomaly.getAwsUsageType(), anomaly.getAzureSubscription(),
        anomaly.getAzureResourceGroup(), anomaly.getAzureMeterCategory()));
    Iterables.removeIf(list, Objects::isNull);
    return list.size();
  }

  private String getConcatString(AnomalyEntity anomaly) {
    return Joiner.on(" ").skipNulls().join(anomaly.getClusterId(), anomaly.getNamespace(), anomaly.getWorkloadName(),
        anomaly.getGcpProject(), anomaly.getGcpProduct(), anomaly.getGcpSKUId(), anomaly.getAwsAccount(),
        anomaly.getAwsService(), anomaly.getAwsUsageType(), anomaly.getAzureSubscription(),
        anomaly.getAzureResourceGroup(), anomaly.getAzureMeterCategory());
  }
}
