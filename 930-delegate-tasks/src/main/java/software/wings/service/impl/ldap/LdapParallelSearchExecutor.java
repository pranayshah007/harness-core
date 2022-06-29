/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.ldap;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;

import software.wings.helpers.ext.ldap.LdapSearch;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.ldaptive.Connection;
import org.ldaptive.LdapException;

/**
 * This class is meant to make parallel call for LDAP search
 */
@OwnedBy(PL)
@Slf4j
public class LdapParallelSearchExecutor {
  int LDAP_SEARCH_MAX_WAIT_TIME_IN_SECONDS = 30;

  private void updateLdapRequestTimeout(LdapSearch ldapSearch) {
    try {
      Connection connection = ldapSearch.getConnectionFactory().getConnection();
      LDAP_SEARCH_MAX_WAIT_TIME_IN_SECONDS = (int) connection.getConnectionConfig().getResponseTimeout().getSeconds();
    } catch (LdapException e) {
      LDAP_SEARCH_MAX_WAIT_TIME_IN_SECONDS = 30;
    }
  }

  public AbstractLdapResponse userExist(final List<LdapUserExistsRequest> userExistsQueryObjectList,
      Function<LdapUserExistsRequest, LdapUserExistsResponse> executeLdapUserExistsRequest) {
    LdapUserExistsResponse ldapUserExistsReturn = null;

    if (!isEmpty(userExistsQueryObjectList)) {
      if (userExistsQueryObjectList.size() == 1) {
        ldapUserExistsReturn = executeLdapUserExistsRequest.apply(userExistsQueryObjectList.get(0));
      } else {
        ldapUserExistsReturn = executeLdapUserExistsRequest(userExistsQueryObjectList, executeLdapUserExistsRequest);
      }
    }

    return ldapUserExistsReturn;
  }

  private LdapUserExistsResponse executeLdapUserExistsRequest(
      @NotNull List<LdapUserExistsRequest> ldapUserExistsRequest,
      Function<LdapUserExistsRequest, LdapUserExistsResponse> executeLdapUserExistsRequest) {
    LdapUserExistsResponse ldapUserExistsReturn = null;

    if (ldapUserExistsRequest.size() != 0) {
      updateLdapRequestTimeout(ldapUserExistsRequest.get(0).getLdapSearch());
    }

    CompletionService<LdapUserExistsResponse> taskCompletionService =
        new ExecutorCompletionService<>(LdapExecutorService.getInstance().getExecutorService());

    List<Future<? extends AbstractLdapResponse>> ldapUserExistsResponseFutures =
        ldapUserExistsRequest.stream()
            .map(request -> taskCompletionService.submit(() -> executeLdapUserExistsRequest.apply(request)))
            .collect(Collectors.toList());

    LdapUserExistsResponse ldapUserExistsCallResponseTemp = null;
    for (int i = 0; i < ldapUserExistsRequest.size(); i++) {
      try {
        ldapUserExistsCallResponseTemp =
            taskCompletionService.take().get(LDAP_SEARCH_MAX_WAIT_TIME_IN_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        log.error("InterruptedException exception occurred when making user exist search", e);
      } catch (ExecutionException e) {
        log.error("ExecutionException exception occurred when making user exist search", e);
      } catch (TimeoutException e) {
        log.error("TimeoutException exception occurred when making user exist search", e);
      }

      if (ldapUserExistsCallResponseTemp != null && StringUtils.isNotBlank(ldapUserExistsCallResponseTemp.getDn())) {
        ldapUserExistsReturn = ldapUserExistsCallResponseTemp;
        // Cancel other tasks
        cancelPendingTasks(ldapUserExistsResponseFutures);
        break;
      }
    }

    return ldapUserExistsReturn;
  }

  public List<LdapGetUsersResponse> getUserSearchResult(final List<LdapGetUsersRequest> ldapGetUsersRequests,
      Function<LdapGetUsersRequest, LdapGetUsersResponse> executeLdapGetUsersRequest) {
    List<LdapGetUsersResponse> ldapGetUsersResponse = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(ldapGetUsersRequests)) {
      if (ldapGetUsersRequests.size() == 1) {
        ldapGetUsersResponse = Arrays.asList(executeLdapGetUsersRequest.apply(ldapGetUsersRequests.get(0)));
      } else {
        ldapGetUsersResponse = executeLdapGetUsersRequest(ldapGetUsersRequests, executeLdapGetUsersRequest);
      }
    }

    return ldapGetUsersResponse;
  }

