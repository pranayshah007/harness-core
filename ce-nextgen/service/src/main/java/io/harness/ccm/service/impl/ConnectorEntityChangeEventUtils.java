/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.LightwingClient;
import io.harness.remote.client.NGRestUtils;

import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import okhttp3.MediaType;
import okhttp3.RequestBody;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class ConnectorEntityChangeEventUtils {
  private static final String CONNECTOR = "connector";
  private static final String ACCOUNT_IDENTIFIER = "account_identifier";
  private static final String EVENT_TYPE = "event_type";

  public static void lightwingAutocudDc(String action, String accountIdentifier, Object connectorDto,
      LightwingClient lightwingClient, CENextGenConfiguration configuration) {
    log.info("Inside lightwingAutocudDc, calling lightwing data collector api");
    if (!configuration.isEnableLightwingAutoCUDDC()) {
      log.info("ENABLE_LIGHTWING_AUTOCUD_DC is not enabled");
      return;
    }

    if (connectorDto == null) {
      log.info("The connector dto object sent is null");
      return;
    }

    try {
      JSONObject jsonObject = new JSONObject();
      jsonObject.put(EVENT_TYPE, action);
      jsonObject.put(ACCOUNT_IDENTIFIER, accountIdentifier);
      jsonObject.put(CONNECTOR, connectorDto);
      log.info("The request body formed for lw data collector API is : {}", jsonObject);
      RequestBody body = RequestBody.create(jsonObject.toString(), MediaType.parse("application/json"));
      NGRestUtils.getResponse(lightwingClient.scheduleAutoCUDDataCollectorJob(body));
      log.info("Connector {}: The data collector api of autocud fired with no exception", action);
    } catch (Exception e) {
      log.info("Connector {}: Error while calling the data collector job of autocud {}", action, e.toString());
    }
  }
}
