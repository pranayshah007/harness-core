/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.service.batch;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.persistence.HIterator;

import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.service.ServiceController;
import software.wings.graphql.schema.type.QLService;
import software.wings.graphql.schema.type.QLService.QLServiceBuilder;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.dataloader.MappedBatchLoader;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ServiceBatchDataLoader implements MappedBatchLoader<String, QLService> {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public CompletionStage<Map<String, QLService>> load(Set<String> serviceIds) {
    return CompletableFuture.supplyAsync(() -> {
      Map<String, QLService> serviceMap = null;
      if (!CollectionUtils.isEmpty(serviceIds)) {
        serviceMap = getServiceMap(serviceIds);
      } else {
        serviceMap = Collections.EMPTY_MAP;
      }
      return serviceMap;
    });
  }

  public Map<String, QLService> getServiceMap(@NotNull Set<String> serviceIds) {
    Query<Service> query =
        wingsPersistence.createQuery(Service.class, excludeAuthority).field(ServiceKeys.uuid).in(serviceIds);
    Map<String, QLService> serviceMap = new HashMap<>();

    try (HIterator<Service> services = new HIterator<>(query.fetch())) {
      services.forEach(service -> {
        final QLServiceBuilder builder = QLService.builder();
        ServiceController.populateService(service, builder);
        serviceMap.put(service.getUuid(), builder.build());
      });
    }
    return serviceMap;
  }
}
