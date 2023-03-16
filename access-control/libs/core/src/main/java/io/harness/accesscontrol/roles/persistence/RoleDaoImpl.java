/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles.persistence;

import static io.harness.accesscontrol.common.filter.ManagedFilter.NO_FILTER;
import static io.harness.accesscontrol.common.filter.ManagedFilter.ONLY_CUSTOM;
import static io.harness.accesscontrol.common.filter.ManagedFilter.ONLY_MANAGED;
import static io.harness.accesscontrol.roles.persistence.RoleDBOMapper.fromDBO;
import static io.harness.accesscontrol.roles.persistence.RoleDBOMapper.toDBO;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.filter.RoleFilter;
import io.harness.accesscontrol.roles.persistence.RoleDBO.RoleDBOKeys;
import io.harness.accesscontrol.roles.persistence.repositories.RoleRepository;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.utils.PageUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.UpdateResult;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.validation.executable.ValidateOnExecution;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PL)
@Singleton
@ValidateOnExecution
public class RoleDaoImpl implements RoleDao {
  private final RoleRepository roleRepository;
  private final ScopeService scopeService;

  @Inject
  public RoleDaoImpl(RoleRepository roleRepository, ScopeService scopeService) {
    this.roleRepository = roleRepository;
    this.scopeService = scopeService;
  }

  @Override
  public Role create(Role role) {
    RoleDBO roleDBO = toDBO(role);
    try {
      return fromDBO(roleRepository.save(roleDBO));
    } catch (DuplicateKeyException e) {
      throw new DuplicateFieldException(String.format("A role with identifier %s in this scope %s is already present",
          roleDBO.getIdentifier(), roleDBO.getScopeIdentifier()));
    }
  }

