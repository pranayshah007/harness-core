/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl;

import static io.harness.accesscontrol.permissions.PermissionStatus.EXPERIMENTAL;
import static io.harness.accesscontrol.permissions.PermissionStatus.INACTIVE;
import static io.harness.accesscontrol.permissions.PermissionStatus.STAGING;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.EnumUtils.isValidEnum;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;

import io.harness.accesscontrol.ResourceInfo;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.conditions.ACLExpressionEvaluatorProvider;
import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.ACLDAO;
import io.harness.accesscontrol.permissions.Permission;
import io.harness.accesscontrol.permissions.PermissionFilter;
import io.harness.accesscontrol.permissions.PermissionService;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupDBO;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupDBO.UserGroupDBOKeys;
import io.harness.accesscontrol.resources.resourcegroups.ResourceSelector;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentAggregateDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeLevel;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Singleton
@ValidateOnExecution
@Slf4j
public class ACLServiceImpl implements ACLService {
  private final ACLDAO aclDAO;
  private final PermissionService permissionService;
  private static final PermissionFilter permissionFilter =
      PermissionFilter.builder().statusFilter(Sets.newHashSet(INACTIVE, EXPERIMENTAL, STAGING)).build();
  private volatile Set<String> disabledPermissions;
  private final ACLExpressionEvaluatorProvider aclExpressionEvaluatorProvider;
  private final MongoTemplate mongoTemplate;
  private final InMemoryPermissionRepository inMemoryPermissionRepository;

  @Inject
  public ACLServiceImpl(ACLDAO aclDAO, PermissionService permissionService,
      ACLExpressionEvaluatorProvider aclExpressionEvaluatorProvider, MongoTemplate mongoTemplate,
      InMemoryPermissionRepository inMemoryPermissionRepository) {
    this.aclDAO = aclDAO;
    this.permissionService = permissionService;
    this.aclExpressionEvaluatorProvider = aclExpressionEvaluatorProvider;
    this.mongoTemplate = mongoTemplate;
    this.inMemoryPermissionRepository = inMemoryPermissionRepository;
  }

  private PermissionCheckResult getPermissionCheckResult(PermissionCheck permissionCheck, boolean permitted) {
    return PermissionCheckResult.builder()
        .permission(permissionCheck.getPermission())
        .resourceIdentifier(permissionCheck.getResourceIdentifier())
        .resourceAttributes(permissionCheck.getResourceAttributes())
        .resourceScope(permissionCheck.getResourceScope())
        .resourceType(permissionCheck.getResourceType())
        .permitted(permitted)
        .build();
  }

  private Set<ResourceSelector> getResourceSelectorsFromRoleAssignment(ResourceGroupDBO resourceGroup) {
    Set<ResourceSelector> resourceSelectors = new HashSet<>();
    if (resourceGroup.getResourceSelectors() != null) {
      resourceSelectors.addAll(resourceGroup.getResourceSelectors()
                                   .stream()
                                   .map(selector -> ResourceSelector.builder().selector(selector).build())
                                   .collect(Collectors.toList()));
    }
    if (resourceGroup.getResourceSelectorsV2() != null) {
      resourceSelectors.addAll(resourceGroup.getResourceSelectorsV2());
    }
    return resourceSelectors;
  }

  private List<String> getACLsForPrincipal(
      Principal principal, Criteria principalCriteria, List<PermissionCheck> permissionChecks) {
    List<String> permissions =
        permissionChecks.stream().map(PermissionCheck::getPermission).collect(Collectors.toList());
    principalCriteria.and(RoleAssignmentDBOKeys.disabled).is(false);
    MatchOperation match = Aggregation.match(principalCriteria);
    Criteria permissionCriteria = Criteria.where("roleInfo.permissions").in(permissions);
    MatchOperation permissionMatch = Aggregation.match(permissionCriteria);
    Aggregation aggregation = newAggregation(match,
        Aggregation.lookup("resourcegroups", "resourceGroupIdentifier", "identifier", "resourceGroupInfo"),
        Aggregation.lookup("roles", "roleIdentifier", "identifier", "roleInfo"),
        Aggregation.unwind("resourceGroupInfo"), Aggregation.unwind("roleInfo"),
        Aggregation.unwind("roleInfo.permissions"), permissionMatch,
        Aggregation.project("resourceGroupInfo", "scopeIdentifier")
            .andExpression("roleInfo.permissions")
            .as("permission"));
    AggregationResults<RoleAssignmentAggregateDBO> results =
        mongoTemplate.aggregate(aggregation, RoleAssignmentDBO.class, RoleAssignmentAggregateDBO.class);

    List<String> acls = new ArrayList<>();
    for (RoleAssignmentAggregateDBO result : results) {
      for (ResourceSelector resourceSelector : getResourceSelectorsFromRoleAssignment(result.getResourceGroupInfo())) {
        String scopeIdentifier, selector;
        if (resourceSelector.getSelector().contains("$")) {
          scopeIdentifier = resourceSelector.getSelector().split("\\$")[0];
          selector = resourceSelector.getSelector().split("\\$")[1];
        } else {
          scopeIdentifier = result.getScopeIdentifier();
          selector = resourceSelector.getSelector();
        }
        acls.add(ACL.getAclQueryString(scopeIdentifier, selector, principal.getPrincipalType().name(),
            principal.getPrincipalIdentifier(), result.getPermission()));
      }
    }
    return acls;
  }

