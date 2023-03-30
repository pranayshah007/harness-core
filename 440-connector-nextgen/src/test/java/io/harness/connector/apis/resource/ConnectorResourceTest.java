/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.apis.resource;

import static io.harness.delegate.beans.connector.ConnectorType.KUBERNETES_CLUSTER;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.INHERIT_FROM_DELEGATE;

import static com.google.common.base.Predicates.alwaysTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorCatalogueItem;
import io.harness.connector.ConnectorCatalogueResponseDTO;
import io.harness.connector.ConnectorCategory;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.featureflagfilter.ConnectorEnumFilter;
import io.harness.connector.helper.CatalogueHelper;
import io.harness.connector.helper.ConnectorRbacHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorAllowedFieldValues;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.PageTestUtils;
import io.harness.utils.PageUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.validation.Validation;
import javax.validation.Validator;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(HarnessTeam.DX)
public class ConnectorResourceTest extends CategoryTest {
  @Mock private ConnectorService connectorService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private ConnectorRbacHelper connectorRbacHelper;
  @Mock private ConnectorEnumFilter enumFilter;
  @InjectMocks private CatalogueHelper catalogueHelper;
  @InjectMocks private ConnectorResource connectorResource;
  ConnectorResponseDTO connectorResponse;
  ConnectorInfoDTO connectorInfo;
  ConnectorDTO connectorRequest;
  String accountIdentifier = "accountIdentifier";
  ConnectorCatalogueResponseDTO catalogueResponseDTO;

