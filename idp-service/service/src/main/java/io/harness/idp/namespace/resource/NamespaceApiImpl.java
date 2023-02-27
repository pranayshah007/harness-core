/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.namespace.resource;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.mappers.NamespaceMapper;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.spec.server.idp.v1.NamespaceApi;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import com.google.inject.Inject;
import java.util.Optional;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NamespaceApiImpl implements NamespaceApi {
  private NamespaceService namespaceService;

  @Override
  public Response createNamespace(String accountIdentifier) {
    NamespaceEntity saveResponse;
    try {
      saveResponse = namespaceService.saveAccountIdNamespace(accountIdentifier);
    } catch (DuplicateKeyException e) {
      String logMessage = String.format("Namespace already created for given account Id - %s", accountIdentifier);
      log.error(logMessage);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(logMessage).build();
    }
    NamespaceInfo namespaceInfo = NamespaceMapper.toDTO(saveResponse);
    return Response.status(Response.Status.CREATED).entity(namespaceInfo).build();
  }

  @Override
  public Response getNamespaceInfo(String accountIdentifier) {
    Optional<NamespaceInfo> namespaceInfo = namespaceService.getNamespaceForAccountIdentifier(accountIdentifier);
    return Response.status(Response.Status.OK).entity(namespaceInfo).build();
  }
}
