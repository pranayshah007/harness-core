/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.modulelicense;

import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.CreditUtils;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.services.CreditService;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.entity_crud.modulelicense.ModuleLicenseEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(GTM)
@Slf4j
@Singleton
public class ModuleLicenseStreamListener implements MessageListener {
  @Inject private CreditService creditService;

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();

      if (metadataMap != null && CREATE_ACTION.equals(metadataMap.get(ACTION))) {
        ModuleLicenseEntityChangeDTO moduleLicenseEntityChangeDTO;

        try {
          moduleLicenseEntityChangeDTO = ModuleLicenseEntityChangeDTO.parseFrom(message.getMessage().getData());
        } catch (InvalidProtocolBufferException e) {
          throw new InvalidRequestException(
              String.format("Exception in unpacking ModuleLicense for key %s", message.getId()), e);
        }

        if (ModuleType.CI.name().equals(moduleLicenseEntityChangeDTO.getModuleType())) {
          return processModuleLicenseEvent(moduleLicenseEntityChangeDTO.getAccountIdentifier());
        }
      }
    }
    return true;
  }

  private boolean processModuleLicenseEvent(String accountIdentifier) {
    try {
      CreditDTO creditDTO = CreditUtils.buildCreditDTO(accountIdentifier);
      creditService.purchaseCredit(accountIdentifier, creditDTO);
      log.info("Successfully provisioned monthly free CI build credits for account: " + accountIdentifier);
    } catch (Exception ex) {
      log.error(
          "Error occurred while provisioning monthly free CI build credits for account: " + accountIdentifier, ex);
      return false;
    }
    return true;
  }
}
