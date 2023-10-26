/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.ManifestSourceWrapper;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class K8sApplyStepInfoTest extends CategoryTest {
  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExtractRefs() {
    K8sApplyStepInfo k8sApplyStepInfo =
        K8sApplyStepInfo.infoBuilder()
            .overrides(List.of(
                ManifestConfigWrapper.builder()
                    .manifest(
                        ManifestConfig.builder()
                            .identifier("values_test1")
                            .type(ManifestConfigType.VALUES)
                            .spec(K8sManifest.builder()
                                      .store(ParameterField.createValueField(
                                          StoreConfigWrapper.builder()
                                              .spec(HarnessStore.builder()
                                                        .files(ParameterField.createValueField(List.of("/script.sh")))
                                                        .build())
                                              .build()))

                                      .build())
                            .build())
                    .build()))
            .build();

    Map<String, ParameterField<List<String>>> fileMap;
    fileMap = k8sApplyStepInfo.extractFileRefs();
    assertThat(fileMap.get("overrides.manifest.values_test1.spec.store.spec.files").getValue().get(0))
        .isEqualTo("/script.sh");
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testValidateFieldsWithBothFilePathAndManifestSource() {
    ParameterField<List<String>> filePaths = new ParameterField<>();
    filePaths.setValue(Collections.singletonList("file.yaml"));
    K8sApplyStepInfo k8sApplyStepInfo = K8sApplyStepInfo.infoBuilder()
                                            .manifestSource(ManifestSourceWrapper.builder().build())
                                            .filePaths(filePaths)
                                            .build();
    String stepName = "SampleStep";

    assertThatThrownBy(() -> k8sApplyStepInfo.validateFields(stepName))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage(
            "Configured Apply Step: SampleStep contains both filePath and manifestSource. Please configure only one option to proceed");

    K8sApplyStepInfo k8sApplyStepInfo1 = K8sApplyStepInfo.infoBuilder().filePaths(filePaths).build();
    assertThatCode(() -> k8sApplyStepInfo1.validateFields(stepName)).doesNotThrowAnyException();

    K8sApplyStepInfo k8sApplyStepInfo2 =
        K8sApplyStepInfo.infoBuilder().manifestSource(ManifestSourceWrapper.builder().build()).build();
    assertThatCode(() -> k8sApplyStepInfo2.validateFields(stepName)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testExtractConnectorRefs() {
    GitStore gitStoreStepLevelSource =
        GitStore.builder()
            .branch(ParameterField.createValueField("master"))
            .paths(ParameterField.createValueField(asList("deployment.yaml", "service.yaml")))
            .connectorRef(ParameterField.createValueField("git-connector"))
            .build();
    GitStore gitStoreStepLevelSourceOverride =
        GitStore.builder()
            .branch(ParameterField.createValueField("master"))
            .paths(ParameterField.createValueField(asList("values.yaml")))
            .connectorRef(ParameterField.createValueField("git-connector-override"))
            .build();
    GitStore gitStoreStepLevelSourceOverride2 =
        GitStore.builder()
            .branch(ParameterField.createValueField("master"))
            .paths(ParameterField.createValueField(asList("values2.yaml")))
            .connectorRef(ParameterField.createValueField("git-connector-override2"))
            .build();
    StoreConfigWrapper storeConfigWrapper = StoreConfigWrapper.builder().spec(gitStoreStepLevelSource).build();
    StoreConfigWrapper storeConfigWrapper2 = StoreConfigWrapper.builder().spec(gitStoreStepLevelSourceOverride).build();
    StoreConfigWrapper storeConfigWrapper3 =
        StoreConfigWrapper.builder().spec(gitStoreStepLevelSourceOverride2).build();
    List<ManifestConfigWrapper> serviceOverrides = new ArrayList<>();
    serviceOverrides.add(
        ManifestConfigWrapper.builder()
            .manifest(
                ManifestConfig.builder()
                    .spec(ValuesManifest.builder().store(ParameterField.createValueField(storeConfigWrapper2)).build())
                    .type(ManifestConfigType.VALUES)
                    .identifier("override1")
                    .build())
            .build());
    serviceOverrides.add(ManifestConfigWrapper.builder()
                             .manifest(ManifestConfig.builder()
                                           .spec(ValuesManifest.builder()
                                                     .store(ParameterField.createValueField(storeConfigWrapper3))

                                                     .build())
                                           .type(ManifestConfigType.VALUES)
                                           .identifier("override2")
                                           .build())
                             .build());
    ParameterField<List<String>> filePaths = new ParameterField<>();
    filePaths.setValue(Collections.singletonList("file.yaml"));
    K8sApplyStepInfo k8sApplyStepInfo =
        K8sApplyStepInfo.infoBuilder()
            .manifestSource(ManifestSourceWrapper.builder()
                                .spec(K8sManifest.builder()
                                          .store(ParameterField.createValueField(storeConfigWrapper))
                                          .valuesPaths(ParameterField.createValueField(
                                              asList("path/to/helm/chart/valuesOverride.yaml")))
                                          .build())
                                .type(ManifestConfigType.K8_MANIFEST)
                                .build())
            .overrides(serviceOverrides)
            .build();

    Map<String, ParameterField<String>> response = k8sApplyStepInfo.extractConnectorRefs();
    assertThat(response.size()).isEqualTo(3);
    assertThat(response.get("configuration.overrides.override1.spec.store.spec.connectorRef").getValue())
        .isEqualTo("git-connector-override");
    assertThat(response.get("configuration.overrides.override2.spec.store.spec.connectorRef").getValue())
        .isEqualTo("git-connector-override2");
    assertThat(response.get("configuration.manifestSource.spec.store.spec.connectorRef").getValue())
        .isEqualTo("git-connector");
  }
}
