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

import static com.epam.reportportal.base.infrastructure.rules.commons.validation.Suppliers.formattedSupplier;
import static com.epam.reportportal.base.infrastructure.rules.exception.ErrorType.UNABLE_INTERACT_WITH_INTEGRATION;
import static com.epam.reportportal.base.infrastructure.rules.exception.ErrorType.UNABLE_TO_LOAD_BINARY_DATA;
import static com.epam.reportportal.extension.util.CommandParamUtils.ENTITY_PARAM;
import static java.util.Optional.ofNullable;

import com.epam.reportportal.api.model.PluginCommandRQ;
import com.epam.reportportal.base.infrastructure.commons.template.TemplateEngine;
import com.epam.reportportal.base.infrastructure.commons.template.TemplateEngineProvider;
import com.epam.reportportal.base.infrastructure.model.externalsystem.AllowedValue;
import com.epam.reportportal.base.infrastructure.model.externalsystem.PostFormField;
import com.epam.reportportal.base.infrastructure.model.externalsystem.PostTicketRQ;
import com.epam.reportportal.base.infrastructure.model.externalsystem.Ticket;
import com.epam.reportportal.base.infrastructure.persistence.binary.impl.AttachmentDataStoreService;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.TestItemRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.entity.integration.Integration;
import com.epam.reportportal.base.infrastructure.persistence.entity.item.TestItem;
import com.epam.reportportal.base.infrastructure.persistence.entity.organization.OrganizationRole;
import com.epam.reportportal.base.infrastructure.persistence.entity.project.ProjectRole;
import com.epam.reportportal.base.infrastructure.persistence.entity.user.UserRole;
import com.epam.reportportal.base.infrastructure.persistence.filesystem.DataEncoder;
import com.epam.reportportal.base.infrastructure.rules.exception.ReportPortalException;
import com.epam.reportportal.extension.bugtracking.BtsActivityPublisher;
import com.epam.reportportal.extension.bugtracking.BtsConstants;
import com.epam.reportportal.extension.bugtracking.InternalTicket;
import com.epam.reportportal.extension.bugtracking.InternalTicketAssembler;
import com.epam.reportportal.extension.bugtracking.rally.client.RallyClientProvider;
import com.epam.reportportal.extension.bugtracking.rally.model.Defect;
import com.epam.reportportal.extension.bugtracking.rally.model.RallyConstants;
import com.epam.reportportal.extension.bugtracking.rally.model.RallyObject;
import com.epam.reportportal.extension.bugtracking.rally.utils.RallyJsonConverter;
import com.epam.reportportal.extension.bugtracking.rally.utils.RallyTicketConverter;
import com.epam.reportportal.extension.command.AbstractExtensionCommand;
import com.epam.reportportal.extension.util.FileNameExtractor;
import com.epam.reportportal.extension.util.RequestEntityConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.CreateRequest;
import com.rallydev.rest.request.UpdateRequest;
import com.rallydev.rest.response.CreateResponse;
import com.rallydev.rest.response.Response;
import com.rallydev.rest.response.UpdateResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.CollectionUtils;

@Slf4j
public class PostTicketCommand extends AbstractExtensionCommand<Ticket> {

  private static final String BUG_TEMPLATE_PATH = "bug_template.ftl";

  private final RallyClientProvider clientProvider;
  private final RequestEntityConverter requestEntityConverter;
  private final Supplier<ObjectMapper> objectMapperSupplier;
  private final Supplier<InternalTicketAssembler> ticketAssemblerSupplier;
  private final TestItemRepository testItemRepository;
  private final AttachmentDataStoreService attachmentDataStoreService;
  private final DataEncoder dataEncoder;
  private final BtsActivityPublisher btsActivityPublisher;
  private final TemplateEngine templateEngine = new TemplateEngineProvider().get();

