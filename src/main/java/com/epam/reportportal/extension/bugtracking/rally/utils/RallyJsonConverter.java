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

package com.epam.reportportal.extension.bugtracking.rally.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bridges the Gson {@link JsonElement} types mandated by the {@code rally-rest-api} client's public API with Jackson's
 * {@link JsonMapper}, which is used for all (de)serialization of this plugin's own model classes.
 */
public final class RallyJsonConverter {

  private RallyJsonConverter() {
  }

  public static <T> T fromJson(JsonMapper jsonMapper, JsonElement json, Class<T> type) {
    return jsonMapper.readValue(json.toString(), type);
  }

  public static <T> List<T> fromJsonList(JsonMapper jsonMapper, JsonElement json,
      Class<T> elementType) {
    return jsonMapper.readValue(json.toString(),
        jsonMapper.getTypeFactory().constructCollectionType(List.class, elementType));
  }

  public static JsonObject toJsonObject(JsonMapper jsonMapper, Object value) {
    return JsonParser.parseString(jsonMapper.writeValueAsString(value)).getAsJsonObject();
  }
}
