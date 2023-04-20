/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.resourcegroup;

import static io.harness.platform.resourcegroup.ResourceGroupServiceSetup.RESOURCE_GROUP_SERVICE;
import static io.harness.rule.OwnerRule.NISHANT;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.verifyPrivate;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.platform.remote.ResourceGroupOpenApiResource;
import io.harness.resourcegroup.ResourceGroupServiceConfig;
import io.harness.rule.Owner;

import com.google.inject.Injector;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.reflect.Whitebox;

public class ResourceGroupServiceSetupTest extends CategoryTest {
  @Mock Environment environment;
  @Mock JerseyEnvironment jerseyEnvironment;
  @Mock Injector injector;
  @Mock ResourceGroupServiceSetup resourceGroupServiceSetup;
  @Captor ArgumentCaptor<ResourceGroupOpenApiResource> resourceGroupOpenApiResourceArgumentCaptor;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(injector.getInstance(ResourceGroupOpenApiResource.class)).thenReturn(new ResourceGroupOpenApiResource());
    when(environment.jersey()).thenReturn(jerseyEnvironment);
    doNothing().when(jerseyEnvironment).register(any());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testRegisterOasResource() throws Exception {
    ResourceGroupServiceConfig config =
        ResourceGroupServiceConfig.builder().hostname("localhost").basePathPrefix("").build();
    Whitebox.invokeMethod(resourceGroupServiceSetup, "registerOasResource", config, environment, injector);
    verify(jerseyEnvironment, times(1)).register(resourceGroupOpenApiResourceArgumentCaptor.capture());
    assertEquals(RESOURCE_GROUP_SERVICE, resourceGroupOpenApiResourceArgumentCaptor.getValue().getModule());
    assertEquals(
        0L, resourceGroupOpenApiResourceArgumentCaptor.getValue().getOpenApiConfiguration().getCacheTTL().longValue());
    verifyPrivate(resourceGroupServiceSetup, times(1)).invoke("getOasConfig", config);
  }
}
