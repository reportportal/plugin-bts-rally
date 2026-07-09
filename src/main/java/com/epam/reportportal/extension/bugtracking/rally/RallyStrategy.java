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

package com.epam.reportportal.extension.bugtracking.rally;

import com.epam.reportportal.base.core.events.domain.PluginUploadedEvent;
import com.epam.reportportal.base.infrastructure.persistence.binary.impl.AttachmentDataStoreService;
import com.epam.reportportal.base.infrastructure.persistence.dao.IntegrationRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.IntegrationTypeRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.LogRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.TestItemRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.TicketRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.filesystem.DataEncoder;
import com.epam.reportportal.extension.CommonPluginCommand;
import com.epam.reportportal.extension.IntegrationGroupEnum;
import com.epam.reportportal.extension.NamedPluginCommand;
import com.epam.reportportal.extension.PluginCommand;
import com.epam.reportportal.extension.ReportPortalExtensionPoint;
import com.epam.reportportal.extension.bugtracking.InternalTicketAssembler;
import com.epam.reportportal.extension.bugtracking.rally.client.RallyClientProvider;
import com.epam.reportportal.extension.bugtracking.rally.command.GetIssueCommand;
import com.epam.reportportal.extension.bugtracking.rally.command.GetIssueFieldsCommand;
import com.epam.reportportal.extension.bugtracking.rally.command.GetIssueTypesCommand;
import com.epam.reportportal.extension.bugtracking.rally.command.PostTicketCommand;
import com.epam.reportportal.extension.bugtracking.rally.command.RetrieveCreationParamsCommand;
import com.epam.reportportal.extension.bugtracking.rally.command.RetrieveUpdateParamsCommand;
import com.epam.reportportal.extension.bugtracking.rally.command.TestConnectionCommand;
import com.epam.reportportal.extension.bugtracking.rally.event.plugin.PluginLoadedEventListener;
import com.epam.reportportal.extension.bugtracking.rally.info.impl.PluginInfoProviderImpl;
import com.epam.reportportal.extension.bugtracking.rally.utils.MemoizingSupplier;
import com.epam.reportportal.extension.command.ExtensionCommand;
import com.epam.reportportal.extension.util.RequestEntityConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jasypt.util.text.BasicTextEncryptor;
import org.pf4j.Extension;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * @author Dzmitry_Kavalets
 */
@Extension
public class RallyStrategy implements ReportPortalExtensionPoint, DisposableBean {

  private static final String DOCUMENTATION_LINK_FIELD = "documentationLink";
  private static final String DOCUMENTATION_LINK = "https://reportportal.io/docs/plugins/Rally";
  private static final String PLUGIN_ID = "Rally";
  private static final String NAME_FIELD = "name";

  private final Supplier<Map<String, ExtensionCommand<?>>> pluginCommandMapping =
      new MemoizingSupplier<>(this::getIntegrationExtensionCommands);
  private final Supplier<Map<String, ExtensionCommand<?>>> commonPluginCommandMapping =
      new MemoizingSupplier<>(this::getCommonExtensionCommands);

  private final Supplier<ObjectMapper> objectMapperSupplier;
  private final Supplier<RequestEntityConverter> requestEntityConverterSupplier;
  private final Supplier<RallyClientProvider> clientProviderSupplier;
  private final Supplier<InternalTicketAssembler> ticketAssemblerSupplier;
  private final Supplier<ApplicationListener<PluginUploadedEvent>> pluginLoadedListenerSupplier;

  @Autowired
  private ApplicationContext applicationContext;
  @Autowired
  private IntegrationTypeRepository integrationTypeRepository;
  @Autowired
  private IntegrationRepository integrationRepository;
  @Autowired
  private TicketRepository ticketRepository;
  @Autowired
  private ProjectRepository projectRepository;
  @Autowired
  private ProjectUserRepository projectUserRepository;
  @Autowired
  private OrganizationRepository organizationRepository;
  @Autowired
  private OrganizationUserRepository organizationUserRepository;
  @Autowired
  private TestItemRepository testItemRepository;
  @Autowired
  private LogRepository logRepository;
  @Autowired
  private AttachmentDataStoreService attachmentDataStoreService;
  @Autowired
  private DataEncoder dataEncoder;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private BasicTextEncryptor encryptor;

