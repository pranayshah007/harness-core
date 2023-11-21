/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.template;

import static io.harness.template.resources.beans.NGTemplateConstants.DUMMY_NODE;

import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.pms.merger.helpers.RuntimeInputsValidator;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Path;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import org.hibernate.validator.internal.engine.path.PathImpl;

public class MonitoredServiceValidator {
  public static boolean validateTemplateInputs(String nodeToValidateYaml, String sourceNodeInputSetFormatYaml) {
    nodeToValidateYaml = addDummyNodeToYaml(nodeToValidateYaml);
    return RuntimeInputsValidator.validateInputsAgainstSourceNode(nodeToValidateYaml, sourceNodeInputSetFormatYaml);
  }

  public static String addDummyNodeToYaml(String yaml) {
    YamlField yamlField = YamlUtils.readYamlTree(yaml);
    JsonNode templateInputs = yamlField.getNode().getCurrJsonNode();
    Map<String, JsonNode> dummyTemplateInputsMap = new LinkedHashMap<>();
    dummyTemplateInputsMap.put(DUMMY_NODE, templateInputs);
    return YamlUtils.writeYamlString(dummyTemplateInputsMap);
  }

  public static void validateMSDTO(MonitoredServiceDTO monitoredServiceDTO) {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();
    Set<ConstraintViolation<MonitoredServiceDTO>> violations = validator.validate(monitoredServiceDTO);
    violations.forEach(violation -> {
      throw new RuntimeException(getFieldFromPath(violation.getPropertyPath()) + " " + violation.getMessage());
    });
  }

  private static String getFieldFromPath(Path fieldPath) {
    PathImpl pathImpl = (PathImpl) fieldPath;
    return pathImpl.getLeafNode().getName();
  }
}
