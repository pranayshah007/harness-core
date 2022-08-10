/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertNotNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineServiceConfigurationTest extends CategoryTest {
  @InjectMocks PipelineServiceConfiguration configuration;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetSwaggerBundleConfiguration() {
    SwaggerBundleConfiguration swaggerBundleConfiguration = configuration.getSwaggerBundleConfiguration();
    assertNotNull(swaggerBundleConfiguration);
    assertNotNull(swaggerBundleConfiguration.getResourcePackage());
    assertNotNull(swaggerBundleConfiguration.getTitle());
    assertNotNull(swaggerBundleConfiguration.getVersion());
    assertNotNull(swaggerBundleConfiguration.getSchemes());
  }
}
