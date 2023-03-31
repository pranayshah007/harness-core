/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.FeatureName.SPG_ENABLE_SHARING_FILTERS;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.mongo.MongoUtils.setUnset;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.AccountAuditFilter;
import software.wings.beans.ApplicationAuditFilter;
import software.wings.beans.AuditPreference;
import software.wings.beans.AuditPreference.AuditPreferenceKeys;
import software.wings.beans.AuditPreferenceResponse;
import software.wings.beans.AuditPreferenceResponse.AuditPreferenceResponseBuilder;
import software.wings.beans.DeploymentPreference;
import software.wings.beans.HarnessTagFilter;
import software.wings.beans.HarnessTagFilter.TagFilterCondition;
import software.wings.beans.Preference;
import software.wings.beans.Preference.PreferenceKeys;
import software.wings.beans.PreferenceType;
import software.wings.beans.ResourceLookup;
import software.wings.beans.ResourceLookup.ResourceLookupKeys;
import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.PreferenceService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.UpdateOperations;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class PreferenceServiceImpl implements PreferenceService {
  public static final String PREFERENCE_WITH_SAME_NAME_EXISTS = "Preference with same name exists for user";
  public static final String USER_IS_NOT_ENABLED = "User is not a enabled to add a share filter";
  private static final String USER_ID_KEY = "userId";
  @Inject private WingsPersistence wingsPersistence;
  @Inject private UserGroupService userGroupService;
  @Inject private UserService userService;
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public Preference save(String accountId, String userId, Preference preference) {
    Preference savedPreference = null;
    Preference existingPreference = getPreferenceByName(accountId, userId, preference.getName());
    if (existingPreference != null) {
      throw new InvalidRequestException(PREFERENCE_WITH_SAME_NAME_EXISTS, USER);
    }

    if (featureFlagService.isEnabled(SPG_ENABLE_SHARING_FILTERS, accountId) && !hasUserGroupAdmin(accountId, userId)
        && preference.getUserGroupsIdToShare() != null) {
      throw new InvalidRequestException(USER_IS_NOT_ENABLED, USER);
    }

    savedPreference = wingsPersistence.saveAndGet(Preference.class, preference);
    return savedPreference;
  }

  @VisibleForTesting
  public boolean hasUserGroupAdmin(String accountId, String userId) {
    User user = userService.get(accountId, userId);
    UserGroup userGroupAdmin = userGroupService.getAdminUserGroup(accountId);

    List<UserGroup> listOfAdminUG = userGroupService.listByAccountId(accountId, user, true)
                                        .stream()
                                        .filter(userGroup -> userGroup.getUuid().equals(userGroupAdmin.getUuid()))
                                        .collect(Collectors.toList());
    if (listOfAdminUG.isEmpty()) {
      return false;
    }
    return true;
  }

  @Override
  public Preference getPreferenceByName(String accountId, String userId, String name) {
    return wingsPersistence.createQuery(Preference.class)
        .filter(PreferenceKeys.accountId, accountId)
        .filter(PreferenceKeys.userId, userId)
        .filter(PreferenceKeys.name, name)
        .get();
  }

  @Override
  public Preference get(String accountId, String userId, String preferenceId) {
    Preference preference = wingsPersistence.createQuery(Preference.class)
                                .filter(PreferenceKeys.accountId, accountId)
                                .filter(PreferenceKeys.userId, userId)
                                .filter(PreferenceKeys.id, preferenceId)
                                .get();
    if (preference instanceof DeploymentPreference) {
      ((DeploymentPreference) preference)
          .setUiDisplayTagString(
              constructUiDisplayTagString(((DeploymentPreference) preference).getHarnessTagFilter()));
    }
    return preference;
  }

  @Override
  public PageResponse<Preference> list(PageRequest<Preference> pageRequest, String accountId, String userId) {
    User user = userService.get(userId);
    List<String> l = userGroupService.listByAccountId(accountId, user, true)
                         .stream()
                         .map(UserGroup::getUuid)
                         .collect(Collectors.toList());

    if (featureFlagService.isEnabled(SPG_ENABLE_SHARING_FILTERS, accountId)) {
      SearchFilter userIdFilter =
          SearchFilter.builder().fieldName(PreferenceKeys.userId).fieldValues(new String[] {userId}).op(EQ).build();
      for (String s : l) {
        pageRequest.addFilter("", SearchFilter.Operator.OR,
            SearchFilter.builder()
                .fieldName(PreferenceKeys.userGroupsIdToShare)
                .op(SearchFilter.Operator.EQ)
                .fieldValues(new String[] {s})
                .build(),
            userIdFilter);
      }
    } else {
      pageRequest.addFilter(USER_ID_KEY, EQ, userId);
    }

    PageResponse<Preference> preferences = wingsPersistence.query(Preference.class, pageRequest);
    if (preferences != null && isNotEmpty(preferences.getResponse())) {
      for (Preference preference : preferences.getResponse()) {
        if (preference instanceof DeploymentPreference) {
          ((DeploymentPreference) preference)
              .setUiDisplayTagString(
                  constructUiDisplayTagString(((DeploymentPreference) preference).getHarnessTagFilter()));
        }
      }
    }
    return preferences;
  }

  @Override
  public AuditPreferenceResponse listAuditPreferences(String accountId, String userId) {
    // pageRequest.addFilter(USER_ID_KEY, EQ, userId);
    List<AuditPreference> auditPreferences =
        wingsPersistence.createQuery(AuditPreference.class)
            .filter(AuditPreferenceKeys.accountId, accountId)
            .filter(AuditPreferenceKeys.preferenceType, PreferenceType.AUDIT_PREFERENCE.name())
            .filter(AuditPreferenceKeys.userId, userId)
            .asList();

    AuditPreferenceResponseBuilder responseBuilder =
        AuditPreferenceResponse.builder().auditPreferences(auditPreferences);

    generateResourceLookupsForIdsInFilter(auditPreferences, responseBuilder, accountId);

    return responseBuilder.build();
  }

  private void generateResourceLookupsForIdsInFilter(
      List<AuditPreference> auditPreferences, AuditPreferenceResponseBuilder responseBuilder, String accountId) {
    Set<String> ids = new HashSet<>();

    // generate Id set
    auditPreferences.forEach(auditPreference -> {
      ApplicationAuditFilter applicationAuditFilter = auditPreference.getApplicationAuditFilter();
      if (applicationAuditFilter != null) {
        addToSet(ids, applicationAuditFilter.getAppIds());
        addToSet(ids, applicationAuditFilter.getResourceIds());
      }

      AccountAuditFilter accountAuditFilter = auditPreference.getAccountAuditFilter();
      if (accountAuditFilter != null) {
        addToSet(ids, accountAuditFilter.getResourceIds());
      }
    });

    List<ResourceLookup> resourceLookups = wingsPersistence.createQuery(ResourceLookup.class)
                                               .field(ResourceLookupKeys.resourceId)
                                               .in(ids)
                                               .filter(ResourceLookupKeys.accountId, accountId)
                                               .project(ResourceLookupKeys.resourceId, true)
                                               .project(ResourceLookupKeys.resourceName, true)
                                               .project(ResourceLookupKeys.resourceType, true)
                                               .project(ResourceLookupKeys.appId, true)
                                               .project(ResourceLookupKeys.accountId, true)
                                               .asList();

    Map<String, ResourceLookup> lookupMap = new HashMap<>();
    if (isNotEmpty(resourceLookups)) {
      resourceLookups.forEach(resourceLookup -> lookupMap.put(resourceLookup.getResourceId(), resourceLookup));
    }

    responseBuilder.resourceLookupMap(lookupMap);
  }

  private void addToSet(Set<String> ids, List<String> input) {
    if (isNotEmpty(input)) {
      ids.addAll(input);
    }
  }

  @Override
  public Preference update(String accountId, String userId, String preferenceId, Preference preference) {
    // Update preference for given account, user and preference Id
    Preference existingPreference = getPreferenceByName(accountId, userId, preference.getName());
    if (existingPreference != null && !existingPreference.getUuid().equals(preferenceId)) {
      throw new InvalidRequestException(PREFERENCE_WITH_SAME_NAME_EXISTS, USER);
    }

    if (preference instanceof DeploymentPreference) {
      DeploymentPreference deployPref = null;
      deployPref = (DeploymentPreference) preference;

      UpdateOperations<Preference> updateOperations = wingsPersistence.createUpdateOperations(Preference.class);

      if (featureFlagService.isEnabled(SPG_ENABLE_SHARING_FILTERS, accountId) && hasUserGroupAdmin(accountId, userId)) {
        setUnset(updateOperations, PreferenceKeys.userGroupsIdToShare, deployPref.getUserGroupsIdToShare());
      } else {
        setUnset(updateOperations, PreferenceKeys.userGroupsIdToShare, null);
      }

      // Set fields to update
      setUnset(updateOperations, "name", deployPref.getName());
      setUnset(updateOperations, "appIds", deployPref.getAppIds());
      setUnset(updateOperations, "pipelineIds", deployPref.getPipelineIds());
      setUnset(updateOperations, "workflowIds", deployPref.getWorkflowIds());
      setUnset(updateOperations, "serviceIds", deployPref.getServiceIds());
      setUnset(updateOperations, "envIds", deployPref.getEnvIds());
      setUnset(updateOperations, "status", deployPref.getStatus());
      setUnset(updateOperations, "startTime", deployPref.getStartTime());
      setUnset(updateOperations, "endTime", deployPref.getEndTime());
      setUnset(updateOperations, "keywords", deployPref.getKeywords());
      setUnset(updateOperations, "includeIndirectExecutions", deployPref.isIncludeIndirectExecutions());
      setUnset(updateOperations, "harnessTagFilter", deployPref.getHarnessTagFilter());

      update(accountId, userId, preferenceId, updateOperations);
    } else if (preference instanceof AuditPreference) {
      AuditPreference auditPreference = (AuditPreference) preference;

      UpdateOperations<Preference> updateOperations = wingsPersistence.createUpdateOperations(Preference.class);
      // Set fields to update
      setUnset(updateOperations, AuditPreferenceKeys.name, auditPreference.getName());
      setUnset(updateOperations, AuditPreferenceKeys.startTime, auditPreference.getStartTime());
      setUnset(updateOperations, AuditPreferenceKeys.endTime, auditPreference.getEndTime());
      setUnset(updateOperations, AuditPreferenceKeys.lastNDays, auditPreference.getLastNDays());
      setUnset(updateOperations, AuditPreferenceKeys.createdByUserIds, auditPreference.getCreatedByUserIds());
      setUnset(updateOperations, AuditPreferenceKeys.operationTypes, auditPreference.getOperationTypes());
      setUnset(updateOperations, AuditPreferenceKeys.includeAccountLevelResources,
          auditPreference.isIncludeAccountLevelResources());
      setUnset(
          updateOperations, AuditPreferenceKeys.includeAppLevelResources, auditPreference.isIncludeAppLevelResources());
      setUnset(updateOperations, AuditPreferenceKeys.accountAuditFilter, auditPreference.getAccountAuditFilter());
      setUnset(
          updateOperations, AuditPreferenceKeys.applicationAuditFilter, auditPreference.getApplicationAuditFilter());

      update(accountId, userId, preferenceId, updateOperations);
    }

    // Return updated preference
    return wingsPersistence.get(Preference.class, PreferenceKeys.uuid);
  }

  private void update(
      String accountId, String userId, String preferenceId, UpdateOperations<Preference> updateOperations) {
    wingsPersistence.update(wingsPersistence.createQuery(Preference.class)
                                .filter(PreferenceKeys.accountId, accountId)
                                .filter(PreferenceKeys.userId, userId)
                                .filter(PreferenceKeys.uuid, preferenceId),
        updateOperations);
  }

  @Override
  public void delete(String accountId, String userId, String preferenceId) {
    Preference preference = get(accountId, userId, preferenceId);
    if (preference == null) {
      throw new InvalidRequestException("Permission is insufficient to delete this filter", USER);
    }

    if (preference.getUserGroupsIdToShare() == null
        || (featureFlagService.isEnabled(SPG_ENABLE_SHARING_FILTERS, accountId)
            && hasUserGroupAdmin(accountId, userId))) {
      wingsPersistence.delete(wingsPersistence.createQuery(Preference.class)
                                  .filter(PreferenceKeys.accountId, accountId)
                                  .filter(PreferenceKeys.userId, userId)
                                  .filter(PreferenceKeys.uuid, preferenceId));
    }
  }

  // this will return a Tag in the format "label" or "key:value" based on the filter
  private String constructUiDisplayTagString(HarnessTagFilter harnessTagFilter) {
    if (harnessTagFilter != null) {
      List<TagFilterCondition> conditions = harnessTagFilter.getConditions();
      StringBuilder stringBuilder = new StringBuilder();
      if (isNotEmpty(conditions)) {
        stringBuilder.append(conditions.get(0).getName());
        if (isNotEmpty(conditions.get(0).getValues())) {
          stringBuilder.append(':');
          stringBuilder.append(conditions.get(0).getValues().get(0));
        }
        return stringBuilder.toString();
      }
    }
    return null;
  }
}
