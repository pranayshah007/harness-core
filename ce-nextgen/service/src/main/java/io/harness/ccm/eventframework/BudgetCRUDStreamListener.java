/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.eventframework;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CCM_BUDGET;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.graphql.core.budget.BudgetService;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CE)
public class BudgetCRUDStreamListener implements MessageListener {
  @Inject private BudgetService budgetService;

  @Override
  public boolean handleMessage(final Message message) {
    if (Objects.nonNull(message) && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (isBudgetCRUDEvent(metadataMap)) {
        log.info("Received Budget Create/ Update Event, Message: {}", message);
        final EntityChangeDTO entityChangeDTO = getEntityChangeDTO(message);
        Budget budget = budgetService.get(
            entityChangeDTO.getIdentifier().getValue(), entityChangeDTO.getAccountIdentifier().getValue());
        // The actual and forecasted were updated while creating or updating budget in sync.
        // Here we just update the last period cost async.
        budgetService.updateBudgetCosts(budget, false, true);
        if (CREATE_ACTION.equals(metadataMap.get(ACTION))) {
          budgetService.updateBudgetHistory(budget);
        }
        budgetService.update(budget);
      }
    }
    return true;
  }

  private boolean isBudgetCRUDEvent(final Map<String, String> metadataMap) {
    return metadataMap != null && CCM_BUDGET.equals(metadataMap.get(ENTITY_TYPE));
  }

  private EntityChangeDTO getEntityChangeDTO(final Message message) {
    EntityChangeDTO entityChangeDTO;
    try {
      entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (final InvalidProtocolBufferException ex) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), ex);
    }
    return entityChangeDTO;
  }
}
