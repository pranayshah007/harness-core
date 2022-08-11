package io.harness.cdng.pipeline.helpers;

import static io.harness.rule.OwnerRule.VLAD;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.NGInstanceUnitType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.rule.Owner;
import io.harness.steps.matrix.StrategyParameters;

import software.wings.utils.ArtifactType;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class CDNGPipelineExecutionStrategyHelperTest extends CategoryTest {
  private static final String STRATEGY = "strategy:";

  @InjectMocks
  private final CDNGPipelineExecutionStrategyHelper cdngPipelineExecutionStrategyHelper =
      new CDNGPipelineExecutionStrategyHelper();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testGenerateCanaryYaml() throws IOException {
    Integer[] phases = {50, 100};
    StrategyParameters strategyParameters = StrategyParameters.builder()
                                                .phases(phases)
                                                .unitType(NGInstanceUnitType.PERCENTAGE)
                                                .artifactType(ArtifactType.WAR)
                                                .build();
    String result =
        cdngPipelineExecutionStrategyHelper.generateCanaryYaml(ServiceDefinitionType.SSH, strategyParameters, false);
    assertThat(result).contains(STRATEGY);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testGenerateRollingYaml() throws IOException {
    StrategyParameters strategyParameters =
        StrategyParameters.builder().instances(5).artifactType(ArtifactType.JAR).build();
    String result =
        cdngPipelineExecutionStrategyHelper.generateRollingYaml(ServiceDefinitionType.WINRM, strategyParameters, false);
    assertThat(result).contains(STRATEGY);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testGenerateCanaryYamlWinRm() throws IOException {
    Integer[] phases = {50, 100};
    StrategyParameters strategyParameters = StrategyParameters.builder()
                                                .phases(phases)
                                                .unitType(NGInstanceUnitType.PERCENTAGE)
                                                .artifactType(ArtifactType.JAR)
                                                .build();
    String result =
        cdngPipelineExecutionStrategyHelper.generateCanaryYaml(ServiceDefinitionType.WINRM, strategyParameters, false);
    assertThat(result).contains(STRATEGY);
  }
}
