package io.harness.gitx;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.manage.GlobalContextManager;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.rule.Owner;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@OwnedBy(PIPELINE)
public class GitXSettingsHelperTest extends CategoryTest {
  @Mock private NGSettingsClient ngSettingsClient;
  @InjectMocks GitXSettingsHelper gitXSettingsHelper;

  private final String ACCOUNT_IDENTIFIER = "accountId";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testEnforceGitExperienceIfApplicable() {
    GitEntityInfo branchInfo = GitEntityInfo.builder().storeType(StoreType.REMOTE).build();
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
    gitXSettingsHelper.enforceGitExperienceIfApplicable(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER);

    verify(gitXSettingsHelper, times(0)).isGitExperienceEnforcedInSettings(any(), any(), any());
  }
}
