/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.merger.helpers.InputSetMergeHelper.mergeInputSetIntoPipeline;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateFromPipeline;
import static io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType.INPUT_SET;
import static io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType.OVERLAY_INPUT_SET;
import static io.harness.pms.ngpipeline.inputset.helpers.InputSetErrorsHelper.getInvalidFQNsInInputSet;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.inputset.InputSetErrorResponseDTOPMS;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class InputSetErrorsHelperTest extends CategoryTest {
  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetErrorMap() {
    String filename = "pipeline-extensive.yml";
    String yaml = readFile(filename);

    String inputSetWrongFile = "inputSetWrong1.yml";
    String inputSetWrongYaml = readFile(inputSetWrongFile);

    InputSetErrorWrapperDTOPMS errorWrapperDTOPMS = InputSetErrorsHelper.getErrorMap(yaml, inputSetWrongYaml);
    assertThat(errorWrapperDTOPMS).isNotNull();
    assertThat(errorWrapperDTOPMS.getErrorPipelineYaml())
        .isEqualTo("pipeline:\n"
            + "  identifier: \"Test_Pipline11\"\n"
            + "  stages:\n"
            + "  - stage:\n"
            + "      identifier: \"qaStage\"\n"
            + "      type: \"Deployment\"\n"
            + "      spec:\n"
            + "        execution:\n"
            + "          steps:\n"
            + "          - step:\n"
            + "              identifier: \"httpStep1\"\n"
            + "              type: \"Http\"\n"
            + "              spec:\n"
            + "                method: \"pipeline.stages.qaStage.spec.execution.steps.httpStep1.spec.method\"\n");
    assertThat(errorWrapperDTOPMS.getUuidToErrorResponseMap().size()).isEqualTo(1);
    assertThat(errorWrapperDTOPMS.getUuidToErrorResponseMap().containsKey(
                   "pipeline.stages.qaStage.spec.execution.steps.httpStep1.spec.method"))
        .isTrue();

    String inputSetFile = "inputSet1.yml";
    String inputSetYaml = readFile(inputSetFile);
    InputSetErrorWrapperDTOPMS emptyErrorWrapperDTOPMS = InputSetErrorsHelper.getErrorMap(yaml, inputSetYaml);
    assertThat(emptyErrorWrapperDTOPMS).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetErrorMapForInputSetValidators() {
    String yamlFile = "pipeline-with-input-set-validators.yaml";
    String pipelineYaml = readFile(yamlFile);

    String inputSetCorrectFile = "input-set-for-validators.yaml";
    String inputSetCorrect = readFile(inputSetCorrectFile);

    InputSetErrorWrapperDTOPMS errorMap = InputSetErrorsHelper.getErrorMap(pipelineYaml, inputSetCorrect);
    assertThat(errorMap).isNull();

    String inputSetWrongFile = "wrong-input-set-for-validators.yaml";
    String inputSetWrong = readFile(inputSetWrongFile);

    InputSetErrorWrapperDTOPMS errorMapWrong = InputSetErrorsHelper.getErrorMap(pipelineYaml, inputSetWrong);
    assertThat(errorMapWrong).isNotNull();
    Map<String, InputSetErrorResponseDTOPMS> uuidToErrorResponseMap = errorMapWrong.getUuidToErrorResponseMap();
    assertThat(uuidToErrorResponseMap.size()).isEqualTo(5);
    assertThat(uuidToErrorResponseMap.containsKey("pipeline.variables.port2")).isTrue();
    assertThat(
        uuidToErrorResponseMap.containsKey(
            "pipeline.stages.qaStage.spec.service.serviceDefinition.spec.manifests.baseValues.spec.store.spec.connectorRef"))
        .isTrue();
    assertThat(uuidToErrorResponseMap.containsKey("pipeline.stages.qaStage.spec.infrastructure.environment.tags.team"))
        .isTrue();
    assertThat(uuidToErrorResponseMap.containsKey(
                   "pipeline.stages.qaStage.spec.infrastructure.infrastructureDefinition.spec.namespace"))
        .isTrue();
    assertThat(uuidToErrorResponseMap.containsKey("pipeline.stages.qaStage.spec.execution.steps.httpStep2.spec.method"))
        .isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetUuidToErrorResponseMap() {
    String filename = "pipeline-extensive.yml";
    String yaml = readFile(filename);

    String runtimeInputWrongFile = "runtimeInputWrong1.yml";
    String runtimeInputWrong = readFile(runtimeInputWrongFile);

    Map<String, InputSetErrorResponseDTOPMS> uuidToErrorResponseMap =
        InputSetErrorsHelper.getUuidToErrorResponseMap(yaml, runtimeInputWrong);
    assertThat(uuidToErrorResponseMap).isNotNull();
    assertThat(uuidToErrorResponseMap.size()).isEqualTo(2);
    assertThat(uuidToErrorResponseMap.containsKey("pipeline.stages.qaStage.spec.execution.steps.httpStep1.spec.method"))
        .isTrue();
    assertThat(uuidToErrorResponseMap.containsKey("pipeline.stages.qaStage.absolutelyWrongKey")).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInvalidInputSetReferences() {
    List<String> identifiers = Arrays.asList("i1", "i2", "i3", "i4");
    List<Optional<InputSetEntity>> inputSets = new ArrayList<>();
    inputSets.add(Optional.of(InputSetEntity.builder().isInvalid(false).inputSetEntityType(INPUT_SET).build()));
    inputSets.add(Optional.of(InputSetEntity.builder().isInvalid(true).inputSetEntityType(INPUT_SET).build()));
    inputSets.add(Optional.of(InputSetEntity.builder().isInvalid(false).inputSetEntityType(OVERLAY_INPUT_SET).build()));
    inputSets.add(Optional.empty());
    Map<String, String> invalidInputSetReferences =
        InputSetErrorsHelper.getInvalidInputSetReferences(inputSets, identifiers);
    assertThat(invalidInputSetReferences).hasSize(3);
    assertThat(invalidInputSetReferences.get("i2")).isEqualTo("Reference is an outdated input set");
    assertThat(invalidInputSetReferences.get("i3")).isEqualTo("References can't be other overlay input sets");
    assertThat(invalidInputSetReferences.get("i4")).isEqualTo("Reference does not exist");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInvalidFQNsInInputSet() {
    String filename = "pipeline-extensive.yml";
    String yaml = readFile(filename);
    String templateYaml = createTemplateFromPipeline(yaml);

    String inputSet = "runtimeInput1.yml";
    String inputSetYaml = readFile(inputSet);

    Set<FQN> invalidFQNs = InputSetErrorsHelper.getInvalidFQNsInInputSet(templateYaml, inputSetYaml).keySet();
    assertThat(invalidFQNs).isEmpty();

    String inputSetWrong = "runtimeInputWrong1.yml";
    String inputSetYamlWrong = readFile(inputSetWrong);

    invalidFQNs = InputSetErrorsHelper.getInvalidFQNsInInputSet(templateYaml, inputSetYamlWrong).keySet();
    assertThat(invalidFQNs.size()).isEqualTo(2);
    String invalidFQN1 =
        "pipeline.stages.stage[identifier:qaStage].spec.execution.steps.step[identifier:httpStep1].spec.method.";
    String invalidFQN2 = "pipeline.stages.stage[identifier:qaStage].absolutelyWrongKey.";
    assertThat(invalidFQNs.stream().map(FQN::display).collect(Collectors.toList()).contains(invalidFQN1)).isTrue();
    assertThat(invalidFQNs.stream().map(FQN::display).collect(Collectors.toList()).contains(invalidFQN2)).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testInputSetValidatorsOnListOfStrings() {
    String yamlFile = "paths-with-validators-pipeline.yaml";
    String yaml = readFile(yamlFile);
    String template = createTemplateFromPipeline(yaml);
    assertThat(template).isNotNull();

    String runtimeInputFile = "paths-with-validators-runtime-input.yaml";
    String runtimeInput = readFile(runtimeInputFile);
    String mergedYaml = mergeInputSetIntoPipeline(yaml, runtimeInput, true);
    String fullYamlFile = "paths-with-validators-merged.yaml";
    String fullYaml = readFile(fullYamlFile);
    assertThat(mergedYaml).isEqualTo(fullYaml);

    Map<FQN, String> noInvalidFQNsInInputSet = getInvalidFQNsInInputSet(template, runtimeInput);
    assertThat(noInvalidFQNsInInputSet).isEmpty();

    String runtimeInputFileWrong = "paths-with-validators-runtime-input-wrong.yaml";
    String runtimeInputWrong = readFile(runtimeInputFileWrong);
    Map<FQN, String> invalidFQNsInInputSet = getInvalidFQNsInInputSet(template, runtimeInputWrong);
    assertThat(invalidFQNsInInputSet.size()).isEqualTo(2);
    List<String> invalidFQNStrings =
        invalidFQNsInInputSet.keySet().stream().map(FQN::display).collect(Collectors.toList());
    String invalidFQN1 =
        "pipeline.stages.stage[identifier:d1].spec.serviceConfig.serviceDefinition.spec.manifests.manifest[identifier:m1].spec.store.spec.paths.";
    String invalidFQN2 =
        "pipeline.stages.stage[identifier:d1].spec.serviceConfig.serviceDefinition.spec.manifests.manifest[identifier:m2].spec.store.spec.paths.";
    assertThat(invalidFQNStrings.contains(invalidFQN1)).isTrue();
    assertThat(invalidFQNStrings.contains(invalidFQN2)).isTrue();
  }
}
