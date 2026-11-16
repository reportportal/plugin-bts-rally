/*
 * Copyright 2018 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.extension.bugtracking.rally.client;

import static com.epam.reportportal.base.infrastructure.rules.exception.ErrorType.UNABLE_INTERACT_WITH_INTEGRATION;

import com.epam.reportportal.base.infrastructure.persistence.entity.integration.IntegrationParams;
import com.epam.reportportal.base.infrastructure.rules.exception.ReportPortalException;
import com.epam.reportportal.extension.bugtracking.BtsConstants;
import com.rallydev.rest.RallyRestApi;
import java.net.URI;
import java.net.URISyntaxException;
import org.jasypt.util.text.BasicTextEncryptor;

public class RallyClientProvider {

  private final BasicTextEncryptor encryptor;

  public RallyClientProvider(BasicTextEncryptor encryptor) {
    this.encryptor = encryptor;
  }

  public RallyRestApi provide(IntegrationParams params) {
    String url = BtsConstants.URL.getParam(params, String.class).orElseThrow(
        () -> new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, "Rally URL value cannot be NULL")
    );
    String apiKey = encryptor.decrypt(
        BtsConstants.OAUTH_ACCESS_KEY.getParam(params, String.class).orElseThrow(
            () -> new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, "OAUTH key cannot be NULL")
        ));
    try {
      return new RallyRestApi(new URI(url), apiKey);
    } catch (URISyntaxException e) {
      throw new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, "Invalid Rally URL: " + url);
    }
  }
}
