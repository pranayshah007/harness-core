/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ci;

import static io.harness.rule.OwnerRule.HARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cistatus.GithubAppTokenCreationResponse;
import io.harness.cistatus.StatusCreationResponse;
import io.harness.cistatus.service.GithubRestClient;
import io.harness.cistatus.service.GithubServiceImpl;
import io.harness.cistatus.service.bitbucket.BitbucketRestClient;
import io.harness.cistatus.service.bitbucket.BitbucketServiceImpl;
import io.harness.cistatus.service.gitlab.GitlabRestClient;
import io.harness.cistatus.service.gitlab.GitlabServiceImpl;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.status.BuildStatusPushResponse;
import io.harness.delegate.beans.ci.status.BuildStatusPushResponse.Status;
import io.harness.delegate.task.ci.CIBuildStatusPushParameters;
import io.harness.delegate.task.ci.CIBuildStatusPushTask;
import io.harness.delegate.task.ci.GitSCMType;
import io.harness.rule.Owner;

import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import retrofit2.Call;
import retrofit2.Response;

public class CIBuildStatusPushTaskTest extends CategoryTest {
  private GithubRestClient githubRestClient;
  private GitlabRestClient gitlabRestClient;
  private BitbucketRestClient bitbucketRestClient;

  private GithubServiceImpl githubServiceImpl;
  private GitlabServiceImpl gitlabServiceImpl;
  private BitbucketServiceImpl bitbucketServiceImpl;

  private final String APP_ID = "APP_ID";
  private final String DESC = "desc";
  private final String STATE = "success";
  private final String BUILD_NUMBER = "buildNumber";
  private final String TITLE = "title";
  private final String REPO = "repo";
  private final String OWNER = "owner";
  private final String USERNAME = "user";
  private final String TOKEN = "token";
  private final String SHA = "e9a0d31c5ac677ec1e06fb3ab69cd1d2cc62a74a";
  private final String IDENTIFIER = "stageIdentifier";
  private final String INSTALL_ID = "123";
  private final String TARGET_URL = "https://app.harness.io";
  private final String KEY = "dummyKey";

