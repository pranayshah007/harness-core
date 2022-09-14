package io.harness.template.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.template.entity.TemplateEntity;

@OwnedBy(HarnessTeam.PL)
public interface TemplateGitXService {
  String getWorkingBranch(String entityRepoURL);

  boolean isNewGitXEnabled(TemplateEntity templateToSave, GitEntityInfo gitEntityInfo);
}
