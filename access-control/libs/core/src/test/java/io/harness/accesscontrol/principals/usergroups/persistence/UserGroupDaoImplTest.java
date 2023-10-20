/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.usergroups.persistence;

import static io.harness.accesscontrol.scopes.harness.ScopeMapper.fromParams;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.rule.OwnerRule.MEENAKSHI;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlCoreTestBase;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.scopes.ScopeFilterType;
import io.harness.accesscontrol.scopes.ScopeSelector;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PL)
public class UserGroupDaoImplTest extends AccessControlCoreTestBase {
  private UserGroupRepository userGroupRepository;
  private UserGroupDao userGroupDao;
  private static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);

  @Before
  public void setup() {
    userGroupRepository = mock(UserGroupRepository.class);
    userGroupDao = spy(new UserGroupDaoImpl(userGroupRepository));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpsertUpdate() {
    UserGroup userGroup = UserGroup.builder().lastModifiedAt(1L).build();
    UserGroupDBO userGroupDBO = UserGroupDBO.builder().lastModifiedAt(0L).build();
    when(userGroupRepository.findByIdentifierAndScopeIdentifier(
             userGroup.getIdentifier(), userGroup.getScopeIdentifier()))
        .thenReturn(Optional.of(userGroupDBO));
    when(userGroupRepository.save(userGroupDBO)).thenReturn(userGroupDBO);
    assertEquals(0L, userGroupDao.upsert(userGroup).getLastModifiedAt().longValue());
    verify(userGroupRepository, times(1))
        .findByIdentifierAndScopeIdentifier(userGroup.getIdentifier(), userGroup.getScopeIdentifier());
    when(userGroupRepository.save(userGroupDBO)).thenReturn(userGroupDBO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpsertCreate() {
    UserGroup userGroup = UserGroup.builder().lastModifiedAt(1L).build();
    UserGroupDBO userGroupDBO = UserGroupDBO.builder().lastModifiedAt(1L).build();
    when(userGroupRepository.findByIdentifierAndScopeIdentifier(
             userGroup.getIdentifier(), userGroup.getScopeIdentifier()))
        .thenReturn(Optional.empty());
    when(userGroupRepository.save(userGroupDBO)).thenReturn(userGroupDBO);
    assertEquals(1L, userGroupDao.upsert(userGroup).getLastModifiedAt().longValue());
    verify(userGroupRepository, times(1))
        .findByIdentifierAndScopeIdentifier(userGroup.getIdentifier(), userGroup.getScopeIdentifier());
    when(userGroupRepository.save(userGroupDBO)).thenReturn(userGroupDBO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(1).build();
    Pageable pageable = PageUtils.getPageRequest(pageRequest);
    String scopeIdentifier = randomAlphabetic(10);
    PageResponse pageResponse = PageUtils.getNGPageResponse(Page.empty());
    when(userGroupRepository.findByScopeIdentifier(scopeIdentifier, pageable)).thenReturn(Page.empty());
    assertEquals(pageResponse, userGroupDao.list(pageRequest, scopeIdentifier));
    verify(userGroupRepository, times(1)).findByScopeIdentifier(scopeIdentifier, pageable);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListWithScopeSelector_allAccountScope_includingChild() {
    String scopeIdentifier = "/ACCOUNT/" + ACCOUNT_IDENTIFIER;
    String user = randomAlphabetic(10);
    Set<ScopeSelector> scopeSelectors = Set.of(ScopeSelector.builder()
                                                   .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                   .filter(ScopeFilterType.INCLUDING_CHILD_SCOPES)
                                                   .build());
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(userGroupRepository.find(criteriaArgumentCaptor.capture()))
        .thenReturn(List.of(UserGroupDBO.builder().build()));
    userGroupDao.list(scopeIdentifier, user, scopeSelectors);
    verify(userGroupRepository, times(1)).find(criteriaArgumentCaptor.capture());
    Criteria actualCriteria = criteriaArgumentCaptor.getValue();
    Criteria expectedCriteria = new Criteria();
    List<Criteria> scopeCriterion = new ArrayList<>();
    String scopeId = fromParams(HarnessScopeParams.builder()
                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                    .orgIdentifier(null)
                                    .projectIdentifier(null)
                                    .build())
                         .toString();
    Pattern startsWithScope = Pattern.compile("^".concat(scopeId));
    scopeCriterion.add(Criteria.where("scopeIdentifier").regex(startsWithScope));
    expectedCriteria.orOperator(scopeCriterion.toArray(new Criteria[0])).and("users").in(user);
    assertEquals(new Query(actualCriteria).toString(), new Query(expectedCriteria).toString());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListWithScopeSelector_allAccountScope_excludingChildScope() {
    String scopeIdentifier = "/ACCOUNT/" + ACCOUNT_IDENTIFIER;
    String user = randomAlphabetic(10);
    Set<ScopeSelector> scopeSelectors = Set.of(ScopeSelector.builder()
                                                   .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                   .filter(ScopeFilterType.EXCLUDING_CHILD_SCOPES)
                                                   .build());
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(userGroupRepository.find(criteriaArgumentCaptor.capture()))
        .thenReturn(List.of(UserGroupDBO.builder().build()));
    userGroupDao.list(scopeIdentifier, user, scopeSelectors);
    verify(userGroupRepository, times(1)).find(criteriaArgumentCaptor.capture());
    Criteria actualCriteria = criteriaArgumentCaptor.getValue();
    Criteria expectedCriteria = new Criteria();
    List<Criteria> scopeCriterion = new ArrayList<>();
    String scopeId = fromParams(HarnessScopeParams.builder()
                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                    .orgIdentifier(null)
                                    .projectIdentifier(null)
                                    .build())
                         .toString();
    scopeCriterion.add(Criteria.where("scopeIdentifier").is(scopeId));
    expectedCriteria.orOperator(scopeCriterion.toArray(new Criteria[0])).and("users").in(user);
    assertEquals(new Query(actualCriteria).toString(), new Query(expectedCriteria).toString());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<UserGroupDBO> userGroupDBOOptional = Optional.of(UserGroupDBO.builder().build());
    when(userGroupRepository.findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier))
        .thenReturn(userGroupDBOOptional);
    assertEquals(Optional.of(UserGroup.builder().build()), userGroupDao.get(identifier, scopeIdentifier));
    verify(userGroupRepository, times(1)).findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetNotFound() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<UserGroupDBO> userGroupDBOOptional = Optional.empty();
    when(userGroupRepository.findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier))
        .thenReturn(userGroupDBOOptional);
    assertEquals(Optional.empty(), userGroupDao.get(identifier, scopeIdentifier));
    verify(userGroupRepository, times(1)).findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    UserGroupDBO userGroupDBO = UserGroupDBO.builder().build();
    when(userGroupRepository.deleteByIdentifierAndScopeIdentifier(identifier, scopeIdentifier))
        .thenReturn(Optional.of(userGroupDBO));
    assertEquals(Optional.of(UserGroup.builder().build()), userGroupDao.delete(identifier, scopeIdentifier));
    verify(userGroupRepository, times(1)).deleteByIdentifierAndScopeIdentifier(identifier, scopeIdentifier);
  }
}
