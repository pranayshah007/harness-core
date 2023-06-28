/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.persistence;

import static io.harness.accesscontrol.acl.persistence.ACL.getAclQueryString;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.acl.PermissionCheck;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeLevel;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@Singleton
@Slf4j
public class ACLDAOImpl implements ACLDAO {
  private static final String PATH_DELIMITER = "/";
  private static final String ALL_RESOURCES_IDENTIFIER = "*";
  private static final String INCLUDE_CHILD_SCOPES_IDENTIFIER = "**";
  private final ACLRepository aclRepository;
  private final Set<String> scopeResourceTypes;
  private final boolean disableRedundantACLs;

  @Inject
  public ACLDAOImpl(@Named(ACL.PRIMARY_COLLECTION) ACLRepository aclRepository, Map<String, ScopeLevel> scopeLevels,
      @Named("disableRedundantACLs") boolean disableRedundantACLs) {
    this.aclRepository = aclRepository;
    this.scopeResourceTypes =
        scopeLevels.values().stream().map(ScopeLevel::getResourceType).collect(Collectors.toSet());
    this.disableRedundantACLs = disableRedundantACLs;
  }

  private String getResourceSelector(String resourceType, String resourceIdentifier) {
    return PATH_DELIMITER.concat(resourceType).concat(PATH_DELIMITER).concat(resourceIdentifier);
  }

  private String getIncludeChildScopesResourceSelector(String resourceType) {
    return PATH_DELIMITER.concat(INCLUDE_CHILD_SCOPES_IDENTIFIER)
        .concat(PATH_DELIMITER)
        .concat(resourceType)
        .concat(PATH_DELIMITER)
        .concat(ALL_RESOURCES_IDENTIFIER);
  }

  private boolean isValidPermissionCheckForSameScopeLevel(PermissionCheck permissionCheck) {
    String resourceType = permissionCheck.getResourceType();
    String resourceIdentifier = permissionCheck.getResourceIdentifier();
    if (!scopeResourceTypes.contains(resourceType)) {
      return true;
    }
    if (permissionCheck.getResourceScope() == null) {
      return true;
    }
    if (resourceType.equalsIgnoreCase(permissionCheck.getResourceScope().getLevel().toString())) {
      return resourceIdentifier == null
          || resourceIdentifier.equals(permissionCheck.getResourceScope().getInstanceId());
    }
    return false;
  }

