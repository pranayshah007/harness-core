/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitx;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.manage.GlobalContextManager;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(PIPELINE)
public class GitXSettingsHelperTest extends CategoryTest {
  @Mock private NGSettingsClient ngSettingsClient;
  @Spy @InjectMocks GitXSettingsHelper gitXSettingsHelper;

  private final String ACCOUNT_IDENTIFIER = "accountId";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";

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

    gitXSettingsHelper.setDefaultStoreTypeForEntities(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER);

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

    gitXSettingsHelper.setDefaultStoreTypeForEntities(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER);

    GitEntityInfo gitEntityInfoProcessed = GitAwareContextHelper.getGitRequestParamsInfo();
    assertThat(gitEntityInfoProcessed.getStoreType()).isEqualTo(StoreType.INLINE);
  }
}
