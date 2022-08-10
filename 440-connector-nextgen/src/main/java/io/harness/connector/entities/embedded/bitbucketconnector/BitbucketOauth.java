package io.harness.connector.entities.embedded.bitbucketconnector;

import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccess;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("io.harness.connector.entities.embedded.bitbucketconnector.GitlabOauth")
public class BitbucketOauth implements BitbucketHttpAuth, BitbucketApiAccess {
  String tokenRef;
  String refreshTokenRef;
}
