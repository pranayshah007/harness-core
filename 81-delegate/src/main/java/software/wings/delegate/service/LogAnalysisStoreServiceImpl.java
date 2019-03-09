package software.wings.delegate.service;

import static io.harness.network.SafeHttpCall.execute;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.wings.delegatetasks.LogAnalysisStoreService;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.sm.StateType;
import software.wings.verification.VerificationServiceClient;

import java.util.List;

/**
 * Created by rsingh on 06/20/17.
 */
@Singleton
public class LogAnalysisStoreServiceImpl implements LogAnalysisStoreService {
  private static final Logger logger = LoggerFactory.getLogger(LogAnalysisStoreServiceImpl.class);
  @Inject private VerificationServiceClient verificationServiceClient;

  @Override
  public boolean save(StateType stateType, String accountId, String appId, String cvConfigId, String stateExecutionId,
      String workflowId, String workflowExecutionId, String serviceId, String delegateTaskId, List<LogElement> logs) {
    try {
      switch (stateType) {
        case SPLUNKV2:
          return execute(verificationServiceClient.saveLogs(accountId, appId, cvConfigId, stateExecutionId, workflowId,
                             workflowExecutionId, serviceId, ClusterLevel.L2, delegateTaskId, StateType.SPLUNKV2, logs))
              .getResource();
        case SUMO:
        case ELK:
        case LOGZ:
        case LOG_VERIFICATION:
        case BUG_SNAG:
          return execute(verificationServiceClient.saveLogs(accountId, appId, cvConfigId, stateExecutionId, workflowId,
                             workflowExecutionId, serviceId, ClusterLevel.L0, delegateTaskId, stateType, logs))
              .getResource();
        default:
          throw new IllegalStateException("Invalid state: " + stateType);
      }
    } catch (Exception ex) {
      logger.error("Exception while saving log data for stateExecutionId: {}", stateExecutionId, ex);
      return false;
    }
  }
}
