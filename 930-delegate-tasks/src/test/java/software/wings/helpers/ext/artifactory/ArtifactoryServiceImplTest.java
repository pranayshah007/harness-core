/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.artifactory;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.StatusLine;
import org.assertj.core.api.Assertions;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryResponse;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ArtifactoryServiceImplTest {
  @InjectMocks private ArtifactoryServiceImpl service;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleNullListResultWhenGetHelmChartNames() throws IOException {
    Artifactory artifactory = mock(Artifactory.class);
    ArtifactoryResponse artifactoryResponse = mock(ArtifactoryResponse.class);
    StatusLine statusLine = mock(StatusLine.class);

    Map<String, List> response = new HashMap<>();
    response.put(ArtifactoryServiceImpl.RESULTS, null);

    when(artifactory.restCall(any())).thenReturn(artifactoryResponse);
    when(artifactoryResponse.getStatusLine()).thenReturn(statusLine);
    when(artifactoryResponse.parseBody(Map.class)).thenReturn(response);

    int maxVersions = 0;
    final List<String> result = service.getHelmChartNames(
        artifactory, "aclQuery", "repositoryName", "chartName", maxVersions, "version", false);
    Assertions.assertThat(result).isEmpty();
  }
}