  Pageable pageable;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    connectorInfo = ConnectorInfoDTO.builder()
                        .name("connector")
                        .identifier("identifier")
                        .connectorType(KUBERNETES_CLUSTER)
                        .connectorConfig(KubernetesClusterConfigDTO.builder()
                                             .delegateSelectors(Collections.singleton("delegateName"))
                                             .credential(KubernetesCredentialDTO.builder()
                                                             .kubernetesCredentialType(INHERIT_FROM_DELEGATE)

                                                             .config(null)
                                                             .build())
                                             .build())
                        .build();
    connectorRequest = ConnectorDTO.builder().connectorInfo(connectorInfo).build();
    connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfo).build();
    catalogueResponseDTO = setUpCatalogueResponse();
    pageable = PageUtils.getPageRequest(0, 100, List.of(ConnectorKeys.lastModifiedAt, Sort.Direction.DESC.toString()));
  }

  private ConnectorCatalogueResponseDTO setUpCatalogueResponse() {
    doReturn(alwaysTrue()).when(enumFilter).filter(any(), any());
    return ConnectorCatalogueResponseDTO.builder()
        .catalogue(catalogueHelper.getConnectorTypeToCategoryMapping(accountIdentifier))
        .build();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void create() {
    doReturn(connectorResponse).when(connectorService).create(any(), any());
    ResponseDTO<ConnectorResponseDTO> connectorResponseDTO =
        connectorResource.create(connectorRequest, accountIdentifier, null);
    Mockito.verify(connectorService, times(1)).create(any(), any());
    assertThat(connectorResponseDTO.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void update() {
    when(connectorService.update(any(), any())).thenReturn(connectorResponse);
    ResponseDTO<ConnectorResponseDTO> connectorResponseDTO =
        connectorResource.update(connectorRequest, accountIdentifier, null);
    Mockito.verify(connectorService, times(1)).update(any(), any());
    assertThat(connectorResponseDTO.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void get() {
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponse));
    ConnectorResponseDTO connectorRequestDTO =
        connectorResource.get("accountIdentifier", "orgIdentifier", "projectIdentifier", "connectorIdentifier", null)
            .getData();
    Mockito.verify(connectorService, times(1)).get(any(), any(), any(), any());
    assertThat(connectorRequestDTO).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void list() {
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String filterIdentifier = "filterIdentifier";
    String searchTerm = "searchTerm";
    ConnectorFilterPropertiesDTO connectorListFilter = ConnectorFilterPropertiesDTO.builder().build();
    final Page<ConnectorResponseDTO> page =
        PageTestUtils.getPage(Arrays.asList(ConnectorResponseDTO.builder().build()), 1);
    when(connectorService.list(
             anyString(), any(), anyString(), anyString(), anyString(), anyString(), any(), any(), any()))
        .thenReturn(page);
    ResponseDTO<PageResponse<ConnectorResponseDTO>> connectorSummaryListResponse = connectorResource.list(
        accountIdentifier, searchTerm, orgIdentifier, projectIdentifier, filterIdentifier, false, null, null, false,
        PageRequest.builder()
            .pageSize(100)
            .pageIndex(0)
            .sortOrders(
                List.of(SortOrder.Builder.aSortOrder().withField("lastModifiedAt", SortOrder.OrderType.DESC).build()))
            .build());
    Mockito.verify(connectorService, times(1))
        .list(eq(accountIdentifier), eq(null), eq(orgIdentifier), eq(projectIdentifier), eq(filterIdentifier),
            eq(searchTerm), eq(false), eq(false), any());
    assertThat(connectorSummaryListResponse.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void delete() {
    when(connectorService.delete(
             "accountIdentifier", "orgIdentifier", "projectIdentifier", "connectorIdentifier", false))
        .thenReturn(true);
    ResponseDTO<Boolean> result = connectorResource.delete(
        "accountIdentifier", "orgIdentifier", "projectIdentifier", "connectorIdentifier", null, false);
    Mockito.verify(connectorService, times(1))
        .delete("accountIdentifier", "orgIdentifier", "projectIdentifier", "connectorIdentifier", false);
    assertThat(result.getData()).isTrue();
  }
  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void deleteWithForceDeleteAsTrue() {
    when(
        connectorService.delete("accountIdentifier", "orgIdentifier", "projectIdentifier", "connectorIdentifier", true))
        .thenReturn(true);
    ResponseDTO<Boolean> result = connectorResource.delete(
        "accountIdentifier", "orgIdentifier", "projectIdentifier", "connectorIdentifier", null, true);
    Mockito.verify(connectorService, times(1))
        .delete("accountIdentifier", "orgIdentifier", "projectIdentifier", "connectorIdentifier", true);
    assertThat(result.getData()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void validateTheIdentifierIsUniqueTest() {
    when(connectorService.validateTheIdentifierIsUnique(any(), any(), any(), any())).thenReturn(true);
    ResponseDTO<Boolean> result = connectorResource.validateTheIdentifierIsUnique(
        "accountIdentifier", "orgIdentifier", "projectIdentifier", "connectorIdentifier");
    Mockito.verify(connectorService, times(1)).validateTheIdentifierIsUnique(any(), any(), any(), any());
    assertThat(result.getData()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testConnectionResourceTest() {
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponse));
    ResponseDTO<ConnectorValidationResult> validationResult = connectorResource.testConnection(
        "accountIdentifier", "orgIdentifier", "projectIdentifier", "connectorIdentifier", null);
    Mockito.verify(connectorService, times(1)).testConnection(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.HARSH)
  @Category(UnitTests.class)
  public void testGitRepoConnectionResourceTest() {
    ResponseDTO<ConnectorValidationResult> validationResult =
        connectorResource.testGitRepoConnection("accountIdentifier", "orgIdentifier", "projectIdentifier",
            "connectorIdentifier", "https://github.com/harness/harness-core.git");
    Mockito.verify(connectorService, times(1)).testGitRepoConnection(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void getConnectorCatalogueTest() {
    when(connectorService.getConnectorCatalogue(accountIdentifier)).thenReturn(catalogueResponseDTO);
    final ResponseDTO<ConnectorCatalogueResponseDTO> response =
        connectorResource.getConnectorCatalogue("accountIdentifier");
    assertThat(response).isNotNull();
    final List<ConnectorCatalogueItem> catalogue = response.getData().getCatalogue();
    assertThat(catalogue.size()).isEqualTo(ConnectorCategory.values().length);
    final int totalConnectorsWithinAllCategories =
        catalogue.stream().map(item -> item.getConnectors().size()).mapToInt(Integer::intValue).sum();
    assertThat(totalConnectorsWithinAllCategories).isEqualTo(ConnectorType.values().length);
    Mockito.verify(connectorService, times(1)).getConnectorCatalogue(accountIdentifier);
  }

  @Test
  @Owner(developers = OwnerRule.BOOPESH)
  @Category(UnitTests.class)
  public void getAllowedFieldValuesTest() {
    String connectorType = "NewRelic";
    ResponseDTO responseDTO = ResponseDTO.newResponse(ConnectorAllowedFieldValues.TYPE_TO_FIELDS.get(connectorType));
    assertThat(responseDTO.getStatus().toString()).isEqualTo("SUCCESS");
  }

  @Test
  @Owner(developers = OwnerRule.JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void createWith128CharIdentifierAndName() {
    String identifier = RandomStringUtils.randomAlphanumeric(128);
    String name = RandomStringUtils.randomAlphanumeric(128);
    connectorInfo = ConnectorInfoDTO.builder()
                        .name(name)
                        .identifier(identifier)
                        .connectorType(KUBERNETES_CLUSTER)
                        .connectorConfig(KubernetesClusterConfigDTO.builder()
                                             .delegateSelectors(Collections.singleton("delegateName"))
                                             .credential(KubernetesCredentialDTO.builder()
                                                             .kubernetesCredentialType(INHERIT_FROM_DELEGATE)
                                                             .config(null)
                                                             .build())
                                             .build())
                        .build();
    connectorRequest.setConnectorInfo(connectorInfo);
    connectorResponse.setConnector(connectorInfo);
    doReturn(connectorResponse).when(connectorService).create(any(), any());
    ResponseDTO<ConnectorResponseDTO> connectorResponseDTO =
        connectorResource.create(connectorRequest, accountIdentifier, null);
    Mockito.verify(connectorService, times(1)).create(any(), any());
    assertThat(connectorResponseDTO.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.DEV_MITTAL)
  @Category(UnitTests.class)
  public void connectorDTOObjectValidationTest() {
    ObjectMapper mapper = new ObjectMapper();
    assertThat(validate("440-connector-nextgen/src/test/resources/connector/GithubConnector1.json", mapper)).isFalse();
    assertThat(validate("440-connector-nextgen/src/test/resources/connector/GithubConnector2.json", mapper)).isTrue();
    assertThat(validate("440-connector-nextgen/src/test/resources/connector/GithubConnector3.json", mapper)).isFalse();
    assertThat(validate("440-connector-nextgen/src/test/resources/connector/GithubConnector4.json", mapper)).isTrue();
  }

  public static boolean validate(String path, ObjectMapper mapper) {
    try {
      byte[] encoded = Files.readAllBytes(Paths.get(path));
      String connectorJson = new String(encoded, Charset.defaultCharset());
      ConnectorDTO connectorDTO = mapper.readValue(connectorJson, ConnectorDTO.class);
      Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
      return validator.validate(connectorDTO).size() == 0;
    } catch (Exception e) {
      return false;
    }
  }
}
