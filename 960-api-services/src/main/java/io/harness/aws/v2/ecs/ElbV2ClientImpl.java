package io.harness.aws.v2.ecs;

import com.google.inject.Singleton;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.AwsClientHelper;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyListenerRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyListenerResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyRuleRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyRuleResponse;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class ElbV2ClientImpl extends AwsClientHelper implements ElbV2Client {
    @Override
    public SdkClient getClient(AwsInternalConfig awsConfig, String region) {
        return ElasticLoadBalancingV2Client.builder()
                .credentialsProvider(getAwsCredentialsProvider(awsConfig))
                .region(Region.of(region))
                .overrideConfiguration(getClientOverrideConfiguration(awsConfig))
                .build();
    }

    @Override
    public String client() {
        return "ELB";
    }

    @Override
    public void handleClientServiceException(AwsServiceException awsServiceException) {

    }

    @Override
    public DescribeListenersResponse describeListener(AwsInternalConfig awsConfig, DescribeListenersRequest describeListenersRequest, String region) {
        try (ElasticLoadBalancingV2Client elbClient = (ElasticLoadBalancingV2Client) getClient(awsConfig, region)) {
            super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
            return elbClient.describeListeners(describeListenersRequest);
        } catch (Exception exception) {
            super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
            super.handleException(exception);
        }
        return DescribeListenersResponse.builder().build();
    }

    @Override
    public DescribeRulesResponse describeRules(AwsInternalConfig awsConfig, DescribeRulesRequest describeRulesRequest, String region) {
        try (ElasticLoadBalancingV2Client elbClient = (ElasticLoadBalancingV2Client) getClient(awsConfig, region)) {
            super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
            return elbClient.describeRules(describeRulesRequest);
        } catch (Exception exception) {
            super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
            super.handleException(exception);
        }
        return DescribeRulesResponse.builder().build();
    }

    @Override
    public ModifyRuleResponse modifyRule(AwsInternalConfig awsConfig, ModifyRuleRequest modifyRuleRequest, String region) {
        try (ElasticLoadBalancingV2Client elbClient = (ElasticLoadBalancingV2Client) getClient(awsConfig, region)) {
            super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
            return elbClient.modifyRule(modifyRuleRequest);
        } catch (Exception exception) {
            super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
            super.handleException(exception);
        }
        return ModifyRuleResponse.builder().build();
    }

    @Override
    public ModifyListenerResponse modifyListener(AwsInternalConfig awsConfig, ModifyListenerRequest modifyListenerRequest,
                                                 String region) {
        try (ElasticLoadBalancingV2Client elbClient = (ElasticLoadBalancingV2Client) getClient(awsConfig, region)) {
            super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
            return elbClient.modifyListener(modifyListenerRequest);
        } catch (Exception exception) {
            super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
            super.handleException(exception);
        }
        return ModifyListenerResponse.builder().build();
    }
}
