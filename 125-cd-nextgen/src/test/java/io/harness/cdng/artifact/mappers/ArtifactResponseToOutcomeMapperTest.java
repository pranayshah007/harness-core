package io.harness.cdng.artifact.mappers;

import static io.harness.rule.OwnerRule.SAHIL;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.ParameterField;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.ArtifactOutcome;
import io.harness.cdng.artifact.bean.DockerArtifactOutcome;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.rule.Owner;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ArtifactResponseToOutcomeMapperTest extends CategoryTest {
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testToArtifactOutcome() {
    ArtifactConfig artifactConfig = DockerHubArtifactConfig.builder()
                                        .connectorRef(ParameterField.createValueField("connector"))
                                        .imagePath(ParameterField.createValueField("IMAGE"))
                                        .build();
    ArtifactDelegateResponse artifactDelegateResponse = DockerArtifactDelegateResponse.builder().build();

    ArtifactOutcome artifactOutcome =
        ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, artifactDelegateResponse);

    assertThat(artifactOutcome).isNotNull();
    assertThat(artifactOutcome).isInstanceOf(DockerArtifactOutcome.class);
  }
}