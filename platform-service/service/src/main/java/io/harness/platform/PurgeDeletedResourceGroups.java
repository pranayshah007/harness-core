/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.resourcegroup.framework.v1.repositories.spring.ResourceGroupRepository;
import io.harness.resourcegroup.v1.model.ResourceGroup;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class PurgeDeletedResourceGroups {
  private final ResourceGroupRepository resourceGroupRepository;

  public void cleanUp() {
    log.info("Purging Deleted Resource Groups");
    Page<ResourceGroup> deletedResourceGroups =
        resourceGroupRepository.findAll(Criteria.where("deleted").is(Boolean.TRUE), Pageable.unpaged());
    resourceGroupRepository.deleteAll(deletedResourceGroups);
    log.info("Purged Deleted Resource Groups");
  }
}
