/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg.manifest;

import static io.harness.aws.asg.manifest.AsgManifestType.AsgLaunchTemplate;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.asg.manifest.request.AsgConfigurationManifestRequest;
import io.harness.aws.asg.manifest.request.AsgInstanceCapacity;
import io.harness.aws.asg.manifest.request.AsgLaunchTemplateManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScalingPolicyManifestRequest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class AsgManifestHandlerChainFactoryTest extends CategoryTest {
  static String asgName = "testAsg";
  static String launchTemplateManifestContent =
      "{\"LaunchTemplateData\": {\"ImageId\": \"ami-05fa00d4c63e32376\",\"InstanceType\": \"t2.micro\"}}";
  static String asgConfigurationManifestContent =
      "{\"autoScalingGroupName\": \"vit-asg5\", \"availabilityZones\": [\"us-east-1a\", \"us-east-1c\"]}";

  static String scalingPolicyManifestContent1 =
      "{\"policyName\": \"testPolicy1\", \"policyType\": \"testpolicyType1\"}";
  static String scalingPolicyManifestContent2 =
      "{\"policyName\": \"testPolicy2\", \"policyType\": \"testpolicyType2\"}";

  static Map<String, Object> asgConfigurationOverrideProperties = new HashMap<>() {
    {
      put(AsgConfigurationManifestHandler.OverrideProperties.minSize, 1);
      put(AsgConfigurationManifestHandler.OverrideProperties.maxSize, 3);
      put(AsgConfigurationManifestHandler.OverrideProperties.desiredCapacity, 2);
    }
  };

  AsgSdkManager asgSdkManager;
  AsgLaunchTemplateManifestHandler asgLaunchTemplateManifestHandler;
  AsgConfigurationManifestHandler asgConfigurationManifestHandler;
  AsgScalingPolicyManifestHandler asgScalingPolicyManifestHandler;

  @Before
  public void setUp() throws IllegalAccessException {
    asgSdkManager = Mockito.mock(AsgSdkManager.class);
    asgLaunchTemplateManifestHandler = Mockito.mock(AsgLaunchTemplateManifestHandler.class);
    asgConfigurationManifestHandler = Mockito.mock(AsgConfigurationManifestHandler.class);
    asgScalingPolicyManifestHandler = Mockito.mock(AsgScalingPolicyManifestHandler.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void shouldUpsert() {
    AsgManifestHandlerChainState state =
        AsgManifestHandlerChainState.builder().asgName(asgName).launchTemplateVersion("testVersion").build();
    doReturn(state).when(asgLaunchTemplateManifestHandler).upsert(any());
    doReturn(state).when(asgLaunchTemplateManifestHandler).upsert(any(), any());
    doCallRealMethod().when(asgLaunchTemplateManifestHandler).setNextHandler(any());
    doCallRealMethod().when(asgLaunchTemplateManifestHandler).getNextHandler();

    state = AsgManifestHandlerChainState.builder().asgName(asgName).launchTemplateVersion("testVersion2").build();
    doReturn(state).when(asgConfigurationManifestHandler).upsert(any());
    doReturn(state).when(asgConfigurationManifestHandler).upsert(any(), any());
    doCallRealMethod().when(asgConfigurationManifestHandler).setNextHandler(any());
    doCallRealMethod().when(asgConfigurationManifestHandler).getNextHandler();

    state = AsgManifestHandlerChainState.builder().asgName(asgName).launchTemplateVersion("testVersion3").build();
    doReturn(state).when(asgScalingPolicyManifestHandler).upsert(any());
    doReturn(state).when(asgScalingPolicyManifestHandler).upsert(any(), any());
    doCallRealMethod().when(asgScalingPolicyManifestHandler).setNextHandler(any());
    doCallRealMethod().when(asgScalingPolicyManifestHandler).getNextHandler();

    AsgManifestHandlerChainState result =
        AsgManifestHandlerChainFactory.builder()
            .initialChainState(AsgManifestHandlerChainState.builder().asgName(asgName).build())
            .asgSdkManager(asgSdkManager)
            .build()
            .addHandler(asgLaunchTemplateManifestHandler)
            .addHandler(asgConfigurationManifestHandler)
            .addHandler(asgScalingPolicyManifestHandler)
            .executeUpsert();

    assertThat(result.getLaunchTemplateVersion()).isEqualTo("testVersion3");

    ArgumentCaptor<AsgManifestHandlerChainState> argumentCaptor =
        ArgumentCaptor.forClass(AsgManifestHandlerChainState.class);

    verify(asgLaunchTemplateManifestHandler, times(1)).upsert(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getLaunchTemplateVersion()).isNull();
    verify(asgConfigurationManifestHandler, times(1)).upsert(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getLaunchTemplateVersion()).isEqualTo("testVersion");
    verify(asgScalingPolicyManifestHandler, times(1)).upsert(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getLaunchTemplateVersion()).isEqualTo("testVersion2");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void shouldAddHandlersUnmarshallContentsAndOverrideProperties() {
    AsgManifestHandlerChainFactory.builder()
        .build()
        .addHandler(AsgLaunchTemplate,
            AsgLaunchTemplateManifestRequest.builder().manifests(Arrays.asList(launchTemplateManifestContent)).build())
        .addHandler(AsgManifestType.AsgConfiguration,
            AsgConfigurationManifestRequest.builder()
                .manifests(Arrays.asList(asgConfigurationManifestContent))
                .overrideProperties(asgConfigurationOverrideProperties)
                .build())
        .addHandler(AsgManifestType.AsgScalingPolicy,
            AsgScalingPolicyManifestRequest.builder()
                .manifests(Arrays.asList(scalingPolicyManifestContent1, scalingPolicyManifestContent2))
                .build());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getNrOfAlreadyRunningInstances() {
    AsgConfigurationManifestHandler asgConfigurationManifestHandler = new AsgConfigurationManifestHandler(null, null);
    AutoScalingGroup autoScalingGroup = new AutoScalingGroup().withDesiredCapacity(3).withMinSize(1).withMaxSize(5);
    AsgConfigurationManifestRequest asgConfigurationManifestRequest = AsgConfigurationManifestRequest.builder().build();
    AsgInstanceCapacity ret =
        asgConfigurationManifestHandler.getRunningInstanceCapacity(autoScalingGroup, asgConfigurationManifestRequest);
    assertThat(ret.getDesiredCapacity()).isEqualTo(3);
    assertThat(ret.getMinCapacity()).isEqualTo(1);
    assertThat(ret.getMaxCapacity()).isEqualTo(5);

    ret = asgConfigurationManifestHandler.getRunningInstanceCapacity(null, asgConfigurationManifestRequest);
    assertThat(ret.getDesiredCapacity()).isNull();

    asgConfigurationManifestRequest =
        AsgConfigurationManifestRequest.builder()
            .alreadyRunningInstanceCapacity(AsgInstanceCapacity.builder().desiredCapacity(7).build())
            .build();
    ret = asgConfigurationManifestHandler.getRunningInstanceCapacity(null, asgConfigurationManifestRequest);
    assertThat(ret.getDesiredCapacity()).isEqualTo(7);
  }
}
