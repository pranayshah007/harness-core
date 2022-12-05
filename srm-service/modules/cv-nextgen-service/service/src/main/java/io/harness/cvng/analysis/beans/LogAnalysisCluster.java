/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Value
@Builder
public class LogAnalysisCluster {
  private String host;
  private String clusterLabel;

  private List<MessageFrequency> messageFrequencies;
  private List<String> tags = new ArrayList<>();
  private List<Integer> anomalousCounts = new ArrayList<>();
  private boolean unexpectedFrequencies;
  private String text;
  private double x;
  private double y;
  private String feedbackId;

  private List<String> diffTags = new ArrayList<>();

  private double alertScore;
  private double testScore;
  private double controlScore;
  private double freqScore;
  private int controlLabel;
  private double riskLevel = 1.0;

  @Data
  @Builder
  public static class MessageFrequency {
    private int count;
    private String oldLabel;
    private String host;
    private long time;
  }
}
