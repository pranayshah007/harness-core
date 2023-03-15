/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.SettingAttribute;
import software.wings.service.intfc.SettingsService;
import software.wings.sm.ExecutionContext;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class GitFileConfigHelperService {
  @Inject private SettingsService settingsService;

  public GitFileConfig getGitFileConfigFromYaml(String accountId, String appId, GitFileConfig gitFileConfig) {
    if (gitFileConfig == null) {
      return null;
    }

    GitFileConfig newGitFileConfig = createNewGitFileConfig(gitFileConfig);

    SettingAttribute settingAttribute =
        settingsService.getByName(accountId, appId, newGitFileConfig.getConnectorName());
    if (settingAttribute == null) {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT)
          .addParam("args", "No git connector exists with name " + newGitFileConfig.getConnectorName());
    }

    newGitFileConfig.setConnectorId(settingAttribute.getUuid());
    newGitFileConfig.setConnectorName(null);

    return newGitFileConfig;
  }

  public GitFileConfig getGitFileConfigForToYaml(GitFileConfig gitFileConfig) {
    if (gitFileConfig == null) {
      return null;
    }

    GitFileConfig newGitFileConfig = createNewGitFileConfig(gitFileConfig);

    SettingAttribute settingAttribute = settingsService.get(newGitFileConfig.getConnectorId());
    if (settingAttribute == null) {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT)
          .addParam("args", "No git connector exists with id " + newGitFileConfig.getConnectorId());
    }

    newGitFileConfig.setConnectorId(null);
    newGitFileConfig.setConnectorName(settingAttribute.getName());

    return newGitFileConfig;
  }

  private GitFileConfig createNewGitFileConfig(GitFileConfig gitFileConfig) {
    return GitFileConfig.builder()
        .connectorId(gitFileConfig.getConnectorId())
        .branch(gitFileConfig.getBranch())
        .filePath(gitFileConfig.getFilePath())
        .commitId(gitFileConfig.getCommitId())
        .useBranch(gitFileConfig.isUseBranch())
        .useInlineServiceDefinition(gitFileConfig.isUseInlineServiceDefinition())
        .serviceSpecFilePath(gitFileConfig.getServiceSpecFilePath())
        .taskSpecFilePath(gitFileConfig.getTaskSpecFilePath())
        .connectorName(gitFileConfig.getConnectorName())
        .repoName(gitFileConfig.getRepoName())
        .build();
  }

  public GitFileConfig renderGitFileConfig(ExecutionContext context, GitFileConfig gitFileConfig) {
    if (context == null) {
      return gitFileConfig;
    }

    if (gitFileConfig.getCommitId() != null) {
      gitFileConfig.setCommitId(context.renderExpression(gitFileConfig.getCommitId()).trim());
    }

    if (gitFileConfig.getBranch() != null) {
      gitFileConfig.setBranch(context.renderExpression(gitFileConfig.getBranch()).trim());
    }

    if (gitFileConfig.getFilePath() != null) {
      gitFileConfig.setFilePath(context.renderExpression(gitFileConfig.getFilePath()).trim());
    }

    if (gitFileConfig.getRepoName() != null) {
      gitFileConfig.setRepoName(context.renderExpression(gitFileConfig.getRepoName()).trim());
    }

    if (gitFileConfig.getServiceSpecFilePath() != null) {
      gitFileConfig.setServiceSpecFilePath(context.renderExpression(gitFileConfig.getServiceSpecFilePath().trim()));
    }

    if (gitFileConfig.getTaskSpecFilePath() != null) {
      gitFileConfig.setTaskSpecFilePath(context.renderExpression(gitFileConfig.getTaskSpecFilePath().trim()));
    }

    if (gitFileConfig.getFilePathList() != null) {
      gitFileConfig.setFilePathList(gitFileConfig.getFilePathList()
                                        .stream()
                                        .map(context::renderExpression)
                                        .map(String::trim)
                                        .collect(Collectors.toList()));
    }

    return gitFileConfig;
  }

  public void validate(GitFileConfig gitFileConfig) {
    notNullCheck("gitFileConfig has to be specified", gitFileConfig, USER);
    if (isBlank(gitFileConfig.getConnectorId())) {
      throw new InvalidRequestException("Connector id cannot be empty.", USER);
    }

    if (gitFileConfig.isUseBranch() && isBlank(gitFileConfig.getBranch())) {
      throw new InvalidRequestException("Branch cannot be empty if useBranch is selected.", USER);
    }

    if (!gitFileConfig.isUseBranch() && isBlank(gitFileConfig.getCommitId())) {
      throw new InvalidRequestException("CommitId cannot be empty if useBranch is not selected.", USER);
    }

    SettingAttribute settingAttribute = settingsService.get(gitFileConfig.getConnectorId());
    if (null == settingAttribute) {
      throw new InvalidRequestException("Invalid git connector provided.", USER);
    }

    if (!(settingAttribute.getValue() instanceof GitConfig)) {
      throw new InvalidRequestException(
          String.format("Invalid git connector provided [connectorId=%s, name=%s, type=%s]",
              gitFileConfig.getConnectorId(), settingAttribute.getName(), settingAttribute.getValue().getType()),
          USER);
    }

    GitConfig gitConfig = (GitConfig) settingAttribute.getValue();
    if (GitConfig.UrlType.ACCOUNT == gitConfig.getUrlType() && isBlank(gitFileConfig.getRepoName())) {
      throw new InvalidRequestException("Repository name not provided for Account level git connector.", USER);
    }
  }

  public void validateEcsGitfileConfig(GitFileConfig gitFileConfig) {
    notNullCheck("gitFileConfig has to be specified", gitFileConfig, USER);
    if (isBlank(gitFileConfig.getTaskSpecFilePath())) {
      throw new InvalidRequestException("File Path to Task Definition cannot be empty.", USER);
    }
    if (!gitFileConfig.isUseInlineServiceDefinition()) {
      if (isBlank(gitFileConfig.getServiceSpecFilePath())) {
        throw new InvalidRequestException("File Path to Service Definition cannot be empty.", USER);
      }
    }
  }
}
