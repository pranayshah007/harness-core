/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.event;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CgEventConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.service.EventConfigService;

import software.wings.graphql.datafetcher.AbstractObjectDataFetcher;
import software.wings.graphql.schema.query.QLEventsConfigQueryParameters;
import software.wings.graphql.schema.type.event.QLEventsConfig;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AppService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(CDC)
public class EventsConfigDataFetcher extends AbstractObjectDataFetcher<QLEventsConfig, QLEventsConfigQueryParameters> {
  @Inject EventConfigService eventConfigService;
  @Inject private AppService appService;

  @Override
  @AuthRule(permissionType = LOGGED_IN, action = PermissionAttribute.Action.READ)
  protected QLEventsConfig fetch(QLEventsConfigQueryParameters qlQuery, String accountId) {
    if (!appService.exist(qlQuery.getAppId())) {
      throw new InvalidRequestException("Application does not exist");
    }
    CgEventConfig cgEventConfig = null;
    if (StringUtils.isNotBlank(qlQuery.getEventsConfigId())) {
      cgEventConfig = eventConfigService.getEventsConfig(accountId, qlQuery.getAppId(), qlQuery.getEventsConfigId());
    }
    if (StringUtils.isNotBlank(qlQuery.getName())) {
      cgEventConfig = eventConfigService.getEventsConfigByName(accountId, qlQuery.getAppId(), qlQuery.getName());
    }
    QLEventsConfig qlEventsConfig = QLEventsConfig.getQLEventsConfig(cgEventConfig);
    if (qlEventsConfig == null) {
      throw new InvalidRequestException("Events Config Does Not Exist", WingsException.USER);
    }
    return qlEventsConfig;
  }
}