  @InjectMocks
  private CIBuildStatusPushTask ciBuildStatusPushTask = new CIBuildStatusPushTask(
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Before
  public void setUp() throws IllegalAccessException {
    githubRestClient = Mockito.mock(GithubRestClient.class);
    gitlabRestClient = Mockito.mock(GitlabRestClient.class);
    bitbucketRestClient = Mockito.mock(BitbucketRestClient.class);
    githubServiceImpl = spy(new GithubServiceImpl());
    gitlabServiceImpl = spy(new GitlabServiceImpl());
    bitbucketServiceImpl = spy(new BitbucketServiceImpl());
    doReturn(githubRestClient).when(githubServiceImpl).getGithubClient(any());
    doReturn(gitlabRestClient).when(gitlabServiceImpl).getGitlabRestClient(any(), any());
    doReturn(bitbucketRestClient).when(bitbucketServiceImpl).getBitbucketClient(any(), any());
    on(ciBuildStatusPushTask).set("githubService", githubServiceImpl);
    on(ciBuildStatusPushTask).set("gitlabService", gitlabServiceImpl);
    on(ciBuildStatusPushTask).set("bitbucketService", bitbucketServiceImpl);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void testDelegatePushStatusForGithub() throws IOException {
    GithubAppTokenCreationResponse githubAppTokenCreationResponse =
        GithubAppTokenCreationResponse.builder().token("token").build();
    Call<GithubAppTokenCreationResponse> githubAppTokenCreationResponseCall = mock(Call.class);
    when(githubAppTokenCreationResponseCall.execute()).thenReturn(Response.success(githubAppTokenCreationResponse));

    when(githubRestClient.createAccessToken(anyString(), anyString())).thenReturn(githubAppTokenCreationResponseCall);

    StatusCreationResponse statusCreationResponse = StatusCreationResponse.builder().build();
    Call<StatusCreationResponse> githubStatusCreationResponseCall = mock(Call.class);
    when(githubStatusCreationResponseCall.execute()).thenReturn(Response.success(statusCreationResponse));

    when(githubRestClient.createStatus(anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(githubStatusCreationResponseCall);

    BuildStatusPushResponse buildStatusPushResponse =
        (BuildStatusPushResponse) ciBuildStatusPushTask.run(CIBuildStatusPushParameters.builder()
                                                                .appId(APP_ID)
                                                                .sha(SHA)
                                                                .key(KEY)
                                                                .identifier(IDENTIFIER)
                                                                .buildNumber(BUILD_NUMBER)
                                                                .installId(INSTALL_ID)
                                                                .gitSCMType(GitSCMType.GITHUB)
                                                                .owner(OWNER)
                                                                .repo(REPO)
                                                                .state(STATE)
                                                                .title(TITLE)
                                                                .target_url(TARGET_URL)
                                                                .desc(DESC)
                                                                .build());

    assertThat(buildStatusPushResponse.getStatus()).isEqualTo(Status.SUCCESS);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void testDelegatePushStatusForBitbucket() throws IOException {
    StatusCreationResponse statusCreationResponse = StatusCreationResponse.builder().build();
    Call<StatusCreationResponse> statusCreationResponseCall = mock(Call.class);
    when(statusCreationResponseCall.execute()).thenReturn(Response.success(statusCreationResponse));

    when(bitbucketRestClient.createStatus(anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(statusCreationResponseCall);

    BuildStatusPushResponse buildStatusPushResponse =
        (BuildStatusPushResponse) ciBuildStatusPushTask.run(CIBuildStatusPushParameters.builder()
                                                                .appId(APP_ID)
                                                                .sha(SHA)
                                                                .key(KEY)
                                                                .identifier(IDENTIFIER)
                                                                .buildNumber(BUILD_NUMBER)
                                                                .installId(INSTALL_ID)
                                                                .owner(OWNER)
                                                                .userName(USERNAME)
                                                                .gitSCMType(GitSCMType.BITBUCKET)
                                                                .token(TOKEN)
                                                                .repo(REPO)
                                                                .state(STATE)
                                                                .title(TITLE)
                                                                .target_url(TARGET_URL)
                                                                .desc(DESC)
                                                                .build());

    assertThat(buildStatusPushResponse.getStatus()).isEqualTo(Status.SUCCESS);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testErrorStatePushStatusForBitbucket() throws IOException {
    StatusCreationResponse statusCreationResponse = StatusCreationResponse.builder().build();
    Call<StatusCreationResponse> statusCreationResponseCall = mock(Call.class);
    when(statusCreationResponseCall.execute())
        .thenReturn(Response.error(ResponseBody.create(MediaType.parse("text/plain"), "MSG"),
            new okhttp3.Response
                .Builder() //
                .code(401)
                .protocol(Protocol.HTTP_1_1)
                .request(new Request.Builder().url("http://localhost/").build())
                .message("err")
                .build()));

    when(bitbucketRestClient.createStatus(anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(statusCreationResponseCall);

    BuildStatusPushResponse buildStatusPushResponse =
        (BuildStatusPushResponse) ciBuildStatusPushTask.run(CIBuildStatusPushParameters.builder()
                                                                .appId(APP_ID)
                                                                .sha(SHA)
                                                                .key(KEY)
                                                                .identifier(IDENTIFIER)
                                                                .buildNumber(BUILD_NUMBER)
                                                                .installId(INSTALL_ID)
                                                                .owner(OWNER)
                                                                .userName(USERNAME)
                                                                .gitSCMType(GitSCMType.BITBUCKET)
                                                                .token(TOKEN)
                                                                .repo(REPO)
                                                                .state(STATE)
                                                                .title(TITLE)
                                                                .target_url(TARGET_URL)
                                                                .desc(DESC)
                                                                .build());

    assertThat(buildStatusPushResponse.getStatus()).isEqualTo(Status.ERROR);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void testDelegatePushStatusForGitlab() throws IOException {
    StatusCreationResponse statusCreationResponse = StatusCreationResponse.builder().build();
    Call<StatusCreationResponse> statusCreationResponseCall = mock(Call.class);
    when(statusCreationResponseCall.execute()).thenReturn(Response.success(statusCreationResponse));

    when(gitlabRestClient.createStatus(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(statusCreationResponseCall);

    BuildStatusPushResponse buildStatusPushResponse =
        (BuildStatusPushResponse) ciBuildStatusPushTask.run(CIBuildStatusPushParameters.builder()
                                                                .appId(APP_ID)
                                                                .sha(SHA)
                                                                .key(KEY)
                                                                .identifier(IDENTIFIER)
                                                                .buildNumber(BUILD_NUMBER)
                                                                .installId(INSTALL_ID)
                                                                .owner(OWNER)
                                                                .userName(USERNAME)
                                                                .gitSCMType(GitSCMType.GITLAB)
                                                                .token(TOKEN)
                                                                .repo(REPO)
                                                                .state(STATE)
                                                                .title(TITLE)
                                                                .target_url(TARGET_URL)
                                                                .desc(DESC)
                                                                .build());

    assertThat(buildStatusPushResponse.getStatus()).isEqualTo(Status.SUCCESS);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testErrorStatePushStatusForGitlab() throws IOException {
    StatusCreationResponse statusCreationResponse = StatusCreationResponse.builder().build();
    Call<StatusCreationResponse> statusCreationResponseCall = mock(Call.class);
    when(statusCreationResponseCall.execute())
        .thenReturn(Response.error(ResponseBody.create(MediaType.parse("text/plain"), "MSG"),
            new okhttp3.Response
                .Builder() //
                .code(401)
                .protocol(Protocol.HTTP_1_1)
                .request(new Request.Builder().url("http://localhost/").build())
                .message("err")
                .build()));

    when(gitlabRestClient.createStatus(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(statusCreationResponseCall);

    BuildStatusPushResponse buildStatusPushResponse =
        (BuildStatusPushResponse) ciBuildStatusPushTask.run(CIBuildStatusPushParameters.builder()
                                                                .appId(APP_ID)
                                                                .sha(SHA)
                                                                .key(KEY)
                                                                .identifier(IDENTIFIER)
                                                                .buildNumber(BUILD_NUMBER)
                                                                .installId(INSTALL_ID)
                                                                .owner(OWNER)
                                                                .userName(USERNAME)
                                                                .gitSCMType(GitSCMType.GITLAB)
                                                                .token(TOKEN)
                                                                .repo(REPO)
                                                                .state(STATE)
                                                                .title(TITLE)
                                                                .target_url(TARGET_URL)
                                                                .desc(DESC)
                                                                .build());

    assertThat(buildStatusPushResponse.getStatus()).isEqualTo(Status.ERROR);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testDelegatePushStatusERRORForGithub() throws IOException {
    Call<GithubAppTokenCreationResponse> githubAppTokenCreationResponseCall = mock(Call.class);

    when(githubAppTokenCreationResponseCall.execute())
        .thenReturn(Response.error(ResponseBody.create(MediaType.parse("text/plain"), "MSG"),
            new okhttp3.Response
                .Builder() //
                .code(401)
                .protocol(Protocol.HTTP_1_1)
                .request(new Request.Builder().url("http://localhost/").build())
                .message("err")
                .build()));

    when(githubRestClient.createAccessToken(anyString(), anyString())).thenReturn(githubAppTokenCreationResponseCall);

    BuildStatusPushResponse buildStatusPushResponse =
        (BuildStatusPushResponse) ciBuildStatusPushTask.run(CIBuildStatusPushParameters.builder()
                                                                .appId(APP_ID)
                                                                .sha(SHA)
                                                                .key(KEY)
                                                                .identifier(IDENTIFIER)
                                                                .buildNumber(BUILD_NUMBER)
                                                                .installId(INSTALL_ID)
                                                                .owner(OWNER)
                                                                .repo(REPO)
                                                                .gitSCMType(GitSCMType.GITHUB)
                                                                .state(STATE)
                                                                .title(TITLE)
                                                                .target_url(TARGET_URL)
                                                                .desc(DESC)
                                                                .build());

    assertThat(buildStatusPushResponse.getStatus()).isEqualTo(Status.ERROR);
  }
}
