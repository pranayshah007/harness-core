/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.application;

import static io.harness.beans.FeatureName.GITHUB_WEBHOOK_AUTHENTICATION;
import static io.harness.beans.FeatureName.SPG_ALLOW_DISABLE_TRIGGERS;
import static io.harness.beans.FeatureName.WEBHOOK_TRIGGER_AUTHORIZATION;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATIONS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.Application;
import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.application.input.QLCreateApplicationInput;
import software.wings.graphql.schema.mutation.application.payload.QLCreateApplicationPayload;
import software.wings.graphql.schema.type.QLApplication;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AppService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class CreateApplicationDataFetcher
    extends BaseMutatorDataFetcher<QLCreateApplicationInput, QLCreateApplicationPayload> {
  private AppService appService;
  private FeatureFlagService featureFlagService;

  @Inject
  public CreateApplicationDataFetcher(AppService appService, FeatureFlagService featureFlagService) {
    super(QLCreateApplicationInput.class, QLCreateApplicationPayload.class);
    this.appService = appService;
    this.featureFlagService = featureFlagService;
  }

  private Application prepareApplication(QLCreateApplicationInput qlApplicationInput, String accountId) {
    return Application.Builder.anApplication()
        .name(qlApplicationInput.getName())
        .description(qlApplicationInput.getDescription())
        .accountId(accountId)
        .isManualTriggerAuthorized(qlApplicationInput.getIsManualTriggerAuthorized())
        .areWebHookSecretsMandated(qlApplicationInput.getAreWebHookSecretsMandated())
        .disableTriggers(qlApplicationInput.getDisableTriggers())
        .build();
  }
  private QLApplication prepareQLApplication(Application savedApplication) {
    return ApplicationController.populateQLApplication(savedApplication, QLApplication.builder()).build();
  }

  @Override
  @AuthRule(permissionType = MANAGE_APPLICATIONS, action = PermissionAttribute.Action.CREATE)
  protected QLCreateApplicationPayload mutateAndFetch(
      QLCreateApplicationInput parameter, MutationContext mutationContext) {
    if (Boolean.TRUE.equals(parameter.getIsManualTriggerAuthorized())
        && !featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, mutationContext.getAccountId())) {
      throw new InvalidRequestException("Please enable feature flag to authorize manual triggers");
    }

    if (Boolean.TRUE.equals(parameter.getAreWebHookSecretsMandated())
        && !featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, mutationContext.getAccountId())) {
      throw new InvalidRequestException("Please enable feature flag to mandate git webhooks secrets");
    }

    if (Boolean.TRUE.equals(parameter.getDisableTriggers())
        && !featureFlagService.isEnabled(SPG_ALLOW_DISABLE_TRIGGERS, mutationContext.getAccountId())) {
      throw new InvalidRequestException("Please enable feature flag to disable triggers");
    }
    final Application savedApplication = appService.save(prepareApplication(parameter, mutationContext.getAccountId()));
    return QLCreateApplicationPayload.builder()
        .clientMutationId(parameter.getClientMutationId())
        .application(prepareQLApplication(savedApplication))
        .build();
  }
}
