/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection.pythonserviceendpoint;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class PythonResponse {
  String id;
  @SerializedName("anomaly_score") Double anomalyScore;
  Double y;
  @SerializedName("y_hat") Double yHat;
  @SerializedName("y_hat_lower") Double yHatLower;
  @SerializedName("y_hat_upper") Double yHatUpper;
  @SerializedName("is_anomaly") Boolean isAnomaly;
  Long timestamp;
}
