/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.template.resources.beans.NGTemplateConstants.SPEC;
import static io.harness.template.resources.beans.NGTemplateConstants.TEMPLATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlNodeUtils;
import io.harness.pms.yaml.YamlUtils;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.handler.FieldPlacementStrategy;
import io.harness.template.handler.TemplateYamlConversionData;
import io.harness.template.handler.TemplateYamlConversionHandlerRegistry;
import io.harness.template.handler.YamlConversionHandler;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class TemplateYamlConversionHelper {
  TemplateYamlConversionHandlerRegistry templateYamlConversionHandlerRegistry;

  public String convertTemplateYamlToEntityYaml(TemplateEntity templateEntity) {
    String templateYaml = templateEntity.getYaml();
    TemplateEntityType templateEntityType = templateEntity.getTemplateEntityType();
    YamlConversionHandler yamlConversionHandler =
        templateYamlConversionHandlerRegistry.obtain(templateEntityType.toString());
    String rootYamlField = yamlConversionHandler.getRootField(templateEntityType);
    JsonNode templateSpecJsonNode = getTemplateSpecNode(templateYaml);
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode rootJsonNode = mapper.createObjectNode();
    rootJsonNode.set(rootYamlField, templateSpecJsonNode);
    YamlNode rootFieldYamlNode = new YamlNode(rootJsonNode);
    YamlField rootFieldYamlField = new YamlField(rootFieldYamlNode);
    addAdditionalFieldsToYaml(templateEntityType, yamlConversionHandler, rootFieldYamlField);

    // Removing stageType from yaml Node only for stepGroup Templates.
    if (templateEntity.getTemplateEntityType().equals(TemplateEntityType.STEPGROUP_TEMPLATE)) {
      rootFieldYamlField.getNode().removePath("stepGroup/stageType");
    }
    return YamlUtils.writeYamlString(rootFieldYamlField.getNode().getCurrJsonNode());
  }

  private JsonNode getTemplateSpecNode(String templateYaml) {
    YamlField templateYamlField = getYamlField(templateYaml).getNode().getField(TEMPLATE);
    if (templateYamlField == null) {
      log.error("Yaml provided is not a template yaml. Yaml:\n" + templateYaml);
      throw new NGTemplateException("Yaml provided is not a template yaml.");
    }
    return templateYamlField.getNode().getCurrJsonNode().get(SPEC);
  }

  private void addAdditionalFieldsToYaml(
      TemplateEntityType templateEntityType, YamlConversionHandler yamlConversionHandler, YamlField yamlField) {
    TemplateYamlConversionData templateYamlConversionData =
        yamlConversionHandler.getAdditionalFieldsToAdd(templateEntityType, yamlField);
    templateYamlConversionData.getTemplateYamlConversionRecordList().forEach(templateYamlConversionRecord -> {
      FieldPlacementStrategy fieldPlacementStrategy = templateYamlConversionRecord.getFieldPlacementStrategy();
      JsonNode fieldsToAdd = JsonPipelineUtils.asTree(templateYamlConversionRecord.getFieldsToAdd());
      if (fieldPlacementStrategy.equals(FieldPlacementStrategy.PARALLEL)) {
        String path = templateYamlConversionRecord.getPath();
        if (path.lastIndexOf('/') >= 0) {
          path = path.substring(0, path.lastIndexOf('/'));
        }
        YamlNodeUtils.addToPath(yamlField.getNode(), path, fieldsToAdd);
      } else if (fieldPlacementStrategy.equals(FieldPlacementStrategy.REPLACE)) {
        yamlField.getNode().replacePath(templateYamlConversionRecord.getPath(), fieldsToAdd);
      }
    });
  }

  private YamlField getYamlField(String yaml) {
    try {
      return YamlUtils.readTree(yaml);
    } catch (IOException e) {
      throw new NGTemplateException("Cannot convert yaml to yamlField due to " + e.getMessage());
    }
  }
}
