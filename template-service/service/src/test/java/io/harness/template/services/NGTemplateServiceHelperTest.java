/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.ng.core.template.TemplateListType.STABLE_TEMPLATE_TYPE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.ng.core.template.ListingScope;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateListType;
import io.harness.persistence.gitaware.GitAware;
import io.harness.repositories.NGTemplateRepository;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.gitsync.TemplateGitSyncBranchContextGuard;
import io.harness.template.resources.beans.TemplateFilterProperties;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;
import io.harness.template.resources.beans.UpdateGitDetailsParams;

import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(CDC)
public class NGTemplateServiceHelperTest extends CategoryTest {
  NGTemplateServiceHelper templateServiceHelper;
  @Mock FilterService filterService;
  @Mock NGTemplateRepository templateRepository;
  @Mock GitSyncSdkService gitSyncSdkService;

  @Mock TemplateGitXService templateGitXService;

  @Mock GitAwareEntityHelper gitAwareEntityHelper;

  @Mock TelemetryReporter telemetryReporter;

  @Mock @Named("TemplateServiceHelperExecutorService") ExecutorService executorService;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";

  private final String REPO_NAME = "testRepo";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    templateServiceHelper = new NGTemplateServiceHelper(filterService, templateRepository, gitSyncSdkService,
        templateGitXService, gitAwareEntityHelper, telemetryReporter, executorService);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testValidatePresenceOfRequiredFields() {
    assertThatThrownBy(() -> NGTemplateServiceHelper.validatePresenceOfRequiredFields("", null, "2"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("One of the required fields is null.");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetTemplateGitContext() {
    TemplateEntity entity = TemplateEntity.builder()
                                .accountId(ACCOUNT_ID)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJ_IDENTIFIER)
                                .identifier(TEMPLATE_IDENTIFIER)
                                .versionLabel(TEMPLATE_VERSION_LABEL)
                                .filePath("FILE_PATH")
                                .rootFolder("ROOT")
                                .objectIdOfYaml("YAML_ID_TEMPLATE")
                                .build();
    try (TemplateGitSyncBranchContextGuard ignored =
             templateServiceHelper.getTemplateGitContextForGivenTemplate(entity, null, "")) {
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
      assertThat(gitEntityInfo).isNull();
    }

    GitEntityInfo gitEntityInfoInput = GitEntityInfo.builder()
                                           .commitId("COMMIT_ID")
                                           .yamlGitConfigId("YAML_ID")
                                           .branch("BRANCH")
                                           .isSyncFromGit(true)
                                           .build();
    try (TemplateGitSyncBranchContextGuard ignored =
             templateServiceHelper.getTemplateGitContextForGivenTemplate(entity, gitEntityInfoInput, "")) {
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
      assertThat(gitEntityInfo).isNotNull();
      assertThat(gitEntityInfo.getBranch()).isEqualTo("BRANCH");
      assertThat(gitEntityInfo.getYamlGitConfigId()).isEqualTo("YAML_ID");
      assertThat(gitEntityInfo.getFilePath()).isEqualTo("FILE_PATH");
      assertThat(gitEntityInfo.getFolderPath()).isEqualTo("ROOT");
      assertThat(gitEntityInfo.getLastObjectId()).isEqualTo("YAML_ID_TEMPLATE");
      assertThat(gitEntityInfo.isSyncFromGit()).isTrue();
    }
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testFormCriteriaUsingFilterIdentifier() {
    String filterIdentifier = "filterIdentifier";
    FilterDTO filterDTO = FilterDTO.builder()
                              .filterProperties(TemplateFilterPropertiesDTO.builder()
                                                    .templateIdentifiers(Collections.singletonList(TEMPLATE_IDENTIFIER))
                                                    .templateNames(Collections.singletonList(TEMPLATE_IDENTIFIER))
                                                    .build())
                              .build();
    doReturn(filterDTO)
        .when(filterService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, filterIdentifier, FilterType.TEMPLATE);

    Criteria criteria = templateServiceHelper.formCriteria(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, filterIdentifier, null, false, "", false);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(TemplateEntityKeys.accountId)).isEqualTo(ACCOUNT_ID);
    assertThat(criteriaObject.get(TemplateEntityKeys.orgIdentifier)).isEqualTo(ORG_IDENTIFIER);
    assertThat(criteriaObject.get(TemplateEntityKeys.projectIdentifier)).isEqualTo(PROJ_IDENTIFIER);
    assertThat(criteriaObject.get(TemplateEntityKeys.deleted)).isEqualTo(false);

    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get(TemplateEntityKeys.identifier)).get("$in")).size())
        .isEqualTo(1);
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get(TemplateEntityKeys.identifier)).get("$in"))
                   .contains(TEMPLATE_IDENTIFIER));
    assertThat(
        ((Pattern) ((Document) ((List<?>) ((Document) ((List<?>) criteriaObject.get("$and")).get(0)).get("$or")).get(0))
                .get("name"))
            .pattern())
        .isEqualTo("template1");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testFormCriteriaUsingTemplateListType() {
    Criteria criteria = templateServiceHelper.formCriteria(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "", null, false, TEMPLATE_IDENTIFIER, false);
    templateServiceHelper.formCriteria(criteria, TemplateListType.LAST_UPDATED_TEMPLATE_TYPE);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(TemplateEntityKeys.projectIdentifier)).isEqualTo(PROJ_IDENTIFIER);
    assertThat(criteriaObject.get(TemplateEntityKeys.isLastUpdatedTemplate)).isEqualTo(true);

    criteria = templateServiceHelper.formCriteria(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "", null, false, TEMPLATE_IDENTIFIER, false);
    templateServiceHelper.formCriteria(criteria, STABLE_TEMPLATE_TYPE);
    criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(TemplateEntityKeys.projectIdentifier)).isEqualTo(PROJ_IDENTIFIER);
    assertThat(criteriaObject.get(TemplateEntityKeys.isStableTemplate)).isEqualTo(true);

    criteria = templateServiceHelper.formCriteria(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "", null, false, TEMPLATE_IDENTIFIER, true);
    criteriaObject = criteria.getCriteriaObject();
    assertThat(((Document) ((List<?>) ((Document) ((List<?>) criteriaObject.get("$and")).get(0)).get("$or")).get(2))
                   .get("templateScope"))
        .isEqualTo(Scope.ACCOUNT);
    assertThat(((Document) ((List<?>) ((Document) ((List<?>) criteriaObject.get("$and")).get(0)).get("$or")).get(1))
                   .get("templateScope"))
        .isEqualTo(Scope.ORG);
    assertThat(((Document) ((List<?>) ((Document) ((List<?>) criteriaObject.get("$and")).get(0)).get("$or")).get(0))
                   .get("templateScope"))
        .isEqualTo(Scope.PROJECT);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFilterByRepo() {
    TemplateFilterPropertiesDTO filterPropertiesDTO = TemplateFilterPropertiesDTO.builder().repoName(REPO_NAME).build();
    Criteria criteria = templateServiceHelper.formCriteria(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "", filterPropertiesDTO, false, TEMPLATE_IDENTIFIER, false);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(TemplateEntityKeys.repo)).isEqualTo(REPO_NAME);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testFormCriteriaUsingFilterDto() {
    TemplateFilterPropertiesDTO propertiesDTO = TemplateFilterPropertiesDTO.builder()
                                                    .templateNames(Collections.singletonList(TEMPLATE_IDENTIFIER))
                                                    .description("random")
                                                    .listingScope(ListingScope.builder()
                                                                      .accountIdentifier(ACCOUNT_ID)
                                                                      .orgIdentifier(ORG_IDENTIFIER)
                                                                      .projectIdentifier(PROJ_IDENTIFIER)
                                                                      .build())
                                                    .build();
    Criteria criteria = templateServiceHelper.formCriteria(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "", propertiesDTO, false, TEMPLATE_IDENTIFIER, false);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(TemplateEntityKeys.accountId)).isEqualTo(ACCOUNT_ID);
    assertThat(criteriaObject.get(TemplateEntityKeys.orgIdentifier)).isEqualTo(ORG_IDENTIFIER);
    assertThat(criteriaObject.get(TemplateEntityKeys.projectIdentifier)).isEqualTo(PROJ_IDENTIFIER);
    assertThat(criteriaObject.get(TemplateEntityKeys.deleted)).isEqualTo(false);

    assertThat(
        ((Pattern) ((Document) ((List<?>) criteriaObject.get("$and")).get(1)).get(TemplateEntityKeys.description))
            .pattern())
        .isEqualTo("random");
    assertThat(((List<?>) ((Document) ((List<?>) criteriaObject.get("$and")).get(2)).get("$or")).size()).isEqualTo(6);
    assertThat(((Document) ((List<?>) ((Document) ((List<?>) criteriaObject.get("$and")).get(2)).get("$or")).get(0))
                   .get(TemplateEntityKeys.identifier))
        .isNotNull();
    assertThat(((Document) ((List<?>) ((Document) ((List<?>) criteriaObject.get("$and")).get(2)).get("$or")).get(1))
                   .get(TemplateEntityKeys.name))
        .isNotNull();
    assertThat(((Document) ((List<?>) ((Document) ((List<?>) criteriaObject.get("$and")).get(2)).get("$or")).get(2))
                   .get(TemplateEntityKeys.versionLabel))
        .isNotNull();
    assertThat(((Document) ((List<?>) ((Document) ((List<?>) criteriaObject.get("$and")).get(2)).get("$or")).get(3))
                   .get(TemplateEntityKeys.description))
        .isNotNull();
    assertThat(((Document) ((List<?>) ((Document) ((List<?>) criteriaObject.get("$and")).get(2)).get("$or")).get(4))
                   .get("tags.key"))
        .isNotNull();
    assertThat(((Document) ((List<?>) ((Document) ((List<?>) criteriaObject.get("$and")).get(2)).get("$or")).get(5))
                   .get("tags.value"))
        .isNotNull();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFormCriteriaUsingFilterDtoWithSearchTerm() {
    Criteria criteria = templateServiceHelper.formCriteria(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "", false, null, "search", true);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(TemplateEntityKeys.accountId)).isEqualTo(ACCOUNT_ID);
    assertThat(((Document) ((List<?>) ((Document) ((List<?>) criteriaObject.get("$and")).get(1)).get("$or")).get(4))
                   .get("tags.key"))
        .isNotNull();
    assertThat(((Document) ((List<?>) ((Document) ((List<?>) criteriaObject.get("$and")).get(1)).get("$or")).get(5))
                   .get("tags.value"))
        .isNotNull();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFormCriteriaUsingFilterDtoAndFilterIdentifier() {
    TemplateFilterPropertiesDTO propertiesDTO = TemplateFilterPropertiesDTO.builder()
                                                    .templateNames(Collections.singletonList(TEMPLATE_IDENTIFIER))
                                                    .description("random")
                                                    .listingScope(ListingScope.builder()
                                                                      .accountIdentifier(ACCOUNT_ID)
                                                                      .orgIdentifier(ORG_IDENTIFIER)
                                                                      .projectIdentifier(PROJ_IDENTIFIER)
                                                                      .build())
                                                    .build();
    assertThatThrownBy(()
                           -> templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               "filterIdentifier", propertiesDTO, false, TEMPLATE_IDENTIFIER, false))
        .hasMessage("Can not apply both filter properties and saved filter together")
        .isInstanceOf(InvalidRequestException.class);

    TemplateFilterProperties templateFilterProperties =
        TemplateFilterProperties.builder().templateNames(Collections.singletonList(TEMPLATE_IDENTIFIER)).build();
    assertThatThrownBy(()
                           -> templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               "filterIdentifier", false, templateFilterProperties, TEMPLATE_IDENTIFIER, false))
        .hasMessage("Can not apply both filter properties and saved filter together")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFormCriteriaForRepoListing() {
    // Project scope
    Criteria criteria =
        templateServiceHelper.formCriteriaForRepoListing(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(TemplateEntityKeys.accountId)).isEqualTo(ACCOUNT_ID);
    assertThat(criteriaObject.get(TemplateEntityKeys.orgIdentifier)).isEqualTo(ORG_IDENTIFIER);
    assertThat(criteriaObject.get(TemplateEntityKeys.projectIdentifier)).isEqualTo(PROJ_IDENTIFIER);

    // Org scope
    criteria = templateServiceHelper.formCriteriaForRepoListing(ACCOUNT_ID, ORG_IDENTIFIER, "", false);
    criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(TemplateEntityKeys.accountId)).isEqualTo(ACCOUNT_ID);
    assertThat(criteriaObject.get(TemplateEntityKeys.orgIdentifier)).isEqualTo(ORG_IDENTIFIER);
    assertThat(((Document) (criteriaObject.get("projectIdentifier"))).get("$exists").equals(false));

    // Account scope
    criteria = templateServiceHelper.formCriteriaForRepoListing(ACCOUNT_ID, "", "", false);
    criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(TemplateEntityKeys.accountId)).isEqualTo(ACCOUNT_ID);
    assertThat(((Document) (criteriaObject.get("orgIdentifier"))).get("$exists").equals(false));
    assertThat(((Document) (criteriaObject.get("projectIdentifier"))).get("$exists").equals(false));

    criteria = templateServiceHelper.formCriteriaForRepoListing(ACCOUNT_ID, "", "", true);
    criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(TemplateEntityKeys.accountId)).isEqualTo(ACCOUNT_ID);

    criteria = templateServiceHelper.formCriteriaForRepoListing(ACCOUNT_ID, ORG_IDENTIFIER, "", true);
    criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(TemplateEntityKeys.accountId)).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetOrThrowExceptionIfInvalid() {
    Optional<TemplateEntity> template1 =
        Optional.of(TemplateEntity.builder().accountId(ACCOUNT_ID).isEntityInvalid(true).build());
    doReturn(template1)
        .when(templateRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNot(ACCOUNT_ID,
            ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, true, false, false, false);
    assertThatThrownBy(()
                           -> templateServiceHelper.getOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, false, false, false))
        .isInstanceOf(NGTemplateException.class)
        .hasMessage("Invalid Template yaml cannot be used. Please correct the template version yaml.");

    when(
        templateRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "temp2", TEMPLATE_VERSION_LABEL, true, false, false, false))
        .thenThrow(new NullPointerException());
    assertThatThrownBy(()
                           -> templateServiceHelper.getOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, "temp2", TEMPLATE_VERSION_LABEL, false, false, false))
        .isInstanceOf(InvalidRequestException.class);

    when(
        templateRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "temp3", TEMPLATE_VERSION_LABEL, true, false, false, false))
        .thenThrow(new ScmException(ErrorCode.DEFAULT_ERROR_CODE));
    assertThatThrownBy(()
                           -> templateServiceHelper.getOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, "temp3", TEMPLATE_VERSION_LABEL, false, false, false))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testBatchTemplates() {
    String uniqueKey1 = "uniqueKey1";
    Map<String, GitAware> batchFilesResponse = new HashMap<>();
    batchFilesResponse.put(uniqueKey1, TemplateEntity.builder().build());
    doReturn(batchFilesResponse).when(gitAwareEntityHelper).fetchEntitiesFromRemote(ACCOUNT_ID, new HashMap<>());
    templateServiceHelper.getBatchRemoteTemplates(ACCOUNT_ID, new HashMap<>());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOldGitSyncTemplateCases() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .identifier(TEMPLATE_IDENTIFIER)
                                        .versionLabel(TEMPLATE_VERSION_LABEL)
                                        .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                                        .build();
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(anyString(), anyString(), anyString());
    when(templateRepository
             .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNotForOldGitSync(
                 anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Optional.of(templateEntity));
    TemplateEntity entity = templateServiceHelper
                                .getTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, null,
                                    false, false, false, false)
                                .get();
    assertThat(entity.getIdentifier()).isEqualTo("template1");

    when(templateRepository
             .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNotForOldGitSync(
                 anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Optional.of(templateEntity));
    entity = templateServiceHelper
                 .getTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL,
                     false, false, false, false)
                 .get();
    assertThat(entity.getIdentifier()).isEqualTo("template1");

    when(
        templateRepository
            .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNotForOldGitSync(
                anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Optional.of(templateEntity));
    entity = templateServiceHelper
                 .getLastUpdatedTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, false)
                 .get();
    assertThat(entity.getIdentifier()).isEqualTo("template1");

    final Page<TemplateEntity> templateList =
        new PageImpl<>(Collections.singletonList(templateEntity), Pageable.unpaged(), 1);
    when(templateRepository.findAll(any(), any(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(templateList);
    Criteria criteria = templateServiceHelper.formCriteria(new Criteria(), STABLE_TEMPLATE_TYPE);
    Page<TemplateEntity> templates = templateServiceHelper.listTemplate(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, criteria, Pageable.unpaged(), true);
    assertThat(templates.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetGitDetailsUpdate() {
    templateServiceHelper.getGitDetailsUpdate(
        UpdateGitDetailsParams.builder().repoName("repo").connectorRef("connector").filePath("filepath").build());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetComment() {
    assertThat(templateServiceHelper.getComment("created", TEMPLATE_IDENTIFIER, "commitMessage"))
        .isEqualTo("commitMessage");
    assertThat(templateServiceHelper.getComment("created", TEMPLATE_IDENTIFIER, ""))
        .isEqualTo("[HARNESS]: Template with template identifier [template1] has been [created]");
  }
}