  public PostTicketCommand(ProjectRepository projectRepository,
      OrganizationUserRepository organizationUserRepository,
      OrganizationRepository organizationRepository, ProjectUserRepository projectUserRepository,
      RallyClientProvider clientProvider,
      RequestEntityConverter requestEntityConverter,
      Supplier<ObjectMapper> objectMapperSupplier,
      Supplier<InternalTicketAssembler> ticketAssemblerSupplier,
      TestItemRepository testItemRepository,
      AttachmentDataStoreService attachmentDataStoreService,
      DataEncoder dataEncoder,
      BtsActivityPublisher btsActivityPublisher) {
    super(projectRepository, organizationUserRepository, organizationRepository, projectUserRepository);
    this.clientProvider = clientProvider;
    this.requestEntityConverter = requestEntityConverter;
    this.objectMapperSupplier = objectMapperSupplier;
    this.ticketAssemblerSupplier = ticketAssemblerSupplier;
    this.testItemRepository = testItemRepository;
    this.attachmentDataStoreService = attachmentDataStoreService;
    this.dataEncoder = dataEncoder;
    this.btsActivityPublisher = btsActivityPublisher;
    this.minProjectRole = ProjectRole.EDITOR;
    this.minOrgRole = OrganizationRole.MANAGER;
    this.minUserRole = UserRole.ADMINISTRATOR;
  }

  @Override
  public String getName() {
    return "postTicket";
  }

