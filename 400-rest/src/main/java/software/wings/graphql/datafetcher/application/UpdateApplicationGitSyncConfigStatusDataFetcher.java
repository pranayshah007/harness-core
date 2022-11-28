/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.application;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.User;
import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.application.input.QLUpdateApplicationGitSyncConfigInput;
import software.wings.graphql.schema.mutation.application.input.QLUpdateApplicationGitSyncConfigInput.QLUpdateApplicationGitSyncConfigInputKeys;
import software.wings.graphql.schema.mutation.application.payload.QLUpdateApplicationGitSyncConfigPayload;
import software.wings.graphql.schema.type.QLGitSyncConfig;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class UpdateApplicationGitSyncConfigStatusDataFetcher
    extends BaseMutatorDataFetcher<QLUpdateApplicationGitSyncConfigInput, QLUpdateApplicationGitSyncConfigPayload> {
  private final AppService appService;
  private final YamlGitService yamlGitService;
  private final AuthService authService;

  @Inject
  public UpdateApplicationGitSyncConfigStatusDataFetcher(
      AppService appService, YamlGitService yamlGitService, AuthService authService) {
    super(QLUpdateApplicationGitSyncConfigInput.class, QLUpdateApplicationGitSyncConfigPayload.class);
    this.appService = appService;
    this.yamlGitService = yamlGitService;
    this.authService = authService;
  }

  @Override
  @AuthRule(permissionType = PermissionType.MANAGE_CONFIG_AS_CODE)
  protected QLUpdateApplicationGitSyncConfigPayload mutateAndFetch(
      QLUpdateApplicationGitSyncConfigInput input, MutationContext mutationContext) {
    validate(input);
    final User user = UserThreadLocal.get();
    if (user != null) {
      authService.authorizeAppAccess(
          mutationContext.getAccountId(), input.getApplicationId(), user, PermissionAttribute.Action.UPDATE);
    }
    final Application application = getApplication(input.getApplicationId());

    final YamlGitConfig savedYamlGitConfig =
        getSavedYamlGitConfig(input.getApplicationId(), mutationContext.getAccountId());

    if (savedYamlGitConfig == null) {
      throw new InvalidRequestException(
          "Git not configured for application Id " + application.getUuid(), WingsException.USER);
    }

    final YamlGitConfig updatedYamlGitConfig = updateYamlGitConfig(input, savedYamlGitConfig);

    return QLUpdateApplicationGitSyncConfigPayload.builder()
        .clientMutationId(input.getClientMutationId())
        .gitSyncConfig(getGitConfigFrom(updatedYamlGitConfig))

        .build();
  }

  private QLGitSyncConfig getGitConfigFrom(YamlGitConfig yamlGitConfig) {
    return YamlGitConfigController.populateQLGitConfig(yamlGitConfig, QLGitSyncConfig.builder()).build();
  }

  private YamlGitConfig updateYamlGitConfig(
      QLUpdateApplicationGitSyncConfigInput input, YamlGitConfig savedYamlGitConfig) {
    savedYamlGitConfig.setEnabled(input.getSyncEnabled());
    return yamlGitService.update(savedYamlGitConfig);
  }

  private void validate(QLUpdateApplicationGitSyncConfigInput input) {
    utils.ensureNotBlankField(input.getApplicationId(), QLUpdateApplicationGitSyncConfigInputKeys.applicationId);
    utils.ensureNotNullField(input.getSyncEnabled(), QLUpdateApplicationGitSyncConfigInputKeys.syncEnabled);
  }

  private YamlGitConfig getSavedYamlGitConfig(String applicationId, String accountId) {
    return yamlGitService.get(accountId, applicationId, EntityType.APPLICATION);
  }

  private Application getApplication(String applicationId) {
    return appService.get(applicationId);
  }
}
