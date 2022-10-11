/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.core.resources;

import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.RiskCategoryDTO;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class RiskCategoryResourceTest extends CvNextGenTestBase {
  @Inject private Injector injector;

  private BuilderFactory builderFactory;
  @Inject private static RiskCategoryResource riskCategoryResource = new RiskCategoryResource();

  @ClassRule
  public static final ResourceTestRule RESOURCES = ResourceTestRule.builder().addResource(riskCategoryResource).build();

  @Before
  public void setup() {
    injector.injectMembers(riskCategoryResource);
    builderFactory = BuilderFactory.getDefault();
  }
  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetRiskCategory() {
    List<RiskCategoryDTO> riskCategoryList = builderFactory.getRiskCategoryList();
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/" + CVNextGenConstants.CVNG_RISK_CATEGORY_PATH)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(200);
    List<RiskCategoryDTO> retrievedRiskCategories =
        response.readEntity(new GenericType<RestResponse<List<RiskCategoryDTO>>>() {}).getResource();
    List<String> displayNameList =
        retrievedRiskCategories.stream().map(RiskCategoryDTO::getDisplayName).collect(Collectors.toList());
    assertThat(displayNameList).contains(riskCategoryList.get(0).getDisplayName());
    assertThat(displayNameList).contains(riskCategoryList.get(1).getDisplayName());
  }
}
