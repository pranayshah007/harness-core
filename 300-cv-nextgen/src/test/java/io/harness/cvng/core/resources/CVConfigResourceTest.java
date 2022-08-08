/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.NEMANJA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CVConfigResourceTest extends CvNextGenTestBase {
  @Inject private Injector injector;
  @Inject private static CVConfigResource cvConfigResource = new CVConfigResource();
  @Inject private CVConfigService cvConfigService;

  @ClassRule
  public static final ResourceTestRule RESOURCES = ResourceTestRule.builder().addResource(cvConfigResource).build();

  private String accountId;
  private String connectorIdentifier;
  private String productName;
  private String monitoringSourceIdentifier;
  private String monitoringSourceName;
  private BuilderFactory builderFactory;

  @Before
  public void setup() {
    injector.injectMembers(cvConfigResource);
    builderFactory = BuilderFactory.getDefault();
    this.accountId = builderFactory.getContext().getAccountId();
    this.connectorIdentifier = generateUuid();
    this.productName = generateUuid();
    this.monitoringSourceIdentifier = generateUuid();
    this.monitoringSourceName = generateUuid();
  }
  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetProductNames() {
    List<CVConfig> cvConfigs = Arrays.asList(createCVConfig(), createCVConfig());
    cvConfigs.get(1).setProductName(generateUuid());
    cvConfigService.save(cvConfigs);

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/cv-config/product-names")
                            .queryParam("accountId", accountId)
                            .queryParam("connectorIdentifier", connectorIdentifier)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();

    assertThat(response.getStatus()).isEqualTo(200);
    List<String> retrievedProductNames =
        response.readEntity(new GenericType<RestResponse<List<String>>>() {}).getResource();
    assertThat(retrievedProductNames).contains(cvConfigs.get(0).getProductName());
    assertThat(retrievedProductNames).contains(cvConfigs.get(1).getProductName());
  }

  private CVConfig createCVConfig() {
    return builderFactory.splunkCVConfigBuilder()
        .productName(productName)
        .connectorIdentifier(connectorIdentifier)
        .monitoredServiceIdentifier(monitoringSourceIdentifier)
        .monitoringSourceName(monitoringSourceName)
        .build();
  }
}
