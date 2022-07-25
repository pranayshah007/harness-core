/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection;

public class AnomalyDetectionConstants {
  public static final int DAYS_TO_CONSIDER = 36;
  public static final double DEFAULT_COST = -1.0;
  public static final double MINIMUM_AMOUNT = 100.0;
  public static final int MIN_DAYS_REQUIRED_DAILY = 14;
  public static final int BATCH_SIZE = 50;
  public static final Double STATS_MODEL_RELATIVITY_THRESHOLD = 1.25;
  public static final Double STATS_MODEL_ABSOLUTE_THRESHOLD = 100.0;
  public static final Double STATS_MODEL_PROBABILITY_THRESHOLD = 0.98;

  public static final Double NEARBY_ANOMALIES_THRESHOLD = 1.5;

  private AnomalyDetectionConstants() {}
}
