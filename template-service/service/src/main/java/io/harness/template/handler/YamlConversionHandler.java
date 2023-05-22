/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.handler;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.yaml.YamlField;

@OwnedBy(HarnessTeam.CDC)
public interface YamlConversionHandler {
  String getRootField(TemplateEntityType templateEntityType);
  TemplateYamlConversionData getAdditionalFieldsToAdd(TemplateEntityType templateEntityType, YamlField yamlField);
}
