/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.handler;

import static io.harness.template.resources.beans.NGTemplateConstants.IDENTIFIER;
import static io.harness.template.resources.beans.NGTemplateConstants.NAME;
import static io.harness.template.resources.beans.NGTemplateConstants.TEMPLATE_INPUTS;
import static io.harness.template.resources.beans.NGTemplateConstants.TYPE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.yaml.YamlField;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class TemplateYamlConversionHandler implements YamlConversionHandler {
  @Override
  public String getRootField(TemplateEntityType templateEntityType) {
    return templateEntityType.getRootYamlName();
  }

  @Override
  public TemplateYamlConversionData getAdditionalFieldsToAdd(
      TemplateEntityType templateEntityType, YamlField yamlField) {
    Map<String, Object> fieldsToAdd = new HashMap<>();
    fieldsToAdd.put(IDENTIFIER, TEMPLATE_INPUTS);
    fieldsToAdd.put(NAME, TEMPLATE_INPUTS);
    String rootYamlFieldName = getRootField(templateEntityType);
    YamlField rootYamlField = yamlField.getNode().getField(rootYamlFieldName);
    if (rootYamlField == null) {
      throw new NGTemplateException("yamlNode provided doesn not have root yaml field: " + rootYamlFieldName);
    }
    YamlField typeYamlField = rootYamlField.getNode().getField(TYPE);
    if (typeYamlField == null) {
      throw new NGTemplateException("yamlNode provided doesn not have type yaml field");
    }
    TemplateYamlConversionRecord conversionRecord = TemplateYamlParallelConversionRecord.builder()
                                                        .fieldsToAdd(fieldsToAdd)
                                                        .path(typeYamlField.getYamlPath())
                                                        .build();
    return TemplateYamlConversionData.builder()
        .templateYamlConversionRecordList(Collections.singletonList(conversionRecord))
        .build();
  }
}
