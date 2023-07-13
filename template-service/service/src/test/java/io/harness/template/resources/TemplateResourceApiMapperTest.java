/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.rule.OwnerRule.TARUN_UBA;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateMetadataSummaryResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.TemplateWithInputsResponseDTO;
import io.harness.rule.Owner;
import io.harness.spec.server.template.v1.model.GitCreateDetails;
import io.harness.spec.server.template.v1.model.GitFindDetails;
import io.harness.spec.server.template.v1.model.GitUpdateDetails;
import io.harness.spec.server.template.v1.model.TemplateMetadataSummaryResponse;
import io.harness.spec.server.template.v1.model.TemplateResponse;
import io.harness.spec.server.template.v1.model.TemplateWithInputsResponse;

import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.HashSet;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Path;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.metadata.ConstraintDescriptor;
import org.hibernate.validator.internal.engine.path.PathImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class TemplateResourceApiMapperTest extends CategoryTest {
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";
  private final String TEMPLATE_CHILD_TYPE = "ShellScript";
  private final String INPUT_YAML = "Input YAML not requested";
  private final String BRANCH = "example_branch";
  private final String FILE_PATH = "example_file_path";
  private final String REPO_NAME = "example_repo_name";
  private final String COMMIT_ID = "example_commit_id";
  private final String FILE_URL = "example_file_url";
  private final String REPO_URL = "example_repo_url";
  private final String OBJECT_ID = "example_object_id";
  String name = randomAlphabetic(10);
  String description = randomAlphabetic(10);
  String versionLabel = randomAlphabetic(10);

  private TemplateResourceApiMapper templateResourceApiMapper;
  @Spy @InjectMocks TemplateResourceApiMapper templateResourceApiMapperWithMockedValidator;
  private Validator validator;
  @Mock Validator mockedValidator;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
    templateResourceApiMapper = new TemplateResourceApiMapper(validator);
    templateResourceApiMapperWithMockedValidator = new TemplateResourceApiMapper(mockedValidator);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testEntityGitDetailsWithAndWithoutJerseyError() {
    EntityGitDetails entityGitDetails = EntityGitDetails.builder()
                                            .branch(BRANCH)
                                            .filePath(FILE_PATH)
                                            .commitId(COMMIT_ID)
                                            .fileUrl(FILE_URL)
                                            .objectId(OBJECT_ID)
                                            .repoName(REPO_NAME)
                                            .repoUrl(REPO_URL)
                                            .build();

    io.harness.spec.server.template.v1.model.EntityGitDetails responseGitDetails =
        templateResourceApiMapper.toEntityGitDetails(entityGitDetails);

    assertEquals(BRANCH, responseGitDetails.getBranchName());
    assertEquals(COMMIT_ID, responseGitDetails.getCommitId());
    assertEquals(REPO_NAME, responseGitDetails.getRepoName());
    assertEquals(FILE_PATH, responseGitDetails.getFilePath());
    assertEquals(OBJECT_ID, responseGitDetails.getObjectId());
    assertEquals(FILE_URL, responseGitDetails.getFileUrl());
    assertEquals(REPO_URL, responseGitDetails.getRepoUrl());

    Set<ConstraintViolation<Object>> violations = new HashSet<>();
    ConstraintViolation<Object> violation1 = createDummyViolation("Field1", "Value is too short");
    violations.add(violation1);
    violations.add(violation1);
    doReturn(violations)
        .when(mockedValidator)
        .validate(any(io.harness.spec.server.template.v1.model.EntityGitDetails.class));
    assertThatThrownBy(() -> templateResourceApiMapperWithMockedValidator.toEntityGitDetails(entityGitDetails))
        .isInstanceOf(JerseyViolationException.class)
        .hasMessage("Field1: Value is too short");
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testTemplateMetadataResponse() {
    EntityGitDetails entityGitDetails = EntityGitDetails.builder()
                                            .branch(BRANCH)
                                            .filePath(FILE_PATH)
                                            .commitId(COMMIT_ID)
                                            .fileUrl(FILE_URL)
                                            .objectId(OBJECT_ID)
                                            .repoName(REPO_NAME)
                                            .repoUrl(REPO_URL)
                                            .build();
    TemplateMetadataSummaryResponseDTO templateMetadataSummaryResponseDTO =
        TemplateMetadataSummaryResponseDTO.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .identifier(OBJECT_ID)
            .name(name)
            .description(description)
            .versionLabel(versionLabel)
            .templateScope(Scope.fromString("project"))
            .templateEntityType(TemplateEntityType.getTemplateType("STAGE"))
            .storeType(StoreType.getFromStringOrNull("INLINE"))
            .gitDetails(entityGitDetails)
            .lastUpdatedAt(123456789L)
            .stableTemplate(true)
            .build();

    TemplateMetadataSummaryResponse templateMetadataSummaryResponse =
        templateResourceApiMapper.mapToTemplateMetadataResponse(templateMetadataSummaryResponseDTO);
    Set<ConstraintViolation<Object>> violations = validator.validate(templateMetadataSummaryResponse);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    assertEquals(ACCOUNT_ID, templateMetadataSummaryResponse.getAccount());
    assertEquals(description, templateMetadataSummaryResponse.getDescription());
    assertEquals(ORG_IDENTIFIER, templateMetadataSummaryResponse.getOrg());
    assertEquals(PROJ_IDENTIFIER, templateMetadataSummaryResponse.getProject());
    assertEquals(name, templateMetadataSummaryResponse.getName());
    assertEquals("Stage", templateMetadataSummaryResponse.getEntityType().toString());
    assertEquals("INLINE", templateMetadataSummaryResponse.getStoreType().toString());
    assertEquals("project", templateMetadataSummaryResponse.getScope().toString());
    assertEquals(OBJECT_ID, templateMetadataSummaryResponse.getIdentifier());
    assertEquals(true, templateMetadataSummaryResponse.isStableTemplate().booleanValue());
    assertEquals(123456789L, templateMetadataSummaryResponse.getUpdated().longValue());
    assertEquals(versionLabel, templateMetadataSummaryResponse.getVersionLabel());
    assertEquals(entityGitDetails.getBranch(), templateMetadataSummaryResponse.getGitDetails().getBranchName());
    assertEquals(entityGitDetails.getCommitId(), templateMetadataSummaryResponse.getGitDetails().getCommitId());
    assertEquals(entityGitDetails.getFilePath(), templateMetadataSummaryResponse.getGitDetails().getFilePath());
    assertEquals(entityGitDetails.getRepoName(), templateMetadataSummaryResponse.getGitDetails().getRepoName());
    assertEquals(entityGitDetails.getFileUrl(), templateMetadataSummaryResponse.getGitDetails().getFileUrl());
    assertEquals(entityGitDetails.getRepoUrl(), templateMetadataSummaryResponse.getGitDetails().getRepoUrl());
    assertEquals(entityGitDetails.getObjectId(), templateMetadataSummaryResponse.getGitDetails().getObjectId());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testToTemplateWithInputResponseWithJersayValdationError() {
    TemplateResponseDTO templateResponseDTO = TemplateResponseDTO.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .identifier(OBJECT_ID)
                                                  .name(name)
                                                  .description(description)
                                                  .versionLabel(versionLabel)
                                                  .templateScope(Scope.fromString("project"))
                                                  .templateEntityType(TemplateEntityType.getTemplateType("STAGE"))
                                                  .storeType(StoreType.getFromStringOrNull("INLINE"))
                                                  .gitDetails(null)
                                                  .lastUpdatedAt(123456789L)
                                                  .gitDetails(EntityGitDetails.builder().build())
                                                  .isStableTemplate(true)
                                                  .yaml("example_yaml")
                                                  .build();
    TemplateWithInputsResponseDTO templateWithInputsResponseDTO = TemplateWithInputsResponseDTO.builder()
                                                                      .templateResponseDTO(templateResponseDTO)
                                                                      .templateInputs("inputs")
                                                                      .build();
    Set<ConstraintViolation<Object>> violations = new HashSet<>();
    ConstraintViolation<Object> violation1 = createDummyViolation("Field1", "Value is too short");
    violations.add(violation1);
    violations.add(violation1);
    doReturn(violations).when(mockedValidator).validate(any(TemplateResponseDTO.class));
    assertThatThrownBy(
        () -> templateResourceApiMapperWithMockedValidator.toTemplateWithInputResponse(templateWithInputsResponseDTO))
        .isInstanceOf(JerseyViolationException.class)
        .hasMessage("Field1: Value is too short");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testToTemplateResponseDefaultWithJersayValdationError() {
    TemplateResponseDTO templateResponseDTO = TemplateResponseDTO.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .identifier(OBJECT_ID)
                                                  .name(name)
                                                  .description(description)
                                                  .versionLabel(versionLabel)
                                                  .templateScope(Scope.fromString("project"))
                                                  .templateEntityType(TemplateEntityType.getTemplateType("STAGE"))
                                                  .storeType(StoreType.getFromStringOrNull("INLINE"))
                                                  .gitDetails(null)
                                                  .lastUpdatedAt(123456789L)
                                                  .gitDetails(EntityGitDetails.builder().build())
                                                  .isStableTemplate(true)
                                                  .yaml("example_yaml")
                                                  .build();
    Set<ConstraintViolation<Object>> violations = new HashSet<>();
    ConstraintViolation<Object> violation1 = createDummyViolation("Field1", "Value is too short");
    violations.add(violation1);
    violations.add(violation1);
    doReturn(violations).when(mockedValidator).validate(any(TemplateWithInputsResponse.class));
    assertThatThrownBy(
        () -> templateResourceApiMapperWithMockedValidator.toTemplateResponseDefault(templateResponseDTO))
        .isInstanceOf(JerseyViolationException.class)
        .hasMessage("Field1: Value is too short");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testToTemplateResponseWithJersayValdationError() {
    TemplateResponseDTO templateResponseDTO = TemplateResponseDTO.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .identifier(OBJECT_ID)
                                                  .name(name)
                                                  .description(description)
                                                  .versionLabel(versionLabel)
                                                  .templateScope(Scope.fromString("project"))
                                                  .templateEntityType(TemplateEntityType.getTemplateType("STAGE"))
                                                  .storeType(StoreType.getFromStringOrNull("INLINE"))
                                                  .gitDetails(null)
                                                  .lastUpdatedAt(123456789L)
                                                  .gitDetails(EntityGitDetails.builder().build())
                                                  .isStableTemplate(true)
                                                  .yaml("example_yaml")
                                                  .build();
    Set<ConstraintViolation<Object>> violations = new HashSet<>();
    ConstraintViolation<Object> violation1 = createDummyViolation("Field1", "Value is too short");
    violations.add(violation1);
    violations.add(violation1);
    doReturn(violations).when(mockedValidator).validate(any(TemplateResponse.class));
    assertThatThrownBy(() -> templateResourceApiMapperWithMockedValidator.toTemplateResponse(templateResponseDTO))
        .isInstanceOf(JerseyViolationException.class)
        .hasMessage("Field1: Value is too short");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testTemplateMetadataResponseWithNullStoreTypeAndJerseyValidationError() {
    EntityGitDetails entityGitDetails = EntityGitDetails.builder()
                                            .branch(BRANCH)
                                            .filePath(FILE_PATH)
                                            .commitId(COMMIT_ID)
                                            .fileUrl(FILE_URL)
                                            .objectId(OBJECT_ID)
                                            .repoName(REPO_NAME)
                                            .repoUrl(REPO_URL)
                                            .build();
    TemplateMetadataSummaryResponseDTO templateMetadataSummaryResponseDTO =
        TemplateMetadataSummaryResponseDTO.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .identifier(OBJECT_ID)
            .name(name)
            .description(description)
            .versionLabel(versionLabel)
            .templateScope(Scope.fromString("project"))
            .templateEntityType(TemplateEntityType.getTemplateType("STAGE"))
            .gitDetails(entityGitDetails)
            .lastUpdatedAt(123456789L)
            .stableTemplate(true)
            .build();

    TemplateMetadataSummaryResponse templateMetadataSummaryResponse =
        templateResourceApiMapper.mapToTemplateMetadataResponse(templateMetadataSummaryResponseDTO);
    Set<ConstraintViolation<Object>> violations = validator.validate(templateMetadataSummaryResponse);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    assertEquals(ACCOUNT_ID, templateMetadataSummaryResponse.getAccount());
    assertEquals(description, templateMetadataSummaryResponse.getDescription());
    assertEquals(ORG_IDENTIFIER, templateMetadataSummaryResponse.getOrg());
    assertEquals(PROJ_IDENTIFIER, templateMetadataSummaryResponse.getProject());
    assertEquals(name, templateMetadataSummaryResponse.getName());
    assertEquals("Stage", templateMetadataSummaryResponse.getEntityType().toString());
    assertThat(templateMetadataSummaryResponseDTO.getStoreType()).isNull();
    assertEquals("project", templateMetadataSummaryResponse.getScope().toString());
    assertEquals(OBJECT_ID, templateMetadataSummaryResponse.getIdentifier());
    assertEquals(true, templateMetadataSummaryResponse.isStableTemplate().booleanValue());
    assertEquals(123456789L, templateMetadataSummaryResponse.getUpdated().longValue());
    assertEquals(versionLabel, templateMetadataSummaryResponse.getVersionLabel());
    assertEquals(entityGitDetails.getBranch(), templateMetadataSummaryResponse.getGitDetails().getBranchName());
    assertEquals(entityGitDetails.getCommitId(), templateMetadataSummaryResponse.getGitDetails().getCommitId());
    assertEquals(entityGitDetails.getFilePath(), templateMetadataSummaryResponse.getGitDetails().getFilePath());
    assertEquals(entityGitDetails.getRepoName(), templateMetadataSummaryResponse.getGitDetails().getRepoName());
    assertEquals(entityGitDetails.getFileUrl(), templateMetadataSummaryResponse.getGitDetails().getFileUrl());
    assertEquals(entityGitDetails.getRepoUrl(), templateMetadataSummaryResponse.getGitDetails().getRepoUrl());
    assertEquals(entityGitDetails.getObjectId(), templateMetadataSummaryResponse.getGitDetails().getObjectId());

    violations = new HashSet<>();
    ConstraintViolation<Object> violation1 = createDummyViolation("Field1", "Value is too short");
    violations.add(violation1);
    doReturn(violations).when(mockedValidator).validate(any(TemplateMetadataSummaryResponseDTO.class));
    assertThatThrownBy(()
                           -> templateResourceApiMapperWithMockedValidator.mapToTemplateMetadataResponse(
                               templateMetadataSummaryResponseDTO))
        .isInstanceOf(JerseyViolationException.class)
        .hasMessage("Field1: Value is too short");
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testTemplateResponse() {
    EntityGitDetails entityGitDetails = EntityGitDetails.builder()
                                            .branch(BRANCH)
                                            .filePath(FILE_PATH)
                                            .commitId(COMMIT_ID)
                                            .fileUrl(FILE_URL)
                                            .objectId(OBJECT_ID)
                                            .repoName(REPO_NAME)
                                            .repoUrl(REPO_URL)
                                            .build();
    TemplateResponseDTO templateResponseDTO = TemplateResponseDTO.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .identifier(OBJECT_ID)
                                                  .name(name)
                                                  .description(description)
                                                  .versionLabel(versionLabel)
                                                  .templateScope(Scope.fromString("project"))
                                                  .templateEntityType(TemplateEntityType.getTemplateType("STAGE"))
                                                  .storeType(StoreType.getFromStringOrNull("INLINE"))
                                                  .gitDetails(entityGitDetails)
                                                  .lastUpdatedAt(123456789L)
                                                  .isStableTemplate(true)
                                                  .yaml("example_yaml")
                                                  .build();

    TemplateResponse templateResponse = templateResourceApiMapper.toTemplateResponse(templateResponseDTO);
    Set<ConstraintViolation<Object>> violations = validator.validate(templateResponse);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();
    assertEquals(ACCOUNT_ID, templateResponse.getAccount());
    assertEquals(description, templateResponse.getDescription());
    assertEquals(ORG_IDENTIFIER, templateResponse.getOrg());
    assertEquals(PROJ_IDENTIFIER, templateResponse.getProject());
    assertEquals(name, templateResponse.getName());
    assertEquals("Stage", templateResponse.getEntityType().toString());
    assertEquals(OBJECT_ID, templateResponse.getIdentifier());
    assertEquals(true, templateResponse.isStableTemplate().booleanValue());
    assertEquals(123456789L, templateResponse.getUpdated().longValue());
    assertEquals(versionLabel, templateResponse.getVersionLabel());
    assertEquals("project", templateResponse.getScope().toString());
    assertEquals("INLINE", templateResponse.getStoreType().toString());
    assertEquals("example_yaml", templateResponse.getYaml());
    assertEquals(entityGitDetails.getBranch(), templateResponse.getGitDetails().getBranchName());
    assertEquals(entityGitDetails.getCommitId(), templateResponse.getGitDetails().getCommitId());
    assertEquals(entityGitDetails.getFilePath(), templateResponse.getGitDetails().getFilePath());
    assertEquals(entityGitDetails.getRepoName(), templateResponse.getGitDetails().getRepoName());
    assertEquals(entityGitDetails.getFileUrl(), templateResponse.getGitDetails().getFileUrl());
    assertEquals(entityGitDetails.getRepoUrl(), templateResponse.getGitDetails().getRepoUrl());
    assertEquals(entityGitDetails.getObjectId(), templateResponse.getGitDetails().getObjectId());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testTemplateResponseDefault() {
    EntityGitDetails entityGitDetails = EntityGitDetails.builder()
                                            .branch(BRANCH)
                                            .filePath(FILE_PATH)
                                            .commitId(COMMIT_ID)
                                            .fileUrl(FILE_URL)
                                            .objectId(OBJECT_ID)
                                            .repoName(REPO_NAME)
                                            .repoUrl(REPO_URL)
                                            .build();
    TemplateResponseDTO templateResponseDTO = TemplateResponseDTO.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .identifier(OBJECT_ID)
                                                  .name(name)
                                                  .description(description)
                                                  .versionLabel(versionLabel)
                                                  .templateScope(Scope.fromString("project"))
                                                  .templateEntityType(TemplateEntityType.getTemplateType("Stage"))
                                                  .storeType(StoreType.getFromStringOrNull("INLINE"))
                                                  .gitDetails(entityGitDetails)
                                                  .lastUpdatedAt(123456789L)
                                                  .isStableTemplate(true)
                                                  .yaml("example_yaml")
                                                  .build();

    TemplateWithInputsResponse templateWithInputsResponse =
        templateResourceApiMapper.toTemplateResponseDefault(templateResponseDTO);
    TemplateResponse templateResponse = templateWithInputsResponse.getTemplate();
    Set<ConstraintViolation<Object>> violations = validator.validate(templateResponse);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();
    assertEquals(ACCOUNT_ID, templateResponse.getAccount());
    assertEquals(description, templateResponse.getDescription());
    assertEquals(ORG_IDENTIFIER, templateResponse.getOrg());
    assertEquals(PROJ_IDENTIFIER, templateResponse.getProject());
    assertEquals(name, templateResponse.getName());
    assertEquals("Stage", templateResponse.getEntityType().toString());
    assertEquals(OBJECT_ID, templateResponse.getIdentifier());
    assertEquals(true, templateResponse.isStableTemplate().booleanValue());
    assertEquals(123456789L, templateResponse.getUpdated().longValue());
    assertEquals("example_yaml", templateResponse.getYaml());
    assertEquals(versionLabel, templateResponse.getVersionLabel());
    assertEquals("project", templateResponse.getScope().toString());
    assertEquals("INLINE", templateResponse.getStoreType().toString());
    assertEquals(entityGitDetails.getBranch(), templateResponse.getGitDetails().getBranchName());
    assertEquals(entityGitDetails.getCommitId(), templateResponse.getGitDetails().getCommitId());
    assertEquals(entityGitDetails.getFilePath(), templateResponse.getGitDetails().getFilePath());
    assertEquals(entityGitDetails.getRepoName(), templateResponse.getGitDetails().getRepoName());
    assertEquals(entityGitDetails.getFileUrl(), templateResponse.getGitDetails().getFileUrl());
    assertEquals(entityGitDetails.getRepoUrl(), templateResponse.getGitDetails().getRepoUrl());
    assertEquals(entityGitDetails.getObjectId(), templateResponse.getGitDetails().getObjectId());
    assertEquals(templateWithInputsResponse.getInputs(), "Input YAML not requested");
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testTemplateWithInputResponse() {
    EntityGitDetails entityGitDetails = EntityGitDetails.builder()
                                            .branch(BRANCH)
                                            .filePath(FILE_PATH)
                                            .commitId(COMMIT_ID)
                                            .fileUrl(FILE_URL)
                                            .objectId(OBJECT_ID)
                                            .repoName(REPO_NAME)
                                            .repoUrl(REPO_URL)
                                            .build();
    TemplateResponseDTO templateResponseDTO = TemplateResponseDTO.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .identifier(OBJECT_ID)
                                                  .name(name)
                                                  .description(description)
                                                  .versionLabel(versionLabel)
                                                  .templateScope(Scope.fromString("project"))
                                                  .templateEntityType(TemplateEntityType.getTemplateType("Stage"))
                                                  .storeType(StoreType.getFromStringOrNull("INLINE"))
                                                  .gitDetails(entityGitDetails)
                                                  .lastUpdatedAt(123456789L)
                                                  .isStableTemplate(true)
                                                  .yaml("example_yaml")
                                                  .build();
    TemplateWithInputsResponseDTO templateWithInputsResponseDTO = TemplateWithInputsResponseDTO.builder()
                                                                      .templateResponseDTO(templateResponseDTO)
                                                                      .templateInputs("With Input Response")
                                                                      .build();
    TemplateWithInputsResponse templateWithInputsResponse =
        templateResourceApiMapper.toTemplateWithInputResponse(templateWithInputsResponseDTO);
    TemplateResponse templateResponse = templateWithInputsResponse.getTemplate();
    assertEquals(ACCOUNT_ID, templateResponse.getAccount());
    assertEquals(description, templateResponse.getDescription());
    assertEquals(ORG_IDENTIFIER, templateResponse.getOrg());
    assertEquals(PROJ_IDENTIFIER, templateResponse.getProject());
    assertEquals(name, templateResponse.getName());
    assertEquals("Stage", templateResponse.getEntityType().toString());
    assertEquals(OBJECT_ID, templateResponse.getIdentifier());
    assertEquals("example_yaml", templateResponse.getYaml());
    assertEquals(true, templateResponse.isStableTemplate().booleanValue());
    assertEquals(123456789L, templateResponse.getUpdated().longValue());
    assertEquals(versionLabel, templateResponse.getVersionLabel());
    assertEquals("project", templateResponse.getScope().toString());
    assertEquals("INLINE", templateResponse.getStoreType().toString());
    assertEquals(entityGitDetails.getBranch(), templateResponse.getGitDetails().getBranchName());
    assertEquals(entityGitDetails.getCommitId(), templateResponse.getGitDetails().getCommitId());
    assertEquals(entityGitDetails.getFilePath(), templateResponse.getGitDetails().getFilePath());
    assertEquals(entityGitDetails.getRepoName(), templateResponse.getGitDetails().getRepoName());
    assertEquals(entityGitDetails.getFileUrl(), templateResponse.getGitDetails().getFileUrl());
    assertEquals(entityGitDetails.getRepoUrl(), templateResponse.getGitDetails().getRepoUrl());
    assertEquals(entityGitDetails.getObjectId(), templateResponse.getGitDetails().getObjectId());
    assertEquals(templateWithInputsResponse.getInputs(), "With Input Response");
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testPopulatingCreateGitDetailsInlineWhenNull() {
    GitCreateDetails gitCreateDetails = new GitCreateDetails();
    gitCreateDetails.setBaseBranch(null);
    gitCreateDetails.setStoreType(null);
    GitEntityInfo gitEntityInfo = templateResourceApiMapper.populateGitCreateDetails(gitCreateDetails);
    assertEquals(gitEntityInfo.getStoreType(), StoreType.INLINE);
    GitEntityInfo gitEntityInfo1 = templateResourceApiMapper.populateGitCreateDetails(null);
    assertNull(gitEntityInfo1.getStoreType());
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testPopulatingCreateGitDetailsRemote() {
    GitCreateDetails gitCreateDetails = new GitCreateDetails();
    gitCreateDetails.setBaseBranch(null);
    gitCreateDetails.setStoreType(GitCreateDetails.StoreTypeEnum.REMOTE);
    GitEntityInfo gitEntityInfo = templateResourceApiMapper.populateGitCreateDetails(gitCreateDetails);
    assertEquals(gitEntityInfo.getStoreType(), StoreType.REMOTE);
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testPopulatingCreateGitDetailsInline() {
    GitCreateDetails gitCreateDetails = new GitCreateDetails();
    gitCreateDetails.setBaseBranch(null);
    gitCreateDetails.setStoreType(GitCreateDetails.StoreTypeEnum.INLINE);
    GitEntityInfo gitEntityInfo = templateResourceApiMapper.populateGitCreateDetails(gitCreateDetails);
    assertEquals(gitEntityInfo.getStoreType(), StoreType.INLINE);
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testPopulatingUpdateGitDetailsInlineWhenNull() {
    GitUpdateDetails gitUpdateDetails = new GitUpdateDetails();
    gitUpdateDetails.setBaseBranch(null);
    gitUpdateDetails.setStoreType(null);
    GitEntityInfo gitEntityInfo = templateResourceApiMapper.populateGitUpdateDetails(gitUpdateDetails);
    assertEquals(gitEntityInfo.getStoreType(), StoreType.INLINE);
    gitEntityInfo = templateResourceApiMapper.populateGitUpdateDetails(null);
    assertNull(gitEntityInfo.getStoreType());
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testPopulatingUpdateGitDetailsRemote() {
    GitUpdateDetails gitUpdateDetails = new GitUpdateDetails();
    gitUpdateDetails.setBaseBranch(null);
    gitUpdateDetails.setStoreType(GitUpdateDetails.StoreTypeEnum.REMOTE);
    GitEntityInfo gitEntityInfo = templateResourceApiMapper.populateGitUpdateDetails(gitUpdateDetails);
    assertEquals(gitEntityInfo.getStoreType(), StoreType.REMOTE);
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testPopulatingUpdateGitDetailsInline() {
    GitUpdateDetails gitUpdateDetails = new GitUpdateDetails();
    gitUpdateDetails.setBaseBranch(null);
    gitUpdateDetails.setStoreType(GitUpdateDetails.StoreTypeEnum.INLINE);
    GitEntityInfo gitEntityInfo = templateResourceApiMapper.populateGitUpdateDetails(gitUpdateDetails);
    assertEquals(gitEntityInfo.getStoreType(), StoreType.INLINE);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testPopulateGitFindDetails() {
    GitFindDetails gitFindDetails = new GitFindDetails();
    gitFindDetails.setBranchName("main");
    GitEntityInfo gitEntityInfo = templateResourceApiMapper.populateGitFindDetails(gitFindDetails);
    assertThat(gitEntityInfo.getBranch()).isEqualTo("main");
    gitEntityInfo = templateResourceApiMapper.populateGitFindDetails(null);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testMapSort() {
    String order = templateResourceApiMapper.mapSort("", "");
    assertThat(order).isEqualTo("lastUpdatedAt,DESC");

    order = templateResourceApiMapper.mapSort("", "DESC");
    assertThat(order).isEqualTo("lastUpdatedAt,DESC");

    order = templateResourceApiMapper.mapSort("identifier", "");
    assertThat(order).isEqualTo("identifier,DESC");

    order = templateResourceApiMapper.mapSort("name", "");
    assertThat(order).isEqualTo("name,DESC");

    order = templateResourceApiMapper.mapSort("updated", "");
    assertThat(order).isEqualTo("lastUpdatedAt,DESC");

    assertThatThrownBy(() -> templateResourceApiMapper.mapSort("xyz", ""))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Field provided for sorting unidentified. Accepted values: identifier / name / updated");
  }

  private static ConstraintViolation<Object> createDummyViolation(String propertyPath, String message) {
    return new ConstraintViolation<Object>() {
      @Override
      public Object getRootBean() {
        return null;
      }

      @Override
      public Class<Object> getRootBeanClass() {
        return Object.class;
      }

      @Override
      public Object getLeafBean() {
        return null;
      }

      @Override
      public Object[] getExecutableParameters() {
        return new Object[0];
      }

      @Override
      public Object getExecutableReturnValue() {
        return null;
      }

      @Override
      public Path getPropertyPath() {
        return PathImpl.createPathFromString(propertyPath);
      }

      @Override
      public Object getInvalidValue() {
        return null;
      }

      @Override
      public ConstraintDescriptor<?> getConstraintDescriptor() {
        return null;
      }

      @Override
      public <U> U unwrap(Class<U> type) {
        return null;
      }

      @Override
      public String getMessage() {
        return message;
      }

      @Override
      public String getMessageTemplate() {
        return null;
      }
    };
  }
}
