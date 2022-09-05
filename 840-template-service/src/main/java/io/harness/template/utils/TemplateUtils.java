/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.utils;

import io.harness.beans.Scope;
import io.harness.exception.ScmException;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.template.entity.TemplateEntity;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TemplateUtils {
  public Scope buildScope(TemplateEntity templateEntity) {
    return Scope.of(templateEntity.getAccountIdentifier(), templateEntity.getOrgIdentifier(),
        templateEntity.getProjectIdentifier());
  }

  public boolean isInlineEntity(GitEntityInfo gitEntityInfo) {
    return StoreType.INLINE.equals(gitEntityInfo.getStoreType()) || gitEntityInfo.getStoreType() == null;
  }

  public boolean isRemoteEntity(GitEntityInfo gitEntityInfo) {
    return StoreType.REMOTE.equals(gitEntityInfo.getStoreType());
  }

  public ScmException getScmException(Throwable ex) {
    while (ex != null) {
      if (ex instanceof ScmException) {
        return (ScmException) ex;
      }
      ex = ex.getCause();
    }
    return null;
  }
}
