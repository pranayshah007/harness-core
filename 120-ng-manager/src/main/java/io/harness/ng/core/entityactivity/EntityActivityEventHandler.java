/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entityactivity;

import io.harness.EntityType;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.ng.core.entityactivity.connector.ConnectorEntityActivityEventHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class EntityActivityEventHandler {
  @Inject ConnectorEntityActivityEventHandler connectorEntityActivityEventHandler;

  public void updateActivityResultInEntity(NGActivityDTO ngActivityDTO) {
    // If in future, we need to update the activity for other entities like
    // secret, services, then we can change this if else to factory
    if (ngActivityDTO.getReferredEntity().getType() == EntityType.CONNECTORS) {
      try {
        connectorEntityActivityEventHandler.updateActivityResultInConnectors(ngActivityDTO);
      } catch (Exception ex) {
        log.error("Exception occurred while updating the activity result : ", ex);
      }
    }
  }
}
