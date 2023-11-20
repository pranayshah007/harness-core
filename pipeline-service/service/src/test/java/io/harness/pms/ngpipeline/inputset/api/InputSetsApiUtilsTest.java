/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.ngpipeline.inputset.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.pipeline.api.PipelinesApiUtils.getMoveConfigType;
import static io.harness.rule.OwnerRule.MANKRIT;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SANDESH_SALUNKHE;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncConstants;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.pms.inputset.InputSetErrorDTOPMS;
import io.harness.pms.inputset.InputSetErrorResponseDTOPMS;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.inputset.InputSetMoveConfigOperationDTO;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.FQNtoError;
import io.harness.spec.server.pipeline.v1.model.GitCreateDetails;
import io.harness.spec.server.pipeline.v1.model.GitDetails;
import io.harness.spec.server.pipeline.v1.model.GitMoveDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetError;
import io.harness.spec.server.pipeline.v1.model.InputSetErrorDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetErrorWrapperDTO;
import io.harness.spec.server.pipeline.v1.model.InputSetGitUpdateDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetResponseBody;
import io.harness.spec.server.pipeline.v1.model.InputSetUpdateRequestBody;
import io.harness.spec.server.pipeline.v1.model.MoveConfigOperationType;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ThreadOperationContextHelper;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

