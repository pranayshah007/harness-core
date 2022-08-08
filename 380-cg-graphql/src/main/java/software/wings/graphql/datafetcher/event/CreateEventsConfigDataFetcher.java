/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.event;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATIONS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CgEventConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.service.EventConfigService;

import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.event.EventsConfigValidationHelper;
import software.wings.graphql.schema.mutation.event.input.QLCreateEventsConfigInput;
import software.wings.graphql.schema.mutation.event.payload.QLCreateEventsConfigPayload;
import software.wings.graphql.schema.type.event.QLEventsConfig;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AppService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class CreateEventsConfigDataFetcher
    extends BaseMutatorDataFetcher<QLCreateEventsConfigInput, QLCreateEventsConfigPayload> {
  @Inject private EventConfigService eventConfigService;
  @Inject private AppService appService;
  @Inject private EventsConfigValidationHelper eventsConfigValidationHelper;

  public CreateEventsConfigDataFetcher() {
    super(QLCreateEventsConfigInput.class, QLCreateEventsConfigPayload.class);
  }

  @Override
  @AuthRule(permissionType = MANAGE_APPLICATIONS, action = PermissionAttribute.Action.CREATE)
  protected QLCreateEventsConfigPayload mutateAndFetch(
      QLCreateEventsConfigInput parameter, MutationContext mutationContext) {
    String accountId = mutationContext.getAccountId();
    if (!appService.exist(parameter.getAppId())) {
      throw new InvalidRequestException("Application does not exist");
    }
    CgEventConfig cgeventConfig = CgEventConfig.builder()
                                      .appId(parameter.getAppId())
                                      .accountId(accountId)
                                      .name(parameter.getName())
                                      .config(parameter.getWebhookConfig())
                                      .rule(parameter.getRule())
                                      .delegateSelectors(parameter.getDelegateSelectors())
                                      .enabled(parameter.isEnabled())
                                      .build();
    eventsConfigValidationHelper.validatePipelineIds(cgeventConfig, accountId, parameter.getAppId());
    eventsConfigValidationHelper.validateWorkflowIds(cgeventConfig, accountId, parameter.getAppId());
    CgEventConfig eventConfig = eventConfigService.createEventsConfig(accountId, parameter.getAppId(), cgeventConfig);
    return QLCreateEventsConfigPayload.builder()
        .clientMutationId(parameter.getClientMutationId())
        .eventsConfig(QLEventsConfig.getQLEventsConfig(eventConfig))
        .build();
  }
}
