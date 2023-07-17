/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.k8s.request;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.CapabilityType;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.expression.ExpressionEvaluator;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.helpers.ext.kustomize.KustomizeConfig;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class K8sTaskParametersTest extends WingsBaseTest {
  @Mock private K8sClusterConfig clusterConfig;
  @Mock private ExpressionEvaluator expressionEvaluator;

  @Before
  public void setUp() throws Exception {
    doReturn(Arrays.asList(HttpConnectionExecutionCapability.builder().build()))
        .when(clusterConfig)
        .fetchRequiredExecutionCapabilities(null);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void fetchClusterConfigCapabilities() {
    assertThat(K8sApplyTaskParameters.builder()
                   .k8sClusterConfig(clusterConfig)
                   .build()
                   .fetchRequiredExecutionCapabilities(null)
                   .stream()
                   .map(ExecutionCapability::getCapabilityType)
                   .collect(Collectors.toList()))
        .containsExactly(CapabilityType.HTTP);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void fetchRequiredExecutionCapabilitiesKustomize() {
    assertThat(hasKustomizeCapability(K8sScaleTaskParameters.builder()
                                          .k8sClusterConfig(clusterConfig)
                                          .build()
                                          .fetchRequiredExecutionCapabilities(null)))
        .isFalse();
    assertThat(hasKustomizeCapability(k8sTaskParamsWithNoKustomizeConfig().fetchRequiredExecutionCapabilities(null)))
        .isFalse();
    assertThat(
        hasKustomizeCapability(k8sTaskParamsWithKustomizePluginPath(null).fetchRequiredExecutionCapabilities(null)))
        .isTrue();
    assertThat(
        hasKustomizeCapability(k8sTaskParamsWithKustomizePluginPath(EMPTY).fetchRequiredExecutionCapabilities(null)))
        .isTrue();
    assertThat(
        hasKustomizeCapability(k8sTaskParamsWithKustomizePluginPath("foo").fetchRequiredExecutionCapabilities(null)))
        .isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSecretManagerExecutionCapabilitiesReturnEmptyListWhenManifestConfigIsNull() {
    final K8sApplyTaskParameters p = K8sApplyTaskParameters.builder().build();
    assertThat(p.getSecretManagerExecutionCapabilities(expressionEvaluator, null)).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSecretManagerExecutionCapabilitiesReturnEmptyListWhenFFisDisabled() {
    final K8sApplyTaskParameters p = K8sApplyTaskParameters.builder().build();
    K8sDelegateManifestConfig manifestConfig =
        K8sDelegateManifestConfig.builder().secretManagerCapabilitiesEnabled(false).build();
    assertThat(p.getSecretManagerExecutionCapabilities(expressionEvaluator, manifestConfig)).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSecretManagerExecutionCapabilitiesReturnEmptyListWhenFFisEnabledAndEncryptedDetailsIsNull() {
    final K8sApplyTaskParameters p = K8sApplyTaskParameters.builder().build();
    K8sDelegateManifestConfig manifestConfig =
        K8sDelegateManifestConfig.builder().secretManagerCapabilitiesEnabled(true).build();
    try (MockedStatic<EncryptedDataDetailsCapabilityHelper> aMock =
             Mockito.mockStatic(EncryptedDataDetailsCapabilityHelper.class)) {
      assertThat(p.getSecretManagerExecutionCapabilities(expressionEvaluator, manifestConfig)).isEmpty();
      aMock.verify(()
                       -> EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                           null, expressionEvaluator));
    }
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSecretManagerExecutionCapabilitiesReturnNonEmptyWhenFFisEnabled() {
    final K8sApplyTaskParameters p = K8sApplyTaskParameters.builder().build();
    K8sDelegateManifestConfig manifestConfig =
        K8sDelegateManifestConfig.builder().secretManagerCapabilitiesEnabled(true).build();

    try (MockedStatic<EncryptedDataDetailsCapabilityHelper> aMock =
             Mockito.mockStatic(EncryptedDataDetailsCapabilityHelper.class)) {
      aMock
          .when(()
                    -> EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                        null, expressionEvaluator))
          .thenReturn(List.of(Mockito.mock(ExecutionCapability.class)));
      assertThat(p.getSecretManagerExecutionCapabilities(expressionEvaluator, manifestConfig)).isNotEmpty();
    }
  }

  private boolean hasKustomizeCapability(List<ExecutionCapability> capabilityList) {
    return capabilityList.stream()
        .map(ExecutionCapability::getCapabilityType)
        .collect(Collectors.toSet())
        .contains(CapabilityType.KUSTOMIZE);
  }

  private K8sTaskParameters k8sTaskParamsWithNoKustomizeConfig() {
    return K8sRollingDeployTaskParameters.builder()
        .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
        .k8sClusterConfig(clusterConfig)
        .build();
  }

  private K8sTaskParameters k8sTaskParamsWithKustomizePluginPath(String kustomizePluginPath) {
    return K8sApplyTaskParameters.builder()
        .k8sClusterConfig(clusterConfig)
        .k8sDelegateManifestConfig(
            K8sDelegateManifestConfig.builder()
                .kustomizeConfig(KustomizeConfig.builder().pluginRootDir(kustomizePluginPath).build())
                .build())
        .build();
  }
}
