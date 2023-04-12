package io.harness.releaseradar.beans;

import io.harness.releaseradar.clients.HarnessClient;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EnvDeploymentStatus {
    String branch;
    String commitId;
    String version;
    String timestamp;

    public static EnvDeploymentStatus toEnvDeploymentStatus(HarnessClient.VersionInfo versionInfo) {
        return EnvDeploymentStatus.builder()
                .branch(versionInfo.getGitBranch())
                .commitId(versionInfo.getGitCommit())
                .version(versionInfo.getVersion())
                .timestamp(versionInfo.getTimestamp())
                .build();
    }
}
