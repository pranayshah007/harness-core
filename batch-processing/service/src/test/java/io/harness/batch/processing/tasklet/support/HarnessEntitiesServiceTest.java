/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.support;

import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.tasklet.support.HarnessEntitiesService.HarnessEntities;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HarnessEntitiesServiceTest extends CategoryTest {
  public static final String APP_ENTITY_ID = "APP_ENTITY_ID";
  public static final String APP_NAME = "APP_NAME";
  public static final String ENV_ENTITY_ID = "ENV_ENTITY_ID";
  public static final String ENV_NAME = "APP_ENTITY_ID";
  public static final String SERVICE_ENTITY_ID = "SERVICE_ENTITY_ID";
  public static final String SERVICE_NAME = "SERVICE_NAME";
  @InjectMocks private HarnessEntitiesService harnessEntitiesService;
  @Mock private CloudToHarnessMappingService cloudToHarnessMappingService;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testFetchEntityName() {
    when(cloudToHarnessMappingService.getApplicationName(APP_ENTITY_ID)).thenReturn(APP_NAME);
    when(cloudToHarnessMappingService.getEnvironmentName(ENV_ENTITY_ID)).thenReturn(ENV_NAME);
    when(cloudToHarnessMappingService.getServiceName(SERVICE_ENTITY_ID)).thenReturn(SERVICE_NAME);
    String appName = harnessEntitiesService.fetchEntityName(HarnessEntities.APP, APP_ENTITY_ID);
    assertThat(appName).isEqualTo(APP_NAME);
    String envName = harnessEntitiesService.fetchEntityName(HarnessEntities.ENV, ENV_ENTITY_ID);
    assertThat(envName).isEqualTo(ENV_NAME);
    String serviceName = harnessEntitiesService.fetchEntityName(HarnessEntities.SERVICE, SERVICE_ENTITY_ID);
    assertThat(serviceName).isEqualTo(SERVICE_NAME);
  }
}
