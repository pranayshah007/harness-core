/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.helpers;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.freeze.beans.EntityConfig;
import io.harness.freeze.beans.FilterType;
import io.harness.freeze.beans.FreezeEntityRule;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.beans.PermissionTypes;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.PrincipalType;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.java.Log;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@UtilityClass
@Log
public class FreezeRBACHelper {
  private static final String DEPLOYMENTFREEZE = "DEPLOYMENTFREEZE";
  public void checkAccess(
      String accountId, String projectId, String orgId, String yaml, AccessControlClient accessControlClient) {
    FreezeConfig freezeConfig = NGFreezeDtoMapper.toFreezeConfig(yaml);
    if (freezeConfig.getFreezeInfoConfig() == null) {
      return;
    }
    List<FreezeEntityRule> freezeEntityRules = freezeConfig.getFreezeInfoConfig().getRules();
    for (FreezeEntityRule freezeEntityRule : freezeEntityRules) {
      if (freezeEntityRule.getEntityConfigList() == null) {
        continue;
      }
      List<EntityConfig> entityConfigList = freezeEntityRule.getEntityConfigList();
      for (EntityConfig entityConfig : entityConfigList) {
        if (entityConfig.getFilterType().equals(FilterType.EQUALS)) {
          FreezeEntityType entityType = entityConfig.getFreezeEntityType();
          Optional<Pair<String, String>> resourceAndPermission = getResourceTypeAndPermission(entityType);
          if (resourceAndPermission.isPresent()) {
            List<String> referenceList = entityConfig.getEntityReference();
            for (String reference : referenceList) {
              accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
                  Resource.of(resourceAndPermission.get().getKey(), reference), resourceAndPermission.get().getValue());
            }
          }
        }
      }
    }
  }

  public boolean checkIfUserHasFreezeOverrideAccessWithoutPrincipal(NGFeatureFlagHelperService featureFlagHelperService,
      String accountId, String projectId, String orgId, AccessControlClient accessControlClient) {
    Principal principal = getPrincipalInfoFromSecurityContext();
    PrincipalType principalType = getPrincipalTypeFromSecurityContext(principal);
    return checkIfUserHasFreezeOverrideAccess(featureFlagHelperService, accountId, orgId, projectId,
        accessControlClient,
        io.harness.accesscontrol.acl.api.Principal.of(
            convertToAccessControlPrincipalType(principalType), principal.getName()));
  }

  public boolean checkIfUserHasFreezeOverrideAccess(NGFeatureFlagHelperService featureFlagHelperService,
      String accountId, String orgId, String projectId, AccessControlClient accessControlClient) {
    Resource resource = Resource.of(DEPLOYMENTFREEZE, null);
    boolean overrideAccess = accessControlClient.hasAccess(
        ResourceScope.of(accountId, orgId, projectId), resource, PermissionTypes.DEPLOYMENT_FREEZE_OVERRIDE_PERMISSION);
    if (overrideAccess) {
      log.info("User had deployment freezeOverride Access");
    }
    return overrideAccess;
  }

  public boolean checkIfUserHasFreezeOverrideAccess(NGFeatureFlagHelperService featureFlagHelperService,
      String accountId, String orgId, String projectId, AccessControlClient accessControlClient,
      io.harness.accesscontrol.acl.api.Principal principal) {
    if (principal == null
        || io.harness.accesscontrol.principals.PrincipalType.SERVICE.equals(principal.getPrincipalType())) {
      return false;
    }
    Resource resource = Resource.of(DEPLOYMENTFREEZE, null);
    boolean overrideAccess = accessControlClient.hasAccess(
        io.harness.accesscontrol.acl.api.Principal.of(principal.getPrincipalType(), principal.getPrincipalIdentifier()),
        ResourceScope.of(accountId, orgId, projectId), resource, PermissionTypes.DEPLOYMENT_FREEZE_OVERRIDE_PERMISSION);
    if (overrideAccess) {
      log.info("User had deployment freezeOverride Access");
    }
    return overrideAccess;
  }

  public Optional<Pair<String, String>> getResourceTypeAndPermission(FreezeEntityType type) {
    Pair<String, String> result = null;
    switch (type) {
      case SERVICE: {
        result = MutablePair.of("SERVICE", "core_service_view");
        break;
      }
      case PROJECT: {
        result = MutablePair.of("PROJECT", "core_project_view");
        break;
      }
      case ORG: {
        result = MutablePair.of("ORGANIZATION", "core_organization_view");
        break;
      }
      case ENVIRONMENT: {
        result = MutablePair.of("ENVIRONMENT", "core_environment_view");
        break;
      }
      case PIPELINE: {
        result = MutablePair.of("PIPELINE", "core_pipeline_view");
        break;
      }
      default: {
        break;
      }
    }
    return Optional.ofNullable(result);
  }

  protected Principal getPrincipalInfoFromSecurityContext() {
    Principal principalInContext = SecurityContextBuilder.getPrincipal();
    if (principalInContext == null || principalInContext.getName() == null || principalInContext.getType() == null) {
      throw new AccessDeniedException("Principal cannot be null", ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
    return principalInContext;
  }
  private PrincipalType getPrincipalTypeFromSecurityContext(Principal principal) {
    String principalName = principal.getName();
    if (EmptyPredicate.isEmpty(principalName)) {
      return null;
    }
    return principal.getType();
  }

  public io.harness.accesscontrol.principals.PrincipalType convertToAccessControlPrincipalType(
      PrincipalType principalType) {
    switch (principalType) {
      case USER:
        return io.harness.accesscontrol.principals.PrincipalType.USER;
      case SERVICE:
        return io.harness.accesscontrol.principals.PrincipalType.SERVICE;
      case API_KEY:
        return io.harness.accesscontrol.principals.PrincipalType.API_KEY;
      case SERVICE_ACCOUNT:
        return io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
      default:
        throw new InvalidRequestException("Unknown principal type found");
    }
  }
}
