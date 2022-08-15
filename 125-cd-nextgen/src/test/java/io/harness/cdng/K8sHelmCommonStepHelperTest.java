package io.harness.cdng;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome;
import io.harness.cdng.manifest.yaml.kinds.kustomize.OverlayConfiguration;
import io.harness.logging.LoggingInitializer;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sHelmCommonStepHelperTest extends CategoryTest {
  @Inject @InjectMocks K8sHelmCommonStepHelper k8sHelmCommonStepHelper;
  @Inject K8sHelmCommonStepHelper spyHelmCommonStepHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    LoggingInitializer.initializeLogging();
    spyHelmCommonStepHelper = Mockito.spy(k8sHelmCommonStepHelper);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void getKustomizeManifestBasePathTest() {
    GithubStore githubStore = GithubStore.builder().folderPath(ParameterField.createValueField("kustomize/")).build();
    KustomizeManifestOutcome kustomizeManifestOutcome =
        KustomizeManifestOutcome.builder()
            .overlayConfiguration(ParameterField.createValueField(
                OverlayConfiguration.builder()
                    .kustomizeYamlFolderPath(ParameterField.createValueField("env/prod/"))
                    .build()))
            .build();
    List<String> paths = k8sHelmCommonStepHelper.getKustomizeManifestBasePath(githubStore, kustomizeManifestOutcome);

    assertThat(paths.get(0)).isEqualTo("kustomize/");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void getKustomizeManifestBasePathCase2Test() {
    GithubStore githubStore = GithubStore.builder().folderPath(ParameterField.createValueField("kustomize/")).build();
    KustomizeManifestOutcome kustomizeManifestOutcome =
        KustomizeManifestOutcome.builder()
            .overlayConfiguration(ParameterField.createValueField(OverlayConfiguration.builder().build()))
            .build();
    List<String> paths = k8sHelmCommonStepHelper.getKustomizeManifestBasePath(githubStore, kustomizeManifestOutcome);

    assertThat(paths.get(0)).isEqualTo("/");
  }
}