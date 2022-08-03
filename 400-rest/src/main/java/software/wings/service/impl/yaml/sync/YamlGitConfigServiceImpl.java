/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.sync;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.Base.ID_KEY2;

import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter;
import io.harness.exception.UnexpectedException;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.EntityType;
import software.wings.beans.User;
import software.wings.dl.WingsMongoPersistence;
import software.wings.dl.WingsPersistence;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.yaml.sync.YamlGitConfigService;
import software.wings.yaml.gitSync.YamlGitConfig;
import software.wings.yaml.gitSync.YamlGitConfig.YamlGitConfigKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class YamlGitConfigServiceImpl implements YamlGitConfigService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AppService appService;
  @Inject private AccountService accountService;
  @Inject private AuthService authService;
  @Inject private WingsMongoPersistence wingsMongoPersistence;

  @Override
  public List<YamlGitConfig> getYamlGitConfigAccessibleToUserWithEntityName(String accountId) {
    final List<YamlGitConfig> gitConfigs = getActiveYamlGitConfig(accountId);
    return filterYamlGitConfigWithAccessibleEntityAndAddEntityName(gitConfigs, accountId);
  }

  private List<YamlGitConfig> getActiveYamlGitConfig(String accountId) {
    final List<EntityType> supportedEntityTypes = Arrays.asList(EntityType.APPLICATION, EntityType.ACCOUNT);
    return wingsPersistence.createQuery(YamlGitConfig.class)
        .filter(YamlGitConfigKeys.accountId, accountId)
        .filter(YamlGitConfigKeys.enabled, Boolean.TRUE)
        .field(YamlGitConfig.ENTITY_TYPE_KEY)
        .in(supportedEntityTypes)
        .asList();
  }

  private List<YamlGitConfig> filterYamlGitConfigWithAccessibleEntityAndAddEntityName(
      List<YamlGitConfig> yamlGitConfigs, String accountId) {
    if (isEmpty(yamlGitConfigs)) {
      return yamlGitConfigs;
    }
    Map<String, String> namesOfAppsAccessibleToUser = getAppIdNameMapForAppAccessibleToUser(yamlGitConfigs, accountId);
    List<YamlGitConfig> gitConfigAccessibleToUser = new ArrayList<>();
    for (YamlGitConfig yamlGitConfig : yamlGitConfigs) {
      if (yamlGitConfig.getEntityType() == EntityType.APPLICATION) {
        if (namesOfAppsAccessibleToUser.containsKey(yamlGitConfig.getEntityId())) {
          yamlGitConfig.setEntityName(namesOfAppsAccessibleToUser.get(yamlGitConfig.getEntityId()));
        } else {
          // The user doesn't has permission for this app
          continue;
        }
      } else {
        yamlGitConfig.setEntityName(getAccountName(accountId));
      }
      gitConfigAccessibleToUser.add(yamlGitConfig);
    }
    return gitConfigAccessibleToUser;
  }

  private Set<String> getAllAppsAllowedToTheUser(String accountId) {
    User user = UserThreadLocal.get();
    if (user == null) {
      throw new UnexpectedException("The user variable is not set in the thread");
    }
    UserPermissionInfo userPermissionInfo = authService.getUserPermissionInfo(accountId, user, false);
    return userPermissionInfo.getAppPermissionMapInternal().keySet();
  }

  private List<Application> getApplicationsAllowedToUser(Set<String> applicationIds, String accountId) {
    // Getting the allowed appIds
    Set<String> allAppsAllowedToTheUser = getAllAppsAllowedToTheUser(accountId);
    if (allAppsAllowedToTheUser == null) {
      return Collections.emptyList();
    }
    List<String> filteredAppIds =
        applicationIds.stream().filter(allAppsAllowedToTheUser::contains).collect(Collectors.toList());
    if (isEmpty(filteredAppIds)) {
      return Collections.emptyList();
    }
    PageRequest<Application> req = aPageRequest()
                                       .addFilter(ApplicationKeys.accountId, SearchFilter.Operator.EQ, accountId)
                                       .addFilter("_id", IN, filteredAppIds.toArray())
                                       .addFieldsIncluded(ApplicationKeys.name)
                                       .addFieldsIncluded(ApplicationKeys.uuid)
                                       .build();
    return wingsMongoPersistence.getAllEntities(req, () -> appService.list(req));
  }

  private Map<String, String> getAppIdNameMapForAppAccessibleToUser(
      List<YamlGitConfig> yamlGitConfigs, String accountId) {
    final Set<String> applicationIds =
        yamlGitConfigs.stream()
            .filter(yamlGitConfig -> yamlGitConfig.getEntityType() == EntityType.APPLICATION)
            .map(YamlGitConfig::getEntityId)
            .collect(Collectors.toSet());
    List<Application> applications = getApplicationsAllowedToUser(applicationIds, accountId);
    if (isEmpty(applications)) {
      return Collections.emptyMap();
    }
    return applications.stream().collect(Collectors.toMap(Application::getUuid, Application::getName));
  }

  private String getAccountName(String accountId) {
    Account account = accountService.get(accountId);
    if (account != null) {
      return account.getAccountName();
    } else {
      throw new UnexpectedException(String.format("No account exists with the id %s", accountId));
    }
  }

  public Set<String> getAppIdsForYamlGitConfig(List<String> yamlGitConfigIds) {
    List<YamlGitConfig> yamlGitConfigs = wingsPersistence.createQuery(YamlGitConfig.class)
                                             .field(ID_KEY2)
                                             .in(yamlGitConfigIds)
                                             .project(ApplicationKeys.appId, true)
                                             .asList();
    if (isEmpty(yamlGitConfigs)) {
      return Collections.emptySet();
    }
    return yamlGitConfigs.stream().map(config -> config.getAppId()).collect(Collectors.toSet());
  }

  @Override
  public YamlGitConfig getYamlGitConfigFromAppId(String appId, String accountId) {
    return wingsPersistence.createQuery(YamlGitConfig.class)
        .filter(YamlGitConfigKeys.accountId, accountId)
        .filter(YamlGitConfigKeys.appId, appId)
        .get();
  }

  @Override
  public void deleteByAccountId(String accountId) {
    boolean delete = wingsPersistence.delete(
        wingsPersistence.createQuery(YamlGitConfig.class).filter(YamlGitConfigKeys.accountId, accountId));
    log.info("Deleted [{}] yaml git config for account [{}]", delete, accountId);
  }

  @Override
  public void pruneByApplication(String appId) {
    boolean delete = wingsPersistence.delete(
        wingsPersistence.createQuery(YamlGitConfig.class).filter(YamlGitConfigKeys.appId, appId));
    log.info("Deleted [{}] yaml git config for appid [{}]", delete, appId);
  }
}
