/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logging.client;

import java.util.Map;
import lombok.Data;

@Data
public class StackdriverLogLine {
  final Map<String, ?> payload;
  final Map<String, String> labels;
  final int severity;
  final long timestamp;
}
