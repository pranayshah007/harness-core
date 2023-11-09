/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cdng.gitops;

import static io.harness.rule.OwnerRule.MEENA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.gitops.updategitopsapp.KustomizeReplicas;
import io.harness.cdng.gitops.updategitopsapp.KustomizeValues;
import io.harness.cdng.gitops.updategitopsapp.UpdateGitOpsAppRunnable;
import io.harness.cdng.gitops.updategitopsapp.UpdateGitOpsAppStepParameters;
import io.harness.gitops.models.ApplicationResource;
import io.harness.gitops.models.ApplicationResource.App;
import io.harness.gitops.models.ApplicationResource.ApplicationSpec;
import io.harness.gitops.models.ApplicationResource.KustomizeSource;
import io.harness.gitops.models.ApplicationResource.Replicas;
import io.harness.gitops.models.ApplicationResource.Source;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junitparams.JUnitParamsRunner;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.GITOPS)
@RunWith(JUnitParamsRunner.class)
public class UpdateGitOpsAppRunnableTest extends CDNGTestBase {
  @InjectMocks private UpdateGitOpsAppRunnable updateGitOpsAppRunnable;

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testPopulateKustomizeStringValues() {
    Source source = Source.builder().kustomize(KustomizeSource.builder().images(List.of("test:123")).build()).build();
    KustomizeValues pmsKustomizeValues =
        KustomizeValues.builder()
            .namePrefix(ParameterField.createValueField("updated-prefix"))
            .nameSuffix(ParameterField.createValueField("updated-suffix"))
            .namespace(ParameterField.createValueField("updated-ns"))
            .commonAnnotations(ParameterField.createValueField(null))
            .commonLabels(ParameterField.createValueField(null))
            .forceCommonLabels(ParameterField.createValueField(null))
            .forceCommonAnnotations(ParameterField.createValueField(null))
            .replicas(ParameterField.createValueField(List.of(KustomizeReplicas.builder()
                                                                  .name(ParameterField.createValueField("deployment"))
                                                                  .count(ParameterField.createValueField("2"))
                                                                  .build())))
            .images(ParameterField.<List<String>>builder().value(List.of("test:updated-image")).build())
            .build();

    updateGitOpsAppRunnable.getUpdateRequest(getApplicationResource(source), getStepParams(pmsKustomizeValues));
    assertThat(source).isNotNull();
    assertThat(source.getKustomize().getImages()).isEqualTo(List.of("test:updated-image"));
    assertThat(source.getKustomize().getNamePrefix()).isEqualTo("updated-prefix");
    assertThat(source.getKustomize().getNameSuffix()).isEqualTo("updated-suffix");
    assertThat(source.getKustomize().getNamespace()).isEqualTo("updated-ns");
    assertThat(source.getKustomize().getReplicas()).isNotNull();
    assertThat(source.getKustomize().getReplicas())
        .isEqualTo(List.of(Replicas.builder().name("deployment").count("2").build()));
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testPopulateKustomizeCommonAnnotations() {
    Map<String, String> existingParams = new HashMap<>();
    existingParams.put("existing/field", "value");

    Map<String, String> pmsParams = new HashMap<>();
    pmsParams.put("new/field", "value");
    pmsParams.put("existing/field", "updated-value");

    Source source =
        Source.builder().kustomize(KustomizeSource.builder().commonAnnotations(existingParams).build()).build();
    KustomizeValues pmsKustomizeValues =
        KustomizeValues.builder()
            .namePrefix(ParameterField.createValueField(null))
            .nameSuffix(ParameterField.createValueField(null))
            .namespace(ParameterField.createValueField(null))
            .commonAnnotations(ParameterField.createValueField(pmsParams))
            .commonLabels(ParameterField.createValueField(null))
            .forceCommonLabels(ParameterField.createValueField(null))
            .forceCommonAnnotations(ParameterField.createValueField(true))
            .replicas(ParameterField.createValueField(List.of(KustomizeReplicas.builder()
                                                                  .name(ParameterField.createValueField(null))
                                                                  .count(ParameterField.createValueField(null))
                                                                  .build())))
            .images(ParameterField.<List<String>>builder().value(null).build())
            .build();
    updateGitOpsAppRunnable.getUpdateRequest(getApplicationResource(source), getStepParams(pmsKustomizeValues));
    assertThat(source).isNotNull();
    assertThat(source.getKustomize().getCommonAnnotations()).isNotNull();
    assertThat((Map<String, String>) source.getKustomize().getCommonAnnotations())
        .containsKeys("new/field", "existing/field");
    assertThat(((Map<String, String>) source.getKustomize().getCommonAnnotations()).get("existing/field"))
        .isEqualTo("updated-value");
    assertThat(source.getKustomize().getForceCommonAnnotations()).isEqualTo(true);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testPopulateKustomizeCommonLabels() {
    Map<String, String> existingParams = new HashMap<>();
    existingParams.put("existing/label", "value");

    Map<String, String> pmsParams = new HashMap<>();
    pmsParams.put("new/label", "value");
    pmsParams.put("existing/label", "updated-value");

    Source source = Source.builder().kustomize(KustomizeSource.builder().commonLabels(existingParams).build()).build();
    KustomizeValues pmsKustomizeValues =
        KustomizeValues.builder()
            .namePrefix(ParameterField.createValueField(null))
            .nameSuffix(ParameterField.createValueField(null))
            .namespace(ParameterField.createValueField(null))
            .commonAnnotations(ParameterField.createValueField(null))
            .commonLabels(ParameterField.createValueField(pmsParams))
            .forceCommonLabels(ParameterField.createValueField(true))
            .forceCommonAnnotations(ParameterField.createValueField(null))
            .replicas(ParameterField.createValueField(List.of(KustomizeReplicas.builder()
                                                                  .name(ParameterField.createValueField(null))
                                                                  .count(ParameterField.createValueField(null))
                                                                  .build())))
            .images(ParameterField.<List<String>>builder().value(null).build())
            .build();
    updateGitOpsAppRunnable.getUpdateRequest(getApplicationResource(source), getStepParams(pmsKustomizeValues));
    assertThat(source).isNotNull();
    assertThat(source.getKustomize().getCommonLabels()).isNotNull();
    assertThat((Map<String, String>) source.getKustomize().getCommonLabels())
        .containsKeys("new/label", "existing/label");
    assertThat(((Map<String, String>) source.getKustomize().getCommonLabels()).get("existing/label"))
        .isEqualTo("updated-value");
    assertThat(source.getKustomize().getForceCommonLabels()).isEqualTo(true);
  }

  private UpdateGitOpsAppStepParameters getStepParams(KustomizeValues pmsKustomizeValues) {
    UpdateGitOpsAppStepParameters updateGitOpsAppStepParameters = UpdateGitOpsAppStepParameters.infoBuilder().build();
    updateGitOpsAppStepParameters.setKustomize(ParameterField.createValueField(pmsKustomizeValues));
    return updateGitOpsAppStepParameters;
  }

  private ApplicationResource getApplicationResource(Source source) {
    ApplicationResource applicationResource = ApplicationResource.builder().build();
    applicationResource.setApp(App.builder().spec(ApplicationSpec.builder().source(source).build()).build());
    return applicationResource;
  }
}
