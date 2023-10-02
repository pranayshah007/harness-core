/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.reconciliation;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.RESOUCE_GROUP_SERVICE;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;

import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.resourcegroup.framework.v2.remote.mapper.ResourceGroupMapper;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupValidator;
import io.harness.resourcegroup.v2.model.ResourceGroup;
import io.harness.resourcegroup.v2.model.ResourceGroup.ResourceGroupKeys;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class ResourceGroupAsyncReconciliationHandler implements MongoPersistenceIterator.Handler<ResourceGroup> {
  public static final String GROUP = "RESOURCE_GROUP_ASYNC_RECONCILIATION";

  @Inject private final PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private final MongoTemplate mongoTemplate;
  @Inject private final ResourceGroupService resourceGroupService;
  @Inject private final ResourceGroupValidator resourceGroupValidator;

  @Override
  public void handle(ResourceGroup resourceGroup) {
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(RESOUCE_GROUP_SERVICE.getServiceId()));
      boolean updated = resourceGroupValidator.sanitizeResourceSelectors(resourceGroup);
      if (updated) {
        resourceGroupService.update(
            ResourceGroupMapper.toDTO(resourceGroup), Boolean.TRUE.equals(resourceGroup.getHarnessManaged()));
      }
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  public void registerIterators() {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("ResourceGroupReconciliationIterator")
            .poolSize(2)
            .interval(ofMinutes(1))
            .build(),
        ResourceGroup.class,
        MongoPersistenceIterator.<ResourceGroup, SpringFilterExpander>builder()
            .clazz(ResourceGroup.class)
            .fieldName(ResourceGroupKeys.nextIteration)
            .targetInterval(ofHours(1))
            .acceptableNoAlertDelay(ofHours(1))
            .filterExpander(getFilterExpander())
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  private SpringFilterExpander getFilterExpander() {
    return query -> {
      Criteria criteria = Criteria.where(ResourceGroupKeys.harnessManaged).ne(true);
      query.addCriteria(criteria);
    };
  }
}
