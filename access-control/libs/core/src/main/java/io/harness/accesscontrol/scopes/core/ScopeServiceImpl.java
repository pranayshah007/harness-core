/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.scopes.core;

import static io.harness.accesscontrol.scopes.core.Scope.PATH_DELIMITER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.accesscontrol.scopes.core.persistence.ScopeDBO;
import io.harness.accesscontrol.scopes.core.persistence.ScopeDao;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;

@OwnedBy(HarnessTeam.PL)
@Singleton
@ValidateOnExecution
public class ScopeServiceImpl implements ScopeService {
  private final ScopeDao scopeDao;
  private final Map<String, ScopeLevel> scopeLevelsByResourceType;
  private final Map<String, ScopeLevel> scopeLevels;

  @Inject
  public ScopeServiceImpl(ScopeDao scopeDao, Map<String, ScopeLevel> scopeLevels) {
    this.scopeDao = scopeDao;
    this.scopeLevels = scopeLevels;
    this.scopeLevelsByResourceType = new HashMap<>();
    scopeLevels.values().forEach(scopeLevel -> scopeLevelsByResourceType.put(scopeLevel.getResourceType(), scopeLevel));
  }

  @Override
  public long saveAll(List<Scope> scopes) {
    List<ScopeDBO> scopesList = scopes.stream().map(this::fromScope).collect(Collectors.toList());
    return scopeDao.saveAll(scopesList);
  }

  @Override
  public Optional<Scope> get(String identifier) {
    Optional<ScopeDBO> scopeDBO = scopeDao.get(identifier);
    if (scopeDBO.isPresent()) {
      return Optional.ofNullable(
          Scope.builder().instanceId(scopeDBO.get().getIdentifier()).instanceName(scopeDBO.get().getName()).build());
    }
    return null;
  }

  @Override
  public Scope getOrCreate(Scope scope) {
    ScopeDBO scopeDBO = fromScope(scope);
    return toScope(scopeDao.createIfNotPresent(scopeDBO));
  }

  @Override
  public boolean isPresent(String identifier) {
    return scopeDao.get(identifier).isPresent();
  }

  @Override
  public Optional<Scope> deleteIfPresent(String identifier) {
    return scopeDao.delete(identifier).flatMap(s -> Optional.of(toScope(s)));
  }

  @Override
  public Scope buildScopeFromScopeIdentifier(String identifier) {
    List<String> scopeIdentifierElements = Arrays.asList(identifier.split(PATH_DELIMITER));
    if (scopeIdentifierElements.size() < 3) {
      return null;
    }
    String instanceId = scopeIdentifierElements.get(scopeIdentifierElements.size() - 1);
    ScopeLevel scopeLevel =
        scopeLevelsByResourceType.get(scopeIdentifierElements.get(scopeIdentifierElements.size() - 2));

    if (isEmpty(instanceId)) {
      throw new InvalidArgumentsException("The instance id is invalid or empty in the scopeIdentifier");
    }
    if (scopeLevel == null) {
      throw new InvalidRequestException(
          "The scope level mentioned in the scope identifier is not registered in the system");
    }
    String parentScopeIdentifier =
        String.join(PATH_DELIMITER, scopeIdentifierElements.subList(0, scopeIdentifierElements.size() - 2));
    return Scope.builder()
        .instanceId(instanceId)
        .level(scopeLevel)
        .parentScope(buildScopeFromScopeIdentifier(parentScopeIdentifier))
        .build();
  }

  @Override
  public boolean areScopeLevelsValid(Set<String> scopeLevelsToValidate) {
    return scopeLevelsToValidate.stream().allMatch(scopeLevel -> scopeLevels.get(scopeLevel) != null);
  }

  @Override
  public Set<String> getAllScopeLevels() {
    return scopeLevels.keySet();
  }

  private Scope toScope(ScopeDBO scopeDBO) {
    return buildScopeFromScopeIdentifier(scopeDBO.getIdentifier());
  }

  private ScopeDBO fromScope(Scope scope) {
    return ScopeDBO.builder()
        .identifier(scope.toString())
        .name(Optional.ofNullable(scope.getInstanceName()).orElse(scope.getInstanceId()))
        .build();
  }
}
