package io.harness.delegate.beans.connector.scm.azurerepo;

import io.harness.delegate.beans.connector.scm.GitConfigConstants;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AzureRepoConnectionTypeDTO {
  @JsonProperty(GitConfigConstants.PROJECT) PROJECT,
  @JsonProperty(GitConfigConstants.REPO) REPO
}
