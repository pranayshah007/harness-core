/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ipallowlist.resource;

import static io.harness.ng.accesscontrol.PlatformPermissions.EDIT_AUTHSETTING_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_AUTHSETTING_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.AUTHSETTING;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.ipallowlist.IPAllowlistResourceUtils;
import io.harness.ipallowlist.entity.IPAllowlistEntity;
import io.harness.ipallowlist.service.IPAllowlistService;
import io.harness.spec.server.ng.v1.IpAllowlistApi;
import io.harness.spec.server.ng.v1.model.IPAllowlistConfigRequest;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class IpAllowlistApiImpl implements IpAllowlistApi {
  @Inject private final IPAllowlistService ipAllowlistService;
  @Inject private final IPAllowlistResourceUtils ipAllowlistResourceUtil;
  @Inject private final AccessControlClient accessControlClient;

  @Override
  public Response createIpAllowlistConfig(
      @Valid IPAllowlistConfigRequest ipAllowlistConfigRequest, String accountIdentifier) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    IPAllowlistEntity ipAllowlistEntity =
        ipAllowlistResourceUtil.toIPAllowlistEntity(ipAllowlistConfigRequest.getIpAllowlistConfig(), accountIdentifier);
    IPAllowlistEntity createdIpAllowlistEntity = ipAllowlistService.create(ipAllowlistEntity);

    return Response.status(Response.Status.CREATED)
        .entity(ipAllowlistResourceUtil.toIPAllowlistConfigResponse(createdIpAllowlistEntity))
        .build();
  }

  @Override
  public Response updateIpAllowlistConfig(
      String ipConfigIdentifier, @Valid IPAllowlistConfigRequest ipAllowlistConfigRequest, String harnessAccount) {
    return null;
  }

  @Override
  public Response deleteIpAllowlistConfig(String ipConfigIdentifier, String harnessAccount) {
    return null;
  }

  @Override
  public Response getIpAllowlistConfig(String ipConfigIdentifier, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(AUTHSETTING, ipConfigIdentifier), VIEW_AUTHSETTING_PERMISSION);
    IPAllowlistEntity ipAllowlistEntity = ipAllowlistService.get(harnessAccount, ipConfigIdentifier);

    return Response.status(Response.Status.OK)
        .entity(ipAllowlistResourceUtil.toIPAllowlistConfigResponse(ipAllowlistEntity))
        .build();
  }

  @Override
  public Response getIpAllowlistConfigs(
      List<String> identifier, String searchTerm, Integer page, @Max(1000L) Integer limit, String harnessAccount) {
    return null;
  }

  @Override
  public Response validateIpAddressAllowlistedOrNot(
      @NotNull String ipAddress, String harnessAccount, String ipAddressBlock) {
    return null;
  }

  @Override
  public Response validateUniqueIpAllowlistConfigIdentifier(String ipConfigIdentifier, String harnessAccount) {
    return null;
  }
}