  private List<LdapGetUsersResponse> executeLdapGetUsersRequest(@NotNull List<LdapGetUsersRequest> ldapGetUsersRequests,
      Function<LdapGetUsersRequest, LdapGetUsersResponse> executeLdapGetUsersRequest) {
    if (ldapGetUsersRequests.size() != 0) {
      updateLdapRequestTimeout(ldapGetUsersRequests.get(0).getLdapSearch());
    }

    CompletionService<LdapGetUsersResponse> taskCompletionService =
        new ExecutorCompletionService<>(LdapExecutorService.getInstance().getExecutorService());

    ldapGetUsersRequests.stream()
        .map(request -> taskCompletionService.submit(() -> executeLdapGetUsersRequest.apply(request)))
        .collect(Collectors.toList());

    List<LdapGetUsersResponse> ldapGetUsersResponse = new ArrayList<>();

    for (int i = 0; i < ldapGetUsersRequests.size(); i++) {
      LdapGetUsersResponse currentLdapGetUsersResponse = null;
      try {
        currentLdapGetUsersResponse =
            taskCompletionService.take().get(LDAP_SEARCH_MAX_WAIT_TIME_IN_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        log.error("InterruptedException exception occurred when making user search", e);
      } catch (ExecutionException e) {
        log.error("ExecutionException exception occurred when making user search", e);
      } catch (TimeoutException e) {
        log.error("TimeoutException exception occurred when making user search", e);
      }

      if (currentLdapGetUsersResponse != null) {
        if (currentLdapGetUsersResponse.getSearchResult() != null
            && currentLdapGetUsersResponse.getSearchResult().size() > 0) {
          ldapGetUsersResponse.add(currentLdapGetUsersResponse);
        }
      }
    }

    return ldapGetUsersResponse;
  }

  public List<LdapListGroupsResponse> listGroupsSearchResult(
      @NotNull List<LdapListGroupsRequest> ldapListGroupsRequests,
      Function<LdapListGroupsRequest, LdapListGroupsResponse> executeLdapGroupsSearchRequest) {
    List<LdapListGroupsResponse> ldapListGroupsResponses = Lists.newArrayList();
    if (CollectionUtils.isNotEmpty(ldapListGroupsRequests)) {
      if (ldapListGroupsRequests.size() == 1) {
        ldapListGroupsResponses = Arrays.asList(executeLdapGroupsSearchRequest.apply(ldapListGroupsRequests.get(0)));
      } else {
        ldapListGroupsResponses = executeListGroupsSearchResult(ldapListGroupsRequests, executeLdapGroupsSearchRequest);
      }
    }
    return ldapListGroupsResponses;
  }

  public List<LdapListGroupsResponse> executeListGroupsSearchResult(
      @NotNull List<LdapListGroupsRequest> ldapListGroupsRequests,
      Function<LdapListGroupsRequest, LdapListGroupsResponse> executeLdapGroupsSearchRequest) {
    if (ldapListGroupsRequests.size() != 0) {
      updateLdapRequestTimeout(ldapListGroupsRequests.get(0).getLdapSearch());
    }

    CompletionService<LdapListGroupsResponse> taskCompletionService =
        new ExecutorCompletionService<>(LdapExecutorService.getInstance().getExecutorService());

    ldapListGroupsRequests.forEach(
        request -> taskCompletionService.submit(() -> executeLdapGroupsSearchRequest.apply(request)));

    List<LdapListGroupsResponse> ldapListGroupsResponses = new ArrayList<>();

    for (int i = 0; i < ldapListGroupsRequests.size(); i++) {
      LdapListGroupsResponse currentLdapGroupResponse = null;
      try {
        currentLdapGroupResponse =
            taskCompletionService.take().get(LDAP_SEARCH_MAX_WAIT_TIME_IN_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        log.error("InterruptedException exception occurred when making group search name", e);
      } catch (ExecutionException e) {
        log.error("ExecutionException exception occurred when making group search name", e);
      } catch (TimeoutException e) {
        log.error("TimeoutException exception occurred when making group search name", e);
      }

      if (currentLdapGroupResponse != null) {
        if (currentLdapGroupResponse.getSearchResult() != null
            && currentLdapGroupResponse.getSearchResult().size() > 0) {
          ldapListGroupsResponses.add(currentLdapGroupResponse);
        }
      }
    }
    return ldapListGroupsResponses;
  }

  private void cancelPendingTasks(List<Future<? extends AbstractLdapResponse>> futures) {
    if (CollectionUtils.isNotEmpty(futures)) {
      futures.stream().filter(f -> !f.isDone()).forEach(f -> f.cancel(true));
    }
  }
}
