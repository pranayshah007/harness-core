/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.SPG_WORKFLOW_RBAC_ON_TRIGGER_RESOURCE;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;

import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerKeys;
import software.wings.graphql.datafetcher.AbstractObjectDataFetcher;
import software.wings.graphql.schema.query.QLTriggerQueryParameters;
import software.wings.graphql.schema.type.trigger.QLTrigger;
import software.wings.graphql.schema.type.trigger.QLTrigger.QLTriggerBuilder;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;
import software.wings.service.impl.trigger.TriggerAuthHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.TriggerService;

import com.google.inject.Inject;
import java.util.Collections;

@OwnedBy(CDC)
public class TriggerDataFetcher extends AbstractObjectDataFetcher<QLTrigger, QLTriggerQueryParameters> {
  private static final String EMPTY_TRIGGER_NAME = "Trigger Name should not be empty";
  @Inject HPersistence persistence;
  @Inject AppService appService;
  @Inject TriggerAuthHandler triggerAuthHandler;
  @Inject private TriggerService triggerService;
  @Inject TriggerController triggerController;
  @Inject private FeatureFlagService featureFlagService;
  public static final String EMPTY_APPLICATION_ID = "Application Id should not be empty";

  @Override
  @AuthRule(permissionType = PermissionType.LOGGED_IN)
  protected QLTrigger fetch(QLTriggerQueryParameters parameters, String accountId) {
    Trigger trigger = null;
    if (parameters.getTriggerId() != null) {
      trigger = persistence.get(Trigger.class, parameters.getTriggerId());
      if (trigger == null) {
        return null;
      }
    } else if (parameters.getTriggerName() != null) {
      if (EmptyPredicate.isEmpty(parameters.getApplicationId())) {
        throw new InvalidRequestException(EMPTY_APPLICATION_ID, WingsException.USER);
      }

      if (EmptyPredicate.isEmpty(parameters.getTriggerName())) {
        throw new InvalidRequestException(EMPTY_TRIGGER_NAME, WingsException.USER);
      }
      trigger = persistence.createQuery(Trigger.class)
                    .filter(TriggerKeys.name, parameters.getTriggerName())
                    .filter(TriggerKeys.appId, parameters.getApplicationId())
                    .get();
      if (trigger == null) {
        throw new InvalidRequestException(String.format("Trigger %s does not exist in given application %s",
                                              parameters.getTriggerName(), parameters.getApplicationId()),
            USER);
      }
    }

    if (!accountId.equals(appService.getAccountIdByAppId(trigger.getAppId()))) {
      throw new InvalidRequestException("Trigger doesn't exist", USER);
    }
    triggerAuthHandler.authorizeAppAccess(Collections.singletonList(trigger.getAppId()), accountId);
    if (featureFlagService.isEnabled(SPG_WORKFLOW_RBAC_ON_TRIGGER_RESOURCE, trigger.getAccountId())) {
      triggerService.authorizeRead(trigger);
    }

    QLTriggerBuilder qlTriggerBuilder = QLTrigger.builder();
    triggerController.populateTrigger(trigger, qlTriggerBuilder, accountId);
    return qlTriggerBuilder.build();
  }
}
