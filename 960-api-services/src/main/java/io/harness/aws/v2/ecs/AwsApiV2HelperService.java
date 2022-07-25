package io.harness.aws.v2.ecs;

import io.harness.aws.beans.AwsInternalConfig;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;

public interface AwsApiV2HelperService {
    AwsCredentialsProvider getAwsCredentialsProvider(AwsInternalConfig awsConfig);

    ClientOverrideConfiguration getClientOverrideConfiguration(AwsInternalConfig awsConfig);

}
