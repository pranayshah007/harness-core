/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.gitsync.common.remote.YamlGitConfigMapper.toSetupGitSyncDTO;
import static io.harness.gitsync.common.remote.YamlGitConfigMapper.toYamlGitConfigDTO;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.SATYAM_GOEL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.eraro.ErrorCode;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.exception.DuplicateEntityException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.dtos.GitSyncConfigDTO;
import io.harness.gitsync.common.dtos.GitSyncFolderConfigDTO;
import io.harness.gitsync.common.events.GitSyncConfigChangeEventConstants;
import io.harness.gitsync.common.events.GitSyncConfigChangeEventType;
import io.harness.gitsync.common.events.GitSyncConfigSwitchType;
import io.harness.gitsync.common.helper.UserProfileHelper;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.rule.Owner;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(DX)
public class YamlGitConfigServiceImplTest extends GitSyncTestBase {
  @Mock ConnectorService defaultConnectorService;
  @Inject YamlGitConfigServiceImpl yamlGitConfigService;
  @Mock Producer gitSyncConfigEventProducer;
  @Mock UserProfileHelper userProfileHelper;
  @Mock ScmFacilitatorService scmFacilitatorService;
  @Mock IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;
  @Mock IdentifierRefHelper identifierRefHelper;
  @Mock Producer setupUsageEventProducer;
  private IdentifierRefProtoDTO yamlGitConfigReference;
  private final String ACCOUNT_ID = "ACCOUNT_ID";
  private final String ACCOUNT_ID_1 = "ACCOUNT_ID_1";
  private final String ORG_ID = "ORG_ID";
  private final String PROJECT_ID = "PROJECT_ID";
  private final String IDENTIFIER = "ID";
  private final String IDENTIFIER_1 = "ID_1";
  private final String CONNECTOR_ID = "CONNECTOR_ID";
  private final String CONNECTOR_ID_1 = "CONNECTOR_ID_1";
  private final String REPO = "REPO";
  private final String BRANCH = "BRANCH";
  private final String ROOT_FOLDER = "ROOT_FOLDER/.harness/";
  private final String ROOT_FOLDER_ID = "ROOT_FOLDER_ID";
  private final String ROOT_FOLDER_1 = "ROOT_FOLDER_1";
  private final String ROOT_FOLDER_ID_1 = "ROOT_FOLDER_ID_1/.harness/";
  private final String ERROR = "error";
  private final String GIT_CONNECTOR_REF = "gitConnectorRef";

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    GithubConnectorDTO githubConnector =
        GithubConnectorDTO.builder().apiAccess(GithubApiAccessDTO.builder().build()).build();
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder().connectorConfig(githubConnector).build();
    when(defaultConnectorService.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(ConnectorResponseDTO.builder().connector(connectorInfo).build()));
    when(userProfileHelper.validateIfScmUserProfileIsSet(ACCOUNT_ID)).thenReturn(true);
    when(defaultConnectorService.testGitRepoConnection(any(), any(), any(), any(), any()))
        .thenReturn(ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build());
    when(scmFacilitatorService.listBranchesUsingConnector(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Arrays.asList(BRANCH));
    FieldUtils.writeField(yamlGitConfigService, "connectorService", defaultConnectorService, true);
    FieldUtils.writeField(yamlGitConfigService, "gitSyncConfigEventProducer", gitSyncConfigEventProducer, true);
    FieldUtils.writeField(yamlGitConfigService, "userProfileHelper", userProfileHelper, true);
    FieldUtils.writeField(yamlGitConfigService, "scmFacilitatorService", scmFacilitatorService, true);
    FieldUtils.writeField(yamlGitConfigService, "setupUsageEventProducer", setupUsageEventProducer, true);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void test_save() {
    GitSyncFolderConfigDTO rootFolder =
        GitSyncFolderConfigDTO.builder().isDefault(true).rootFolder(ROOT_FOLDER).build();
    GitSyncConfigDTO gitSyncConfigDTO =
        buildGitSyncDTO(Collections.singletonList(rootFolder), CONNECTOR_ID, REPO, BRANCH, IDENTIFIER);
    GitSyncConfigDTO ret =
        toSetupGitSyncDTO(yamlGitConfigService.save(toYamlGitConfigDTO(gitSyncConfigDTO, ACCOUNT_ID)));
    assertThat(ret).isEqualTo(gitSyncConfigDTO);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void test_gitSyncEnabledEventIsSent() throws Exception {
    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);

    GitSyncFolderConfigDTO rootFolder =
        GitSyncFolderConfigDTO.builder().isDefault(true).rootFolder(ROOT_FOLDER).build();
    GitSyncConfigDTO gitSyncConfigDTO =
        buildGitSyncDTO(Collections.singletonList(rootFolder), CONNECTOR_ID, REPO, BRANCH, IDENTIFIER);
    GitSyncConfigDTO ret =
        toSetupGitSyncDTO(yamlGitConfigService.save(toYamlGitConfigDTO(gitSyncConfigDTO, ACCOUNT_ID)));
    verify(gitSyncConfigEventProducer, times(1)).send(messageArgumentCaptor.capture());

    Message message = messageArgumentCaptor.getValue();
    final Map<String, String> metadataMap = message.getMetadataMap();
    assertThat(metadataMap.get("accountId")).isEqualTo(ACCOUNT_ID);
    assertThat(metadataMap.get(GitSyncConfigChangeEventConstants.EVENT_TYPE))
        .isEqualTo(GitSyncConfigChangeEventType.SAVE_EVENT.name());
    assertThat(metadataMap.get(GitSyncConfigChangeEventConstants.CONFIG_SWITCH_TYPE))
        .isEqualTo(GitSyncConfigSwitchType.ENABLED.name());
    final ByteString data = message.getData();
    final EntityScopeInfo entityScopeInfo = EntityScopeInfo.parseFrom(data);
    assertThat(entityScopeInfo.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(entityScopeInfo.getOrgId().getValue()).isEqualTo(ORG_ID);
    assertThat(entityScopeInfo.getProjectId().getValue()).isEqualTo(PROJECT_ID);
    assertThat(ret).isEqualTo(gitSyncConfigDTO);
  }

  private GitSyncConfigDTO buildGitSyncDTO(
      List<GitSyncFolderConfigDTO> rootFolder, String connectorId, String repo, String branch, String identifier) {
    return GitSyncConfigDTO.builder()
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .repo(repo)
        .branch(branch)
        .gitConnectorRef(connectorId)
        .identifier(identifier)
        .gitSyncFolderConfigDTOs(rootFolder)
        .name(repo)
        .build();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void testConnectorUpdate() {
    GitSyncFolderConfigDTO rootFolder =
        GitSyncFolderConfigDTO.builder().isDefault(true).rootFolder(ROOT_FOLDER).build();
    GitSyncConfigDTO gitSyncConfigDTO =
        saveYamlGitConfig(Collections.singletonList(rootFolder), CONNECTOR_ID, REPO, BRANCH, IDENTIFIER);
    gitSyncConfigDTO.setGitConnectorRef(CONNECTOR_ID_1);
    GitSyncConfigDTO ret =
        toSetupGitSyncDTO(yamlGitConfigService.update(toYamlGitConfigDTO(gitSyncConfigDTO, ACCOUNT_ID)));
    assertThat(ret.getGitConnectorRef()).isEqualTo(CONNECTOR_ID_1);
  }

  private GitSyncFolderConfigDTO getDefault(GitSyncConfigDTO gitSyncConfigDTOS) {
    List<GitSyncFolderConfigDTO> gitSyncFolderConfigDTOs = gitSyncConfigDTOS.getGitSyncFolderConfigDTOs();
    Optional<GitSyncFolderConfigDTO> defaultGitSync =
        gitSyncFolderConfigDTOs.stream().filter(GitSyncFolderConfigDTO::getIsDefault).findFirst();
    return defaultGitSync.orElse(null);
  }

  private GitSyncConfigDTO saveYamlGitConfig(
      List<GitSyncFolderConfigDTO> rootFolder, String connectorId, String repo, String branch, String identifier) {
    GitSyncConfigDTO gitSyncConfigDTO = buildGitSyncDTO(rootFolder, connectorId, repo, branch, identifier);
    return toSetupGitSyncDTO(yamlGitConfigService.save(toYamlGitConfigDTO(gitSyncConfigDTO, ACCOUNT_ID)));
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void testValidateThatHarnessStringComesOnceWithValidInput() {
    List<YamlGitConfigDTO.RootFolder> rootFolders = Arrays.asList(getRootFolder(ROOT_FOLDER),
        getRootFolder(ROOT_FOLDER_1), getRootFolder("config/code/config-harness/config-.harness/.harness/"),
        getRootFolder("config-harness/.harness/"), getRootFolder("/.harness/"),
        getRootFolder("config////abc///.harness/"), getRootFolder("harness/.harness/"));
    YamlGitConfigDTO yamlGitConfigDTO = YamlGitConfigDTO.builder().rootFolders(rootFolders).build();
    yamlGitConfigService.validateThatHarnessStringShouldNotComeMoreThanOnce(yamlGitConfigDTO);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void testValidateThatHarnessStringComesOnceWithInvalidInput() {
    List<YamlGitConfigDTO.RootFolder> rootFolders = Arrays.asList(getRootFolder("/src/.harness/src1/.harness"),
        getRootFolder("harness-config/.harness/xyz-.harness/.harness"), getRootFolder(".harness/.harness"),
        getRootFolder("/.harness/harness/.harness"));

    rootFolders.forEach(rootFolder -> {
      YamlGitConfigDTO yamlGitConfigDTO =
          YamlGitConfigDTO.builder().rootFolders(Collections.singletonList(rootFolder)).build();
      assertThatThrownBy(
          () -> yamlGitConfigService.validateThatHarnessStringShouldNotComeMoreThanOnce(yamlGitConfigDTO))
          .isInstanceOf(InvalidRequestException.class);
    });
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void test_DuplicateSave() {
    GitSyncFolderConfigDTO rootFolder =
        GitSyncFolderConfigDTO.builder().isDefault(true).rootFolder(ROOT_FOLDER).build();
    GitSyncConfigDTO gitSyncConfigDTO =
        buildGitSyncDTO(Collections.singletonList(rootFolder), CONNECTOR_ID, REPO, BRANCH, IDENTIFIER);
    yamlGitConfigService.save(toYamlGitConfigDTO(gitSyncConfigDTO, ACCOUNT_ID));
    try {
      yamlGitConfigService.save(toYamlGitConfigDTO(gitSyncConfigDTO, ACCOUNT_ID));
    } catch (DuplicateEntityException ex) {
      assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS);
    }
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATYAM_GOEL)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void test_checkIfBranchExists_shouldThrowExceptionIfBranchNotEists() {
    GitSyncFolderConfigDTO rootFolder =
        GitSyncFolderConfigDTO.builder().isDefault(true).rootFolder(ROOT_FOLDER).build();
    GitSyncConfigDTO gitSyncConfigDTO =
        buildGitSyncDTO(Collections.singletonList(rootFolder), CONNECTOR_ID, REPO, BRANCH, IDENTIFIER);
    when(scmFacilitatorService.listBranchesUsingConnector(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Arrays.asList("branch1"));
    toSetupGitSyncDTO(yamlGitConfigService.save(toYamlGitConfigDTO(gitSyncConfigDTO, ACCOUNT_ID)));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATYAM_GOEL)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void test_checkIfBranchExists_shouldThrowExceptionIfRepoUrlIsIncorrect() {
    GitSyncFolderConfigDTO rootFolder =
        GitSyncFolderConfigDTO.builder().isDefault(true).rootFolder(ROOT_FOLDER).build();
    GitSyncConfigDTO gitSyncConfigDTO =
        buildGitSyncDTO(Collections.singletonList(rootFolder), CONNECTOR_ID, REPO, BRANCH, IDENTIFIER);
    when(scmFacilitatorService.listBranchesUsingConnector(any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new InvalidRequestException("repo doesn't exists"));
    toSetupGitSyncDTO(yamlGitConfigService.save(toYamlGitConfigDTO(gitSyncConfigDTO, ACCOUNT_ID)));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void testDeleteAllEntities() {
    GitSyncFolderConfigDTO rootFolder =
        GitSyncFolderConfigDTO.builder().isDefault(true).rootFolder(ROOT_FOLDER).build();
    GitSyncConfigDTO gitSyncConfigDTO = GitSyncConfigDTO.builder()
                                            .branch(BRANCH)
                                            .orgIdentifier(ORG_ID)
                                            .projectIdentifier(PROJECT_ID)
                                            .repo(REPO)
                                            .identifier(IDENTIFIER)
                                            .gitSyncFolderConfigDTOs(Collections.singletonList(rootFolder))
                                            .gitConnectorRef(CONNECTOR_ID)
                                            .name(REPO)
                                            .build();
    yamlGitConfigService.save(toYamlGitConfigDTO(gitSyncConfigDTO, ACCOUNT_ID_1));

    when(identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(any(), any(), any(), any()))
        .thenReturn(yamlGitConfigReference);
    yamlGitConfigService.deleteAllEntities(ACCOUNT_ID_1, ORG_ID, PROJECT_ID);
    assertThat(yamlGitConfigService.list(PROJECT_ID, ORG_ID, ACCOUNT_ID_1)).hasSize(0);
  }

  private YamlGitConfigDTO.RootFolder getRootFolder(String folderPath) {
    return YamlGitConfigDTO.RootFolder.builder().rootFolder(folderPath).build();
  }
}
