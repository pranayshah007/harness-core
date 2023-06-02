/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.BitbucketStore;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.GitLabStore;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Collections;
import org.assertj.core.api.ThrowableAssert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class ManifestOutcomeValidatorTest extends CategoryTest {
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidHelmChartManifest() {
    HelmChartManifestOutcome helmChartManifest =
        HelmChartManifestOutcome.builder()
            .store(GcsStoreConfig.builder()
                       .connectorRef(ParameterField.createValueField("connector"))
                       .bucketName(ParameterField.createValueField("bucket"))
                       .build())
            .chartName(ParameterField.createValueField("chart"))
            .build();

    assertThatCode(() -> ManifestOutcomeValidator.validate(helmChartManifest, false)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidHelmChartManifestGitStore() {
    HelmChartManifestOutcome helmChartManifest =
        HelmChartManifestOutcome.builder()
            .store(GitStore.builder()
                       .connectorRef(ParameterField.createValueField("connector"))
                       .repoName(ParameterField.createValueField("repo"))
                       .gitFetchType(FetchType.BRANCH)
                       .branch(ParameterField.createValueField("master"))
                       .folderPath(ParameterField.createValueField("test"))
                       .build())
            .build();

    assertThatCode(() -> ManifestOutcomeValidator.validate(helmChartManifest, false)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidHelmChartManifestAllowExpression() {
    HelmChartManifestOutcome helmChartManifest =
        HelmChartManifestOutcome.builder()
            .store(GcsStoreConfig.builder()
                       .connectorRef(ParameterField.createValueField("connector"))
                       .bucketName(ParameterField.createValueField("bucket"))
                       .build())
            .chartName(ParameterField.createExpressionField(true, "<+expression>", null, true))
            .build();

    assertThatCode(() -> ManifestOutcomeValidator.validate(helmChartManifest, true)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInvalidHelmChartManifestDisallowExpression() {
    HelmChartManifestOutcome helmChartManifest =
        HelmChartManifestOutcome.builder()
            .store(GcsStoreConfig.builder()
                       .connectorRef(ParameterField.createValueField("connector"))
                       .bucketName(ParameterField.createValueField("bucket"))
                       .build())
            .chartName(ParameterField.createExpressionField(true, "<+expression>", null, true))
            .build();

    assertInvalidParamsArgsMessage(
        () -> ManifestOutcomeValidator.validate(helmChartManifest, false), "chartName: required for Gcs store type");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInvalidHelmChartManifestGitStore() {
    GitStore store = GitStore.builder()
                         .connectorRef(ParameterField.createValueField("connector"))
                         .repoName(ParameterField.createValueField("repo"))
                         .gitFetchType(FetchType.BRANCH)
                         .branch(ParameterField.createValueField("master"))
                         .folderPath(ParameterField.createValueField("test"))
                         .build();
    HelmChartManifestOutcome helmChartManifestChartName =
        HelmChartManifestOutcome.builder().store(store).chartName(ParameterField.createValueField("chartName")).build();

    HelmChartManifestOutcome helmChartManifestChartVersion =
        HelmChartManifestOutcome.builder()
            .store(store)
            .chartVersion(ParameterField.createValueField("chartVersion"))
            .build();

    assertInvalidParamsArgsMessage(()
                                       -> ManifestOutcomeValidator.validate(helmChartManifestChartName, false),
        "chartName: not allowed for Git store type");
    assertInvalidParamsArgsMessage(()
                                       -> ManifestOutcomeValidator.validate(helmChartManifestChartVersion, false),
        "chartVersion: not allowed for Git store");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidGitStoreBranch() {
    GitStore gitStore = GitStore.builder()
                            .connectorRef(ParameterField.createValueField("connector"))
                            .repoName(ParameterField.createValueField("repo"))
                            .gitFetchType(FetchType.BRANCH)
                            .branch(ParameterField.createValueField("master"))
                            .paths(ParameterField.createValueField(Collections.singletonList("test")))
                            .build();

    assertThatCode(() -> ManifestOutcomeValidator.validateStore(gitStore, ManifestType.K8Manifest, "test", false))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidGitStoreBranchAllowExpression() {
    BitbucketStore gitStore = BitbucketStore.builder()
                                  .connectorRef(ParameterField.createExpressionField(true, "<+expression>", null, true))
                                  .repoName(ParameterField.createExpressionField(true, "<+expression>", null, true))
                                  .gitFetchType(FetchType.BRANCH)
                                  .branch(ParameterField.createExpressionField(true, "<+expression>", null, true))
                                  .paths(ParameterField.createValueField(Collections.singletonList("test")))
                                  .build();

    assertThatCode(() -> ManifestOutcomeValidator.validateStore(gitStore, ManifestType.K8Manifest, "test", true))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInvalidGitStoreBranchDisallowExpression() {
    GitLabStore gitStore = GitLabStore.builder()
                               .connectorRef(ParameterField.createExpressionField(true, "<+expression>", null, true))
                               .repoName(ParameterField.createExpressionField(true, "<+expression>", null, true))
                               .gitFetchType(FetchType.BRANCH)
                               .branch(ParameterField.createExpressionField(true, "<+expression>", null, true))
                               .build();

    assertThatThrownBy(() -> ManifestOutcomeValidator.validateStore(gitStore, ManifestType.K8Manifest, "test", false))
        .hasMessageContaining("Missing or empty connectorRef");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInvalidGitStoreBranchCommitId() {
    GithubStore gitStore = GithubStore.builder()
                               .connectorRef(ParameterField.createValueField("connector"))
                               .repoName(ParameterField.createValueField("repo"))
                               .gitFetchType(FetchType.BRANCH)
                               .commitId(ParameterField.createValueField("commitId"))
                               .branch(ParameterField.createValueField("master"))
                               .paths(ParameterField.createValueField(Collections.singletonList("test")))
                               .build();

    assertInvalidParamsArgsMessage(
        ()
            -> ManifestOutcomeValidator.validateStore(gitStore, ManifestType.K8Manifest, "test", false),
        "commitId: Not allowed for gitFetchType: Branch");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidGitStoreCommitId() {
    GitStore gitStore = GitStore.builder()
                            .connectorRef(ParameterField.createValueField("connector"))
                            .repoName(ParameterField.createValueField("repo"))
                            .gitFetchType(FetchType.COMMIT)
                            .commitId(ParameterField.createValueField("commitId"))
                            .paths(ParameterField.createValueField(Collections.singletonList("test")))
                            .build();

    assertThatCode(() -> ManifestOutcomeValidator.validateStore(gitStore, ManifestType.K8Manifest, "test", false))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidGitStoreCommitIdAllowExpression() {
    BitbucketStore gitStore = BitbucketStore.builder()
                                  .connectorRef(ParameterField.createValueField("connector"))
                                  .repoName(ParameterField.createValueField("repo"))
                                  .gitFetchType(FetchType.COMMIT)
                                  .commitId(ParameterField.createExpressionField(true, "<+expression>", null, true))
                                  .paths(ParameterField.createValueField(Collections.singletonList("test")))
                                  .build();

    assertThatCode(() -> ManifestOutcomeValidator.validateStore(gitStore, ManifestType.K8Manifest, "test", true))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInvalidGitStoreCommitIdDisallowExpression() {
    GitLabStore gitStore = GitLabStore.builder()
                               .connectorRef(ParameterField.createValueField("connector"))
                               .repoName(ParameterField.createValueField("repo"))
                               .gitFetchType(FetchType.COMMIT)
                               .commitId(ParameterField.createExpressionField(true, "<+expression>", null, true))
                               .paths(ParameterField.createValueField(Collections.singletonList("test")))
                               .build();

    assertInvalidParamsArgsMessage(
        ()
            -> ManifestOutcomeValidator.validateStore(gitStore, ManifestType.K8Manifest, "test", false),
        "commitId: Cannot be empty or null for gitFetchType: Commit");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidGitStoreFolderPath() {
    GithubStore gitStore = GithubStore.builder()
                               .connectorRef(ParameterField.createValueField("connector"))
                               .repoName(ParameterField.createValueField("repo"))
                               .gitFetchType(FetchType.COMMIT)
                               .commitId(ParameterField.createValueField("commitId"))
                               .folderPath(ParameterField.createValueField("test"))
                               .build();

    assertThatCode(() -> ManifestOutcomeValidator.validateStore(gitStore, ManifestType.HelmChart, "test", false))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidGitStoreFolderPathAllowExpression() {
    GithubStore gitStore = GithubStore.builder()
                               .connectorRef(ParameterField.createValueField("connector"))
                               .repoName(ParameterField.createValueField("repo"))
                               .gitFetchType(FetchType.COMMIT)
                               .commitId(ParameterField.createValueField("commitId"))
                               .folderPath(ParameterField.createExpressionField(true, "<+test>", null, true))
                               .build();

    assertThatCode(() -> ManifestOutcomeValidator.validateStore(gitStore, ManifestType.HelmChart, "test", true))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInvalidGitStoreFolderPathMissing() {
    GithubStore gitStore = GithubStore.builder()
                               .connectorRef(ParameterField.createValueField("connector"))
                               .repoName(ParameterField.createValueField("repo"))
                               .gitFetchType(FetchType.COMMIT)
                               .commitId(ParameterField.createValueField("commitId"))
                               .build();

    assertInvalidParamsArgsMessage(
        ()
            -> ManifestOutcomeValidator.validateStore(gitStore, ManifestType.HelmChart, "test", false),
        "folderPath: is required for store type 'Github' and manifest type 'HelmChart'");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInvalidGitStoreFolderPathDisallowExpression() {
    GithubStore gitStore = GithubStore.builder()
                               .connectorRef(ParameterField.createValueField("connector"))
                               .repoName(ParameterField.createValueField("repo"))
                               .gitFetchType(FetchType.COMMIT)
                               .commitId(ParameterField.createValueField("commitId"))
                               .folderPath(ParameterField.createExpressionField(true, "<+test>", null, true))
                               .build();

    assertInvalidParamsArgsMessage(
        ()
            -> ManifestOutcomeValidator.validateStore(gitStore, ManifestType.HelmChart, "test", false),
        "folderPath: is required for store type 'Github' and manifest type 'HelmChart'");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidS3Store() {
    S3StoreConfig s3Store = S3StoreConfig.builder()
                                .connectorRef(ParameterField.createValueField("connector"))
                                .bucketName(ParameterField.createValueField("bucket"))
                                .region(ParameterField.createValueField("region"))
                                .build();

    assertThatCode(() -> ManifestOutcomeValidator.validateStore(s3Store, ManifestType.HelmChart, "test", false))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidS3StoreAllowExpression() {
    S3StoreConfig s3Store = S3StoreConfig.builder()
                                .connectorRef(ParameterField.createExpressionField(true, "<+expression>", null, true))
                                .bucketName(ParameterField.createValueField("<+expression>"))
                                .region(ParameterField.createValueField("<+expression>"))
                                .build();

    assertThatCode(() -> ManifestOutcomeValidator.validateStore(s3Store, ManifestType.HelmChart, "test", true))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInvalidS3StoreDisallowExpression() {
    S3StoreConfig s3Store = S3StoreConfig.builder()
                                .connectorRef(ParameterField.createExpressionField(true, "<+expression>", null, true))
                                .bucketName(ParameterField.createExpressionField(true, "<+expression>", null, true))
                                .region(ParameterField.createExpressionField(true, "<+expression>", null, true))
                                .build();

    assertInvalidParamsArgsMessage(
        ()
            -> ManifestOutcomeValidator.validateStore(s3Store, ManifestType.HelmChart, "test", false),
        "Missing or empty connectorRef in S3 store");

    s3Store.setConnectorRef(ParameterField.createValueField("test"));
    assertInvalidParamsArgsMessage(
        ()
            -> ManifestOutcomeValidator.validateStore(s3Store, ManifestType.HelmChart, "test", false),
        "region: Cannot be empty or null for S3 store");

    s3Store.setRegion(ParameterField.createValueField("region"));
    assertInvalidParamsArgsMessage(
        ()
            -> ManifestOutcomeValidator.validateStore(s3Store, ManifestType.HelmChart, "test", false),
        "bucketName: Cannot be empty or null for S3 store");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidGcsStore() {
    GcsStoreConfig gcsStore = GcsStoreConfig.builder()
                                  .connectorRef(ParameterField.createValueField("connector"))
                                  .bucketName(ParameterField.createValueField("bucket"))
                                  .build();

    assertThatCode(() -> ManifestOutcomeValidator.validateStore(gcsStore, ManifestType.HelmChart, "test", false))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidGcsStoreAllowExpression() {
    GcsStoreConfig gcsStore = GcsStoreConfig.builder()
                                  .connectorRef(ParameterField.createExpressionField(true, "<+expression>", null, true))
                                  .bucketName(ParameterField.createValueField("<+expression>"))
                                  .build();

    assertThatCode(() -> ManifestOutcomeValidator.validateStore(gcsStore, ManifestType.HelmChart, "test", true))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInvalidGcsStoreDisallowExpression() {
    GcsStoreConfig gcsStore = GcsStoreConfig.builder()
                                  .connectorRef(ParameterField.createExpressionField(true, "<+expression>", null, true))
                                  .bucketName(ParameterField.createExpressionField(true, "<+expression>", null, true))
                                  .build();

    assertInvalidParamsArgsMessage(
        ()
            -> ManifestOutcomeValidator.validateStore(gcsStore, ManifestType.HelmChart, "test", false),
        "Missing or empty connectorRef in Gcs store");

    gcsStore.setConnectorRef(ParameterField.createValueField("test"));
    assertInvalidParamsArgsMessage(
        ()
            -> ManifestOutcomeValidator.validateStore(gcsStore, ManifestType.HelmChart, "test", false),
        "bucketName: Cannot be empty or null for Gcs store");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testInvalidManifestForInvalidOverridePaths() {
    HelmChartManifestOutcome helmChartManifest =
        HelmChartManifestOutcome.builder()
            .identifier("Test")
            .chartName(ParameterField.createValueField("TodoList"))
            .store(GcsStoreConfig.builder()
                       .connectorRef(ParameterField.createValueField("connector"))
                       .bucketName(ParameterField.createValueField("bucket"))
                       .build())
            .valuesPaths(ParameterField.createValueField(asList(" ", " ")))
            .build();

    assertInvalidParamsArgsMessage(()
                                       -> ManifestOutcomeValidator.validate(helmChartManifest, false),
        "Path for values.yaml files for manifest identifier: Test and manifest type: HelmChart is not valid. Check in the values.yaml setup in the manifest configuration to make sure it's not empty");

    K8sManifestOutcome k8sManifest = K8sManifestOutcome.builder()
                                         .identifier("Test")
                                         .valuesPaths(ParameterField.createValueField(asList("path1", " ")))
                                         .build();

    assertInvalidParamsArgsMessage(()
                                       -> ManifestOutcomeValidator.validate(k8sManifest, false),
        "Path for values.yaml files for manifest identifier: Test and manifest type: K8sManifest is not valid. Check in the values.yaml setup in the manifest configuration to make sure it's not empty");

    OpenshiftManifestOutcome openshiftManifestOutcome =
        OpenshiftManifestOutcome.builder()
            .identifier("Test")
            .paramsPaths(ParameterField.createValueField(asList(" ", "path1")))
            .build();

    assertInvalidParamsArgsMessage(()
                                       -> ManifestOutcomeValidator.validate(openshiftManifestOutcome, false),
        "Path for params.yaml files for manifest identifier: Test and manifest type: OpenshiftTemplate is not valid. Check in the params.yaml setup in the manifest configuration to make sure it's not empty");

    KustomizeManifestOutcome kustomizeManifestOutcome =
        KustomizeManifestOutcome.builder()
            .identifier("Test")
            .patchesPaths(ParameterField.createValueField(asList("path1", null)))
            .build();

    assertInvalidParamsArgsMessage(()
                                       -> ManifestOutcomeValidator.validate(kustomizeManifestOutcome, false),
        "Path for patches.yaml files for manifest identifier: Test and manifest type: Kustomize is not valid. Check in the params.yaml setup in the manifest configuration to make sure it's not empty");
  }

  private void assertInvalidParamsArgsMessage(ThrowableAssert.ThrowingCallable callable, String msg) {
    try {
      callable.call();
    } catch (Throwable e) {
      assertThat(e).isInstanceOf(InvalidArgumentsException.class);
      InvalidArgumentsException exception = (InvalidArgumentsException) e;
      if (exception.getParams().isEmpty()) {
        assertThat(exception.getMessage()).contains(msg);
      } else {
        assertThat((String) exception.getParams().values().iterator().next()).contains(msg);
      }
      return;
    }

    fail("Expected to throw an exception");
  }
}
