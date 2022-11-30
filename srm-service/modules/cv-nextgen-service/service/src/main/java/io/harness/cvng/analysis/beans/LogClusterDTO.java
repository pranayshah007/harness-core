/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import io.harness.cvng.analysis.entities.ClusteredLog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogClusterDTO {
  String verificationTaskId;
  long epochMinute;
  String host;
  String log;
  String clusterLabel;
  int clusterCount;

  public ClusteredLog toClusteredLog() {
    return ClusteredLog.builder()
        .verificationTaskId(verificationTaskId)
        .host(host)
        .clusterCount(clusterCount)
        .clusterLabel(clusterLabel)
        .log(log)
        .timestamp(Instant.ofEpochMilli(TimeUnit.MINUTES.toMillis(epochMinute)))
        .build();
  }
}
