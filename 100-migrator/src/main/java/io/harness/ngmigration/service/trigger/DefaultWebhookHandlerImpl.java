/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.trigger;

import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.custom.CustomTriggerSpec;

import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.ngmigration.CgEntityId;

import java.util.Map;

public class DefaultWebhookHandlerImpl implements WebhookHandler {
  @Override
  public WebhookTriggerConfigV2 getConfig(WebHookTriggerCondition condition, Map<CgEntityId, NGYamlFile> yamlFileMap) {
    return WebhookTriggerConfigV2.builder()
        .spec(CustomTriggerSpec.builder().jexlCondition("__PLEASE_FIX_ME__").build())
        .type(WebhookTriggerType.CUSTOM)
        .build();
  }
}
