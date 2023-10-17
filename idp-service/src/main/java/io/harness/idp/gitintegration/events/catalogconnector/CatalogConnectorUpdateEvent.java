/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.gitintegration.events.catalogconnector;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_CATALOG_CONNECTOR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(IDP)
@Getter
@NoArgsConstructor
public class CatalogConnectorUpdateEvent implements Event {
  public static final String CATALOG_CONNECTOR_UPDATED = "CatalogConnectorUpdated";

  private CatalogConnectorEntity newCatalogConnectorEntity;
  private CatalogConnectorEntity oldCatalogConnectorEntity;
  private String accountIdentifier;

  public CatalogConnectorUpdateEvent(String accountIdentifier, CatalogConnectorEntity newCatalogConnectorEntity,
      CatalogConnectorEntity oldCatalogConnectorEntity) {
    this.newCatalogConnectorEntity = newCatalogConnectorEntity;
    this.oldCatalogConnectorEntity = oldCatalogConnectorEntity;
    this.accountIdentifier = accountIdentifier;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME,
        newCatalogConnectorEntity.getConnectorProviderType() + " Catalog Connector");
    return Resource.builder()
        .identifier(newCatalogConnectorEntity.getConnectorIdentifier() + "_" + accountIdentifier)
        .type(IDP_CATALOG_CONNECTOR)
        .labels(labels)
        .build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return CATALOG_CONNECTOR_UPDATED;
  }
}
