/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.TemplateServiceTestBase;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateListType;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.yaml.ParameterField;
import io.harness.project.remote.ProjectClient;
import io.harness.repositories.NGTemplateRepository;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.springdata.TransactionHelper;
import io.harness.template.TemplateFilterPropertiesDTO;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.helpers.TemplateReferenceHelper;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(CDC)
public class NGTemplateServiceImplTest extends TemplateServiceTestBase {
  @Mock EnforcementClientService enforcementClientService;
  @InjectMocks private NGTemplateServiceHelper templateServiceHelper;
  @Inject private GitSyncSdkService gitSyncSdkService;
  @Inject private NGTemplateRepository templateRepository;
  @Inject private TransactionHelper transactionHelper;
  @Mock private ProjectClient projectClient;
  @Mock private OrganizationClient organizationClient;
  @Mock private TemplateReferenceHelper templateReferenceHelper;
  @Mock AccountClient accountClient;

  @InjectMocks NGTemplateServiceImpl templateService;

  private final String ACCOUNT_ID = RandomStringUtils.randomAlphanumeric(6);
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";
  private final String TEMPLATE_CHILD_TYPE = "ShellScript";

  private String yaml;

  TemplateEntity entity;

  @Before
  public void setUp() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "template.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    on(templateServiceHelper).set("templateRepository", templateRepository);
    on(templateService).set("templateRepository", templateRepository);
    on(templateService).set("gitSyncSdkService", gitSyncSdkService);
    on(templateService).set("transactionHelper", transactionHelper);
    on(templateService).set("templateServiceHelper", templateServiceHelper);
    on(templateService).set("enforcementClientService", enforcementClientService);
    on(templateService).set("projectClient", projectClient);
    on(templateService).set("organizationClient", organizationClient);
    on(templateService).set("templateReferenceHelper", templateReferenceHelper);
    on(templateService).set("accountClient", accountClient);

    doNothing().when(enforcementClientService).checkAvailability(any(), any());
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
                 .build();

