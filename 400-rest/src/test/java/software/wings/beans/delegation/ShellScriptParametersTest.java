/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.delegation;

import static io.harness.annotations.dev.HarnessModule._950_DELEGATE_TASKS_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PARDHA;
import static io.harness.rule.OwnerRule.RAFAEL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.rule.Owner;

import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.service.impl.ContainerServiceParams;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@TargetModule(_950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDP)
public class ShellScriptParametersTest extends CategoryTest {
  @Mock ContainerServiceParams containerServiceParams;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PARDHA)
  @Category(UnitTests.class)
  public void fetchRequiredExecutionCapabilitiesWithoutInfraSelectors() {
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setValue(KubernetesClusterConfig.builder().useKubernetesDelegate(true).build());
    when(containerServiceParams.getSettingAttribute()).thenReturn(settingAttribute.toDTO());
    when(containerServiceParams.fetchRequiredExecutionCapabilities(null))
        .thenReturn(Arrays.asList(SelectorCapability.builder().build()));
    ShellScriptParameters shellScriptParameters = ShellScriptParameters.builder()
                                                      .script("")
                                                      .executeOnDelegate(true)
                                                      .containerServiceParams(containerServiceParams)
                                                      .includeInfraSelectors(false)
                                                      .build();
    assertThat(shellScriptParameters.fetchRequiredExecutionCapabilities(null)).isEmpty();

    shellScriptParameters.setScript("HARNESS_KUBE_CONFIG_PATH");
    assertThat(shellScriptParameters.fetchRequiredExecutionCapabilities(null)).isEmpty();
  }

  @Test
  @Owner(developers = PARDHA)
  @Category(UnitTests.class)
  public void fetchRequiredExecutionCapabilitiesWithInfraSelectors() {
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setValue(KubernetesClusterConfig.builder().useKubernetesDelegate(true).build());
    when(containerServiceParams.getSettingAttribute()).thenReturn(settingAttribute.toDTO());
    when(containerServiceParams.fetchRequiredExecutionCapabilities(null))
        .thenReturn(Arrays.asList(SelectorCapability.builder().build()));
    ShellScriptParameters shellScriptParameters = ShellScriptParameters.builder()
                                                      .script("")
                                                      .executeOnDelegate(true)
                                                      .containerServiceParams(containerServiceParams)
                                                      .includeInfraSelectors(true)
                                                      .build();
    assertThat(shellScriptParameters.fetchRequiredExecutionCapabilities(null))
        .containsExactly(SelectorCapability.builder().build());

    shellScriptParameters.setScript("HARNESS_KUBE_CONFIG_PATH");
    assertThat(shellScriptParameters.fetchRequiredExecutionCapabilities(null))
        .containsExactly(SelectorCapability.builder().build());
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void testEscapedServiceEnv() {
    Map<String, String> hash = new HashMap<>();
    hash.put("key1", "test$(uname -r)test");
    hash.put("key2", "test$(uname -r)test$(ls)test");

    ShellScriptParameters shellScriptParameters = ShellScriptParameters.builder().serviceVariables(hash).build();
    Map<String, String> escaped = shellScriptParameters.getEscapedServiceVariables();

    assertThat(escaped.get("key1").equalsIgnoreCase("test\\$(uname -r)test")).isTrue();
    assertThat(escaped.get("key2").equalsIgnoreCase("test\\$(uname -r)test\\$(ls)test")).isTrue();
  }
}
