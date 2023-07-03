/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.impl.scm;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.encryption.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cistatus.service.GithubService;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketOAuthDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabOauthDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessApiAccessDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessJWTTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessTokenSpecDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.git.GitClientHelper;
import io.harness.product.ci.scm.proto.AuthType;
import io.harness.product.ci.scm.proto.AzureProvider;
import io.harness.product.ci.scm.proto.BitbucketCloudProvider;
import io.harness.product.ci.scm.proto.BitbucketServerProvider;
import io.harness.product.ci.scm.proto.GithubProvider;
import io.harness.product.ci.scm.proto.GitlabProvider;
import io.harness.product.ci.scm.proto.HarnessAccessToken;
import io.harness.product.ci.scm.proto.HarnessJWT;
import io.harness.product.ci.scm.proto.HarnessProvider;
import io.harness.product.ci.scm.proto.Provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Singleton
@Slf4j
@OwnedBy(DX)
public class ScmGitProviderMapper {
  @Inject(optional = true) GithubService githubService;
  @Inject ScmGitProviderHelper scmGitProviderHelper;
  private static final String SCM_SKIP_SSL = "SCM_SKIP_SSL";
  private static final String ADDITIONAL_CERTS_PATH = "ADDITIONAL_CERTS_PATH";

  public Provider mapToSCMGitProvider(ScmConnector scmConnector) {
    return mapToSCMGitProvider(scmConnector, false);
  }

  public Provider mapToSCMGitProvider(ScmConnector scmConnector, boolean debug) {
    if (scmConnector instanceof GithubConnectorDTO) {
      return mapToGithubProvider((GithubConnectorDTO) scmConnector, debug);
    } else if (scmConnector instanceof GitlabConnectorDTO) {
      return mapToGitLabProvider((GitlabConnectorDTO) scmConnector, debug);
    } else if (scmConnector instanceof BitbucketConnectorDTO) {
      return mapToBitbucketProvider((BitbucketConnectorDTO) scmConnector, debug);
    } else if (scmConnector instanceof AzureRepoConnectorDTO) {
      return mapToAzureRepoProvider((AzureRepoConnectorDTO) scmConnector, debug);
    } else if (scmConnector instanceof HarnessConnectorDTO) {
      return mapToHarnessProvider((HarnessConnectorDTO) scmConnector, debug);
    } else {
      throw new NotImplementedException(
          String.format("The scm apis for the provider type %s is not supported", scmConnector.getClass()));
    }
  }

  private Provider mapToBitbucketProvider(BitbucketConnectorDTO bitbucketConnector, boolean debug) {
    boolean skipVerify = checkScmSkipVerify();
    String bitBucketApiURL = GitClientHelper.getBitBucketApiURL(bitbucketConnector.getUrl());
    Provider.Builder builder = Provider.newBuilder().setEndpoint(bitBucketApiURL).setDebug(debug);
    if (GitClientHelper.isBitBucketSAAS(bitbucketConnector.getUrl())) {
      builder.setBitbucketCloud(createBitbucketCloudProvider(bitbucketConnector));
    } else {
      builder.setBitbucketServer(createBitbucketServerProvider(bitbucketConnector));
    }
    return builder.setSkipVerify(skipVerify).setAdditionalCertsPath(getAdditionalCertsPath()).build();
  }