  @Override
  protected Ticket invokeCommand(Integration integration, PluginCommandRQ pluginCommandRq) {
    PostTicketRQ ticketRQ =
        requestEntityConverter.getEntity(ENTITY_PARAM, pluginCommandRq.getArguments(),
            PostTicketRQ.class);

    try (RallyRestApi restApi = clientProvider.provide(integration.getParams())) {
      List<InternalTicket.LogEntry> logs = ofNullable(
          ticketAssemblerSupplier.get().apply(ticketRQ).getLogs()).orElseGet(Lists::newArrayList);

      Defect newDefect = postDefect(restApi, ticketRQ, integration);
      String description = newDefect.getDescription();

      Map<String, String> attachments = new HashMap<>();
      logs.stream().filter(InternalTicket.LogEntry::isHasAttachment).forEach(
          entry -> attachments.put(entry.getDecodedFileName(),
              String.valueOf(postImage(newDefect.getRef(), entry, restApi).getObjectId())));

      for (Map.Entry<String, String> binaryDataEntry : attachments.entrySet()) {
        description = description.replace(binaryDataEntry.getKey(),
            "/slm/attachment/" + binaryDataEntry.getValue() + "/" + binaryDataEntry.getKey());
      }
      updateDescription(description, newDefect, restApi);
      Ticket ticket = RallyTicketConverter.toTicket(newDefect, integration);
      btsActivityPublisher.publishTicketPostedEvent(ticket, ticketRQ, pluginCommandRq.getContext(),
          integration);
      return ticket;
    } catch (ReportPortalException rpe) {
      throw rpe;
    } catch (Exception e) {
      log.error("Unable to submit ticket: {}", e.getMessage(), e);
      throw new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, "Unable to submit ticket");
    }
  }

  private Defect postDefect(RallyRestApi restApi, PostTicketRQ ticketRQ, Integration integration) {
    JsonObject newDefect = new JsonObject();
    List<PostFormField> fields = ticketRQ.getFields();
    List<PostFormField> savedFields = new ArrayList<>();
    BtsConstants.DEFECT_FORM_FIELDS.getParam(integration.getParams())
        .ifPresent(integrationFields -> {
          try {
            ObjectMapper mapper = objectMapperSupplier.get();
            savedFields.addAll(
                mapper.readValue(mapper.writeValueAsBytes(integrationFields),
                    mapper.getTypeFactory()
                        .constructParametricType(List.class, PostFormField.class)));
          } catch (IOException e) {
            log.error("Unable to parse post form fields: {}", e.getMessage());
            throw new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, e);
          }
        });

    for (PostFormField field : fields) {
      if (CollectionUtils.isNotEmpty(field.getValue())) {
        String value = field.getValue().get(0);
        for (PostFormField savedField : savedFields) {
          if (savedField.getId().equalsIgnoreCase(field.getId())) {
            List<AllowedValue> definedValues = savedField.getDefinedValues();
            if (definedValues != null) {
              for (AllowedValue definedValue : definedValues) {
                if (definedValue.getValueName().equals(field.getValue().get(0))
                    && definedValue.getValueId() != null) {
                  value = definedValue.getValueId();
                }
              }
            }
          }
        }
        newDefect.addProperty(field.getId(), value);
      }
    }

    List<InternalTicket.LogEntry> logs = ofNullable(ticketAssemblerSupplier.get().apply(ticketRQ).getLogs())
        .orElseGet(Lists::newArrayList);
    String description = createDescription(ticketRQ, logs);
    newDefect.addProperty(RallyConstants.DESCRIPTION,
        newDefect.get(RallyConstants.DESCRIPTION) != null
            ? (newDefect.get(RallyConstants.DESCRIPTION).getAsString() + "<br>" + description)
            : description);

    CreateRequest createRequest = new CreateRequest(RallyConstants.DEFECT, newDefect);
    try {
      CreateResponse createResponse = restApi.create(createRequest);
      checkResponse(createResponse);
      return RallyJsonConverter.fromJson(objectMapperSupplier.get(), createResponse.getObject(),
          Defect.class);
    } catch (ReportPortalException rpe) {
      throw rpe;
    } catch (Exception e) {
      log.error("Errored request: {} - {}", createRequest.getBody(), e.getMessage(), e);
      throw new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION,
          "Errored request: " + createRequest.getBody() + " - " + e.getMessage());
    }
  }

  private RallyObject postImage(String itemRef, InternalTicket.LogEntry logEntry,
      RallyRestApi restApi) {
    Optional<InputStream> fileOptional = attachmentDataStoreService.load(logEntry.getFileId());
    if (fileOptional.isPresent()) {
      try (InputStream file = fileOptional.get()) {
        byte[] bytes = ByteStreams.toByteArray(file);
        JsonObject attach = new JsonObject();
        attach.addProperty(RallyConstants.CONTENT, Base64.encodeBase64String(bytes));
        CreateResponse attachmentContentResponse =
            restApi.create(new CreateRequest(RallyConstants.ATTACHMENT_CONTENT, attach));
        JsonObject attachmentObject = new JsonObject();
        attachmentObject.addProperty(RallyConstants.ARTIFACT, itemRef);
        attachmentObject.addProperty(RallyConstants.CONTENT,
            attachmentContentResponse.getObject().get(RallyConstants.REF).getAsString());
        attachmentObject.addProperty(RallyConstants.NAME,
            FileNameExtractor.extractFileName(dataEncoder, logEntry.getFileId()));
        attachmentObject.addProperty(RallyConstants.DESCRIPTION, logEntry.getFileId());
        attachmentObject.addProperty(RallyConstants.CONTENT_TYPE, logEntry.getContentType());
        attachmentObject.addProperty(RallyConstants.SIZE, bytes.length);
        CreateResponse attachmentResponse =
            restApi.create(new CreateRequest(RallyConstants.ATTACHMENT, attachmentObject));
        checkResponse(attachmentResponse);
        return RallyJsonConverter.fromJson(objectMapperSupplier.get(),
            attachmentResponse.getObject(), RallyObject.class);
      } catch (IOException e) {
        log.error("Unable to post ticket image: {}\n{}", e.getMessage(),
            Arrays.toString(e.getStackTrace()), e);
        throw new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION,
            "Unable to post ticket image: " + e.getMessage(), e);
      }
    } else {
      throw new ReportPortalException(UNABLE_TO_LOAD_BINARY_DATA);
    }
  }

  private void updateDescription(String description, Defect defect, RallyRestApi restApi)
      throws IOException {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty(RallyConstants.DESCRIPTION, description);
    UpdateRequest updateRequest = new UpdateRequest(defect.getRef(), jsonObject);
    UpdateResponse update = restApi.update(updateRequest);
    checkResponse(update);
    RallyJsonConverter.fromJson(objectMapperSupplier.get(), update.getObject(), Defect.class);
  }

  private String createDescription(PostTicketRQ ticketRQ, List<InternalTicket.LogEntry> itemLogs) {
    TestItem testItem = testItemRepository.findById(ticketRQ.getTestItemId()).orElseThrow(
        () -> new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION,
            formattedSupplier("Test item {} not found", ticketRQ.getTestItemId())));
    HashMap<Object, Object> templateData = new HashMap<>();
    if (ticketRQ.getIsIncludeComments()) {
      ofNullable(testItem.getItemResults().getIssue()).ifPresent(
          issue -> templateData.put("comments", issue.getIssueDescription()));
    }
    if (ticketRQ.getBackLinks() != null) {
      templateData.put("backLinks", ticketRQ.getBackLinks());
    }
    if (itemLogs != null && (ticketRQ.getIsIncludeLogs() || ticketRQ.getIsIncludeScreenshots())) {
      templateData.put("logs", itemLogs);
    }
    return templateEngine.merge(BUG_TEMPLATE_PATH, templateData);
  }

  private void checkResponse(Response response) {
    if (response.getErrors().length > 0) {
      throw new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION,
          "Error during interacting with Rally: " + String.join(" ", response.getErrors()));
    }
  }
}
