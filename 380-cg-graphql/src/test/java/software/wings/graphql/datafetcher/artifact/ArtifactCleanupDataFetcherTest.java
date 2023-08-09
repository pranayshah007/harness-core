/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.artifact;

import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.workers.background.iterator.ArtifactCleanupHandler;

import software.wings.beans.User;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.BambooArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.AuthRuleGraphQL;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.mutation.artifact.ArtifactCleanUpPayload;
import software.wings.security.PermissionAttribute;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.AuthService;

import com.google.common.collect.ImmutableMap;
import graphql.schema.DataFetchingEnvironment;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactCleanupDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock DataFetcherUtils utils;
  @Mock AuthRuleGraphQL authRuleInstrumentation;
  @Mock private ArtifactCleanupHandler artifactCleanupHandler;
  @Mock private AuthService authService;
  @Mock private ArtifactStreamService artifactStreamService;
  @InjectMocks
  @Spy
  ArtifactCleanupDataFetcher artifactCleanupDataFetcher =
      new ArtifactCleanupDataFetcher(artifactStreamService, artifactCleanupHandler, authService);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void test_mutateAndFetch() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.of("artifactStreamId", "artifactStreamId")).when(dataFetchingEnvironment).getArguments();

    User user = testUtils.createUser(testUtils.createAccount());

    ArtifactStream artifactStream = DockerArtifactStream.builder()
                                        .appId("appId")
                                        .autoPopulate(true)
                                        .imageName("artifactImage")
                                        .uuid("artifactStreamId")
                                        .serviceId("serviceId")
                                        .metadataOnly(true)
                                        .build();

    doReturn("artifactStreamId").when(utils).getAccountId(dataFetchingEnvironment);
    doReturn(artifactStream).when(artifactStreamService).get("artifactStreamId");
    doReturn("accountId").when(artifactCleanupHandler).fetchAccountId(artifactStream);
    doNothing()
        .when(authService)
        .authorize("accountId", artifactStream.getUuid(), artifactStream.getServiceId(), user,
            asList(new PermissionAttribute(PermissionAttribute.ResourceType.SERVICE,
                PermissionAttribute.PermissionType.SERVICE, PermissionAttribute.Action.READ)),
            true);
    doNothing().when(artifactCleanupHandler).handleManually(artifactStream, "accountId");
    final ArtifactCleanUpPayload artifactCleanUpPayload = artifactCleanupDataFetcher.get(dataFetchingEnvironment);

    assertThat(artifactCleanUpPayload.getMessage())
        .isEqualTo("Cleanup successful for Artifact stream with id: " + artifactStream.getUuid());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void test_mutateAndFetch_authError() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.of("artifactStreamId", "artifactStreamId")).when(dataFetchingEnvironment).getArguments();

    User user = testUtils.createUser(testUtils.createAccount());
    UserThreadLocal.set(user);

    ArtifactStream artifactStream = DockerArtifactStream.builder()
                                        .appId("appId")
                                        .autoPopulate(true)
                                        .imageName("artifactImage")
                                        .uuid("artifactStreamId")
                                        .serviceId("serviceId")
                                        .metadataOnly(true)
                                        .build();

    doReturn("accountId").when(utils).getAccountId(dataFetchingEnvironment);
    doReturn(artifactStream).when(artifactStreamService).get("artifactStreamId");
    doReturn("accountId").when(artifactCleanupHandler).fetchAccountId(artifactStream);
    doThrow(new AccessDeniedException("Not authorized", USER))
        .when(authService)
        .authorize("accountId", artifactStream.getAppId(), artifactStream.getServiceId(), user,
            asList(new PermissionAttribute(PermissionAttribute.ResourceType.SERVICE,
                       PermissionAttribute.PermissionType.SERVICE, PermissionAttribute.Action.READ),
                new PermissionAttribute(PermissionAttribute.ResourceType.SERVICE,
                    PermissionAttribute.PermissionType.SERVICE, PermissionAttribute.Action.CREATE),
                new PermissionAttribute(PermissionAttribute.ResourceType.SERVICE,
                    PermissionAttribute.PermissionType.SERVICE, PermissionAttribute.Action.UPDATE)),
            true);
    doNothing().when(artifactCleanupHandler).handleManually(artifactStream, "accountId");

    assertThatThrownBy(() -> artifactCleanupDataFetcher.get(dataFetchingEnvironment))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("User not authorized: Not authorized")
        .hasCauseInstanceOf(AccessDeniedException.class)
        .hasRootCauseMessage("Not authorized");
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void test_mutateAndFetch_artifactStreamNotPresent() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.of("artifactStreamId", "artifactStreamId")).when(dataFetchingEnvironment).getArguments();

    User user = testUtils.createUser(testUtils.createAccount());
    UserThreadLocal.set(user);

    doReturn("accountId").when(utils).getAccountId(dataFetchingEnvironment);
    doReturn(null).when(artifactStreamService).get("artifactStreamId");

    final ArtifactCleanUpPayload artifactCleanUpPayload = artifactCleanupDataFetcher.get(dataFetchingEnvironment);

    assertThat(artifactCleanUpPayload.getMessage()).isEqualTo("Artifact stream not found for the id: artifactStreamId");
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void test_mutateAndFetch_artifactStreamTypeNotSupported() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.of("artifactStreamId", "artifactStreamId")).when(dataFetchingEnvironment).getArguments();

    User user = testUtils.createUser(testUtils.createAccount());
    UserThreadLocal.set(user);

    ArtifactStream artifactStream = BambooArtifactStream.builder()
                                        .appId(APP1_ID_ACCOUNT1)
                                        .autoPopulate(true)
                                        .jobname("JOB_NAME")
                                        .uuid("artifactId")
                                        .serviceId("serviceId")
                                        .metadataOnly(true)
                                        .build();

    doReturn("accountId").when(utils).getAccountId(dataFetchingEnvironment);
    doReturn(artifactStream).when(artifactStreamService).get("artifactStreamId");

    final ArtifactCleanUpPayload artifactCleanUpPayload = artifactCleanupDataFetcher.get(dataFetchingEnvironment);

    assertThat(artifactCleanUpPayload.getMessage())
        .isEqualTo("Clean up not supported for artifact Stream type: " + artifactStream.getArtifactStreamType());
  }
}
