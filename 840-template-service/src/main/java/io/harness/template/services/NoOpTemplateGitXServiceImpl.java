/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.template.entity.TemplateEntity;

public class NoOpTemplateGitXServiceImpl implements TemplateGitXService {
  @Override
  public String getWorkingBranch(String entityRepoURL) {
    return "";
  }

  @Override
  public boolean isNewGitXEnabled(TemplateEntity templateToSave, GitEntityInfo gitEntityInfo) {
    return false;
  }
}
