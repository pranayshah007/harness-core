/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import static io.harness.rule.OwnerRule.RAGHAV_MURALI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({IteratorExecutionHandler.class})
@PowerMockIgnore({"javax.xml.*", "org.xml.*"})
public class IteratorConfigWatcherTest {
  private static final String iteratorConfigPath = System.getProperty("user.dir");
  private static final String iteratorConfigFile = iteratorConfigPath + "/iterator_config.json";
  private IteratorConfigWatcher iteratorConfigWatcher;

  @Before
  public void setup() {
    IteratorExecutionHandler iteratorExecutionHandler = PowerMockito.mock(IteratorExecutionHandler.class);
    mockStatic(IteratorExecutionHandler.class);
    iteratorConfigWatcher = new IteratorConfigWatcher(iteratorExecutionHandler, iteratorConfigPath, iteratorConfigFile);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void readIteratorConfiguration_testParsing() throws IOException {
    // Arrange
    String iteratorName1 = "AlertReconciliation";
    String iteratorName2 = "ArtifactCollection";

    List<IteratorExecutionHandler.DynamicIteratorConfig> iteratorConfigOptionList = new ArrayList<>();
    iteratorConfigOptionList.add(IteratorExecutionHandler.DynamicIteratorConfig.builder()
                                     .name(iteratorName1)
                                     .enabled(true)
                                     .threadPoolSize(1)
                                     .threadPoolIntervalInSeconds(10)
                                     .nextIterationMode("TARGET")
                                     .targetIntervalInSeconds(10)
                                     .build());
    iteratorConfigOptionList.add(IteratorExecutionHandler.DynamicIteratorConfig.builder()
                                     .name(iteratorName2)
                                     .enabled(false)
                                     .threadPoolSize(3)
                                     .threadPoolIntervalInSeconds(60)
                                     .nextIterationMode("THROTTLE")
                                     .throttleIntervalInSeconds(10)
                                     .build());
    writeTestIteratorConfigToFile(iteratorConfigOptionList);

    // Act
    IteratorExecutionHandler.DynamicIteratorConfig parsedConfigs[] = iteratorConfigWatcher.readIteratorConfiguration();

    // Assert
    for (int i = 0; i < iteratorConfigOptionList.size(); i++) {
      assertThat(parsedConfigs[i]).isEqualTo(iteratorConfigOptionList.get(i));
    }
  }

  /**
   * Method to write test iterator configuration to the config file
   */
  private void writeTestIteratorConfigToFile(
      List<IteratorExecutionHandler.DynamicIteratorConfig> iteratorConfigOptionList) throws IOException {
    /*
     * Create a iterator configuration file
     * */
    File file = new File(iteratorConfigFile);
    if (!file.exists()) {
      file.createNewFile();
    }

    /*
     * Write test iterator configurations to the config file
     * */
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.writeValue(file, iteratorConfigOptionList);
  }
}
