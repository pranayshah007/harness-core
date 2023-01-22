package io.harness.delegate.task.googlefunctions.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.googlefunctions.GoogleFunctionCommandTypeNG;
import io.harness.delegate.task.googlefunctions.GoogleFunctionInfraConfig;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import static io.harness.expression.Expression.ALLOW_SECRETS;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionRollbackRequest implements GoogleFunctionCommandRequest, ExpressionReflectionUtils.NestedAnnotationResolver {
    GoogleFunctionCommandTypeNG googleFunctionCommandType;
    String commandName;
    CommandUnitsProgress commandUnitsProgress;
    @NonFinal @Expression(ALLOW_SECRETS) GoogleFunctionInfraConfig googleFunctionInfraConfig;
    @NonFinal @Expression(ALLOW_SECRETS) Integer timeoutIntervalInMin;
    @NonFinal @Expression(ALLOW_SECRETS) String googleCloudRunServiceAsString;
    @NonFinal @Expression(ALLOW_SECRETS) String googleFunctionAsString;
    boolean isFirstDeployment;
}
