package io.harness.git.checks;

import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.task.ci.GitSCMType;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GitStatusCheckParams {
  String title;
  String desc;
  String state;
  String buildNumber;
  String detailsUrl;
  String repo;
  String owner;
  String sha;
  String identifier;
  String target_url;
  String userName;
  ConnectorDetails connectorDetails; // Use connectorDetails to retrieve all information
  GitSCMType gitSCMType;
}
