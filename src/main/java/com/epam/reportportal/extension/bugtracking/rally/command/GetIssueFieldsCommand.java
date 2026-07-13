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

package com.epam.reportportal.extension.bugtracking.rally.command;

import static com.epam.reportportal.base.infrastructure.rules.exception.ErrorType.UNABLE_INTERACT_WITH_INTEGRATION;

import com.epam.reportportal.api.model.PluginCommandRQ;
import com.epam.reportportal.base.infrastructure.model.externalsystem.AllowedValue;
import com.epam.reportportal.base.infrastructure.model.externalsystem.PostFormField;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.entity.integration.Integration;
import com.epam.reportportal.base.infrastructure.persistence.entity.organization.OrganizationRole;
import com.epam.reportportal.base.infrastructure.persistence.entity.project.ProjectRole;
import com.epam.reportportal.base.infrastructure.persistence.entity.user.UserRole;
import com.epam.reportportal.base.infrastructure.rules.exception.ReportPortalException;
import com.epam.reportportal.extension.bugtracking.rally.client.RallyClientProvider;
import com.epam.reportportal.extension.bugtracking.rally.model.AllowedAttributeValue;
import com.epam.reportportal.extension.bugtracking.rally.model.AttributeDefinition;
import com.epam.reportportal.extension.bugtracking.rally.model.RallyConstants;
import com.epam.reportportal.extension.bugtracking.rally.model.TypeDefinition;
import com.epam.reportportal.extension.bugtracking.rally.utils.RallyJsonConverter;
import com.epam.reportportal.extension.command.AbstractExtensionCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.response.QueryResponse;
import com.rallydev.rest.util.Fetch;
import com.rallydev.rest.util.QueryFilter;
import com.rallydev.rest.util.Ref;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetIssueFieldsCommand extends AbstractExtensionCommand<List<PostFormField>> {

  private final RallyClientProvider clientProvider;
  private final Supplier<ObjectMapper> objectMapperSupplier;

  public GetIssueFieldsCommand(RallyClientProvider clientProvider,
      Supplier<ObjectMapper> objectMapperSupplier, ProjectRepository projectRepository,
      OrganizationUserRepository organizationUserRepository,
      OrganizationRepository organizationRepository, ProjectUserRepository projectUserRepository) {
    super(projectRepository, organizationUserRepository, organizationRepository,
        projectUserRepository);
    this.clientProvider = clientProvider;
    this.objectMapperSupplier = objectMapperSupplier;
    this.minProjectRole = ProjectRole.EDITOR;
    this.minOrgRole = OrganizationRole.MANAGER;
    this.minUserRole = UserRole.ADMINISTRATOR;
  }

  @Override
  public String getName() {
    return "getIssueFields";
  }

  @Override
  protected List<PostFormField> invokeCommand(Integration integration,
      PluginCommandRQ pluginCommandRq) {
    try (RallyRestApi restApi = clientProvider.provide(integration.getParams())) {
      List<PostFormField> fields = new ArrayList<>();
      List<AttributeDefinition> attributeDefinitions = findDefectAttributeDefinitions(restApi);
      for (AttributeDefinition attributeDefinition : attributeDefinitions) {
        if (!attributeDefinition.isReadOnly()) {
          PostFormField postFormField = new PostFormField();
          if (attributeDefinition.getAllowedValue().getCount() > 0) {
            List<AllowedValue> definedValues = new ArrayList<>();
            for (AllowedAttributeValue allowedAttributeValue : findAllowedAttributeValues(restApi,
                attributeDefinition)) {
              if (allowedAttributeValue.getStringValue() != null
                  && !allowedAttributeValue.getStringValue().isEmpty()) {
                AllowedValue allowedValue = new AllowedValue();
                allowedValue.setValueName(allowedAttributeValue.getStringValue());
                if (!"null".equals(allowedAttributeValue.getRef())) {
                  allowedValue.setValueId(Ref.getRelativeRef(allowedAttributeValue.getRef()));
                }
                definedValues.add(allowedValue);
              }
            }
            postFormField.setDefinedValues(definedValues);
          }
          postFormField.setId(attributeDefinition.getElementName());
          postFormField.setFieldName(attributeDefinition.getName());
          postFormField.setIsRequired(attributeDefinition.isRequired());
          postFormField.setFieldType(attributeDefinition.getType());
          fields.add(postFormField);
        }
      }
      return fields;
    } catch (IOException e) {
      log.error("Unable to load ticket fields: {}", e.getMessage(), e);
      throw new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION,
          "Unable to load ticket fields: " + e.getMessage(), e);
    }
  }

  private List<AttributeDefinition> findDefectAttributeDefinitions(RallyRestApi restApi)
      throws IOException {
    QueryRequest typeDefRequest = new QueryRequest(RallyConstants.TYPE_DEFINITION);
    typeDefRequest.setFetch(new Fetch(RallyConstants.OBJECT_ID, RallyConstants.ATTRIBUTES));
    typeDefRequest.setQueryFilter(new QueryFilter(RallyConstants.NAME, "=", RallyConstants.DEFECT));
    QueryResponse typeDefQueryResponse = restApi.query(typeDefRequest);
    JsonObject typeDefJsonObject = typeDefQueryResponse.getResults().get(0).getAsJsonObject();
    ObjectMapper objectMapper = objectMapperSupplier.get();
    TypeDefinition typeDefinition =
        RallyJsonConverter.fromJson(objectMapper, typeDefJsonObject, TypeDefinition.class);
    QueryRequest attributeRequest = new QueryRequest(
        RallyJsonConverter.toJsonObject(objectMapper, typeDefinition.getAttributeDefinition()));
    attributeRequest.setFetch(new Fetch(RallyConstants.ALLOWED_VALUES, RallyConstants.ELEMENT_NAME,
        RallyConstants.NAME, RallyConstants.REQUIRED, RallyConstants.TYPE, RallyConstants.OBJECT_ID,
        RallyConstants.READ_ONLY));
    QueryResponse attributesQueryResponse = restApi.query(attributeRequest);
    return RallyJsonConverter.fromJsonList(objectMapper, attributesQueryResponse.getResults(),
        AttributeDefinition.class);
  }

  private List<AllowedAttributeValue> findAllowedAttributeValues(RallyRestApi restApi,
      AttributeDefinition attributeDefinition) throws IOException {
    ObjectMapper objectMapper = objectMapperSupplier.get();
    QueryRequest allowedValuesRequest = new QueryRequest(
        RallyJsonConverter.toJsonObject(objectMapper, attributeDefinition.getAllowedValue()));
    allowedValuesRequest.setFetch(new Fetch(RallyConstants.STRING_VALUE));
    QueryResponse allowedValuesResponse = restApi.query(allowedValuesRequest);
    return RallyJsonConverter.fromJsonList(objectMapper, allowedValuesResponse.getResults(),
        AllowedAttributeValue.class);
  }
}
