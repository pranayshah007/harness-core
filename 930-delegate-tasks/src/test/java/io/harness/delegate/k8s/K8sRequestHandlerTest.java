/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sRollingDeployRequest;
import io.harness.delegate.task.k8s.KustomizeManifestDelegateConfig;
import io.harness.delegate.task.k8s.OpenshiftManifestDelegateConfig;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class K8sRequestHandlerTest extends CategoryTest {
  private static final String TEST_VALUES_FROMAT = "value: %s";
  private static final Map<String, String> REPLACE_VALUES =
      ImmutableMap.of("REPLACE_VALUE_1", "value_1", "REPLACE_VALUE_2", "value_2");

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Spy private K8sRequestHandler k8sRequestHandler;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetManifestOverrideFliesValuesYaml() {
    final K8sRollingDeployRequest deployRequest =
        K8sRollingDeployRequest.builder()
            .manifestDelegateConfig(K8sManifestDelegateConfig.builder().build())
            .valuesYamlList(getTestValuesWithValuesFormat("REPLACE_VALUE_1", "REPLACE_VALUE_2"))
            .build();

    testGetManifestOverrideFliesWithReplace(deployRequest, "value_1", "value_2");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetManifestOverrideFliesKustomizePatches() {
    final K8sRollingDeployRequest deployRequest =
        K8sRollingDeployRequest.builder()
            .manifestDelegateConfig(KustomizeManifestDelegateConfig.builder().build())
            .kustomizePatchesList(getTestValuesWithValuesFormat("REPLACE_VALUE_1", "REPLACE_VALUE_2"))
            .build();

    testGetManifestOverrideFliesWithReplace(deployRequest, "value_1", "value_2");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetManifestOverrideFliesOpenshiftParams() {
    final K8sRollingDeployRequest deployRequest =
        K8sRollingDeployRequest.builder()
            .manifestDelegateConfig(OpenshiftManifestDelegateConfig.builder().build())
            .openshiftParamList(getTestValuesWithValuesFormat("REPLACE_VALUE_1", "REPLACE_VALUE_2"))
            .build();

    testGetManifestOverrideFliesWithReplace(deployRequest, "value_1", "value_2");
  }

  private void testGetManifestOverrideFliesWithReplace(K8sDeployRequest deployRequest, String... expected) {
    List<String> result = k8sRequestHandler.getManifestOverrideFlies(deployRequest, REPLACE_VALUES);
    assertThat(result).containsExactlyElementsOf(getTestValuesWithValuesFormat(expected));
  }

  private List<String> getTestValuesWithValuesFormat(String... values) {
    return Stream.of(values).map(value -> format(TEST_VALUES_FROMAT, value)).collect(Collectors.toList());
  }
}
