/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.kinds;

import static io.harness.cdng.manifest.yaml.HelmCommandFlagType.Fetch;
import static io.harness.cdng.manifest.yaml.HelmCommandFlagType.Template;
import static io.harness.cdng.manifest.yaml.StoreConfigHelper.CONNECTOR_REF;
import static io.harness.cdng.manifest.yaml.StoreConfigHelper.FOLDER_PATH;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.HelmManifestCommandFlag;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.k8s.model.HelmVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.CDP)
public class HelmChartManifestTest extends CategoryTest {
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testApplyOverrides() {
    StoreConfigWrapper originalStoreConfig = Mockito.mock(StoreConfigWrapper.class);
    StoreConfigWrapper overrideStoreConfig = Mockito.mock(StoreConfigWrapper.class);
    HelmChartManifest original = HelmChartManifest.builder()
                                     .helmVersion(HelmVersion.V3)
                                     .skipResourceVersioning(ParameterField.createValueField(true))
                                     .valuesPaths(ParameterField.createValueField(asList("file/path")))
                                     .store(ParameterField.createValueField(originalStoreConfig))
                                     .commandFlags(asList(HelmManifestCommandFlag.builder()
                                                              .commandType(Fetch)
                                                              .flag(ParameterField.createValueField("--debug"))
                                                              .build()))
                                     .build();

    HelmChartManifest override = HelmChartManifest.builder()
                                     .helmVersion(HelmVersion.V2)
                                     .skipResourceVersioning(ParameterField.createValueField(false))
                                     .valuesPaths(ParameterField.createValueField(asList("override/file/path")))
                                     .store(ParameterField.createValueField(overrideStoreConfig))
                                     .commandFlags(asList(HelmManifestCommandFlag.builder()
                                                              .commandType(Template)
                                                              .flag(ParameterField.createValueField("--template"))
                                                              .build(),
                                         HelmManifestCommandFlag.builder()
                                             .commandType(Fetch)
                                             .flag(ParameterField.createValueField("--updated-debug"))
                                             .build()))
                                     .build();

    doReturn(overrideStoreConfig).when(originalStoreConfig).applyOverrides(overrideStoreConfig);

    HelmChartManifest result = (HelmChartManifest) original.applyOverrides(override);

    assertThat(original.getHelmVersion()).isEqualTo(HelmVersion.V3);
    assertThat(original.getSkipResourceVersioning().getValue()).isTrue();
    assertThat(original.getValuesPaths().getValue().size()).isEqualTo(1);
    assertThat(original.getValuesPaths().getValue().get(0)).isEqualTo("file/path");
    assertThat(original.getStore().getValue()).isEqualTo(originalStoreConfig);
    assertThat(original.getCommandFlags().stream().map(HelmManifestCommandFlag::getCommandType))
        .containsExactlyInAnyOrder(Fetch);
    assertThat(original.getCommandFlags().stream().map(HelmManifestCommandFlag::getFlag).map(ParameterField::getValue))
        .containsExactlyInAnyOrder("--debug");

    assertThat(override.getHelmVersion()).isEqualTo(HelmVersion.V2);
    assertThat(override.getSkipResourceVersioning().getValue()).isFalse();
    assertThat(override.getValuesPaths().getValue().size()).isEqualTo(1);
    assertThat(override.getValuesPaths().getValue().get(0)).isEqualTo("override/file/path");
    assertThat(override.getStore().getValue()).isEqualTo(overrideStoreConfig);
    assertThat(override.getCommandFlags().stream().map(HelmManifestCommandFlag::getCommandType))
        .containsExactlyInAnyOrder(Template, Fetch);
    assertThat(override.getCommandFlags().stream().map(HelmManifestCommandFlag::getFlag).map(ParameterField::getValue))
        .containsExactlyInAnyOrder("--template", "--updated-debug");

    assertThat(result.getHelmVersion()).isEqualTo(HelmVersion.V2);
    assertThat(result.getSkipResourceVersioning().getValue()).isFalse();
    assertThat(result.getValuesPaths().getValue().size()).isEqualTo(1);
    assertThat(result.getValuesPaths().getValue().get(0)).isEqualTo("override/file/path");
    assertThat(result.getStore().getValue()).isEqualTo(overrideStoreConfig);
    assertThat(result.getCommandFlags().stream().map(HelmManifestCommandFlag::getCommandType))
        .containsExactlyInAnyOrder(Template, Fetch);
    assertThat(result.getCommandFlags().stream().map(HelmManifestCommandFlag::getFlag).map(ParameterField::getValue))
        .containsExactlyInAnyOrder("--template", "--updated-debug");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testApplyOverridesWithNulls() {
    StoreConfigWrapper storeConfig = Mockito.mock(StoreConfigWrapper.class);
    HelmChartManifest original = HelmChartManifest.builder()
                                     .skipResourceVersioning(ParameterField.createValueField(true))
                                     .valuesPaths(ParameterField.createValueField(asList("file/path")))
                                     .store(ParameterField.createValueField(storeConfig))
                                     .helmVersion(HelmVersion.V3)
                                     .commandFlags(asList(HelmManifestCommandFlag.builder()
                                                              .commandType(Template)
                                                              .flag(ParameterField.createValueField("--template"))
                                                              .build(),
                                         HelmManifestCommandFlag.builder()
                                             .commandType(Fetch)
                                             .flag(ParameterField.createValueField("--debug"))
                                             .build()))
                                     .build();

    HelmChartManifest override = HelmChartManifest.builder().build();

    HelmChartManifest result = (HelmChartManifest) original.applyOverrides(override);

    assertThat(original.getHelmVersion()).isEqualTo(HelmVersion.V3);
    assertThat(original.getSkipResourceVersioning().getValue()).isTrue();
    assertThat(original.getValuesPaths().getValue().size()).isEqualTo(1);
    assertThat(original.getValuesPaths().getValue().get(0)).isEqualTo("file/path");
    assertThat(original.getCommandFlags().stream().map(HelmManifestCommandFlag::getCommandType))
        .containsExactlyInAnyOrder(Template, Fetch);
    assertThat(original.getCommandFlags().stream().map(HelmManifestCommandFlag::getFlag).map(ParameterField::getValue))
        .containsExactlyInAnyOrder("--template", "--debug");

    assertThat(result).isEqualTo(original);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testValidateAtRuntime() {
    HelmChartManifest helmChartManifest = HelmChartManifest.builder()
                                              .chartName(ParameterField.createValueField("<+input>"))
                                              .store(ParameterField.createValueField(
                                                  StoreConfigWrapper.builder()
                                                      .spec(GithubStore.builder()
                                                                .commitId(ParameterField.createValueField("commitId"))
                                                                .connectorRef(null)
                                                                .folderPath(ParameterField.createValueField("<+input>"))
                                                                .build())
                                                      .build()))
                                              .build();
    Set<String> invalidParameters = helmChartManifest.validateAtRuntime();
    Set<String> expectedInvalidParameters = new HashSet<>();
    expectedInvalidParameters.add(CONNECTOR_REF);
    expectedInvalidParameters.add(FOLDER_PATH);
    assertThat(invalidParameters).isEqualTo(expectedInvalidParameters);
  }
}
