/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.mapper;

import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.apache.groovy.util.Maps;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class InfrastructureEntityConfigMapperTest {
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testToYaml() {
    String yaml = InfrastructureEntityConfigMapper.toYaml(
        InfrastructureConfig.builder()
            .infrastructureDefinitionConfig(
                InfrastructureDefinitionConfig.builder()
                    .identifier("id")
                    .orgIdentifier("orgId")
                    .projectIdentifier("projId")
                    .allowSimultaneousDeployments(true)
                    .name("name")
                    .environmentRef("env")
                    .tags(Maps.of("k1", "v1"))
                    .type(InfrastructureType.KUBERNETES_DIRECT)
                    .spec(K8SDirectInfrastructure.builder()
                              .connectorRef(ParameterField.<String>builder().value("infra").build())
                              .namespace(ParameterField.<String>builder().value("default").build())
                              .build())
                    .build())
            .build());
    assertThat(yaml).isEqualTo("infrastructureDefinition:\n"
        + "  name: name\n"
        + "  identifier: id\n"
        + "  orgIdentifier: orgId\n"
        + "  projectIdentifier: projId\n"
        + "  environmentRef: env\n"
        + "  tags:\n"
        + "    k1: v1\n"
        + "  allowSimultaneousDeployments: true\n"
        + "  type: KubernetesDirect\n"
        + "  spec:\n"
        + "    connectorRef: infra\n"
        + "    namespace: default\n");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testToInfrastructureConfig() {
    InfrastructureConfig infrastructureConfig =
        InfrastructureEntityConfigMapper.toInfrastructureConfig(InfrastructureEntity.builder()
                                                                    .accountId("accountId")
                                                                    .orgIdentifier("orgId")
                                                                    .identifier("id")
                                                                    .projectIdentifier("projId")
                                                                    .name("name")
                                                                    .envIdentifier("envId")
                                                                    .tag(NGTag.builder().key("k1").value("v1").build())
                                                                    .type(InfrastructureType.KUBERNETES_DIRECT)
                                                                    .yaml("infrastructureDefinition:\n"
                                                                        + "  name: \"name\"\n"
                                                                        + "  identifier: \"id\"\n"
                                                                        + "  orgIdentifier: \"orgId\"\n"
                                                                        + "  projectIdentifier: \"projId\"\n"
                                                                        + "  environmentRef: \"env\"\n"
                                                                        + "  tags:\n"
                                                                        + "    k1: \"v1\"\n"
                                                                        + "  allowSimultaneousDeployments: true\n"
                                                                        + "  type: \"KubernetesDirect\"\n"
                                                                        + "  spec:\n"
                                                                        + "    connectorRef: \"infra\"\n"
                                                                        + "    namespace: \"defaultns\"\n")
                                                                    .build());
    InfrastructureDefinitionConfig cfg = infrastructureConfig.getInfrastructureDefinitionConfig();
    assertThat(cfg.getIdentifier()).isEqualTo("id");
    assertThat(cfg.getOrgIdentifier()).isEqualTo("orgId");
    assertThat(cfg.getProjectIdentifier()).isEqualTo("projId");
    assertThat(cfg.getEnvironmentRef()).isEqualTo("envId");
    assertThat(cfg.isAllowSimultaneousDeployments()).isTrue();
    assertThat(cfg.getTags()).containsOnlyKeys("k1");
    assertThat(cfg.getTags().get("k1")).isEqualTo("v1");
    assertThat(cfg.getSpec().getConnectorReference().getValue()).isEqualTo("infra");
    assertThat(((K8SDirectInfrastructure) cfg.getSpec()).getNamespace().getValue()).isEqualTo("defaultns");
  }
}
