/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.usergroups.persistence;

import static io.harness.accesscontrol.principals.usergroups.persistence.UserGroupDBOMapper.fromDBO;
import static io.harness.accesscontrol.principals.usergroups.persistence.UserGroupDBOMapper.toDBO;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.fromParams;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupDBO.UserGroupDBOKeys;
import io.harness.accesscontrol.scopes.ScopeFilterType;
import io.harness.accesscontrol.scopes.ScopeSelector;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Singleton
@ValidateOnExecution
public class UserGroupDaoImpl implements UserGroupDao {
  private final UserGroupRepository userGroupRepository;

  @Inject
  public UserGroupDaoImpl(UserGroupRepository userGroupRepository) {
    this.userGroupRepository = userGroupRepository;
  }

  @Override
  public UserGroup upsert(UserGroup userGroupUpdate) {
    UserGroupDBO userGroupDBOUpdate = toDBO(userGroupUpdate);
    Optional<UserGroupDBO> userGroupDBOOpt = userGroupRepository.findByIdentifierAndScopeIdentifier(
        userGroupDBOUpdate.getIdentifier(), userGroupDBOUpdate.getScopeIdentifier());
    if (userGroupDBOOpt.isPresent()) {
      UserGroupDBO userGroupDBO = userGroupDBOOpt.get();
      if (userGroupDBO.equals(userGroupDBOUpdate)) {
        return fromDBO(userGroupDBO);
      }
      userGroupDBOUpdate.setId(userGroupDBO.getId());
      userGroupDBOUpdate.setVersion(userGroupDBO.getVersion());
      userGroupDBOUpdate.setCreatedAt(userGroupDBO.getCreatedAt());
      userGroupDBOUpdate.setLastModifiedAt(userGroupDBO.getCreatedAt());
      userGroupDBOUpdate.setNextReconciliationIterationAt(userGroupDBO.getNextReconciliationIterationAt());
    }
    return fromDBO(userGroupRepository.save(userGroupDBOUpdate));
  }

  @Override
  public PageResponse<UserGroup> list(PageRequest pageRequest, String scopeIdentifier) {
    Pageable pageable = PageUtils.getPageRequest(pageRequest);
    Page<UserGroupDBO> userGroupPages = userGroupRepository.findByScopeIdentifier(scopeIdentifier, pageable);
    return PageUtils.getNGPageResponse(userGroupPages.map(UserGroupDBOMapper::fromDBO));
  }

  @Override
  public List<UserGroup> list(String scopeIdentifier, String userIdentifier) {
    List<UserGroupDBO> userGroupPages =
        userGroupRepository.findByScopeIdentifierAndUsersIn(scopeIdentifier, userIdentifier);
    return userGroupPages.stream().map(UserGroupDBOMapper::fromDBO).collect(Collectors.toList());
  }

  @Override
  public List<UserGroup> list(String scopeIdentifier, String userIdentifier, Set<ScopeSelector> scopeSelectors) {
    if (scopeSelectors.isEmpty()) {
      list(scopeIdentifier, userIdentifier);
    }
    Criteria criteria = new Criteria();
    List<Criteria> scopeCriterion = new ArrayList<>();
    for (ScopeSelector selector : scopeSelectors) {
      String scopeId = fromParams(getHarnessScopeParams(selector.getAccountIdentifier(), selector.getOrgIdentifier(),
                                      selector.getProjectIdentifier()))
                           .toString();

      if (ScopeFilterType.EXCLUDING_CHILD_SCOPES.equals(selector.getFilter())) {
        scopeCriterion.add(Criteria.where(UserGroupDBOKeys.scopeIdentifier).is(scopeId));
      } else {
        Pattern startsWithScope = Pattern.compile("^".concat(scopeId));
        scopeCriterion.add(Criteria.where(UserGroupDBOKeys.scopeIdentifier).regex(startsWithScope));
      }
    }
    criteria.orOperator(scopeCriterion.toArray(new Criteria[0])).and(UserGroupDBOKeys.users).in(userIdentifier);

    List<UserGroupDBO> userGroupPages = userGroupRepository.find(criteria);
    return userGroupPages.stream().map(UserGroupDBOMapper::fromDBO).collect(Collectors.toList());
  }

  private HarnessScopeParams getHarnessScopeParams(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return HarnessScopeParams.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  @Override
  public Optional<UserGroup> get(String identifier, String scopeIdentifier) {
    return userGroupRepository.findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier)
        .flatMap(ug -> Optional.of(UserGroupDBOMapper.fromDBO(ug)));
  }

  @Override
  public Optional<UserGroup> delete(String identifier, String scopeIdentifier) {
    Optional<UserGroupDBO> optionalUserGroupDBO =
        userGroupRepository.deleteByIdentifierAndScopeIdentifier(identifier, scopeIdentifier);
    return optionalUserGroupDBO.map(UserGroupDBOMapper::fromDBO);
  }
}
