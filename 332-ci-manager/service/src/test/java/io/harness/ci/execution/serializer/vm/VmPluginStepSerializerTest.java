/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer.vm;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.serializer.vm.VmPluginStepSerializer;
import io.harness.ci.utils.HarnessImageUtils;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Map;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CI)
public class VmPluginStepSerializerTest extends CategoryTest {
  @Mock private ConnectorUtils connectorUtils;
  @Mock private HarnessImageUtils harnessImageUtils;

  @InjectMocks private VmPluginStepSerializer vmPluginStepSerializer;
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                            "projectIdentfier", "orgIdentifier", "orgIdentifier"))
                                        .build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testPluginStepSerialize() {
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .image(ParameterField.createValueField("image"))
            .privileged(ParameterField.createValueField(true))
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of(
                "key1", ParameterField.createValueField("val1"), "key2", ParameterField.createValueField("val2"))))
            .build();
    VmPluginStep vmPluginStep =
        (VmPluginStep) vmPluginStepSerializer.serialize(pluginStepInfo, null, "id", null, null, ambiance, null, null);
    assertThat(vmPluginStep.isPrivileged()).isTrue();
    assertThat(vmPluginStep.getImage()).isEqualTo("image");
    assertThat(vmPluginStep.getEnvVariables()).isEqualTo(Map.of("key1", "val1", "key2", "val2"));
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testPluginStepSerializeGitClone() {
    PluginStepInfo pluginStepInfo =
        PluginStepInfo.builder()
            .image(ParameterField.createValueField("image"))
            .privileged(ParameterField.createValueField(true))
            .harnessManagedImage(true)
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of(
                "key1", ParameterField.createValueField("val1"), "key2", ParameterField.createValueField("val2"))))
            .build();
    VmPluginStep vmPluginStep = (VmPluginStep) vmPluginStepSerializer.serialize(
        pluginStepInfo, null, "harness-git-clone", null, null, ambiance, null, null);
    assertThat(vmPluginStep.isPrivileged()).isTrue();
    assertThat(vmPluginStep.getImage()).isEqualTo("image");
    assertThat(vmPluginStep.getEnvVariables()).isEqualTo(Map.of("key1", "val1", "key2", "val2"));
  }
}
