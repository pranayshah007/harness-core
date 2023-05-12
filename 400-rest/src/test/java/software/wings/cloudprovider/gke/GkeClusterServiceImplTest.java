/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.cloudprovider.gke;

import static io.harness.rule.OwnerRule.BRETT;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.delegate.task.gcp.helpers.GkeClusterHelper;
import io.harness.exception.WingsException;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesConfig.KubernetesConfigBuilder;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by brett on 2/10/17.
 */
public class GkeClusterServiceImplTest extends WingsBaseTest {
  @Mock private EncryptionService encryptionService;
  @Mock private GkeClusterHelper gkeClusterHelper;

  @Inject @InjectMocks private GkeClusterService gkeClusterService;

  private final char[] serviceAccountKey = "{\"project_id\": \"project-a\"}".toCharArray();
  private static final SettingAttribute COMPUTE_PROVIDER_SETTING =
      aSettingAttribute()
          .withValue(
              GcpConfig.builder().serviceAccountKeyFileContent("{\"project_id\": \"project-a\"}".toCharArray()).build())
          .build();
  private static final String ZONE_CLUSTER = "zone-a/foo-bar";
  private static final ImmutableMap<String, String> CREATE_CLUSTER_PARAMS = ImmutableMap.<String, String>builder()
                                                                                .put("nodeCount", "1")
                                                                                .put("masterUser", "master")
                                                                                .put("masterPwd", "password")
                                                                                .build();
  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldCreateCluster() {
    KubernetesConfigBuilder kubernetesConfigBuilder = KubernetesConfig.builder()
                                                          .masterUrl("https://1.1.1.1/")
                                                          .username("master1".toCharArray())
                                                          .password("password1".toCharArray());
    KubernetesConfig config = kubernetesConfigBuilder.build();

    when(gkeClusterHelper.createCluster(serviceAccountKey, false, ZONE_CLUSTER, "default", CREATE_CLUSTER_PARAMS))
        .thenReturn(config);

    KubernetesConfig result = gkeClusterService.createCluster(
        COMPUTE_PROVIDER_SETTING.toDTO(), Collections.emptyList(), ZONE_CLUSTER, "default", CREATE_CLUSTER_PARAMS);

    verify(gkeClusterHelper, times(1))
        .createCluster(eq(serviceAccountKey), eq(false), eq(ZONE_CLUSTER), eq("default"), eq(CREATE_CLUSTER_PARAMS));
    assertThat(result).isEqualTo(config);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotCreateClusterIfError() {
    when(gkeClusterHelper.createCluster(serviceAccountKey, false, ZONE_CLUSTER, "default", CREATE_CLUSTER_PARAMS))
        .thenReturn(null);

    KubernetesConfig config = gkeClusterService.createCluster(
        COMPUTE_PROVIDER_SETTING.toDTO(), Collections.emptyList(), ZONE_CLUSTER, "default", CREATE_CLUSTER_PARAMS);

    verify(gkeClusterHelper, times(1))
        .createCluster(eq(serviceAccountKey), eq(false), eq(ZONE_CLUSTER), eq("default"), eq(CREATE_CLUSTER_PARAMS));
    assertThat(config).isNull();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetCluster() {
    KubernetesConfigBuilder kubernetesConfigBuilder = KubernetesConfig.builder()
                                                          .masterUrl("https://1.1.1.1/")
                                                          .username("master1".toCharArray())
                                                          .password("password1".toCharArray());
    KubernetesConfig config = kubernetesConfigBuilder.build();
    when(gkeClusterHelper.getCluster(serviceAccountKey, false, ZONE_CLUSTER, "default")).thenReturn(config);

    KubernetesConfig result = gkeClusterService.getCluster(
        COMPUTE_PROVIDER_SETTING.toDTO(), Collections.emptyList(), ZONE_CLUSTER, "default", false);

    verify(gkeClusterHelper, times(1)).getCluster(eq(serviceAccountKey), eq(false), eq(ZONE_CLUSTER), eq("default"));
    assertThat(result).isEqualTo(config);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotGetClusterIfError() {
    when(gkeClusterHelper.getCluster(serviceAccountKey, false, ZONE_CLUSTER, "default"))
        .thenThrow(WingsException.class);

    try {
      gkeClusterService.getCluster(
          COMPUTE_PROVIDER_SETTING.toDTO(), Collections.emptyList(), ZONE_CLUSTER, "default", false);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      // Expected
    }

    verify(gkeClusterHelper, times(1)).getCluster(eq(serviceAccountKey), eq(false), eq(ZONE_CLUSTER), eq("default"));
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldListClusters() {
    List<String> clusterList = Arrays.asList("zone-a/cluster-1", "zone-a/cluster-2");
    when(gkeClusterHelper.listClusters(serviceAccountKey, false)).thenReturn(clusterList);

    List<String> result = gkeClusterService.listClusters(COMPUTE_PROVIDER_SETTING.toDTO(), Collections.emptyList());

    verify(gkeClusterHelper, times(1)).listClusters(eq(serviceAccountKey), eq(false));
    assertThat(result).containsExactlyInAnyOrder("zone-a/cluster-1", "zone-a/cluster-2");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotListClustersIfError() {
    when(gkeClusterHelper.listClusters(serviceAccountKey, false)).thenReturn(null);

    List<String> result = gkeClusterService.listClusters(COMPUTE_PROVIDER_SETTING.toDTO(), Collections.emptyList());

    verify(gkeClusterHelper, times(1)).listClusters(eq(serviceAccountKey), eq(false));
    assertThat(result).isNull();
  }
}
