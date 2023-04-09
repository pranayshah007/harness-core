/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.resources;

import static io.harness.rule.OwnerRule.SATHISH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.onboarding.service.OnboardingService;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.HarnessBackstageEntities;
import io.harness.spec.server.idp.v1.model.HarnessEntitiesCountResponse;
import io.harness.spec.server.idp.v1.model.ImportEntitiesResponse;
import io.harness.spec.server.idp.v1.model.ImportHarnessEntitiesRequest;

import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class OnboardingResourceApiImplTest extends CategoryTest {
  static final String ACCOUNT_IDENTIFIER = "123";
  @InjectMocks private OnboardingResourceApiImpl onboardingResourceApiImpl;
  @Mock private OnboardingService onboardingService;
  AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetHarnessEntitiesCount() {
    HarnessEntitiesCountResponse harnessEntitiesCountResponse = new HarnessEntitiesCountResponse();
    harnessEntitiesCountResponse.setOrgCount(1);
    harnessEntitiesCountResponse.setProjectCount(2);
    harnessEntitiesCountResponse.setServiceCount(3);
    when(onboardingService.getHarnessEntitiesCount(ACCOUNT_IDENTIFIER)).thenReturn(harnessEntitiesCountResponse);
    Response response = onboardingResourceApiImpl.getHarnessEntitiesCount(ACCOUNT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    HarnessEntitiesCountResponse harnessEntitiesCountResponseFromApi =
        (HarnessEntitiesCountResponse) response.getEntity();
    assertThat(harnessEntitiesCountResponseFromApi.getOrgCount()).isEqualTo(1);
    assertThat(harnessEntitiesCountResponseFromApi.getProjectCount()).isEqualTo(2);
    assertThat(harnessEntitiesCountResponseFromApi.getServiceCount()).isEqualTo(3);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetHarnessEntities() {
    List<HarnessBackstageEntities> harnessBackstageEntities = new ArrayList<>();
    harnessBackstageEntities.add(new HarnessBackstageEntities()
                                     .name("entityName")
                                     .entityType("entityType")
                                     .identifier("entityIdentifier")
                                     .type("type"));
    PageResponse<HarnessBackstageEntities> harnessBackstageEntitiesPageResponse =
        PageResponse.<HarnessBackstageEntities>builder()
            .pageSize(10)
            .pageIndex(0)
            .totalPages(1)
            .totalItems(1)
            .pageItemCount(1)
            .content(harnessBackstageEntities)
            .build();
    when(onboardingService.getHarnessEntities(ACCOUNT_IDENTIFIER, 0, 10, null, null, null, null))
        .thenReturn(harnessBackstageEntitiesPageResponse);
    Response response = onboardingResourceApiImpl.getHarnessEntities(ACCOUNT_IDENTIFIER, 0, 10, null, null, null, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    List<HarnessBackstageEntities> harnessBackstageEntitiesFromApi =
        (List<HarnessBackstageEntities>) response.getEntity();
    assertThat(harnessBackstageEntitiesFromApi).hasSize(1);
    assertThat(harnessBackstageEntitiesFromApi.get(0).getName()).isEqualTo("entityName");
    assertThat(harnessBackstageEntitiesFromApi.get(0).getEntityType()).isEqualTo("entityType");
    assertThat(harnessBackstageEntitiesFromApi.get(0).getIdentifier()).isEqualTo("entityIdentifier");
    assertThat(harnessBackstageEntitiesFromApi.get(0).getType()).isEqualTo("type");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testImportHarnessEntities() {
    ImportEntitiesResponse importEntitiesResponse = new ImportEntitiesResponse();
    importEntitiesResponse.setStatus("SUCCESS");
    when(onboardingService.importHarnessEntities(ACCOUNT_IDENTIFIER, new ImportHarnessEntitiesRequest()))
        .thenReturn(importEntitiesResponse);
    Response response =
        onboardingResourceApiImpl.importHarnessEntities(new ImportHarnessEntitiesRequest(), ACCOUNT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    ImportEntitiesResponse importEntitiesResponseFromApi = (ImportEntitiesResponse) response.getEntity();
    assertThat(importEntitiesResponseFromApi.getStatus()).isEqualTo("SUCCESS");
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
