/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.permissions.persistence;

import static io.harness.accesscontrol.permissions.persistence.PermissionDBOMapper.fromDBO;
import static io.harness.accesscontrol.permissions.persistence.PermissionDBOMapper.toDBO;

import io.harness.accesscontrol.permissions.Permission;
import io.harness.accesscontrol.permissions.PermissionFilter;
import io.harness.accesscontrol.permissions.PermissionFilter.IncludedInAllRolesFilter;
import io.harness.accesscontrol.permissions.persistence.PermissionDBO.PermissionDBOKeys;
import io.harness.accesscontrol.permissions.persistence.repositories.PermissionRepository;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PL)
@Singleton
@ValidateOnExecution
public class PermissionDaoImpl implements PermissionDao {
  private final PermissionRepository permissionRepository;

  @Inject
  public PermissionDaoImpl(PermissionRepository permissionRepository) {
    this.permissionRepository = permissionRepository;
  }

  @Override
  public Permission create(Permission permissionDTO) {
    PermissionDBO permissionDBO = toDBO(permissionDTO);
    try {
      return fromDBO(permissionRepository.save(permissionDBO));
    } catch (DuplicateKeyException e) {
      throw new DuplicateFieldException(
          String.format("A permission with identifier %s is already present", permissionDBO.getIdentifier()));
    }
  }

  @Override
  public List<Permission> list(PermissionFilter permissionFilter) {
    if (permissionFilter.isEmpty()) {
      Iterable<PermissionDBO> permissionDBOIterable = permissionRepository.findAll();
      List<Permission> permissions = new ArrayList<>();
      permissionDBOIterable.iterator().forEachRemaining(permissionDBO -> permissions.add(fromDBO(permissionDBO)));
      return permissions;
    }

    Criteria criteria = new Criteria();
    if (!permissionFilter.getIdentifierFilter().isEmpty()) {
      criteria.and(PermissionDBOKeys.identifier).in(permissionFilter.getIdentifierFilter());
    }
    if (!permissionFilter.getAllowedScopeLevelsFilter().isEmpty()) {
      criteria.and(PermissionDBOKeys.allowedScopeLevels).in(permissionFilter.getAllowedScopeLevelsFilter());
    }
    if (!permissionFilter.getStatusFilter().isEmpty()) {
      criteria.and(PermissionDBOKeys.status).in(permissionFilter.getStatusFilter());
    }
    if (IncludedInAllRolesFilter.PERMISSIONS_INCLUDED_IN_ALL_ROLES.equals(
            permissionFilter.getIncludedInAllRolesFilter())) {
      criteria.and(PermissionDBOKeys.includeInAllRoles).is(Boolean.TRUE);
    } else if (IncludedInAllRolesFilter.PERMISSIONS_NOT_INCLUDED_IN_ALL_ROLES.equals(
                   permissionFilter.getIncludedInAllRolesFilter())) {
      criteria.and(PermissionDBOKeys.includeInAllRoles).is(Boolean.FALSE);
    }

    List<PermissionDBO> permissionDBOList = permissionRepository.findAll(criteria);
    return permissionDBOList.stream().map(PermissionDBOMapper::fromDBO).collect(Collectors.toList());
  }

  @Override
  public Optional<Permission> get(String identifier) {
    Optional<PermissionDBO> permission = permissionRepository.findByIdentifier(identifier);
    return permission.flatMap(p -> Optional.of(fromDBO(p)));
  }

  @Override
  public Permission update(Permission permissionUpdate) {
    Optional<PermissionDBO> permissionDBOOptional =
        permissionRepository.findByIdentifier(permissionUpdate.getIdentifier());
    if (!permissionDBOOptional.isPresent()) {
      throw new InvalidRequestException(
          String.format("Could not find the permission %s", permissionUpdate.getIdentifier()));
    }
    PermissionDBO permissionUpdateDBO = toDBO(permissionUpdate);
    permissionUpdateDBO.setId(permissionDBOOptional.get().getId());
    return fromDBO(permissionRepository.save(permissionUpdateDBO));
  }

  @Override
  public Permission delete(String identifier) {
    return fromDBO(
        permissionRepository.deleteByIdentifier(identifier)
            .orElseThrow(
                () -> new InvalidRequestException(String.format("Could not delete the permission %s", identifier))));
  }
}
