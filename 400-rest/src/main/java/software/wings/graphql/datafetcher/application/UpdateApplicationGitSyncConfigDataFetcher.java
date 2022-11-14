/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.application;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CONFIG_AS_CODE;

import static org.apache.commons.lang3.StringUtils.strip;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;

import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.User;
import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.application.input.QLUpdateApplicationGitSyncConfigInput;
import software.wings.graphql.schema.mutation.application.input.QLUpdateApplicationGitSyncConfigInput.QLUpdateApplicationGitSyncConfigInputKeys;
import software.wings.graphql.schema.mutation.application.payload.QLUpdateApplicationGitSyncConfigPayload;
import software.wings.graphql.schema.type.QLGitSyncConfig;
import software.wings.security.PermissionAttribute;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.yaml.gitSync.beans.YamlGitConfig;
import software.wings.yaml.gitSync.beans.YamlGitConfig.YamlGitConfigBuilder;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class UpdateApplicationGitSyncConfigDataFetcher
    extends BaseMutatorDataFetcher<QLUpdateApplicationGitSyncConfigInput, QLUpdateApplicationGitSyncConfigPayload> {
  private final AppService appService;
  private final YamlGitService yamlGitService;
  private HPersistence persistence;
  private AuthService authService;
  private SettingsService settingsService;

  @Inject
  public UpdateApplicationGitSyncConfigDataFetcher(AppService appService, YamlGitService yamlGitService,
      HPersistence persistence, AuthService authService, SettingsService settingsService) {
    super(QLUpdateApplicationGitSyncConfigInput.class, QLUpdateApplicationGitSyncConfigPayload.class);
    this.appService = appService;
    this.yamlGitService = yamlGitService;
    this.persistence = persistence;
    this.authService = authService;
    this.settingsService = settingsService;
  }

  @Override
  @AuthRule(permissionType = MANAGE_CONFIG_AS_CODE)
  protected QLUpdateApplicationGitSyncConfigPayload mutateAndFetch(
      QLUpdateApplicationGitSyncConfigInput input, MutationContext mutationContext) {
    validate(input);

    final User user = UserThreadLocal.get();
    if (user != null) {
      authService.authorizeAppAccess(
          mutationContext.getAccountId(), input.getApplicationId(), user, PermissionAttribute.Action.UPDATE);
    }
    final Application application = getApplication(input.getApplicationId());
    validateGitConnector(strip(input.getGitConnectorId()), mutationContext.getAccountId());
    final YamlGitConfig savedYamlGitConfig =
        getSavedYamlGitConfig(input.getApplicationId(), mutationContext.getAccountId());

    YamlGitConfig updatedYamlGitConfig;
    if (savedYamlGitConfig == null) {
      // create new
      updatedYamlGitConfig = createAndSaveYamlGitConfig(input, application);
    } else {
      // update existing
      updatedYamlGitConfig = updateYamlGitConfig(input, savedYamlGitConfig);
    }

    return QLUpdateApplicationGitSyncConfigPayload.builder()
        .clientMutationId(input.getClientMutationId())
        .gitSyncConfig(getGitConfigFrom(updatedYamlGitConfig))
        .build();
  }
  private QLGitSyncConfig getGitConfigFrom(YamlGitConfig yamlGitConfig) {
    return YamlGitConfigController.populateQLGitConfig(yamlGitConfig, QLGitSyncConfig.builder()).build();
  }

  private void validateGitConnector(String connectorId, String accountId) {
    final SettingAttribute settingAttribute = settingsService.getWithRbac(connectorId);

    if (!settingAttribute.getAccountId().equals(accountId)) {
      throw new InvalidRequestException("Git Connector does not exist", WingsException.USER);
    }

    if (!(SettingCategory.CONNECTOR == settingAttribute.getCategory()
            && SettingVariableTypes.GIT
                == Optional.of(settingAttribute)
                       .map(SettingAttribute::getValue)
                       .map(SettingValue::getSettingType)
                       .orElse(null))) {
      throw new InvalidRequestException("Connector should be a Git Connector", WingsException.USER);
    }
  }

  private YamlGitConfig updateYamlGitConfig(
      QLUpdateApplicationGitSyncConfigInput input, YamlGitConfig savedYamlGitConfig) {
    savedYamlGitConfig.setGitConnectorId(input.getGitConnectorId());
    savedYamlGitConfig.setRepositoryName(input.getRepositoryName());
    savedYamlGitConfig.setBranchName(input.getBranch());
    savedYamlGitConfig.setEnabled(input.getSyncEnabled());
    return yamlGitService.update(savedYamlGitConfig);
  }

  private void validate(QLUpdateApplicationGitSyncConfigInput input) {
    utils.ensureNotBlankField(input.getApplicationId(), QLUpdateApplicationGitSyncConfigInputKeys.applicationId);
    utils.ensureNotBlankField(input.getGitConnectorId(), QLUpdateApplicationGitSyncConfigInputKeys.gitConnectorId);
    utils.ensureNotBlankField(input.getBranch(), QLUpdateApplicationGitSyncConfigInputKeys.branch);
    utils.ensureNotNullField(input.getSyncEnabled(), QLUpdateApplicationGitSyncConfigInputKeys.syncEnabled);
  }

  private YamlGitConfig getSavedYamlGitConfig(String applicationId, String accountId) {
    return yamlGitService.get(accountId, applicationId, EntityType.APPLICATION);
  }

  private Application getApplication(String applicationId) {
    return appService.get(applicationId);
  }

  private YamlGitConfig createAndSaveYamlGitConfig(
      QLUpdateApplicationGitSyncConfigInput input, Application application) {
    final YamlGitConfigBuilder configBuilder = YamlGitConfig.builder();
    final YamlGitConfig yamlGitConfig = configBuilder.accountId(application.getAccountId())
                                            .gitConnectorId(strip(input.getGitConnectorId()))
                                            .repositoryName(strip(input.getRepositoryName()))
                                            .branchName(strip(input.getBranch()))
                                            .enabled(input.getSyncEnabled())
                                            .syncMode(YamlGitConfig.SyncMode.BOTH)
                                            .entityType(EntityType.APPLICATION)
                                            .entityId(application.getUuid())
                                            .build();
    yamlGitConfig.setAppId(application.getAppId());
    return yamlGitService.save(yamlGitConfig);
  }
}
