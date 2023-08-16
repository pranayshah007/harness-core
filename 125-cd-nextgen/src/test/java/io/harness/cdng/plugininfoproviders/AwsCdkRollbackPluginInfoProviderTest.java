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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.provision.awscdk.AwsCdkConfigDAL;
import io.harness.cdng.provision.awscdk.AwsCdkRollbackStepInfo;
import io.harness.cdng.provision.awscdk.beans.AwsCdkConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
public class AwsCdkRollbackPluginInfoProviderTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private AwsCdkConfigDAL awsCdkConfigDAL;
  @InjectMocks @Spy private AwsCdkRollbackPluginInfoProvider awsCdkRollbackPluginInfoProvider;
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
    AwsCdkRollbackStepInfo awsCdkRollbackStepInfo =
        AwsCdkRollbackStepInfo.infoBuilder().envVariables(stepEnvVars).build();
    doReturn(awsCdkRollbackStepInfo).when(cdAbstractStepNode).getStepSpecType();
    doReturn(AwsCdkConfig.builder()
                 .imagePullPolicy(ImagePullPolicy.ALWAYS)
                 .image("image")
                 .connectorRef("connectorRef")
                 .envVariables(envVars)
                 .build())
        .when(awsCdkConfigDAL)
        .getRollbackAwsCdkConfig(any(), any());
    doReturn(cdAbstractStepNode).when(awsCdkRollbackPluginInfoProvider).getCdAbstractStepNode(any(), any());
    doReturn(awsCdkRollbackStepInfo).when(cdAbstractStepNode).getStepSpecType();

    PluginCreationResponseWrapper pluginCreationResponseWrapper =
        awsCdkRollbackPluginInfoProvider.getPluginInfo(pluginCreationRequest, new HashSet<>(), ambiance);

    assertThat(pluginCreationResponseWrapper.getStepInfo().getIdentifier()).isEqualTo("identifier");
    assertThat(pluginCreationResponseWrapper.getStepInfo().getName()).isEqualTo("name");
    assertThat(pluginCreationResponseWrapper.getStepInfo().getUuid()).isEqualTo("uuid");
    assertThat(pluginCreationResponseWrapper.getResponse().getPluginDetails().getEnvVariablesMap().get("key1"))
        .isEqualTo("value1");
    assertThat(pluginCreationResponseWrapper.getResponse().getPluginDetails().getEnvVariablesMap().get("stepKey1"))
        .isEqualTo("stepValue1");
  }
}
