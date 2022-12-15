/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import io.harness.cvng.analysis.entities.LogAnalysisCluster;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.AnalysisResult;
import io.harness.mongo.index.FdIndex;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
public class LogAnalysisDTO {
  @Setter private String verificationTaskId;
  @Setter private Instant analysisStartTime;
  @Setter private Instant analysisEndTime;
  @FdIndex private String accountId;
  private String analysisSummaryMessage;
  private double score;
  private long analysisMinute;

  private List<LogAnalysisCluster> logClusters;
  private List<AnalysisResult> logAnalysisResults;

  public List<LogAnalysisCluster> toAnalysisClusters(
      String verificationTaskId, Instant analysisStartTime, Instant analysisEndTime) {
    if (logClusters != null) {
      logClusters.forEach(cluster -> {
        cluster.setVerificationTaskId(verificationTaskId);
        cluster.setAnalysisStartTime(analysisStartTime);
        cluster.setAnalysisEndTime(analysisEndTime);
        cluster.setAccountId(accountId);
        cluster.setAnalysisMinute(analysisMinute);
      });
      return logClusters;
    }
    return null;
  }

  public LogAnalysisResult toAnalysisResult(
      String verificationTaskId, Instant analysisStartTime, Instant analysisEndTime) {
    return LogAnalysisResult.builder()
        .analysisStartTime(analysisStartTime)
        .analysisEndTime(analysisEndTime)
        .accountId(accountId)
        .verificationTaskId(verificationTaskId)
        .logAnalysisResults(logAnalysisResults)
        .overallRisk(score)
        .build();
  }
}
