package io.harness.template.utils;

import io.harness.beans.Scope;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.template.entity.TemplateEntity;

public class TemplateUtils {
  public static Scope buildScope(TemplateEntity templateEntity) {
    return Scope.of(templateEntity.getAccountIdentifier(), templateEntity.getOrgIdentifier(),
        templateEntity.getProjectIdentifier());
  }

  public static boolean isOldGitSync(GitSyncSdkService gitSyncSdkService, TemplateEntity templateEntity) {
    return gitSyncSdkService.isGitSyncEnabled(
        templateEntity.getAccountId(), templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier());
  }

  public static boolean isInlineEntity(GitEntityInfo gitEntityInfo) {
    return StoreType.INLINE.equals(gitEntityInfo.getStoreType());
  }

  public static boolean isRemoteEntity(GitEntityInfo gitEntityInfo) {
    return StoreType.REMOTE.equals(gitEntityInfo.getStoreType());
  }
}
