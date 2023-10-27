/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitx;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import retrofit2.Call;

@OwnedBy(PIPELINE)
public class GitXSettingsHelperTest extends CategoryTest {
  @Mock private NGSettingsClient ngSettingsClient;
  @Spy @InjectMocks GitXSettingsHelper gitXSettingsHelper;

  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;

  private final String ACCOUNT_IDENTIFIER = "accountId";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String CONNECTOR_REF = "connectorRef";
  private final String REPO = "repo";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testEnforceGitExperienceIfApplicableWithInlineStoreType() {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().storeType(StoreType.INLINE).build();
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(gitEntityInfo).build());
    doReturn(true).when(gitXSettingsHelper).isGitExperienceEnforcedInSettings(any(), any(), any());

    assertThatThrownBy(
        () -> gitXSettingsHelper.enforceGitExperienceIfApplicable(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Git Experience is enforced for the current scope with accountId: [accountId], orgIdentifier: [orgId] and projIdentifier: [projId]. Hence Interaction with INLINE entities is forbidden.");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testEnforceGitExperienceIfApplicableWithRemoteEntities() {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().storeType(StoreType.REMOTE).build();
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(gitEntityInfo).build());

    assertThatCode(
        () -> gitXSettingsHelper.enforceGitExperienceIfApplicable(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDefaultStoreTypeWithGitExperienceEnforced() {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().build();
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(gitEntityInfo).build());
    doReturn(true).when(gitXSettingsHelper).isGitExperienceEnforcedInSettings(any(), any(), any());

    gitXSettingsHelper.setDefaultStoreTypeForEntities(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER, EntityType.PIPELINES);

    GitEntityInfo gitEntityInfoProcessed = GitAwareContextHelper.getGitRequestParamsInfo();
    assertThat(gitEntityInfoProcessed.getStoreType()).isEqualTo(StoreType.REMOTE);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDefaultStoreType() {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().build();
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(gitEntityInfo).build());
    doReturn(false).when(gitXSettingsHelper).isGitExperienceEnforcedInSettings(any(), any(), any());

    SettingValueResponseDTO settingValueResponseDTO = SettingValueResponseDTO.builder().value("INLINE").build();
    MockedStatic<NGRestUtils> ngRestUtilsMockedStatic = mockStatic(NGRestUtils.class);
    ngRestUtilsMockedStatic.when(() -> NGRestUtils.getResponse(any()))
        .thenAnswer(invocationOnMock -> settingValueResponseDTO);

    gitXSettingsHelper.setDefaultStoreTypeForEntities(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER, EntityType.INPUT_SETS);

    GitEntityInfo gitEntityInfoProcessed = GitAwareContextHelper.getGitRequestParamsInfo();
    assertThat(gitEntityInfoProcessed.getStoreType()).isEqualTo(StoreType.INLINE);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDefaultStoreTypeForInputSets() {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().build();
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(gitEntityInfo).build());

    SettingValueResponseDTO settingValueResponseDTO = SettingValueResponseDTO.builder().value("INLINE").build();
    MockedStatic<NGRestUtils> ngRestUtilsMockedStatic = mockStatic(NGRestUtils.class);
    ngRestUtilsMockedStatic.when(() -> NGRestUtils.getResponse(any()))
        .thenAnswer(invocationOnMock -> settingValueResponseDTO);

    gitXSettingsHelper.setDefaultStoreTypeForEntities(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER, EntityType.TEMPLATE);

    GitEntityInfo gitEntityInfoProcessed = GitAwareContextHelper.getGitRequestParamsInfo();
    assertThat(gitEntityInfoProcessed.getStoreType()).isEqualTo(StoreType.INLINE);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetGitRepoAllowlist() {
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("org1/repo1  ,  org2/repo2,org3/  repo3").build();
    MockedStatic<NGRestUtils> ngRestUtilsMockedStatic = mockStatic(NGRestUtils.class);
    ngRestUtilsMockedStatic.when(() -> NGRestUtils.getResponse(any()))
        .thenAnswer(invocationOnMock -> settingValueResponseDTO);

    List<String> listOfRepos =
        gitXSettingsHelper.getGitRepoAllowlist(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER);

    assertThat(listOfRepos.get(0)).isEqualTo("org1/repo1");
    assertThat(listOfRepos.get(1)).isEqualTo("org2/repo2");
    assertThat(listOfRepos.get(2)).isEqualTo("org3/  repo3");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testEnforceGitExperienceIfApplicableWhenGitEntityIsNotPresent() {
    GitEntityInfo branchInfo = GitEntityInfo.builder().build();
    setupGitContext(branchInfo);
    gitXSettingsHelper.setConnectorRefForRemoteEntity(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    verify(ngSettingsClient, times(0)).getSetting(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testEnforceGitExperienceIfApplicableWhenGitEntityIsPresent() {
    GitEntityInfo branchInfo = GitEntityInfo.builder().storeType(StoreType.REMOTE).build();
    setupGitContext(branchInfo);
    doReturn(CONNECTOR_REF).when(gitXSettingsHelper).getDefaultConnectorForGitX(any(), any(), any());
    doReturn(request).when(ngSettingsClient).getSetting(any(), any(), any(), any());
    gitXSettingsHelper.setConnectorRefForRemoteEntity(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    assertEquals(GitAwareContextHelper.getGitRequestParamsInfo().getConnectorRef(), CONNECTOR_REF);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSetDefaultRepoForRemoteEntityWhenGitEntityIsNotPresent() {
    gitXSettingsHelper.setDefaultRepoForRemoteEntity(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    verify(ngSettingsClient, times(0)).getSetting(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSetDefaultRepoForRemoteEntityWhenGitEntityIsPresent() {
    GitEntityInfo branchInfo = GitEntityInfo.builder().storeType(StoreType.REMOTE).build();
    setupGitContext(branchInfo);
    doReturn(REPO).when(gitXSettingsHelper).getDefaultRepoForGitX(any(), any(), any());
    gitXSettingsHelper.setDefaultRepoForRemoteEntity(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    assertEquals(GitAwareContextHelper.getGitRequestParamsInfo().getRepoName(), REPO);
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }
}
