/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.config;

import io.harness.annotation.RecasterAlias;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeAlias("stoStepsExecutionConfig")
@RecasterAlias("io.harness.beans.config.STOStepsExecutionConfig")
public class STOStepsExecutionConfig {
  String defaultTag;
  List<String> defaultEntrypoint;
  List<STOImageConfig> images;
}
