/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.githubpackages;

import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.githubpackages.beans.GithubPackagesInternalConfig;
import io.harness.artifacts.githubpackages.client.GithubPackagesRestClient;
import io.harness.artifacts.githubpackages.client.GithubPackagesRestClientFactory;
import io.harness.artifacts.githubpackages.service.GithubPackagesRegistryServiceImpl;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class GithubPackagesRegistryServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks GithubPackagesRegistryServiceImpl githubPackagesRegistryService;
  @Mock private GithubPackagesRestClientFactory githubPackagesRestClientFactory;
  @Mock private GithubPackagesRestClient githubPackagesRestClient;
  @Mock private Call<List<JsonNode>> call;

  @Before
  public void before() {}

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetBuildsForUser() throws IOException {
    GithubPackagesInternalConfig githubPackagesInternalConfig = GithubPackagesInternalConfig.builder()
                                                                    .githubPackagesUrl("https://github.com/username")
                                                                    .authMechanism("UsernameToken")
                                                                    .username("username")
                                                                    .token("token")
                                                                    .build();

    String packageName = "helloworld";
    String packageType = "container";
    String org = null;
    String versionRegex = "*";
    Integer MAX_NO_OF_TAGS_PER_IMAGE = 10000;

    ObjectMapper mapper = new ObjectMapper();

    File from = new File("960-api-services/src/test/resources/__files/githubpackages/build-details-for-user.json");

    ArrayNode versionsJsonFormat = null;

    try {
      versionsJsonFormat = (ArrayNode) mapper.readTree(from);
    } catch (IOException e) {
      doNothing();
    }

    List<JsonNode> list = new ArrayList<>();
    for (JsonNode node : versionsJsonFormat) {
      list.add(node);
    }

    doReturn(githubPackagesRestClient)
        .when(githubPackagesRestClientFactory)
        .getGithubPackagesRestClient(githubPackagesInternalConfig);

    Call<List<JsonNode>> executeCall = mock(Call.class);

    doReturn(executeCall)
        .when(githubPackagesRestClient)
        .listVersionsForPackages(anyString(), anyString(), anyString(), anyInt(), anyInt());

    doReturn(Response.success(list)).when(executeCall).execute();

    List<BuildDetails> builds = githubPackagesRegistryService.getBuilds(
        githubPackagesInternalConfig, packageName, packageType, null, versionRegex, MAX_NO_OF_TAGS_PER_IMAGE);

    BuildDetails build1 = builds.get(0);

    assertThat(build1.getNumber()).isEqualTo("5");
    assertThat(build1.getArtifactPath()).isEqualTo("ghcr.io/username/helloworld:5");
    assertThat(build1.getBuildUrl()).isEqualTo("https://github.com/username/packages/container/helloworld/39008634");
    assertThat(build1.getBuildDisplayName()).isEqualTo("helloworld: 5");
    assertThat(build1.getBuildFullDisplayName())
        .isEqualTo("sha256:49f75d46899bf47edbf3558890e1557a008a20b78e3d0b22e9d18cf00d27699d");
    assertThat(build1.getUiDisplayName()).isEqualTo("Tag# 5");

    BuildDetails build2 = builds.get(1);

    assertThat(build2.getNumber()).isEqualTo("4");
    assertThat(build2.getArtifactPath()).isEqualTo("ghcr.io/username/helloworld:4");
    assertThat(build2.getBuildUrl()).isEqualTo("https://github.com/username/packages/container/helloworld/39008588");
    assertThat(build2.getBuildDisplayName()).isEqualTo("helloworld: 4");
    assertThat(build2.getBuildFullDisplayName())
        .isEqualTo("sha256:f26fbadb0acab4a21ecb4e337a326907e61fbec36c9a9b52e725669d99ed1261");
    assertThat(build2.getUiDisplayName()).isEqualTo("Tag# 4");

    BuildDetails build3 = builds.get(2);

    assertThat(build3.getNumber()).isEqualTo("3");
    assertThat(build3.getArtifactPath()).isEqualTo("ghcr.io/username/helloworld:3");
    assertThat(build3.getBuildUrl()).isEqualTo("https://github.com/username/packages/container/helloworld/39008548");
    assertThat(build3.getBuildDisplayName()).isEqualTo("helloworld: 3");
    assertThat(build3.getBuildFullDisplayName())
        .isEqualTo("sha256:54fc6c7e4927da8e3a6ae3e2bf3ec97481d860455adab48b8cff5f6916a69652");
    assertThat(build3.getUiDisplayName()).isEqualTo("Tag# 3");

    BuildDetails build4 = builds.get(3);

    assertThat(build4.getNumber()).isEqualTo("2");
    assertThat(build4.getArtifactPath()).isEqualTo("ghcr.io/username/helloworld:2");
    assertThat(build4.getBuildUrl()).isEqualTo("https://github.com/username/packages/container/helloworld/39008500");
    assertThat(build4.getBuildDisplayName()).isEqualTo("helloworld: 2");
    assertThat(build4.getBuildFullDisplayName())
        .isEqualTo("sha256:08cde8fece645d8b60bc13cf85691f0a092238a270c1a95554fc71714cd25237");
    assertThat(build4.getUiDisplayName()).isEqualTo("Tag# 2");

    BuildDetails build5 = builds.get(4);

    assertThat(build5.getNumber()).isEqualTo("1");
    assertThat(build5.getArtifactPath()).isEqualTo("ghcr.io/username/helloworld:1");
    assertThat(build5.getBuildUrl()).isEqualTo("https://github.com/username/packages/container/helloworld/39008399");
    assertThat(build5.getBuildDisplayName()).isEqualTo("helloworld: 1");
    assertThat(build5.getBuildFullDisplayName())
        .isEqualTo("sha256:e987fb89e5455d7a465e50d88f4c1497e8947342acfab6cfd347ec201ed6885f");
    assertThat(build5.getUiDisplayName()).isEqualTo("Tag# 1");
  }
}
