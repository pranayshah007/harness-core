/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.service;

import static io.harness.idp.onboarding.utils.Constants.SERVICE;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.events.producers.IdpEntityCrudStreamProducer;
import io.harness.idp.gitintegration.processor.factory.ConnectorProcessorFactory;
import io.harness.idp.gitintegration.processor.impl.GithubConnectorProcessor;
import io.harness.idp.gitintegration.repositories.CatalogConnectorRepository;
import io.harness.idp.gitintegration.service.GitIntegrationService;
import io.harness.idp.onboarding.beans.AsyncCatalogImportDetails;
import io.harness.idp.onboarding.beans.BackstageCatalogComponentEntity;
import io.harness.idp.onboarding.beans.BackstageCatalogDomainEntity;
import io.harness.idp.onboarding.beans.BackstageCatalogEntity;
import io.harness.idp.onboarding.beans.BackstageCatalogSystemEntity;
import io.harness.idp.onboarding.client.FakeOrganizationClient;
import io.harness.idp.onboarding.client.FakeProjectClient;
import io.harness.idp.onboarding.client.FakeServiceResourceClient;
import io.harness.idp.onboarding.config.OnboardingModuleConfig;
import io.harness.idp.onboarding.entities.AsyncCatalogImportEntity;
import io.harness.idp.onboarding.mappers.HarnessOrgToBackstageDomain;
import io.harness.idp.onboarding.mappers.HarnessProjectToBackstageSystem;
import io.harness.idp.onboarding.mappers.HarnessServiceToBackstageComponent;
import io.harness.idp.onboarding.repositories.AsyncCatalogImportRepository;
import io.harness.idp.onboarding.service.impl.OnboardingServiceImpl;
import io.harness.idp.onboarding.utils.FileUtils;
import io.harness.idp.status.service.StatusInfoService;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.AllEntitiesImport;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;
import io.harness.spec.server.idp.v1.model.ConnectorDetails;
import io.harness.spec.server.idp.v1.model.EntitiesForImport;
import io.harness.spec.server.idp.v1.model.GenerateYamlRequest;
import io.harness.spec.server.idp.v1.model.GenerateYamlResponse;
import io.harness.spec.server.idp.v1.model.HarnessBackstageEntities;
import io.harness.spec.server.idp.v1.model.HarnessEntitiesCountResponse;
import io.harness.spec.server.idp.v1.model.ImportEntitiesBase;
import io.harness.spec.server.idp.v1.model.ImportEntitiesResponse;
import io.harness.spec.server.idp.v1.model.IndividualEntitiesImport;
import io.harness.spec.server.idp.v1.model.SampleEntitiesImport;
import io.harness.springdata.TransactionHelper;

