/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.SPG_WORKFLOW_RBAC_ON_TRIGGER_RESOURCE;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import software.wings.beans.trigger.Trigger;
import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.type.trigger.QLCreateOrUpdateTriggerInput;
import software.wings.graphql.schema.type.trigger.QLTriggerPayload;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.TriggerService;

import com.google.inject.Inject;

@OwnedBy(CDC)
public class UpdateTriggerDataFetcher extends BaseMutatorDataFetcher<QLCreateOrUpdateTriggerInput, QLTriggerPayload> {
  @Inject TriggerController triggerController;
  @Inject FeatureFlagService featureFlagService;
  private TriggerService triggerService;

  @Inject
  public UpdateTriggerDataFetcher(TriggerService triggerService) {
    super(QLCreateOrUpdateTriggerInput.class, QLTriggerPayload.class);
    this.triggerService = triggerService;
  }

  @Override
  @AuthRule(permissionType = LOGGED_IN)
  protected QLTriggerPayload mutateAndFetch(QLCreateOrUpdateTriggerInput parameter, MutationContext mutationContext) {
    try (AutoLogContext ignore0 =
             new AccountLogContext(mutationContext.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      final Trigger trigger = triggerController.prepareTrigger(parameter, mutationContext.getAccountId());
      if (featureFlagService.isEnabled(SPG_WORKFLOW_RBAC_ON_TRIGGER_RESOURCE, mutationContext.getAccountId())) {
        triggerService.authorizeUpdate(trigger);
      }
      final Trigger savedTrigger = triggerService.update(trigger, false);
      return triggerController.prepareQLTrigger(
          savedTrigger, parameter.getClientMutationId(), mutationContext.getAccountId());
    }
  }
}
