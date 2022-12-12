package io.harness.delegate.task.helm;

import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.delegate.beans.connector.helm.HttpHelmAuthType.USER_PASSWORD;
import static io.harness.rule.OwnerRule.ACHYUTH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmUsernamePasswordDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.encryption.SecretRefData;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class HelmFetchChartVersionTaskNGTest extends CategoryTest {
  @Mock private HelmTaskHelperBase helmTaskHelperBase;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;

  @InjectMocks
  @Spy
  HelmFetchChartVersionTaskNG helmFetchChartVersionTaskNG =
      new HelmFetchChartVersionTaskNG(DelegateTaskPackage.builder()
                                          .delegateId("delegateid")
                                          .data(TaskData.builder().timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                                          .build(),
          logStreamingTaskClient, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRunMethod() throws Exception {
    HttpHelmConnectorDTO connectorDTO =
        HttpHelmConnectorDTO.builder()
            .auth(HttpHelmAuthenticationDTO.builder()
                      .authType(USER_PASSWORD)
                      .credentials(
                          HttpHelmUsernamePasswordDTO.builder()
                              .username("test")
                              .passwordRef(SecretRefData.builder().decryptedValue("password".toCharArray()).build())
                              .build())
                      .build())
            .build();
    HelmChartManifestDelegateConfig manifestDelegateConfig =
        HelmChartManifestDelegateConfig.builder()
            .storeDelegateConfig(HttpHelmStoreDelegateConfig.builder()
                                     .encryptedDataDetails(Collections.emptyList())
                                     .httpHelmConnector(connectorDTO)
                                     .build())
            .helmVersion(HelmVersion.V380)
            .build();

    HelmFetchChartVersionRequestNG helmFetchChartVersionRequestNG =
        HelmFetchChartVersionRequestNG.builder().helmChartManifestDelegateConfig(manifestDelegateConfig).build();

    doNothing().when(helmTaskHelperBase).decryptEncryptedDetails(eq(manifestDelegateConfig));
    doReturn(Collections.emptyList())
        .when(helmTaskHelperBase)
        .fetchChartVersions(eq(manifestDelegateConfig), anyLong(), any());
    doReturn("dir").when(helmFetchChartVersionTaskNG).getDestinationDirectory(any());

    HelmFetchChartVersionResponse helmFetchChartVersionResponse =
        helmFetchChartVersionTaskNG.run(helmFetchChartVersionRequestNG);
    verify(helmTaskHelperBase, times(1)).decryptEncryptedDetails(eq(manifestDelegateConfig));
    assertThat(helmFetchChartVersionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }
}
