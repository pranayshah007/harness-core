/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.rancher;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.SAINATH;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.RancherConfig;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class RancherTaskHelperTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private EncryptionService encryptionService;
  @Spy @InjectMocks private RancherTaskHelper rancherTaskHelper;

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testCreateKubeConfigWithMinimalValuesInYaml() throws IOException {
    RancherConfig rancherConfig = RancherConfig.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = new ArrayList<>();

    List<RancherClusterDataResponse.ClusterData> clusterDataList =
        Arrays.asList(RancherClusterDataResponse.ClusterData.builder().id("clusterA").name("clusterA").build());

    RancherClusterDataResponse rancherClusterData = RancherClusterDataResponse.builder().data(clusterDataList).build();

    doReturn(rancherClusterData).when(rancherTaskHelper).resolveRancherClusters(any(), any());
    doReturn("apiVersion: 0.0.1").when(rancherTaskHelper).generateKubeConfigFromRancher(any(), any());

    assertThatCode(() -> rancherTaskHelper.createKubeconfig(rancherConfig, encryptedDataDetailList, "clusterA", null))
        .doesNotThrowAnyException();
  }
}