@OwnedBy(PIPELINE)
public class InputSetsApiUtilsTest extends CategoryTest {
  private InputSetsApiUtils inputSetsApiUtils;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private NGSettingsClient ngSettingsClient;
  String identifier = randomAlphabetic(10);
  String name = randomAlphabetic(10);
  String account = "accountId";
  static final String ERROR_PIPELINE_YAML = "error_pipeline_yaml";
  static final String INPUT_SET_YAML = "input_set_yaml";
  static final String UUID = "uuid";
  static final String INVALID_INPUT_SET_REFERENCE = "invalid_input_set_reference";
  static final String ORG_IDENTIFIER = "org1";
  static final String PROJ_IDENTIFIER = "project1";
  static final String DESCRIPTION = "description";
  static final String OBJECT_ID = "objectId";
  static final String BRANCH_NAME = "branchName";
  static final String BASE_BRANCH = "baseBranch";
  static final String COMMIT_MESSAGE = "commit message";
  static final String COMMIT_ID = "commitId";
  static final String FILE_PATH = "filePath";
  static final String REPO_NAME = "repoName";
  static final String CONNECTOR_REF = "connectorRef";
  private GitDetails gitDetails = new GitDetails();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    inputSetsApiUtils = new InputSetsApiUtils(pmsFeatureFlagHelper, ngSettingsClient);
  }

  private String readFile(String filename) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read file " + filename, e);
    }
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetGitDetails() {
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .objectIdOfYaml("objectId")
                                        .branch("branch")
                                        .repo("repoName")
                                        .repoURL("repoURL")
                                        .filePath("filePath")
                                        .build();
    GitDetails gitDetails = inputSetsApiUtils.getGitDetails(inputSetEntity);
    assertEquals("objectId", gitDetails.getObjectId());
    assertEquals("branch", gitDetails.getBranchName());
    assertEquals("filePath", gitDetails.getFilePath());
    assertEquals("repoURL", gitDetails.getRepoUrl());
    assertEquals("repoName", gitDetails.getRepoName());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetGitDetailsRemote() {
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .storeType(StoreType.REMOTE)
                                        .objectIdOfYaml("objectId")
                                        .branch("branch")
                                        .repo("repoName")
                                        .repoURL("repoURL")
                                        .filePath("filePath")
                                        .build();
    EntityGitDetails entityGitDetails = EntityGitDetails.builder()
                                            .objectId("objectId")
                                            .branch("branch")
                                            .commitId("commitId")
                                            .filePath("filePath")
                                            .fileUrl("fileURL")
                                            .repoName("repoName")
                                            .repoIdentifier("repoId")
                                            .repoUrl("repoURL")
                                            .build();
    mockStatic(GitAwareContextHelper.class);
    when(GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata()).thenReturn(entityGitDetails);
    GitDetails gitDetails = inputSetsApiUtils.getGitDetails(inputSetEntity);
    assertEquals("objectId", gitDetails.getObjectId());
    assertEquals("branch", gitDetails.getBranchName());
    assertEquals("filePath", gitDetails.getFilePath());
    assertEquals("repoURL", gitDetails.getRepoUrl());
    assertEquals("repoName", gitDetails.getRepoName());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetGitDetailsInline() {
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .storeType(StoreType.INLINE)
                                        .objectIdOfYaml("objectId")
                                        .branch("branch")
                                        .repo("repoName")
                                        .repoURL("repoURL")
                                        .filePath("filePath")
                                        .build();
    EntityGitDetails entityGitDetails = EntityGitDetails.builder()
                                            .objectId("objectId")
                                            .branch("branch")
                                            .commitId("commitId")
                                            .filePath("filePath")
                                            .fileUrl("fileURL")
                                            .repoName("repoName")
                                            .repoIdentifier("repoId")
                                            .repoUrl("repoURL")
                                            .build();
    mockStatic(GitAwareContextHelper.class);
    when(GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata()).thenReturn(entityGitDetails);
    GitDetails gitDetails = inputSetsApiUtils.getGitDetails(inputSetEntity);
    assertThat(gitDetails).isNull();
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetResponseBody() {
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .yaml("yaml")
                                        .identifier(identifier)
                                        .name(name)
                                        .createdAt(123456L)
                                        .lastUpdatedAt(987654L)
                                        .build();
    InputSetResponseBody responseBody = inputSetsApiUtils.getInputSetResponse(inputSetEntity);
    assertEquals("yaml", responseBody.getInputSetYaml());
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals(name, responseBody.getName());
    assertEquals(123456L, responseBody.getCreated().longValue());
    assertEquals(987654L, responseBody.getUpdated().longValue());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputSetVersion() {
    String yaml = readFile("inputSetV1.yaml");
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CI_YAML_VERSIONING)).thenReturn(true);
    String inputSetVersion = inputSetsApiUtils.inputSetVersion(account, yaml);
    assertThat(inputSetVersion).isEqualTo(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputSetVersionFeatureDisabled() {
    String yaml = readFile("inputSetV1.yaml");
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CI_YAML_VERSIONING)).thenReturn(false);
    String inputSetVersion = inputSetsApiUtils.inputSetVersion(account, yaml);
    assertThat(inputSetVersion).isEqualTo(HarnessYamlVersion.V0);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputSetVersionOnV0Yaml() {
    String yaml = readFile("inputSet1.yml");
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CI_YAML_VERSIONING)).thenReturn(true);
    String inputSetVersion = inputSetsApiUtils.inputSetVersion(account, yaml);
    assertThat(inputSetVersion).isEqualTo(HarnessYamlVersion.V0);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetInputSetErrorWrapper() {
    InputSetErrorResponseDTOPMS inputSetErrorResponseDTOPMS = InputSetErrorResponseDTOPMS.builder().build();
    Map<String, InputSetErrorResponseDTOPMS> uuidToErrorResponseMap =
        Collections.singletonMap(UUID, inputSetErrorResponseDTOPMS);
    List<String> invalidInputSetReferences = Collections.singletonList(INVALID_INPUT_SET_REFERENCE);
    InputSetErrorWrapperDTOPMS inputSetErrorWrapperDTOPMS = InputSetErrorWrapperDTOPMS.builder()
                                                                .errorPipelineYaml(ERROR_PIPELINE_YAML)
                                                                .uuidToErrorResponseMap(uuidToErrorResponseMap)
                                                                .invalidInputSetReferences(invalidInputSetReferences)
                                                                .build();
    InputSetErrorWrapperDTO inputSetErrorWrapperDTO = new InputSetErrorWrapperDTO();
    inputSetErrorWrapperDTO.setErrorPipelineYaml(ERROR_PIPELINE_YAML);
    inputSetErrorWrapperDTO.setUuidToErrorResponseMap(uuidToErrorResponseMap);
    inputSetErrorWrapperDTO.setInvalidInputsetReferences(invalidInputSetReferences);

    InputSetErrorWrapperDTO result = inputSetsApiUtils.getInputSetErrorWrapper(inputSetErrorWrapperDTOPMS);
    assertThat(inputSetErrorWrapperDTO).isEqualTo(result);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetInputSetResponseWithError() {
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .yaml(ERROR_PIPELINE_YAML)
                                        .identifier(identifier)
                                        .name(name)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .description(DESCRIPTION)
                                        .build();
    InputSetErrorResponseDTOPMS inputSetErrorResponseDTOPMS = InputSetErrorResponseDTOPMS.builder().build();
    Map<String, InputSetErrorResponseDTOPMS> uuidToErrorResponseMap =
        Collections.singletonMap(UUID, inputSetErrorResponseDTOPMS);
    List<String> invalidInputSetReferences = Collections.emptyList();
    InputSetErrorWrapperDTOPMS inputSetErrorWrapperDTOPMS = InputSetErrorWrapperDTOPMS.builder()
                                                                .errorPipelineYaml(ERROR_PIPELINE_YAML)
                                                                .uuidToErrorResponseMap(uuidToErrorResponseMap)
                                                                .invalidInputSetReferences(invalidInputSetReferences)
                                                                .build();

    List<FQNtoError> fqNtoErrors = new ArrayList<>();
    FQNtoError fqNtoError = new FQNtoError();
    fqNtoError.fqn(UUID);
    fqNtoError.errors(Collections.emptyList());
    fqNtoErrors.add(fqNtoError);
    InputSetErrorDetails errorDetails = new InputSetErrorDetails();
    errorDetails.setValid(false);
    errorDetails.setOutdated(false);
    errorDetails.setMessage("Some fields in the Input Set are invalid.");
    errorDetails.setErrorPipelineYaml(ERROR_PIPELINE_YAML);
    errorDetails.setInvalidRefs(Collections.emptyList());
    errorDetails.setFqnErrors(fqNtoErrors);

    InputSetResponseBody inputSetResponseBody = getInputSetResponseWithError(inputSetEntity, errorDetails);

    InputSetResponseBody result =
        inputSetsApiUtils.getInputSetResponseWithError(inputSetEntity, inputSetErrorWrapperDTOPMS);
    assertThat(result).isEqualTo(inputSetResponseBody);
  }

  private InputSetResponseBody getInputSetResponseWithError(
      InputSetEntity inputSetEntity, InputSetErrorDetails errorDetails) {
    InputSetResponseBody inputSetResponseBody = new InputSetResponseBody();
    inputSetResponseBody.setInputSetYaml(inputSetEntity.getYaml());
    inputSetResponseBody.setIdentifier(inputSetEntity.getIdentifier());
    inputSetResponseBody.setName(inputSetEntity.getName());
    inputSetResponseBody.setOrg(inputSetEntity.getOrgIdentifier());
    inputSetResponseBody.setProject(inputSetEntity.getProjectIdentifier());
    inputSetResponseBody.setDescription(inputSetEntity.getDescription());
    inputSetResponseBody.setTags(Collections.emptyMap());
    inputSetResponseBody.setErrorDetails(errorDetails);
    inputSetResponseBody.setGitDetails(gitDetails);
    inputSetResponseBody.setCreated(0L);
    inputSetResponseBody.setUpdated(0L);
    return inputSetResponseBody;
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetFQNErrorsGetsOneInputSetError() {
    InputSetErrorDTOPMS inputSetErrorDTOPMS = InputSetErrorDTOPMS.builder()
                                                  .message("Testing GetFQNErrors method.")
                                                  .identifierOfErrorSource(identifier)
                                                  .fieldName("error_field")
                                                  .build();

    InputSetErrorResponseDTOPMS inputSetErrorResponseDTOPMS =
        InputSetErrorResponseDTOPMS.builder().errors(Collections.singletonList(inputSetErrorDTOPMS)).build();
    Map<String, InputSetErrorResponseDTOPMS> uuidToErrorResponseMap =
        Collections.singletonMap(UUID, inputSetErrorResponseDTOPMS);
    InputSetErrorWrapperDTOPMS inputSetErrorWrapperDTOPMS =
        InputSetErrorWrapperDTOPMS.builder().uuidToErrorResponseMap(uuidToErrorResponseMap).build();

    InputSetError inputSetError = new InputSetError();
    inputSetError.setMessage("Testing GetFQNErrors method.");
    inputSetError.setIdentifierOfErrorSource(identifier);
    inputSetError.setFieldName("error_field");

    List<FQNtoError> fqNtoErrors = new ArrayList<>();
    FQNtoError fqNtoError = new FQNtoError();
    fqNtoError.fqn(UUID);
    fqNtoError.errors(Collections.singletonList(inputSetError));
    fqNtoErrors.add(fqNtoError);

    List<FQNtoError> result = inputSetsApiUtils.getFQNErrors(inputSetErrorWrapperDTOPMS);
    assertThat(result).isEqualTo(fqNtoErrors);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetErrorsGetsOneInputSetError() {
    InputSetErrorDTOPMS inputSetErrorDTOPMS = InputSetErrorDTOPMS.builder()
                                                  .message("message")
                                                  .identifierOfErrorSource("identifierOfErrorSource")
                                                  .fieldName("fieldName")
                                                  .build();
    List<InputSetErrorDTOPMS> errorDTOPMS = Collections.singletonList(inputSetErrorDTOPMS);
    InputSetError inputSetError = new InputSetError();
    inputSetError.setMessage("message");
    inputSetError.setIdentifierOfErrorSource("identifierOfErrorSource");
    inputSetError.setFieldName("fieldName");
    List<InputSetError> errors = Collections.singletonList(inputSetError);
    List<InputSetError> result = inputSetsApiUtils.getErrors(errorDTOPMS);
    assertThat(result).isEqualTo(errors);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testPopulateGitCreateDetailsNullGitDetails() {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().build();
    GitEntityInfo result = InputSetsApiUtils.populateGitCreateDetails(null);
    assertThat(result.getBaseBranch()).isEqualTo(gitEntityInfo.getBaseBranch());
    assertThat(result.getBranch()).isEqualTo(gitEntityInfo.getBranch());
    assertThat(result.getCommitId()).isEqualTo(gitEntityInfo.getCommitId());
    assertThat(result.getConnectorRef()).isEqualTo(gitEntityInfo.getConnectorRef());
    assertThat(result.getFilePath()).isEqualTo(gitEntityInfo.getFilePath());
    assertThat(result.getRepoName()).isEqualTo(gitEntityInfo.getRepoName());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testPopulateGitCreateDetailsWithGitDetailsNonEmptyBaseBranch() {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .branch(BRANCH_NAME)
                                      .baseBranch(BASE_BRANCH)
                                      .commitMsg(COMMIT_MESSAGE)
                                      .filePath(FILE_PATH)
                                      .connectorRef(CONNECTOR_REF)
                                      .storeType(StoreType.INLINE)
                                      .repoName(REPO_NAME)
                                      .build();
    GitCreateDetails gitCreateDetails = new GitCreateDetails();
    gitCreateDetails.setBranchName(BRANCH_NAME);
    gitCreateDetails.setRepoName(REPO_NAME);
    gitCreateDetails.setFilePath(FILE_PATH);
    gitCreateDetails.setBaseBranch(BASE_BRANCH);
    gitCreateDetails.setCommitMessage(COMMIT_MESSAGE);
    gitCreateDetails.setStoreType(GitCreateDetails.StoreTypeEnum.INLINE);
    gitCreateDetails.setConnectorRef(CONNECTOR_REF);
    GitEntityInfo result = InputSetsApiUtils.populateGitCreateDetails(gitCreateDetails);
    assertThat(result.getBaseBranch()).isEqualTo(gitEntityInfo.getBaseBranch());
    assertThat(result.getBranch()).isEqualTo(gitEntityInfo.getBranch());
    assertThat(result.getCommitId()).isEqualTo(gitEntityInfo.getCommitId());
    assertThat(result.getConnectorRef()).isEqualTo(gitEntityInfo.getConnectorRef());
    assertThat(result.getFilePath()).isEqualTo(gitEntityInfo.getFilePath());
    assertThat(result.getRepoName()).isEqualTo(gitEntityInfo.getRepoName());
    assertThat(result.isNewBranch()).isTrue();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testPopulateGitCreateDetailsWithGitDetailsEmptyBaseBranch() {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .branch(BRANCH_NAME)
                                      .baseBranch("")
                                      .commitMsg(COMMIT_MESSAGE)
                                      .filePath(FILE_PATH)
                                      .connectorRef(CONNECTOR_REF)
                                      .storeType(StoreType.INLINE)
                                      .repoName(REPO_NAME)
                                      .build();
    GitCreateDetails gitCreateDetails = new GitCreateDetails();
    gitCreateDetails.setBranchName(BRANCH_NAME);
    gitCreateDetails.setRepoName(REPO_NAME);
    gitCreateDetails.setFilePath(FILE_PATH);
    gitCreateDetails.setBaseBranch("");
    gitCreateDetails.setCommitMessage(COMMIT_MESSAGE);
    gitCreateDetails.setStoreType(GitCreateDetails.StoreTypeEnum.INLINE);
    gitCreateDetails.setConnectorRef(CONNECTOR_REF);
    GitEntityInfo result = InputSetsApiUtils.populateGitCreateDetails(gitCreateDetails);
    assertThat(result.getBaseBranch()).isEqualTo(gitEntityInfo.getBaseBranch());
    assertThat(result.getBranch()).isEqualTo(gitEntityInfo.getBranch());
    assertThat(result.getCommitId()).isEqualTo(gitEntityInfo.getCommitId());
    assertThat(result.getConnectorRef()).isEqualTo(gitEntityInfo.getConnectorRef());
    assertThat(result.getFilePath()).isEqualTo(gitEntityInfo.getFilePath());
    assertThat(result.getRepoName()).isEqualTo(gitEntityInfo.getRepoName());
    assertThat(result.isNewBranch()).isFalse();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testPopulateGitCreateDetailsWithGitDetailsEmptyBranch() {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .branch("")
                                      .baseBranch(BASE_BRANCH)
                                      .commitMsg(COMMIT_MESSAGE)
                                      .filePath(FILE_PATH)
                                      .connectorRef(CONNECTOR_REF)
                                      .storeType(StoreType.INLINE)
                                      .repoName(REPO_NAME)
                                      .build();
    GitCreateDetails gitCreateDetails = new GitCreateDetails();
    gitCreateDetails.setBranchName("");
    gitCreateDetails.setRepoName(REPO_NAME);
    gitCreateDetails.setFilePath(FILE_PATH);
    gitCreateDetails.setBaseBranch(BASE_BRANCH);
    gitCreateDetails.setCommitMessage(COMMIT_MESSAGE);
    gitCreateDetails.setStoreType(GitCreateDetails.StoreTypeEnum.INLINE);
    gitCreateDetails.setConnectorRef(CONNECTOR_REF);
    GitEntityInfo result = InputSetsApiUtils.populateGitCreateDetails(gitCreateDetails);
    assertThat(result.getBaseBranch()).isEqualTo(gitEntityInfo.getBaseBranch());
    assertThat(result.getBranch()).isEqualTo(gitEntityInfo.getBranch());
    assertThat(result.getCommitId()).isEqualTo(gitEntityInfo.getCommitId());
    assertThat(result.getConnectorRef()).isEqualTo(gitEntityInfo.getConnectorRef());
    assertThat(result.getFilePath()).isEqualTo(gitEntityInfo.getFilePath());
    assertThat(result.getRepoName()).isEqualTo(gitEntityInfo.getRepoName());
    assertThat(result.isNewBranch()).isFalse();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testPopulateGitCreateDetailsWithGitDetailsEmptyBranchAndBaseBranch() {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .branch("")
                                      .baseBranch("")
                                      .commitMsg(COMMIT_MESSAGE)
                                      .filePath(FILE_PATH)
                                      .connectorRef(CONNECTOR_REF)
                                      .storeType(StoreType.INLINE)
                                      .repoName(REPO_NAME)
                                      .build();
    GitCreateDetails gitCreateDetails = new GitCreateDetails();
    gitCreateDetails.setBranchName("");
    gitCreateDetails.setRepoName(REPO_NAME);
    gitCreateDetails.setFilePath(FILE_PATH);
    gitCreateDetails.setBaseBranch("");
    gitCreateDetails.setCommitMessage(COMMIT_MESSAGE);
    gitCreateDetails.setStoreType(GitCreateDetails.StoreTypeEnum.INLINE);
    gitCreateDetails.setConnectorRef(CONNECTOR_REF);
    GitEntityInfo result = InputSetsApiUtils.populateGitCreateDetails(gitCreateDetails);
    assertThat(result.getBaseBranch()).isEqualTo(gitEntityInfo.getBaseBranch());
    assertThat(result.getBranch()).isEqualTo(gitEntityInfo.getBranch());
    assertThat(result.getCommitId()).isEqualTo(gitEntityInfo.getCommitId());
    assertThat(result.getConnectorRef()).isEqualTo(gitEntityInfo.getConnectorRef());
    assertThat(result.getFilePath()).isEqualTo(gitEntityInfo.getFilePath());
    assertThat(result.getRepoName()).isEqualTo(gitEntityInfo.getRepoName());
    assertThat(result.isNewBranch()).isFalse();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testPopulateGitUpdateDetailsNullGitDetails() {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().build();
    GitEntityInfo result = InputSetsApiUtils.populateGitUpdateDetails(null);
    assertThat(result.getBaseBranch()).isEqualTo(gitEntityInfo.getBaseBranch());
    assertThat(result.getBranch()).isEqualTo(gitEntityInfo.getBranch());
    assertThat(result.getCommitId()).isEqualTo(gitEntityInfo.getCommitId());
    assertThat(result.getConnectorRef()).isEqualTo(gitEntityInfo.getConnectorRef());
    assertThat(result.getFilePath()).isEqualTo(gitEntityInfo.getFilePath());
    assertThat(result.getRepoName()).isEqualTo(gitEntityInfo.getRepoName());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testPopulateGitUpdateDetailsWithGitDetailsNonEmptyBaseBranch() {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .lastObjectId(OBJECT_ID)
                                      .branch(BRANCH_NAME)
                                      .baseBranch(BASE_BRANCH)
                                      .commitMsg(COMMIT_MESSAGE)
                                      .connectorRef(CONNECTOR_REF)
                                      .storeType(StoreType.INLINE)
                                      .lastCommitId(COMMIT_ID)
                                      .repoName(REPO_NAME)
                                      .parentEntityConnectorRef(CONNECTOR_REF)
                                      .parentEntityRepoName(REPO_NAME)
                                      .build();
    InputSetGitUpdateDetails gitUpdateDetails = new InputSetGitUpdateDetails();
    gitUpdateDetails.setBranchName(BRANCH_NAME);
    gitUpdateDetails.setBaseBranch(BASE_BRANCH);
    gitUpdateDetails.setCommitMessage(COMMIT_MESSAGE);
    gitUpdateDetails.setLastCommitId(COMMIT_ID);
    gitUpdateDetails.setLastObjectId(OBJECT_ID);
    gitUpdateDetails.setParentEntityConnectorRef(CONNECTOR_REF);
    gitUpdateDetails.setParentEntityRepoName(REPO_NAME);
    GitEntityInfo result = InputSetsApiUtils.populateGitUpdateDetails(gitUpdateDetails);
    assertThat(result.getBaseBranch()).isEqualTo(gitEntityInfo.getBaseBranch());
    assertThat(result.getBranch()).isEqualTo(gitEntityInfo.getBranch());
    assertThat(result.getLastCommitId()).isEqualTo(gitEntityInfo.getLastCommitId());
    assertThat(result.getParentEntityConnectorRef()).isEqualTo(gitEntityInfo.getParentEntityConnectorRef());
    assertThat(result.getFilePath()).isEqualTo(gitEntityInfo.getFilePath());
    assertThat(result.getParentEntityRepoName()).isEqualTo(gitEntityInfo.getParentEntityRepoName());
    assertThat(result.isNewBranch()).isTrue();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testPopulateGitUpdateDetailsWithGitDetailsEmptyBaseBranch() {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .lastObjectId(OBJECT_ID)
                                      .branch(BRANCH_NAME)
                                      .baseBranch("")
                                      .commitMsg(COMMIT_MESSAGE)
                                      .connectorRef(CONNECTOR_REF)
                                      .storeType(StoreType.INLINE)
                                      .lastCommitId(COMMIT_ID)
                                      .repoName(REPO_NAME)
                                      .parentEntityConnectorRef(CONNECTOR_REF)
                                      .parentEntityRepoName(REPO_NAME)
                                      .build();
    InputSetGitUpdateDetails gitUpdateDetails = new InputSetGitUpdateDetails();
    gitUpdateDetails.setBranchName(BRANCH_NAME);
    gitUpdateDetails.setBaseBranch("");
    gitUpdateDetails.setCommitMessage(COMMIT_MESSAGE);
    gitUpdateDetails.setLastCommitId(COMMIT_ID);
    gitUpdateDetails.setLastObjectId(OBJECT_ID);
    gitUpdateDetails.setParentEntityConnectorRef(CONNECTOR_REF);
    gitUpdateDetails.setParentEntityRepoName(REPO_NAME);
    GitEntityInfo result = InputSetsApiUtils.populateGitUpdateDetails(gitUpdateDetails);
    assertThat(result.getBaseBranch()).isEqualTo(gitEntityInfo.getBaseBranch());
    assertThat(result.getBranch()).isEqualTo(gitEntityInfo.getBranch());
    assertThat(result.getLastCommitId()).isEqualTo(gitEntityInfo.getLastCommitId());
    assertThat(result.getParentEntityConnectorRef()).isEqualTo(gitEntityInfo.getParentEntityConnectorRef());
    assertThat(result.getFilePath()).isEqualTo(gitEntityInfo.getFilePath());
    assertThat(result.getParentEntityRepoName()).isEqualTo(gitEntityInfo.getParentEntityRepoName());
    assertThat(result.isNewBranch()).isFalse();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testPopulateGitUpdateDetailsWithGitDetailsEmptyBranch() {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .lastObjectId(OBJECT_ID)
                                      .branch("")
                                      .baseBranch(BASE_BRANCH)
                                      .commitMsg(COMMIT_MESSAGE)
                                      .connectorRef(CONNECTOR_REF)
                                      .storeType(StoreType.INLINE)
                                      .lastCommitId(COMMIT_ID)
                                      .repoName(REPO_NAME)
                                      .parentEntityConnectorRef(CONNECTOR_REF)
                                      .parentEntityRepoName(REPO_NAME)
                                      .build();
    InputSetGitUpdateDetails gitUpdateDetails = new InputSetGitUpdateDetails();
    gitUpdateDetails.setBranchName("");
    gitUpdateDetails.setBaseBranch(BASE_BRANCH);
    gitUpdateDetails.setCommitMessage(COMMIT_MESSAGE);
    gitUpdateDetails.setLastCommitId(COMMIT_ID);
    gitUpdateDetails.setLastObjectId(OBJECT_ID);
    gitUpdateDetails.setParentEntityConnectorRef(CONNECTOR_REF);
    gitUpdateDetails.setParentEntityRepoName(REPO_NAME);
    GitEntityInfo result = InputSetsApiUtils.populateGitUpdateDetails(gitUpdateDetails);
    assertThat(result.getBaseBranch()).isEqualTo(gitEntityInfo.getBaseBranch());
    assertThat(result.getBranch()).isEqualTo(gitEntityInfo.getBranch());
    assertThat(result.getLastCommitId()).isEqualTo(gitEntityInfo.getLastCommitId());
    assertThat(result.getParentEntityConnectorRef()).isEqualTo(gitEntityInfo.getParentEntityConnectorRef());
    assertThat(result.getFilePath()).isEqualTo(gitEntityInfo.getFilePath());
    assertThat(result.getParentEntityRepoName()).isEqualTo(gitEntityInfo.getParentEntityRepoName());
    assertThat(result.isNewBranch()).isFalse();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testPopulateGitUpdateDetailsWithGitDetailsEmptyBranchAndBaseBranch() {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .lastObjectId(OBJECT_ID)
                                      .branch("")
                                      .baseBranch("")
                                      .commitMsg(COMMIT_MESSAGE)
                                      .connectorRef(CONNECTOR_REF)
                                      .storeType(StoreType.INLINE)
                                      .lastCommitId(COMMIT_ID)
                                      .repoName(REPO_NAME)
                                      .parentEntityConnectorRef(CONNECTOR_REF)
                                      .parentEntityRepoName(REPO_NAME)
                                      .build();
    InputSetGitUpdateDetails gitUpdateDetails = new InputSetGitUpdateDetails();
    gitUpdateDetails.setBranchName("");
    gitUpdateDetails.setBaseBranch("");
    gitUpdateDetails.setCommitMessage(COMMIT_MESSAGE);
    gitUpdateDetails.setLastCommitId(COMMIT_ID);
    gitUpdateDetails.setLastObjectId(OBJECT_ID);
    gitUpdateDetails.setParentEntityConnectorRef(CONNECTOR_REF);
    gitUpdateDetails.setParentEntityRepoName(REPO_NAME);
    GitEntityInfo result = InputSetsApiUtils.populateGitUpdateDetails(gitUpdateDetails);
    assertThat(result.getBaseBranch()).isEqualTo(gitEntityInfo.getBaseBranch());
    assertThat(result.getBranch()).isEqualTo(gitEntityInfo.getBranch());
    assertThat(result.getLastCommitId()).isEqualTo(gitEntityInfo.getLastCommitId());
    assertThat(result.getParentEntityConnectorRef()).isEqualTo(gitEntityInfo.getParentEntityConnectorRef());
    assertThat(result.getFilePath()).isEqualTo(gitEntityInfo.getFilePath());
    assertThat(result.getParentEntityRepoName()).isEqualTo(gitEntityInfo.getParentEntityRepoName());
    assertThat(result.isNewBranch()).isFalse();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testMapCreateToRequestInfoDTONullRequestBody() {
    assertThatThrownBy(() -> InputSetsApiUtils.mapCreateToRequestInfoDTO(null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Create Request Body cannot be null.");
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testMapCreateToRequestInfoDTO() {
    InputSetCreateRequestBody requestBody = new InputSetCreateRequestBody();
    requestBody.setName("name");
    requestBody.setIdentifier(identifier);
    requestBody.setInputSetYaml(INPUT_SET_YAML);
    requestBody.setDescription(DESCRIPTION);
    requestBody.setTags(Collections.emptyMap());

    InputSetRequestInfoDTO requestInfoDTO = InputSetRequestInfoDTO.builder()
                                                .name("name")
                                                .tags(Collections.emptyMap())
                                                .description(DESCRIPTION)
                                                .identifier(identifier)
                                                .yaml(INPUT_SET_YAML)
                                                .build();

    InputSetRequestInfoDTO result = InputSetsApiUtils.mapCreateToRequestInfoDTO(requestBody);
    assertThat(result.getName()).isEqualTo(requestInfoDTO.getName());
    assertThat(result.getTags()).isEqualTo(requestInfoDTO.getTags());
    assertThat(result.getDescription()).isEqualTo(requestInfoDTO.getDescription());
    assertThat(result.getYaml()).isEqualTo(requestInfoDTO.getYaml());
    assertThat(result.getIdentifier()).isEqualTo(requestInfoDTO.getIdentifier());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testMapUpdateToRequestInfoDTONullRequestBody() {
    assertThatThrownBy(() -> InputSetsApiUtils.mapUpdateToRequestInfoDTO(null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Update Request Body cannot be null.");
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testMapUpdateToRequestInfoDTO() {
    InputSetUpdateRequestBody requestBody = new InputSetUpdateRequestBody();
    requestBody.setName("name");
    requestBody.setIdentifier(identifier);
    requestBody.setInputSetYaml(INPUT_SET_YAML);
    requestBody.setDescription(DESCRIPTION);
    requestBody.setTags(Collections.emptyMap());

    InputSetRequestInfoDTO requestInfoDTO = InputSetRequestInfoDTO.builder()
                                                .name("name")
                                                .tags(Collections.emptyMap())
                                                .description(DESCRIPTION)
                                                .identifier(identifier)
                                                .yaml(INPUT_SET_YAML)
                                                .build();

    InputSetRequestInfoDTO result = InputSetsApiUtils.mapUpdateToRequestInfoDTO(requestBody);
    assertThat(result.getName()).isEqualTo(requestInfoDTO.getName());
    assertThat(result.getTags()).isEqualTo(requestInfoDTO.getTags());
    assertThat(result.getDescription()).isEqualTo(requestInfoDTO.getDescription());
    assertThat(result.getYaml()).isEqualTo(requestInfoDTO.getYaml());
    assertThat(result.getIdentifier()).isEqualTo(requestInfoDTO.getIdentifier());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testBuildMoveConfigOperationDTO() {
    GitMoveDetails gitMoveDetails = new GitMoveDetails();
    gitMoveDetails.setRepoName(REPO_NAME);
    gitMoveDetails.setBranchName(BRANCH_NAME);
    gitMoveDetails.setConnectorRef(CONNECTOR_REF);
    gitMoveDetails.setBaseBranch(BASE_BRANCH);
    gitMoveDetails.setCommitMessage(COMMIT_MESSAGE);
    gitMoveDetails.setFilePath(FILE_PATH);
    MoveConfigOperationType moveConfigOperationType = MoveConfigOperationType.INLINE_TO_REMOTE;
    InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO =
        InputSetMoveConfigOperationDTO.builder()
            .repoName(gitMoveDetails.getRepoName())
            .branch(gitMoveDetails.getBranchName())
            .moveConfigOperationType(getMoveConfigType(moveConfigOperationType))
            .connectorRef(gitMoveDetails.getConnectorRef())
            .baseBranch(gitMoveDetails.getBaseBranch())
            .commitMessage(gitMoveDetails.getCommitMessage())
            .isNewBranch(true)
            .filePath(gitMoveDetails.getFilePath())
            .pipelineIdentifier(identifier)
            .build();
    InputSetMoveConfigOperationDTO result =
        InputSetsApiUtils.buildMoveConfigOperationDTO(gitMoveDetails, moveConfigOperationType, identifier);
    assertThat(result.getRepoName()).isEqualTo(inputSetMoveConfigOperationDTO.getRepoName());
    assertThat(result.getBranch()).isEqualTo(inputSetMoveConfigOperationDTO.getBranch());
    assertThat(result.getBaseBranch()).isEqualTo(inputSetMoveConfigOperationDTO.getBaseBranch());
    assertThat(result.getPipelineIdentifier()).isEqualTo(inputSetMoveConfigOperationDTO.getPipelineIdentifier());
    assertThat(result.getFilePath()).isEqualTo(inputSetMoveConfigOperationDTO.getFilePath());
    assertThat(result.getCommitMessage()).isEqualTo(inputSetMoveConfigOperationDTO.getCommitMessage());
    assertThat(result.getMoveConfigOperationType())
        .isEqualTo(inputSetMoveConfigOperationDTO.getMoveConfigOperationType());
    assertThat(result.isNewBranch()).isEqualTo(inputSetMoveConfigOperationDTO.isNewBranch()).isTrue();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testBuildMoveConfigOperationDTOEmptyBranch() {
    GitMoveDetails gitMoveDetails = new GitMoveDetails();
    gitMoveDetails.setRepoName(REPO_NAME);
    gitMoveDetails.setBranchName("");
    gitMoveDetails.setConnectorRef(CONNECTOR_REF);
    gitMoveDetails.setBaseBranch(BASE_BRANCH);
    gitMoveDetails.setCommitMessage(COMMIT_MESSAGE);
    gitMoveDetails.setFilePath(FILE_PATH);
    MoveConfigOperationType moveConfigOperationType = MoveConfigOperationType.INLINE_TO_REMOTE;
    InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO =
        InputSetMoveConfigOperationDTO.builder()
            .repoName(gitMoveDetails.getRepoName())
            .branch(gitMoveDetails.getBranchName())
            .moveConfigOperationType(getMoveConfigType(moveConfigOperationType))
            .connectorRef(gitMoveDetails.getConnectorRef())
            .baseBranch(gitMoveDetails.getBaseBranch())
            .commitMessage(gitMoveDetails.getCommitMessage())
            .isNewBranch(false)
            .filePath(gitMoveDetails.getFilePath())
            .pipelineIdentifier(identifier)
            .build();
    InputSetMoveConfigOperationDTO result =
        InputSetsApiUtils.buildMoveConfigOperationDTO(gitMoveDetails, moveConfigOperationType, identifier);
    assertThat(result.getRepoName()).isEqualTo(inputSetMoveConfigOperationDTO.getRepoName());
    assertThat(result.getBranch()).isEqualTo(inputSetMoveConfigOperationDTO.getBranch());
    assertThat(result.getBaseBranch()).isEqualTo(inputSetMoveConfigOperationDTO.getBaseBranch());
    assertThat(result.getPipelineIdentifier()).isEqualTo(inputSetMoveConfigOperationDTO.getPipelineIdentifier());
    assertThat(result.getFilePath()).isEqualTo(inputSetMoveConfigOperationDTO.getFilePath());
    assertThat(result.getCommitMessage()).isEqualTo(inputSetMoveConfigOperationDTO.getCommitMessage());
    assertThat(result.getMoveConfigOperationType())
        .isEqualTo(inputSetMoveConfigOperationDTO.getMoveConfigOperationType());
    assertThat(result.isNewBranch()).isEqualTo(inputSetMoveConfigOperationDTO.isNewBranch()).isFalse();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testBuildMoveConfigOperationDTOEmptyBaseBranch() {
    GitMoveDetails gitMoveDetails = new GitMoveDetails();
    gitMoveDetails.setRepoName(REPO_NAME);
    gitMoveDetails.setBranchName(BRANCH_NAME);
    gitMoveDetails.setConnectorRef(CONNECTOR_REF);
    gitMoveDetails.setBaseBranch("");
    gitMoveDetails.setCommitMessage(COMMIT_MESSAGE);
    gitMoveDetails.setFilePath(FILE_PATH);
    MoveConfigOperationType moveConfigOperationType = MoveConfigOperationType.INLINE_TO_REMOTE;
    InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO =
        InputSetMoveConfigOperationDTO.builder()
            .repoName(gitMoveDetails.getRepoName())
            .branch(gitMoveDetails.getBranchName())
            .moveConfigOperationType(getMoveConfigType(moveConfigOperationType))
            .connectorRef(gitMoveDetails.getConnectorRef())
            .baseBranch(gitMoveDetails.getBaseBranch())
            .commitMessage(gitMoveDetails.getCommitMessage())
            .isNewBranch(false)
            .filePath(gitMoveDetails.getFilePath())
            .pipelineIdentifier(identifier)
            .build();
    InputSetMoveConfigOperationDTO result =
        InputSetsApiUtils.buildMoveConfigOperationDTO(gitMoveDetails, moveConfigOperationType, identifier);
    assertThat(result.getRepoName()).isEqualTo(inputSetMoveConfigOperationDTO.getRepoName());
    assertThat(result.getBranch()).isEqualTo(inputSetMoveConfigOperationDTO.getBranch());
    assertThat(result.getBaseBranch()).isEqualTo(inputSetMoveConfigOperationDTO.getBaseBranch());
    assertThat(result.getPipelineIdentifier()).isEqualTo(inputSetMoveConfigOperationDTO.getPipelineIdentifier());
    assertThat(result.getFilePath()).isEqualTo(inputSetMoveConfigOperationDTO.getFilePath());
    assertThat(result.getCommitMessage()).isEqualTo(inputSetMoveConfigOperationDTO.getCommitMessage());
    assertThat(result.getMoveConfigOperationType())
        .isEqualTo(inputSetMoveConfigOperationDTO.getMoveConfigOperationType());
    assertThat(result.isNewBranch()).isEqualTo(inputSetMoveConfigOperationDTO.isNewBranch()).isFalse();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testBuildMoveConfigOperationDTOEmptyBranchAndBaseBranch() {
    GitMoveDetails gitMoveDetails = new GitMoveDetails();
    gitMoveDetails.setRepoName(REPO_NAME);
    gitMoveDetails.setBranchName("");
    gitMoveDetails.setConnectorRef(CONNECTOR_REF);
    gitMoveDetails.setBaseBranch("");
    gitMoveDetails.setCommitMessage(COMMIT_MESSAGE);
    gitMoveDetails.setFilePath(FILE_PATH);
    MoveConfigOperationType moveConfigOperationType = MoveConfigOperationType.INLINE_TO_REMOTE;
    InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO =
        InputSetMoveConfigOperationDTO.builder()
            .repoName(gitMoveDetails.getRepoName())
            .branch(gitMoveDetails.getBranchName())
            .moveConfigOperationType(getMoveConfigType(moveConfigOperationType))
            .connectorRef(gitMoveDetails.getConnectorRef())
            .baseBranch(gitMoveDetails.getBaseBranch())
            .commitMessage(gitMoveDetails.getCommitMessage())
            .isNewBranch(false)
            .filePath(gitMoveDetails.getFilePath())
            .pipelineIdentifier(identifier)
            .build();
    InputSetMoveConfigOperationDTO result =
        InputSetsApiUtils.buildMoveConfigOperationDTO(gitMoveDetails, moveConfigOperationType, identifier);
    assertThat(result.getRepoName()).isEqualTo(inputSetMoveConfigOperationDTO.getRepoName());
    assertThat(result.getBranch()).isEqualTo(inputSetMoveConfigOperationDTO.getBranch());
    assertThat(result.getBaseBranch()).isEqualTo(inputSetMoveConfigOperationDTO.getBaseBranch());
    assertThat(result.getPipelineIdentifier()).isEqualTo(inputSetMoveConfigOperationDTO.getPipelineIdentifier());
    assertThat(result.getFilePath()).isEqualTo(inputSetMoveConfigOperationDTO.getFilePath());
    assertThat(result.getCommitMessage()).isEqualTo(inputSetMoveConfigOperationDTO.getCommitMessage());
    assertThat(result.getMoveConfigOperationType())
        .isEqualTo(inputSetMoveConfigOperationDTO.getMoveConfigOperationType());
    assertThat(result.isNewBranch()).isEqualTo(inputSetMoveConfigOperationDTO.isNewBranch()).isFalse();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testCheckAndSetContextIfGetOnlyFileContentAndFFEnabled() {
    mockStatic(ThreadOperationContextHelper.class);
    boolean getOnlyFileContent = true;
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(account, FeatureName.PIE_GET_FILE_CONTENT_ONLY);
    inputSetsApiUtils.checkAndSetContextIfGetOnlyFileContentEnabled(account, getOnlyFileContent);
    verifyStatic(ThreadOperationContextHelper.class, times(1));
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testCheckAndSetContextIfGetOnlyFileContentDisabledAndFFEnabled() {
    mockStatic(ThreadOperationContextHelper.class);
    boolean getOnlyFileContent = false;
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(account, FeatureName.PIE_GET_FILE_CONTENT_ONLY);
    inputSetsApiUtils.checkAndSetContextIfGetOnlyFileContentEnabled(account, getOnlyFileContent);
    verifyStatic(ThreadOperationContextHelper.class, never());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testCheckAndSetContextIfGetOnlyFileContentEnabledAndFFDisabled() {
    mockStatic(ThreadOperationContextHelper.class);
    boolean getOnlyFileContent = true;
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(account, FeatureName.PIE_GET_FILE_CONTENT_ONLY);
    inputSetsApiUtils.checkAndSetContextIfGetOnlyFileContentEnabled(account, getOnlyFileContent);
    verifyStatic(ThreadOperationContextHelper.class, never());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testCheckAndSetContextIfGetOnlyFileContentDisabledAndFFDisabled() {
    mockStatic(ThreadOperationContextHelper.class);
    boolean getOnlyFileContent = false;
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(account, FeatureName.PIE_GET_FILE_CONTENT_ONLY);
    inputSetsApiUtils.checkAndSetContextIfGetOnlyFileContentEnabled(account, getOnlyFileContent);
    verifyStatic(ThreadOperationContextHelper.class, never());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testIsDifferentRepoForPipelineAndInputSetsAccountSettingEnabled() {
    mockStatic(NGRestUtils.class);
    String expectedSettingValue = GitSyncConstants.TRUE_VALUE;
    Call<ResponseDTO<SettingValueResponseDTO>> setting = ngSettingsClient.getSetting(
        GitSyncConstants.ALLOW_DIFFERENT_REPO_FOR_PIPELINE_AND_INPUT_SETS, account, null, null);
    SettingValueResponseDTO response = SettingValueResponseDTO.builder().value(expectedSettingValue).build();
    when(ngSettingsClient.getSetting(
             GitSyncConstants.ALLOW_DIFFERENT_REPO_FOR_PIPELINE_AND_INPUT_SETS, account, null, null))
        .thenReturn(setting);
    when(NGRestUtils.getResponse(setting)).thenReturn(response);
    boolean result = inputSetsApiUtils.isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(account);
    assertThat(result).isTrue();
    verify(ngSettingsClient, times(2))
        .getSetting(GitSyncConstants.ALLOW_DIFFERENT_REPO_FOR_PIPELINE_AND_INPUT_SETS, account, null, null);
  }
}
