/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;

import static software.wings.security.EnvFilter.FilterType.NON_PROD;
import static software.wings.security.EnvFilter.FilterType.PROD;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SECRETS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.FeatureName;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretScopeMetadata;
import io.harness.exception.SecretManagementException;
import io.harness.ff.FeatureFlagService;
import io.harness.secrets.setupusage.SecretSetupUsageService;

import software.wings.beans.Base;
import software.wings.beans.User;
import software.wings.security.EnvFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.PermissionAttribute;
import software.wings.security.UsageRestrictions;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.UserService;
import software.wings.settings.RestrictionsAndAppEnvMap;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Slf4j
@Singleton
@ValidateOnExecution
@OwnedBy(PL)
public class SecretsRBACServiceImpl implements SecretsRBACService {
  private final UsageRestrictionsService usageRestrictionsService;
  private final SecretSetupUsageService secretSetupUsageService;
  private final UserService userService;
  private final AppService appService;
  private final EnvironmentService envService;
  @Inject private FeatureFlagService featureFlagService;

  @Inject
  public SecretsRBACServiceImpl(UsageRestrictionsService usageRestrictionsService,
      SecretSetupUsageService secretSetupUsageService, UserService userService, AppService appService,
      EnvironmentService environmentService) {
    this.usageRestrictionsService = usageRestrictionsService;
    this.secretSetupUsageService = secretSetupUsageService;
    this.userService = userService;
    this.appService = appService;
    this.envService = environmentService;
  }

  private void checkIfUsagesAreInScope(String accountId, String secretId, UsageRestrictions usageRestrictions) {
    Map<String, Set<String>> setupAppEnvMap = secretSetupUsageService.getUsagesAppEnvMap(accountId, secretId);
    if (setupAppEnvMap.size() == 0) {
      // This secret is not referred by any setup entities. no need to check.
      return;
    }
    usageRestrictionsService.validateSetupUsagesOnUsageRestrictionsUpdate(accountId, setupAppEnvMap, usageRestrictions);
  }