  @Override
  public List<PermissionCheckResult> checkAccessUsingRoleAssignments(String accountIdentifier, Principal principal,
      List<PermissionCheck> permissionChecks, ResourceAttributeProvider resourceAttributeProvider) {
    Criteria principalCriteria = new Criteria();
    List<String> acls = new ArrayList<>();
    String accountScope = "/ACCOUNT/" + accountIdentifier;
    if (PrincipalType.USER.equals(principal.getPrincipalType())) {
      List<Criteria> principals = new ArrayList<>();
      Criteria userCriteria = Criteria.where(RoleAssignmentDBOKeys.principalIdentifier)
                                  .is(principal.getPrincipalIdentifier())
                                  .and(RoleAssignmentDBOKeys.principalType)
                                  .is(PrincipalType.USER)
                                  .and(RoleAssignmentDBOKeys.scopeIdentifier)
                                  .regex(accountScope);
      principals.add(userCriteria);
      Aggregation aggregation = newAggregation(Aggregation.match(Criteria.where(UserGroupDBOKeys.users)
                                                                     .in(principal.getPrincipalIdentifier())
                                                                     .and(UserGroupDBOKeys.scopeIdentifier)
                                                                     .regex(accountScope)),
          Aggregation.project("identifier", "scopeIdentifier"));
      AggregationResults<UserGroupDBO> results =
          mongoTemplate.aggregate(aggregation, UserGroupDBO.class, UserGroupDBO.class);
      results.forEach(userGroup
          -> principals.add(Criteria.where(RoleAssignmentDBOKeys.principalIdentifier)
                                .is(userGroup.getIdentifier())
                                .and(RoleAssignmentDBOKeys.principalType)
                                .is(PrincipalType.USER_GROUP)
                                .and(RoleAssignmentDBOKeys.scopeIdentifier)
                                .regex(userGroup.getScopeIdentifier())));
      principalCriteria.orOperator(principals.toArray(new Criteria[0]));
      acls = getACLsForPrincipal(principal, principalCriteria, permissionChecks);
    } else if (PrincipalType.SERVICE_ACCOUNT.equals(principal.getPrincipalType())) {
      acls = getACLsForPrincipal(principal,
          Criteria.where(RoleAssignmentDBOKeys.principalIdentifier)
              .is(principal.getPrincipalIdentifier())
              .and(RoleAssignmentDBOKeys.principalType)
              .is(PrincipalType.SERVICE_ACCOUNT)
              .and(RoleAssignmentDBOKeys.scopeIdentifier)
              .regex(accountScope),
          permissionChecks);
    }

    List<PermissionCheckResult> permissionCheckResults = new ArrayList<>();
    ensureDisabledPermissions();
    for (int i = 0; i < permissionChecks.size(); i++) {
      PermissionCheck permissionCheck = permissionChecks.get(i);
      if (disabledPermissions.contains(permissionCheck.getPermission())) {
        permissionCheckResults.add(getPermissionCheckResult(permissionCheck, true));
      } else {
        Set<String> queryStrings = aclDAO.getQueryStrings(permissionCheck, principal);
        boolean permitted = queryStrings.stream().anyMatch(acls::contains);
        permissionCheckResults.add(getPermissionCheckResult(permissionCheck, permitted));
      }
    }

    return permissionCheckResults;
  }

