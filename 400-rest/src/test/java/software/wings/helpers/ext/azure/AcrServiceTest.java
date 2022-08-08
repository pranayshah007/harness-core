/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.azure;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.beans.AzureConfig;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
public class AcrServiceTest extends WingsBaseTest {
  @Mock private AzureDelegateHelperService azureDelegateHelperService;
  @Inject @InjectMocks AcrService acrService;

  AzureConfig azureConfig = AzureConfig.builder().build();
  List<EncryptedDataDetail> encryptionDetailList = null;

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetBuilds() {
    ArtifactStreamAttributes attributes1 =
        ArtifactStreamAttributes.builder().registryHostName("registryHostName").repositoryName("repoName").build();

    when(azureDelegateHelperService.listRepositoryTags(
             azureConfig, encryptionDetailList, "registryHostName", "repoName"))
        .thenReturn(Lists.newArrayList("latest", "v2", "v1"));
    assertThat(acrService.getBuilds(azureConfig, null, attributes1, 100))
        .hasSize(3)
        .isEqualTo(Lists.newArrayList(buildBuildDetails("v1"), buildBuildDetails("v2"), buildBuildDetails("latest")));

    ArtifactStreamAttributes attributes2 = ArtifactStreamAttributes.builder()
                                               .subscriptionId("subId")
                                               .registryName("regName")
                                               .repositoryName("repoName")
                                               .build();

    when(azureDelegateHelperService.getLoginServerForRegistry(azureConfig, encryptionDetailList, "subId", "regName"))
        .thenReturn("registryHostName");
    assertThat(acrService.getBuilds(azureConfig, null, attributes2, 100))
        .hasSize(3)
        .isEqualTo(Lists.newArrayList(buildBuildDetails("v1"), buildBuildDetails("v2"), buildBuildDetails("latest")));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldVerifyImageName() {
    when(azureDelegateHelperService.isValidSubscription(azureConfig, "invalidSubId")).thenReturn(false);
    when(azureDelegateHelperService.isValidSubscription(azureConfig, "validSubId")).thenReturn(true);

    when(azureDelegateHelperService.isValidContainerRegistry(azureConfig, "validSubId", "invalidRegName"))
        .thenReturn(false);
    when(azureDelegateHelperService.isValidContainerRegistry(azureConfig, "validSubId", "validRegName"))
        .thenReturn(true);

    when(azureDelegateHelperService.listRepositories(azureConfig, "validSubId", "validRegName"))
        .thenReturn(Lists.newArrayList("repoName1"))
        .thenReturn(Lists.newArrayList("repoName1", "repoName2"));

    assertThatThrownBy(()
                           -> acrService.verifyImageName(azureConfig, encryptionDetailList,
                               ArtifactStreamAttributes.builder().subscriptionId("invalidSubId").build()))
        .isInstanceOf(WingsException.class)
        .extracting("params")
        .extracting("args")
        .isEqualTo("SubscriptionId [invalidSubId] does not exist in Azure account.");

    assertThatThrownBy(
        ()
            -> acrService.verifyImageName(azureConfig, encryptionDetailList,
                ArtifactStreamAttributes.builder().subscriptionId("validSubId").registryName("invalidRegName").build()))
        .isInstanceOf(WingsException.class)
        .extracting("params")
        .extracting("args")
        .isEqualTo("Registry [invalidRegName] does not exist in Azure subscription.");

    assertThatThrownBy(()
                           -> acrService.verifyImageName(azureConfig, encryptionDetailList,
                               ArtifactStreamAttributes.builder()
                                   .subscriptionId("validSubId")
                                   .registryName("validRegName")
                                   .repositoryName("repoName2")
                                   .build()))
        .isInstanceOf(WingsException.class)
        .extracting("params")
        .extracting("args")
        .isEqualTo("Repository [repoName2] does not exist in Azure Registry.");
    assertThat(acrService.verifyImageName(azureConfig, encryptionDetailList,
                   ArtifactStreamAttributes.builder()
                       .subscriptionId("validSubId")
                       .registryName("validRegName")
                       .repositoryName("repoName2")
                       .build()))
        .isTrue();
  }

  private BuildDetails buildBuildDetails(String tag) {
    Map<String, String> metadata = new HashMap();
    metadata.put(ArtifactMetadataKeys.image, "registryHostName/repoName:" + tag);
    metadata.put(ArtifactMetadataKeys.tag, tag);
    return aBuildDetails().withNumber(tag).withMetadata(metadata).withUiDisplayName("Tag# " + tag).build();
  }
}
