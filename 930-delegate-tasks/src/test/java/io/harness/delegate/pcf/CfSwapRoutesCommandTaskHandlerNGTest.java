package io.harness.delegate.pcf;

import static io.harness.delegate.cf.CfTestConstants.ACCOUNT_ID;
import static io.harness.delegate.cf.CfTestConstants.ORG;
import static io.harness.delegate.cf.CfTestConstants.RUNNING;
import static io.harness.delegate.cf.CfTestConstants.SPACE;
import static io.harness.delegate.cf.CfTestConstants.getPcfConfig;
import static io.harness.rule.OwnerRule.ADWAIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.request.CfSwapRoutesRequestNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class CfSwapRoutesCommandTaskHandlerNGTest extends CategoryTest {
  @Mock CfDeploymentManager cfDeploymentManager;
  @Mock SecretDecryptionService encryptionService;
  @Mock EncryptedDataDetail encryptedDataDetail;
  @Mock LogCallback executionLogCallback;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;

  @InjectMocks @Spy PcfCommandTaskBaseHelper pcfCommandTaskHelper;
  @InjectMocks @Spy CfSwapRouteCommandTaskHandlerNG cfSwapRouteCommandTaskHandlerNG;

  @Before
  public void setUp() {
    doReturn(executionLogCallback).when(logStreamingTaskClient).obtainLogCallback(any());
  }
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testResizeOldApplications() throws PivotalClientApiException {
    List<CfAppSetupTimeDetails> appSetupTimeDetailsList = Collections.singletonList(
        CfAppSetupTimeDetails.builder().applicationName("app1").initialInstanceCount(1).build());

    CfSwapRoutesRequestNG pcfCommandRequest = CfSwapRoutesRequestNG.builder()
                                                  .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROUTES)
                                                  .tasInfraConfig(TasInfraConfig.builder().build())
                                                  .accountId(ACCOUNT_ID)
                                                  .timeoutIntervalInMin(2)
                                                  .downsizeOldApplication(false)
                                                  .finalRoutes(Collections.singletonList("a.b.c"))
                                                  .useAppAutoscalar(false)
                                                  .build();

    reset(cfDeploymentManager);
    CfRouteUpdateRequestConfigData routeUpdateRequestConfigData = CfRouteUpdateRequestConfigData.builder()
                                                                      .downsizeOldApplication(false)
                                                                      .finalRoutes(Collections.singletonList("a.b.c"))
                                                                      .isRollback(false)
                                                                      .isStandardBlueGreen(true)
                                                                      .build();
    cfSwapRouteCommandTaskHandlerNG.resizeOldApplications(
        pcfCommandRequest, CfRequestConfig.builder().build(), routeUpdateRequestConfigData, executionLogCallback, "");
    verify(cfDeploymentManager, never()).resizeApplication(any());
  }
}
