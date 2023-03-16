/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.persistence;

import static io.harness.accesscontrol.common.filter.ManagedFilter.ONLY_CUSTOM;
import static io.harness.accesscontrol.common.filter.ManagedFilter.ONLY_MANAGED;
import static io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBOMapper.fromDBO;
import static io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBOMapper.toDBO;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@ValidateOnExecution
@Singleton
@Slf4j
public class RoleAssignmentDaoImpl implements RoleAssignmentDao {
  private final RoleAssignmentRepository roleAssignmentRepository;

  @Inject
  public RoleAssignmentDaoImpl(RoleAssignmentRepository roleAssignmentRepository) {
    this.roleAssignmentRepository = roleAssignmentRepository;
  }

  @Override
  public RoleAssignment create(RoleAssignment roleAssignment) {
    RoleAssignmentDBO roleAssignmentDBO = toDBO(roleAssignment);
    try {
      return fromDBO(roleAssignmentRepository.save(roleAssignmentDBO));
    } catch (DuplicateKeyException e) {
      throw new DuplicateFieldException(String.format(
          "A role assignment with the same resource group, role and principal is already present in the scope %s",
          roleAssignmentDBO.getScopeIdentifier()));
    }
  }

  @Override
  public PageResponse<RoleAssignment> list(
      PageRequest pageRequest, RoleAssignmentFilter roleAssignmentFilter, boolean hideInternal) {
    Pageable pageable = PageUtils.getPageRequest(pageRequest);
    Criteria criteria = createCriteriaFromFilter(roleAssignmentFilter, hideInternal);
    Page<RoleAssignmentDBO> assignmentPage = roleAssignmentRepository.findAll(criteria, pageable);
    return PageUtils.getNGPageResponse(assignmentPage.map(RoleAssignmentDBOMapper::fromDBO));
  }

