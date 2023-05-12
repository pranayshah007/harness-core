/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plugin;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.PluginMetadataConfig;
import io.harness.app.beans.entities.PluginMetadataStatus;
import io.harness.beans.PluginMetadata;
import io.harness.category.element.UnitTests;
import io.harness.repositories.PluginMetadataRepository;
import io.harness.repositories.PluginMetadataStatusRepository;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import java.util.Arrays;
import javax.ws.rs.InternalServerErrorException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Page;

@OwnedBy(CI)
@Slf4j
public class PluginMetadataServiceTest {
  PluginMetadataRepository pluginMetadataRepository = mock(PluginMetadataRepository.class);
  PluginMetadataStatusRepository pluginMetadataStatusRepository = mock(PluginMetadataStatusRepository.class);
  PluginMetadataService pluginMetadataService;

  @Before
  public void setUp() {
    pluginMetadataService = spy(PluginMetadataService.class);
    pluginMetadataService.pluginMetadataStatusRepository = pluginMetadataStatusRepository;
    pluginMetadataService.pluginMetadataRepository = pluginMetadataRepository;
  }

  @Test(expected = InternalServerErrorException.class)
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testListPluginsNotPopulated() {
    doReturn(null).when(pluginMetadataStatusRepository).find();
    pluginMetadataService.listPlugins(null, null, 0, 1);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testListPlugins() {
    doReturn(PluginMetadataStatus.builder().version(1).build()).when(pluginMetadataStatusRepository).find();
    Page<PluginMetadataConfig> pageableMetadataConfig = PageUtils.getPage(Arrays.asList(getMockData()), 0, 1);

    doReturn(pageableMetadataConfig).when(pluginMetadataRepository).findAll(any(), any());
    pluginMetadataService.listPlugins(null, null, 0, 1);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testListPluginsWithKind() {
    doReturn(PluginMetadataStatus.builder().version(1).build()).when(pluginMetadataStatusRepository).find();
    Page<PluginMetadataConfig> pageableMetadataConfig = PageUtils.getPage(Arrays.asList(getMockData()), 0, 1);

    doReturn(pageableMetadataConfig).when(pluginMetadataRepository).findAll(any(), any());
    pluginMetadataService.listPlugins(null, "harness", 0, 1);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testListPluginsWithKindAndName() {
    doReturn(PluginMetadataStatus.builder().version(1).build()).when(pluginMetadataStatusRepository).find();
    Page<PluginMetadataConfig> pageableMetadataConfig = PageUtils.getPage(Arrays.asList(getMockData()), 0, 1);

    doReturn(pageableMetadataConfig).when(pluginMetadataRepository).findAll(any(), any());
    pluginMetadataService.listPlugins("cache", "harness", 0, 1);
  }

  private static PluginMetadataConfig getMockData() {
    return PluginMetadataConfig.builder()
        .metadata(PluginMetadata.builder()
                      .name("test")
                      .inputs(Arrays.asList(PluginMetadata.Input.builder().name("test").build()))
                      .build())
        .build();
  }
}
