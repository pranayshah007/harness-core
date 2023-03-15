package io.harness.idp.catalog.service;

import com.google.inject.Inject;
import io.harness.clients.BackstageCatalogResourceClient;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;

import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

public class RefreshCatalogServiceImpl implements RefreshCatalogService{
    @Inject
    BackstageCatalogResourceClient backstageCatalogResourceClient;
    @Override
    public void processEntityUpdate(Message message, EntityChangeDTO entityChangeDTO) {
        String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
        Object test = getGeneralResponse(backstageCatalogResourceClient.providerRefresh());
        System.out.println(test);
    }
}