  public RallyStrategy() {
    objectMapperSupplier = new MemoizingSupplier<>(() -> objectMapper);
    requestEntityConverterSupplier =
        new MemoizingSupplier<>(() -> new RequestEntityConverter(objectMapper));
    clientProviderSupplier = new MemoizingSupplier<>(() -> new RallyClientProvider(encryptor));
    ticketAssemblerSupplier = new MemoizingSupplier<>(
        () -> new InternalTicketAssembler(logRepository, testItemRepository,
            attachmentDataStoreService, dataEncoder));
    pluginLoadedListenerSupplier = new MemoizingSupplier<>(
        () -> new PluginLoadedEventListener(PLUGIN_ID, integrationTypeRepository,
            integrationRepository, new PluginInfoProviderImpl()));
  }

  @Override
  public Map<String, ?> getPluginParams() {
    Map<String, Object> params = new HashMap<>();
    params.put(ALLOWED_COMMANDS, new ArrayList<>(pluginCommandMapping.get().keySet()));
    params.put(DOCUMENTATION_LINK_FIELD, DOCUMENTATION_LINK);
    params.put(COMMON_COMMANDS, new ArrayList<>(commonPluginCommandMapping.get().keySet()));
    params.put(NAME_FIELD, PLUGIN_ID);
    return params;
  }

  @Override
  public PluginCommand<?> getIntegrationCommand(String commandName) {
    return null;
  }

  @Override
  public CommonPluginCommand<?> getCommonCommand(String commandName) {
    return null;
  }

  @Override
  public IntegrationGroupEnum getIntegrationGroup() {
    return IntegrationGroupEnum.BTS;
  }

  @PostConstruct
  public void createIntegration() {
    initListeners();
  }

  private void initListeners() {
    ApplicationEventMulticaster applicationEventMulticaster = applicationContext.getBean(
        AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
        ApplicationEventMulticaster.class);
    applicationEventMulticaster.addApplicationListener(pluginLoadedListenerSupplier.get());
  }

  @Override
  public void destroy() {
    removeListeners();
  }

  private void removeListeners() {
    ApplicationEventMulticaster applicationEventMulticaster = applicationContext.getBean(
        AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
        ApplicationEventMulticaster.class);
    applicationEventMulticaster.removeApplicationListener(pluginLoadedListenerSupplier.get());
  }

  @Override
  public Map<String, ExtensionCommand<?>> getCommonExtensionCommands() {
    List<ExtensionCommand<?>> commands = new ArrayList<>();
    commands.add(new RetrieveCreationParamsCommand(projectRepository, organizationUserRepository,
        organizationRepository, projectUserRepository));
    commands.add(new RetrieveUpdateParamsCommand(projectRepository, organizationUserRepository,
        organizationRepository, projectUserRepository));
    commands.add(new GetIssueCommand(clientProviderSupplier.get(), ticketRepository,
        integrationRepository, projectRepository, organizationUserRepository, organizationRepository,
        projectUserRepository));
    return commands.stream().collect(Collectors.toMap(NamedPluginCommand::getName, it -> it));
  }

  @Override
  public Map<String, ExtensionCommand<?>> getIntegrationExtensionCommands() {
    List<ExtensionCommand<?>> commands = new ArrayList<>();
    commands.add(new TestConnectionCommand(clientProviderSupplier.get(), projectRepository,
        organizationUserRepository, organizationRepository, projectUserRepository));
    commands.add(new GetIssueTypesCommand(projectRepository, organizationUserRepository,
        organizationRepository, projectUserRepository));
    commands.add(new GetIssueFieldsCommand(clientProviderSupplier.get(), projectRepository,
        organizationUserRepository, organizationRepository, projectUserRepository));
    commands.add(
        new PostTicketCommand(projectRepository, organizationUserRepository, organizationRepository,
            projectUserRepository, clientProviderSupplier.get(),
            requestEntityConverterSupplier.get(), objectMapperSupplier, ticketAssemblerSupplier,
            testItemRepository, attachmentDataStoreService, dataEncoder));
    return commands.stream().collect(Collectors.toMap(NamedPluginCommand::getName, it -> it));
  }
}
