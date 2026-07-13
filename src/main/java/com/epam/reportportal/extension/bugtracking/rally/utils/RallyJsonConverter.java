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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.List;

/**
 * Bridges the Gson {@link JsonElement} types mandated by the {@code rally-rest-api} client's public API with Jackson's
 * {@link ObjectMapper}, which is used for all (de)serialization of this plugin's own model classes.
 */
public final class RallyJsonConverter {

  private RallyJsonConverter() {
  }

  public static <T> T fromJson(ObjectMapper objectMapper, JsonElement json, Class<T> type)
      throws IOException {
    return objectMapper.readValue(json.toString(), type);
  }

  public static <T> List<T> fromJsonList(ObjectMapper objectMapper, JsonElement json,
      Class<T> elementType) throws IOException {
    return objectMapper.readValue(json.toString(),
        objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
  }

  public static JsonObject toJsonObject(ObjectMapper objectMapper, Object value)
      throws IOException {
    return JsonParser.parseString(objectMapper.writeValueAsString(value)).getAsJsonObject();
  }
}
