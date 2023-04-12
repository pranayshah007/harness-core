package io.harness.releaseradar.services;

import io.harness.releaseradar.beans.CommitDetails;
import io.harness.releaseradar.beans.CommitDetailsRequest;
import io.harness.releaseradar.clients.GitHubApiClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class GitServiceImpl implements GitService {
    GitHubApiClient gitHubApiClient = new GitHubApiClient();
    String GITHUB_API_KEY = System.getenv("GITHUB_X_API_KEY");

    @Override
    @SneakyThrows
    public List<CommitDetails> getCommitList(CommitDetailsRequest commitDetailsRequest) {
        log.info("Commit Details Request : {}", commitDetailsRequest.toString());
        List<GitHubApiClient.Commit> githubCommitList = new ArrayList<>();
        List<CommitDetails> commitDetailsList = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            List<GitHubApiClient.Commit> commitList =
                    gitHubApiClient.listCommits("harness", "harness-core", GITHUB_API_KEY, commitDetailsRequest.getBranch(), i);
            githubCommitList.addAll(commitList);
            if (githubCommitList.size() > commitDetailsRequest.getMaxCommits()) {
                break;
            }
        }

        githubCommitList.forEach(commit -> {
            if (StringUtils.isEmpty(commitDetailsRequest.getSearchKeyword()) || commit.getCommit().getMessage().contains(commitDetailsRequest.getSearchKeyword())) {
                commitDetailsList.add(CommitDetails.toCommitDetails(commit));
            }
        });
        return commitDetailsList;
    }
}