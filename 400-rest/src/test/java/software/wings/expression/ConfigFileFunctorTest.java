/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.FunctorException;
import io.harness.rule.Owner;

import software.wings.beans.ConfigFile;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.ServiceTemplateService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ConfigFileFunctorTest extends CategoryTest {
  private static final String APP_ID = "app-id";
  private static final String SERVICE_TEMPLATE_ID = "service-template-id";
  private static final String ENV_ID = "env-id";

  private ConfigFileFunctor cff;
  private ConfigService configService;
  private ServiceTemplateService serviceTemplateService;

  @Before
  public void setUp() throws Exception {
    configService = mock(ConfigService.class);
    serviceTemplateService = mock(ServiceTemplateService.class);
    cff = ConfigFileFunctor.builder()
              .appId(APP_ID)
              .configService(configService)
              .serviceTemplateId(SERVICE_TEMPLATE_ID)
              .serviceTemplateService(serviceTemplateService)
              .envId(ENV_ID)
              .build();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void canReadFileWithinLimit() {
    ConfigFile configFile = new ConfigFile();
    byte[] contents = new byte[ConfigFileFunctor.MAX_CONFIG_FILE_SIZE];
    String relativeFilePath = "file-path";
    when(serviceTemplateService.computedConfigFileByRelativeFilePath(
             APP_ID, "env-id", SERVICE_TEMPLATE_ID, relativeFilePath))
        .thenReturn(configFile);
    when(configService.getFileContent(eq(APP_ID), any(ConfigFile.class))).thenReturn(contents);
    cff.getAsBase64(relativeFilePath);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void readLargeFileThrowsException() {
    ConfigFile configFile = new ConfigFile();
    byte[] contents = new byte[ConfigFileFunctor.MAX_CONFIG_FILE_SIZE + 1];
    String relativeFilePath = "file-path";
    when(serviceTemplateService.computedConfigFileByRelativeFilePath(
             APP_ID, "env-id", SERVICE_TEMPLATE_ID, relativeFilePath))
        .thenReturn(configFile);
    when(configService.getFileContent(eq(APP_ID), any(ConfigFile.class))).thenReturn(contents);
    assertThatExceptionOfType(FunctorException.class).isThrownBy(() -> cff.getAsBase64(relativeFilePath));
  }
}
