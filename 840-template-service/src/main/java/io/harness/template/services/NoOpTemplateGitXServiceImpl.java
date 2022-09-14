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