  @Override
  public Optional<RoleAssignment> get(String identifier, String scopeIdentifier) {
    Optional<RoleAssignmentDBO> roleAssignment =
        roleAssignmentRepository.findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier);
    return roleAssignment.flatMap(r -> Optional.of(RoleAssignmentDBOMapper.fromDBO(r)));
  }

  @Override
  public RoleAssignment update(RoleAssignment roleAssignmentUpdate) {
    Optional<RoleAssignmentDBO> roleAssignmentDBOOptional = roleAssignmentRepository.findByIdentifierAndScopeIdentifier(
        roleAssignmentUpdate.getIdentifier(), roleAssignmentUpdate.getScopeIdentifier());
    if (roleAssignmentDBOOptional.isPresent()) {
      RoleAssignmentDBO roleAssignmentUpdateDBO = toDBO(roleAssignmentUpdate);
      roleAssignmentUpdateDBO.setId(roleAssignmentDBOOptional.get().getId());
      roleAssignmentUpdateDBO.setCreatedAt(roleAssignmentDBOOptional.get().getCreatedAt());
      roleAssignmentUpdateDBO.setLastModifiedAt(roleAssignmentDBOOptional.get().getLastModifiedAt());
      return fromDBO(roleAssignmentRepository.save(roleAssignmentUpdateDBO));
    }
    throw new InvalidRequestException(
        String.format("Could not find the role assignment in the scope %s", roleAssignmentUpdate.getScopeIdentifier()));
  }

  @Override
  public Optional<RoleAssignment> delete(String identifier, String scopeIdentifier) {
    Optional<RoleAssignmentDBO> optionalRoleAssignmentDBO =
        roleAssignmentRepository.deleteByIdentifierAndScopeIdentifier(identifier, scopeIdentifier);
    if (optionalRoleAssignmentDBO.isPresent()) {
      log.info("The role assignment is being deleted, {}", optionalRoleAssignmentDBO.get());
      return Optional.of(RoleAssignmentDBOMapper.fromDBO(optionalRoleAssignmentDBO.get()));
    }
    return Optional.empty();
  }

  @Override
  public long deleteMulti(RoleAssignmentFilter roleAssignmentFilter) {
    return roleAssignmentRepository.deleteMulti(createCriteriaFromFilter(roleAssignmentFilter, false));
  }

  @Override
  public long deleteMulti(String scopeIdentifier, List<String> identifiers) {
    return roleAssignmentRepository.deleteMulti(createCriteriaForBulkDelete(scopeIdentifier, identifiers));
  }

  private Criteria createCriteriaForBulkDelete(String scopeIdentifier, List<String> roleAssignmentThatCanBeDeleted) {
    return Criteria.where(RoleAssignmentDBOKeys.scopeIdentifier)
        .is(scopeIdentifier)
        .and(RoleAssignmentDBOKeys.identifier)
        .in(roleAssignmentThatCanBeDeleted);
  }

  private Criteria createCriteriaFromFilter(RoleAssignmentFilter roleAssignmentFilter, boolean hideInternal) {
    Criteria criteria = new Criteria();

    List<Criteria> scopeCriteria =
        roleAssignmentFilter.getScopeFilters()
            .stream()
            .map(scopeFilter -> {
              if (!scopeFilter.isIncludeChildScopes()) {
                return Criteria.where(RoleAssignmentDBOKeys.scopeIdentifier).is(scopeFilter.getScope());
              } else {
                Pattern startsWithScope = Pattern.compile("^".concat(scopeFilter.getScope()));
                return Criteria.where(RoleAssignmentDBOKeys.scopeIdentifier).regex(startsWithScope);
              }
            })
            .collect(Collectors.toList());

    if (!isEmpty(roleAssignmentFilter.getScopeFilter())) {
      if (!roleAssignmentFilter.isIncludeChildScopes()) {
        scopeCriteria.add(
            Criteria.where(RoleAssignmentDBOKeys.scopeIdentifier).is(roleAssignmentFilter.getScopeFilter()));
      } else {
        Pattern startsWithScope = Pattern.compile("^".concat(roleAssignmentFilter.getScopeFilter()));
        scopeCriteria.add(Criteria.where(RoleAssignmentDBOKeys.scopeIdentifier).regex(startsWithScope));
      }
    }

    if (!roleAssignmentFilter.getScopeLevelFilter().isEmpty()) {
      criteria.and(RoleAssignmentDBOKeys.scopeLevel).in(roleAssignmentFilter.getScopeLevelFilter());
    }

    if (!roleAssignmentFilter.getRoleFilter().isEmpty()) {
      criteria.and(RoleAssignmentDBOKeys.roleIdentifier).in(roleAssignmentFilter.getRoleFilter());
    }

    if (!roleAssignmentFilter.getResourceGroupFilter().isEmpty()) {
      criteria.and(RoleAssignmentDBOKeys.resourceGroupIdentifier).in(roleAssignmentFilter.getResourceGroupFilter());
    }

    if (ONLY_CUSTOM.equals(roleAssignmentFilter.getManagedFilter())) {
      criteria.and(RoleAssignmentDBOKeys.managed).in(false, null);
    } else if (ONLY_MANAGED.equals(roleAssignmentFilter.getManagedFilter())) {
      criteria.and(RoleAssignmentDBOKeys.managed).is(true);
    }

    if (!roleAssignmentFilter.getDisabledFilter().isEmpty()) {
      criteria.and(RoleAssignmentDBOKeys.disabled).in(roleAssignmentFilter.getDisabledFilter());
    }

    if (!roleAssignmentFilter.getPrincipalTypeFilter().isEmpty()
        || !roleAssignmentFilter.getPrincipalScopeLevelFilter().isEmpty()) {
      if (!roleAssignmentFilter.getPrincipalTypeFilter().isEmpty()) {
        criteria.and(RoleAssignmentDBOKeys.principalType).in(roleAssignmentFilter.getPrincipalTypeFilter());
      }
      if (!roleAssignmentFilter.getPrincipalScopeLevelFilter().isEmpty()) {
        criteria.and(RoleAssignmentDBOKeys.principalScopeLevel).in(roleAssignmentFilter.getPrincipalScopeLevelFilter());
      }
    }

    if (hideInternal) {
      criteria.and(RoleAssignmentDBOKeys.internal).ne(true);
    }

    Criteria[] principalCriteria = roleAssignmentFilter.getPrincipalFilter()
                                       .stream()
                                       .map(principal
                                           -> Criteria.where(RoleAssignmentDBOKeys.principalIdentifier)
                                                  .is(principal.getPrincipalIdentifier())
                                                  .and(RoleAssignmentDBOKeys.principalType)
                                                  .is(principal.getPrincipalType())
                                                  .and(RoleAssignmentDBOKeys.principalScopeLevel)
                                                  .is(principal.getPrincipalScopeLevel()))
                                       .toArray(Criteria[] ::new);

    Criteria criteria1 = new Criteria().orOperator(scopeCriteria.toArray(Criteria[] ::new));
    List<Criteria> criteria3 = new ArrayList<>();
    criteria3.add(criteria);
    if (principalCriteria.length != 0) {
      criteria3.add(new Criteria().orOperator(principalCriteria));
    }
    return scopeCriteria.isEmpty() ? new Criteria().andOperator(criteria3.toArray(Criteria[] ::new))
                                   : criteria1.andOperator(criteria3.toArray(Criteria[] ::new));
  }
}
