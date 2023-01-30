/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.trigger;

import static io.harness.beans.FeatureName.SPG_WORKFLOW_RBAC_ON_TRIGGER_RESOURCE;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.trigger.Trigger;
import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.type.trigger.QLDeleteTriggerInput;
import software.wings.graphql.schema.type.trigger.QLDeleteTriggerPayload;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.TriggerService;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.CDC)
public class DeleteTriggerDataFetcher extends BaseMutatorDataFetcher<QLDeleteTriggerInput, QLDeleteTriggerPayload> {
  TriggerService triggerService;
  AppService appService;
  @Inject private FeatureFlagService featureFlagService;

  @Inject
  public DeleteTriggerDataFetcher(TriggerService triggerService, AppService appService) {
    super(QLDeleteTriggerInput.class, QLDeleteTriggerPayload.class);
    this.triggerService = triggerService;
    this.appService = appService;
  }

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  protected QLDeleteTriggerPayload mutateAndFetch(QLDeleteTriggerInput parameter, MutationContext mutationContext) {
    String appId = parameter.getApplicationId();
    String triggerId = parameter.getTriggerId();

    if (EmptyPredicate.isEmpty(appId)) {
      throw new InvalidRequestException("ApplicationId must not be empty", USER);
    }

    if (!mutationContext.getAccountId().equals(appService.getAccountIdByAppId(appId))) {
      throw new InvalidRequestException("ApplicationId doesn't belong to this account", USER);
    }

    Trigger trigger = triggerService.get(appId, triggerId);
    notNullCheck("Trigger does not exist in the application", trigger);

    if (triggerService.triggerActionExists(trigger)) {
      triggerService.authorize(trigger, true);
    }
    if (featureFlagService.isEnabled(SPG_WORKFLOW_RBAC_ON_TRIGGER_RESOURCE, mutationContext.getAccountId())) {
      triggerService.authorizeDeletion(trigger);
    }
    triggerService.delete(appId, triggerId);

    if (triggerService.get(appId, triggerId) != null) {
      throw new InvalidRequestException("Could not delete Trigger", USER);
    }

    return QLDeleteTriggerPayload.builder().clientMutationId(parameter.getClientMutationId()).build();
  }
}
