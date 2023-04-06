/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.mappers;

import static io.harness.ng.core.environment.EnvironmentTestHelper.readFile;
import static io.harness.ng.core.environment.beans.EnvironmentType.PreProduction;
import static io.harness.ng.core.environment.beans.EnvironmentType.Production;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.dto.EnvironmentRequestDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EnvironmentMapperTest extends CategoryTest {
  private EnvironmentRequestDTO environmentRequestDTO;
  private EnvironmentResponseDTO environmentResponseDTO;
  private Environment requestEnvironment;
  private Environment responseEnvironment;
  private List<NGTag> tags;
  private NGEnvironmentConfig ngEnvironmentConfig;

  @Before
  public void setUp() {
    tags = Arrays.asList(NGTag.builder().key("k1").value("v1").build(), NGTag.builder().key("k2").value("v2").build());
    ngEnvironmentConfig = NGEnvironmentConfig.builder()
                              .ngEnvironmentInfoConfig(NGEnvironmentInfoConfig.builder()
                                                           .identifier("ENV")
                                                           .orgIdentifier("ORG_ID")
                                                           .projectIdentifier("PROJECT_ID")
                                                           .tags(ImmutableMap.of("k1", "v1", "k2", "v2"))
                                                           .type(EnvironmentType.PreProduction)
                                                           .build())
                              .build();
    environmentRequestDTO = EnvironmentRequestDTO.builder()
                                .identifier("ENV")
                                .orgIdentifier("ORG_ID")
                                .projectIdentifier("PROJECT_ID")
                                .color("BLACK")
                                .type(EnvironmentType.PreProduction)
                                .tags(ImmutableMap.of("k1", "v1", "k2", "v2"))
                                .build();

    environmentResponseDTO = EnvironmentResponseDTO.builder()
                                 .accountId("ACCOUNT_ID")
                                 .identifier("ENV")
                                 .orgIdentifier("ORG_ID")
                                 .projectIdentifier("PROJECT_ID")
                                 .color("BLACK")
                                 .type(EnvironmentType.PreProduction)
                                 .deleted(false)
                                 .tags(ImmutableMap.of("k1", "v1", "k2", "v2"))
                                 .build();

    requestEnvironment = Environment.builder()
                             .accountId("ACCOUNT_ID")
                             .identifier("ENV")
                             .orgIdentifier("ORG_ID")
                             .color("BLACK")
                             .projectIdentifier("PROJECT_ID")
                             .type(EnvironmentType.PreProduction)
                             .deleted(false)
                             .tags(tags)
                             .yaml(EnvironmentMapper.toYaml(ngEnvironmentConfig))
                             .build();

    responseEnvironment = Environment.builder()
                              .accountId("ACCOUNT_ID")
                              .identifier("ENV")
                              .orgIdentifier("ORG_ID")
                              .projectIdentifier("PROJECT_ID")
                              .color("BLACK")
                              .type(EnvironmentType.PreProduction)
                              .id("UUID")
                              .deleted(false)
                              .tags(tags)
                              .build();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testToEnvironment() {
    Environment environment = EnvironmentMapper.toEnvironmentEntity("ACCOUNT_ID", environmentRequestDTO);
    assertThat(environment).isNotNull();
    assertThat(environment).isEqualTo(requestEnvironment);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testToEnvironmentValidateManifestOverride() {
    final String filename = "env-with-manifest-overrides.yaml";
    final String yaml = readFile(filename, getClass());
    final EnvironmentRequestDTO requestDTO = EnvironmentRequestDTO.builder()
                                                 .identifier("ENV")
                                                 .orgIdentifier("ORG_ID")
                                                 .projectIdentifier("PROJECT_ID")
                                                 .type(Production)
                                                 .yaml(yaml)
                                                 .build();
    Environment environment = EnvironmentMapper.toEnvironmentEntity("ACCOUNT_ID", requestDTO);
    assertThat(environment).isNotNull();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testToEnvironmentValidateConfigFilesAndManifestOverride() {
    final String filename = "env-with-all-override.yaml";
    final String yaml = readFile(filename, getClass());
    final EnvironmentRequestDTO requestDTO = EnvironmentRequestDTO.builder()
                                                 .identifier("ENV")
                                                 .orgIdentifier("ORG_ID")
                                                 .projectIdentifier("PROJECT_ID")
                                                 .type(Production)
                                                 .yaml(yaml)
                                                 .build();
    Environment environment = EnvironmentMapper.toEnvironmentEntity("ACCOUNT_ID", requestDTO);
    assertThat(environment).isNotNull();
    final NGEnvironmentConfig ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(environment);
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig()).isNotNull();
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride()).isNotNull();
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride().getConfigFiles())
        .hasSize(2);
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig()
                   .getNgEnvironmentGlobalOverride()
                   .getConfigFiles()
                   .stream()
                   .map(ConfigFileWrapper::getConfigFile)
                   .map(ConfigFile::getIdentifier))
        .containsExactly("c1", "c2");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testToEnvironmentValidateConfigFilesOverrideOnly() {
    final String filename = "env-with-only-config-files-only.yaml";
    final String yaml = readFile(filename, getClass());
    final EnvironmentRequestDTO requestDTO = EnvironmentRequestDTO.builder()
                                                 .identifier("ENV")
                                                 .orgIdentifier("ORG_ID")
                                                 .projectIdentifier("PROJECT_ID")
                                                 .type(Production)
                                                 .yaml(yaml)
                                                 .build();
    Environment environment = EnvironmentMapper.toEnvironmentEntity("ACCOUNT_ID", requestDTO);
    assertThat(environment).isNotNull();
    final NGEnvironmentConfig ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(environment);
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig()).isNotNull();
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride()).isNotNull();
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride().getConfigFiles())
        .hasSize(2);
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig()
                   .getNgEnvironmentGlobalOverride()
                   .getConfigFiles()
                   .stream()
                   .map(ConfigFileWrapper::getConfigFile)
                   .map(ConfigFile::getIdentifier))
        .containsExactly("c1", "c2");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testToEnvForInputFromRequestDTO() {
    final String filename = "env-with-no-override.yaml";
    final String yaml = readFile(filename, getClass());
    final EnvironmentRequestDTO requestDTO = EnvironmentRequestDTO.builder()
                                                 .identifier("ENV1")
                                                 .orgIdentifier("ORG_ID")
                                                 .projectIdentifier("PROJECT_ID")
                                                 .description("dto description")
                                                 .type(Production)
                                                 .tags(Collections.singletonMap("dto_key", "dto_value"))
                                                 .yaml(yaml)
                                                 .build();
    Environment environment = EnvironmentMapper.toEnvironmentEntity("ACCOUNT_ID", requestDTO);
    assertThat(environment).isNotNull();
    assertThat(environment.getTags()).hasSize(1);
    assertThat(environment.getTags().get(0).getKey()).isEqualTo("dto_key");
    assertThat(environment.getTags().get(0).getValue()).isEqualTo("dto_value");
    assertThat(environment.getDescription()).isEqualTo(requestDTO.getDescription());
    assertThat(environment.getType()).isEqualTo(requestDTO.getType());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testToEnvironmentNoOverrideFfBehaviour() {
    final String filename = "env-with-no-override.yaml";
    final String yaml = readFile(filename, getClass());
    final EnvironmentRequestDTO requestDTO = EnvironmentRequestDTO.builder()
                                                 .identifier("ENV1")
                                                 .orgIdentifier("ORG_ID")
                                                 .projectIdentifier("PROJECT_ID")
                                                 .type(Production)
                                                 .yaml(yaml)
                                                 .build();
    Environment environment = EnvironmentMapper.toEnvironmentEntity("ACCOUNT_ID", requestDTO);
    assertThat(environment).isNotNull();
    NGEnvironmentConfig ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(environment);
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig()).isNotNull();
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride()).isNull();

    environment = EnvironmentMapper.toEnvironmentEntity("ACCOUNT_ID", requestDTO);
    ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(environment);
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig()).isNotNull();
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride()).isNull();

    environment = EnvironmentMapper.toEnvironmentEntity("ACCOUNT_ID", requestDTO);
    ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(environment);
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig()).isNotNull();
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride()).isNull();

    environment = EnvironmentMapper.toEnvironmentEntity("ACCOUNT_ID", requestDTO);
    ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(environment);
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig()).isNotNull();
    assertThat(ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride()).isNull();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testToEnvironmentValidateConfigFileOverrideFail() {
    final String filename = "env-with-invalid-config-files-overrides.yaml";
    final String yaml = readFile(filename, getClass());
    final EnvironmentRequestDTO requestDTO = EnvironmentRequestDTO.builder()
                                                 .identifier("ENV")
                                                 .orgIdentifier("ORG_ID")
                                                 .projectIdentifier("PROJECT_ID")
                                                 .type(PreProduction)
                                                 .yaml(yaml)
                                                 .build();
    assertThatThrownBy(() -> EnvironmentMapper.toEnvironmentEntity("ACCOUNT_ID", requestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Found duplicate configFiles identifiers [c1,c2]");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testToEnvironmentValidateManifestOverrideFail() {
    final String filename = "env-with-invalid-manifest-overrides.yaml";
    final String yaml = readFile(filename, getClass());
    final EnvironmentRequestDTO requestDTO = EnvironmentRequestDTO.builder()
                                                 .identifier("ENV")
                                                 .orgIdentifier("ORG_ID")
                                                 .projectIdentifier("PROJECT_ID")
                                                 .type(PreProduction)
                                                 .yaml(yaml)
                                                 .build();
    assertThatThrownBy(() -> EnvironmentMapper.toEnvironmentEntity("ACCOUNT_ID", requestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Found duplicate manifest identifiers [m1]");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testMultipleHelmOverridesException() {
    final String filename = "env-with-multiple-helm-overrides.yaml";
    final String yaml = readFile(filename, getClass());
    final EnvironmentRequestDTO requestDTO = EnvironmentRequestDTO.builder()
                                                 .identifier("ENV")
                                                 .orgIdentifier("ORG_ID")
                                                 .projectIdentifier("PROJECT_ID")
                                                 .type(PreProduction)
                                                 .yaml(yaml)
                                                 .build();
    assertThatThrownBy(() -> EnvironmentMapper.toEnvironmentEntity("ACCOUNT_ID", requestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("You cannot configure multiple Helm Repo Overrides");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testWriteDTO() {
    EnvironmentResponseDTO environmentDTO = EnvironmentMapper.writeDTO(responseEnvironment);
    assertThat(environmentDTO).isNotNull();
    assertThat(environmentDTO).isEqualTo(environmentResponseDTO);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetEnvironmentResponseList() {
    Environment env1 = requestEnvironment.withVersion(10L);
    Environment env2 = requestEnvironment.withVersion(20L);

    List<EnvironmentResponse> environmentResponseList = EnvironmentMapper.toResponseWrapper(Arrays.asList(env1, env2));
    assertThat(environmentResponseList.size()).isEqualTo(2);
    assertThat(environmentResponseList.get(0).getEnvironment().getVersion()).isEqualTo(10L);
    assertThat(environmentResponseList.get(1).getEnvironment().getVersion()).isEqualTo(20L);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testToYaml() {
    NGEnvironmentConfig cfg = NGEnvironmentConfig.builder()
                                  .ngEnvironmentInfoConfig(NGEnvironmentInfoConfig.builder()
                                                               .identifier("id1")
                                                               .name("name")
                                                               .orgIdentifier("orgId")
                                                               .projectIdentifier("projId")
                                                               .description("desc")
                                                               .type(PreProduction)
                                                               .tags(ImmutableMap.of("k1", "v2"))
                                                               .build())
                                  .build();
    String yaml = EnvironmentMapper.toYaml(cfg);
    assertThat(yaml).isEqualTo("environment:\n"
        + "  orgIdentifier: \"orgId\"\n"
        + "  projectIdentifier: \"projId\"\n"
        + "  identifier: \"id1\"\n"
        + "  tags:\n"
        + "    k1: \"v2\"\n"
        + "  name: \"name\"\n"
        + "  description: \"desc\"\n"
        + "  type: \"PreProduction\"\n");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testToNGEnvironmentConfig() {
    Environment entity = Environment.builder()
                             .type(PreProduction)
                             .accountId("accountId")
                             .orgIdentifier("orgId")
                             .projectIdentifier("projId")
                             .identifier("id")
                             .name("name")
                             .tag(NGTag.builder().key("k1").value("v1").build())
                             .build();
    NGEnvironmentConfig ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(entity);

    NGEnvironmentInfoConfig cfg = ngEnvironmentConfig.getNgEnvironmentInfoConfig();

    assertThat(cfg.getIdentifier()).isEqualTo("id");
    assertThat(cfg.getOrgIdentifier()).isEqualTo("orgId");
    assertThat(cfg.getProjectIdentifier()).isEqualTo("projId");
    assertThat(cfg.getName()).isEqualTo("name");
    assertThat(cfg.getType()).isEqualTo(PreProduction);
    assertThat(cfg.getTags().get("k1")).isEqualTo("v1");
    assertThat(cfg.getVariables()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testTestToNGEnvironmentConfig() {
    EnvironmentRequestDTO dto = EnvironmentRequestDTO.builder()
                                    .identifier("id")
                                    .name("name")
                                    .orgIdentifier("orgId")
                                    .projectIdentifier("projId")
                                    .description("desc")
                                    .type(PreProduction)
                                    .tags(ImmutableMap.of("k1", "v1"))
                                    .build();

    NGEnvironmentConfig ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(dto);

    NGEnvironmentInfoConfig cfg = ngEnvironmentConfig.getNgEnvironmentInfoConfig();

    assertThat(cfg.getIdentifier()).isEqualTo("id");
    assertThat(cfg.getOrgIdentifier()).isEqualTo("orgId");
    assertThat(cfg.getProjectIdentifier()).isEqualTo("projId");
    assertThat(cfg.getName()).isEqualTo("name");
    assertThat(cfg.getType()).isEqualTo(PreProduction);
    assertThat(cfg.getTags().get("k1")).isEqualTo("v1");
    assertThat(cfg.getVariables()).isNull();
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testToOrgLevelEnvironment() {
    final String filename = "env-org-level.yaml";

    final String yaml = readFile(filename, getClass());

    final EnvironmentRequestDTO requestDTO =
        EnvironmentRequestDTO.builder().identifier("env").orgIdentifier("ORG_ID").type(Production).yaml(yaml).build();

    assertThat(EnvironmentMapper.toEnvironmentEntity("ACCOUNT_ID", requestDTO)).isNotNull();
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testToEnvironmentValidateInvalidFields() {
    final String filename = "env-with-manifest-overrides.yaml";
    final String yaml = readFile(filename, getClass());
    final EnvironmentRequestDTO requestDTO = EnvironmentRequestDTO.builder()
                                                 .identifier("invalidId")
                                                 .name("invalidName")
                                                 .projectIdentifier("invalidProjectId")
                                                 .type(PreProduction)
                                                 .yaml(yaml)
                                                 .build();
    assertThatThrownBy(() -> EnvironmentMapper.toEnvironmentEntity("ACCOUNT_ID", requestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "For the environment [name: invalidName, identifier: invalidId], Found mismatch in following fields between yaml and requested value respectively: {Environment type=[Production, PreProduction], Org Identifier=[ORG_ID, null], Project Identifier =[PROJECT_ID, invalidProjectId], Environment Identifier=[ENV, invalidId], Environment name=[envtest, invalidName]}");
  }
}