import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class OnboardingServiceImplTest extends CategoryTest {
  static final String ACCOUNT_IDENTIFIER = "123";
  static final String ACCOUNT_IDENTIFIER_DUMMY = "dummy_account_identifier";
  static final String TEST_PROJECT_IDENTIFIER = "projectId";
  static final String TEST_SERVICE_IDENTIFIER = "serviceId";
  static final String GENERATE_YAML_DEF =
      "apiVersion: backstage.io/v1alpha1\nkind: Component\nmetadata:\n  name: my-example-service\n  description: |\n    My Example service which has something to do with APIs and database.\n  links:\n    - title: Website\n      url: http://my-internal-website.com\n  annotations:\n    github.com/project-slug: myorg/myrepo\n    backstage.io/techdocs-ref: dir:.\n    lighthouse.com/website-url: https://harness.io\n# labels:\n#   key1: value1\n# tags: \nspec:\n  type: service\n  owner: my-team\n  lifecycle: experimental\n  system: my-project\n#  dependsOn:\n#    - resource:default/my-db\n#  consumesApis:\n#    - user-api\n#  providesApis:\n#    - example-api";
  static final String GENERATE_YAML_DEF_WITH_ENTITIES =
      "kind: Component\nspec:\n  type: Service\n  lifecycle: Unknown\n  owner: Unknown\n  system: projectId\napiVersion: backstage.io/v1alpha1\nmetadata:\n  name: serviceId\n  description: serviceDesc\n  tags: []\n  annotations:\n    harness.io/project-url: https://localhost:8181/ng/account/123/home/orgs/orgId/projects/projectId/details\n    harness.io/cd-serviceId: serviceId\n";
  private static final String URL = "https://www.github.com";
  private static final String CONNECTOR_NAME = "test-connector-name";
  private static final String DELEGATE_SELECTOR1 = "ds1";
  private static final String DELEGATE_SELECTOR2 = "ds2";
  static final String TEST_ENTITY_NAME = "entityName";
  static final String TEST_ENTITY_IDENTIFIER = "entityIdentifier";
  static final String TEST_ENTITY_DOMAIN = "domainEntity";
  static final String TEST_ENTITY_SYSTEM = "domainSystem";
  AutoCloseable openMocks;
  @InjectMocks private OnboardingServiceImpl onboardingServiceImpl;
  @InjectMocks HarnessOrgToBackstageDomain harnessOrgToBackstageDomain;
  @InjectMocks HarnessProjectToBackstageSystem harnessProjectToBackstageSystem;
  @Mock ConnectorProcessorFactory connectorProcessorFactory;
  @Mock GithubConnectorProcessor githubConnectorProcessor;
  @Mock GitIntegrationService gitIntegrationService;
  @Mock CatalogConnectorRepository catalogConnectorRepository;
  @Mock AsyncCatalogImportRepository asyncCatalogImportRepository;
  @Mock StatusInfoService statusInfoService;
  @Mock TransactionHelper transactionHelper;
  @Mock IdpEntityCrudStreamProducer idpEntityCrudStreamProducer;
  @Mock DelegateSelectorsCache delegateSelectorsCache;
  @Mock BackstageResourceClient backstageResourceClient;
  Call<Object> call;
  final OnboardingModuleConfig onboardingModuleConfig =
      OnboardingModuleConfig.builder()
          .descriptionForSampleEntity(
              "This is an example of how the corresponding service definition YAML files will be created.")
          .descriptionForEntitySelected(
              "A YAML file will be created for each service inside your GitHub repository. An example of what the files will look like is shown below")
          .tmpPathForCatalogInfoYamlStore("/tmp")
          .harnessCiCdAnnotations(Map.of("projectUrl",
              "https://localhost:8181/ng/account/accountIdentifier/home/orgs/orgIdentifier/projects/projectIdentifier/details"))
          .build();

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    openMocks = MockitoAnnotations.openMocks(this);

    FakeOrganizationClient organizationClient = new FakeOrganizationClient();
    FakeProjectClient projectClient = new FakeProjectClient();
    FakeServiceResourceClient serviceResourceClient = new FakeServiceResourceClient();

    FieldUtils.writeField(onboardingServiceImpl, "organizationClient", organizationClient, true);
    FieldUtils.writeField(onboardingServiceImpl, "projectClient", projectClient, true);
    FieldUtils.writeField(onboardingServiceImpl, "serviceResourceClient", serviceResourceClient, true);

    FieldUtils.writeField(onboardingServiceImpl, "harnessOrgToBackstageDomain", harnessOrgToBackstageDomain, true);
    FieldUtils.writeField(
        onboardingServiceImpl, "harnessProjectToBackstageSystem", harnessProjectToBackstageSystem, true);

    HarnessServiceToBackstageComponent harnessServiceToBackstageComponent =
        new HarnessServiceToBackstageComponent(onboardingModuleConfig, "local");
    FieldUtils.writeField(
        onboardingServiceImpl, "harnessServiceToBackstageComponent", harnessServiceToBackstageComponent, true);

    FieldUtils.writeField(onboardingServiceImpl, "onboardingModuleConfig", onboardingModuleConfig, true);

    call = mock(Call.class);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetHarnessEntitiesCount() {
    HarnessEntitiesCountResponse harnessEntitiesCountResponse =
        onboardingServiceImpl.getHarnessEntitiesCount(ACCOUNT_IDENTIFIER);
    assertNotNull(harnessEntitiesCountResponse);
    assertEquals(1, (int) harnessEntitiesCountResponse.getOrgCount());
    assertEquals(1, (int) harnessEntitiesCountResponse.getProjectCount());
    assertEquals(1, (int) harnessEntitiesCountResponse.getServiceCount());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetHarnessEntitiesCountWithInvalidAccountIdentifier() {
    HarnessEntitiesCountResponse harnessEntitiesCountResponse =
        onboardingServiceImpl.getHarnessEntitiesCount(ACCOUNT_IDENTIFIER_DUMMY);
    assertNotNull(harnessEntitiesCountResponse);
    assertEquals(0, (int) harnessEntitiesCountResponse.getOrgCount());
    assertEquals(0, (int) harnessEntitiesCountResponse.getProjectCount());
    assertEquals(0, (int) harnessEntitiesCountResponse.getServiceCount());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetHarnessEntities() {
    PageResponse<HarnessBackstageEntities> harnessBackstageEntitiesPageResponse =
        onboardingServiceImpl.getHarnessEntities(ACCOUNT_IDENTIFIER, 0, 10, null, null, null, null);
    assertNotNull(harnessBackstageEntitiesPageResponse);
    assertEquals(1, (int) harnessBackstageEntitiesPageResponse.getTotalItems());
    assertEquals(1, (int) harnessBackstageEntitiesPageResponse.getTotalPages());
    assertEquals(1, (int) harnessBackstageEntitiesPageResponse.getPageItemCount());
    assertEquals(10, (int) harnessBackstageEntitiesPageResponse.getPageSize());
    List<HarnessBackstageEntities> harnessBackstageEntities = harnessBackstageEntitiesPageResponse.getContent();
    assertNotNull(harnessBackstageEntities);
    assertEquals(1, harnessBackstageEntities.size());
    assertEquals(TEST_SERVICE_IDENTIFIER, harnessBackstageEntities.get(0).getName());
    assertEquals("projectId", harnessBackstageEntities.get(0).getSystem());
    assertEquals("Unknown", harnessBackstageEntities.get(0).getOwner());
    assertEquals("Service", harnessBackstageEntities.get(0).getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetHarnessEntitiesWithProjectFilter() {
    PageResponse<HarnessBackstageEntities> harnessBackstageEntitiesPageResponse =
        onboardingServiceImpl.getHarnessEntities(ACCOUNT_IDENTIFIER, 0, 10, null, null, null, TEST_PROJECT_IDENTIFIER);
    assertNotNull(harnessBackstageEntitiesPageResponse);
    assertEquals(1, (int) harnessBackstageEntitiesPageResponse.getTotalItems());
    assertEquals(1, (int) harnessBackstageEntitiesPageResponse.getTotalPages());
    assertEquals(1, (int) harnessBackstageEntitiesPageResponse.getPageItemCount());
    assertEquals(10, (int) harnessBackstageEntitiesPageResponse.getPageSize());
    List<HarnessBackstageEntities> harnessBackstageEntities = harnessBackstageEntitiesPageResponse.getContent();
    assertNotNull(harnessBackstageEntities);
    assertEquals(1, harnessBackstageEntities.size());
    assertEquals(TEST_SERVICE_IDENTIFIER, harnessBackstageEntities.get(0).getName());
    assertEquals("projectId", harnessBackstageEntities.get(0).getSystem());
    assertEquals("Unknown", harnessBackstageEntities.get(0).getOwner());
    assertEquals("Service", harnessBackstageEntities.get(0).getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGenerateYaml() {
    mockStatic(CommonUtils.class);
    when(CommonUtils.readFileFromClassPath(any())).thenReturn(GENERATE_YAML_DEF);
    GenerateYamlResponse generateYamlResponse =
        onboardingServiceImpl.generateYaml(ACCOUNT_IDENTIFIER, new GenerateYamlRequest().entities(new ArrayList<>()));
    assertNotNull(generateYamlResponse);
    assertEquals(onboardingModuleConfig.getDescriptionForSampleEntity(),
        generateYamlResponse.getGeneratedYaml().getDescription());
    assertEquals(GENERATE_YAML_DEF, generateYamlResponse.getGeneratedYaml().getYamlDef());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGenerateYamlWithEntities() {
    mockStatic(CommonUtils.class);
    when(CommonUtils.readFileFromClassPath(any())).thenReturn(GENERATE_YAML_DEF);
    GenerateYamlResponse generateYamlResponse = onboardingServiceImpl.generateYaml(ACCOUNT_IDENTIFIER,
        new GenerateYamlRequest().entities(Collections.singletonList(new EntitiesForImport()
                                                                         .identifier("orgId|"
                                                                             + "projectId|" + TEST_SERVICE_IDENTIFIER)
                                                                         .entityType("Service"))));
    assertNotNull(generateYamlResponse);
    assertEquals(onboardingModuleConfig.getDescriptionForEntitySelected(),
        generateYamlResponse.getGeneratedYaml().getDescription());
    assertEquals(GENERATE_YAML_DEF_WITH_ENTITIES, generateYamlResponse.getGeneratedYaml().getYamlDef());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGenerateYamlWithNonExistentEntities() {
    mockStatic(CommonUtils.class);
    when(CommonUtils.readFileFromClassPath(any())).thenReturn(GENERATE_YAML_DEF);
    GenerateYamlResponse generateYamlResponse = onboardingServiceImpl.generateYaml(ACCOUNT_IDENTIFIER + "test",
        new GenerateYamlRequest().entities(
            Collections.singletonList(new EntitiesForImport()
                                          .identifier("orgId|"
                                              + "projectId|" + TEST_SERVICE_IDENTIFIER + "test")
                                          .entityType("Service"))));
    assertNotNull(generateYamlResponse);
    assertEquals(onboardingModuleConfig.getDescriptionForSampleEntity(),
        generateYamlResponse.getGeneratedYaml().getDescription());
    assertEquals(GENERATE_YAML_DEF, generateYamlResponse.getGeneratedYaml().getYamlDef());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testImportHarnessEntities() throws Exception {
    Set<String> delegateSelectors = new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2));
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .url(URL)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .delegateSelectors(delegateSelectors)
                                                .executeOnDelegate(false)
                                                .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(githubConnectorDTO)
                                            .connectorType(ConnectorType.GITHUB)
                                            .identifier(ACCOUNT_IDENTIFIER)
                                            .orgIdentifier(null)
                                            .projectIdentifier(null)
                                            .name(CONNECTOR_NAME)
                                            .build();
    mockStatic(CommonUtils.class);
    when(CommonUtils.readFileFromClassPath(any())).thenReturn("");
    when(connectorProcessorFactory.getConnectorProcessor(ConnectorType.fromString("Github")))
        .thenReturn(githubConnectorProcessor);
    when(githubConnectorProcessor.getInfraConnectorType(any())).thenReturn("DIRECT");
    when(githubConnectorProcessor.getConnectorInfo(any(), any())).thenReturn(connectorInfoDTO);
    doNothing()
        .when(gitIntegrationService)
        .createOrUpdateConnectorInBackstage(any(), any(), any(), any(), eq(delegateSelectors));
    ImportEntitiesResponse importEntitiesResponse = onboardingServiceImpl.importHarnessEntities(ACCOUNT_IDENTIFIER,
        new SampleEntitiesImport()
            .type(ImportEntitiesBase.TypeEnum.SAMPLE)
            .catalogConnectorInfo(new CatalogConnectorInfo()
                                      .connector(new ConnectorDetails()
                                                     .identifier("account.sathishgithub")
                                                     .type(ConnectorDetails.TypeEnum.GITHUB))
                                      .repo("https://github.com/sathish-soundarapandian/onboarding-test")
                                      .branch("main")
                                      .path("idp")));
    assertNotNull(importEntitiesResponse);
    assertEquals("SUCCESS", importEntitiesResponse.getStatus());
    verify(delegateSelectorsCache).put(eq(ACCOUNT_IDENTIFIER), any(), any());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testImportHarnessEntitiesWithIndividualEntities() throws Exception {
    Set<String> delegateSelectors = new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2));
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .url(URL)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .delegateSelectors(delegateSelectors)
                                                .executeOnDelegate(false)
                                                .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(githubConnectorDTO)
                                            .connectorType(ConnectorType.GITHUB)
                                            .identifier(ACCOUNT_IDENTIFIER)
                                            .orgIdentifier(null)
                                            .projectIdentifier(null)
                                            .name(CONNECTOR_NAME)
                                            .build();
    mockStatic(CommonUtils.class);
    when(CommonUtils.readFileFromClassPath(any())).thenReturn("");
    when(connectorProcessorFactory.getConnectorProcessor(ConnectorType.fromString("Github")))
        .thenReturn(githubConnectorProcessor);
    when(githubConnectorProcessor.getInfraConnectorType(any())).thenReturn("DIRECT");
    when(githubConnectorProcessor.getConnectorInfo(any(), any())).thenReturn(connectorInfoDTO);
    doNothing()
        .when(gitIntegrationService)
        .createOrUpdateConnectorInBackstage(any(), any(), any(), any(), eq(delegateSelectors));
    when(transactionHelper.performTransaction(any())).thenReturn(null);
    List<EntitiesForImport> entitiesForImports = new ArrayList<>();
    entitiesForImports.add(new EntitiesForImport()
                               .identifier("orgId|"
                                   + "projectId|" + TEST_SERVICE_IDENTIFIER)
                               .entityType(SERVICE));
    ImportEntitiesResponse importEntitiesResponse = onboardingServiceImpl.importHarnessEntities(ACCOUNT_IDENTIFIER,
        new IndividualEntitiesImport()
            .entities(entitiesForImports)
            .type(ImportEntitiesBase.TypeEnum.INDIVIDUAL)
            .catalogConnectorInfo(getCatalogConnectorInfo()));
    assertNotNull(importEntitiesResponse);
    assertEquals("SUCCESS", importEntitiesResponse.getStatus());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testImportHarnessEntitiesWithAllEntities() throws Exception {
    Set<String> delegateSelectors = new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2));
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .url(URL)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .delegateSelectors(delegateSelectors)
                                                .executeOnDelegate(false)
                                                .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(githubConnectorDTO)
                                            .connectorType(ConnectorType.GITHUB)
                                            .identifier(ACCOUNT_IDENTIFIER)
                                            .orgIdentifier(null)
                                            .projectIdentifier(null)
                                            .name(CONNECTOR_NAME)
                                            .build();
    mockStatic(CommonUtils.class);
    when(CommonUtils.readFileFromClassPath(any())).thenReturn("");
    when(connectorProcessorFactory.getConnectorProcessor(ConnectorType.fromString("Github")))
        .thenReturn(githubConnectorProcessor);
    when(githubConnectorProcessor.getInfraConnectorType(any())).thenReturn("DIRECT");
    when(githubConnectorProcessor.getConnectorInfo(any(), any())).thenReturn(connectorInfoDTO);
    doNothing()
        .when(gitIntegrationService)
        .createOrUpdateConnectorInBackstage(any(), any(), any(), any(), eq(delegateSelectors));
    when(transactionHelper.performTransaction(any())).thenReturn(null);
    ImportEntitiesResponse importEntitiesResponse = onboardingServiceImpl.importHarnessEntities(ACCOUNT_IDENTIFIER,
        new AllEntitiesImport().type(ImportEntitiesBase.TypeEnum.ALL).catalogConnectorInfo(getCatalogConnectorInfo()));
    assertNotNull(importEntitiesResponse);
    assertEquals("SUCCESS", importEntitiesResponse.getStatus());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testAsyncCatalogImport() throws IOException {
    Set<String> delegateSelectors = new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2));
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .url(URL)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .delegateSelectors(delegateSelectors)
                                                .executeOnDelegate(false)
                                                .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(githubConnectorDTO)
                                            .connectorType(ConnectorType.GITHUB)
                                            .identifier(ACCOUNT_IDENTIFIER)
                                            .orgIdentifier(null)
                                            .projectIdentifier(null)
                                            .name(CONNECTOR_NAME)
                                            .build();
    mockStatic(CommonUtils.class);
    when(CommonUtils.readFileFromClassPath(any())).thenReturn("");
    MockedStatic<FileUtils> commonUtilsMockedStatic = Mockito.mockStatic(FileUtils.class);
    when(asyncCatalogImportRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER))
        .thenReturn(getAsyncCatalogImportEntity());
    when(connectorProcessorFactory.getConnectorProcessor(ConnectorType.fromString("Github")))
        .thenReturn(githubConnectorProcessor);
    when(githubConnectorProcessor.getInfraConnectorType(any())).thenReturn("DIRECT");
    when(githubConnectorProcessor.getConnectorInfo(any(), any())).thenReturn(connectorInfoDTO);
    Response<Object> response =
        Response.success(200, ResponseBody.create(MediaType.parse("application/json"), "Success"));
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.createCatalogLocation(any(), any())).thenReturn(call);
    onboardingServiceImpl.asyncCatalogImport(
        EntityChangeDTO.newBuilder().setAccountIdentifier(StringValue.of(ACCOUNT_IDENTIFIER)).build());

    commonUtilsMockedStatic.close();
  }

  private CatalogConnectorInfo getCatalogConnectorInfo() {
    return new CatalogConnectorInfo()
        .connector(new ConnectorDetails().identifier("account.sathishgithub").type(ConnectorDetails.TypeEnum.GITHUB))
        .repo("https://github.com/sathish-soundarapandian/onboarding-test.git/")
        .branch("main")
        .path("idp");
  }

  private AsyncCatalogImportEntity getAsyncCatalogImportEntity() {
    return AsyncCatalogImportEntity.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .catalogDomains(AsyncCatalogImportDetails.builder()
                            .entities(Collections.singletonList(BackstageCatalogDomainEntity.builder()
                                                                    .metadata(BackstageCatalogEntity.Metadata.builder()
                                                                                  .name(TEST_ENTITY_NAME)
                                                                                  .identifier(TEST_ENTITY_IDENTIFIER)
                                                                                  .build())
                                                                    .build()))
                            .yamlPath("/tmp/orgYamlPath")
                            .entityTargetParentPath("orgEntityTargetParentPath")
                            .build())
        .catalogSystems(
            AsyncCatalogImportDetails.builder()
                .entities(Collections.singletonList(
                    BackstageCatalogSystemEntity.builder()
                        .spec(BackstageCatalogSystemEntity.Spec.builder().domain(TEST_ENTITY_DOMAIN).build())
                        .metadata(BackstageCatalogEntity.Metadata.builder()
                                      .name(TEST_ENTITY_NAME)
                                      .identifier(TEST_ENTITY_IDENTIFIER)
                                      .build())
                        .build()))
                .yamlPath("/tmp/projectYamlPath")
                .entityTargetParentPath("projectEntityTargetParentPath")
                .build())
        .catalogComponents(
            AsyncCatalogImportDetails.builder()
                .entities(Collections.singletonList(BackstageCatalogComponentEntity.builder()
                                                        .spec(BackstageCatalogComponentEntity.Spec.builder()
                                                                  .domain(TEST_ENTITY_DOMAIN)
                                                                  .harnessSystem(TEST_ENTITY_SYSTEM)
                                                                  .build())
                                                        .metadata(BackstageCatalogEntity.Metadata.builder()
                                                                      .name(TEST_ENTITY_NAME)
                                                                      .identifier(TEST_ENTITY_IDENTIFIER)
                                                                      .build())
                                                        .build()))
                .yamlPath("/tmp/serviceYamlPath")
                .entityTargetParentPath("serviceEntityTargetParentPath")
                .build())
        .catalogInfraConnectorType("DIRECT")
        .catalogConnectorInfo(getCatalogConnectorInfo())
        .userPrincipal(new UserPrincipal("name", "email.harness.io", "username", ACCOUNT_IDENTIFIER))
        .build();
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
