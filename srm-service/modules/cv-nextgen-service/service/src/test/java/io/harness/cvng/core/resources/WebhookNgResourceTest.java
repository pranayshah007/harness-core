/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.services.api.WebhookService;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class WebhookNgResourceTest extends CvNextGenTestBase {
  @Inject private Injector injector;
  @Inject private static WebhookNgResource webhookNgResource = new WebhookNgResource();
  @ClassRule
  public static final ResourceTestRule RESOURCES = ResourceTestRule.builder().addResource(webhookNgResource).build();

  @Before
  public void setup() throws IllegalAccessException {
    injector.injectMembers(webhookNgResource);
    WebhookService mockWebhookService = mock(WebhookService.class);
    FieldUtils.writeField(webhookNgResource, "webhookService", mockWebhookService, true);
    doNothing().when(mockWebhookService).checkAuthorization(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void registerEventInCustomChangeSource() throws IOException {
    BuilderFactory builderFactory = BuilderFactory.getDefault();
    String changeSourceYaml = getResource("change-source/custom-change-source.yaml");
    Entity<String> entity = Entity.json(convertToJson(changeSourceYaml));
    String url = "http://localhost:9998/account/" + builderFactory.getContext().getAccountId() + "/org/"
        + builderFactory.getContext().getOrgIdentifier() + "/project/"
        + builderFactory.getContext().getProjectIdentifier() + "/webhook/custom-change";
    Response response = RESOURCES.client()
                            .target(url)
                            .queryParam("monitoredServiceIdentifier", generateUuid())
                            .queryParam("changeSourceIdentifier", generateUuid())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(entity);
    assertThat(response.getStatus()).isEqualTo(204);
  }
}
