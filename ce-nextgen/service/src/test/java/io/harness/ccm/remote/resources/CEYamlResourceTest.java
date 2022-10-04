/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.remote.beans.K8sClusterSetupRequest;
import io.harness.ccm.service.intf.CEYamlService;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.HintException;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CEYamlResourceTest extends CategoryTest {
  @Mock private HttpServletRequest mockHttpServletRequest;
  @Mock private CEYamlService ceYamlService;
  @InjectMocks private CEYamlResource ceYamlResource;

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testCloudCostK8sClusterSetupThrowsDelegateNAException() throws Exception {
    when(ceYamlService.unifiedCloudCostK8sClusterYaml(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenThrow(new DelegateServiceDriverException(""));

    assertThatThrownBy(()
                           -> ceYamlResource.cloudCostK8sClusterSetupV2(mockHttpServletRequest,
                               "kmpySmUISimoRrJL6NL73w", true, true, K8sClusterSetupRequest.builder().build()))
        .isExactlyInstanceOf(HintException.class)
        .hasMessageContaining("Please make sure that your delegates are connected");
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testCloudCostK8sClusterSetup() throws Exception {
    final String YAML_CONTENT = "yaml content";
    when(ceYamlService.unifiedCloudCostK8sClusterYaml(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn(YAML_CONTENT);

    final Response response = ceYamlResource.cloudCostK8sClusterSetupV2(
        mockHttpServletRequest, "kmpySmUISimoRrJL6NL73w", true, true, K8sClusterSetupRequest.builder().build());

    assertThat(response.getEntity()).isNotNull();
    assertThat(response.getEntity().toString()).isEqualTo(YAML_CONTENT);
  }
}
