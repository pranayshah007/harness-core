/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.envGroup.mappers;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.envGroup.beans.EnvironmentGroupConfig;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.beans.EnvironmentGroupWrapperConfig;
import io.harness.encryption.ScopeHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.envGroup.dto.EnvironmentGroupRequestDTO;
import io.harness.ng.core.envGroup.dto.EnvironmentGroupRequestDTO.EnvironmentGroupRequestDTOBuilder;
import io.harness.ng.core.envGroup.dto.EnvironmentGroupResponse;
import io.harness.ng.core.envGroup.dto.EnvironmentGroupResponseDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EnvironmentGroupMapperTest extends CategoryTest {
  private String ACC_ID = "accId";
  private String ORG_ID = "orgId";
  private String PRO_ID = "proId";
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testWriteDto() {
    EnvironmentGroupEntity environmentGroupEntity = EnvironmentGroupEntity.builder()
                                                        .accountId(ACC_ID)
                                                        .orgIdentifier(ORG_ID)
                                                        .projectIdentifier(PRO_ID)
                                                        .identifier("id1")
                                                        .name("envGroup")
                                                        .envIdentifiers(Arrays.asList("env1", "env2"))
                                                        .color("col")
                                                        .createdAt(1L)
                                                        .lastModifiedAt(2L)
                                                        .yaml("yaml")
                                                        .build();

    List<EnvironmentResponse> envResponseList = Arrays.asList(EnvironmentResponse.builder().build());
    EnvironmentGroupResponseDTO environmentGroupResponseDTO =
        EnvironmentGroupMapper.writeDTO(environmentGroupEntity, envResponseList);
    assertThat(environmentGroupResponseDTO.getName()).isEqualTo("envGroup");
    assertThat(environmentGroupResponseDTO.getAccountId()).isEqualTo(ACC_ID);
    assertThat(environmentGroupResponseDTO.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(environmentGroupResponseDTO.getProjectIdentifier()).isEqualTo(PRO_ID);
    assertThat(environmentGroupResponseDTO.getIdentifier()).isEqualTo("id1");
    assertThat(environmentGroupResponseDTO.getEnvIdentifiers().size()).isEqualTo(2);
    assertThat(environmentGroupResponseDTO.getEnvResponse().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void toResponseWrapper() {
    EnvironmentGroupEntity environmentGroupEntity = EnvironmentGroupEntity.builder()
                                                        .accountId(ACC_ID)
                                                        .orgIdentifier(ORG_ID)
                                                        .projectIdentifier(PRO_ID)
                                                        .identifier("id1")
                                                        .name("envGroup")
                                                        .envIdentifiers(Arrays.asList("env1", "env2"))
                                                        .color("col")
                                                        .createdAt(1L)
                                                        .lastModifiedAt(2L)
                                                        .yaml("yaml")
                                                        .build();
    EnvironmentGroupResponse environmentGroupResponse = EnvironmentGroupMapper.toResponseWrapper(
        environmentGroupEntity, Collections.singletonList(EnvironmentResponse.builder().build()));
    assertThat(environmentGroupResponse.getCreatedAt()).isEqualTo(1L);
    assertThat(environmentGroupResponse.getLastModifiedAt()).isEqualTo(2L);
    assertThat(environmentGroupResponse.getEnvGroup().getIdentifier()).isEqualTo("id1");
    assertThat(environmentGroupResponse.getEnvGroup().getEnvIdentifiers().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetEntityDetail() {
    EnvironmentGroupEntity environmentGroupEntity = EnvironmentGroupEntity.builder()
                                                        .accountId(ACC_ID)
                                                        .orgIdentifier(ORG_ID)
                                                        .projectIdentifier(PRO_ID)
                                                        .identifier("id1")
                                                        .name("envGroup")
                                                        .envIdentifiers(Arrays.asList("env1", "env2"))
                                                        .color("col")
                                                        .createdAt(1L)
                                                        .lastModifiedAt(2L)
                                                        .yaml("yaml")
                                                        .build();

    IdentifierRef expectedIdentifierRef =
        IdentifierRef.builder()
            .accountIdentifier(environmentGroupEntity.getAccountIdentifier())
            .orgIdentifier(environmentGroupEntity.getOrgIdentifier())
            .projectIdentifier(environmentGroupEntity.getProjectIdentifier())
            .scope(ScopeHelper.getScope(environmentGroupEntity.getAccountIdentifier(),
                environmentGroupEntity.getOrgIdentifier(), environmentGroupEntity.getProjectIdentifier()))
            .identifier(environmentGroupEntity.getIdentifier())
            .build();

    EntityDetail entityDetail = EnvironmentGroupMapper.getEntityDetail(environmentGroupEntity);

    assertThat(entityDetail.getName()).isEqualTo(environmentGroupEntity.getName());
    assertThat(entityDetail.getType()).isEqualTo(EntityType.ENVIRONMENT_GROUP);
    assertThat(entityDetail.getEntityRef()).isEqualTo(expectedIdentifierRef);
  }

  private String getYamlFieldFromGivenFileName(String file) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(file);
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    return yaml;
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testToEnvironmentEntity() throws IOException {
    String yaml = getYamlFieldFromGivenFileName("cdng/envGroup/mappers/validEnvGroup.yml");
    EnvironmentGroupConfig environmentGroupConfig =
        YamlUtils.read(yaml, EnvironmentGroupWrapperConfig.class).getEnvironmentGroupConfig();
    String projectIdentifier = environmentGroupConfig.getProjectIdentifier();
    String orgIdentifier = environmentGroupConfig.getOrgIdentifier();
    String name = environmentGroupConfig.getName();
    String identifier = environmentGroupConfig.getIdentifier();
    String color = "color";
    List<String> envIdentifiers = environmentGroupConfig.getEnvIdentifiers();

    EnvironmentGroupRequestDTO environmentGroupRequestDTO = EnvironmentGroupRequestDTO.builder()
                                                                .identifier(identifier)
                                                                .orgIdentifier(orgIdentifier)
                                                                .projectIdentifier(projectIdentifier)
                                                                .color(color)
                                                                .yaml(yaml)
                                                                .build();
    EnvironmentGroupEntity environmentGroupEntity =
        EnvironmentGroupMapper.toEnvironmentGroupEntity(ACC_ID, environmentGroupRequestDTO);

    assertThat(environmentGroupEntity.getIdentifier()).isEqualTo(identifier);
    assertThat(environmentGroupEntity.getName()).isEqualTo(name);
    assertThat(environmentGroupEntity.getAccountIdentifier()).isEqualTo(ACC_ID);
    assertThat(environmentGroupEntity.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(environmentGroupEntity.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(environmentGroupEntity.getEnvIdentifiers().size()).isEqualTo(2);
    assertThat(environmentGroupEntity.getEnvIdentifiers())
        .containsExactly(envIdentifiers.get(0), envIdentifiers.get(1));
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testYamlEnvGroup() throws IOException {
    // To test org and project id passed in query param to be same as passed in yaml
    String yaml = getYamlFieldFromGivenFileName("cdng/envGroup/mappers/validEnvGroup.yml");

    EnvironmentGroupConfig config =
        YamlUtils.read(yaml, EnvironmentGroupWrapperConfig.class).getEnvironmentGroupConfig();
    EnvironmentGroupRequestDTOBuilder dtoBuilder = EnvironmentGroupRequestDTO.builder();
    // Incorrect Org And ProjectId
    EnvironmentGroupRequestDTO dto1 = dtoBuilder.orgIdentifier("newOrg").projectIdentifier("newProject").build();
    assertThatThrownBy(() -> EnvironmentGroupMapper.validateYamlOfEnvGroup(config, dto1))
        .isInstanceOf(InvalidRequestException.class);

    // Incorrect Org and Correct project id
    EnvironmentGroupRequestDTO dto2 =
        dtoBuilder.orgIdentifier("newOrg").projectIdentifier(config.getProjectIdentifier()).build();
    assertThatThrownBy(() -> EnvironmentGroupMapper.validateYamlOfEnvGroup(config, dto2))
        .isInstanceOf(InvalidRequestException.class);

    // Correct Org and Incorrect project id
    EnvironmentGroupRequestDTO dto3 =
        dtoBuilder.orgIdentifier(config.getOrgIdentifier()).projectIdentifier("newProject").build();
    assertThatThrownBy(() -> EnvironmentGroupMapper.validateYamlOfEnvGroup(config, dto3))
        .isInstanceOf(InvalidRequestException.class);

    // Correct Org and Correct project id
    EnvironmentGroupRequestDTO dto4 = dtoBuilder.orgIdentifier(config.getOrgIdentifier())
                                          .projectIdentifier(config.getProjectIdentifier())
                                          .identifier(config.getIdentifier())
                                          .build();
    assertThatCode(() -> EnvironmentGroupMapper.validateYamlOfEnvGroup(config, dto4)).doesNotThrowAnyException();
  }
}
