/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.clients.BackstageResourceClient;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;

import com.google.inject.Inject;

public class RefreshCatalogServiceImpl implements RefreshCatalogService {
  @Inject BackstageResourceClient backstageCatalogResourceClient;
  @Override
  public void processEntityUpdate(Message message, EntityChangeDTO entityChangeDTO) {
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    Object test = getGeneralResponse(backstageCatalogResourceClient.providerRefresh());
  }
}
