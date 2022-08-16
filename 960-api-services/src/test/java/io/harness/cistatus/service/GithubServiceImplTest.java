/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cistatus.service;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.gitpolling.github.GitHubPollingWebhookEventDelivery;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.gitpolling.github.GitPollingWebhookEventMetadata;
import io.harness.gitpolling.github.GithubWebhookRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class GithubServiceImplTest {
  GithubRestClient githubRestClient;

  GithubServiceImpl githubService;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  private final String GITHUB_WEBHOOK_URL = "https://api.github.com/";

  private final String TOKEN = "t3stT0keN";

  private final String REPO_NAME = "testRepo";

  private final String REPO_OWNER = "owner";

  @Test
  @Category(UnitTests.class)
  public void testGetWebhookRecentDeliveryEvents() throws IOException {
    List<GitPollingWebhookEventMetadata> webhookEventsMetadataResponse = Arrays.asList(
        GitPollingWebhookEventMetadata.builder().id("123").status("Invalid HTTP Response: 400").statusCode(400).build(),
        GitPollingWebhookEventMetadata.builder().id("124").status("OK").statusCode(200).build(),
        GitPollingWebhookEventMetadata.builder().id("125").status("Invalid HTTP Response: 308").statusCode(308).build(),
        GitPollingWebhookEventMetadata.builder().id("126").status("ACCEPTED").statusCode(202).build());

    Call<List<GitPollingWebhookEventMetadata>> call = mock(Call.class);
    githubService = spy(GithubServiceImpl.class);
    githubRestClient = mock(GithubRestClient.class);
    Response successWebhookRecentEvents = Response.success(webhookEventsMetadataResponse);
    when(call.execute()).thenReturn(successWebhookRecentEvents);
    when(githubRestClient.getWebhookRecentDeliveryEventsIds(any(), any(), any(), any())).thenReturn(call);
    Mockito.doReturn(githubRestClient).when(githubService).getGithubClient(any());

    GitHubPollingWebhookEventDelivery delivery1 =
        GitHubPollingWebhookEventDelivery.builder()
            .id("123")
            .statusCode(400)
            .request(GithubWebhookRequest.builder().payload(getPayload()).headers(createHeaders()).build())
            .build();

    GitHubPollingWebhookEventDelivery delivery2 =
        GitHubPollingWebhookEventDelivery.builder()
            .id("123")
            .statusCode(308)
            .request(GithubWebhookRequest.builder().payload(getPayload()).headers(createHeaders()).build())
            .build();

    Call<GitHubPollingWebhookEventDelivery> deliveryCall1 = mock(Call.class);
    Call<GitHubPollingWebhookEventDelivery> deliveryCall2 = mock(Call.class);

    Response<GitHubPollingWebhookEventDelivery> fullWebhookResponse = Response.success(delivery1);
    Response<GitHubPollingWebhookEventDelivery> fullWebhookResponse2 = Response.success(delivery2);

    when(githubRestClient.getWebhookDeliveryId(any(), any(), any(), any(), eq("123"))).thenReturn(deliveryCall1);
    when(githubRestClient.getWebhookDeliveryId(any(), any(), any(), any(), eq("125"))).thenReturn(deliveryCall2);
    when(deliveryCall1.execute()).thenReturn(fullWebhookResponse);
    when(deliveryCall2.execute()).thenReturn(fullWebhookResponse2);
    List<GitPollingWebhookData> result =
        githubService.getWebhookRecentDeliveryEvents(GITHUB_WEBHOOK_URL, TOKEN, REPO_OWNER, REPO_NAME, "1234");
    assertEquals(2, result.size());
    result.stream().map(GitPollingWebhookData::getPayload).forEach(Assert::assertNotNull);
    result.stream().map(GitPollingWebhookData::getHeaders).forEach(Assert::assertNotNull);
  }

  @Test
  @Category(UnitTests.class)
  public void testGetWebhookRecentDeliverEventsFailure() throws IOException {
    List<GitPollingWebhookEventMetadata> webhookEventsMetadataResponse = Arrays.asList(
        GitPollingWebhookEventMetadata.builder().id("123").status("Invalid HTTP Response: 400").statusCode(400).build(),
        GitPollingWebhookEventMetadata.builder().id("124").status("OK").statusCode(200).build(),
        GitPollingWebhookEventMetadata.builder().id("125").status("Invalid HTTP Response: 308").statusCode(308).build(),
        GitPollingWebhookEventMetadata.builder().id("126").status("ACCEPTED").statusCode(202).build());

    Call<List<GitPollingWebhookEventMetadata>> call = mock(Call.class);
    githubService = spy(GithubServiceImpl.class);
    githubRestClient = mock(GithubRestClient.class);
    Response successWebhookRecentEvents = Response.success(webhookEventsMetadataResponse);
    when(call.execute()).thenReturn(successWebhookRecentEvents);
    when(githubRestClient.getWebhookRecentDeliveryEventsIds(any(), any(), any(), any())).thenReturn(call);
    Mockito.doReturn(githubRestClient).when(githubService).getGithubClient(any());

    GitHubPollingWebhookEventDelivery delivery2 =
        GitHubPollingWebhookEventDelivery.builder()
            .id("123")
            .statusCode(308)
            .request(GithubWebhookRequest.builder().payload(getPayload()).headers(createHeaders()).build())
            .build();

    Call<GitHubPollingWebhookEventDelivery> deliveryCall1 = mock(Call.class);
    Call<GitHubPollingWebhookEventDelivery> deliveryCall2 = mock(Call.class);

    Response<GitHubPollingWebhookEventDelivery> fullWebhookResponse =
        Response.error(ResponseBody.create(MediaType.parse("application/json"), "Internal Server error"),
            new okhttp3.Response.Builder()
                .message("message")
                .code(500)
                .protocol(Protocol.HTTP_1_1)
                .request(new Request.Builder().url("http://localhost/").build())
                .build());
    Response<GitHubPollingWebhookEventDelivery> fullWebhookResponse2 = Response.success(delivery2);

    when(githubRestClient.getWebhookDeliveryId(any(), any(), any(), any(), eq("123"))).thenReturn(deliveryCall1);
    when(githubRestClient.getWebhookDeliveryId(any(), any(), any(), any(), eq("125"))).thenReturn(deliveryCall2);
    when(deliveryCall1.execute()).thenReturn(fullWebhookResponse);
    when(deliveryCall2.execute()).thenReturn(fullWebhookResponse2);
    List<GitPollingWebhookData> result =
        githubService.getWebhookRecentDeliveryEvents(GITHUB_WEBHOOK_URL, TOKEN, REPO_OWNER, REPO_NAME, "1234");
    assertEquals(1, result.size());
    result.stream().map(GitPollingWebhookData::getPayload).forEach(Assert::assertNotNull);
    result.stream().map(GitPollingWebhookData::getHeaders).forEach(Assert::assertNotNull);
  }

  @Test
  @Category(UnitTests.class)
  public void testGetWebhookRecentDeliveryMetadataFailure() throws IOException {
    Call<List<GitPollingWebhookEventMetadata>> call = mock(Call.class);
    githubService = spy(GithubServiceImpl.class);
    githubRestClient = mock(GithubRestClient.class);

    Response errorResp = Response.error(ResponseBody.create(MediaType.parse("application/json"), "Not found"),
        new okhttp3.Response.Builder()
            .message("message")
            .code(400)
            .protocol(Protocol.HTTP_1_1)
            .request(new Request.Builder().url("http://localhost/").build())
            .build());
    when(call.execute()).thenReturn(errorResp);
    when(githubRestClient.getWebhookRecentDeliveryEventsIds(any(), any(), any(), any())).thenReturn(call);
    Mockito.doReturn(githubRestClient).when(githubService).getGithubClient(any());

    List<GitPollingWebhookData> result =
        githubService.getWebhookRecentDeliveryEvents(GITHUB_WEBHOOK_URL, TOKEN, REPO_OWNER, REPO_NAME, "1234");
    assertEquals(0, result.size());
  }

  public static Object createHeaders() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readValue("{\n"
            + "            \"Accept\": \"*/*\",\n"
            + "            \"User-Agent\": \"GitHub-Hookshot/9398d35\",\n"
            + "            \"X-GitHub-Delivery\": \"b4f1c9b0-11de-11ed-8227-8d270a85257e\",\n"
            + "            \"X-GitHub-Event\": \"issue_comment\",\n"
            + "            \"X-GitHub-Hook-ID\": \"371605658\",\n"
            + "            \"X-GitHub-Hook-Installation-Target-ID\": \"509526209\",\n"
            + "            \"X-GitHub-Hook-Installation-Target-Type\": \"repository\",\n"
            + "            \"content-type\": \"application/json\"\n"
            + "        }",
        JsonNode.class);
  }

  public static String getPayload() throws JsonProcessingException {
    return " \"action\": \"edited\",\n"
        + "            \"changes\": {\n"
        + "                \"body\": {\n"
        + "                    \"from\": \"test\"\n"
        + "                }\n"
        + "            },\n"
        + "            \"issue\": {\n"
        + "                \"url\": \"https://api.github.com/repos/wings-software/sridhartest/issues/1\",\n"
        + "                \"repository_url\": \"https://api.github.com/repos/wings-software/sridhartest\",\n"
        + "                \"labels_url\": \"https://api.github.com/repos/wings-software/sridhartest/issues/1/labels{/name}\",\n"
        + "                \"comments_url\": \"https://api.github.com/repos/wings-software/sridhartest/issues/1/comments\",\n"
        + "                \"events_url\": \"https://api.github.com/repos/wings-software/sridhartest/issues/1/events\",\n"
        + "                \"html_url\": \"https://github.com/wings-software/sridhartest/pull/1\",\n"
        + "                \"id\": 1291653046,\n"
        + "                \"node_id\": \"PR_kwDOHl7Awc46uLF5\",\n"
        + "                \"number\": 1,\n"
        + "                \"title\": \"New PR\",\n"
        + "                \"user\": {\n"
        + "                    \"login\": \"sribalij\",\n"
        + "                    \"id\": 106265061,\n"
        + "                    \"node_id\": \"U_kgDOBlV55Q\",\n"
        + "                    \"avatar_url\": \"https://avatars.githubusercontent.com/u/106265061?v=4\",\n"
        + "                    \"gravatar_id\": \"\",\n"
        + "                    \"url\": \"https://api.github.com/users/sribalij\",\n"
        + "                    \"html_url\": \"https://github.com/sribalij\",\n"
        + "                    \"followers_url\": \"https://api.github.com/users/sribalij/followers\",\n"
        + "                    \"following_url\": \"https://api.github.com/users/sribalij/following{/other_user}\",\n"
        + "                    \"gists_url\": \"https://api.github.com/users/sribalij/gists{/gist_id}\",\n"
        + "                    \"starred_url\": \"https://api.github.com/users/sribalij/starred{/owner}{/repo}\",\n"
        + "                    \"subscriptions_url\": \"https://api.github.com/users/sribalij/subscriptions\",\n"
        + "                    \"organizations_url\": \"https://api.github.com/users/sribalij/orgs\",\n"
        + "                    \"repos_url\": \"https://api.github.com/users/sribalij/repos\",\n"
        + "                    \"events_url\": \"https://api.github.com/users/sribalij/events{/privacy}\",\n"
        + "                    \"received_events_url\": \"https://api.github.com/users/sribalij/received_events\",\n"
        + "                    \"type\": \"User\",\n"
        + "                    \"site_admin\": false\n"
        + "                },\n"
        + "                \"labels\": [],\n"
        + "                \"state\": \"open\",\n"
        + "                \"locked\": false,\n"
        + "                \"assignee\": null,\n"
        + "                \"assignees\": [],\n"
        + "                \"milestone\": null,\n"
        + "                \"comments\": 1,\n"
        + "                \"created_at\": \"2022-07-01T17:22:25Z\",\n"
        + "                \"updated_at\": \"2022-08-01T21:12:53Z\",\n"
        + "                \"closed_at\": null,\n"
        + "                \"author_association\": \"COLLABORATOR\",\n"
        + "                \"active_lock_reason\": null,\n"
        + "                \"draft\": false,\n"
        + "                \"pull_request\": {\n"
        + "                    \"url\": \"https://api.github.com/repos/wings-software/sridhartest/pulls/1\",\n"
        + "                    \"html_url\": \"https://github.com/wings-software/sridhartest/pull/1\",\n"
        + "                    \"diff_url\": \"https://github.com/wings-software/sridhartest/pull/1.diff\",\n"
        + "                    \"patch_url\": \"https://github.com/wings-software/sridhartest/pull/1.patch\",\n"
        + "                    \"merged_at\": null\n"
        + "                },\n"
        + "                \"body\": null,\n"
        + "                \"reactions\": {\n"
        + "                    \"url\": \"https://api.github.com/repos/wings-software/sridhartest/issues/1/reactions\",\n"
        + "                    \"total_count\": 0,\n"
        + "                    \"+1\": 0,\n"
        + "                    \"-1\": 0,\n"
        + "                    \"laugh\": 0,\n"
        + "                    \"hooray\": 0,\n"
        + "                    \"confused\": 0,\n"
        + "                    \"heart\": 0,\n"
        + "                    \"rocket\": 0,\n"
        + "                    \"eyes\": 0\n"
        + "                },\n"
        + "                \"timeline_url\": \"https://api.github.com/repos/wings-software/sridhartest/issues/1/timeline\",\n"
        + "                \"performed_via_github_app\": null,\n"
        + "                \"state_reason\": null\n"
        + "            },\n"
        + "            \"comment\": {\n"
        + "                \"url\": \"https://api.github.com/repos/wings-software/sridhartest/issues/comments/1201639547\",\n"
        + "                \"html_url\": \"https://github.com/wings-software/sridhartest/pull/1#issuecomment-1201639547\",\n"
        + "                \"issue_url\": \"https://api.github.com/repos/wings-software/sridhartest/issues/1\",\n"
        + "                \"id\": 1201639547,\n"
        + "                \"node_id\": \"IC_kwDOHl7Awc5Hn5B7\",\n"
        + "                \"user\": {\n"
        + "                    \"login\": \"sribalij\",\n"
        + "                    \"id\": 106265061,\n"
        + "                    \"node_id\": \"U_kgDOBlV55Q\",\n"
        + "                    \"avatar_url\": \"https://avatars.githubusercontent.com/u/106265061?v=4\",\n"
        + "                    \"gravatar_id\": \"\",\n"
        + "                    \"url\": \"https://api.github.com/users/sribalij\",\n"
        + "                    \"html_url\": \"https://github.com/sribalij\",\n"
        + "                    \"followers_url\": \"https://api.github.com/users/sribalij/followers\",\n"
        + "                    \"following_url\": \"https://api.github.com/users/sribalij/following{/other_user}\",\n"
        + "                    \"gists_url\": \"https://api.github.com/users/sribalij/gists{/gist_id}\",\n"
        + "                    \"starred_url\": \"https://api.github.com/users/sribalij/starred{/owner}{/repo}\",\n"
        + "                    \"subscriptions_url\": \"https://api.github.com/users/sribalij/subscriptions\",\n"
        + "                    \"organizations_url\": \"https://api.github.com/users/sribalij/orgs\",\n"
        + "                    \"repos_url\": \"https://api.github.com/users/sribalij/repos\",\n"
        + "                    \"events_url\": \"https://api.github.com/users/sribalij/events{/privacy}\",\n"
        + "                    \"received_events_url\": \"https://api.github.com/users/sribalij/received_events\",\n"
        + "                    \"type\": \"User\",\n"
        + "                    \"site_admin\": false\n"
        + "                },\n"
        + "                \"created_at\": \"2022-08-01T19:45:19Z\",\n"
        + "                \"updated_at\": \"2022-08-01T21:12:53Z\",\n"
        + "                \"author_association\": \"COLLABORATOR\",\n"
        + "                \"body\": \"test123\",\n"
        + "                \"reactions\": {\n"
        + "                    \"url\": \"https://api.github.com/repos/wings-software/sridhartest/issues/comments/1201639547/reactions\",\n"
        + "                    \"total_count\": 0,\n"
        + "                    \"+1\": 0,\n"
        + "                    \"-1\": 0,\n"
        + "                    \"laugh\": 0,\n"
        + "                    \"hooray\": 0,\n"
        + "                    \"confused\": 0,\n"
        + "                    \"heart\": 0,\n"
        + "                    \"rocket\": 0,\n"
        + "                    \"eyes\": 0\n"
        + "                },\n"
        + "                \"performed_via_github_app\": null\n"
        + "            },\n"
        + "            \"repository\": {\n"
        + "                \"id\": 509526209,\n"
        + "                \"node_id\": \"R_kgDOHl7AwQ\",\n"
        + "                \"name\": \"sridhartest\",\n"
        + "                \"full_name\": \"wings-software/sridhartest\",\n"
        + "                \"private\": true,\n"
        + "                \"owner\": {\n"
        + "                    \"login\": \"wings-software\",\n"
        + "                    \"id\": 18273000,\n"
        + "                    \"node_id\": \"MDEyOk9yZ2FuaXphdGlvbjE4MjczMDAw\",\n"
        + "                    \"avatar_url\": \"https://avatars.githubusercontent.com/u/18273000?v=4\",\n"
        + "                    \"gravatar_id\": \"\",\n"
        + "                    \"url\": \"https://api.github.com/users/wings-software\",\n"
        + "                    \"html_url\": \"https://github.com/wings-software\",\n"
        + "                    \"followers_url\": \"https://api.github.com/users/wings-software/followers\",\n"
        + "                    \"following_url\": \"https://api.github.com/users/wings-software/following{/other_user}\",\n"
        + "                    \"gists_url\": \"https://api.github.com/users/wings-software/gists{/gist_id}\",\n"
        + "                    \"starred_url\": \"https://api.github.com/users/wings-software/starred{/owner}{/repo}\",\n"
        + "                    \"subscriptions_url\": \"https://api.github.com/users/wings-software/subscriptions\",\n"
        + "                    \"organizations_url\": \"https://api.github.com/users/wings-software/orgs\",\n"
        + "                    \"repos_url\": \"https://api.github.com/users/wings-software/repos\",\n"
        + "                    \"events_url\": \"https://api.github.com/users/wings-software/events{/privacy}\",\n"
        + "                    \"received_events_url\": \"https://api.github.com/users/wings-software/received_events\",\n"
        + "                    \"type\": \"Organization\",\n"
        + "                    \"site_admin\": false\n"
        + "                },\n"
        + "                \"html_url\": \"https://github.com/wings-software/sridhartest\",\n"
        + "                \"description\": null,\n"
        + "                \"fork\": false,\n"
        + "                \"url\": \"https://api.github.com/repos/wings-software/sridhartest\",\n"
        + "                \"forks_url\": \"https://api.github.com/repos/wings-software/sridhartest/forks\",\n"
        + "                \"keys_url\": \"https://api.github.com/repos/wings-software/sridhartest/keys{/key_id}\",\n"
        + "                \"collaborators_url\": \"https://api.github.com/repos/wings-software/sridhartest/collaborators{/collaborator}\",\n"
        + "                \"teams_url\": \"https://api.github.com/repos/wings-software/sridhartest/teams\",\n"
        + "                \"hooks_url\": \"https://api.github.com/repos/wings-software/sridhartest/hooks\",\n"
        + "                \"issue_events_url\": \"https://api.github.com/repos/wings-software/sridhartest/issues/events{/number}\",\n"
        + "                \"events_url\": \"https://api.github.com/repos/wings-software/sridhartest/events\",\n"
        + "                \"assignees_url\": \"https://api.github.com/repos/wings-software/sridhartest/assignees{/user}\",\n"
        + "                \"branches_url\": \"https://api.github.com/repos/wings-software/sridhartest/branches{/branch}\",\n"
        + "                \"tags_url\": \"https://api.github.com/repos/wings-software/sridhartest/tags\",\n"
        + "                \"blobs_url\": \"https://api.github.com/repos/wings-software/sridhartest/git/blobs{/sha}\",\n"
        + "                \"git_tags_url\": \"https://api.github.com/repos/wings-software/sridhartest/git/tags{/sha}\",\n"
        + "                \"git_refs_url\": \"https://api.github.com/repos/wings-software/sridhartest/git/refs{/sha}\",\n"
        + "                \"trees_url\": \"https://api.github.com/repos/wings-software/sridhartest/git/trees{/sha}\",\n"
        + "                \"statuses_url\": \"https://api.github.com/repos/wings-software/sridhartest/statuses/{sha}\",\n"
        + "                \"languages_url\": \"https://api.github.com/repos/wings-software/sridhartest/languages\",\n"
        + "                \"stargazers_url\": \"https://api.github.com/repos/wings-software/sridhartest/stargazers\",\n"
        + "                \"contributors_url\": \"https://api.github.com/repos/wings-software/sridhartest/contributors\",\n"
        + "                \"subscribers_url\": \"https://api.github.com/repos/wings-software/sridhartest/subscribers\",\n"
        + "                \"subscription_url\": \"https://api.github.com/repos/wings-software/sridhartest/subscription\",\n"
        + "                \"commits_url\": \"https://api.github.com/repos/wings-software/sridhartest/commits{/sha}\",\n"
        + "                \"git_commits_url\": \"https://api.github.com/repos/wings-software/sridhartest/git/commits{/sha}\",\n"
        + "                \"comments_url\": \"https://api.github.com/repos/wings-software/sridhartest/comments{/number}\",\n"
        + "                \"issue_comment_url\": \"https://api.github.com/repos/wings-software/sridhartest/issues/comments{/number}\",\n"
        + "                \"contents_url\": \"https://api.github.com/repos/wings-software/sridhartest/contents/{+path}\",\n"
        + "                \"compare_url\": \"https://api.github.com/repos/wings-software/sridhartest/compare/{base}...{head}\",\n"
        + "                \"merges_url\": \"https://api.github.com/repos/wings-software/sridhartest/merges\",\n"
        + "                \"archive_url\": \"https://api.github.com/repos/wings-software/sridhartest/{archive_format}{/ref}\",\n"
        + "                \"downloads_url\": \"https://api.github.com/repos/wings-software/sridhartest/downloads\",\n"
        + "                \"issues_url\": \"https://api.github.com/repos/wings-software/sridhartest/issues{/number}\",\n"
        + "                \"pulls_url\": \"https://api.github.com/repos/wings-software/sridhartest/pulls{/number}\",\n"
        + "                \"milestones_url\": \"https://api.github.com/repos/wings-software/sridhartest/milestones{/number}\",\n"
        + "                \"notifications_url\": \"https://api.github.com/repos/wings-software/sridhartest/notifications{?since,all,participating}\",\n"
        + "                \"labels_url\": \"https://api.github.com/repos/wings-software/sridhartest/labels{/name}\",\n"
        + "                \"releases_url\": \"https://api.github.com/repos/wings-software/sridhartest/releases{/id}\",\n"
        + "                \"deployments_url\": \"https://api.github.com/repos/wings-software/sridhartest/deployments\",\n"
        + "                \"created_at\": \"2022-07-01T16:45:31Z\",\n"
        + "                \"updated_at\": \"2022-07-01T16:45:31Z\",\n"
        + "                \"pushed_at\": \"2022-07-06T18:10:47Z\",\n"
        + "                \"git_url\": \"git://github.com/wings-software/sridhartest.git\",\n"
        + "                \"ssh_url\": \"git@github.com:wings-software/sridhartest.git\",\n"
        + "                \"clone_url\": \"https://github.com/wings-software/sridhartest.git\",\n"
        + "                \"svn_url\": \"https://github.com/wings-software/sridhartest\",\n"
        + "                \"homepage\": null,\n"
        + "                \"size\": 7,\n"
        + "                \"stargazers_count\": 0,\n"
        + "                \"watchers_count\": 0,\n"
        + "                \"language\": null,\n"
        + "                \"has_issues\": true,\n"
        + "                \"has_projects\": true,\n"
        + "                \"has_downloads\": true,\n"
        + "                \"has_wiki\": true,\n"
        + "                \"has_pages\": false,\n"
        + "                \"forks_count\": 0,\n"
        + "                \"mirror_url\": null,\n"
        + "                \"archived\": false,\n"
        + "                \"disabled\": false,\n"
        + "                \"open_issues_count\": 1,\n"
        + "                \"license\": null,\n"
        + "                \"allow_forking\": false,\n"
        + "                \"is_template\": false,\n"
        + "                \"web_commit_signoff_required\": false,\n"
        + "                \"topics\": [],\n"
        + "                \"visibility\": \"private\",\n"
        + "                \"forks\": 0,\n"
        + "                \"open_issues\": 1,\n"
        + "                \"watchers\": 0,\n"
        + "                \"default_branch\": \"main\"\n"
        + "            },\n"
        + "            \"organization\": {\n"
        + "                \"login\": \"wings-software\",\n"
        + "                \"id\": 18273000,\n"
        + "                \"node_id\": \"MDEyOk9yZ2FuaXphdGlvbjE4MjczMDAw\",\n"
        + "                \"url\": \"https://api.github.com/orgs/wings-software\",\n"
        + "                \"repos_url\": \"https://api.github.com/orgs/wings-software/repos\",\n"
        + "                \"events_url\": \"https://api.github.com/orgs/wings-software/events\",\n"
        + "                \"hooks_url\": \"https://api.github.com/orgs/wings-software/hooks\",\n"
        + "                \"issues_url\": \"https://api.github.com/orgs/wings-software/issues\",\n"
        + "                \"members_url\": \"https://api.github.com/orgs/wings-software/members{/member}\",\n"
        + "                \"public_members_url\": \"https://api.github.com/orgs/wings-software/public_members{/member}\",\n"
        + "                \"avatar_url\": \"https://avatars.githubusercontent.com/u/18273000?v=4\",\n"
        + "                \"description\": \"\"\n"
        + "            },\n"
        + "            \"enterprise\": {\n"
        + "                \"id\": 6657,\n"
        + "                \"slug\": \"harness\",\n"
        + "                \"name\": \"Harness\",\n"
        + "                \"node_id\": \"MDEwOkVudGVycHJpc2U2NjU3\",\n"
        + "                \"avatar_url\": \"https://avatars.githubusercontent.com/b/6657?v=4\",\n"
        + "                \"description\": \"\",\n"
        + "                \"website_url\": \"https://harness.io\",\n"
        + "                \"html_url\": \"https://github.com/enterprises/harness\",\n"
        + "                \"created_at\": \"2021-04-13T17:56:36Z\",\n"
        + "                \"updated_at\": \"2022-06-21T23:25:27Z\"\n"
        + "            },\n"
        + "            \"sender\": {\n"
        + "                \"login\": \"sribalij\",\n"
        + "                \"id\": 106265061,\n"
        + "                \"node_id\": \"U_kgDOBlV55Q\",\n"
        + "                \"avatar_url\": \"https://avatars.githubusercontent.com/u/106265061?v=4\",\n"
        + "                \"gravatar_id\": \"\",\n"
        + "                \"url\": \"https://api.github.com/users/sribalij\",\n"
        + "                \"html_url\": \"https://github.com/sribalij\",\n"
        + "                \"followers_url\": \"https://api.github.com/users/sribalij/followers\",\n"
        + "                \"following_url\": \"https://api.github.com/users/sribalij/following{/other_user}\",\n"
        + "                \"gists_url\": \"https://api.github.com/users/sribalij/gists{/gist_id}\",\n"
        + "                \"starred_url\": \"https://api.github.com/users/sribalij/starred{/owner}{/repo}\",\n"
        + "                \"subscriptions_url\": \"https://api.github.com/users/sribalij/subscriptions\",\n"
        + "                \"organizations_url\": \"https://api.github.com/users/sribalij/orgs\",\n"
        + "                \"repos_url\": \"https://api.github.com/users/sribalij/repos\",\n"
        + "                \"events_url\": \"https://api.github.com/users/sribalij/events{/privacy}\",\n"
        + "                \"received_events_url\": \"https://api.github.com/users/sribalij/received_events\",\n"
        + "                \"type\": \"User\",\n"
        + "                \"site_admin\": false\n"
        + "            }";
  }
}
