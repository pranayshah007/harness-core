/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.YOGESH;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitsync.sdk.CacheState;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.TemplateSummaryResponseDTO;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;

import com.google.common.io.Resources;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class NGTemplateDtoMapperTest extends CategoryTest {
  private final String ACCOUNT_ID = "accountId";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";
  private final String TEMPLATE_CHILD_TYPE = "ShellScript";

  private String yaml;
  TemplateEntity entity;

  @Before
  public void setUp() throws IOException {
    yaml = readFile("template.yaml");

    entity = TemplateEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .identifier(TEMPLATE_IDENTIFIER)
                 .name(TEMPLATE_IDENTIFIER)
                 .versionLabel(TEMPLATE_VERSION_LABEL)
                 .yaml(yaml)
                 .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                 .childType(TEMPLATE_CHILD_TYPE)
                 .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                 .templateScope(Scope.PROJECT)
                 .version(0L)
                 .build();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testWriteTemplateResponseDto() {
    TemplateResponseDTO templateResponseDTO = NGTemplateDtoMapper.writeTemplateResponseDto(entity);
    assertThat(templateResponseDTO).isNotNull();
    assertThat(templateResponseDTO.getIdentifier()).isEqualTo(entity.getIdentifier());
    assertThat(templateResponseDTO.getAccountId()).isEqualTo(entity.getAccountIdentifier());
    assertThat(templateResponseDTO.getOrgIdentifier()).isEqualTo(entity.getOrgIdentifier());
    assertThat(templateResponseDTO.getProjectIdentifier()).isEqualTo(entity.getProjectIdentifier());
    assertThat(templateResponseDTO.getYaml()).isEqualTo(entity.getYaml());
    assertThat(templateResponseDTO.getName()).isEqualTo(entity.getName());
    assertThat(templateResponseDTO.getDescription()).isEqualTo(entity.getDescription());
    assertThat(templateResponseDTO.isStableTemplate()).isEqualTo(entity.isStableTemplate());
    assertThat(templateResponseDTO.getTemplateEntityType()).isEqualTo(entity.getTemplateEntityType());
    assertThat(templateResponseDTO.getChildType()).isEqualTo(entity.getChildType());
    assertThat(templateResponseDTO.getTemplateScope()).isEqualTo(entity.getTemplateScope());
    assertThat(templateResponseDTO.getVersionLabel()).isEqualTo(entity.getVersionLabel());
    assertThat(templateResponseDTO.getVersion()).isEqualTo(entity.getVersion());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testWriteTemplateSummaryResponseDto() {
    TemplateSummaryResponseDTO templateSummaryResponseDTO =
        NGTemplateDtoMapper.prepareTemplateSummaryResponseDto(entity);
    assertThat(templateSummaryResponseDTO).isNotNull();
    assertThat(templateSummaryResponseDTO.getIdentifier()).isEqualTo(entity.getIdentifier());
    assertThat(templateSummaryResponseDTO.getAccountId()).isEqualTo(entity.getAccountIdentifier());
    assertThat(templateSummaryResponseDTO.getOrgIdentifier()).isEqualTo(entity.getOrgIdentifier());
    assertThat(templateSummaryResponseDTO.getProjectIdentifier()).isEqualTo(entity.getProjectIdentifier());
    assertThat(templateSummaryResponseDTO.getYaml()).isEqualTo(entity.getYaml());
    assertThat(templateSummaryResponseDTO.getName()).isEqualTo(entity.getName());
    assertThat(templateSummaryResponseDTO.getDescription()).isEqualTo(entity.getDescription());
    assertThat(templateSummaryResponseDTO.isStableTemplate()).isEqualTo(entity.isStableTemplate());
    assertThat(templateSummaryResponseDTO.getTemplateEntityType()).isEqualTo(entity.getTemplateEntityType());
    assertThat(templateSummaryResponseDTO.getChildType()).isEqualTo(entity.getChildType());
    assertThat(templateSummaryResponseDTO.getTemplateScope()).isEqualTo(entity.getTemplateScope());
    assertThat(templateSummaryResponseDTO.getVersionLabel()).isEqualTo(entity.getVersionLabel());
    assertThat(templateSummaryResponseDTO.getVersion()).isEqualTo(entity.getVersion());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testToTemplateEntity() {
    TemplateEntity result = NGTemplateDtoMapper.toTemplateEntity(ACCOUNT_ID, yaml);
    assertThat(result).isNotNull();
    assertThat(result.getIdentifier()).isEqualTo(entity.getIdentifier());
    assertThat(result.getAccountId()).isEqualTo(entity.getAccountIdentifier());
    assertThat(result.getOrgIdentifier()).isEqualTo(entity.getOrgIdentifier());
    assertThat(result.getProjectIdentifier()).isEqualTo(entity.getProjectIdentifier());
    assertThat(result.getYaml()).isEqualTo(entity.getYaml());
    assertThat(result.getName()).isEqualTo(entity.getName());
    assertThat(result.getDescription()).isEqualTo(entity.getDescription() == null ? "" : entity.getDescription());
    assertThat(result.isStableTemplate()).isEqualTo(entity.isStableTemplate());
    assertThat(result.getTemplateEntityType()).isEqualTo(entity.getTemplateEntityType());
    assertThat(result.getChildType()).isEqualTo(entity.getChildType());
    assertThat(result.getTemplateScope()).isEqualTo(entity.getTemplateScope());
    assertThat(result.getVersionLabel()).isEqualTo(entity.getVersionLabel());

    result = NGTemplateDtoMapper.toTemplateEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml);
    assertThat(result).isNotNull();
    assertThat(result.getIdentifier()).isEqualTo(entity.getIdentifier());
    assertThat(result.getAccountId()).isEqualTo(entity.getAccountIdentifier());
    assertThat(result.getOrgIdentifier()).isEqualTo(entity.getOrgIdentifier());
    assertThat(result.getProjectIdentifier()).isEqualTo(entity.getProjectIdentifier());
    assertThat(result.getYaml()).isEqualTo(entity.getYaml());
    assertThat(result.getName()).isEqualTo(entity.getName());
    assertThat(result.getDescription()).isEqualTo(entity.getDescription() == null ? "" : entity.getDescription());
    assertThat(result.isStableTemplate()).isEqualTo(entity.isStableTemplate());
    assertThat(result.getTemplateEntityType()).isEqualTo(entity.getTemplateEntityType());
    assertThat(result.getChildType()).isEqualTo(entity.getChildType());
    assertThat(result.getTemplateScope()).isEqualTo(entity.getTemplateScope());
    assertThat(result.getVersionLabel()).isEqualTo(entity.getVersionLabel());

    result = NGTemplateDtoMapper.toTemplateEntity(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, yaml);
    assertThat(result).isNotNull();
    assertThat(result.getIdentifier()).isEqualTo(entity.getIdentifier());
    assertThat(result.getAccountId()).isEqualTo(entity.getAccountIdentifier());
    assertThat(result.getOrgIdentifier()).isEqualTo(entity.getOrgIdentifier());
    assertThat(result.getProjectIdentifier()).isEqualTo(entity.getProjectIdentifier());
    assertThat(result.getYaml()).isEqualTo(entity.getYaml());
    assertThat(result.getName()).isEqualTo(entity.getName());
    assertThat(result.getDescription()).isEqualTo(entity.getDescription() == null ? "" : entity.getDescription());
    assertThat(result.isStableTemplate()).isEqualTo(entity.isStableTemplate());
    assertThat(result.getTemplateEntityType()).isEqualTo(entity.getTemplateEntityType());
    assertThat(result.getChildType()).isEqualTo(entity.getChildType());
    assertThat(result.getTemplateScope()).isEqualTo(entity.getTemplateScope());
    assertThat(result.getVersionLabel()).isEqualTo(entity.getVersionLabel());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testToTemplateEntityStepGroupTemplate() throws IOException {
    String yaml = readFile("stepgroup-template.yaml");
    TemplateEntity result = NGTemplateDtoMapper.toTemplateEntity(ACCOUNT_ID, yaml);

    assertThat(result.getChildType()).isEqualTo("Deployment");
  }

  private String readFile(String filename) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testToTemplateDto() {
    NGTemplateConfig ngTemplateConfig = NGTemplateDtoMapper.toDTO(yaml);
    assertThat(ngTemplateConfig).isNotNull();
    assertThat(ngTemplateConfig.getTemplateInfoConfig().getIdentifier()).isEqualTo(entity.getIdentifier());
    assertThat(ngTemplateConfig.getTemplateInfoConfig().getVersionLabel()).isEqualTo(entity.getVersionLabel());

    ngTemplateConfig = NGTemplateDtoMapper.toDTO(entity);
    assertThat(ngTemplateConfig).isNotNull();
    assertThat(ngTemplateConfig.getTemplateInfoConfig().getIdentifier()).isEqualTo(entity.getIdentifier());
    assertThat(ngTemplateConfig.getTemplateInfoConfig().getVersionLabel()).isEqualTo(entity.getVersionLabel());

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> NGTemplateDtoMapper.toDTO("    template:\n   name: \"some_name\""))
        .withMessageContaining(
            "The provided template yaml does not contain the \"template\" keyword at the root level");

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> NGTemplateDtoMapper.toDTO("abc: qwe"))
        .withMessageContaining(
            "The provided template yaml does not contain the \"template\" keyword at the root level");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testVersionLabelValidations() {
    String yaml = "template:\n"
        + "  identifier: template1\n"
        + "  versionLabel: Version 1\n"
        + "  name: template1\n"
        + "  type: Step";

    assertThatThrownBy(() -> NGTemplateDtoMapper.toTemplateEntity(ACCOUNT_ID, yaml))
        .isInstanceOf(JerseyViolationException.class);

    String yaml2 = "template:\n"
        + "  identifier: template1\n"
        + "  versionLabel: _Version1\n"
        + "  name: template1\n"
        + "  type: Step";

    assertThatThrownBy(() -> NGTemplateDtoMapper.toTemplateEntity(ACCOUNT_ID, yaml2))
        .isInstanceOf(JerseyViolationException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testValidateIconForTemplateWithInvalidFormat() {
    String icon1 = "data:image/pmg;base64,ICONSTRING";
    assertThatThrownBy(() -> NGTemplateDtoMapper.validateIconForTemplate(icon1))
        .isInstanceOf(InvalidRequestException.class);

    String icon2 = "ICONSTRING";
    assertThatThrownBy(() -> NGTemplateDtoMapper.validateIconForTemplate(icon2))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test(expected = Test.None.class)
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testValidateIconForTemplateWithValidFormat() {
    String icon1 = "data:image/png;base64,ICONSTRING";
    NGTemplateDtoMapper.validateIconForTemplate(icon1);
    String icon2 = "data:image/jpeg;base64,ICONSTRING";
    NGTemplateDtoMapper.validateIconForTemplate(icon2);
    String icon3 = "data:image/jpg;base64,ICONSTRING";
    NGTemplateDtoMapper.validateIconForTemplate(icon3);
    String icon4 = "data:image/svg+xml;base64,ICONSTRING";
    NGTemplateDtoMapper.validateIconForTemplate(icon4);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testWritePipelineDtoWithCache() {
    CacheResponse cacheResponse = CacheResponse.builder().cacheState(CacheState.VALID_CACHE).build();

    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").cacheResponse(cacheResponse).build());

    TemplateEntity remote = TemplateEntity.builder()
                                .accountId(ACCOUNT_ID)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJ_IDENTIFIER)
                                .identifier(TEMPLATE_IDENTIFIER)
                                .name(TEMPLATE_IDENTIFIER)
                                .versionLabel(TEMPLATE_VERSION_LABEL)
                                .yaml(yaml)
                                .storeType(StoreType.REMOTE)
                                .build();

    TemplateResponseDTO templateResponseDTO = NGTemplateDtoMapper.writeTemplateResponseDto(remote);
    assertThat(templateResponseDTO).isNotNull();
    assertThat(templateResponseDTO.getCacheResponseMetadata().getCacheState()).isEqualTo(CacheState.VALID_CACHE);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testParseLoadFromCacheHeaderParam() {
    //    when null is passed for string loadFromCache
    assertFalse(NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(null));
    //    when empty is passed for string loadFromCache
    assertFalse(NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(""));
    //    when true is passed for string loadFromCache
    assertTrue(NGTemplateDtoMapper.parseLoadFromCacheHeaderParam("true"));
    //    when false is passed for string loadFromCache
    assertFalse(NGTemplateDtoMapper.parseLoadFromCacheHeaderParam("false"));
    //    when junk value is passed for string loadFromCache
    assertFalse(NGTemplateDtoMapper.parseLoadFromCacheHeaderParam("abcs"));
  }
}
