/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.dto.HelmChartSpecification;
import software.wings.helpers.ext.helm.request.HelmInstallCommandRequest;
import software.wings.helpers.ext.helm.request.HelmReleaseHistoryCommandRequest;
import software.wings.helpers.ext.helm.request.HelmRollbackCommandRequest;

import com.google.inject.Inject;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class HelmCommandHelperTest extends WingsBaseTest {
  @Inject private HelmCommandHelper helmCommandHelper;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateHelmDeployChartSpecFromYaml() throws Exception {
    Optional<HarnessHelmDeployConfig> optional = helmCommandHelper.generateHelmDeployChartSpecFromYaml("harness:\n"
        + "    helm:\n"
        + "      chart:\n"
        + "          url: http://storage.googleapis.com/kubernetes-charts\n"
        + "          name: ABC\n"
        + "          version: 0.1.0");

    assertThat(optional.isPresent()).isTrue();
    HelmDeployChartSpec helmDeployChartSpec = optional.get().getHelmDeployChartSpec();
    assertThat(helmDeployChartSpec.getUrl()).isEqualTo("http://storage.googleapis.com/kubernetes-charts");
    assertThat(helmDeployChartSpec.getName()).isEqualTo("ABC");
    assertThat(helmDeployChartSpec.getVersion()).isEqualTo("0.1.0");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateHelmDeployChartSpecFromYamlNull() throws Exception {
    Optional<HarnessHelmDeployConfig> optional = helmCommandHelper.generateHelmDeployChartSpecFromYaml("harness:\n"
        + "    helm:\n"
        + "       chart:\n"
        + "          url: http://storage.googleapis.com/kubernetes-charts\n");
    assertThat(optional.isPresent()).isTrue();
    HelmDeployChartSpec helmDeployChartSpec = optional.get().getHelmDeployChartSpec();
    assertThat(helmDeployChartSpec.getUrl()).isEqualTo("http://storage.googleapis.com/kubernetes-charts");
    assertThat(helmDeployChartSpec.getName()).isNull();
    assertThat(helmDeployChartSpec.getVersion()).isNull();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateHelmDeployChartSpecFromYamlInvalid() throws Exception {
    try {
      Optional<HarnessHelmDeployConfig> optional = helmCommandHelper.generateHelmDeployChartSpecFromYaml("harness:\n"
          + "    helm:\n"
          + "       chart:\n");
      assertThat(true).isFalse();
    } catch (Exception e) {
      assertThat(e instanceof WingsException).isTrue();
      assertThat(e.getMessage()).isEqualTo("Invalid Yaml, Failed while parsing yamlString");
      assertThat(true).isTrue();
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateHelmDeployChartSpecFromYamlMultiple() throws Exception {
    Optional<HarnessHelmDeployConfig> optional = helmCommandHelper.generateHelmDeployChartSpecFromYaml("name: ABC\n"
        + "url: http://url.com\n"
        + "---\n"
        + "harness:\n"
        + "    helm:\n"
        + "      chart:\n"
        + "         url: http://storage.googleapis.com/kubernetes-charts\n"
        + "         name: ABC\n"
        + "         version: 0.1.0");

    assertThat(optional.isPresent()).isTrue();
    HelmDeployChartSpec helmDeployChartSpec = optional.get().getHelmDeployChartSpec();
    assertThat(helmDeployChartSpec.getUrl()).isEqualTo("http://storage.googleapis.com/kubernetes-charts");
    assertThat(helmDeployChartSpec.getName()).isEqualTo("ABC");
    assertThat(helmDeployChartSpec.getVersion()).isEqualTo("0.1.0");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGenerateHelmDeployChartSpecFromYamlEmpty() {
    Optional<HarnessHelmDeployConfig> optional = helmCommandHelper.generateHelmDeployChartSpecFromYaml("");
    assertThat(optional.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testIsValidChartSpecification() {
    assertThat(helmCommandHelper.isValidChartSpecification(null)).isFalse();
    assertThat(helmCommandHelper.isValidChartSpecification(HelmChartSpecification.builder().build())).isFalse();
    String DUMMY = "DUMMY";
    assertThat(helmCommandHelper.isValidChartSpecification(HelmChartSpecification.builder().chartName(DUMMY).build()))
        .isTrue();
    assertThat(helmCommandHelper.isValidChartSpecification(HelmChartSpecification.builder().chartUrl(DUMMY).build()))
        .isTrue();
    assertThat(helmCommandHelper.isValidChartSpecification(
                   HelmChartSpecification.builder().chartUrl(DUMMY).chartName(DUMMY).build()))
        .isTrue();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetDeploymentMessage() {
    HelmInstallCommandRequest helmInstallCommandRequest = HelmInstallCommandRequest.builder().build();
    assertThat(helmCommandHelper.getDeploymentMessage(helmInstallCommandRequest)).isEqualTo("Installing");

    HelmRollbackCommandRequest helmRollbackCommandRequest = HelmRollbackCommandRequest.builder().build();
    assertThat(helmCommandHelper.getDeploymentMessage(helmRollbackCommandRequest)).isEqualTo("Rolling back");

    HelmReleaseHistoryCommandRequest helmReleaseHistoryCommandRequest =
        HelmReleaseHistoryCommandRequest.builder().build();
    assertThat(helmCommandHelper.getDeploymentMessage(helmReleaseHistoryCommandRequest))
        .isEqualTo("Getting release history");
  }
}
