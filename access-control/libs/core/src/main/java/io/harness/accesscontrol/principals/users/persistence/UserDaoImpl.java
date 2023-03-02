/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.users.persistence;

import static io.harness.accesscontrol.principals.users.persistence.UserDBOMapper.fromDBO;
import static io.harness.accesscontrol.principals.users.persistence.UserDBOMapper.toDBO;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.principals.users.User;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
@Singleton
@ValidateOnExecution
public class UserDaoImpl implements UserDao {
  private final UserRepository userRepository;

  @Inject
  public UserDaoImpl(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public long saveAll(List<User> users) {
    List<UserDBO> userList = users.stream().map(UserDBOMapper::toDBO).collect(Collectors.toList());
    return userRepository.insertAllIgnoringDuplicates(userList);
  }

  @Override
  public User createIfNotPresent(User user) {
    UserDBO userDBO = toDBO(user);
    Optional<UserDBO> savedUser =
        userRepository.findByIdentifierAndScopeIdentifier(userDBO.getIdentifier(), userDBO.getScopeIdentifier());
    return fromDBO(savedUser.orElseGet(() -> userRepository.save(userDBO)));
  }

  @Override
  public PageResponse<User> list(PageRequest pageRequest, String scopeIdentifier) {
    Pageable pageable = PageUtils.getPageRequest(pageRequest);
    Page<UserDBO> userPages = userRepository.findByScopeIdentifier(scopeIdentifier, pageable);
    return PageUtils.getNGPageResponse(userPages.map(UserDBOMapper::fromDBO));
  }

  @Override
  public Optional<User> get(String identifier, String scopeIdentifier) {
    return userRepository.findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier)
        .flatMap(u -> Optional.of(fromDBO(u)));
  }

  @Override
  public Optional<User> delete(String identifier, String scopeIdentifier) {
    Optional<UserDBO> optionalUserDBO =
        userRepository.deleteByIdentifierAndScopeIdentifier(identifier, scopeIdentifier);
    return optionalUserDBO.map(UserDBOMapper::fromDBO);
  }
}
