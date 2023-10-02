/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service.impl;

import static io.harness.rule.OwnerRule.ABHINAV;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.rule.Owner;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NGTriggerServiceImplTest {
  @InjectMocks NGTriggerServiceImpl ngTriggerService;
  @Mock AccessControlClient accessControlClient;

  private final String IDENTIFIER = "first_trigger";
  private final String NAME = "first trigger";
  private final String PIPELINE_IDENTIFIER = "myPipeline";
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private String ngTriggerYaml;
  NGTriggerEntity ngTriggerEntity;
  private NGTriggerConfigV2 ngTriggerConfig;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    ClassLoader classLoader = getClass().getClassLoader();
    String filename = "ng-trigger-github-pr-v2.yaml";
    ngTriggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    ngTriggerConfig = YamlPipelineUtils.read(ngTriggerYaml, NGTriggerConfigV2.class);
    WebhookTriggerConfigV2 webhookTriggerConfig = (WebhookTriggerConfigV2) ngTriggerConfig.getSource().getSpec();
    WebhookMetadata metadata = WebhookMetadata.builder().type(webhookTriggerConfig.getType().getValue()).build();
    NGTriggerMetadata ngTriggerMetadata = NGTriggerMetadata.builder().webhook(metadata).build();

    ngTriggerEntity = NGTriggerEntity.builder()
                          .accountId(ACCOUNT_ID)
                          .orgIdentifier(ORG_IDENTIFIER)
                          .projectIdentifier(PROJ_IDENTIFIER)
                          .targetIdentifier(PIPELINE_IDENTIFIER)
                          .identifier(IDENTIFIER)
                          .name(NAME)
                          .targetType(TargetType.PIPELINE)
                          .type(NGTriggerType.WEBHOOK)
                          .metadata(ngTriggerMetadata)
                          .yaml(ngTriggerYaml)
                          .version(0L)
                          .build();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testSCMAccessCheck() {
    ngTriggerService.checkForAccessForHarnessScm(ngTriggerEntity);
    verify(accessControlClient, times(0)).checkForAccessOrThrow(any(), any(), any());
    final NGTriggerEntity finalNgTriggerEntity = ngTriggerEntity;
    finalNgTriggerEntity.setMetadata(
        NGTriggerMetadata.builder()
            .webhook(
                WebhookMetadata.builder().type("HARNESS").git(GitMetadata.builder().isHarnessScm(true).build()).build())
            .build());
    ngTriggerService.checkForAccessForHarnessScm(finalNgTriggerEntity);
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
  }
}
