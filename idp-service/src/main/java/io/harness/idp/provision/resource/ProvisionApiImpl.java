/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.provision.resource;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.mappers.NamespaceMapper;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.provision.service.ProvisionService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.ProvisionApi;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class ProvisionApiImpl implements ProvisionApi {
  private IdpCommonService idpCommonService;
  private ProvisionService provisionService;
  private NamespaceService namespaceService;

  @Override
  public Response provisionIdp(String accountIdentifier) {
    try {
      idpCommonService.checkUserAuthorization();
      NamespaceEntity namespaceEntity = namespaceService.saveAccountIdNamespace(accountIdentifier);
      NamespaceInfo namespaceInfo = NamespaceMapper.toDTO(namespaceEntity);
      provisionService.triggerPipelineAndCreatePermissions(
          namespaceInfo.getAccountIdentifier(), namespaceInfo.getNamespace());
      return Response.status(Response.Status.CREATED).entity(namespaceInfo).build();
    } catch (DuplicateKeyException e) {
      String logMessage = String.format("Namespace already created for given account Id - %s", accountIdentifier);
      log.info(logMessage);
      NamespaceInfo namespaceInfo = namespaceService.getNamespaceForAccountIdentifier(accountIdentifier);
      provisionService.triggerPipelineAndCreatePermissions(accountIdentifier, namespaceInfo.getNamespace());
      return Response.status(Response.Status.CREATED).entity(namespaceInfo).build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