  private Provider mapToAzureRepoProvider(AzureRepoConnectorDTO azureRepoConnector, boolean debug) {
    boolean skipVerify = checkScmSkipVerify();
    String org;
    String orgAndProject;
    String project;
    if (azureRepoConnector.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      orgAndProject = GitClientHelper.getAzureRepoOrgAndProjectHTTP(azureRepoConnector.getUrl());
    } else {
      orgAndProject = GitClientHelper.getAzureRepoOrgAndProjectSSH(azureRepoConnector.getUrl());
    }
    org = GitClientHelper.getAzureRepoOrg(orgAndProject);
    project = GitClientHelper.getAzureRepoProject(orgAndProject);
    String azureRepoApiURL = GitClientHelper.getAzureRepoApiURL(azureRepoConnector.getUrl());
    AzureRepoApiAccessDTO apiAccess = azureRepoConnector.getApiAccess();
    AzureRepoTokenSpecDTO azureRepoUsernameTokenApiAccessDTO = (AzureRepoTokenSpecDTO) apiAccess.getSpec();
    String personalAccessToken = String.valueOf(azureRepoUsernameTokenApiAccessDTO.getTokenRef().getDecryptedValue());
    AzureProvider.Builder azureProvider =
        AzureProvider.newBuilder().setOrganization(org).setPersonalAccessToken(personalAccessToken);
    if (isNotEmpty(project)) {
      azureProvider.setProject(project);
    }
    Provider.Builder builder =
        Provider.newBuilder().setDebug(debug).setAzure(azureProvider).setEndpoint(azureRepoApiURL);
    return builder.setSkipVerify(skipVerify).setAdditionalCertsPath(getAdditionalCertsPath()).build();
  }

  private boolean checkScmSkipVerify() {
    final String scm_skip_ssl = System.getenv(SCM_SKIP_SSL);
    boolean skipVerify = "true".equals(scm_skip_ssl);
    if (skipVerify) {
      log.info("Skipping verification");
    }
    return skipVerify;
  }

  private String getAdditionalCertsPath() {
    String additionalCertsPath = "";
    try {
      additionalCertsPath = System.getenv(ADDITIONAL_CERTS_PATH);
    } catch (SecurityException e) {
      log.error("Don't have sufficient permission to query ADDITIONAL_CERTS_PATH", e);
    }
    if (additionalCertsPath == null) {
      additionalCertsPath = "";
    }
    return additionalCertsPath;
  }

  private BitbucketCloudProvider createBitbucketCloudProvider(BitbucketConnectorDTO bitbucketConnector) {
    BitbucketApiAccessDTO apiAccess = bitbucketConnector.getApiAccess();
    if (apiAccess.getSpec() instanceof BitbucketUsernameTokenApiAccessDTO) {
      BitbucketUsernameTokenApiAccessDTO bitbucketUsernameTokenApiAccessDTO =
          (BitbucketUsernameTokenApiAccessDTO) apiAccess.getSpec();
      String username = getSecretAsStringFromPlainTextOrSecretRef(
          bitbucketUsernameTokenApiAccessDTO.getUsername(), bitbucketUsernameTokenApiAccessDTO.getUsernameRef());
      String appPassword = String.valueOf(bitbucketUsernameTokenApiAccessDTO.getTokenRef().getDecryptedValue());
      return BitbucketCloudProvider.newBuilder().setUsername(username).setAppPassword(appPassword).build();
    } else {
      BitbucketOAuthDTO bitBucketOAuthDTO = (BitbucketOAuthDTO) apiAccess.getSpec();
      return BitbucketCloudProvider.newBuilder()
          .setOauthToken(scmGitProviderHelper.getToken(bitBucketOAuthDTO.getTokenRef()))
          .setAuthType(AuthType.OAUTH)
          .build();
    }
  }

  private BitbucketServerProvider createBitbucketServerProvider(BitbucketConnectorDTO bitbucketConnector) {
    BitbucketApiAccessDTO apiAccess = bitbucketConnector.getApiAccess();
    BitbucketUsernameTokenApiAccessDTO bitbucketUsernameTokenApiAccessDTO =
        (BitbucketUsernameTokenApiAccessDTO) apiAccess.getSpec();
    String username = getSecretAsStringFromPlainTextOrSecretRef(
        bitbucketUsernameTokenApiAccessDTO.getUsername(), bitbucketUsernameTokenApiAccessDTO.getUsernameRef());
    String personalAccessToken = String.valueOf(bitbucketUsernameTokenApiAccessDTO.getTokenRef().getDecryptedValue());

    return BitbucketServerProvider.newBuilder()
        .setUsername(username)
        .setPersonalAccessToken(personalAccessToken)
        .build();
  }

