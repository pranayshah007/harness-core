/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.appmanifest;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.manifest.CustomSourceConfig;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.GitFileConfig;
import software.wings.beans.helm.HelmCommandFlagConfig;
import software.wings.beans.helm.HelmCommandFlagConstants.HelmSubCommand;
import software.wings.helpers.ext.kustomize.KustomizeConfig;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
@TargetModule(_957_CG_BEANS)
public class ApplicationManifestTest extends WingsBaseTest {
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void cloneInternalForBareBoneAppManifest() {
    ApplicationManifest sourceManifest = buildBareBoneAppManifest(StoreType.KustomizeSourceRepo);
    ApplicationManifest destManifest = sourceManifest.cloneInternal();
    verifyCloning(sourceManifest, destManifest);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void cloneInternalForKustomizeManifest() {
    ApplicationManifest sourceManifest = buildBareBoneAppManifest(StoreType.KustomizeSourceRepo);
    sourceManifest.setKustomizeConfig(
        KustomizeConfig.builder().pluginRootDir("./foo").kustomizeDirPath("./home").build());
    sourceManifest.setGitFileConfig(
        GitFileConfig.builder().connectorId("connectedId").branch("master").useBranch(true).build());
    ApplicationManifest destManifest = sourceManifest.cloneInternal();
    verifyCloning(sourceManifest, destManifest);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void cloneInternalForCustomManifest() {
    ApplicationManifest sourceManifest = buildBareBoneAppManifest(StoreType.CUSTOM);
    sourceManifest.setCustomSourceConfig(CustomSourceConfig.builder().path("test").script("echo test").build());
    ApplicationManifest destManifest = sourceManifest.cloneInternal();
    verifyCloning(sourceManifest, destManifest);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void cloneInternalForHelmCommandFlags() {
    ApplicationManifest sourceManifest = buildBareBoneAppManifest(StoreType.CUSTOM);
    Map<HelmSubCommand, String> commandFlagMap = new HashMap<>();
    commandFlagMap.put(HelmSubCommand.FETCH, "fetch-flag");
    commandFlagMap.put(HelmSubCommand.DELETE, "delete-flag");
    sourceManifest.setHelmCommandFlag(HelmCommandFlagConfig.builder().valueMap(commandFlagMap).build());
    ApplicationManifest destManifest = sourceManifest.cloneInternal();
    verifyCloning(sourceManifest, destManifest);
  }

  private void verifyCloning(ApplicationManifest sourceManifest, ApplicationManifest destManifest) {
    assertThat(sourceManifest != destManifest).isTrue();
    assertThat(sourceManifest.getKustomizeConfig() == null
        || sourceManifest.getKustomizeConfig() != destManifest.getKustomizeConfig())
        .isTrue();
    assertThat(sourceManifest.getCustomSourceConfig() == null
        || sourceManifest.getCustomSourceConfig() != destManifest.getCustomSourceConfig())
        .isTrue();
    assertThat(sourceManifest).isEqualTo(destManifest);
    if (sourceManifest.getHelmCommandFlag() != null) {
      MapDifference<HelmSubCommand, String> diff = Maps.difference(
          sourceManifest.getHelmCommandFlag().getValueMap(), destManifest.getHelmCommandFlag().getValueMap());
      assertThat(diff.areEqual()).isTrue();
    }
  }

  private ApplicationManifest buildBareBoneAppManifest(StoreType storeType) {
    return ApplicationManifest.builder()
        .storeType(storeType)
        .kind(AppManifestKind.K8S_MANIFEST)
        .serviceId("serviceId")
        .skipVersioningForAllK8sObjects(true)
        .build();
  }
}
