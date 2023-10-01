/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcetypes.persistence;

import static io.harness.accesscontrol.resources.resourcetypes.persistence.ResourceTypeDBOMapper.fromDBO;
import static io.harness.accesscontrol.resources.resourcetypes.persistence.ResourceTypeDBOMapper.toDBO;

import io.harness.accesscontrol.resources.resourcetypes.ResourceType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.validation.executable.ValidateOnExecution;

@OwnedBy(HarnessTeam.PL)
@Singleton
@ValidateOnExecution
public class ResourceTypeDaoImpl implements ResourceTypeDao {
  private final ResourceTypeRepository resourceTypeRepository;

  @Inject
  public ResourceTypeDaoImpl(ResourceTypeRepository resourceTypeRepository) {
    this.resourceTypeRepository = resourceTypeRepository;
  }

  @Override
  public ResourceType save(ResourceType resourceType) {
    ResourceTypeDBO newResourceTypeDBO = toDBO(resourceType);
    Optional<ResourceTypeDBO> existingResourceTypeDBOOptional =
        resourceTypeRepository.findByIdentifier(resourceType.getIdentifier());
    if (existingResourceTypeDBOOptional.isPresent()) {
      ResourceTypeDBO existingResourceTypeDBO = existingResourceTypeDBOOptional.get();
      newResourceTypeDBO.setVersion(existingResourceTypeDBO.getVersion());
      newResourceTypeDBO.setCreatedAt(existingResourceTypeDBO.getCreatedAt());
      newResourceTypeDBO.setId(existingResourceTypeDBO.getId());
    }
    return fromDBO(resourceTypeRepository.save(newResourceTypeDBO));
  }

  @Override
  public Optional<ResourceType> get(String identifier) {
    return resourceTypeRepository.findByIdentifier(identifier).flatMap(r -> Optional.of(fromDBO(r)));
  }

  @Override
  public Optional<ResourceType> getByPermissionKey(String permissionKey) {
    return resourceTypeRepository.findByPermissionKey(permissionKey).flatMap(r -> Optional.of(fromDBO(r)));
  }

  @Override
  public List<ResourceType> list() {
    Iterable<ResourceTypeDBO> iterable = resourceTypeRepository.findAll();
    List<ResourceType> resourceTypes = new ArrayList<>();
    iterable.forEach(resourceTypeDBO -> resourceTypes.add(fromDBO(resourceTypeDBO)));
    return resourceTypes;
  }
}
