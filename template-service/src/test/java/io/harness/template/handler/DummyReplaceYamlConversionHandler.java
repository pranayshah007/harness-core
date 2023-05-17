/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.handler;

import static io.harness.ng.core.template.TemplateEntityConstants.STEP;
import static io.harness.template.resources.beans.NGTemplateConstants.IDENTIFIER;
import static io.harness.template.resources.beans.NGTemplateConstants.NAME;

import static java.util.Arrays.asList;

import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.yaml.YamlField;

import java.util.HashMap;
import java.util.Map;

public class DummyReplaceYamlConversionHandler implements YamlConversionHandler {
  @Override
  public String getRootField(TemplateEntityType templateEntityType) {
    return "dummyReplace";
  }

  @Override
  public TemplateYamlConversionData getAdditionalFieldsToAdd(
      TemplateEntityType templateEntityType, YamlField yamlField) {
    Map<String, Object> fieldsToAdd = new HashMap<>();
    fieldsToAdd.put(IDENTIFIER, IDENTIFIER);
    fieldsToAdd.put(NAME, NAME);
    TemplateYamlConversionRecord conversionRecord1 = TemplateYamlReplaceConversionRecord.builder()
                                                         .fieldsToAdd(getRootField(templateEntityType))
                                                         .path("dummyReplace/spec/execution/steps/[0]/step/name")
                                                         .build();

    Map<String, Object> fieldsToAdd2 = new HashMap<>();
    fieldsToAdd2.put(STEP, fieldsToAdd);
    TemplateYamlConversionRecord conversionRecord2 = TemplateYamlReplaceConversionRecord.builder()
                                                         .fieldsToAdd(fieldsToAdd2)
                                                         .path("dummyReplace/spec/execution/steps/[1]")
                                                         .build();
    return TemplateYamlConversionData.builder()
        .templateYamlConversionRecordList(asList(conversionRecord1, conversionRecord2))
        .build();
  }
}
