/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.metrics;

import static io.harness.rule.OwnerRule.TRUNAPUSHPA;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.category.element.UnitTests;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.CEModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
public class CENGTelemetryServiceImplTest {
  @Mock private NgLicenseHttpClient ngLicenseHttpClient;
  @InjectMocks private CENGTelemetryServiceImpl cengTelemetryService;

  private final String ACCOUNT_ID = "some_account_id";
  private final long END_OF_DAY = 1680566400000L; // 4 April 2023 00:00:00
  private final long YEAR_START = 1672531200000L; // 1 January 2023 00:00:00

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    // Set up any mock objects or stub any methods here
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void checkLicenseStartTimeThreeYearsBeforeCurrentYear() {
    when(ngLicenseHttpClient.getAccountLicensesDTO(Mockito.any()))
        .thenAnswer((Answer<Call<ResponseDTO<AccountLicenseDTO>>>) invocation -> {
          Call<ResponseDTO<AccountLicenseDTO>> call = Mockito.mock(Call.class);
          Map<ModuleType, List<ModuleLicenseDTO>> testLicenses = new HashMap<>();
          List<ModuleLicenseDTO> ceModuleLicneseDTOs = new ArrayList<>();
          ceModuleLicneseDTOs.add(CEModuleLicenseDTO.builder()
                                      .moduleType(ModuleType.CE)
                                      .status(LicenseStatus.ACTIVE)
                                      .startTime(1594684800000L) // 14 July 2020 00:00:00
                                      .build());
          testLicenses.put(ModuleType.CE, ceModuleLicneseDTOs);
          when(call.execute())
              .thenReturn(Response.success(
                  ResponseDTO.newResponse(AccountLicenseDTO.builder().allModuleLicenses(testLicenses).build())));
          when(call.clone()).thenReturn(null);
          return call;
        });

    assertEquals(1626220800000L, cengTelemetryService.getLicenseStartTime(ACCOUNT_ID, END_OF_DAY));
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void checkLicenseStartTimeTwoYearsBeforeCurrentYear() {
    when(ngLicenseHttpClient.getAccountLicensesDTO(Mockito.any()))
        .thenAnswer((Answer<Call<ResponseDTO<AccountLicenseDTO>>>) invocation -> {
          Call<ResponseDTO<AccountLicenseDTO>> call = Mockito.mock(Call.class);
          Map<ModuleType, List<ModuleLicenseDTO>> testLicenses = new HashMap<>();
          List<ModuleLicenseDTO> ceModuleLicneseDTOs = new ArrayList<>();
          ceModuleLicneseDTOs.add(CEModuleLicenseDTO.builder()
                                      .moduleType(ModuleType.CE)
                                      .status(LicenseStatus.ACTIVE)
                                      .startTime(1626276463294L) // 14 July 2021 15:27:43.294
                                      .build());
          testLicenses.put(ModuleType.CE, ceModuleLicneseDTOs);
          when(call.execute())
              .thenReturn(Response.success(
                  ResponseDTO.newResponse(AccountLicenseDTO.builder().allModuleLicenses(testLicenses).build())));
          when(call.clone()).thenReturn(null);
          return call;
        });

    assertEquals(1626276463294L, cengTelemetryService.getLicenseStartTime(ACCOUNT_ID, END_OF_DAY));
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void checkAnnualLicenseStartTimeOneYearBeforeCurrentYear() {
    when(ngLicenseHttpClient.getAccountLicensesDTO(Mockito.any()))
        .thenAnswer((Answer<Call<ResponseDTO<AccountLicenseDTO>>>) invocation -> {
          Call<ResponseDTO<AccountLicenseDTO>> call = Mockito.mock(Call.class);
          Map<ModuleType, List<ModuleLicenseDTO>> testLicenses = new HashMap<>();
          List<ModuleLicenseDTO> ceModuleLicneseDTOs = new ArrayList<>();
          ceModuleLicneseDTOs.add(CEModuleLicenseDTO.builder()
                                      .moduleType(ModuleType.CE)
                                      .status(LicenseStatus.ACTIVE)
                                      .startTime(1648798031000L) // 1 April 2022 07:27:11
                                      .build());
          testLicenses.put(ModuleType.CE, ceModuleLicneseDTOs);
          when(call.execute())
              .thenReturn(Response.success(
                  ResponseDTO.newResponse(AccountLicenseDTO.builder().allModuleLicenses(testLicenses).build())));
          when(call.clone()).thenReturn(null);
          return call;
        });

    assertEquals(1648798031000L, cengTelemetryService.getLicenseStartTime(ACCOUNT_ID, END_OF_DAY));
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void checkLicenseStartTimeInCurrentYear() {
    when(ngLicenseHttpClient.getAccountLicensesDTO(Mockito.any()))
        .thenAnswer((Answer<Call<ResponseDTO<AccountLicenseDTO>>>) invocation -> {
          Call<ResponseDTO<AccountLicenseDTO>> call = Mockito.mock(Call.class);
          Map<ModuleType, List<ModuleLicenseDTO>> testLicenses = new HashMap<>();
          List<ModuleLicenseDTO> ceModuleLicneseDTOs = new ArrayList<>();
          ceModuleLicneseDTOs.add(CEModuleLicenseDTO.builder()
                                      .moduleType(ModuleType.CE)
                                      .status(LicenseStatus.ACTIVE)
                                      .startTime(1680307200000L) // 1 April 2023 00:00:00
                                      .build());
          testLicenses.put(ModuleType.CE, ceModuleLicneseDTOs);
          when(call.execute())
              .thenReturn(Response.success(
                  ResponseDTO.newResponse(AccountLicenseDTO.builder().allModuleLicenses(testLicenses).build())));
          when(call.clone()).thenReturn(null);
          return call;
        });

    assertEquals(1680307200000L, cengTelemetryService.getLicenseStartTime(ACCOUNT_ID, END_OF_DAY));
  }
}
