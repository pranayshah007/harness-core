/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mappers;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NGServiceEntityMapperTest {
  private Map<FeatureName, Boolean> featureFlags = new HashMap<>();
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testToNGServiceConfig() {
    compareServiceEntityWithNgServiceV2InfoConfig(null, null, false);
    compareServiceEntityWithNgServiceV2InfoConfig(null, null, true);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testToNGServiceConfigWithServiceDefinition() {
    String yaml = "service:\n"
        + "  name: \"se\"\n"
        + "  identifier: \"serviceId\"\n"
        + "  orgIdentifier: \"orgId\"\n"
        + "  projectIdentifier: \"projectId\"\n"
        + "  description: \"desc of service\"\n"
        + "  serviceDefinition:\n"
        + "    type: \"Kubernetes\"\n"
        + "    spec:\n"
        + "        variables: []\n"
        + "        manifests:\n"
        + "            - manifest:\n"
        + "                  identifier: \"stable\"\n"
        + "                  type: \"HelmChart\"\n"
        + "                  spec:\n"
        + "                      store:\n"
        + "                          type: \"Http\"\n"
        + "                          spec:\n"
        + "                              connectorRef: \"stable\"\n"
        + "                      chartName: \"chartmuseum\"";
    compareServiceEntityWithNgServiceV2InfoConfig(yaml, ServiceDefinitionType.KUBERNETES, false);
    compareServiceEntityWithNgServiceV2InfoConfig(yaml, ServiceDefinitionType.KUBERNETES, true);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testToNGServiceConfigWithMultipleManifestSupport() {
    String yaml = "service:\n"
        + "  name: \"se\"\n"
        + "  identifier: \"serviceId\"\n"
        + "  orgIdentifier: \"orgId\"\n"
        + "  projectIdentifier: \"projectId\"\n"
        + "  description: \"desc of service\"\n"
        + "  serviceDefinition:\n"
        + "    type: \"NativeHelm\"\n"
        + "    spec:\n"
        + "        variables: []\n"
        + "        manifestConfigurations:\n"
        + "            primaryManifestRef: \"stable2\"\n"
        + "        manifests:\n"
        + "            - manifest:\n"
        + "                  identifier: \"stable\"\n"
        + "                  type: \"HelmChart\"\n"
        + "                  spec:\n"
        + "                      store:\n"
        + "                          type: \"Http\"\n"
        + "                          spec:\n"
        + "                              connectorRef: \"stable\"\n"
        + "                      chartName: \"chartmuseum\"\n"
        + "            - manifest:\n"
        + "                  identifier: \"stable2\"\n"
        + "                  type: \"HelmChart\"\n"
        + "                  spec:\n"
        + "                      store:\n"
        + "                          type: \"Http\"\n"
        + "                          spec:\n"
        + "                              connectorRef: \"stable\"\n"
        + "                      chartName: \"chartmuseum\"";
    compareServiceEntityWithNgServiceV2InfoConfig(yaml, ServiceDefinitionType.NATIVE_HELM, true);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testFailToNGServiceConfigWithMultipleManifestSupport() {
    String yaml = "service:\n"
        + "  name: \"se\"\n"
        + "  identifier: \"serviceId\"\n"
        + "  orgIdentifier: \"orgId\"\n"
        + "  projectIdentifier: \"projectId\"\n"
        + "  description: \"desc of service\"\n"
        + "  serviceDefinition:\n"
        + "    type: \"Kubernetes\"\n"
        + "    spec:\n"
        + "        variables: []\n"
        + "        manifestConfigurations:\n"
        + "            primaryManifestRef: \"stable2\"\n"
        + "        manifests:\n"
        + "            - manifest:\n"
        + "                  identifier: \"stable\"\n"
        + "                  type: \"HelmChart\"\n"
        + "                  spec:\n"
        + "                      store:\n"
        + "                          type: \"Http\"\n"
        + "                          spec:\n"
        + "                              connectorRef: \"stable\"\n"
        + "                      chartName: \"chartmuseum\"\n"
        + "            - manifest:\n"
        + "                  identifier: \"stable2\"\n"
        + "                  type: \"K8sManifest\"\n"
        + "                  spec:\n"
        + "                      store:\n"
        + "                          type: \"Http\"\n"
        + "                          spec:\n"
        + "                              connectorRef: \"stable\"\n"
        + "                      chartName: \"chartmuseum\"";
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> compareServiceEntityWithNgServiceV2InfoConfig(yaml, ServiceDefinitionType.KUBERNETES, true))
        .withMessage(
            "Multiple manifests found [stable2 : K8sManifest]. Kubernetes deployment support only Helm Chart for multiple manifest feature. Remove all unused manifests");
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> compareServiceEntityWithNgServiceV2InfoConfig(yaml, ServiceDefinitionType.KUBERNETES, false))
        .withMessage(
            "Multiple manifests found [stable2 : K8sManifest, stable : HelmChart]. Kubernetes deployment support only one manifest of one of types: K8sManifest, HelmChart, Kustomize, OpenshiftTemplate. Remove all unused manifests");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testToNGServiceConfigInvalidYaml() {
    String invalidYaml = "foo=bar";
    ServiceEntity entity = ServiceEntity.builder()
                               .name("se")
                               .identifier("serviceId")
                               .orgIdentifier("orgId")
                               .projectIdentifier("projectId")
                               .description("sample service")
                               .tags(Collections.singletonList(NGTag.builder().key("k1").value("v1").build()))
                               .yaml(invalidYaml)
                               .build();
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> NGServiceEntityMapper.toNGServiceConfig(entity));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testToNGServiceConfigIdentifierConflict() {
    String yaml = "service:\n"
        + "  name: \"sample-service\"\n"
        + "  identifier: \"sample-service-id\"\n"
        + "  orgIdentifier: \"orgId\"\n"
        + "  projectIdentifier: \"projectId\"\n"
        + "  description: \"desc of service\"\n"
        + "  serviceDefinition:\n"
        + "    type: \"Kubernetes\"\n"
        + "    spec:\n"
        + "        variables: []\n";
    ServiceEntity entity = ServiceEntity.builder()
                               .name("se")
                               .identifier("different-service-id")
                               .orgIdentifier("orgId")
                               .projectIdentifier("projectId")
                               .description("sample service")
                               .tags(Collections.singletonList(NGTag.builder().key("k1").value("v1").build()))
                               .yaml(yaml)
                               .build();
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> NGServiceEntityMapper.toNGServiceConfig(entity));
  }

  private void compareServiceEntityWithNgServiceV2InfoConfig(
      String yaml, ServiceDefinitionType serviceDefinitionType, boolean isHelmMultipleManifestSupportEnabled) {
    final ServiceEntity entity = ServiceEntity.builder()
                                     .name("se")
                                     .identifier("serviceId")
                                     .orgIdentifier("orgId")
                                     .projectIdentifier("projectId")
                                     .description("sample service")
                                     .tags(Collections.singletonList(NGTag.builder().key("k1").value("v1").build()))
                                     .yaml(yaml)
                                     .build();
    featureFlags.put(FeatureName.CDS_HELM_MULTIPLE_MANIFEST_SUPPORT_NG, isHelmMultipleManifestSupportEnabled);
    final NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(entity, featureFlags);
    final NGServiceV2InfoConfig ngServiceV2InfoConfig = ngServiceConfig.getNgServiceV2InfoConfig();
    assertThat(ngServiceV2InfoConfig.getName()).isEqualTo(entity.getName());
    assertThat(ngServiceV2InfoConfig.getIdentifier()).isEqualTo(entity.getIdentifier());
    assertThat(ngServiceV2InfoConfig.getDescription()).isEqualTo(entity.getDescription());
    assertThat(ngServiceV2InfoConfig.getTags().get("k1")).isEqualTo(entity.getTags().get(0).getValue());
    if (isNotEmpty(yaml)) {
      assertThat(ngServiceV2InfoConfig.getServiceDefinition().getType()).isEqualTo(serviceDefinitionType);
    }
  }
}
