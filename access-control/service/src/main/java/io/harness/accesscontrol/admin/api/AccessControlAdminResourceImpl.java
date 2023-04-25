/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.admin.api;

import io.harness.aggregator.AccessControlAdminService;
import io.harness.aggregator.models.BlockedAccount;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class AccessControlAdminResourceImpl implements AccessControlAdminResource {
  private AccessControlAdminService accessControlAdminService;

  @Inject
  public AccessControlAdminResourceImpl(AccessControlAdminService accessControlAdminService) {
    this.accessControlAdminService = accessControlAdminService;
  }

  @Override
  public Response blockAccount(BlockAccountDTO blockAccountDTO) {
    accessControlAdminService.block(blockAccountDTO.getAccountIdentifier());
    return Response.accepted().build();
  }

  @Override
  public Response unblockAccount(UnblockAccountDTO unblockAccountDTO) {
    accessControlAdminService.unblock(unblockAccountDTO.getAccountIdentifier());
    return Response.accepted().build();
  }

  @Override
  public Response getBlockedAccounts() {
    List<BlockedAccount> blockedACLEntities = accessControlAdminService.getAllBlockedAccounts();
    List<BlockAccountDTO> blockAccountDTOS =
        blockedACLEntities.stream()
            .map(blockedAccount
                -> BlockAccountDTO.builder().accountIdentifier(blockedAccount.getAccountIdentifier()).build())
            .collect(Collectors.toList());
    return Response.ok().entity(blockAccountDTOS).build();
  }
}
