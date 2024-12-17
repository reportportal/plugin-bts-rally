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

import com.epam.reportportal.rules.commons.validation.BusinessRule;
import com.epam.reportportal.rules.commons.validation.Suppliers;
import com.epam.reportportal.rules.exception.ErrorType;
import com.epam.ta.reportportal.commons.Predicates;
import com.epam.ta.reportportal.entity.integration.Integration;

/**
 * @author <a href="mailto:siarhei_hrabko@epam.com">Siarhei Hrabko</a>
 */
public final class IntegrationValidator {

  private static final String RALLY_BASE_URL = "https://rally1.rallydev.com";

  private IntegrationValidator() {
    //static only
  }


  /**
   * Validates Validates Rally server url.
   *
   * @param integration {@link Integration}
   */
  public static void validateThirdPartyUrl(Integration integration) {
    var valid = String.valueOf(integration.getParams().getParams().get("url"))
        .startsWith(RALLY_BASE_URL);

    BusinessRule.expect(valid, Predicates.equalTo(true))
        .verify(ErrorType.BAD_REQUEST_ERROR,
            Suppliers.formattedSupplier("Integration url is not acceptable")
        );
  }
}
