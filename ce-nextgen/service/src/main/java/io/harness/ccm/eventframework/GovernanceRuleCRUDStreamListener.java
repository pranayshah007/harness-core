/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.eventframework;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CCM_RULE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;

import io.harness.ccm.views.entities.RuleEnforcement;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.ccm.views.service.RuleEnforcementService;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class GovernanceRuleCRUDStreamListener implements MessageListener {
  @Inject GovernanceRuleService ruleService;
  @Inject RuleEnforcementService ruleEnforcementService;

  @Override
  public boolean handleMessage(Message message) {
    if (Objects.nonNull(message) && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (isRuleDeleteEvent(metadataMap)) {
        log.info("Received Rule Delete Event, Message: {}", message);
        final EntityChangeDTO entityChangeDTO = getEntityChangeDTO(message);
        String ruleId = entityChangeDTO.getIdentifier().getValue();
        String accountId = entityChangeDTO.getAccountIdentifier().getValue();
        List<RuleEnforcement> ruleEnforcementsWithGivenRule =
            ruleEnforcementService.listEnforcementsWithGivenRule(accountId, ruleId);
        ruleEnforcementsWithGivenRule.forEach(
            ruleEnforcement -> ruleEnforcementService.removeRuleFromEnforcement(ruleEnforcement, ruleId));
        log.info("Removed rule: {} from rule enforcements", ruleId);
      }
    }
    return true;
  }

  private boolean isRuleDeleteEvent(final Map<String, String> metadataMap) {
    return metadataMap != null && CCM_RULE.equals(metadataMap.get(ENTITY_TYPE))
        && DELETE_ACTION.equals(metadataMap.get(ACTION));
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
