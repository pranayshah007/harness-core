/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.MLUKIC;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmDockerApiListTagsTaskParams;
import io.harness.delegate.beans.connector.helm.OciHelmUsernamePasswordDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.OciHelmDockerApiException;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
public class OciHelmDockerApiHelperTest extends CategoryTest {
  @InjectMocks private OciHelmDockerApiHelper ociHelmDockerApiHelper = spy(OciHelmDockerApiHelper.class);
  @Spy @InjectMocks private OciHelmApiHelperUtils ociHelmApiHelperUtils;
  @Mock private SecretDecryptionService decryptionService;

  @Rule
  public WireMockRule wireMockRule =
      new WireMockRule(WireMockConfiguration.wireMockConfig()
                           .usingFilesUnderClasspath("930-delegate-tasks/src/test/resources")
                           .disableRequestJournal()
                           .port(0));

  String url;
  String accountId = "accId";
  String chartName = "test/chart";

  @Before
  public void setup() throws IOException {
    initMocks(this);
    url = String.format("http://localhost:%d", wireMockRule.port());
  }

  private OciHelmUsernamePasswordDTO getOciHelmAuthenticationDTO(boolean isAnonymous) {
    if (isAnonymous) {
      return OciHelmUsernamePasswordDTO.builder().build();
    } else {
      return OciHelmUsernamePasswordDTO.builder()
          .username("test")
          .passwordRef(SecretRefData.builder().decryptedValue("test".toCharArray()).build())
          .build();
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetChartVersions() {
    String lastTag = null;
    int pageSize = 3;
    OciHelmAuthenticationDTO ociHelmAuthenticationDTO = OciHelmAuthenticationDTO.builder()
                                                            .authType(OciHelmAuthType.USER_PASSWORD)
                                                            .credentials(getOciHelmAuthenticationDTO(false))
                                                            .build();

    OciHelmConnectorDTO ociHelmConnectorDTO = OciHelmConnectorDTO.builder()
                                                  .helmRepoUrl(url)
                                                  .auth(ociHelmAuthenticationDTO)
                                                  .delegateSelectors(new HashSet<>())
                                                  .build();

    OciHelmDockerApiListTagsTaskParams ociHelmDockerApiListTagsTaskParams = OciHelmDockerApiListTagsTaskParams.builder()
                                                                                .ociHelmConnector(ociHelmConnectorDTO)
                                                                                .chartName(chartName)
                                                                                .lastTag(lastTag)
                                                                                .build();

    doReturn(null).when(decryptionService).decrypt(any(), any());

    wireMockRule.stubFor(get(urlEqualTo(String.format("/v2/%s/tags/list?n=%d", chartName, pageSize)))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("{\n"
                                                 + "    \"name\": \"" + chartName + "\",\n"
                                                 + "    \"tags\": [\n"
                                                 + "        \"0.1.0\",\n"
                                                 + "        \"0.1.1\",\n"
                                                 + "        \"0.1.2\"\n"
                                                 + "    ]\n"
                                                 + "}")
                                             .withHeader("Content-Type", "application/json")));

    List<String> versions = ociHelmDockerApiHelper.getChartVersions(accountId, ociHelmDockerApiListTagsTaskParams, 3);
    assertThat(versions).contains("0.1.0", "0.1.1", "0.1.2");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetChartVersionsEmptyChartName() {
    OciHelmAuthenticationDTO ociHelmAuthenticationDTO = OciHelmAuthenticationDTO.builder()
                                                            .authType(OciHelmAuthType.USER_PASSWORD)
                                                            .credentials(getOciHelmAuthenticationDTO(false))
                                                            .build();

    OciHelmConnectorDTO ociHelmConnectorDTO = OciHelmConnectorDTO.builder()
                                                  .helmRepoUrl(url)
                                                  .auth(ociHelmAuthenticationDTO)
                                                  .delegateSelectors(new HashSet<>())
                                                  .build();

    OciHelmDockerApiListTagsTaskParams ociHelmDockerApiListTagsTaskParams = OciHelmDockerApiListTagsTaskParams.builder()
                                                                                .chartName("")
                                                                                .lastTag(null)
                                                                                .ociHelmConnector(ociHelmConnectorDTO)
                                                                                .build();

    assertThatThrownBy(() -> ociHelmDockerApiHelper.getChartVersions(accountId, ociHelmDockerApiListTagsTaskParams, 3))
        .isInstanceOf(OciHelmDockerApiException.class);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetChartVersionsWhenPathIsProvidedInConnector() {
    String lastTag = null;
    int pageSize = 3;
    OciHelmAuthenticationDTO ociHelmAuthenticationDTO = OciHelmAuthenticationDTO.builder()
                                                            .authType(OciHelmAuthType.USER_PASSWORD)
                                                            .credentials(getOciHelmAuthenticationDTO(false))
                                                            .build();

    OciHelmConnectorDTO ociHelmConnectorDTO = OciHelmConnectorDTO.builder()
                                                  .helmRepoUrl(url + "/somepath")
                                                  .auth(ociHelmAuthenticationDTO)
                                                  .delegateSelectors(new HashSet<>())
                                                  .build();

    OciHelmDockerApiListTagsTaskParams ociHelmDockerApiListTagsTaskParams = OciHelmDockerApiListTagsTaskParams.builder()
                                                                                .ociHelmConnector(ociHelmConnectorDTO)
                                                                                .chartName(chartName)
                                                                                .lastTag(lastTag)
                                                                                .build();

    doReturn(null).when(decryptionService).decrypt(any(), any());

    wireMockRule.stubFor(get(urlEqualTo(String.format("/v2/%s/tags/list?n=%d", "somepath/" + chartName, pageSize)))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("{\n"
                                                 + "    \"name\": \"" + chartName + "\",\n"
                                                 + "    \"tags\": [\n"
                                                 + "        \"0.1.0\",\n"
                                                 + "        \"0.1.1\",\n"
                                                 + "        \"0.1.2\"\n"
                                                 + "    ]\n"
                                                 + "}")
                                             .withHeader("Content-Type", "application/json")));

    List<String> versions = ociHelmDockerApiHelper.getChartVersions(accountId, ociHelmDockerApiListTagsTaskParams, 3);
    assertThat(versions).contains("0.1.0", "0.1.1", "0.1.2");
  }
}
