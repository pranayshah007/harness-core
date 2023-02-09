/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import io.harness.CvNextGenTestBase;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;

public class WebhookResourceTest extends CvNextGenTestBase {
  @Inject private Injector injector;
  @Mock private AccessControlClient accessControlClient;
  private BuilderFactory builderFactory;
  private static WebhookResource webhookResource = new WebhookResource();

  @ClassRule
  public static final ResourceTestRule RESOURCES = ResourceTestRule.builder().addResource(webhookResource).build();

  @Before
  public void setup() {
    injector.injectMembers(webhookResource);
    builderFactory = BuilderFactory.getDefault();
    Mockito.when(accessControlClient.hasAccess(any(), any(), any())).thenReturn(true);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testHandleCustomChangeWebhookRequest() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    Response response =
        RESOURCES.client()
            .target("http://localhost:9998/webhook/custom-change")
            .queryParam("accountIdentifier", builderFactory.getContext().getAccountId())
            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
            .queryParam("monitoredServiceIdentifier", "monitoredServiceId")
            .queryParam("changeSourceIdentifier", "changeSourceId")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header("X-Api-Key", "test")
            .post(Entity.json(objectMapper.writeValueAsString(builderFactory.getCustomChangeWebhookPayload().build())));

    assertThat(response.getStatus()).isEqualTo(204);
  }
}
