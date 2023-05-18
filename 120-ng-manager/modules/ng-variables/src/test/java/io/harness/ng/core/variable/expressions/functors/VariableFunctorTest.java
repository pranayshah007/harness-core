/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.expressions.functors;

import static io.harness.rule.OwnerRule.NISHANT;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.variable.VariableValueType;
import io.harness.ng.core.variable.dto.StringVariableConfigDTO;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;
import io.harness.ng.core.variable.services.VariableService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

public class VariableFunctorTest extends CategoryTest {
  @Mock private VariableService variableService;
  @InjectMocks private VariableFunctor variableFunctor;
  private MockedStatic<AmbianceUtils> ambianceUtilsMockedStatic;

  @Captor public ArgumentCaptor<String> accountIdentifierCaptor;
  @Captor public ArgumentCaptor<String> orgIdentifierCaptor;
  @Captor public ArgumentCaptor<String> projectIdentifierCaptor;
  @Captor public ArgumentCaptor<String> variableIdentifierCaptor;
  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ambianceUtilsMockedStatic = Mockito.mockStatic(AmbianceUtils.class);
  }

  @After
  public void cleanup() {
    ambianceUtilsMockedStatic.close();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  @PrepareForTest(AmbianceUtils.class)
  public void testGet_getVariableValueMapAccount() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String variableIdentifier = randomAlphabetic(5);
    String fixedValue = randomAlphabetic(10);

    when(AmbianceUtils.getNgAccess(any()))
        .thenAnswer(invocationOnMock -> getNgAccess(accountIdentifier, orgIdentifier, projectIdentifier));
    when(variableService.list(any(), any(), any()))
        .thenReturn(Collections.singletonList(getVariableDTO(variableIdentifier, fixedValue)));
    Object variableMap = variableFunctor.get(Ambiance.newBuilder().build(), "account");
    verify(variableService, times(1))
        .list(accountIdentifierCaptor.capture(), orgIdentifierCaptor.capture(), projectIdentifierCaptor.capture());
    assertThat(accountIdentifierCaptor.getValue()).isEqualTo(accountIdentifier);
    assertThat(orgIdentifierCaptor.getValue()).isNull();
    assertThat(projectIdentifierCaptor.getValue()).isNull();
    Map<String, Object> expectedMap = new HashMap<>();
    expectedMap.put(variableIdentifier, fixedValue);
    assertThat(variableMap).isEqualTo(expectedMap);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  @PrepareForTest(AmbianceUtils.class)
  public void testGet_getVariableValueMapOrg() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String variableIdentifier = randomAlphabetic(5);
    String fixedValue = randomAlphabetic(10);

    when(AmbianceUtils.getNgAccess(any()))
        .thenAnswer(invocationOnMock -> getNgAccess(accountIdentifier, orgIdentifier, projectIdentifier));
    when(variableService.list(any(), any(), any()))
        .thenReturn(Collections.singletonList(getVariableDTO(variableIdentifier, fixedValue)));
    Object variableMap = variableFunctor.get(Ambiance.newBuilder().build(), "org");
    verify(variableService, times(1))
        .list(accountIdentifierCaptor.capture(), orgIdentifierCaptor.capture(), projectIdentifierCaptor.capture());
    assertThat(accountIdentifierCaptor.getValue()).isEqualTo(accountIdentifier);
    assertThat(orgIdentifierCaptor.getValue()).isEqualTo(orgIdentifier);
    assertThat(projectIdentifierCaptor.getValue()).isNull();
    Map<String, Object> expectedMap = new HashMap<>();
    expectedMap.put(variableIdentifier, fixedValue);
    assertThat(variableMap).isEqualTo(expectedMap);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  @PrepareForTest(AmbianceUtils.class)
  public void testGet_getVariableValueAccount() {
    String accountIdentifier = randomAlphabetic(10);
    String variableIdentifier = randomAlphabetic(5);
    String fixedValue = randomAlphabetic(10);

    when(AmbianceUtils.getNgAccess(any())).thenAnswer(invocationOnMock -> getNgAccess(accountIdentifier, null, null));
    when(variableService.get(any(), any(), any(), anyString()))
        .thenReturn(Optional.ofNullable(getVariableResponseDTO(variableIdentifier, fixedValue)));
    Object variableValue = variableFunctor.get(Ambiance.newBuilder().build(), variableIdentifier);
    verify(variableService, times(1))
        .get(accountIdentifierCaptor.capture(), orgIdentifierCaptor.capture(), projectIdentifierCaptor.capture(),
            variableIdentifierCaptor.capture());
    assertThat(variableIdentifierCaptor.getValue()).isEqualTo(variableIdentifier);
    assertThat(accountIdentifierCaptor.getValue()).isEqualTo(accountIdentifier);
    assertThat(orgIdentifierCaptor.getValue()).isNull();
    assertThat(projectIdentifierCaptor.getValue()).isNull();
    assertThat(variableValue).isEqualTo(fixedValue);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  @PrepareForTest(AmbianceUtils.class)
  public void testGet_getVariableValueOrg() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String variableIdentifier = randomAlphabetic(5);
    String fixedValue = randomAlphabetic(10);

    when(AmbianceUtils.getNgAccess(any()))
        .thenAnswer(invocationOnMock -> getNgAccess(accountIdentifier, orgIdentifier, null));
    when(variableService.get(any(), any(), any(), anyString()))
        .thenReturn(Optional.ofNullable(getVariableResponseDTO(variableIdentifier, fixedValue)));
    Object variableValue = variableFunctor.get(Ambiance.newBuilder().build(), variableIdentifier);
    verify(variableService, times(1))
        .get(accountIdentifierCaptor.capture(), orgIdentifierCaptor.capture(), projectIdentifierCaptor.capture(),
            variableIdentifierCaptor.capture());
    assertThat(variableIdentifierCaptor.getValue()).isEqualTo(variableIdentifier);
    assertThat(accountIdentifierCaptor.getValue()).isEqualTo(accountIdentifier);
    assertThat(orgIdentifierCaptor.getValue()).isEqualTo(orgIdentifier);
    assertThat(projectIdentifierCaptor.getValue()).isNull();
    assertThat(variableValue).isEqualTo(fixedValue);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  @PrepareForTest(AmbianceUtils.class)
  public void testGet_getVariableValueProject() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String variableIdentifier = randomAlphabetic(5);
    String fixedValue = randomAlphabetic(10);

    when(AmbianceUtils.getNgAccess(any()))
        .thenAnswer(invocationOnMock -> getNgAccess(accountIdentifier, orgIdentifier, projectIdentifier));
    when(variableService.get(any(), any(), any(), anyString()))
        .thenReturn(Optional.ofNullable(getVariableResponseDTO(variableIdentifier, fixedValue)));
    Object variableValue = variableFunctor.get(Ambiance.newBuilder().build(), variableIdentifier);
    verify(variableService, times(1))
        .get(accountIdentifierCaptor.capture(), orgIdentifierCaptor.capture(), projectIdentifierCaptor.capture(),
            variableIdentifierCaptor.capture());
    assertThat(variableIdentifierCaptor.getValue()).isEqualTo(variableIdentifier);
    assertThat(accountIdentifierCaptor.getValue()).isEqualTo(accountIdentifier);
    assertThat(orgIdentifierCaptor.getValue()).isEqualTo(orgIdentifier);
    assertThat(projectIdentifierCaptor.getValue()).isEqualTo(projectIdentifier);
    assertThat(variableValue).isEqualTo(fixedValue);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  @PrepareForTest(AmbianceUtils.class)
  public void testGet_getVariableValueNotFound() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String variableIdentifier = randomAlphabetic(5);

    when(AmbianceUtils.getNgAccess(any()))
        .thenAnswer(invocationOnMock -> getNgAccess(accountIdentifier, orgIdentifier, projectIdentifier));
    when(variableService.get(any(), any(), any(), anyString())).thenReturn(Optional.empty());
    exceptionRule.expect(NotFoundException.class);
    exceptionRule.expectMessage(
        String.format("Variable with identifier [%s] not found in scope [%s]", variableIdentifier, ScopeLevel.PROJECT));
    variableFunctor.get(Ambiance.newBuilder().build(), variableIdentifier);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  @PrepareForTest(AmbianceUtils.class)
  public void testGet_getVariableValueNotFoundForNullVariable() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String variableIdentifier = randomAlphabetic(5);

    when(AmbianceUtils.getNgAccess(any()))
        .thenAnswer(invocationOnMock -> getNgAccess(accountIdentifier, orgIdentifier, projectIdentifier));
    when(variableService.get(any(), any(), any(), anyString()))
        .thenReturn(Optional.ofNullable(VariableResponseDTO.builder().build()));
    exceptionRule.expect(NotFoundException.class);
    exceptionRule.expectMessage(
        String.format("Variable with identifier [%s] not found in scope [%s]", variableIdentifier, ScopeLevel.PROJECT));
    variableFunctor.get(Ambiance.newBuilder().build(), variableIdentifier);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  @PrepareForTest(AmbianceUtils.class)
  public void testGet_getVariableValueNotFoundForNullVariableConfig() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String variableIdentifier = randomAlphabetic(5);

    when(AmbianceUtils.getNgAccess(any()))
        .thenAnswer(invocationOnMock -> getNgAccess(accountIdentifier, orgIdentifier, projectIdentifier));
    when(variableService.get(any(), any(), any(), anyString()))
        .thenReturn(Optional.ofNullable(VariableResponseDTO.builder().variable(VariableDTO.builder().build()).build()));
    exceptionRule.expect(NotFoundException.class);
    exceptionRule.expectMessage(
        String.format("Variable with identifier [%s] not found in scope [%s]", variableIdentifier, ScopeLevel.PROJECT));
    variableFunctor.get(Ambiance.newBuilder().build(), variableIdentifier);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  @PrepareForTest(AmbianceUtils.class)
  public void testGet_failValidateAccess() {
    String accountIdentifier = randomAlphabetic(10);

    when(AmbianceUtils.getNgAccess(any())).thenAnswer(invocationOnMock -> getNgAccess(accountIdentifier, null, null));
    exceptionRule.expect(InvalidArgumentsException.class);
    exceptionRule.expectMessage(
        String.format("Variable of %s scope cannot be used at %s scope", ScopeLevel.ORGANIZATION, ScopeLevel.ACCOUNT));
    variableFunctor.get(Ambiance.newBuilder().build(), "org");
  }

  public NGAccess getNgAccess(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  public VariableDTO getVariableDTO(String identifier, String value) {
    return VariableDTO.builder()
        .identifier(identifier)
        .variableConfig(StringVariableConfigDTO.builder().fixedValue(value).valueType(VariableValueType.FIXED).build())
        .build();
  }

  public VariableResponseDTO getVariableResponseDTO(String identifier, String value) {
    return VariableResponseDTO.builder().variable(getVariableDTO(identifier, value)).build();
  }
}