  @Override
  public PageResponse<Role> list(PageRequest pageRequest, RoleFilter roleFilter, boolean hideInternal) {
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder managedOrder =
          SortOrder.Builder.aSortOrder().withField(RoleDBOKeys.managed, SortOrder.OrderType.DESC).build();
      SortOrder lastModifiedOrder =
          SortOrder.Builder.aSortOrder().withField(RoleDBOKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(managedOrder, lastModifiedOrder));
    }
    Pageable pageable = PageUtils.getPageRequest(pageRequest);
    Criteria criteria = createCriteriaFromFilter(roleFilter, hideInternal);
    Page<RoleDBO> rolePages = roleRepository.findAll(criteria, pageable);
    return PageUtils.getNGPageResponse(rolePages.map(RoleDBOMapper::fromDBO));
  }

  @Override
  public Optional<Role> get(String identifier, String scopeIdentifier, ManagedFilter managedFilter) {
    return getInternal(identifier, scopeIdentifier, managedFilter).flatMap(r -> Optional.of(fromDBO(r)));
  }

  private Optional<RoleDBO> getInternal(String identifier, String scopeIdentifier, ManagedFilter managedFilter) {
    Criteria criteria = new Criteria();
    criteria.and(RoleDBOKeys.identifier).is(identifier);

    if (managedFilter.equals(NO_FILTER)) {
      criteria.and(RoleDBOKeys.scopeIdentifier).in(scopeIdentifier, null);
    } else if (managedFilter.equals(ONLY_CUSTOM)) {
      criteria.and(RoleDBOKeys.scopeIdentifier).is(scopeIdentifier);
      criteria.and(RoleDBOKeys.managed).is(false);
    } else if (managedFilter.equals(ONLY_MANAGED)) {
      criteria.and(RoleDBOKeys.scopeIdentifier).is(null);
      criteria.and(RoleDBOKeys.managed).is(true);
    }

    if (!isEmpty(scopeIdentifier)) {
      Scope scope = scopeService.buildScopeFromScopeIdentifier(scopeIdentifier);
      criteria.and(RoleDBOKeys.allowedScopeLevels).is(scope.getLevel().toString());
    }

    return roleRepository.find(criteria);
  }

  @Override
  public Role update(Role roleUpdate) {
    ManagedFilter managedFilter = roleUpdate.isManaged() ? ONLY_MANAGED : ONLY_CUSTOM;
    Optional<RoleDBO> roleDBOOptional =
        getInternal(roleUpdate.getIdentifier(), roleUpdate.getScopeIdentifier(), managedFilter);
    if (roleDBOOptional.isPresent()) {
      RoleDBO roleUpdateDBO = toDBO(roleUpdate);
      roleUpdateDBO.setId(roleDBOOptional.get().getId());
      return fromDBO(roleRepository.save(roleUpdateDBO));
    }
    throw new InvalidRequestException(
        String.format("Could not find the role in the scope %s", roleUpdate.getScopeIdentifier()));
  }

  @Override
  public Optional<Role> delete(String identifier, String scopeIdentifier, boolean managed) {
    Optional<RoleDBO> optionalRoleDBO =
        roleRepository.deleteByIdentifierAndScopeIdentifierAndManaged(identifier, scopeIdentifier, managed);
    return optionalRoleDBO.map(RoleDBOMapper::fromDBO);
  }

  @Override
  public boolean removePermissionFromRoles(String permissionIdentifier, RoleFilter roleFilter) {
    Criteria criteria = createCriteriaFromFilter(roleFilter, false);
    criteria.and(RoleDBOKeys.permissions).is(permissionIdentifier);
    Update update = new Update().pull(RoleDBOKeys.permissions, permissionIdentifier);
    UpdateResult updateResult = roleRepository.updateMulti(criteria, update);
    return updateResult.getMatchedCount() == updateResult.getModifiedCount();
  }

  @Override
  public boolean addPermissionToRoles(String permissionIdentifier, RoleFilter roleFilter) {
    Criteria criteria = createCriteriaFromFilter(roleFilter, false);
    criteria.and(RoleDBOKeys.permissions).ne(permissionIdentifier);
    Update update = new Update().push(RoleDBOKeys.permissions, permissionIdentifier);
    UpdateResult updateResult = roleRepository.updateMulti(criteria, update);
    return updateResult.getMatchedCount() == updateResult.getModifiedCount();
  }

  @Override
  public long deleteMulti(RoleFilter roleFilter) {
    Criteria criteria = createCriteriaFromFilter(roleFilter, false);
    return roleRepository.deleteMulti(criteria);
  }

  @VisibleForTesting
  protected Criteria createCriteriaFromFilter(RoleFilter roleFilter, boolean hideInternal) {
    Criteria criteria = new Criteria();
    if (isNotBlank(roleFilter.getSearchTerm())) {
      criteria.orOperator(Criteria.where(RoleDBOKeys.name).regex(roleFilter.getSearchTerm(), "i"),
          Criteria.where(RoleDBOKeys.identifier).regex(roleFilter.getSearchTerm(), "i"));
    }
    if (!roleFilter.getIdentifierFilter().isEmpty()) {
      criteria.and(RoleDBOKeys.identifier).in(roleFilter.getIdentifierFilter());
    }
    if (!roleFilter.getScopeLevelsFilter().isEmpty()) {
      criteria.and(RoleDBOKeys.allowedScopeLevels).in(roleFilter.getScopeLevelsFilter());
    }
    if (hideInternal) {
      criteria.and(RoleDBOKeys.internal).ne(true);
    }
    if (roleFilter.getManagedFilter().equals(ONLY_MANAGED)) {
      criteria.and(RoleDBOKeys.scopeIdentifier).is(null);
      criteria.and(RoleDBOKeys.managed).is(true);
    } else if (roleFilter.getManagedFilter().equals(NO_FILTER)) {
      criteria.and(RoleDBOKeys.scopeIdentifier).in(roleFilter.getScopeIdentifier(), null);
    } else if (roleFilter.getManagedFilter().equals(ONLY_CUSTOM) && roleFilter.isIncludeChildScopes()) {
      Pattern startsWithScope = Pattern.compile("^".concat(roleFilter.getScopeIdentifier()));
      criteria.and(RoleDBOKeys.scopeIdentifier).regex(startsWithScope);
      criteria.and(RoleDBOKeys.managed).is(false);
    } else if (roleFilter.getManagedFilter().equals(ONLY_CUSTOM)) {
      criteria.and(RoleDBOKeys.scopeIdentifier).is(roleFilter.getScopeIdentifier());
      criteria.and(RoleDBOKeys.managed).is(false);
    }
    if (isNotEmpty(roleFilter.getScopeIdentifier()) && !roleFilter.isIncludeChildScopes()) {
      Scope scope = scopeService.buildScopeFromScopeIdentifier(roleFilter.getScopeIdentifier());
      criteria.and(RoleDBOKeys.allowedScopeLevels).is(scope.getLevel().toString());
    }
    if (!roleFilter.getPermissionFilter().isEmpty()) {
      criteria.and(RoleDBOKeys.permissions).in(roleFilter.getPermissionFilter());
    }
    return criteria;
  }
}
