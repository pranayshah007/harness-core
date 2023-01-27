/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeSource;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.CustomChangeSourceSpec;
import io.harness.cvng.core.entities.changeSource.CustomChangeSource;
import io.harness.cvng.core.services.api.WebhookConfigService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;

public class CustomChangeSourceSpecTransformerTest extends CvNextGenTestBase {
  private BuilderFactory builderFactory;

  @Inject private CustomChangeSourceSpecTransformer customChangeSourceSpecTransformer;

  @Mock private WebhookConfigService webhookConfigService;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    Mockito.when(webhookConfigService.getWebhookApiBaseUrl()).thenReturn("testUrl");
    FieldUtils.writeField(customChangeSourceSpecTransformer, "webhookConfigService", webhookConfigService, true);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void getEntity() {
    ChangeSourceDTO changeSourceDTO =
        builderFactory.getCustomChangeSourceDTOBuilder(ChangeSourceType.CUSTOM_DEPLOY).build();
    CustomChangeSource changeSource = customChangeSourceSpecTransformer.getEntity(
        builderFactory.getContext().getMonitoredServiceParams(), changeSourceDTO);
    assertThat(changeSource.getClass()).isEqualTo(CustomChangeSource.class);
    assertThat(changeSource.getIdentifier()).isEqualTo(changeSourceDTO.getIdentifier());
    assertThat(changeSource.getName()).isEqualTo(changeSourceDTO.getName());
    assertThat(changeSource.getAccountId()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(changeSource.getProjectIdentifier()).isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(changeSource.getMonitoredServiceIdentifier())
        .isEqualTo(builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier());
    assertThat(changeSource.isEnabled()).isTrue();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void getSpec() {
    CustomChangeSource changeSource =
        builderFactory.getCustomChangeSourceBuilder(ChangeSourceType.CUSTOM_DEPLOY).build();
    CustomChangeSourceSpec changeSourceSpec = customChangeSourceSpecTransformer.getSpec(changeSource);
    assertThat(changeSourceSpec.getName()).isEqualTo(changeSource.getName());
    assertThat(changeSourceSpec.getType()).isEqualTo(changeSource.getType());
    assertThat(changeSourceSpec.getWebhookUrl()).isNotEmpty();
    assertThat(changeSourceSpec.getWebhookCurlCommand()).isNotEmpty();
  }
}