  private Provider mapToGitLabProvider(GitlabConnectorDTO gitlabConnector, boolean debug) {
    boolean skipVerify = checkScmSkipVerify();
    return Provider.newBuilder()
        .setGitlab(createGitLabProvider(gitlabConnector))
        .setDebug(debug)
        .setEndpoint(GitClientHelper.getGitlabApiURL(gitlabConnector.getUrl(), getGitlabApiUrl(gitlabConnector)))
        .setSkipVerify(skipVerify)
        .setAdditionalCertsPath(getAdditionalCertsPath())
        .build();
  }

  private GitlabProvider createGitLabProvider(GitlabConnectorDTO gitlabConnector) {
    String accessToken = getAccessToken(gitlabConnector);
    return GitlabProvider.newBuilder().setAccessToken(accessToken).build();
  }

  private String getAccessToken(GitlabConnectorDTO gitlabConnector) {
    GitlabApiAccessDTO apiAccess = gitlabConnector.getApiAccess();
    switch (apiAccess.getType()) {
      case TOKEN:
        GitlabTokenSpecDTO apiAccessDTO = (GitlabTokenSpecDTO) apiAccess.getSpec();
        return String.valueOf(apiAccessDTO.getTokenRef().getDecryptedValue());
      case OAUTH:
        GitlabOauthDTO gitlabOauthDTO = (GitlabOauthDTO) apiAccess.getSpec();
        return String.valueOf(gitlabOauthDTO.getTokenRef().getDecryptedValue());
      default:
        throw new NotImplementedException(
            String.format("The scm apis for the api access type %s is not supported", apiAccess.getType()));
    }
  }

  private Provider mapToGithubProvider(GithubConnectorDTO githubConnector, boolean debug) {
    boolean skipVerify = checkScmSkipVerify();
    return Provider.newBuilder()
        .setGithub(createGithubProvider(githubConnector))
        .setDebug(debug)
        .setEndpoint(GitClientHelper.getGithubApiURL(githubConnector.getUrl()))
        .setSkipVerify(skipVerify)
        .setAdditionalCertsPath(getAdditionalCertsPath())
        .build();
  }

  private Provider mapToHarnessProvider(HarnessConnectorDTO harnessConnector, boolean debug) {
    boolean skipVerify = checkScmSkipVerify();
    return Provider.newBuilder()
        .setHarness(createHarnessProvider(harnessConnector))
        .setDebug(debug)
        .setEndpoint(GitClientHelper.getHarnessApiURL(harnessConnector.getUrl()))
        .setSkipVerify(skipVerify)
        .setAdditionalCertsPath(getAdditionalCertsPath())
        .build();
  }

  private GithubProvider createGithubProvider(GithubConnectorDTO githubConnector) {
    switch (githubConnector.getApiAccess().getType()) {
      case GITHUB_APP:
        // todo @aradisavljevic: switch to scm provider for github app after it is implemented
        String token = getAccessTokenFromGithubApp(githubConnector);
        return GithubProvider.newBuilder().setAccessToken(token).setIsGithubApp(true).build();
      case TOKEN:
        String accessToken = getAccessToken(githubConnector);
        return GithubProvider.newBuilder().setAccessToken(accessToken).build();
      case OAUTH:
        String oauthToken = getOauthToken(githubConnector);
        return GithubProvider.newBuilder().setAccessToken(oauthToken).build();
      default:
        throw new NotImplementedException(String.format(
            "The scm apis for the api access type %s is not supported", githubConnector.getApiAccess().getType()));
    }
  }

