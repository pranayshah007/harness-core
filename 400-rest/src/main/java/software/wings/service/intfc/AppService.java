/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.Application;
import software.wings.service.intfc.ownership.OwnedByAccount;

import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Application Service.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface AppService extends OwnedByAccount, Exterminator {
  /**
   * Save.
   *
   * @param app the app
   * @return the application
   */
  Application save(Application app);

  PageResponse<Application> list(
      PageRequest<Application> req, boolean details, boolean withTags, String tagFilter, boolean hitSecondary);

  PageResponse<Application> list(PageRequest<Application> req);

  /**
   * Exist boolean.
   *
   * @param appId the app id
   * @return the boolean
   */
  boolean exist(@NotEmpty String appId);

  /**
   * Find by uuid.
   *
   * @param uuid the uuid
   * @return the application
   */
  Application get(@NotEmpty String uuid);

  /**
   * Find by uuid.
   *
   * @param uuid the uuid
   * @return the application or null if not found
   */
  Application getNullable(@NotEmpty String uuid);

  /**
   * Retrieves the application with defaults
   * @param uuid
   * @return
   */
  Application getApplicationWithDefaults(@NotEmpty String uuid);

  /**
   * Get application.
   *
   * @param appId        the app id
   * @param details the details
   * @return the application
   */
  Application get(@NotEmpty String appId, boolean details);

  Application getAppByName(String accountId, String appName);

  /**
   * Update.
   *
   * @param app the app
   * @return the application
   */
  Application update(Application app);

  /**
   * Delete app.
   *
   * @param appId the app id
   */
  void delete(@NotEmpty String appId);

  /**
   * Prune owned from the app entities.
   *
   * @param appId the app id
   */
  void pruneDescendingEntities(@NotEmpty String appId);

  /**
   * Gets apps by account id.
   *
   * @param accountId the account id
   * @return the apps by account id
   */
  List<Application> getAppsByAccountId(String accountId);

  /**
   * Gets app ids by account id.
   *
   * @param accountId the account id
   * @return the app ids by account id
   */
  List<String> getAppIdsByAccountId(String accountId);

  /**
   * Gets app ids by account id.
   *
   * @param accountId the account id
   * @return the app ids by account id
   */
  Set<String> getAppIdsAsSetByAccountId(String accountId);

  /**
   * Gets app names by account id.
   *
   * @param accountId the account id
   * @return the app names by account id
   */
  List<String> getAppNamesByAccountId(String accountId);

  String getAccountIdByAppId(String appId);

  void delete(String appId, boolean syncFromGit);

  List<Application> getAppsByIds(@NotNull Set<String> appIds);

  Boolean getDisableTriggersByAppId(String appId);
}
