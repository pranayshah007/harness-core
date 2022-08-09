package io.harness.connector.task.artifactory;

import static io.harness.utils.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;

import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryConfigRequest.ArtifactoryConfigRequestBuilder;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;

import com.google.inject.Singleton;

@Singleton
public class ArtifactoryRequestMapper {
  public ArtifactoryConfigRequest toArtifactoryRequest(ArtifactoryConnectorDTO artifactoryConnector) {
    final ArtifactoryConfigRequestBuilder artifactoryConfigRequestBuilder =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(artifactoryConnector.getArtifactoryServerUrl())
            .hasCredentials(false);
    if (artifactoryConnector.getAuth().getAuthType() == ArtifactoryAuthType.USER_PASSWORD) {
      final ArtifactoryUsernamePasswordAuthDTO credentials =
          (ArtifactoryUsernamePasswordAuthDTO) artifactoryConnector.getAuth().getCredentials();
      artifactoryConfigRequestBuilder.hasCredentials(true)
          .password(credentials.getPasswordRef().getDecryptedValue())
          .username(getSecretAsStringFromPlainTextOrSecretRef(credentials.getUsername(), credentials.getUsernameRef()));
    }
    return artifactoryConfigRequestBuilder.build();
  }
}