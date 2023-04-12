package io.harness.releaseradar.services;

import io.harness.releaseradar.beans.CommitDetails;
import io.harness.releaseradar.beans.CommitDetailsRequest;
import io.harness.releaseradar.clients.GitHubApiClient;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class GitServiceImpl implements GitService {
    GitHubApiClient gitHubApiClient = new GitHubApiClient();
    String GITHUB_API_KEY = System.getenv("GITHUB_X_API_KEY");

    @Override
    public List<CommitDetails> getCommitList(CommitDetailsRequest commitDetailsRequest) throws IOException {
        List<GitHubApiClient.Commit> githubCommitList = new ArrayList<>();
        List<CommitDetails> commitDetailsList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            List<GitHubApiClient.Commit> commitList =
                    gitHubApiClient.listCommits("harness", "harness-core", GITHUB_API_KEY, i);
            githubCommitList.addAll(commitList);
        }

        githubCommitList.forEach(commit -> commitDetailsList.add(CommitDetails.toCommitDetails(commit)));
        return commitDetailsList;
    }
}