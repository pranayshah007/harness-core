/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.handler;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TemplateYamlParallelConversionRecord implements TemplateYamlConversionRecord {
  String path;
  // In case path corresponds to an array, fieldsToAdd will be added as single array element.
  Map<String, Object> fieldsToAdd;

  @Override
  public FieldPlacementStrategy getFieldPlacementStrategy() {
    return FieldPlacementStrategy.PARALLEL;
  }
}
