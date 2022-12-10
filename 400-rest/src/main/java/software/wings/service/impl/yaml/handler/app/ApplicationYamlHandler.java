/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.app;

import static io.harness.annotations.dev.HarnessModule._955_CG_YAML;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.GITHUB_WEBHOOK_AUTHENTICATION;
import static io.harness.beans.FeatureName.SPG_ALLOW_DISABLE_TRIGGERS;
import static io.harness.beans.FeatureName.WEBHOOK_TRIGGER_AUTHORIZATION;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.EntityType.APPLICATION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.Application;
import software.wings.beans.Application.Yaml;
import software.wings.beans.EntityType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.yaml.gitSync.beans.YamlGitConfig;
import software.wings.yaml.gitSync.beans.YamlGitConfig.SyncMode;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
/**
 * @author rktummala on 10/22/17
 */
@Singleton
@OwnedBy(CDC)
@TargetModule(_955_CG_YAML)
public class ApplicationYamlHandler extends BaseYamlHandler<Application.Yaml, Application> {
  @Inject YamlHelper yamlHelper;
  @Inject AppService appService;
  @Inject YamlGitService yamlGitService;
  @Inject FeatureFlagService featureFlagService;
  @Inject SettingsService settingsService;

  @Override
  public void delete(ChangeContext<Yaml> changeContext) {
    Application application = get(changeContext.getChange().getAccountId(), changeContext.getChange().getFilePath());
    if (application != null) {
      appService.delete(application.getUuid(), changeContext.getChange().isSyncFromGit());
      yamlGitService.delete(application.getAccountId(), application.getUuid(), EntityType.APPLICATION);
    }
  }

  @Override
  public Application.Yaml toYaml(Application application, String appId) {
    boolean gitSync = false;
    String gitConnector = null;
    String branchName = null;
    String repoName = null;

    if (application.getYamlGitConfig() != null && application.getYamlGitConfig().isEnabled()) {
      gitSync = application.getYamlGitConfig().isEnabled();
      branchName = application.getYamlGitConfig().getBranchName();
      repoName = application.getYamlGitConfig().getRepositoryName();
      SettingAttribute encryptedYamlRef = settingsService.get(application.getYamlGitConfig().getGitConnectorId());
      if (encryptedYamlRef != null) {
        gitConnector = encryptedYamlRef.getName();
      }
    }

    Yaml yaml = Yaml.builder()
                    .type(APPLICATION.name())
                    .description(application.getDescription())
                    .harnessApiVersion(getHarnessApiVersion())
                    .isGitSyncEnabled(gitSync)
                    .gitConnector(gitConnector)
                    .branchName(branchName)
                    .repoName(repoName)
                    .build();

    if (featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, application.getAccountId())) {
      yaml.setAreWebHookSecretsMandated(application.getAreWebHookSecretsMandated());
    }

    if (featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, application.getAccountId())) {
      yaml.setIsManualTriggerAuthorized(application.getIsManualTriggerAuthorized());
    }

    if (featureFlagService.isEnabled(SPG_ALLOW_DISABLE_TRIGGERS, application.getAccountId())) {
      yaml.setDisableTriggers(application.getDisableTriggers());
    }
    updateYamlWithAdditionalInfo(application, appId, yaml);
    return yaml;
  }

  @Override
  public Application upsertFromYaml(ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    String accountId = changeContext.getChange().getAccountId();
    Yaml yaml = changeContext.getYaml();
    String yamlFilePath = changeContext.getChange().getFilePath();
    Application previous = get(accountId, yamlFilePath);

    String appName = yamlHelper.getAppName(yamlFilePath);
    Application current = anApplication().accountId(accountId).name(appName).description(yaml.getDescription()).build();

    if (featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, accountId)) {
      if (yaml.getIsManualTriggerAuthorized() == null && previous != null
          && previous.getIsManualTriggerAuthorized() != null) {
        current.setIsManualTriggerAuthorized(false);
      } else {
        current.setIsManualTriggerAuthorized(yaml.getIsManualTriggerAuthorized());
      }
    }

    if (featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, accountId)) {
      if (yaml.getAreWebHookSecretsMandated() == null && previous != null
          && previous.getAreWebHookSecretsMandated() != null) {
        current.setAreWebHookSecretsMandated(false);
      } else {
        current.setAreWebHookSecretsMandated(yaml.getAreWebHookSecretsMandated());
      }
    }

    if (featureFlagService.isEnabled(SPG_ALLOW_DISABLE_TRIGGERS, accountId)) {
      if (yaml.getDisableTriggers() == null && previous != null && previous.getDisableTriggers() != null) {
        current.setDisableTriggers(false);
      } else {
        current.setDisableTriggers(yaml.getDisableTriggers());
      }
    }

    current.setSyncFromGit(changeContext.getChange().isSyncFromGit());

    Application updatedApplication;
    if (previous != null) {
      current.setUuid(previous.getUuid());
      current.setAppId(previous.getUuid());

      // Needs special handling for not modifying the yamlGitConfig for existing application
      YamlGitConfig yamlGitConfig = yamlGitService.get(accountId, current.getUuid(), EntityType.APPLICATION);
      current.setYamlGitConfig(yamlGitConfig);

      updatedApplication = appService.update(current);
    } else {
      software.wings.yaml.gitSync.YamlGitConfig yamlGitConfig = null;

      if (changeContext.getChange() instanceof GitFileChange) {
        yamlGitConfig = ((GitFileChange) changeContext.getChange()).getYamlGitConfig();
      }
      current.setYamlGitConfig(createAppYamlGitConfig(accountId, yamlGitConfig));

      updatedApplication = appService.save(current);
    }

    changeContext.setEntity(updatedApplication);
    return updatedApplication;
  }

  @Override
  public Class getYamlClass() {
    return Application.Yaml.class;
  }

  @Override
  public Application get(String accountId, String yamlFilePath) {
    return yamlHelper.getApp(accountId, yamlFilePath);
  }

  private YamlGitConfig createAppYamlGitConfig(
      String accountId, software.wings.yaml.gitSync.YamlGitConfig yamlGitConfig) {
    if (yamlGitConfig == null) {
      return null;
    }

    return YamlGitConfig.builder()
        .accountId(accountId)
        .gitConnectorId(yamlGitConfig.getGitConnectorId())
        .branchName(yamlGitConfig.getBranchName())
        .repositoryName(yamlGitConfig.getRepositoryName())
        .syncMode(SyncMode.BOTH)
        .enabled(true)
        .build();
  }
}
