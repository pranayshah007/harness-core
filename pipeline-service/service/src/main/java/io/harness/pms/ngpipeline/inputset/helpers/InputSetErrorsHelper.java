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
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.merger.helpers.YamlSubMapExtractor;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;

import java.util.Collections;
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

  public InputSetErrorWrapperDTOPMS getErrorMap(String pipelineYaml, String inputSetYaml, String inputSetIdentifier) {
    String pipelineComp = getPipelineComponent(inputSetYaml);
    String templateYaml = createTemplateFromPipeline(pipelineYaml);
    Map<FQN, String> invalidFQNs = getInvalidFQNsInInputSet(templateYaml, pipelineComp);
    if (EmptyPredicate.isEmpty(invalidFQNs)) {
      return null;
    }

    String errorPipelineYaml = getErrorPipelineYaml(invalidFQNs.keySet(), pipelineYaml);
    Map<String, InputSetErrorResponseDTOPMS> uuidToErrorResponseMap =
        getUuidToErrorResponseMap(invalidFQNs, inputSetIdentifier);
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
    Map<FQN, String> invalidFQNs = getFQNsWithFailingValidatorsInInputSet(templateYaml, inputSetPipelineComponent);
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
        InputSetErrorWrapperDTOPMS errorMap = getErrorMap(pipelineYaml, inputSetYaml, inputSetEntity.getIdentifier());
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
    return getInvalidFQNsInInputSetFromTemplateConfig(templateConfig, inputSetConfig, false);
  }

  public Map<FQN, String> getFQNsWithFailingValidatorsInInputSet(String templateYaml, String inputSetPipelineCompYaml) {
    YamlConfig inputSetConfig = new YamlConfig(inputSetPipelineCompYaml);
    YamlConfig templateConfig = EmptyPredicate.isEmpty(templateYaml) ? null : new YamlConfig(templateYaml);
    return getInvalidFQNsInInputSetFromTemplateConfig(templateConfig, inputSetConfig, true);
  }

  public Map<FQN, String> getInvalidFQNsInInputSet(YamlConfig pipelineYamlConfig, YamlConfig inputSetConfig) {
    YamlConfig templateYamlConfig =
        RuntimeInputFormHelper.createRuntimeInputFormYamlConfig(pipelineYamlConfig, true, false);
    return getInvalidFQNsInInputSetFromTemplateConfig(templateYamlConfig, inputSetConfig, false);
  }

  Map<FQN, String> getInvalidFQNsInInputSetFromTemplateConfig(
      YamlConfig templateConfig, YamlConfig inputSetConfig, boolean checkOnlyValidators) {
    Map<FQN, String> errorMap = new LinkedHashMap<>();
    Set<FQN> inputSetFQNs = new LinkedHashSet<>(inputSetConfig.getFqnToValueMap().keySet());
    if (!checkOnlyValidators && (templateConfig == null || EmptyPredicate.isEmpty(templateConfig.getFqnToValueMap()))) {
      inputSetFQNs.forEach(fqn -> errorMap.put(fqn, "Pipeline no longer contains any runtime input"));
      return errorMap;
    } else if (templateConfig == null || EmptyPredicate.isEmpty(templateConfig.getFqnToValueMap())) {
      // empty runtime input template means that all runtime input values are redundant, and hence no need to check for
      // FQNs that fail any validator, because there is no validators in the pipeline yaml to begin with
      return null;
    }

    templateConfig.getFqnToValueMap().keySet().forEach(key -> {
      if (inputSetFQNs.contains(key)) {
        Object templateValue = templateConfig.getFqnToValueMap().get(key);
        Object valueFromRuntimeInputYaml = inputSetConfig.getFqnToValueMap().get(key);
        if (!checkOnlyValidators && (key.isType() || key.isIdentifierOrVariableName())) {
          if (!valueFromRuntimeInputYaml.toString().equals(templateValue.toString())) {
            // if the type is wrong, this means that the whole field for which the type is, is potentially (and most
            // probably) invalid. Hence, we need to mark all keys that are parallel to type as invalid. Same goes for
            // name and identifier
            FQN baseFQNOfCurrKey =
                FQN.builder().fqnList(key.getFqnList().subList(0, key.getFqnList().size() - 1)).build();
            // this sub map is of all the keys that are sibling of the `key` and their children. It also contains `key`
            // as well. All these keys are being marked invalid
            Map<FQN, Object> invalidSubMap =
                YamlSubMapExtractor.getFQNToObjectSubMap(templateConfig.getFqnToValueMap(), baseFQNOfCurrKey);
            // marking all the keys in the sub map as invalid
            for (FQN subMapKey : invalidSubMap.keySet()) {
              errorMap.put(subMapKey,
                  "The value for " + key.getExpressionFqn() + " is " + templateValue
                      + "in the pipeline yaml, but the input set has it as " + valueFromRuntimeInputYaml);
            }
          }
        } else {
          String error = validateStaticValues(templateValue, valueFromRuntimeInputYaml);
          if (EmptyPredicate.isNotEmpty(error)) {
            errorMap.put(key, error);
          }
        }

        inputSetFQNs.remove(key);
      } else {
        Map<FQN, Object> subMap = YamlSubMapExtractor.getFQNToObjectSubMap(inputSetConfig.getFqnToValueMap(), key);
        subMap.keySet().forEach(inputSetFQNs::remove);
      }
    });
    if (!checkOnlyValidators) {
      inputSetFQNs.forEach(fqn -> errorMap.put(fqn, "Field not a runtime input"));
    }
    return errorMap;
  }
}
