/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import static io.harness.rule.OwnerRule.RAGHAV_MURALI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.utils.IteratorTestHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class IteratorExecutionHandlerImplTest extends CategoryTest {
  private static final String iteratorConfigPath = System.getProperty("user.dir");
  private static final String iteratorConfigFile = iteratorConfigPath + "/iterator_config.json";
  private static final List<IteratorExecutionHandler.DynamicIteratorConfig> testConfigList =
      new ArrayList<>(Arrays.asList(IteratorExecutionHandler.DynamicIteratorConfig.builder()
                                        .name("AlertReconciliation")
                                        .enabled(true)
                                        .threadPoolSize(1)
                                        .threadPoolIntervalInSeconds(10)
                                        .nextIterationMode("TARGET")
                                        .targetIntervalInSeconds(10)
                                        .iteratorMode("PUMP")
                                        .build(),
          IteratorExecutionHandler.DynamicIteratorConfig.builder()
              .name("ArtifactCollection")
              .enabled(false)
              .threadPoolSize(3)
              .threadPoolIntervalInSeconds(60)
              .nextIterationMode("THROTTLE")
              .throttleIntervalInSeconds(10)
              .iteratorMode("LOOP")
              .build()));
  private IteratorExecutionHandlerImpl iteratorExecutionHandler;

  @Before
  public void setup() throws IOException {
    iteratorExecutionHandler = new IteratorExecutionHandlerImpl(iteratorConfigPath, iteratorConfigFile);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void registerIterators_iteratorInitState() {
    // Arrange
    String iteratorName = "AlertReconciliation";
    IteratorBaseHandler iteratorBaseHandler = new IteratorTestHandler(iteratorName);

    // Act
    iteratorBaseHandler.registerIterator(iteratorExecutionHandler);

    // Assert
    assertThat(iteratorExecutionHandler.getIteratorState().containsKey(iteratorName)).isEqualTo(true);
    IteratorExecutionHandlerImpl.IteratorState iteratorState =
        iteratorExecutionHandler.getIteratorState().get(iteratorName);
    assertThat(iteratorState.getIteratorStateValue()).isEqualTo(IteratorExecutionHandlerImpl.IteratorStateValues.INIT);
    assertThat(iteratorState.getIteratorConfigOption()).isEqualTo(null);
    assertThat(iteratorBaseHandler.getIterator()).isEqualTo(null);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void startIterators_iteratorsEnabledAndDisabled() throws IOException {
    // Arrange
    String iteratorName1 = testConfigList.get(0).getName();
    String iteratorName2 = testConfigList.get(1).getName();
    IteratorBaseHandler iteratorBaseHandler1 = new IteratorTestHandler(iteratorName1);
    IteratorBaseHandler iteratorBaseHandler2 = new IteratorTestHandler(iteratorName2);
    List<IteratorExecutionHandler.DynamicIteratorConfig> iteratorConfigOptionList = new ArrayList<>(testConfigList);
    writeTestIteratorConfigToFile(iteratorConfigOptionList);
    iteratorBaseHandler1.registerIterator(iteratorExecutionHandler);
    iteratorBaseHandler2.registerIterator(iteratorExecutionHandler);

    // Act
    iteratorExecutionHandler.startIterators();

    // Assert
    assertThat(iteratorExecutionHandler.getIteratorState().containsKey(iteratorName1)).isEqualTo(true);
    IteratorExecutionHandlerImpl.IteratorState iteratorState =
        iteratorExecutionHandler.getIteratorState().get(iteratorName1);
    assertThat(iteratorState.getIteratorStateValue())
        .isEqualTo(IteratorExecutionHandlerImpl.IteratorStateValues.RUNNING);
    assertThat(iteratorState.getIteratorConfigOption()).isEqualTo(iteratorConfigOptionList.get(0));
    assertThat(iteratorBaseHandler1.getIterator().getExecutorService().isShutdown()).isEqualTo(false);

    assertThat(iteratorExecutionHandler.getIteratorState().containsKey(iteratorName2)).isEqualTo(true);
    iteratorState = iteratorExecutionHandler.getIteratorState().get(iteratorName2);
    assertThat(iteratorState.getIteratorStateValue())
        .isEqualTo(IteratorExecutionHandlerImpl.IteratorStateValues.NOT_RUNNING);
    assertThat(iteratorState.getIteratorConfigOption()).isEqualTo(iteratorConfigOptionList.get(1));
    assertThat(iteratorBaseHandler2.getIterator()).isEqualTo(null);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void applyConfiguration_disableEnabledIterator() throws IOException {
    // Arrange
    String iteratorName = testConfigList.get(0).getName();
    IteratorBaseHandler iteratorBaseHandler = new IteratorTestHandler(iteratorName);
    List<IteratorExecutionHandler.DynamicIteratorConfig> iteratorConfigOptionList =
        new ArrayList<>(Arrays.asList(testConfigList.get(0)));
    writeTestIteratorConfigToFile(iteratorConfigOptionList);
    iteratorBaseHandler.registerIterator(iteratorExecutionHandler);

    iteratorExecutionHandler.startIterators();

    IteratorExecutionHandler.DynamicIteratorConfig iteratorConfigOption =
        IteratorExecutionHandler.DynamicIteratorConfig.builder().name(iteratorName).enabled(false).build();

    // Act
    iteratorExecutionHandler.applyConfiguration(iteratorConfigOption);

    // Assert
    IteratorExecutionHandlerImpl.IteratorState iteratorState =
        iteratorExecutionHandler.getIteratorState().get(iteratorName);
    assertThat(iteratorState.getIteratorStateValue())
        .isEqualTo(IteratorExecutionHandlerImpl.IteratorStateValues.NOT_RUNNING);
    assertThat(iteratorState.getIteratorConfigOption()).isEqualTo(iteratorConfigOption);
    assertThat(iteratorBaseHandler.getIterator().getExecutorService().isShutdown()).isEqualTo(true);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void applyConfiguration_enableDisabledIterator() throws IOException {
    // Arrange
    String iteratorName = "AlertReconciliation";
    IteratorBaseHandler iteratorBaseHandler = new IteratorTestHandler(iteratorName);
    List<IteratorExecutionHandler.DynamicIteratorConfig> iteratorConfigOptionList = new ArrayList<>();
    iteratorConfigOptionList.add(
        IteratorExecutionHandler.DynamicIteratorConfig.builder().name(iteratorName).enabled(false).build());
    writeTestIteratorConfigToFile(iteratorConfigOptionList);
    iteratorBaseHandler.registerIterator(iteratorExecutionHandler);

    iteratorExecutionHandler.startIterators();

    IteratorExecutionHandler.DynamicIteratorConfig iteratorConfigOption = testConfigList.get(0);

    // Act
    iteratorExecutionHandler.applyConfiguration(iteratorConfigOption);

    // Assert
    assertThat(iteratorBaseHandler.getIteratorName()).isEqualTo(iteratorName);
    IteratorExecutionHandlerImpl.IteratorState iteratorState =
        iteratorExecutionHandler.getIteratorState().get(iteratorName);
    assertThat(iteratorState.getIteratorStateValue())
        .isEqualTo(IteratorExecutionHandlerImpl.IteratorStateValues.RUNNING);
    assertThat(iteratorState.getIteratorConfigOption()).isEqualTo(iteratorConfigOption);
    assertThat(iteratorBaseHandler.getIterator().getExecutorService().isShutdown()).isEqualTo(false);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void applyConfiguration_noChangeInConfig() throws IOException {
    // Arrange
    String iteratorName = "AlertReconciliation";
    IteratorBaseHandler iteratorBaseHandler = new IteratorTestHandler(iteratorName);
    List<IteratorExecutionHandler.DynamicIteratorConfig> iteratorConfigOptionList =
        new ArrayList<>(Arrays.asList(testConfigList.get(0)));
    writeTestIteratorConfigToFile(iteratorConfigOptionList);
    iteratorBaseHandler.registerIterator(iteratorExecutionHandler);

    iteratorExecutionHandler.startIterators();

    // Act
    iteratorExecutionHandler.applyConfiguration(iteratorConfigOptionList.get(0));

    // Assert
    assertThat(iteratorBaseHandler.getIteratorName()).isEqualTo(iteratorName);
    IteratorExecutionHandlerImpl.IteratorState iteratorState =
        iteratorExecutionHandler.getIteratorState().get(iteratorName);
    assertThat(iteratorState.getIteratorStateValue())
        .isEqualTo(IteratorExecutionHandlerImpl.IteratorStateValues.RUNNING);
    assertThat(iteratorState.getIteratorConfigOption()).isEqualTo(iteratorConfigOptionList.get(0));
    assertThat(iteratorBaseHandler.getIterator().getExecutorService().isShutdown()).isEqualTo(false);
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
