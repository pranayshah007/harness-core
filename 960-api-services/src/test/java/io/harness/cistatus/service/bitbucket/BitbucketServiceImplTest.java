/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service.bitbucket;

import static io.harness.rule.OwnerRule.MEENA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cistatus.BitbucketOnPremErrorResponse;
import io.harness.cistatus.BitbucketOnPremMergeResponse;
import io.harness.cistatus.BitbucketSaaSErrorResponse;
import io.harness.cistatus.BitbucketSaaSMergeResponse;
import io.harness.git.model.MergePRResponse;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Arrays;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;

@OwnedBy(HarnessTeam.GITOPS)
@RunWith(MockitoJUnitRunner.class)
public class BitbucketServiceImplTest {
  private static final String CLOUD_URL = "https://bitbucket.org/meenaharness/test-repo.git";
  private static final String ONPREM_URL = "https://bitbucket.dev.harness.io/scm/har/test-repo.git";
  private static final String PAT = "testpat";
  private static final String REF = "testref";
  private static final String ORG = "HAR";
  private static final String REPO_SLUG = "test-repo";
  private static final String SHA = "abc123xyz";
  private static final String TOKEN = "xyz";
  private static final String USERNAME = "testuser";
  private static final String PR_NUMBER = "1";
  private static final String MERGE_ERR_MESSAGE = "This pull request is already closed.";

  BitbucketRestClient bitbucketRestClient;