  private HarnessProvider createHarnessProvider(HarnessConnectorDTO harnessConnector) {
    switch (harnessConnector.getApiAccess().getType()) {
      case TOKEN:
        String accessToken = getAccessToken(harnessConnector);
        HarnessAccessToken harnessAccessToken = HarnessAccessToken.newBuilder().setAccessToken(accessToken).build();
        return HarnessProvider.newBuilder().setHarnessAccessToken(harnessAccessToken).build();
      case JWT_TOKEN:
        String jwtToken = getJWTToken(harnessConnector);
        HarnessJWT harnessJWT = HarnessJWT.newBuilder().setToken(jwtToken).build();
        return HarnessProvider.newBuilder().setHarnessJwt(harnessJWT).build();
      default:
        throw new NotImplementedException(String.format(
            "The scm apis for the api access type %s is not supported", harnessConnector.getApiAccess().getType()));
    }
  }

  private String getAccessTokenFromGithubApp(GithubConnectorDTO githubConnector) {
    GithubApiAccessDTO apiAccess = githubConnector.getApiAccess();
    GithubAppSpecDTO apiAccessDTO = (GithubAppSpecDTO) apiAccess.getSpec();
    return scmGitProviderHelper.getAccessTokenFromGithubApp(apiAccessDTO.getApplicationId(),
        apiAccessDTO.getApplicationIdRef(), apiAccessDTO.getInstallationId(), apiAccessDTO.getInstallationIdRef(),
        apiAccessDTO.getPrivateKeyRef(), githubConnector.getUrl());
  }

  private String getAccessToken(GithubConnectorDTO githubConnector) {
    GithubApiAccessDTO apiAccess = githubConnector.getApiAccess();
    GithubTokenSpecDTO apiAccessDTO = (GithubTokenSpecDTO) apiAccess.getSpec();
    return scmGitProviderHelper.getToken(apiAccessDTO.getTokenRef());
  }

  private String getAccessToken(HarnessConnectorDTO harnessConnector) {
    HarnessApiAccessDTO apiAccess = harnessConnector.getApiAccess();
    HarnessTokenSpecDTO apiAccessDTO = (HarnessTokenSpecDTO) apiAccess.getSpec();
    if (apiAccessDTO.getTokenRef() == null || apiAccessDTO.getTokenRef().getDecryptedValue() == null) {
      throw new InvalidArgumentsException(
          "The Personal Access Token is not set. Please set the Personal Access Token in the Git Connector which has permissions to use providers API's");
    }
    return String.valueOf(apiAccessDTO.getTokenRef().getDecryptedValue());
  }

  private String getJWTToken(HarnessConnectorDTO harnessConnector) {
    HarnessApiAccessDTO apiAccess = harnessConnector.getApiAccess();
    HarnessJWTTokenSpecDTO apiAccessDTO = (HarnessJWTTokenSpecDTO) apiAccess.getSpec();
    if (apiAccessDTO.getTokenRef() == null || apiAccessDTO.getTokenRef().getDecryptedValue() == null) {
      throw new InvalidArgumentsException(
          "The Personal Access Token is not set. Please set the JWT token in the Git Connector which has permissions to use providers API's");
    }
    return String.valueOf(apiAccessDTO.getTokenRef().getDecryptedValue());
  }

  private String getOauthToken(GithubConnectorDTO githubConnector) {
    GithubApiAccessDTO apiAccess = githubConnector.getApiAccess();
    GithubOauthDTO githubOauthDTO = (GithubOauthDTO) apiAccess.getSpec();
    return scmGitProviderHelper.getToken(githubOauthDTO.getTokenRef());
  }

  public static String getGitlabApiUrl(GitlabConnectorDTO gitlabConnector) {
    if (gitlabConnector.getApiAccess() == null || gitlabConnector.getApiAccess().getSpec() == null) {
      // not expected
      return null;
    }
    GitlabApiAccessSpecDTO spec = gitlabConnector.getApiAccess().getSpec();
    if (spec instanceof GitlabTokenSpecDTO) {
      GitlabTokenSpecDTO tokenSpec = (GitlabTokenSpecDTO) spec;
      return tokenSpec.getApiUrl();
    }
    return null;
  }
}