  @Override
  public Set<String> getQueryStrings(PermissionCheck permissionCheck, Principal principal) {
    String scope =
        Optional.ofNullable(permissionCheck.getResourceScope()).flatMap(rs -> Optional.of(rs.toString())).orElse("");
    Set<String> queryStrings = new HashSet<>();
    String resourceType = permissionCheck.getResourceType();
    String resourceIdentifier = permissionCheck.getResourceIdentifier();

    // query for resource=/RESOURCE_TYPE/{resourceIdentifier} in given scope
    if (!StringUtils.isEmpty(resourceIdentifier)) {
      queryStrings.add(getAclQueryString(scope, getResourceSelector(resourceType, resourceIdentifier),
          principal.getPrincipalType().name(), principal.getPrincipalIdentifier(), permissionCheck.getPermission()));
    }

    if (isValidPermissionCheckForSameScopeLevel(permissionCheck)) {
      // query for resource=/RESOURCE_TYPE/* in given scope
      queryStrings.add(getAclQueryString(scope, getResourceSelector(resourceType, ALL_RESOURCES_IDENTIFIER),
          principal.getPrincipalType().name(), principal.getPrincipalIdentifier(), permissionCheck.getPermission()));

      // query for resource=/*/* in given scope
      queryStrings.add(getAclQueryString(scope, getResourceSelector(ALL_RESOURCES_IDENTIFIER, ALL_RESOURCES_IDENTIFIER),
          principal.getPrincipalType().name(), principal.getPrincipalIdentifier(), permissionCheck.getPermission()));
    }

    Scope currentScope = permissionCheck.getResourceScope();
    while (currentScope != null) {
      // query for resource=/**/RESOURCE_TYPE/* in given scope
      queryStrings.add(getAclQueryString(currentScope.toString(), getIncludeChildScopesResourceSelector(resourceType),
          principal.getPrincipalType().name(), principal.getPrincipalIdentifier(), permissionCheck.getPermission()));

      // query for resource=/**/*/* in given scope
      queryStrings.add(getAclQueryString(currentScope.toString(),
          getIncludeChildScopesResourceSelector(ALL_RESOURCES_IDENTIFIER), principal.getPrincipalType().name(),
          principal.getPrincipalIdentifier(), permissionCheck.getPermission()));

      currentScope = currentScope.getParentScope();
    }

    // if RESOURCE_TYPE is a scope, query for scope = {given_scope}/RESOURCE_TYPE/{resourceIdentifier}
    if (scopeResourceTypes.contains(resourceType)) {
      if (resourceIdentifier != null) {
        scope = scope.concat(PATH_DELIMITER + resourceType + PATH_DELIMITER + resourceIdentifier);
      }

      // and resource = /RESOURCE_TYPE/{resourceIdentifier}
      if (!StringUtils.isEmpty(resourceIdentifier)) {
        queryStrings.add(getAclQueryString(scope, getResourceSelector(resourceType, resourceIdentifier),
            principal.getPrincipalType().name(), principal.getPrincipalIdentifier(), permissionCheck.getPermission()));
      }

      // and resource = /RESOURCE_TYPE/*
      queryStrings.add(getAclQueryString(scope, getResourceSelector(resourceType, ALL_RESOURCES_IDENTIFIER),
          principal.getPrincipalType().name(), principal.getPrincipalIdentifier(), permissionCheck.getPermission()));

      // and resource = /*/*
      queryStrings.add(getAclQueryString(scope, getResourceSelector(ALL_RESOURCES_IDENTIFIER, ALL_RESOURCES_IDENTIFIER),
          principal.getPrincipalType().name(), principal.getPrincipalIdentifier(), permissionCheck.getPermission()));

      // query for resource=/**/RESOURCE_TYPE/* in given scope
      queryStrings.add(getAclQueryString(scope, getIncludeChildScopesResourceSelector(resourceType),
          principal.getPrincipalType().name(), principal.getPrincipalIdentifier(), permissionCheck.getPermission()));

      // query for resource=/**/*/* in given scope
      queryStrings.add(getAclQueryString(scope, getIncludeChildScopesResourceSelector(ALL_RESOURCES_IDENTIFIER),
          principal.getPrincipalType().name(), principal.getPrincipalIdentifier(), permissionCheck.getPermission()));
    }
    return queryStrings;
  }

  @Override
  public List<List<ACL>> getMatchingACLs(Principal principal, List<PermissionCheck> permissionChecks) {
    List<Set<String>> aclQueryStringsPerPermission = new ArrayList<>();
    Set<String> aclQueryStrings = new HashSet<>();
    permissionChecks.forEach(permissionCheck -> {
      Set<String> queryStrings = getQueryStrings(permissionCheck, principal);
      aclQueryStringsPerPermission.add(queryStrings);
      aclQueryStrings.addAll(queryStrings);
    });

    List<ACL> aclsPresentInDB = disableRedundantACLs
        ? aclRepository.getByAclQueryStringInAndEnabled(aclQueryStrings, true)
        : aclRepository.getByAclQueryStringIn(aclQueryStrings);
    return aclQueryStringsPerPermission.stream()
        .map(queryStringsForPermission
            -> aclsPresentInDB.stream()
                   .filter(acl -> queryStringsForPermission.contains(acl.getAclQueryString()))
                   .collect(Collectors.toList()))
        .collect(Collectors.toList());
  }
}
