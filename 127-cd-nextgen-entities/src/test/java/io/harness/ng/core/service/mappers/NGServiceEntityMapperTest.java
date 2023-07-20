/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mappers;

import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NGServiceEntityMapperTest {
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testToNGServiceConfig() {
    ServiceEntity entity = ServiceEntity.builder()
                               .name("se")
                               .identifier("serviceId")
                               .orgIdentifier("orgId")
                               .projectIdentifier("projectId")
                               .description("sample service")
                               .tags(Arrays.asList(NGTag.builder().key("k1").value("v1").build()))
                               .build();
    final NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(entity);
    final NGServiceV2InfoConfig ngServiceV2InfoConfig = ngServiceConfig.getNgServiceV2InfoConfig();
    assertThat(ngServiceV2InfoConfig.getName()).isEqualTo("se");
    assertThat(ngServiceV2InfoConfig.getIdentifier()).isEqualTo("serviceId");
    assertThat(ngServiceV2InfoConfig.getDescription()).isEqualTo("sample service");
    assertThat(ngServiceV2InfoConfig.getTags().get("k1")).isEqualTo("v1");
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
    ServiceEntity entity = ServiceEntity.builder()
                               .name("se")
                               .identifier("serviceId")
                               .orgIdentifier("orgId")
                               .projectIdentifier("projectId")
                               .description("sample service")
                               .tags(Arrays.asList(NGTag.builder().key("k1").value("v1").build()))
                               .yaml(yaml)
                               .build();
    final NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(entity);
    final NGServiceV2InfoConfig ngServiceV2InfoConfig = ngServiceConfig.getNgServiceV2InfoConfig();
    assertThat(ngServiceV2InfoConfig.getName()).isEqualTo("se");
    assertThat(ngServiceV2InfoConfig.getIdentifier()).isEqualTo("serviceId");
    assertThat(ngServiceV2InfoConfig.getDescription()).isEqualTo("sample service");
    assertThat(ngServiceV2InfoConfig.getTags().get("k1")).isEqualTo("v1");
    assertThat(ngServiceV2InfoConfig.getServiceDefinition().getType()).isEqualTo(ServiceDefinitionType.KUBERNETES);
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
                               .tags(Arrays.asList(NGTag.builder().key("k1").value("v1").build()))
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
                               .tags(Arrays.asList(NGTag.builder().key("k1").value("v1").build()))
                               .yaml(yaml)
                               .build();
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> NGServiceEntityMapper.toNGServiceConfig(entity));
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testToNGServiceConfigWithManifestConfigurationKubernetes() {
    testToNGServiceConfigWithManifestConfiguration(ServiceDefinitionType.KUBERNETES);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testToNGServiceConfigWithManifestConfigurationNativeHelm() {
    testToNGServiceConfigWithManifestConfiguration(ServiceDefinitionType.NATIVE_HELM);
  }

  private void testToNGServiceConfigWithManifestConfiguration(ServiceDefinitionType serviceDefinitionType) {
    String yaml = "service:\n"
        + "  name: \"se\"\n"
        + "  identifier: \"serviceId\"\n"
        + "  orgIdentifier: \"orgId\"\n"
        + "  projectIdentifier: \"projectId\"\n"
        + "  description: \"desc of service\"\n"
        + "  serviceDefinition:\n"
        + "    type: \"" + serviceDefinitionType.getYamlName() + "\"\n"
        + "    spec:\n"
        + "        variables: []\n"
        + "        manifestConfigurations:\n"
        + "            primaryManifestRef: stable\n"
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
    ServiceEntity entity = ServiceEntity.builder()
                               .name("se")
                               .identifier("serviceId")
                               .orgIdentifier("orgId")
                               .projectIdentifier("projectId")
                               .description("sample service")
                               .tags(Arrays.asList(NGTag.builder().key("k1").value("v1").build()))
                               .yaml(yaml)
                               .build();
    final NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(entity);
    final NGServiceV2InfoConfig ngServiceV2InfoConfig = ngServiceConfig.getNgServiceV2InfoConfig();
    assertThat(ngServiceV2InfoConfig.getName()).isEqualTo("se");
    assertThat(ngServiceV2InfoConfig.getIdentifier()).isEqualTo("serviceId");
    assertThat(ngServiceV2InfoConfig.getDescription()).isEqualTo("sample service");
    assertThat(ngServiceV2InfoConfig.getTags().get("k1")).isEqualTo("v1");
    assertThat(ngServiceV2InfoConfig.getServiceDefinition().getType()).isEqualTo(serviceDefinitionType);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testThrowMultipleManifestsExceptionIfApplicable() {
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
        + "            primaryManifestRef: stable\n"
        + "        manifests:\n"
        + "            - manifest:\n"
        + "                  identifier: \"stable\"\n"
        + "                  type: \"K8sManifest\"\n"
        + "                  spec:\n"
        + "                      store:\n"
        + "                          type: \"Git\"\n"
        + "                          spec:\n"
        + "                              connectorRef: \"stable\"\n"
        + "                      chartName: \"chartmuseum\"";
    ServiceEntity entity = ServiceEntity.builder()
                               .name("se")
                               .identifier("serviceId")
                               .orgIdentifier("orgId")
                               .projectIdentifier("projectId")
                               .description("sample service")
                               .tags(Arrays.asList(NGTag.builder().key("k1").value("v1").build()))
                               .yaml(yaml)
                               .build();
    assertThatThrownBy(() -> NGServiceEntityMapper.toNGServiceConfig(entity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Specifying multiple manifests for deployment type: Kubernetes is only supported for the manifest types: HelmChart. Manifests found: stable : K8sManifest");
  }
}
