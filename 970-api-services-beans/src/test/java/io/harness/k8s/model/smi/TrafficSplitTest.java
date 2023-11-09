/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model.smi;

import static io.harness.rule.OwnerRule.BUHA;

import static org.junit.Assert.assertEquals;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.kubernetes.client.util.Yaml;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TrafficSplitTest extends CategoryTest {
  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCreatingTrafficSplit() throws IOException {
    TrafficSplit trafficSplit =
        TrafficSplit.builder()
            .apiVersion("split.smi-spec.io/v1alpha1")
            .metadata(Metadata.builder().name("traffic-split-test").build())
            .spec(TrafficSplitSpec.builder()
                      .service("root-svc")
                      .backends(List.of(Backend.builder().service("test-svc").weight(95).build(),
                          Backend.builder().service("test-svc-canary").weight(5).build()))
                      .build())
            .build();
    String path = "/smi/TrafficSplitTest.yaml";

    URL url = this.getClass().getResource(path);
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    String printedResource = Yaml.dump(trafficSplit);

    assertEquals(printedResource, fileContents);
  }
}
