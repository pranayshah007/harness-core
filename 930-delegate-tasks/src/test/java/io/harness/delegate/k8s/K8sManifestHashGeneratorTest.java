/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class K8sManifestHashGeneratorTest extends CategoryTest {
  @Mock K8sManifestHashGenerator k8sManifestHashGenerator;

  public static String DEPLOYMENT_DIRECT_APPLY_YAML = "apiVersion: apps/v1\n"
      + "kind: Deployment\n"
      + "metadata:\n"
      + "  labels:\n"
      + "    app: nginx\n"
      + "  name: deployment\n"
      + "  namespace: default\n"
      + "spec:\n"
      + "  replicas: 3\n"
      + "  selector:\n"
      + "    matchLabels:\n"
      + "      app: nginx\n"
      + "  template:\n"
      + "    metadata:\n"
      + "      labels:\n"
      + "        app: nginx\n"
      + "    spec:\n"
      + "      containers:\n"
      + "      - image: nginx:1.7.9\n"
      + "        name: nginx\n"
      + "        ports:\n"
      + "        - containerPort: 80";

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGeneratedHash() throws Exception {
    assertThat(k8sManifestHashGenerator.generatedHash(DEPLOYMENT_DIRECT_APPLY_YAML))
        .isEqualTo("ab9ef5a0ea6784e8411df3f4298c90659e670a78");
  }
}
