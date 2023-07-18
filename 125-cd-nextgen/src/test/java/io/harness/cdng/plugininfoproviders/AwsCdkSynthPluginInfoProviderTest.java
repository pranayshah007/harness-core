/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_APP_PATH;
import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_COMMAND_OPTIONS;
import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_EXPORT_SYNTH_TEMPLATE;
import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_STACK_NAMES;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.provision.awscdk.AwsCdkSynthStepInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.extended.ci.container.ContainerResource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AwsCdkSynthPluginInfoProviderTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks @Spy private AwsCdkSynthPluginInfoProvider awsCdkSynthPluginInfoProvider;

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetPluginInfo() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();

    String jsonNode = "jsonNdod";
    PluginCreationRequest pluginCreationRequest = PluginCreationRequest.newBuilder().setStepJsonNode(jsonNode).build();
    CdAbstractStepNode cdAbstractStepNode = mock(CdAbstractStepNode.class);
    doReturn("identifier").when(cdAbstractStepNode).getIdentifier();
    doReturn("name").when(cdAbstractStepNode).getName();
    doReturn("uuid").when(cdAbstractStepNode).getUuid();

    doReturn(cdAbstractStepNode).when(awsCdkSynthPluginInfoProvider).getCdAbstractStepNode(any(), any());
    AwsCdkSynthStepInfo awsCdkSynthStepInfo =
        AwsCdkSynthStepInfo.infoBuilder()
            .resources(ContainerResource.builder().build())
            .runAsUser(ParameterField.<Integer>builder().value(1).build())
            .imagePullPolicy(ParameterField.<ImagePullPolicy>builder().value(ImagePullPolicy.ALWAYS).build())
            .image(ParameterField.<String>builder().value("image").build())
            .connectorRef(ParameterField.<String>builder().value("connectorRef").build())
            .appPath(ParameterField.<String>builder().value("appPath").build())
            .commandOptions(ParameterField.<List<String>>builder().value(Arrays.asList("test")).build())
            .exportTemplate(ParameterField.<Boolean>builder().value(Boolean.TRUE).build())
            .stackNames(ParameterField.<List<String>>builder().value(Arrays.asList("stack1", "stack2")).build())
            .build();
    doReturn(awsCdkSynthStepInfo).when(cdAbstractStepNode).getStepSpecType();

    PluginCreationResponseWrapper pluginCreationResponseWrapper =
        awsCdkSynthPluginInfoProvider.getPluginInfo(pluginCreationRequest, new HashSet<>(), ambiance);
    assertThat(pluginCreationResponseWrapper.getResponse().getPluginDetails().getEnvVariablesMap().get(
                   PLUGIN_AWS_CDK_APP_PATH))
        .isEqualTo("appPath");
    assertThat(pluginCreationResponseWrapper.getResponse().getPluginDetails().getEnvVariablesMap().get(
                   PLUGIN_AWS_CDK_EXPORT_SYNTH_TEMPLATE))
        .isEqualTo("true");
    assertThat(pluginCreationResponseWrapper.getResponse().getPluginDetails().getEnvVariablesMap().get(
                   PLUGIN_AWS_CDK_COMMAND_OPTIONS))
        .isEqualTo("test");
    assertThat(pluginCreationResponseWrapper.getResponse().getPluginDetails().getEnvVariablesMap().get(
                   PLUGIN_AWS_CDK_STACK_NAMES))
        .isEqualTo("stack1 stack2");

    assertThat(pluginCreationResponseWrapper.getStepInfo().getIdentifier()).isEqualTo("identifier");
    assertThat(pluginCreationResponseWrapper.getStepInfo().getName()).isEqualTo("name");
    assertThat(pluginCreationResponseWrapper.getStepInfo().getUuid()).isEqualTo("uuid");
  }
}
