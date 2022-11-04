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

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATIONS;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.utils.RequestField;

import software.wings.beans.Application;
import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.application.input.QLUpdateApplicationInput;
import software.wings.graphql.schema.mutation.application.input.QLUpdateApplicationInput.QLUpdateApplicationInputKeys;
import software.wings.graphql.schema.mutation.application.payload.QLUpdateApplicationPayload;
import software.wings.graphql.schema.type.QLApplication;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AppService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class UpdateApplicationDataFetcher
    extends BaseMutatorDataFetcher<QLUpdateApplicationInput, QLUpdateApplicationPayload> {
  private AppService appService;
  private FeatureFlagService featureFlagService;

  @Inject
  public UpdateApplicationDataFetcher(AppService appService, FeatureFlagService featureFlagService) {
    super(QLUpdateApplicationInput.class, QLUpdateApplicationPayload.class);
    this.appService = appService;
    this.featureFlagService = featureFlagService;
  }

  private Application prepareApplication(
      QLUpdateApplicationInput qlUpdateApplicationInput, Application existingApplication) {
    final Application.Builder applicationBuilder =
        anApplication()
            .uuid(existingApplication.getUuid())
            .appId(existingApplication.getAppId())
            .accountId(existingApplication.getAccountId())
            .name(existingApplication.getName())
            .description(existingApplication.getDescription())
            .isManualTriggerAuthorized(existingApplication.getIsManualTriggerAuthorized())
            .areWebHookSecretsMandated(existingApplication.getAreWebHookSecretsMandated())
            .disableTriggers(existingApplication.getDisableTriggers())
            .yamlGitConfig(existingApplication.getYamlGitConfig()); // yaml config because the way update is written, it
                                                                    // assumes this would be coming

    if (qlUpdateApplicationInput.getName().isPresent()) {
      applicationBuilder.name(qlUpdateApplicationInput.getName().getValue().map(StringUtils::strip).orElse(null));
    }
    if (qlUpdateApplicationInput.getDescription().isPresent()) {
      applicationBuilder.description(qlUpdateApplicationInput.getDescription().getValue().orElse(null));
    }

    Boolean isManualTriggerAuthorized = qlUpdateApplicationInput.getIsManualTriggerAuthorized();
    if (Boolean.TRUE.equals(isManualTriggerAuthorized)
        && !featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, existingApplication.getAccountId())) {
      throw new InvalidRequestException("Please enable feature flag to authorize manual triggers");
    }

    if (isManualTriggerAuthorized != null) {
      applicationBuilder.isManualTriggerAuthorized(isManualTriggerAuthorized);
    } else if (existingApplication.getIsManualTriggerAuthorized() != null) {
      applicationBuilder.isManualTriggerAuthorized(false);
    }

    Boolean areWebHookSecretsMandated = qlUpdateApplicationInput.getAreWebHookSecretsMandated();

    if (Boolean.TRUE.equals(areWebHookSecretsMandated)
        && !featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, existingApplication.getAccountId())) {
      throw new InvalidRequestException("Please enable feature flag to mandate git webhooks secrets");
    }
    if (areWebHookSecretsMandated != null) {
      applicationBuilder.areWebHookSecretsMandated(areWebHookSecretsMandated);
    } else if (existingApplication.getAreWebHookSecretsMandated() != null) {
      applicationBuilder.areWebHookSecretsMandated(false);
    }

    Boolean disableTriggers = qlUpdateApplicationInput.getDisableTriggers();

    if (Boolean.TRUE.equals(disableTriggers)
        && !featureFlagService.isEnabled(SPG_ALLOW_DISABLE_TRIGGERS, existingApplication.getAccountId())) {
      throw new InvalidRequestException("Please enable feature flag to disable triggers");
    }
    if (disableTriggers != null) {
      applicationBuilder.disableTriggers(disableTriggers);
    } else if (existingApplication.getDisableTriggers() != null) {
      applicationBuilder.disableTriggers(false);
    }

    return applicationBuilder.build();
  }

  private QLApplication prepareQLApplication(Application savedApplication) {
    return ApplicationController.populateQLApplication(savedApplication, QLApplication.builder()).build();
  }

  @Override
  @AuthRule(permissionType = MANAGE_APPLICATIONS, action = PermissionAttribute.Action.UPDATE)
  protected QLUpdateApplicationPayload mutateAndFetch(
      QLUpdateApplicationInput parameter, MutationContext mutationContext) {
    validate(parameter);
    final Application existingApplication = appService.get(parameter.getApplicationId());
    final Application updatedApp = appService.update(prepareApplication(parameter, existingApplication));
    return QLUpdateApplicationPayload.builder()
        .clientMutationId(parameter.getClientMutationId())
        .application(prepareQLApplication(updatedApp))
        .build();
  }

  private void validate(QLUpdateApplicationInput parameter) {
    final RequestField<String> nameRF = parameter.getName();
    if (nameRF.isPresent()) {
      ensureNonEmptyStringField(nameRF.getValue().orElse(null), QLUpdateApplicationInputKeys.name);
    }
  }

  private void ensureNonEmptyStringField(String field, String fieldName) {
    if (StringUtils.isBlank(field)) {
      throw new InvalidRequestException(format("Field: [%s] is required", fieldName));
    }
  }
}
