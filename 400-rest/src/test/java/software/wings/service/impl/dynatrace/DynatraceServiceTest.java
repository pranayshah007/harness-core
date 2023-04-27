/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.dynatrace;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.DynaTraceConfig;
import software.wings.beans.SettingAttribute;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.dynatrace.DynaTraceDelegateService;
import software.wings.service.intfc.dynatrace.DynaTraceService;
import software.wings.service.intfc.security.SecretManager;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @author Praveen
 */
public class DynatraceServiceTest extends CategoryTest {
  @Mock private SettingsService mockSettingsService;
  @Mock private DelegateProxyFactory mockDelegateProxyFactory;
  @Mock private DynaTraceDelegateService dynatraceDelegateService;
  @Mock private SecretManager mockSecretManager;

  @InjectMocks DynaTraceService service = new DynaTraceServiceImpl();

  private String dynatraceSettingId;

  @Before
  public void setup() throws IllegalAccessException {
    dynatraceSettingId = generateUuid();
    MockitoAnnotations.initMocks(this);

    DynaTraceConfig dynaTraceConfig =
        DynaTraceConfig.builder().dynaTraceUrl("dummyURL").apiToken("apiToken".toCharArray()).build();
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(dynaTraceConfig);

    when(mockSettingsService.get(dynatraceSettingId)).thenReturn(attribute);
    when(mockDelegateProxyFactory.getV2(any(), any())).thenReturn(dynatraceDelegateService);
    when(dynatraceDelegateService.getServices(any(), any(), any(), any())).thenReturn(getDynatraceServiceList());
  }

  private List<DynaTraceApplication> getDynatraceServiceList() {
    List<DynaTraceApplication> services = new ArrayList<>();
    services.add(DynaTraceApplication.builder().displayName("serviceName1").entityId("entityID1").build());
    services.add(DynaTraceApplication.builder().displayName("serviceName2").entityId("entityID2").build());
    services.add(DynaTraceApplication.builder().displayName("serviceName3").entityId("entityID3").build());
    return services;
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetServices_happyCase() {
    List<DynaTraceApplication> serviceList = service.getServices(dynatraceSettingId, true);
    assertThat(serviceList).isNotEmpty();
    assertThat(serviceList.containsAll(getDynatraceServiceList())).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetServices_emptySettingId() {
    List<DynaTraceApplication> serviceList = service.getServices(null, true);
    assertThat(serviceList).isNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testResolveServiceNameToID_happyCase() {
    String entityID = service.resolveDynatraceServiceNameToId(dynatraceSettingId, "serviceName3");
    assertThat(entityID).isEqualTo("entityID3");
  }

  @Test(expected = DataCollectionException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testResolveServiceNameToID_badServiceName() {
    String entityID = service.resolveDynatraceServiceNameToId(dynatraceSettingId, "serviceName5");
    assertThat(entityID).isNull();
  }

  @Test(expected = DataCollectionException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testResolveServiceNameToID_badSettingId() {
    String entityID = service.resolveDynatraceServiceNameToId(null, "serviceName5");
    assertThat(entityID).isNull();
  }

  @Test(expected = DataCollectionException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testResolveServiceNameToID_emptyName() {
    String entityID = service.resolveDynatraceServiceNameToId(dynatraceSettingId, "");
    assertThat(entityID).isNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateServiceID_happyCase() {
    boolean isValid = service.validateDynatraceServiceId(dynatraceSettingId, "entityID3");
    assertThat(isValid).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateServiceID_invalidID() {
    boolean isValid = service.validateDynatraceServiceId(dynatraceSettingId, "entityID13");
    assertThat(isValid).isFalse();
  }
}
