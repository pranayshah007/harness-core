/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.delegateselectors.cache.connector;

import static io.harness.idp.gitintegration.beans.CatalogInfraConnectorType.PROXY;

import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCacheLoader;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.gitintegration.repositories.CatalogConnectorRepository;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class ConnectorCacheLoader implements DelegateSelectorsCacheLoader {
  CatalogConnectorRepository catalogConnectorRepository;
  @Override
  public Map<String, Set<String>> load(String accountIdentifier) {
    Map<String, Set<String>> hostDelegateSelectors = new HashMap<>();
    List<CatalogConnectorEntity> connectors =
        catalogConnectorRepository.findAllHostsByAccountIdentifier(accountIdentifier);
    connectors.forEach(connector -> {
      if (PROXY.equals(connector.getType())) {
        hostDelegateSelectors.put(connector.getHost(), connector.getDelegateSelectors());
      }
    });
    return hostDelegateSelectors;
  }
}