  @Override
  public List<PermissionCheckResult> checkAccess(Principal principal, List<PermissionCheck> permissionChecks,
      ResourceAttributeProvider resourceAttributeProvider) {
    permissionChecks.stream()
        .filter(permissionCheck -> !isValidEnum(ScopeLevel.class, permissionCheck.getResourceType()))
        .filter(permissionCheck
            -> !nonNull(
                   inMemoryPermissionRepository.getResourceTypesApplicableToPermission(permissionCheck.getPermission()))
                || !inMemoryPermissionRepository.getResourceTypesApplicableToPermission(permissionCheck.getPermission())
                        .contains(permissionCheck.getResourceType()))
        .forEach(permissionCheck
            -> log.debug("Access check requested for redundant combination of resource : {} with permission : {}",
                permissionCheck.getResourceType(), permissionCheck.getPermission()));

    List<List<ACL>> matchingACLs = aclDAO.getMatchingACLs(principal, permissionChecks);
    List<Boolean> allowedAccessList = checkAccessInternal(permissionChecks, matchingACLs, resourceAttributeProvider);

    List<PermissionCheckResult> permissionCheckResults = new ArrayList<>();
    ensureDisabledPermissions();
    for (int i = 0; i < permissionChecks.size(); i++) {
      PermissionCheck permissionCheck = permissionChecks.get(i);
      if (disabledPermissions.contains(permissionCheck.getPermission())) {
        permissionCheckResults.add(getPermissionCheckResult(permissionCheck, true));
      } else {
        permissionCheckResults.add(getPermissionCheckResult(permissionCheck, allowedAccessList.get(i)));
      }
    }

    return permissionCheckResults;
  }

  private List<Boolean> checkAccessInternal(List<PermissionCheck> permissionChecks, List<List<ACL>> matchedACLs,
      ResourceAttributeProvider resourceAttributeProvider) {
    Set<ResourceInfo> resourcesWhoseAttributesAreRequired = new HashSet<>();
    Boolean[] accessCheckResults = new Boolean[permissionChecks.size()];
    Map<ResourceInfo, Map<String, String>> attributesProvidedByClient = new HashMap<>();
    for (int i = 0; i < permissionChecks.size(); i++) {
      if (matchedACLs.get(i).isEmpty()) {
        accessCheckResults[i] = Boolean.FALSE;
      } else if (matchedACLs.get(i).stream().anyMatch(acl -> !acl.isConditional())) {
        accessCheckResults[i] = Boolean.TRUE;
      } else if (isNotEmpty(permissionChecks.get(i).getResourceIdentifier())
          || isNotEmpty(permissionChecks.get(i).getResourceAttributes())) {
        resourcesWhoseAttributesAreRequired.add(permissionChecks.get(i).getResourceInfo());
      } else {
        accessCheckResults[i] = Boolean.FALSE;
      }
    }

    Map<ResourceInfo, Map<String, String>> attributes = null;
    try {
      attributes = resourceAttributeProvider.getAttributes(resourcesWhoseAttributesAreRequired);
    } catch (Exception ex) {
      log.error("Exception occurred fetching attributes for {}", permissionChecks, ex);
    }

    for (int i = 0; i < permissionChecks.size(); i++) {
      if (accessCheckResults[i] == null) {
        accessCheckResults[i] = attributes != null ? evaluateAccessFromConditionalACLs(permissionChecks.get(i),
                                    matchedACLs.get(i), attributes.get(permissionChecks.get(i).getResourceInfo()))
                                                   : Boolean.FALSE;
      }
    }

    return Arrays.stream(accessCheckResults).collect(Collectors.toList());
  }

  private Boolean evaluateAccessFromConditionalACLs(
      PermissionCheck permissionCheck, List<ACL> matchedACLs, Map<String, String> resourceAttributes) {
    return matchedACLs.stream()
        .filter(ACL::isConditional)
        .anyMatch(acl
            -> aclExpressionEvaluatorProvider.get(permissionCheck, resourceAttributes)
                   .evaluateExpression(acl.getCondition()));
  }

  private void ensureDisabledPermissions() {
    if (disabledPermissions == null) {
      updateDisabledPermissions();
    }
  }

  private void updateDisabledPermissions() {
    disabledPermissions =
        permissionService.list(permissionFilter).stream().map(Permission::getIdentifier).collect(Collectors.toSet());
  }
}
