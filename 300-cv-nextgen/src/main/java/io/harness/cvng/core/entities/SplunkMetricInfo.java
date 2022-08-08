/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder
@FieldNameConstants(innerTypeName = "SplunkMetricInfoKeys")
// had to move it outside as lambok superbuilder is not working when this is a inner class.
public class SplunkMetricInfo extends AnalysisInfo {
  private String query;
}
