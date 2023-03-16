/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.dto.InfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class InfrastructureMapperTest {
  private AutoCloseable mocks;

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void toInfrastructureEntity() {
    InfrastructureRequestDTO dto = InfrastructureRequestDTO.builder()
                                       .identifier("id1")
                                       .orgIdentifier("orgId")
                                       .projectIdentifier("projectId")
                                       .description("description")
                                       .environmentRef("envRef")
                                       .name("my_infra_name")
                                       .type(InfrastructureType.KUBERNETES_DIRECT)
                                       .tags(Map.of("k", "v"))
                                       .yaml("infrastructureDefinition:\n"
                                           + "  name: \"my_infra_name\"\n"
                                           + "  identifier: \"id1\"\n"
                                           + "  description: \"description\"\n"
                                           + "  deploymentType: \"Kubernetes\"\n"
                                           + "  tags: \n"
                                           + "    k: v\n"
                                           + "  orgIdentifier: orgId\n"
                                           + "  projectIdentifier: projectId\n"
                                           + "  environmentRef: envId\n"
                                           + "  type: KubernetesDirect\n"
                                           + "  spec:\n"
                                           + "    connectorRef: <+input>\n"
                                           + "    namespace: default\n"
                                           + "    releaseName: release-<+INFRA_KEY>\n"
                                           + "  allowSimultaneousDeployments: false")
                                       .build();

    InfrastructureEntity infrastructureEntity = InfrastructureMapper.toInfrastructureEntity("accountId", dto);

    assertThat(infrastructureEntity.getYaml()).isEqualTo(dto.getYaml());
    assertThat(infrastructureEntity.getOrgIdentifier()).isEqualTo(dto.getOrgIdentifier());
    assertThat(infrastructureEntity.getProjectIdentifier()).isEqualTo(dto.getProjectIdentifier());
    assertThat(infrastructureEntity.getEnvIdentifier()).isEqualTo(dto.getEnvironmentRef());
    assertThat(infrastructureEntity.getTags()).containsExactly(NGTag.builder().key("k").value("v").build());
    assertThat(infrastructureEntity.getAccountId()).isEqualTo("accountId");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void toInfrastructureEntityInvalid_0() {
    InfrastructureRequestDTO dto = InfrastructureRequestDTO.builder()
                                       .identifier("id1")
                                       .orgIdentifier("orgId")
                                       .projectIdentifier("projectId")
                                       .description("description")
                                       .environmentRef("envRef")
                                       .name("my_infra_name")
                                       .type(InfrastructureType.KUBERNETES_DIRECT)
                                       .tags(Map.of("k", "v"))
                                       .yaml("infrastructureDefinition:\n"
                                           + "  name: \"my_infra_name\"\n"
                                           + "  identifier: \"id1\"\n"
                                           + "  description: \"description\"\n"
                                           + "  tags: \n"
                                           + "    k: v\n"
                                           + "  orgIdentifier: orgId\n"
                                           + "  projectIdentifier: projectId\n"
                                           + "  environmentRef: envId\n"
                                           + "  type: KubernetesDirect\n"
                                           + "  spec:\n"
                                           + "    connectorRef: <+input>\n"
                                           + "    namespace: default\n"
                                           + "    releaseName: release-<+INFRA_KEY>\n"
                                           + "  allowSimultaneousDeployments: false")
                                       .build();

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> InfrastructureMapper.toInfrastructureEntity("accountId", dto))
        .withMessageContaining("deploymentType must not be null");
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void toInfrastructureEntityInvalid_1() {
    InfrastructureRequestDTO dto = InfrastructureRequestDTO.builder()
                                       .identifier("id1")
                                       .name("my_infra_name")
                                       .orgIdentifier("orgId")
                                       .projectIdentifier("projectId")
                                       .description("description")
                                       .type(InfrastructureType.KUBERNETES_DIRECT)
                                       .tags(Map.of("k", "v"))
                                       .yaml("infrastructureDefinition:\n"
                                           + "  name: \"my_infra_name\"\n"
                                           + "  identifier: \"id1\"\n"
                                           + "  description: \"description\"\n"
                                           + "  tags: \n"
                                           + "    k: v\n"
                                           + "  orgIdentifier: orgId\n"
                                           + "  projectIdentifier: projectId\n"
                                           + "  environmentRef: envId\n"
                                           + "  type: KubernetesDirect\n"
                                           + "  spec:\n"
                                           + "    connectorRef: <+input>\n"
                                           + "    namespace: default\n"
                                           + "    releaseName: release-<+INFRA_KEY>\n"
                                           + "  allowSimultaneousDeployments: false")
                                       .build();

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> InfrastructureMapper.toInfrastructureEntity("accountId", dto))
        .withMessageContaining("deploymentType must not be null")
        .withMessageContaining("environmentRef must not be empty");
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void toInfrastructureEntityYamlFieldsMismatch() {
    InfrastructureRequestDTO dto = InfrastructureRequestDTO.builder()
                                       .identifier("id1")
                                       .orgIdentifier("orgId")
                                       .projectIdentifier("projectId")
                                       .description("description")
                                       .environmentRef("envRef")
                                       .name("name1")
                                       .type(InfrastructureType.KUBERNETES_DIRECT)
                                       .tags(Map.of("k", "v"))
                                       .yaml("infrastructureDefinition:\n"
                                           + "  name: \"my_infra_name\"\n"
                                           + "  identifier: \"my_infra_id\"\n"
                                           + "  description: \"description\"\n"
                                           + "  deploymentType: \"Kubernetes\"\n"
                                           + "  tags: \n"
                                           + "    k: v\n"
                                           + "  orgIdentifier: orgId\n"
                                           + "  projectIdentifier: projectId\n"
                                           + "  environmentRef: envId\n"
                                           + "  type: KubernetesDirect\n"
                                           + "  spec:\n"
                                           + "    connectorRef: <+input>\n"
                                           + "    namespace: default\n"
                                           + "    releaseName: release-<+INFRA_KEY>\n"
                                           + "  allowSimultaneousDeployments: false")
                                       .build();

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> InfrastructureMapper.toInfrastructureEntity("accountId", dto))
        .withMessageContaining(
            "For the infrastructure [name: name1, identifier: id1], Found mismatch in following fields between yaml and requested value respectively: {InfraStructureDefinition Name=[my_infra_name, name1], InfrastructureDefinition Identifier=[my_infra_id, id1]}");
  }
}
