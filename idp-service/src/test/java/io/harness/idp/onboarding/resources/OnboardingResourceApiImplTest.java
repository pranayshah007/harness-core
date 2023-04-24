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
import io.harness.spec.server.idp.v1.model.GenerateYamlRequest;
import io.harness.spec.server.idp.v1.model.GenerateYamlResponse;
import io.harness.spec.server.idp.v1.model.HarnessBackstageEntities;
import io.harness.spec.server.idp.v1.model.HarnessEntitiesCountResponse;
import io.harness.spec.server.idp.v1.model.HarnessEntitiesResponse;
import io.harness.spec.server.idp.v1.model.ImportEntitiesBase;
import io.harness.spec.server.idp.v1.model.ImportEntitiesResponse;

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
  static final String GENERATE_YAML_DESC =
      "This is an example of how the corresponding service definition YAML files will be created.";
  static final String GENERATE_YAML_DEF =
      "apiVersion: backstage.io/v1alpha1\nkind: Component\nmetadata:\n  name: my-example-service\n  description: |\n    My Example service which has something to do with APIs and database.\n  links:\n    - title: Website\n      url: http://my-internal-website.com\n  annotations:\n    github.com/project-slug: myorg/myrepo\n    backstage.io/techdocs-ref: dir:.\n    lighthouse.com/website-url: https://harness.io\n# labels:\n#   key1: value1\n# tags: \nspec:\n  type: service\n  owner: my-team\n  lifecycle: experimental\n  system: my-project\n#  dependsOn:\n#    - resource:default/my-db\n#  consumesApis:\n#    - user-api\n#  providesApis:\n#    - example-api";
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
    HarnessEntitiesResponse harnessBackstageEntitiesFromApi = (HarnessEntitiesResponse) response.getEntity();
    List<HarnessBackstageEntities> harnessBackstageEntitiesListFromApi =
        harnessBackstageEntitiesFromApi.getHarnessBackstageEntities();
    assertThat(harnessBackstageEntitiesListFromApi).hasSize(1);
    assertThat(harnessBackstageEntitiesListFromApi.get(0).getName()).isEqualTo("entityName");
    assertThat(harnessBackstageEntitiesListFromApi.get(0).getEntityType()).isEqualTo("entityType");
    assertThat(harnessBackstageEntitiesListFromApi.get(0).getIdentifier()).isEqualTo("entityIdentifier");
    assertThat(harnessBackstageEntitiesListFromApi.get(0).getType()).isEqualTo("type");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testOnboardingGenerateYaml() {
    GenerateYamlResponse generateYamlResponse = new GenerateYamlResponse();
    generateYamlResponse.setDescription(GENERATE_YAML_DESC);
    generateYamlResponse.setYamlDef(GENERATE_YAML_DEF);
    when(onboardingService.generateYaml(ACCOUNT_IDENTIFIER, new GenerateYamlRequest()))
        .thenReturn(generateYamlResponse);
    Response response = onboardingResourceApiImpl.onboardingGenerateYaml(new GenerateYamlRequest(), ACCOUNT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    GenerateYamlResponse generateYamlResponseFromApi = (GenerateYamlResponse) response.getEntity();
    assertThat(generateYamlResponseFromApi.getDescription()).isEqualTo(GENERATE_YAML_DESC);
    assertThat(generateYamlResponseFromApi.getYamlDef()).isEqualTo(GENERATE_YAML_DEF);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testImportHarnessEntities() {
    ImportEntitiesResponse importEntitiesResponse = new ImportEntitiesResponse();
    importEntitiesResponse.setStatus("SUCCESS");
    when(onboardingService.importHarnessEntities(ACCOUNT_IDENTIFIER, new ImportEntitiesBase()))
        .thenReturn(importEntitiesResponse);
    Response response = onboardingResourceApiImpl.importHarnessEntities(new ImportEntitiesBase(), ACCOUNT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    ImportEntitiesResponse importEntitiesResponseFromApi = (ImportEntitiesResponse) response.getEntity();
    assertThat(importEntitiesResponseFromApi.getStatus()).isEqualTo("SUCCESS");
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
