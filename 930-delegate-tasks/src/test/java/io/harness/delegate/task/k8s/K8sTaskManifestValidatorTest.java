/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.k8s.exception.KubernetesExceptionExplanation.FILE_PATH_NOT_PART_OF_MANIFEST_FORMAT;
import static io.harness.k8s.exception.KubernetesExceptionHints.MAYBE_DID_YOU_MEAN_FILE_FORMAT;
import static io.harness.k8s.exception.KubernetesExceptionMessages.UNABLE_TO_FIND_FILE_IN_MANIFEST_DIRECTORY_FORMAT;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.filesystem.FileIo;
import io.harness.rule.Owner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class K8sTaskManifestValidatorTest extends CategoryTest {
  private static final String TEMP_MANIFEST_DIR_SUFFIX = "ManifestFilesUtilsTest";
  private static final byte[] MANIFEST_FILE_CONTENT = "test".getBytes(StandardCharsets.UTF_8);

  private Path testManifestDirectory;

  private final K8sTaskManifestValidator k8sTaskManifestValidator = new K8sTaskManifestValidator();

  @Before
  public void setup() {
    testManifestDirectory = createTestManifestDirectory();
  }

  @After
  @SneakyThrows
  public void destroy() {
    FileIo.deleteDirectoryAndItsContentIfExists(testManifestDirectory.toString());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testK8sManifestValid() {
    createK8sManifestTestFiles();

    assertThatCode(()
                       -> k8sTaskManifestValidator.checkFilesPartOfManifest(testManifestDirectory.toString(),
                           asList("deployment.yaml", "files/deployment.yaml"), K8sTaskManifestValidator.IS_YAML_FILE))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testK8sManifestInvalid() {
    createK8sManifestTestFiles();
    assertThatThrownBy(()
                           -> k8sTaskManifestValidator.checkFilesPartOfManifest(testManifestDirectory.toString(),
                               asList("deployment.yaml", "deployment123.yaml"), K8sTaskManifestValidator.IS_YAML_FILE))
        .matches(throwable -> {
          validateMessages(throwable, "deployment.yaml", "deployment123.yaml");
          return true;
        });
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHelmManifestValid() {
    createHelmTestFiles();

    assertThatCode(()
                       -> k8sTaskManifestValidator.checkFilesPartOfManifest(testManifestDirectory.toString(),
                           asList("templates/deployment.yaml", "templates/service.yaml"),
                           K8sTaskManifestValidator.IS_HELM_TEMPLATE_FILE))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHelmManifestInvalid() {
    createHelmTestFiles();
    assertThatThrownBy(()
                           -> k8sTaskManifestValidator.checkFilesPartOfManifest(testManifestDirectory.toString(),
                               asList("templates/deployment123.yaml"), K8sTaskManifestValidator.IS_HELM_TEMPLATE_FILE))
        .matches(throwable -> {
          validateMessages(throwable, "templates/deployment.yaml", "templates/deployment123.yaml");
          return true;
        });
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testKustomizeValid() {
    createKustomizeTestFiles();

    assertThatCode(()
                       -> k8sTaskManifestValidator.checkFilesPartOfManifest(testManifestDirectory.toString(),
                           asList("overlays/prod"), K8sTaskManifestValidator.IS_KUSTOMIZE_OVERLAY_FOLDER))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testKustomizeInvalid() {
    createKustomizeTestFiles();

    assertThatCode(()
                       -> k8sTaskManifestValidator.checkFilesPartOfManifest(testManifestDirectory.toString(),
                           asList("overlays/developer"), K8sTaskManifestValidator.IS_KUSTOMIZE_OVERLAY_FOLDER))
        .matches(throwable -> {
          validateMessages(throwable, "overlays/dev", "overlays/developer");
          return true;
        });
  }

  @SneakyThrows
  private Path createTestManifestDirectory() {
    return Files.createTempDirectory(TEMP_MANIFEST_DIR_SUFFIX);
  }

  @SneakyThrows
  private void createK8sManifestTestFiles() {
    FileIo.writeFile(pathInManifestDirectory("deployment.yaml"), MANIFEST_FILE_CONTENT);
    FileIo.writeFile(pathInManifestDirectory("service.yaml"), MANIFEST_FILE_CONTENT);
    FileIo.writeFile(pathInManifestDirectory("statefulset.yaml"), MANIFEST_FILE_CONTENT);
    FileIo.createDirectoryIfDoesNotExist(pathInManifestDirectory("files"));
    FileIo.writeFile(pathInManifestDirectory("files/random.bin"), MANIFEST_FILE_CONTENT);
    FileIo.writeFile(pathInManifestDirectory("files/deployment.yaml"), MANIFEST_FILE_CONTENT);
  }

  @SneakyThrows
  private void createHelmTestFiles() {
    FileIo.writeFile("values.yaml", MANIFEST_FILE_CONTENT);
    FileIo.writeFile("values2.yaml", MANIFEST_FILE_CONTENT);
    FileIo.createDirectoryIfDoesNotExist(pathInManifestDirectory("templates"));
    FileIo.writeFile(pathInManifestDirectory("templates/deployment.yaml"), MANIFEST_FILE_CONTENT);
    FileIo.writeFile(pathInManifestDirectory("templates/service.yaml"), MANIFEST_FILE_CONTENT);
    FileIo.writeFile(pathInManifestDirectory("templates/statefulset.yaml"), MANIFEST_FILE_CONTENT);
    FileIo.writeFile(pathInManifestDirectory("templates/_helper.tpl"), MANIFEST_FILE_CONTENT);
  }

  @SneakyThrows
  private void createKustomizeTestFiles() {
    FileIo.createDirectoryIfDoesNotExist(pathInManifestDirectory("overlays/prod"));
    FileIo.createDirectoryIfDoesNotExist(pathInManifestDirectory("overlays/dev"));
    FileIo.createDirectoryIfDoesNotExist(pathInManifestDirectory("base"));
    FileIo.createDirectoryIfDoesNotExist(pathInManifestDirectory("patches"));

    FileIo.writeFile(pathInManifestDirectory("overlays/prod/kustomization.yaml"), MANIFEST_FILE_CONTENT);
    FileIo.writeFile(pathInManifestDirectory("overlays/dev/kustomization.yml"), MANIFEST_FILE_CONTENT);
    FileIo.writeFile(pathInManifestDirectory("base/kustomization.yml"), MANIFEST_FILE_CONTENT);
    FileIo.writeFile(pathInManifestDirectory("patches/patch1.yml"), MANIFEST_FILE_CONTENT);
    FileIo.writeFile(pathInManifestDirectory("patches/patch2.yml"), MANIFEST_FILE_CONTENT);
  }

  private Path pathInManifestDirectory(String path) {
    return Paths.get(testManifestDirectory.toString(), path);
  }

  private void collectMessagesFromWingsException(Throwable throwable, Set<String> messages) {
    if (throwable instanceof WingsException) {
      WingsException we = (WingsException) throwable;
      messages.add((String) we.getParams().entrySet().iterator().next().getValue());
    }

    if (throwable.getCause() != null) {
      collectMessagesFromWingsException(throwable.getCause(), messages);
    }
  }

  private void validateMessages(Throwable throwable, String expectedBestMatchFile, String fileInput) {
    Set<String> messages = new HashSet<>();
    collectMessagesFromWingsException(throwable, messages);

    assertThat(messages).contains(format(MAYBE_DID_YOU_MEAN_FILE_FORMAT, expectedBestMatchFile));
    assertThat(messages).contains(format(FILE_PATH_NOT_PART_OF_MANIFEST_FORMAT, fileInput));
    assertThat(messages).contains(format(UNABLE_TO_FIND_FILE_IN_MANIFEST_DIRECTORY_FORMAT, fileInput));
  }
}