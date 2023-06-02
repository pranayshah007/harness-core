/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.helpers;

import static io.harness.rule.OwnerRule.VLAD;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.rule.Owner;

import software.wings.utils.ArtifactType;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class DefaultTempateLoaderTest extends CategoryTest {
  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldResolveTemplateConfigForSshZip() {
    DefaultTempateLoader.TemplateConfig templateConfig =
        DefaultTempateLoader.resolveTemplateConfig(ServiceDefinitionType.SSH, ArtifactType.ZIP);
    assertThat(templateConfig.getTemplateIdentifier()).isEqualTo("Default_Install_Tar_Bash");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldResolveTemplateConfigForSshTar() {
    DefaultTempateLoader.TemplateConfig templateConfig =
        DefaultTempateLoader.resolveTemplateConfig(ServiceDefinitionType.SSH, ArtifactType.TAR);
    assertThat(templateConfig.getTemplateIdentifier()).isEqualTo("Default_Install_Tar_Bash");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldResolveTemplateConfigForSshJar() {
    DefaultTempateLoader.TemplateConfig templateConfig =
        DefaultTempateLoader.resolveTemplateConfig(ServiceDefinitionType.SSH, ArtifactType.JAR);
    assertThat(templateConfig.getTemplateIdentifier()).isEqualTo("Default_Install_Jar_Bash");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldResolveTemplateConfigForSshRpm() {
    DefaultTempateLoader.TemplateConfig templateConfig =
        DefaultTempateLoader.resolveTemplateConfig(ServiceDefinitionType.SSH, ArtifactType.RPM);
    assertThat(templateConfig.getTemplateIdentifier()).isEqualTo("Default_Install_Jar_Bash");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldResolveTemplateConfigForSshWar() {
    DefaultTempateLoader.TemplateConfig templateConfig =
        DefaultTempateLoader.resolveTemplateConfig(ServiceDefinitionType.SSH, ArtifactType.WAR);
    assertThat(templateConfig.getTemplateIdentifier()).isEqualTo("Default_Install_War_Bash");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldResolveTemplateConfigForSshOther() {
    DefaultTempateLoader.TemplateConfig templateConfig =
        DefaultTempateLoader.resolveTemplateConfig(ServiceDefinitionType.SSH, ArtifactType.OTHER);
    assertThat(templateConfig.getTemplateIdentifier()).isEqualTo("Default_Install_War_Bash");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldResolveTemplateConfigForWinrmIis() {
    DefaultTempateLoader.TemplateConfig templateConfig =
        DefaultTempateLoader.resolveTemplateConfig(ServiceDefinitionType.WINRM, ArtifactType.IIS);
    assertThat(templateConfig.getTemplateIdentifier()).isEqualTo("Default_IIS_Website_PowerShell");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldResolveTemplateConfigForWinrmIisApp() {
    DefaultTempateLoader.TemplateConfig templateConfig =
        DefaultTempateLoader.resolveTemplateConfig(ServiceDefinitionType.WINRM, ArtifactType.IIS_APP);
    assertThat(templateConfig.getTemplateIdentifier()).isEqualTo("Default_IIS_Application_PowerShell");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldResolveTemplateConfigForWinrmVirtualDir() {
    DefaultTempateLoader.TemplateConfig templateConfig =
        DefaultTempateLoader.resolveTemplateConfig(ServiceDefinitionType.WINRM, ArtifactType.IIS_VirtualDirectory);
    assertThat(templateConfig.getTemplateIdentifier()).isEqualTo("Default_IIS_Virtual_Directory_PowerShell");
  }
}
