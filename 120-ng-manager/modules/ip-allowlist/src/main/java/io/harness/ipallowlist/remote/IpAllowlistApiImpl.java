/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ipallowlist.remote;

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
  @Override
  public Response createIpAllowlistConfig(@Valid IPAllowlistConfigRequest body, String harnessAccount) {
    return null;
  }

  @Override
  public Response deleteIpAllowlistConfig(String ipConfigIdentifier, String harnessAccount) {
    return null;
  }

  @Override
  public Response getIpAllowlistConfig(String ipConfigIdentifier, String harnessAccount) {
    return null;
  }

  @Override
  public Response getIpAllowlistConfigs(
      List<String> identifier, String searchTerm, Integer page, @Max(1000L) Integer limit, String harnessAccount) {
    return null;
  }

  @Override
  public Response updateIpAllowlistConfig(
      String ipConfigIdentifier, @Valid IPAllowlistConfigRequest body, String harnessAccount) {
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
