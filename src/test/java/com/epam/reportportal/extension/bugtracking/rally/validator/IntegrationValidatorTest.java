/*
 * Copyright 2024 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.extension.bugtracking.rally.validator;

import com.epam.reportportal.rules.exception.ReportPortalException;
import com.epam.ta.reportportal.entity.integration.Integration;
import com.epam.ta.reportportal.entity.integration.IntegrationParams;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IntegrationValidatorTest {

  @ParameterizedTest
  @CsvSource(value = {
      "rally,https://rally1.rallydev.com",
      "rally,https://rally1.rallydev.com/"
  }, delimiter = ',')
  void validateThirdPartyUrl(String name, String url) {
    Assertions.assertDoesNotThrow(() ->
        IntegrationValidator.validateThirdPartyUrl(getIntegration(url)));
  }

  @ParameterizedTest
  @CsvSource(value = {
      "rally,http://rally1.rallydev.com",
      "rally,https://zloi.hacker.com"
  }, delimiter = ',')
  void validateThirdPartyUrlFailed(String url) {
    Assertions.assertThrows(ReportPortalException.class, () ->
        IntegrationValidator.validateThirdPartyUrl(getIntegration(url)));
  }

  private static Integration getIntegration(String url) {
    Map<String, Object> params = new HashMap<>();
    params.put("url", url);

    IntegrationParams integrationParams = new IntegrationParams();
    integrationParams.setParams(params);

    Integration integration = new Integration();
    integration.setParams(integrationParams);

    return integration;
  }

}
