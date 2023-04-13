package io.harness.releaseradar.beans;

import io.harness.releaseradar.entities.CommitDetails;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class JiraEventDetails {
    String sha;
    Long createdAt;
    Service service;
    Environment environment;

    public static JiraEventDetails toJiraEventDetails(CommitDetails commitDetails) {
        return JiraEventDetails.builder()
                .sha(commitDetails.getSha())
                .environment(commitDetails.getMetadata().getEnvironment())
                .service(commitDetails.getMetadata().getService())
                .createdAt(commitDetails.getCreatedAt())
                .build();
    }
}
