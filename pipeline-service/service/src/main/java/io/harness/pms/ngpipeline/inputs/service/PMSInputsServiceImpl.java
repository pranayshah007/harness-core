/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputs.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.ngpipeline.inputs.beans.entity.InputEntity;
import io.harness.pms.ngpipeline.inputs.beans.entity.InputEntityType;
import io.harness.pms.ngpipeline.inputs.beans.entity.OptionsInput;
import io.harness.pms.ngpipeline.inputs.beans.entity.OptionsInput.CloneInput;
import io.harness.pms.ngpipeline.inputs.beans.entity.OptionsInput.CloneInput.RefInput;
import io.harness.pms.yaml.OptionUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.clone.RefType;
import io.harness.yaml.options.Options;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PMSInputsServiceImpl implements PMSInputsService {
  @Inject private ObjectMapper objectMapper;

  @Override
  public Optional<Map<String, InputEntity>> get(String pipelineYaml) {
    YamlField pipelineField = getPipelineField(pipelineYaml);
    Map<String, InputEntity> inputEntitiesMap = new HashMap<>();
    YamlField inputsField = pipelineField.getNode().getField(YAMLFieldNameConstants.INPUTS);
    if (inputsField != null) {
      inputEntitiesMap = objectMapper.convertValue(inputsField.getNode().getCurrJsonNode(), new TypeReference<>() {});
    }
    return Optional.of(inputEntitiesMap);
  }

  public Optional<OptionsInput> getOptions(String pipelineYaml) {
    Optional<Options> optionalOptions = OptionUtils.getOptions(pipelineYaml);
    if (optionalOptions.isPresent() && optionalOptions.get().getClone() != null
        && optionalOptions.get().getClone().isDisabled()) {
      return Optional.empty();
    }
    return Optional.of(
        OptionsInput.builder()
            .clone(CloneInput.builder()
                       .ref(RefInput.builder()
                                .type(InputEntity.builder()
                                          .type(InputEntityType.STRING)
                                          .required(true)
                                          .enums(List.of(RefType.values()))
                                          .build())
                                .name(InputEntity.builder().type(InputEntityType.STRING).required(true).build())
                                .build())
                       .build())
            .build());
  }

  private YamlField getPipelineField(String pipelineYaml) {
    try {
      return YamlUtils.readTree(pipelineYaml);
    } catch (IOException ex) {
      String message = "Invalid yaml during getInputs request";
      log.error(message, ex);
      throw new InvalidRequestException(message);
    }
  }
}
