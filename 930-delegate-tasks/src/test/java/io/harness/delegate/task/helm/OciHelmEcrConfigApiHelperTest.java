/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsConfig;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.helm.EcrHelmApiListTagsTaskParams;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
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
public class OciHelmEcrConfigApiHelperTest extends CategoryTest {
  @InjectMocks private OciHelmEcrConfigApiHelper ociHelmEcrConfigApiHelper = spy(OciHelmEcrConfigApiHelper.class);
  @Spy @InjectMocks private OciHelmApiHelperUtils ociHelmApiHelperUtils;
  @Mock private SecretDecryptionService decryptionService;
  @Mock private AwsClient awsClient;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;

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

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetChartVersionsECRConfigType() {
    String lastTag = null;
    int pageSize = 3;
    String region = "us-west-1";

    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE).build();

    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder().credential(awsCredentialDTO).delegateSelectors(new HashSet<>()).build();

    EcrHelmApiListTagsTaskParams ecrHelmApiListTagsTaskParams = EcrHelmApiListTagsTaskParams.builder()
                                                                    .chartName(chartName)
                                                                    .lastTag(lastTag)
                                                                    .awsConnectorDTO(awsConnectorDTO)
                                                                    .region(region)
                                                                    .build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    AwsConfig awsConfig = AwsConfig.builder().build();

    doReturn(null).when(decryptionService).decrypt(any(), any());
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(eq(awsConnectorDTO));
    doReturn(url).when(awsClient).getEcrImageUrl(eq(awsInternalConfig), eq(null), eq(region), eq(chartName));
    doReturn(awsConfig)
        .when(awsNgConfigMapper)
        .mapAwsConfigWithDecryption(eq(awsCredentialDTO), eq(AwsCredentialType.INHERIT_FROM_DELEGATE), any());
    doReturn("dGVzdDp0ZXN0").when(awsClient).getAmazonEcrAuthToken(eq(awsConfig), any(), eq(region));

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

    List<String> versions = ociHelmEcrConfigApiHelper.getChartVersions(accountId, ecrHelmApiListTagsTaskParams, 3);
    assertThat(versions).contains("0.1.0", "0.1.1", "0.1.2");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetChartVersionsEmptyChartName() {
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE).build();

    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder().credential(awsCredentialDTO).delegateSelectors(new HashSet<>()).build();

    EcrHelmApiListTagsTaskParams ecrHelmApiListTagsTaskParams =
        EcrHelmApiListTagsTaskParams.builder().chartName("").lastTag(null).awsConnectorDTO(awsConnectorDTO).build();

    assertThatThrownBy(() -> ociHelmEcrConfigApiHelper.getChartVersions(accountId, ecrHelmApiListTagsTaskParams, 3))
        .isInstanceOf(OciHelmDockerApiException.class);
  }
}
