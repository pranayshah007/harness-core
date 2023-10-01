/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles;

import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.commons.bootstrap.ConfigurationState;
import io.harness.accesscontrol.commons.bootstrap.ConfigurationStateRepository;
import io.harness.accesscontrol.roles.filter.RoleFilter;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
@Singleton
public class RolesManagementJob {
  private static final String ROLES_YAML_PATH = "io/harness/accesscontrol/roles/managed-roles.yml";

  private final RoleService roleService;
  private final ConfigurationStateRepository configurationStateRepository;
  private final RolesConfig rolesConfig;

  @Inject
  public RolesManagementJob(RoleService roleService, ConfigurationStateRepository configurationStateRepository) {
    this.configurationStateRepository = configurationStateRepository;
    ObjectMapper om = new ObjectMapper(new YAMLFactory());
    URL url = getClass().getClassLoader().getResource(ROLES_YAML_PATH);
    try {
      byte[] bytes = Resources.toByteArray(url);
      this.rolesConfig = om.readValue(bytes, RolesConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Roles file path or format is invalid");
    }
    this.roleService = roleService;
  }

  public void run() {
    Optional<ConfigurationState> optional = configurationStateRepository.getByIdentifier(rolesConfig.getName());
    if (optional.isPresent() && optional.get().getConfigVersion() >= rolesConfig.getVersion()) {
      log.info("Managed roles are already updated in the database");
      return;
    }

    log.info("Updating roles in the database");

    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(100).build();
    RoleFilter roleFilter = RoleFilter.builder().managedFilter(ManagedFilter.ONLY_MANAGED).build();

    Set<Role> latestRoles = rolesConfig.getRoles();
    Set<Role> currentRoles = new HashSet<>(roleService.list(pageRequest, roleFilter, false).getContent());
    Set<Role> addedOrUpdatedRoles = Sets.difference(latestRoles, currentRoles);

    Set<String> latestIdentifiers = latestRoles.stream().map(Role::getIdentifier).collect(Collectors.toSet());
    Set<String> currentIdentifiers = currentRoles.stream().map(Role::getIdentifier).collect(Collectors.toSet());

    Set<String> addedIdentifiers = Sets.difference(latestIdentifiers, currentIdentifiers);
    Set<String> removedIdentifiers = Sets.difference(currentIdentifiers, latestIdentifiers);

    Set<Role> addedRoles = addedOrUpdatedRoles.stream()
                               .filter(p -> addedIdentifiers.contains(p.getIdentifier()))
                               .collect(Collectors.toSet());

    Set<Role> updatedRoles = addedOrUpdatedRoles.stream()
                                 .filter(p -> !addedIdentifiers.contains(p.getIdentifier()))
                                 .collect(Collectors.toSet());

    addedRoles.forEach(roleService::create);
    updatedRoles.forEach(roleService::update);
    removedIdentifiers.forEach(roleService::deleteManaged);

    ConfigurationState configurationState =
        optional.orElseGet(() -> ConfigurationState.builder().identifier(rolesConfig.getName()).build());
    configurationState.setConfigVersion(rolesConfig.getVersion());
    configurationStateRepository.upsert(configurationState);
  }
}