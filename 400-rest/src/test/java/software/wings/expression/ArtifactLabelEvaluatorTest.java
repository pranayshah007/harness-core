/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.rule.OwnerRule.HARSH;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.service.intfc.BuildSourceService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class ArtifactLabelEvaluatorTest extends WingsBaseTest {
  private ArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .uuid(ARTIFACT_STREAM_ID)
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName("wingsplugins/todolist")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .build();
  @Mock private BuildSourceService buildSourceService;

  private String key = "foo";
  private String value = "abc";

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldGetLabelsforValidKey() {
    ArtifactLabelEvaluator artifactLabelEvaluator = ArtifactLabelEvaluator.builder()
                                                        .artifactStream(dockerArtifactStream)
                                                        .buildNo("abc")
                                                        .buildSourceService(buildSourceService)
                                                        .build();

    List<Map<String, String>> arrayList = new ArrayList<>();

    Map<String, String> labels = new HashMap<>();
    labels.put(key, value);
    arrayList.add(labels);

    when(buildSourceService.getLabels(any(), any())).thenReturn(arrayList);
    assertEquals(artifactLabelEvaluator.get("foo"), "abc");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldNotGetLabelsforInValidKey() {
    ArtifactLabelEvaluator artifactLabelEvaluator = ArtifactLabelEvaluator.builder()
                                                        .artifactStream(dockerArtifactStream)
                                                        .buildNo("abc")
                                                        .buildSourceService(buildSourceService)
                                                        .build();

    List<Map<String, String>> arrayList = new ArrayList<>();

    Map<String, String> labels = new HashMap<>();
    labels.put(key, value);
    arrayList.add(labels);

    when(buildSourceService.getLabels(any(), any())).thenReturn(arrayList);
    assertEquals(artifactLabelEvaluator.get("foo1"), null);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldThrowExceptionforEmptyKeySet() {
    ArtifactLabelEvaluator artifactLabelEvaluator = ArtifactLabelEvaluator.builder()
                                                        .artifactStream(dockerArtifactStream)
                                                        .buildNo("abc")
                                                        .buildSourceService(buildSourceService)
                                                        .build();

    when(buildSourceService.getLabels(any(), any())).thenReturn(null);
    assertEquals(artifactLabelEvaluator.get("foo1"), null);
  }
}
