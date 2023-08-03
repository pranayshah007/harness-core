/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_NAME;
import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.yaml.extended.reports.UnitTestReport;
import io.harness.callback.DelegateCallbackToken;
import io.harness.category.element.UnitTests;
import io.harness.ci.serializer.PluginStepProtobufSerializer;
import io.harness.ci.serializer.SerializerUtils;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.rule.Owner;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CI)
@RunWith(MockitoJUnitRunner.class)
public class PluginStepProtobufSerializerTest {
  private static final String TOKEN = "token";
  private static final String LOG_KEY = "logkey";
  private static final String CALLBACK = "callback";
  private static final String CLONE_CODEBASE_STEP = "cloneCodebaseStep";
  private static final String ACCOUNT_ID = "accountID";
  private static final String PLUGIN_DEPTH = "PLUGIN_DEPTH";
  private static final Integer PORT = 2023;
  private static final Long TIMEOUT = 700000L;

  @Mock Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Mock private SerializerUtils serializerUtils;

  @InjectMocks private PluginStepProtobufSerializer pluginStepProtobufSerializer;

  private PluginStepInfo preparePluginStepInfo() {
    return PluginStepInfo.builder()
        .identifier(GIT_CLONE_STEP_ID)
        .image(ParameterField.createValueField("testImage"))
        .name(GIT_CLONE_STEP_NAME)
        .entrypoint(ParameterField.createValueField(Arrays.asList("gitclone")))
        .harnessManagedImage(true)
        .reports(ParameterField.createValueField(UnitTestReport.builder().build()))
        .settings(ParameterField.createValueField(new HashMap<>()))
        .build();
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testDefaultCloneCodebaseDept() {
    PluginStepInfo pluginStepInfo = preparePluginStepInfo();

    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(TOKEN).build());

    UnitStep unitStep = pluginStepProtobufSerializer.serializeStepWithStepParameters(pluginStepInfo, PORT, CALLBACK,
        LOG_KEY, GIT_CLONE_STEP_ID, ParameterField.createValueField(Timeout.builder().timeoutInMillis(TIMEOUT).build()),
        ACCOUNT_ID, CLONE_CODEBASE_STEP, ManualExecutionSource.builder().branch("main").build(), "podname",
        Ambiance.newBuilder().build());
    assertThat(unitStep.getPlugin().getEnvironmentMap().get(PLUGIN_DEPTH)).isEqualTo("50");
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testSpecificCloneCodebaseDept() {
    PluginStepInfo pluginStepInfo = preparePluginStepInfo();

    Map<String, JsonNode> settings = new HashMap<>();
    settings.put(GIT_CLONE_DEPTH_ATTRIBUTE, JsonNodeFactory.instance.textNode("4"));
    pluginStepInfo.setSettings(ParameterField.createValueField(settings));

    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(TOKEN).build());

    UnitStep unitStep = pluginStepProtobufSerializer.serializeStepWithStepParameters(pluginStepInfo, PORT, CALLBACK,
        LOG_KEY, GIT_CLONE_STEP_ID, ParameterField.createValueField(Timeout.builder().timeoutInMillis(TIMEOUT).build()),
        ACCOUNT_ID, CLONE_CODEBASE_STEP, ManualExecutionSource.builder().branch("main").build(), "podname",
        Ambiance.newBuilder().build());
    assertThat(unitStep.getPlugin().getEnvironmentMap().get(PLUGIN_DEPTH)).isEqualTo("4");
  }
  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testFullCloneCodebaseDept() {
    PluginStepInfo pluginStepInfo = preparePluginStepInfo();

    Map<String, JsonNode> settings = new HashMap<>();
    settings.put(GIT_CLONE_DEPTH_ATTRIBUTE, JsonNodeFactory.instance.textNode("0"));
    pluginStepInfo.setSettings(ParameterField.createValueField(settings));

    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(TOKEN).build());

    UnitStep unitStep = pluginStepProtobufSerializer.serializeStepWithStepParameters(pluginStepInfo, PORT, CALLBACK,
        LOG_KEY, GIT_CLONE_STEP_ID, ParameterField.createValueField(Timeout.builder().timeoutInMillis(TIMEOUT).build()),
        ACCOUNT_ID, CLONE_CODEBASE_STEP, ManualExecutionSource.builder().branch("main").build(), "podname",
        Ambiance.newBuilder().build());
    assertThat(unitStep.getPlugin().getEnvironmentMap().get(PLUGIN_DEPTH)).isEqualTo(null);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testInvalidStepSerializer() {
    PluginStepInfo pluginStepInfo = preparePluginStepInfo();

    assertThatThrownBy(
        ()
            -> pluginStepProtobufSerializer.serializeStepWithStepParameters(pluginStepInfo, PORT, null, LOG_KEY,
                GIT_CLONE_STEP_ID, ParameterField.createValueField(Timeout.builder().timeoutInMillis(TIMEOUT).build()),
                ACCOUNT_ID, CLONE_CODEBASE_STEP, ManualExecutionSource.builder().branch("main").build(), "podname",
                Ambiance.newBuilder().build()))
        .isInstanceOf(CIStageExecutionException.class);

    assertThatThrownBy(
        ()
            -> pluginStepProtobufSerializer.serializeStepWithStepParameters(pluginStepInfo, null, "abc", LOG_KEY,
                GIT_CLONE_STEP_ID, ParameterField.createValueField(Timeout.builder().timeoutInMillis(TIMEOUT).build()),
                ACCOUNT_ID, CLONE_CODEBASE_STEP, ManualExecutionSource.builder().branch("main").build(), "podname",
                Ambiance.newBuilder().build()))
        .isInstanceOf(CIStageExecutionException.class);
  }
}