  @Override
  public boolean hasAccessToEditSecrets(String accountId, Set<SecretScopeMetadata> secretsScopeMetadata) {
    for (SecretScopeMetadata secretScopeMetadata : secretsScopeMetadata) {
      if (!usageRestrictionsService.userHasPermissionsToChangeEntity(accountId, MANAGE_SECRETS,
              secretScopeMetadata.getCalculatedScopes().getUsageRestrictions(),
              secretScopeMetadata.getCalculatedScopes().isScopedToAccount())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean hasAccessToEditSecret(String accountId, SecretScopeMetadata secretScopeMetadata) {
    return hasAccessToEditSecrets(accountId, Sets.newHashSet(secretScopeMetadata));
  }

  @Override
  public boolean hasAccessToReadSecrets(
      String accountId, Set<SecretScopeMetadata> secretsScopeMetadata, String appId, String envId) {
    boolean isAccountAdmin = userService.hasPermission(accountId, MANAGE_SECRETS);
    RestrictionsAndAppEnvMap restrictionsAndAppEnvMap =
        usageRestrictionsService.getRestrictionsAndAppEnvMapFromCache(accountId, PermissionAttribute.Action.READ);
    Map<String, Set<String>> appEnvMapFromPermissions = restrictionsAndAppEnvMap.getAppEnvMap();
    UsageRestrictions restrictionsFromUserPermissions = restrictionsAndAppEnvMap.getUsageRestrictions();

    Set<String> appsByAccountId = appService.getAppIdsAsSetByAccountId(accountId);
    Map<String, List<Base>> appIdEnvMapForAccount = envService.getAppIdEnvMap(appsByAccountId, accountId);
    if (featureFlagService.isGlobalEnabled(FeatureName.SPG_ENVIRONMENT_QUERY_LOGS)) {
      log.info("[GetAppIdEnvMap] SecretsRBACServiceImpl:hasAccessToReadSecrets - debug log");
    }

    for (SecretScopeMetadata secretScopeMetadata : secretsScopeMetadata) {
      if (!usageRestrictionsService.hasAccess(accountId, isAccountAdmin, appId, envId, false,
              secretScopeMetadata.getCalculatedScopes().getUsageRestrictions(), restrictionsFromUserPermissions,
              appEnvMapFromPermissions, appIdEnvMapForAccount,
              secretScopeMetadata.getCalculatedScopes().isScopedToAccount())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean hasAccessToAccountScopedSecrets(String accountId) {
    return userService.hasPermission(accountId, MANAGE_SECRETS);
  }

  @Override
  public List<SecretScopeMetadata> filterSecretsByReadPermission(String accountId,
      List<SecretScopeMetadata> secretsScopeMetadata, String appId, String envId, boolean forUsageInNewApp) {
    List<SecretScopeMetadata> filteredList = new ArrayList<>();
    boolean isAccountAdmin = userService.hasPermission(accountId, MANAGE_SECRETS);
    RestrictionsAndAppEnvMap restrictionsAndAppEnvMap =
        usageRestrictionsService.getRestrictionsAndAppEnvMapFromCache(accountId, PermissionAttribute.Action.READ);
    Map<String, Set<String>> appEnvMapFromPermissions = restrictionsAndAppEnvMap.getAppEnvMap();
    UsageRestrictions restrictionsFromUserPermissions = restrictionsAndAppEnvMap.getUsageRestrictions();

    Set<String> appsByAccountId = appService.getAppIdsAsSetByAccountId(accountId);
    Map<String, List<Base>> appIdEnvMapForAccount = envService.getAppIdEnvMap(appsByAccountId, accountId);
    if (featureFlagService.isGlobalEnabled(FeatureName.SPG_ENVIRONMENT_QUERY_LOGS)) {
      log.info("[GetAppIdEnvMap] SecretsRBACServiceImpl:filterSecretsByReadPermission - debug log");
    }

    for (SecretScopeMetadata secretScopeMetadata : secretsScopeMetadata) {
      if (usageRestrictionsService.hasAccess(accountId, isAccountAdmin, appId, envId, forUsageInNewApp,
              secretScopeMetadata.getCalculatedScopes().getUsageRestrictions(), restrictionsFromUserPermissions,
              appEnvMapFromPermissions, appIdEnvMapForAccount,
              secretScopeMetadata.getCalculatedScopes().isScopedToAccount())) {
        filteredList.add(secretScopeMetadata);
      }
    }
    return filteredList;
  }

  @Override
  public boolean hasAccessToReadSecret(
      String accountId, SecretScopeMetadata secretScopeMetadata, String appId, String envId) {
    return hasAccessToReadSecrets(accountId, Sets.newHashSet(secretScopeMetadata), appId, envId);
  }

  @Override
  public void canReplacePermissions(String accountId, SecretScopeMetadata newSecretScopeMetadata,
      SecretScopeMetadata oldSecretScopeMetadata, boolean validateReferences) {
    usageRestrictionsService.validateUsageRestrictionsOnEntityUpdate(accountId, MANAGE_SECRETS,
        oldSecretScopeMetadata.getCalculatedScopes().getUsageRestrictions(),
        newSecretScopeMetadata.getCalculatedScopes().getUsageRestrictions(),
        newSecretScopeMetadata.getCalculatedScopes().isScopedToAccount());

    if (!newSecretScopeMetadata.isInheritScopesFromSM() && !newSecretScopeMetadata.getSecretScopes().isScopedToAccount()
        && !usageRestrictionsService.isUsageRestrictionsSubset(accountId,
            newSecretScopeMetadata.getSecretScopes().getUsageRestrictions(),
            newSecretScopeMetadata.getSecretsManagerScopes().getUsageRestrictions())) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR,
          "The usage scope of the secret is wider than the usage scope associated with the Secrets Manager", USER);
    }

    if (!Objects.equals(newSecretScopeMetadata.getCalculatedScopes().getUsageRestrictions(),
            oldSecretScopeMetadata.getCalculatedScopes().getUsageRestrictions())
        && validateReferences) {
      // Validate if change of the usage scope is resulting in with dangling references in service/environments.
      checkIfUsagesAreInScope(accountId, oldSecretScopeMetadata.getSecretId(),
          newSecretScopeMetadata.getCalculatedScopes().getUsageRestrictions());
    }
  }

  @Override
  public void canSetPermissions(String accountId, SecretScopeMetadata secretScopeMetadata) {
    usageRestrictionsService.validateUsageRestrictionsOnEntitySave(accountId, MANAGE_SECRETS,
        secretScopeMetadata.getCalculatedScopes().getUsageRestrictions(),
        secretScopeMetadata.getCalculatedScopes().isScopedToAccount());
    if (!secretScopeMetadata.isInheritScopesFromSM() && !secretScopeMetadata.getSecretScopes().isScopedToAccount()) {
      if (!usageRestrictionsService.isUsageRestrictionsSubset(accountId,
              secretScopeMetadata.getSecretScopes().getUsageRestrictions(),
              secretScopeMetadata.getSecretsManagerScopes().getUsageRestrictions())) {
        throw new SecretManagementException(
            SECRET_MANAGEMENT_ERROR, "Given secret scopes are more relaxed than the secrets manager scopes", USER);
      }
    }
  }

  @Override
  public UsageRestrictions getDefaultUsageRestrictions(String accountId) {
    if (hasUserContext()) {
      return usageRestrictionsService.getDefaultUsageRestrictions(accountId, null, null);
    } else {
      GenericEntityFilter appFilter =
          GenericEntityFilter.builder().filterType(GenericEntityFilter.FilterType.ALL).build();
      EnvFilter envFilter = EnvFilter.builder().filterTypes(Sets.newHashSet(PROD, NON_PROD)).build();
      UsageRestrictions.AppEnvRestriction appEnvRestriction =
          UsageRestrictions.AppEnvRestriction.builder().appFilter(appFilter).envFilter(envFilter).build();
      UsageRestrictions usageRestrictions = new UsageRestrictions();
      usageRestrictions.setAppEnvRestrictions(Sets.newHashSet(appEnvRestriction));
      return usageRestrictions;
    }
  }

  @Override
  public boolean isScopeInConflict(EncryptedData encryptedData, SecretManagerConfig secretManagerConfig) {
    if (encryptedData.isInheritScopesFromSM() || encryptedData.isScopedToAccount()) {
      return false;
    }
    return !usageRestrictionsService.isUsageRestrictionsSubset(
        encryptedData.getAccountId(), encryptedData.getUsageRestrictions(), secretManagerConfig.getUsageRestrictions());
  }

  private boolean hasUserContext() {
    User user = UserThreadLocal.get();
    return user != null && user.getUserRequestContext() != null;
  }
}
