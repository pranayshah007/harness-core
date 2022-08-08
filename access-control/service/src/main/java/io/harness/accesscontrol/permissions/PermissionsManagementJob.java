/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.permissions;

import io.harness.accesscontrol.commons.bootstrap.ConfigurationState;
import io.harness.accesscontrol.commons.bootstrap.ConfigurationStateRepository;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

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
public class PermissionsManagementJob {
  private static final String PERMISSIONS_YAML_PATH = "io/harness/accesscontrol/permissions/permissions.yml";

  private final PermissionService permissionService;
  private final ConfigurationStateRepository configurationStateRepository;
  private final PermissionsConfig permissionsConfig;

  @Inject
  public PermissionsManagementJob(
      PermissionService permissionService, ConfigurationStateRepository configurationStateRepository) {
    this.configurationStateRepository = configurationStateRepository;
    ObjectMapper om = new ObjectMapper(new YAMLFactory());
    URL url = getClass().getClassLoader().getResource(PERMISSIONS_YAML_PATH);
    try {
      byte[] bytes = Resources.toByteArray(url);
      this.permissionsConfig = om.readValue(bytes, PermissionsConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Permissions file path is invalid or the syntax is incorrect", e);
    }
    this.permissionService = permissionService;
  }

  public void run() {
    Optional<ConfigurationState> optional = configurationStateRepository.getByIdentifier(permissionsConfig.getName());
    if (optional.isPresent() && optional.get().getConfigVersion() >= permissionsConfig.getVersion()) {
      log.info("Permissions are already updated in the database");
      return;
    }
    log.info("Updating permissions in the database");

    Set<Permission> latestPermissions = permissionsConfig.getPermissions();
    Set<Permission> currentPermissions = new HashSet<>(permissionService.list(PermissionFilter.builder().build()));
    Set<Permission> addedOrUpdatedPermissions = Sets.difference(latestPermissions, currentPermissions);

    Set<String> latestIdentifiers =
        latestPermissions.stream().map(Permission::getIdentifier).collect(Collectors.toSet());
    Set<String> currentIdentifiers =
        currentPermissions.stream().map(Permission::getIdentifier).collect(Collectors.toSet());

    Set<String> addedIdentifiers = Sets.difference(latestIdentifiers, currentIdentifiers);
    Set<String> removedIdentifiers = Sets.difference(currentIdentifiers, latestIdentifiers);

    Set<Permission> addedPermissions = addedOrUpdatedPermissions.stream()
                                           .filter(p -> addedIdentifiers.contains(p.getIdentifier()))
                                           .collect(Collectors.toSet());

    Set<Permission> updatedPermissions = addedOrUpdatedPermissions.stream()
                                             .filter(p -> !addedIdentifiers.contains(p.getIdentifier()))
                                             .collect(Collectors.toSet());

    addedPermissions.forEach(permissionService::create);
    updatedPermissions.forEach(permissionService::update);
    removedIdentifiers.forEach(permissionService::delete);

    ConfigurationState configurationState =
        optional.orElseGet(() -> ConfigurationState.builder().identifier(permissionsConfig.getName()).build());
    configurationState.setConfigVersion(permissionsConfig.getVersion());
    configurationStateRepository.upsert(configurationState);
  }
}
