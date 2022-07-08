package io.harness.connector.entities.embedded.azurerepoconnector;

import io.harness.delegate.beans.connector.scm.GitConfigConstants;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AzureRepoConnectionType {
  @JsonProperty(GitConfigConstants.PROJECT) PROJECT,
  @JsonProperty(GitConfigConstants.REPO) REPO
}
