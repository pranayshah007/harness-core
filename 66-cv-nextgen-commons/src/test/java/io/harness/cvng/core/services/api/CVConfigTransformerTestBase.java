package io.harness.cvng.core.services.api;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.cvng.CVNextGenBaseTest;
import io.harness.cvng.beans.DSConfig;
import io.harness.cvng.core.services.entities.CVConfig;
import org.junit.Before;

public class CVConfigTransformerTestBase extends CVNextGenBaseTest {
  protected String identifier;
  protected String accountId;
  protected String projectIdentifier;
  protected String productName;
  protected String connectorId;
  protected String envIdentifier;
  protected String groupId;
  protected String serviceIdentifier;

  @Before
  public void setUp() {
    identifier = generateUuid();
    groupId = identifier;
    accountId = generateUuid();
    projectIdentifier = "harness";
    productName = "Performance monitoring";
    envIdentifier = "production_harness";
    serviceIdentifier = "manager";
  }

  protected void fillCommonFields(DSConfig dsConfig) {
    dsConfig.setAccountId(accountId);
    dsConfig.setConnectorId(connectorId);
    dsConfig.setEnvIdentifier(envIdentifier);
    dsConfig.setIdentifier(identifier);
    dsConfig.setProductName(productName);
    dsConfig.setProjectIdentifier(projectIdentifier);
  }

  protected void fillCommonFields(CVConfig cvConfig) {
    cvConfig.setEnvIdentifier(envIdentifier);
    cvConfig.setConnectorId(connectorId);
    cvConfig.setProductName(productName);
    cvConfig.setProjectIdentifier(projectIdentifier);
    cvConfig.setAccountId(accountId);
    cvConfig.setGroupId(groupId);
    cvConfig.setServiceIdentifier(serviceIdentifier);
  }
}