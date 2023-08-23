/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcetypes;

import io.harness.accesscontrol.commons.bootstrap.ConfigurationState;
import io.harness.accesscontrol.commons.bootstrap.ConfigurationStateRepository;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.core.type.TypeReference;
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
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Singleton
@Slf4j
public class ResourceTypeManagementJob {
  private static final String RESOURCE_TYPES_YAML_PATH = "io/harness/accesscontrol/resources/resourceTypes.yml";

  private final ResourceTypeService resourceTypeService;
  private final ConfigurationStateRepository configurationStateRepository;
  private final ResourceTypesConfig resourceTypesConfig;

  @Inject
  public ResourceTypeManagementJob(
      ResourceTypeService resourceTypeService, ConfigurationStateRepository configurationStateRepository) {
    this.resourceTypeService = resourceTypeService;
    this.configurationStateRepository = configurationStateRepository;
    this.resourceTypesConfig = fetchResourceTypesFromYaml();
  }

  private ResourceTypesConfig fetchResourceTypesFromYaml() {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());
    URL url = getClass().getClassLoader().getResource(RESOURCE_TYPES_YAML_PATH);
    try {
      byte[] bytes = Resources.toByteArray(url);
      return om.readValue(bytes, new TypeReference<ResourceTypesConfig>() {});
    } catch (IOException e) {
      throw new InvalidRequestException("Resource type file path or file is invalid", e);
    }
  }

  public void run() {
    Optional<ConfigurationState> optional = configurationStateRepository.getByIdentifier(resourceTypesConfig.getName());
    if (optional.isPresent() && optional.get().getConfigVersion() >= resourceTypesConfig.getVersion()) {
      log.info("Resource types are already updated in the database");
      return;
    }

    log.info("Updating resource types in the database");

    Set<ResourceType> currentResourceTypes = new HashSet<>(resourceTypeService.list());
    Set<ResourceType> latestResourceTypes = resourceTypesConfig.getResourceTypes();
    Set<ResourceType> addedOrUpdatedResourceTypes = Sets.difference(latestResourceTypes, currentResourceTypes);

    addedOrUpdatedResourceTypes.forEach(resourceTypeService::save);

    ConfigurationState configurationState =
        optional.orElseGet(() -> ConfigurationState.builder().identifier(resourceTypesConfig.getName()).build());
    configurationState.setConfigVersion(resourceTypesConfig.getVersion());
    configurationStateRepository.upsert(configurationState);
  }
}
