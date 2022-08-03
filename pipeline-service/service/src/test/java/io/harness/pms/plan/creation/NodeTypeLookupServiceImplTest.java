/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.creation;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class NodeTypeLookupServiceImplTest extends CategoryTest {
  @InjectMocks NodeTypeLookupServiceImpl nodeTypeLookupService;
  @Mock PmsSdkInstanceService pmsSdkInstanceService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFindNodeTypeServiceName() {
    Map<String, Map<String, Set<String>>> instances = new HashMap<>();
    doReturn(instances).when(pmsSdkInstanceService).getInstanceNameToSupportedTypes();
    assertThatThrownBy(() -> nodeTypeLookupService.findNodeTypeServiceName("blah"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Supported Types Map is empty");

    Map<String, Set<String>> service1SupportedTypes = new HashMap<>();
    service1SupportedTypes.put("stage", Collections.singleton("Service1"));
    service1SupportedTypes.put("step", Collections.singleton("Service1Step"));
    instances.put("service1", service1SupportedTypes);

    Map<String, Set<String>> service2SupportedTypes = new HashMap<>();
    service2SupportedTypes.put("stage", Collections.singleton("Service2"));
    service2SupportedTypes.put("step", Collections.singleton("Service2Step"));
    instances.put("service2", service2SupportedTypes);

    Map<String, Set<String>> cfServiceSupportedTypes = new HashMap<>();
    cfServiceSupportedTypes.put("stage", Collections.singleton("FeatureFlag"));
    instances.put("anyName", cfServiceSupportedTypes);

    doReturn(instances).when(pmsSdkInstanceService).getInstanceNameToSupportedTypes();
    String serviceName = nodeTypeLookupService.findNodeTypeServiceName("Service2");
    assertThat(serviceName).isEqualTo("service2");
    serviceName = nodeTypeLookupService.findNodeTypeServiceName("Service2Step");
    assertThat(serviceName).isEqualTo("service2");
    serviceName = nodeTypeLookupService.findNodeTypeServiceName("Service1");
    assertThat(serviceName).isEqualTo("service1");
    serviceName = nodeTypeLookupService.findNodeTypeServiceName("Service1Step");
    assertThat(serviceName).isEqualTo("service1");
    serviceName = nodeTypeLookupService.findNodeTypeServiceName("FeatureFlag");
    assertThat(serviceName).isEqualTo("cf");

    assertThatThrownBy(() -> nodeTypeLookupService.findNodeTypeServiceName("eh"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unknown Node type: eh");
  }
}