  private BitbucketServiceImpl bitbucketService;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testMergeSaaSPR() throws IOException {
    bitbucketRestClient = mock(BitbucketRestClient.class);
    bitbucketService = spy(BitbucketServiceImpl.class);
    Mockito.doReturn(bitbucketRestClient).when(bitbucketService).getBitbucketClient(any(), any());
    BitbucketConfig config = BitbucketConfig.builder().bitbucketUrl(CLOUD_URL).personalAccessToken(PAT).build();
    Call<BitbucketSaaSMergeResponse> saasMergeCall = mock(Call.class);

    Response<BitbucketSaaSMergeResponse> saasMergeResponse = Response.success(getSaaSMergeResponse());

    when(bitbucketRestClient.mergeSaaSPR(any(), any(), any(), any(), any())).thenReturn(saasMergeCall);
    when(saasMergeCall.execute()).thenReturn(saasMergeResponse);
    MergePRResponse mergePRResponse =
        bitbucketService.mergePR(config, TOKEN, USERNAME, ORG, REPO_SLUG, PR_NUMBER, true, REF);
    assertThat(mergePRResponse.getSha()).isEqualTo(SHA);
    assertThat(mergePRResponse.isMerged()).isEqualTo(true);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testMergeSaaSPRError() throws IOException {
    bitbucketRestClient = mock(BitbucketRestClient.class);
    bitbucketService = spy(BitbucketServiceImpl.class);
    Mockito.doReturn(bitbucketRestClient).when(bitbucketService).getBitbucketClient(any(), any());
    BitbucketConfig config = BitbucketConfig.builder().bitbucketUrl(CLOUD_URL).personalAccessToken(PAT).build();

    ResponseBody errorBody = ResponseBody.create(
        MediaType.parse("application/json"), new ObjectMapper().writeValueAsString(getSaaSMergeErrorResponse()));
    Call<BitbucketSaaSMergeResponse> saasMergeCall = mock(Call.class);
    Response<BitbucketSaaSMergeResponse> saasMergeResponse = Response.error(500, errorBody);

    when(bitbucketRestClient.mergeSaaSPR(any(), any(), any(), any(), any())).thenReturn(saasMergeCall);
    when(saasMergeCall.execute()).thenReturn(saasMergeResponse);
    MergePRResponse mergePRResponse =
        bitbucketService.mergePR(config, TOKEN, USERNAME, ORG, REPO_SLUG, PR_NUMBER, true, REF);
    assertThat(mergePRResponse.getErrorMessage()).isEqualTo(MERGE_ERR_MESSAGE);
    assertThat(mergePRResponse.isMerged()).isEqualTo(false);
    assertThat(mergePRResponse.getErrorCode()).isEqualTo(500);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testMergeOnPremPR() throws IOException {
    bitbucketRestClient = mock(BitbucketRestClient.class);
    bitbucketService = spy(BitbucketServiceImpl.class);
    Mockito.doReturn(bitbucketRestClient).when(bitbucketService).getBitbucketClient(any(), any());
    BitbucketConfig config = BitbucketConfig.builder().bitbucketUrl(ONPREM_URL).personalAccessToken(PAT).build();
    Call<BitbucketOnPremMergeResponse> onPremMergeCall = mock(Call.class);

    Response<BitbucketOnPremMergeResponse> onPremMergeResponse = Response.success(getOnPremMergeResponse());

    when(bitbucketRestClient.mergeOnPremPR(any(), any(), any(), any(), any())).thenReturn(onPremMergeCall);
    when(onPremMergeCall.execute()).thenReturn(onPremMergeResponse);
    MergePRResponse mergePRResponse =
        bitbucketService.mergePR(config, TOKEN, USERNAME, ORG, REPO_SLUG, PR_NUMBER, false, REF);
    assertThat(mergePRResponse.getSha()).isEqualTo(SHA);
    assertThat(mergePRResponse.isMerged()).isEqualTo(true);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testMergeOnPremPRError() throws IOException {
    bitbucketRestClient = mock(BitbucketRestClient.class);
    bitbucketService = spy(BitbucketServiceImpl.class);
    Mockito.doReturn(bitbucketRestClient).when(bitbucketService).getBitbucketClient(any(), any());
    BitbucketConfig config = BitbucketConfig.builder().bitbucketUrl(ONPREM_URL).personalAccessToken(PAT).build();
    Call<BitbucketOnPremMergeResponse> onPremMergeCall = mock(Call.class);

    ResponseBody errorBody = ResponseBody.create(
        MediaType.parse("application/json"), new ObjectMapper().writeValueAsString(getOnPremMergeErrorResponse()));

    Response<BitbucketOnPremMergeResponse> onPremMergeResponse = Response.error(500, errorBody);

    when(bitbucketRestClient.mergeOnPremPR(any(), any(), any(), any(), any())).thenReturn(onPremMergeCall);
    when(onPremMergeCall.execute()).thenReturn(onPremMergeResponse);
    MergePRResponse mergePRResponse =
        bitbucketService.mergePR(config, TOKEN, USERNAME, ORG, REPO_SLUG, PR_NUMBER, true, REF);
    assertThat(mergePRResponse.getErrorMessage()).isEqualTo(MERGE_ERR_MESSAGE);
    assertThat(mergePRResponse.isMerged()).isEqualTo(false);
    assertThat(mergePRResponse.getErrorCode()).isEqualTo(500);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testDeleteRef() throws IOException {
    MergePRResponse mergePRResponse = new MergePRResponse();
    mergePRResponse.setSha(SHA);
    mergePRResponse.setMerged(true);
    bitbucketRestClient = mock(BitbucketRestClient.class);
    bitbucketService = spy(BitbucketServiceImpl.class);
    Mockito.doReturn(bitbucketRestClient).when(bitbucketService).getBitbucketClient(any(), any());
    BitbucketConfig config = BitbucketConfig.builder().bitbucketUrl(ONPREM_URL).personalAccessToken(PAT).build();
    Call<Object> deleteRefCall = mock(Call.class);
    Response<Object> deleteRefResponse = Response.success(new Object());

    when(bitbucketRestClient.deleteOnPremRef(any(), any(), any(), any())).thenReturn(deleteRefCall);
    when(deleteRefCall.execute()).thenReturn(deleteRefResponse);
    boolean isDeleted = bitbucketService.deleteRef(config, TOKEN, REF, REPO_SLUG, ORG, mergePRResponse);
    assertThat(isDeleted).isEqualTo(true);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testDeleteRefFailure() throws IOException {
    MergePRResponse mergePRResponse = new MergePRResponse();
    mergePRResponse.setSha(SHA);
    mergePRResponse.setMerged(true);
    bitbucketRestClient = mock(BitbucketRestClient.class);
    bitbucketService = spy(BitbucketServiceImpl.class);
    Mockito.doReturn(bitbucketRestClient).when(bitbucketService).getBitbucketClient(any(), any());
    BitbucketConfig config = BitbucketConfig.builder().bitbucketUrl(ONPREM_URL).personalAccessToken(PAT).build();
    Call<Object> deleteRefCall = mock(Call.class);

    ResponseBody body = mock(ResponseBody.class);
    Response<Object> deleteRefResponse = Response.error(500, body);

    when(bitbucketRestClient.deleteOnPremRef(any(), any(), any(), any())).thenReturn(deleteRefCall);
    when(deleteRefCall.execute()).thenReturn(deleteRefResponse);
    boolean isDeleted = bitbucketService.deleteRef(config, "xyz", REF, REPO_SLUG, ORG, mergePRResponse);
    assertThat(isDeleted).isEqualTo(false);
  }

  private BitbucketSaaSErrorResponse getSaaSMergeErrorResponse() {
    BitbucketSaaSErrorResponse.Error error = new BitbucketSaaSErrorResponse.Error();
    error.setMessage(MERGE_ERR_MESSAGE);
    BitbucketSaaSErrorResponse saasErrorResponse = new BitbucketSaaSErrorResponse();
    saasErrorResponse.setError(error);
    return saasErrorResponse;
  }

  private BitbucketSaaSMergeResponse getSaaSMergeResponse() {
    BitbucketSaaSMergeResponse mergeResponse = new BitbucketSaaSMergeResponse();
    BitbucketSaaSMergeResponse.MergeCommit commit = new BitbucketSaaSMergeResponse.MergeCommit();
    commit.setHash(SHA);
    mergeResponse.setMergeCommit(commit);
    return mergeResponse;
  }

  private BitbucketOnPremMergeResponse getOnPremMergeResponse() {
    BitbucketOnPremMergeResponse mergeResponse = new BitbucketOnPremMergeResponse();
    BitbucketOnPremMergeResponse.MergeCommit commit = new BitbucketOnPremMergeResponse.MergeCommit();
    commit.setId(SHA);
    BitbucketOnPremMergeResponse.Properties properties = new BitbucketOnPremMergeResponse.Properties();
    properties.setMergeCommit(commit);
    mergeResponse.setProperties(properties);
    return mergeResponse;
  }

  private BitbucketOnPremErrorResponse getOnPremMergeErrorResponse() {
    BitbucketOnPremErrorResponse.Error err = new BitbucketOnPremErrorResponse.Error();
    err.setMessage(MERGE_ERR_MESSAGE);
    BitbucketOnPremErrorResponse mergeResponse = new BitbucketOnPremErrorResponse();
    mergeResponse.setErrors(Arrays.asList(err));
    return mergeResponse;
  }
}
