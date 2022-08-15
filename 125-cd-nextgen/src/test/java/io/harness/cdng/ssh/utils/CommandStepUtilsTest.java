/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ssh.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.ssh.utils.CommandStepUtils.HARNESS_BACKUP_PATH;
import static io.harness.cdng.ssh.utils.CommandStepUtils.HARNESS_RUNTIME_PATH;
import static io.harness.cdng.ssh.utils.CommandStepUtils.HARNESS_STAGING_PATH;
import static io.harness.cdng.ssh.utils.CommandStepUtils.WINDOWS_RUNTIME_PATH;
import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.shell.ScriptType;
import io.harness.steps.environment.EnvironmentOutcome;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    assertThat(CommandStepUtils.getEnvironmentVariables(null, Collections.emptyMap())).isEmpty();
    assertThat(CommandStepUtils.getEnvironmentVariables(new HashMap<>(), Collections.emptyMap())).isEmpty();

    Map<String, Object> envVariables = new HashMap<>();
    envVariables.put("var1", Arrays.asList(1));
    envVariables.put("var2", "val2");
    envVariables.put("var3", ParameterField.createValueField("val3"));
    envVariables.put("var4", ParameterField.createExpressionField(true, "<+unresolved>", null, true));

    assertThatThrownBy(() -> CommandStepUtils.getEnvironmentVariables(envVariables, Collections.emptyMap()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Env. variable [var4] value found to be null");

    envVariables.remove("var4");
    Map<String, String> environmentVariables =
        CommandStepUtils.getEnvironmentVariables(envVariables, Collections.emptyMap());
    assertThat(environmentVariables).hasSize(2);
    assertThat(environmentVariables.get("var2")).isEqualTo("val2");
    assertThat(environmentVariables.get("var3")).isEqualTo("val3");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetBuildInEnvironmentVariables() {
    ServiceStepOutcome sshServiceOutcome = ServiceStepOutcome.builder().type("Ssh").name("ssh-svc").build();

    PdcInfrastructureOutcome pdcInfrastructure = PdcInfrastructureOutcome.builder()
                                                     .connectorRef("pdcConnector")
                                                     .credentialsRef("sshKeyRef")
                                                     .environment(EnvironmentOutcome.builder().name("env").build())
                                                     .build();

    Map<String, String> environmentVariables =
        CommandStepUtils.getHarnessBuiltInEnvVariables(pdcInfrastructure, sshServiceOutcome);
    assertThat(environmentVariables).hasSize(3);
    assertThat(environmentVariables.get(HARNESS_BACKUP_PATH)).isEqualTo("$HOME/ssh/ssh-svc/env/backup");
    assertThat(environmentVariables.get(HARNESS_RUNTIME_PATH)).isEqualTo("$HOME/ssh/ssh-svc/env/runtime");
    assertThat(environmentVariables.get(HARNESS_STAGING_PATH)).isEqualTo("$HOME/ssh/ssh-svc/env/staging");

    ServiceStepOutcome winRmServiceOutcome = ServiceStepOutcome.builder().type("WinRm").name("winrm-svc").build();
    Map<String, String> winRmenvironmentVariables =
        CommandStepUtils.getHarnessBuiltInEnvVariables(pdcInfrastructure, winRmServiceOutcome);
    assertThat(winRmenvironmentVariables).hasSize(1);
    assertThat(winRmenvironmentVariables.get(WINDOWS_RUNTIME_PATH))
        .isEqualTo("%USERPROFILE%\\winrm\\winrm-svc\\env\\runtime");
  }
}
