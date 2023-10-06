/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.trigger;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.BitbucketSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketTriggerEvent;

import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.ngmigration.CgEntityId;

import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
public class BitbucketWebhookHandlerImpl implements WebhookHandler {
  @Override
  public WebhookTriggerConfigV2 getConfig(WebHookTriggerCondition condition, Map<CgEntityId, NGYamlFile> yamlFileMap) {
    return WebhookTriggerConfigV2.builder()
        .spec(BitbucketSpec.builder().type(BitbucketTriggerEvent.PUSH).build())
        .type(WebhookTriggerType.BITBUCKET)
        .build();
  }
}
