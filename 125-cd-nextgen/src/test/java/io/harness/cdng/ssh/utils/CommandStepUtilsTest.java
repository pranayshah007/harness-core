/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ssh.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.shell.ScriptType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class CommandStepUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetWorkingDirectory() {
    ParameterField<Boolean> onDelegate = ParameterField.createValueField(true);
    assertThat(CommandStepUtils.getWorkingDirectory(ParameterField.ofNull(), ScriptType.BASH, onDelegate.getValue()))
        .isEqualTo("/tmp");
    assertThat(
        CommandStepUtils.getWorkingDirectory(ParameterField.ofNull(), ScriptType.POWERSHELL, onDelegate.getValue()))
        .isEqualTo("/tmp");
    onDelegate = ParameterField.createValueField(false);
    assertThat(
        CommandStepUtils.getWorkingDirectory(ParameterField.ofNull(), ScriptType.POWERSHELL, onDelegate.getValue()))
        .isEqualTo("%TEMP%");

    ParameterField<String> workingDirectory = ParameterField.createValueField("dir");
    assertThat(CommandStepUtils.getWorkingDirectory(workingDirectory, ScriptType.BASH, onDelegate.getValue()))
        .isEqualTo("dir");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetEnvironmentVariables() {
    assertThat(CommandStepUtils.mergeEnvironmentVariables(null, Collections.emptyMap())).isEmpty();
    assertThat(CommandStepUtils.mergeEnvironmentVariables(new HashMap<>(), Collections.emptyMap())).isEmpty();

    Map<String, Object> envVariables = new HashMap<>();
    envVariables.put("var1", Arrays.asList(1));
    envVariables.put("var2", "val2");
    envVariables.put("var3", ParameterField.createValueField("val3"));
    envVariables.put("var4", ParameterField.createExpressionField(true, "<+unresolved>", null, true));

    assertThatThrownBy(() -> CommandStepUtils.mergeEnvironmentVariables(envVariables, Collections.emptyMap()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Env. variable [var4] value found to be null");

    envVariables.remove("var4");
    Map<String, String> environmentVariables =
        CommandStepUtils.mergeEnvironmentVariables(envVariables, Collections.emptyMap());
    assertThat(environmentVariables).hasSize(2);
    assertThat(environmentVariables.get("var2")).isEqualTo("val2");
    assertThat(environmentVariables.get("var3")).isEqualTo("val3");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetOutputVariableValuesWithoutSecrets() {
    Map<String, Object> outputVariables = new HashMap<>();
    outputVariables.put("str-var-name", ParameterField.createValueField("str-var"));
    outputVariables.put("number-var-name", ParameterField.createValueField(10.0));
    outputVariables.put("secret-var-name", ParameterField.createValueField("secret-var"));
    outputVariables.put("plain-str-var-name", "plain-str-var");

    List<String> outputVariableValuesWithoutSecrets =
        CommandStepUtils.getOutputVariableValuesWithoutSecrets(outputVariables, Set.of("secret-var-name"));
    assertThat(outputVariableValuesWithoutSecrets).size().isEqualTo(2);
    assertThat(outputVariableValuesWithoutSecrets).contains("10.0", "str-var");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetSecretOutputVariableValuesAndNames() {
    Map<String, Object> outputVariables = new HashMap<>();
    outputVariables.put("str-var-name", ParameterField.createValueField("str-var"));
    outputVariables.put("number-var-name", ParameterField.createValueField(10.0));
    outputVariables.put("secret-var-name-1", ParameterField.createValueField("secret-var-1"));
    outputVariables.put("secret-var-name-2", ParameterField.createValueField("secret-var-2"));
    outputVariables.put("plain-str-var-name", "plain-str-var");

    List<String> secretOutputVariables = CommandStepUtils.getSecretOutputVariableValues(
        outputVariables, Set.of("secret-var-name-1", "secret-var-name-2"));

    assertThat(secretOutputVariables).size().isEqualTo(2);
    assertThat(secretOutputVariables).contains("secret-var-1", "secret-var-2");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testPrepareOutputVariables() {
    Map<String, Object> outputVariables = new HashMap<>();
    outputVariables.put("str-var-name", ParameterField.createValueField("str-var"));
    outputVariables.put("number-var-name", ParameterField.createValueField(10.0));
    outputVariables.put("secret-var-name", ParameterField.createValueField("secret-var"));
    outputVariables.put("plain-str-var-name", "plain-str-var");

    Map<String, String> sweepingOutputEnvVariables = new HashMap<>();
    sweepingOutputEnvVariables.put("str-var", "resolved-str-var");
    sweepingOutputEnvVariables.put("secret-var", "resolved-secret-var");
    sweepingOutputEnvVariables.put("plain-str-var", "resolved-plain-str-var");

    Map<String, String> secretOutputVariables =
        CommandStepUtils.prepareOutputVariables(sweepingOutputEnvVariables, outputVariables, Set.of("secret-var-name"));

    assertThat(secretOutputVariables).size().isEqualTo(3);
    assertThat(secretOutputVariables.get("str-var-name")).isEqualTo("resolved-str-var");
    assertThat(secretOutputVariables.get("secret-var-name"))
        .contains("${sweepingOutputSecrets.obtain(\"secret-var-name\"");
    assertThat(secretOutputVariables.get("number-var-name")).isNull();
  }
}
