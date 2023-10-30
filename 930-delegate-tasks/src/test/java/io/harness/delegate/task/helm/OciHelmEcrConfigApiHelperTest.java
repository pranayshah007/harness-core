/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PRATYUSH;

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
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.beans.EcrImageDetailConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.helm.EcrHelmApiListTagsTaskParams;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.exception.OciHelmDockerApiException;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import com.amazonaws.services.ecr.model.ImageDetail;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class OciHelmEcrConfigApiHelperTest extends CategoryTest {
  @InjectMocks private OciHelmEcrConfigApiHelper ociHelmEcrConfigApiHelper = spy(OciHelmEcrConfigApiHelper.class);
  @Mock private SecretDecryptionService decryptionService;
  @Mock private AwsClient awsClient;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private OciHelmApiHelperUtils ociHelmApiHelperUtils;
  String chartName = "test/chart";
  int pageSize = 100000;

  @Before
  public void setup() throws IOException {
    initMocks(this);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetChartVersionsECRConfigType() {
    String region = "us-west-1";

    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE).build();

    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder().credential(awsCredentialDTO).delegateSelectors(new HashSet<>()).build();

    EcrHelmApiListTagsTaskParams ecrHelmApiListTagsTaskParams = EcrHelmApiListTagsTaskParams.builder()
                                                                    .chartName(chartName)
                                                                    .awsConnectorDTO(awsConnectorDTO)
                                                                    .region(region)
                                                                    .build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    ImageDetail imageDetail = new ImageDetail();
    imageDetail.setImageTags(Arrays.asList("0.1.0", "0.1.1", "0.1.2"));
    EcrImageDetailConfig ecrImageDetailConfig =
        EcrImageDetailConfig.builder().imageDetails(Collections.singletonList(imageDetail)).nextToken(null).build();

    doReturn(null).when(decryptionService).decrypt(any(), any());
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(eq(awsConnectorDTO));
    doReturn(ecrImageDetailConfig)
        .when(awsClient)
        .listEcrImageTags(eq(awsInternalConfig), eq(null), eq(region), eq(chartName), eq(pageSize), eq(null));

    EcrImageDetailConfig ecrImageDetailConfig2 =
        ociHelmEcrConfigApiHelper.getEcrImageDetailConfig(ecrHelmApiListTagsTaskParams, pageSize);
    assertThat(ecrImageDetailConfig2.getImageDetails()
                   .stream()
                   .map(ImageDetail::getImageTags)
                   .flatMap(List::stream)
                   .collect(Collectors.toList()))
        .contains("0.1.0", "0.1.1", "0.1.2");
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

    assertThatThrownBy(() -> ociHelmEcrConfigApiHelper.getEcrImageDetailConfig(ecrHelmApiListTagsTaskParams, pageSize))
        .isInstanceOf(OciHelmDockerApiException.class);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetChartVersionsFromImageDetails() {
    ImageDetail imageDetail1 = new ImageDetail();
    imageDetail1.setImageTags(Arrays.asList("0.1.0", "0.1.1", "0.1.2"));
    ImageDetail imageDetail2 = new ImageDetail();
    imageDetail2.setImageTags(Arrays.asList("1.1.0", "1.1.1", "1.1.2"));
    EcrImageDetailConfig ecrImageDetailConfig =
        EcrImageDetailConfig.builder().imageDetails(Arrays.asList(imageDetail1, imageDetail2)).nextToken(null).build();
    List<String> chartVersions = ociHelmEcrConfigApiHelper.getChartVersionsFromImageDetails(ecrImageDetailConfig);
    assertThat(chartVersions.size()).isEqualTo(6);
    assertThat(chartVersions).isEqualTo(Arrays.asList("0.1.0", "0.1.1", "0.1.2", "1.1.0", "1.1.1", "1.1.2"));
  }
}
