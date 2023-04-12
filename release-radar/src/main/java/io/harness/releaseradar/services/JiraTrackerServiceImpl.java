package io.harness.releaseradar.services;

import io.harness.releaseradar.beans.CommitDetails;
import io.harness.releaseradar.beans.CommitDetailsRequest;
import io.harness.releaseradar.beans.EnvDeploymentStatus;
import io.harness.releaseradar.beans.Environment;
import io.harness.releaseradar.beans.JiraStatusDetails;
import io.harness.releaseradar.beans.Service;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JiraTrackerServiceImpl implements JiraTrackerService {
    GitService gitService = new GitServiceImpl();
    HarnessEnvService harnessEnvService = new HarnessEnvServiceImpl();

    @Override
    public JiraStatusDetails getJiraStatusDetails(String jiraId) {
        Map<CommitDetails, Set<Pair<Environment, Service>>> commitDetailsListMap = new HashMap<>();

        Arrays.stream(Service.values()).forEach(service -> {
            Map<Environment, EnvDeploymentStatus> deploymentStatusMap = harnessEnvService.getDeploymentStatusForAllEnvs(service);
            deploymentStatusMap.forEach(((environment, envDeploymentStatus) -> {

                List<CommitDetails> commitDetailsList = gitService.getCommitList(CommitDetailsRequest.builder()
                                .branch(envDeploymentStatus.getBranch())
                                .maxCommits(1000)
                                .searchKeyword(jiraId)
                        .build());
                commitDetailsList.forEach(commitDetails -> {
                    if (!commitDetailsListMap.containsKey(commitDetails)) {
                        commitDetailsListMap.put(commitDetails, new HashSet<>());
                    }
                    commitDetailsListMap.get(commitDetails).add(Pair.of(environment, service));
                });
            }));
        });

        return JiraStatusDetails.builder()
                .commitDetailsListMap(commitDetailsListMap)
                .build();
    }
}
