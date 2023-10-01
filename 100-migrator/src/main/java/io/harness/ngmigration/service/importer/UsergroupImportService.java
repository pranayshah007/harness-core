/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.importer;

import static software.wings.ngmigration.NGMigrationEntityType.USER_GROUP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.UsergroupFilter;
import io.harness.ngmigration.service.DiscoveryService;

import software.wings.beans.security.UserGroup;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class UsergroupImportService implements ImportService {
  @Inject DiscoveryService discoveryService;

  @Inject private UserGroupService userGroupService;

  @Override
  public DiscoveryResult discover(ImportDTO importDTO) {
    UsergroupFilter filter = (UsergroupFilter) importDTO.getFilter();
    String accountId = importDTO.getAccountIdentifier();
    List<String> filterIds = filter.getIds();
    if (EmptyPredicate.isEmpty(filterIds)) {
      List<UserGroup> userGroups = userGroupService.listByAccountId(accountId);
      if (EmptyPredicate.isEmpty(userGroups)) {
        return null;
      }
      filterIds = userGroups.stream().map(UserGroup::getUuid).collect(Collectors.toList());
    }

    return discoveryService.discoverMulti(accountId,
        DiscoveryInput.builder()
            .exportImage(false)
            .entities(filterIds.stream()
                          .map(id -> DiscoverEntityInput.builder().entityId(id).type(USER_GROUP).build())
                          .collect(Collectors.toList()))
            .build());
  }
}
