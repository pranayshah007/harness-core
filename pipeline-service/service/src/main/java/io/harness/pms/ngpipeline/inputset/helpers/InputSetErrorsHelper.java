/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateFromPipeline;
import static io.harness.pms.merger.helpers.InputSetYamlHelper.getPipelineComponent;
import static io.harness.pms.yaml.validation.RuntimeInputValuesValidator.validateStaticValues;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.inputset.InputSetErrorDTOPMS;
import io.harness.pms.inputset.InputSetErrorResponseDTOPMS;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.InputSetYamlHelper;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.merger.helpers.YamlSubMapExtractor;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;

import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class InputSetErrorsHelper {
  public final String INVALID_INPUT_SET_MESSAGE = "Reference is an invalid Input Set";
  public final String OUTDATED_INPUT_SET_MESSAGE = "Reference is an outdated input set";
  private static final TextNode EMPTY_STRING_NODE = new TextNode("");

  public InputSetErrorWrapperDTOPMS getErrorMap(String pipelineYaml, String inputSetYaml) {
    String pipelineComp = getPipelineComponent(inputSetYaml);
    String templateYaml = createTemplateFromPipeline(pipelineYaml);
    Map<FQN, String> invalidFQNs = getInvalidFQNsInInputSet(templateYaml, pipelineComp);
    if (EmptyPredicate.isEmpty(invalidFQNs)) {
      return null;
    }

    String errorPipelineYaml = getErrorPipelineYaml(invalidFQNs.keySet(), pipelineYaml);
    Map<String, InputSetErrorResponseDTOPMS> uuidToErrorResponseMap = getUuidToErrorResponseMap(
        invalidFQNs, InputSetYamlHelper.getStringField(inputSetYaml, "identifier", "inputSet"));
    return InputSetErrorWrapperDTOPMS.builder()
        .errorPipelineYaml(errorPipelineYaml)
        .uuidToErrorResponseMap(uuidToErrorResponseMap)
        .build();
  }

  private String getErrorPipelineYaml(Set<FQN> invalidFQNs, String pipelineYaml) {
    Map<FQN, Object> map = new LinkedHashMap<>();
    invalidFQNs.forEach(fqn -> map.put(fqn, fqn.getExpressionFqn()));
    YamlConfig config = new YamlConfig(pipelineYaml);
    YamlConfig res = new YamlConfig(map, config.getYamlMap());
    return res.getYaml();
  }

  public Map<String, InputSetErrorResponseDTOPMS> getUuidToErrorResponseMap(
      String pipelineYaml, String inputSetPipelineComponent) {
    String templateYaml = createTemplateFromPipeline(pipelineYaml);
    Map<FQN, String> invalidFQNs = getInvalidFQNsInInputSet(templateYaml, inputSetPipelineComponent);
    if (EmptyPredicate.isEmpty(invalidFQNs)) {
      return null;
    }
    return getUuidToErrorResponseMap(invalidFQNs, "");
  }

  private Map<String, InputSetErrorResponseDTOPMS> getUuidToErrorResponseMap(
      Map<FQN, String> invalidFQNs, String inputSetIdentifier) {
    Map<String, InputSetErrorResponseDTOPMS> res = new LinkedHashMap<>();
    invalidFQNs.keySet().forEach(fqn -> {
      String uuid = fqn.getExpressionFqn();
      InputSetErrorDTOPMS errorDTOPMS = InputSetErrorDTOPMS.builder()
                                            .fieldName(fqn.getFieldName())
                                            .message(invalidFQNs.get(fqn))
                                            .identifierOfErrorSource(inputSetIdentifier)
                                            .build();
      InputSetErrorResponseDTOPMS errorResponseDTOPMS =
          InputSetErrorResponseDTOPMS.builder().errors(Collections.singletonList(errorDTOPMS)).build();
      res.put(uuid, errorResponseDTOPMS);
    });
    return res;
  }

  public Map<String, String> getInvalidInputSetReferences(
      List<Optional<InputSetEntity>> inputSets, List<String> identifiers, String pipelineYaml) {
    Map<String, String> res = new LinkedHashMap<>();
    for (int i = 0; i < identifiers.size(); i++) {
      String identifier = identifiers.get(i);
      Optional<InputSetEntity> optionalEntity = inputSets.get(i);
      if (!optionalEntity.isPresent()) {
        res.put(identifier, "Reference does not exist");
        continue;
      }
      InputSetEntity inputSetEntity = optionalEntity.get();
      if (inputSetEntity.getInputSetEntityType() == InputSetEntityType.OVERLAY_INPUT_SET) {
        res.put(identifier, "References can't be other overlay input sets");
      } else if (inputSetEntity.getIsInvalid()) {
        res.put(identifier, OUTDATED_INPUT_SET_MESSAGE);
      } else {
        String inputSetYaml = inputSetEntity.getYaml();
        InputSetErrorWrapperDTOPMS errorMap = getErrorMap(pipelineYaml, inputSetYaml);
        if (errorMap != null) {
          res.put(identifier, INVALID_INPUT_SET_MESSAGE);
        }
      }
    }
    return res;
  }

  public Map<String, String> getInvalidInputSetReferences(
      List<Optional<InputSetEntity>> inputSets, List<String> identifiers) {
    Map<String, String> res = new LinkedHashMap<>();
    for (int i = 0; i < identifiers.size(); i++) {
      String identifier = identifiers.get(i);
      Optional<InputSetEntity> optionalEntity = inputSets.get(i);
      if (!optionalEntity.isPresent()) {
        res.put(identifier, "Reference does not exist");
        continue;
      }
      InputSetEntity inputSetEntity = optionalEntity.get();
      if (inputSetEntity.getInputSetEntityType() == InputSetEntityType.OVERLAY_INPUT_SET) {
        res.put(identifier, "References can't be other overlay input sets");
      } else if (inputSetEntity.getIsInvalid()) {
        res.put(identifier, OUTDATED_INPUT_SET_MESSAGE);
      }
    }
    return res;
  }

  // TODO(BRIJESH): This method is duplicated in ExecutionInputServiceImpl. Do the refactoring and keep this at only one
  // place.
  public Map<FQN, String> getInvalidFQNsInInputSet(String templateYaml, String inputSetPipelineCompYaml) {
    YamlConfig inputSetConfig = new YamlConfig(inputSetPipelineCompYaml);
    YamlConfig templateConfig = EmptyPredicate.isEmpty(templateYaml) ? null : new YamlConfig(templateYaml);
    return getInvalidFQNsInInputSetFromTemplateConfig(templateConfig, inputSetConfig);
  }

  public Map<FQN, String> getInvalidFQNsInInputSet(YamlConfig pipelineYamlConfig, YamlConfig inputSetConfig) {
    YamlConfig templateYamlConfig = RuntimeInputFormHelper.createRuntimeInputFormYamlConfig(pipelineYamlConfig, true);
    return getInvalidFQNsInInputSetFromTemplateConfig(templateYamlConfig, inputSetConfig);
  }

  public Map<FQN, String> getInvalidFQNsInInputSetFromTemplateConfig(
      YamlConfig pipelineYamlConfig, YamlConfig inputSetConfig) {
    return getInvalidFQNsInInputSetFromTemplateConfig(
        pipelineYamlConfig != null ? pipelineYamlConfig.getFqnToValueMap() : null, inputSetConfig.getFqnToValueMap());
  }

  Map<FQN, String> getInvalidFQNsInInputSetFromTemplateConfig(
      Map<FQN, Object> templateFqnToValueMap, Map<FQN, Object> inputFqnToValueMap) {
    Map<FQN, String> errorMap = new LinkedHashMap<>();
    Set<FQN> inputSetFQNs = new LinkedHashSet<>(inputFqnToValueMap.keySet());
    if (EmptyPredicate.isEmpty(templateFqnToValueMap)) {
      inputSetFQNs.forEach(fqn -> errorMap.put(fqn, "Pipeline no longer contains any runtime input"));
      return errorMap;
    }

    templateFqnToValueMap.keySet().forEach(key -> {
      if (inputSetFQNs.contains(key)) {
        Object templateValue = templateFqnToValueMap.get(key);
        Object value = inputFqnToValueMap.get(key);
        if (key.isType() || key.isIdentifierOrVariableName()) {
          if (!value.toString().equals(templateValue.toString())) {
            errorMap.put(key,
                "The value for " + key.getExpressionFqn() + " is " + templateValue
                    + "in the pipeline yaml, but the input set has it as " + value);
          }
        } else {
          String error = validateStaticValues(templateValue, value);
          if (EmptyPredicate.isNotEmpty(error)) {
            errorMap.put(key, error);
          }
        }

        inputSetFQNs.remove(key);
      } else {
        if (!key.isType() || !key.isIdentifierOrVariableName()) {
          String error = validateStaticValues(templateFqnToValueMap.get(key));
          if (EmptyPredicate.isNotEmpty(error)) {
            errorMap.put(key, error);
          }
        }
        Map<FQN, Object> subMap = YamlSubMapExtractor.getFQNToObjectSubMap(inputFqnToValueMap, key);
        subMap.keySet().forEach(inputSetFQNs::remove);
      }
    });
    inputSetFQNs.forEach(fqn -> errorMap.put(fqn, "Field not a runtime input"));
    return errorMap;
  }

  /**
   * Return a list of missing {@link FQN} declared as runtime input from the pipeline.
   */
  public List<FQN> getMissingFQNsInInputSet(YamlConfig pipelineYamlConfig) {
    YamlConfig templateYamlConfig = RuntimeInputFormHelper.createRuntimeInputFormYamlConfig(pipelineYamlConfig, true);
    final Map<FQN, Object> templateFqnToValueMap = templateYamlConfig.getFqnToValueMap();
    final Map<FQN, Object> inputFqnToValueMap = new HashMap<>(templateFqnToValueMap.size());

    templateFqnToValueMap.forEach((key, value) -> {
      if (key.isType() || key.isIdentifierOrVariableName()) {
        inputFqnToValueMap.put(key, value);
      } else {
        inputFqnToValueMap.put(key, EMPTY_STRING_NODE);
      }
    });

    return new ArrayList<>(
        getInvalidFQNsInInputSetFromTemplateConfig(templateFqnToValueMap, inputFqnToValueMap).keySet());
  }
}