    Call<ResponseDTO<Optional<ProjectResponse>>> projectCall = mock(Call.class);
    when(projectClient.getProject(anyString(), anyString(), anyString())).thenReturn(projectCall);
    when(projectCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(Optional.of(ProjectResponse.builder().build()))));

    Call<ResponseDTO<Optional<OrganizationResponse>>> organizationCall = mock(Call.class);
    when(organizationClient.getOrganization(anyString(), anyString())).thenReturn(organizationCall);
    when(organizationCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(Optional.of(OrganizationResponse.builder().build()))));

    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(eq(FeatureName.HARD_DELETE_ENTITIES.name()), anyString()))
        .thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(false)));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testServiceLayerForProjectScopeTemplates() {
    TemplateEntity createdEntity = templateService.create(entity, false, "");
    assertThat(createdEntity).isNotNull();
    assertThat(createdEntity.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(createdEntity.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(createdEntity.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(createdEntity.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(createdEntity.getVersion()).isEqualTo(0L);

    Optional<TemplateEntity> optionalTemplateEntity = templateService.get(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, false);
    assertThat(optionalTemplateEntity).isPresent();
    assertThat(optionalTemplateEntity.get().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(optionalTemplateEntity.get().getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(optionalTemplateEntity.get().getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(optionalTemplateEntity.get().getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(optionalTemplateEntity.get().getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(optionalTemplateEntity.get().getVersion()).isEqualTo(0L);

    String description = "Updated Description";
    TemplateEntity updateTemplate = entity.withDescription(description);
    TemplateEntity updatedTemplateEntity =
        templateService.updateTemplateEntity(updateTemplate, ChangeType.MODIFY, false, "");
    assertThat(updatedTemplateEntity).isNotNull();
    assertThat(updatedTemplateEntity.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(updatedTemplateEntity.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(updatedTemplateEntity.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(updatedTemplateEntity.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(updatedTemplateEntity.getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(updatedTemplateEntity.getVersion()).isEqualTo(1L);
    assertThat(updatedTemplateEntity.getDescription()).isEqualTo(description);

    TemplateEntity incorrectTemplate = entity.withVersionLabel("incorrect version");
    assertThatThrownBy(() -> templateService.updateTemplateEntity(incorrectTemplate, ChangeType.MODIFY, false, ""))
        .isInstanceOf(InvalidRequestException.class);

    // Test template list
    Criteria criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    Pageable pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    Page<TemplateEntity> templateEntities =
        templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);

    // Add 1 more entry to template db
    TemplateEntity version2 = entity.withVersionLabel("version2");
    templateService.create(version2, false, "");

    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(2);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("version2");
    // test for lastUpdatedBy
    assertThat(templateEntities.getContent().get(0).isLastUpdatedTemplate()).isFalse();
    assertThat(templateEntities.getContent().get(1).isLastUpdatedTemplate()).isTrue();

    // Template list with search term
    criteria = templateServiceHelper.formCriteria(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "version2", false);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo("version2");

    // Update stable template
    TemplateEntity updateStableTemplateVersion = templateService.updateStableTemplateVersion(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, "version2", "");
    assertThat(updateStableTemplateVersion).isNotNull();
    assertThat(updateStableTemplateVersion.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(updateStableTemplateVersion.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(updateStableTemplateVersion.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(updateStableTemplateVersion.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(updateStableTemplateVersion.getVersionLabel()).isEqualTo("version2");
    assertThat(updateStableTemplateVersion.getVersion()).isEqualTo(1L);
    assertThat(updateStableTemplateVersion.isStableTemplate()).isTrue();

    // Add 1 more entry to template db
    TemplateEntity version3 = entity.withVersionLabel("version3");
    templateService.create(version3, false, "");

    // Testing updating stable template to check the lastUpdatedBy flag
    updateStableTemplateVersion = templateService.updateStableTemplateVersion(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, "version2", "");
    assertThat(updateStableTemplateVersion.isLastUpdatedTemplate()).isTrue();

    // delete template stable template
    assertThatThrownBy(()
                           -> templateService.delete(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, "version2", 1L, ""))
        .isInstanceOf(InvalidRequestException.class);

    boolean markEntityInvalid = templateService.markEntityInvalid(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, "INVALID_YAML");
    assertThat(markEntityInvalid).isTrue();
    assertThatThrownBy(()
                           -> templateService.getOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, false))
        .isInstanceOf(NGTemplateException.class);

    boolean delete = templateService.delete(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, null, "");
    assertThat(delete).isTrue();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteTemplateVersionScenarios() {
    TemplateEntity createdEntity = templateService.create(entity, false, "");
    assertThat(createdEntity.isStableTemplate()).isTrue();

    TemplateEntity entityVersion2 = templateService.create(entity.withVersionLabel("version2"), true, "");
    assertThat(entityVersion2.isStableTemplate()).isTrue();

    TemplateEntity entityVersion3 = templateService.create(entity.withVersionLabel("version3"), false, "");
    assertThat(entityVersion3.isStableTemplate()).isFalse();
    assertThat(entityVersion3.isLastUpdatedTemplate()).isTrue();

    TemplateEntity template2EntityVersion2 =
        templateService.create(entity.withVersionLabel("version2").withIdentifier("template2"), false, "");
    assertThat(template2EntityVersion2.isStableTemplate()).isTrue();

    TemplateEntity template2EntityVersion3 =
        templateService.create(entity.withVersionLabel("version3").withIdentifier("template2"), true, "");
    assertThat(template2EntityVersion3.isStableTemplate()).isTrue();
    assertThat(template2EntityVersion3.isLastUpdatedTemplate()).isTrue();

    // Deleting a last updated version for a particular templateIdentifier.
    boolean delete =
        templateService.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, "version3", null, "");
    assertThat(delete).isTrue();

    Criteria criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    criteria.and(TemplateEntityKeys.isLastUpdatedTemplate).is(true);
    Pageable pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    Page<TemplateEntity> templateEntities =
        templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(2);

    // Deleting a non last update template version
    delete = templateService.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "template2", "version2", null, "");
    assertThat(delete).isTrue();
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(2);

    // Deleting complete templateIdentifier
    delete = templateService.deleteTemplates(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, Sets.newHashSet("version1", "version2"), "");
    assertThat(delete).isTrue();
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);

    criteria = templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null,
        TemplateFilterPropertiesDTO.builder()
            .childTypes(Collections.singletonList(TEMPLATE_CHILD_TYPE))
            .templateEntityTypes(Collections.singletonList(TemplateEntityType.STEP_TEMPLATE))
            .build(),
        false, "", false);
    criteria.and(TemplateEntityKeys.isStableTemplate).is(true);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCreateAndUpdateWithStableTemplate() {
    TemplateEntity createdEntity = templateService.create(entity, false, "");
    assertThat(createdEntity.isStableTemplate()).isTrue();

    TemplateEntity entityVersion2 = templateService.create(entity.withVersionLabel("version2"), false, "");
    assertThat(entityVersion2.isStableTemplate()).isFalse();

    TemplateEntity entityVersion3 = templateService.create(entity.withVersionLabel("version3"), true, "");
    assertThat(entityVersion3.isStableTemplate()).isTrue();

    Criteria criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    Pageable pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    Page<TemplateEntity> templateEntities =
        templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(3);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(templateEntities.getContent().get(0).isStableTemplate()).isFalse();

    // Check update stable template
    TemplateEntity updatedEntity =
        templateService.updateTemplateEntity(entityVersion2.withDescription("Updated"), ChangeType.MODIFY, true, "");
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(3);
    assertThat(templateEntities.getContent().get(1).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("version2");
    assertThat(templateEntities.getContent().get(1).isStableTemplate()).isTrue();
    assertThat(templateEntities.getContent().get(2).getVersionLabel()).isEqualTo("version3");
    assertThat(templateEntities.getContent().get(2).isStableTemplate()).isFalse();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testServiceLayerForOrgScopeTemplates() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "template-orgScope.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);

    entity = NGTemplateDtoMapper.toTemplateEntity(ACCOUNT_ID, yaml);

    TemplateEntity createdEntity = templateService.create(entity, false, "");
    assertThat(createdEntity).isNotNull();
    assertThat(createdEntity.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(createdEntity.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(createdEntity.getProjectIdentifier()).isNull();
    assertThat(createdEntity.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(createdEntity.getVersion()).isEqualTo(0L);

    Optional<TemplateEntity> optionalTemplateEntity =
        templateService.get(ACCOUNT_ID, ORG_IDENTIFIER, null, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, false);
    assertThat(optionalTemplateEntity).isPresent();
    assertThat(optionalTemplateEntity.get().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(optionalTemplateEntity.get().getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(optionalTemplateEntity.get().getProjectIdentifier()).isNull();
    assertThat(optionalTemplateEntity.get().getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(optionalTemplateEntity.get().getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(optionalTemplateEntity.get().getVersion()).isEqualTo(0L);

    String description = "Updated Description";
    TemplateEntity updateTemplate = entity.withDescription(description);
    TemplateEntity updatedTemplateEntity =
        templateService.updateTemplateEntity(updateTemplate, ChangeType.MODIFY, false, "");
    assertThat(updatedTemplateEntity).isNotNull();
    assertThat(updatedTemplateEntity.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(updatedTemplateEntity.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(updatedTemplateEntity.getProjectIdentifier()).isNull();
    assertThat(updatedTemplateEntity.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(updatedTemplateEntity.getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(updatedTemplateEntity.getVersion()).isEqualTo(1L);
    assertThat(updatedTemplateEntity.getDescription()).isEqualTo(description);

    TemplateEntity incorrectTemplate = entity.withVersionLabel("incorrect version");
    assertThatThrownBy(() -> templateService.updateTemplateEntity(incorrectTemplate, ChangeType.MODIFY, false, ""))
        .isInstanceOf(InvalidRequestException.class);

    // Test template list
    Criteria criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, null, null, null, false, "", false);
    Pageable pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    Page<TemplateEntity> templateEntities =
        templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);

    // Add 1 more entry to template db
    TemplateEntity version2 = entity.withVersionLabel("version2");
    templateService.create(version2, false, "");

    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(2);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("version2");

    // Template list with search term
    criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, null, null, null, false, "version2", false);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo("version2");

    // Update stable template
    TemplateEntity updateStableTemplateVersion = templateService.updateStableTemplateVersion(
        ACCOUNT_ID, ORG_IDENTIFIER, null, TEMPLATE_IDENTIFIER, "version2", "");
    assertThat(updateStableTemplateVersion).isNotNull();
    assertThat(updateStableTemplateVersion.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(updateStableTemplateVersion.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(updateStableTemplateVersion.getProjectIdentifier()).isNull();
    assertThat(updateStableTemplateVersion.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(updateStableTemplateVersion.getVersionLabel()).isEqualTo("version2");
    assertThat(updateStableTemplateVersion.getVersion()).isEqualTo(1L);
    assertThat(updateStableTemplateVersion.isStableTemplate()).isTrue();

    // delete template stable template
    assertThatThrownBy(
        () -> templateService.delete(ACCOUNT_ID, ORG_IDENTIFIER, null, TEMPLATE_IDENTIFIER, "version2", 1L, ""))
        .isInstanceOf(InvalidRequestException.class);

    boolean delete =
        templateService.delete(ACCOUNT_ID, ORG_IDENTIFIER, null, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, null, "");
    assertThat(delete).isTrue();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testTemplateSettingsChangeScope() throws JsonProcessingException {
    // Test to update scope from project to org
    templateService.create(entity, false, "");
    // Add 1 more entry to template db
    TemplateEntity version2 = entity.withVersionLabel("version2");
    NGTemplateConfig config = NGTemplateDtoMapper.toDTO(version2.getYaml());
    config.getTemplateInfoConfig().setVersionLabel("version2");
    config.getTemplateInfoConfig().setDescription(ParameterField.createValueField(""));
    version2 = entity.withVersionLabel("version2").withYaml(YamlPipelineUtils.getYamlString(config));
    templateService.create(version2, false, "");

    TemplateEntity version3;
    config.getTemplateInfoConfig().setVersionLabel("version3");
    config.getTemplateInfoConfig().setDescription(ParameterField.createValueField(""));
    version3 = entity.withVersionLabel("version3").withYaml(YamlPipelineUtils.getYamlString(config));
    templateService.create(version3, false, "");

    // Adding different template identifier to just cover more test cases
    TemplateEntity differentIdentifierTemplate =
        TemplateEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .identifier("DifferentIdentifier")
            .name(TEMPLATE_IDENTIFIER)
            .versionLabel("DifferentVersion")
            .yaml(yaml)
            .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
            .childType(TEMPLATE_CHILD_TYPE)
            .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
            .templateScope(Scope.PROJECT)
            .build();
    templateService.create(differentIdentifierTemplate, false, "");

    Criteria criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    Pageable pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    Page<TemplateEntity> templateEntities =
        templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(4);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("version2");
    assertThat(templateEntities.getContent().get(2).getVersionLabel()).isEqualTo("version3");
    assertThat(templateEntities.getContent().get(0).isStableTemplate()).isTrue();
    assertThat(templateEntities.getContent().get(0).getTemplateScope()).isEqualTo(Scope.PROJECT);
    assertThat(templateEntities.getContent().get(1).getTemplateScope()).isEqualTo(Scope.PROJECT);
    assertThat(templateEntities.getContent().get(2).getTemplateScope()).isEqualTo(Scope.PROJECT);
    assertThat(templateEntities.getContent().get(2).isLastUpdatedTemplate()).isTrue();

    // Check for last update criteria
    criteria = templateServiceHelper.formCriteria(criteria, TemplateListType.LAST_UPDATED_TEMPLATE_TYPE);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo("version3");
    assertThat(templateEntities.getContent().get(0).isLastUpdatedTemplate()).isTrue();
    assertThat(templateEntities.getContent().get(1).getIdentifier()).isEqualTo("DifferentIdentifier");
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("DifferentVersion");
    assertThat(templateEntities.getContent().get(1).isLastUpdatedTemplate()).isTrue();

    // Update version2 to check lastUpdatedBy
    version2 = version2.withDescription("Updated desciption");
    templateService.updateTemplateEntity(version2, ChangeType.MODIFY, false, "");
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent().get(1).isLastUpdatedTemplate()).isTrue();

    // Call template scope change from project to org
    templateService.updateTemplateSettings(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, Scope.PROJECT, Scope.ORG, "version3", false);

    criteria = templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, null, null, null, false, "", false);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(3);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("version2");
    assertThat(templateEntities.getContent().get(2).getVersionLabel()).isEqualTo("version3");
    assertThat(templateEntities.getContent().get(0).isStableTemplate()).isFalse();
    assertThat(templateEntities.getContent().get(2).isStableTemplate()).isTrue();
    assertThat(templateEntities.getContent().get(0).getTemplateScope()).isEqualTo(Scope.ORG);
    assertThat(templateEntities.getContent().get(1).getTemplateScope()).isEqualTo(Scope.ORG);
    assertThat(templateEntities.getContent().get(2).getTemplateScope()).isEqualTo(Scope.ORG);
    assertThat(templateEntities.getContent().get(2).isLastUpdatedTemplate()).isTrue();

    // Test to check include all templates
    criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", true);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(4);

    // Test to update scope from org to project
    // Call template scope change from project to org
    templateService.updateTemplateSettings(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, Scope.ORG, Scope.PROJECT, "version3", false);

    criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(4);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("version2");
    assertThat(templateEntities.getContent().get(2).getVersionLabel()).isEqualTo("version3");
    assertThat(templateEntities.getContent().get(0).isStableTemplate()).isFalse();
    assertThat(templateEntities.getContent().get(2).isStableTemplate()).isTrue();
    assertThat(templateEntities.getContent().get(0).getTemplateScope()).isEqualTo(Scope.PROJECT);
    assertThat(templateEntities.getContent().get(1).getTemplateScope()).isEqualTo(Scope.PROJECT);
    assertThat(templateEntities.getContent().get(2).getTemplateScope()).isEqualTo(Scope.PROJECT);

    // Only stable template update and no scope change
    templateService.updateTemplateSettings(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER,
        Scope.PROJECT, Scope.PROJECT, "version1", false);

    criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(4);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("version2");
    assertThat(templateEntities.getContent().get(2).getVersionLabel()).isEqualTo("version3");
    assertThat(templateEntities.getContent().get(0).isStableTemplate()).isTrue();
    assertThat(templateEntities.getContent().get(2).isStableTemplate()).isFalse();
    assertThat(templateEntities.getContent().get(0).getTemplateScope()).isEqualTo(Scope.PROJECT);
    assertThat(templateEntities.getContent().get(1).getTemplateScope()).isEqualTo(Scope.PROJECT);
    assertThat(templateEntities.getContent().get(2).getTemplateScope()).isEqualTo(Scope.PROJECT);

    // Test multiple template delete

    boolean deleteTemplates = templateService.deleteTemplates(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        TEMPLATE_IDENTIFIER, new HashSet<>(Arrays.asList(TEMPLATE_VERSION_LABEL, "version2", "version3")), "");
    assertThat(deleteTemplates).isTrue();

    criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo("DifferentIdentifier");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetActualComments() {
    // Testing comments if git sync is not enabled.
    String comments = templateService.getActualComments(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "COMMENTS");
    assertThat(comments).isEqualTo("COMMENTS");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWithEmptyOrganizationIdOnProjectLevelTemplate() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier(TEMPLATE_IDENTIFIER)
                                        .name(TEMPLATE_IDENTIFIER)
                                        .versionLabel(TEMPLATE_VERSION_LABEL)
                                        .yaml(yaml)
                                        .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                                        .fullyQualifiedIdentifier("account_id/projId/template1/version1/")
                                        .templateScope(Scope.PROJECT)
                                        .build();

    assertThatThrownBy(() -> templateService.create(templateEntity, false, ""))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Project projId specified without the org Identifier");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldThrowExceptionIfTemplateAlreadyExists() {
    TemplateEntity createdEntity = templateService.create(entity, false, "");
    assertThat(createdEntity).isNotNull();
    assertThat(createdEntity.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(createdEntity.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(createdEntity.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(createdEntity.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(createdEntity.getVersion()).isEqualTo(0L);

    assertThatThrownBy(() -> templateService.create(entity, false, ""))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "The template with identifier %s and version label %s already exists in the account %s, org %s, project %s",
            entity.getIdentifier(), entity.getVersionLabel(), entity.getAccountId(), entity.getOrgIdentifier(),
            entity.getProjectIdentifier()));
  }
}
