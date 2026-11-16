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

package com.epam.reportportal.extension.bugtracking.rally.info.impl;

import com.epam.reportportal.base.infrastructure.persistence.entity.integration.IntegrationType;
import com.epam.reportportal.extension.bugtracking.rally.info.PluginInfoProvider;
import java.util.HashMap;
import java.util.Map;

public class PluginInfoProviderImpl implements PluginInfoProvider {

  private static final String DESCRIPTION_KEY = "description";
  private static final String METADATA_KEY = "metadata";
  private static final String PLUGIN_DESCRIPTION =
      "The integration provides an exchange of information between ReportPortal and Rally, "
          + "such as posting issues and linking issues, getting updates on their statuses.";

  private static final Map<String, Object> PLUGIN_METADATA = new HashMap<>();

  static {
    PLUGIN_METADATA.put("embedded", true);
    PLUGIN_METADATA.put("multiple", true);
  }

  @Override
  public IntegrationType provide(IntegrationType integrationType) {
    Map<String, Object> details = integrationType.getDetails().getDetails();
    details.put(DESCRIPTION_KEY, PLUGIN_DESCRIPTION);
    details.put(METADATA_KEY, PLUGIN_METADATA);
    return integrationType;
  }
}
