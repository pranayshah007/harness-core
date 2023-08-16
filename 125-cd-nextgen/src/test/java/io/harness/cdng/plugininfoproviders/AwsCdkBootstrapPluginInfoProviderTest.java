/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.provision.awscdk.AwsCdkBootstrapStepInfo;
import io.harness.cdng.provision.awscdk.AwsCdkHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.extended.ci.container.ContainerResource;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AwsCdkBootstrapPluginInfoProviderTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock AwsCdkHelper awsCdkStepHelper;
  @InjectMocks @Spy private AwsCdkBootstrapPluginInfoProvider awsCdkBootstrapPluginInfoProvider;

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetPluginInfo() {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "accountId").build();
    PluginCreationRequest pluginCreationRequest =
        PluginCreationRequest.newBuilder().setStepJsonNode("jsonNode").build();
    CdAbstractStepNode cdAbstractStepNode = mock(CdAbstractStepNode.class);
    doReturn("identifier").when(cdAbstractStepNode).getIdentifier();
    doReturn("name").when(cdAbstractStepNode).getName();
    doReturn("uuid").when(cdAbstractStepNode).getUuid();
    ParameterField<Map<String, String>> stepEnvVars =
        ParameterField.<Map<String, String>>builder().value(Collections.singletonMap("stepKey1", "stepValue1")).build();
    Map<String, String> envVars = new HashMap<>();
    envVars.put("key1", "value1");
    List<String> commandOptions = Arrays.asList("test");
    AwsCdkBootstrapStepInfo awsCdkBootstrapStepInfo =
        AwsCdkBootstrapStepInfo.infoBuilder()
            .resources(ContainerResource.builder().build())
            .runAsUser(ParameterField.<Integer>builder().value(1).build())
            .imagePullPolicy(ParameterField.<ImagePullPolicy>builder().value(ImagePullPolicy.ALWAYS).build())
            .image(ParameterField.<String>builder().value("image").build())
            .connectorRef(ParameterField.<String>builder().value("connectorRef").build())
            .appPath(ParameterField.<String>builder().value("appPath").build())
            .commandOptions(ParameterField.<List<String>>builder().value(Arrays.asList("test")).build())
            .envVariables(stepEnvVars)
            .build();
    doReturn(cdAbstractStepNode).when(awsCdkBootstrapPluginInfoProvider).getCdAbstractStepNode(any(), any());
    doReturn(awsCdkBootstrapStepInfo).when(cdAbstractStepNode).getStepSpecType();
    doReturn(envVars).when(awsCdkStepHelper).getCommonEnvVariables(any(), any(), any());

    PluginCreationResponseWrapper pluginCreationResponseWrapper =
        awsCdkBootstrapPluginInfoProvider.getPluginInfo(pluginCreationRequest, new HashSet<>(), ambiance);

    assertThat(pluginCreationResponseWrapper.getStepInfo().getIdentifier()).isEqualTo("identifier");
    assertThat(pluginCreationResponseWrapper.getStepInfo().getName()).isEqualTo("name");
    assertThat(pluginCreationResponseWrapper.getStepInfo().getUuid()).isEqualTo("uuid");
    verify(awsCdkStepHelper).getCommonEnvVariables(eq("appPath"), eq(commandOptions), eq(stepEnvVars));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetPluginDetails() {
    Map<String, String> envVars = new HashMap<>();
    envVars.put("key1", "value1");
    ImageDetails imageDetails = ImageDetails.newBuilder().build();
    ContainerResource containerResource = ContainerResource.builder().build();

    PluginDetails pluginDetails = awsCdkBootstrapPluginInfoProvider.getPluginDetails(new HashSet<>(),
        ParameterField.<Integer>builder().value(1).build(), containerResource,
        ParameterField.<Boolean>builder().value(true).build(), envVars, imageDetails);

    assertThat(pluginDetails.getEnvVariablesMap()).isEqualTo(envVars);
    assertThat(pluginDetails.getRunAsUser()).isEqualTo(1);
    assertThat(pluginDetails.getPrivileged()).isEqualTo(true);
    assertThat(pluginDetails.getImageDetails()).isEqualTo(imageDetails);
  }
}
