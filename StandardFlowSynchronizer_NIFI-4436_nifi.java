public class StandardFlowSynchronizer {
    @Override
    public void sync(final FlowController controller, final DataFlow proposedFlow, final StringEncryptor encryptor)
            throws FlowSerializationException, UninheritableFlowException, FlowSynchronizationException, MissingBundleException {

        // handle corner cases involving no proposed flow
        if (proposedFlow == null) {
            if (controller.getGroup(controller.getRootGroupId()).isEmpty()) {
                return;  // no sync to perform
            } else {
                throw new UninheritableFlowException("Proposed configuration is empty, but the controller contains a data flow.");
            }
        }

        // determine if the controller already had flow sync'd to it
        final boolean flowAlreadySynchronized = controller.isFlowSynchronized();
        logger.debug("Synching FlowController with proposed flow: Controller is Already Synchronized = {}", flowAlreadySynchronized);

        // serialize controller state to bytes
        final byte[] existingFlow;
        final boolean existingFlowEmpty;
        try {
            if (flowAlreadySynchronized) {
                existingFlow = toBytes(controller);
                existingFlowEmpty = controller.getGroup(controller.getRootGroupId()).isEmpty()
                    && controller.getAllReportingTasks().isEmpty()
                    && controller.getAllControllerServices().isEmpty()
                    && controller.getFlowRegistryClient().getRegistryIdentifiers().isEmpty();
            } else {
                existingFlow = readFlowFromDisk();
                if (existingFlow == null || existingFlow.length == 0) {
                    existingFlowEmpty = true;
                } else {
                    final Document document = parseFlowBytes(existingFlow);
                    final Element rootElement = document.getDocumentElement();
                    final FlowEncodingVersion encodingVersion = FlowEncodingVersion.parse(rootElement);

                    logger.trace("Setting controller thread counts");
                    final Integer maxThreadCount = getInteger(rootElement, "maxThreadCount");
                    if (maxThreadCount == null) {
                        controller.setMaxTimerDrivenThreadCount(getInt(rootElement, "maxTimerDrivenThreadCount"));
                        controller.setMaxEventDrivenThreadCount(getInt(rootElement, "maxEventDrivenThreadCount"));
                    } else {
                        controller.setMaxTimerDrivenThreadCount(maxThreadCount * 2 / 3);
                        controller.setMaxEventDrivenThreadCount(maxThreadCount / 3);
                    }

                    final Element reportingTasksElement = DomUtils.getChild(rootElement, "reportingTasks");
                    final List<Element> taskElements;
                    if (reportingTasksElement == null) {
                        taskElements = Collections.emptyList();
                    } else {
                        taskElements = DomUtils.getChildElementsByTagName(reportingTasksElement, "reportingTask");
                    }

                    final Element controllerServicesElement = DomUtils.getChild(rootElement, "controllerServices");
                    final List<Element> unrootedControllerServiceElements;
                    if (controllerServicesElement == null) {
                        unrootedControllerServiceElements = Collections.emptyList();
                    } else {
                        unrootedControllerServiceElements = DomUtils.getChildElementsByTagName(controllerServicesElement, "controllerService");
                    }

                    final boolean registriesPresent;
                    final Element registriesElement = DomUtils.getChild(rootElement, "registries");
                    if (registriesElement == null) {
                        registriesPresent = false;
                    } else {
                        final List<Element> flowRegistryElems = DomUtils.getChildElementsByTagName(registriesElement, "flowRegistry");
                        registriesPresent = !flowRegistryElems.isEmpty();
                    }

                    logger.trace("Parsing process group from DOM");
                    final Element rootGroupElement = (Element) rootElement.getElementsByTagName("rootGroup").item(0);
                    final ProcessGroupDTO rootGroupDto = FlowFromDOMFactory.getProcessGroup(null, rootGroupElement, encryptor, encodingVersion);
                    existingFlowEmpty = taskElements.isEmpty()
                        && unrootedControllerServiceElements.isEmpty()
                        && isEmpty(rootGroupDto)
                        && !registriesPresent;
                    logger.debug("Existing Flow Empty = {}", existingFlowEmpty);
                }
            }
        } catch (final IOException e) {
            throw new FlowSerializationException(e);
        }

        logger.trace("Exporting snippets from controller");
        final byte[] existingSnippets = controller.getSnippetManager().export();

        logger.trace("Getting Authorizer fingerprint from controller");

        final byte[] existingAuthFingerprint;
        final ManagedAuthorizer managedAuthorizer;
        final Authorizer authorizer = controller.getAuthorizer();

        if (AuthorizerCapabilityDetection.isManagedAuthorizer(authorizer)) {
            managedAuthorizer = (ManagedAuthorizer) authorizer;
            existingAuthFingerprint = managedAuthorizer.getFingerprint().getBytes(StandardCharsets.UTF_8);
        } else {
            existingAuthFingerprint = null;
            managedAuthorizer = null;
        }

        final Set<String> missingComponents = new HashSet<>();
        controller.getAllControllerServices().stream().filter(cs -> cs.isExtensionMissing()).forEach(cs -> missingComponents.add(cs.getIdentifier()));
        controller.getAllReportingTasks().stream().filter(r -> r.isExtensionMissing()).forEach(r -> missingComponents.add(r.getIdentifier()));
        controller.getRootGroup().findAllProcessors().stream().filter(p -> p.isExtensionMissing()).forEach(p -> missingComponents.add(p.getIdentifier()));

        final DataFlow existingDataFlow = new StandardDataFlow(existingFlow, existingSnippets, existingAuthFingerprint, missingComponents);

        Document configuration = null;

        // check that the proposed flow is inheritable by the controller
        try {
            if (existingFlowEmpty) {
                configuration = parseFlowBytes(proposedFlow.getFlow());
                if (configuration != null) {
                    logger.trace("Checking bundle compatibility");
                    checkBundleCompatibility(configuration);
                }
            } else {
                logger.trace("Checking flow inheritability");
                final String problemInheritingFlow = checkFlowInheritability(existingDataFlow, proposedFlow, controller);
                if (problemInheritingFlow != null) {
                    throw new UninheritableFlowException("Proposed configuration is not inheritable by the flow controller because of flow differences: " + problemInheritingFlow);
                }
            }
        } catch (final FingerprintException fe) {
            throw new FlowSerializationException("Failed to generate flow fingerprints", fe);
        }

        logger.trace("Checking missing component inheritability");

        final String problemInheritingMissingComponents = checkMissingComponentsInheritability(existingDataFlow, proposedFlow);
        if (problemInheritingMissingComponents != null) {
            throw new UninheritableFlowException("Proposed Flow is not inheritable by the flow controller because of differences in missing components: " + problemInheritingMissingComponents);
        }

        logger.trace("Checking authorizer inheritability");

        final AuthorizerInheritability authInheritability = checkAuthorizerInheritability(authorizer, existingDataFlow, proposedFlow);
        if (!authInheritability.isInheritable() && authInheritability.getReason() != null) {
            throw new UninheritableFlowException("Proposed Authorizer is not inheritable by the flow controller because of Authorizer differences: " + authInheritability.getReason());
        }

        // create document by parsing proposed flow bytes
        logger.trace("Parsing proposed flow bytes as DOM document");
        if (configuration == null) {
            configuration = parseFlowBytes(proposedFlow.getFlow());
        }

        // attempt to sync controller with proposed flow
        try {
            if (configuration != null) {
                synchronized (configuration) {
                    // get the root element
                    final Element rootElement = (Element) configuration.getElementsByTagName("flowController").item(0);
                    final FlowEncodingVersion encodingVersion = FlowEncodingVersion.parse(rootElement);

                    // set controller config
                    logger.trace("Updating flow config");
                    final Integer maxThreadCount = getInteger(rootElement, "maxThreadCount");
                    if (maxThreadCount == null) {
                        controller.setMaxTimerDrivenThreadCount(getInt(rootElement, "maxTimerDrivenThreadCount"));
                        controller.setMaxEventDrivenThreadCount(getInt(rootElement, "maxEventDrivenThreadCount"));
                    } else {
                        controller.setMaxTimerDrivenThreadCount(maxThreadCount * 2 / 3);
                        controller.setMaxEventDrivenThreadCount(maxThreadCount / 3);
                    }

                    // get the root group XML element
                    final Element rootGroupElement = (Element) rootElement.getElementsByTagName("rootGroup").item(0);

                    if (!flowAlreadySynchronized || existingFlowEmpty) {
                        final Element registriesElement = DomUtils.getChild(rootElement, "registries");
                        if (registriesElement != null) {
                            final List<Element> flowRegistryElems = DomUtils.getChildElementsByTagName(registriesElement, "flowRegistry");
                            for (final Element flowRegistryElement : flowRegistryElems) {
                                final String registryId = getString(flowRegistryElement, "id");
                                final String registryName = getString(flowRegistryElement, "name");
                                final String registryUrl = getString(flowRegistryElement, "url");
                                final String description = getString(flowRegistryElement, "description");

                                final FlowRegistryClient client = controller.getFlowRegistryClient();
                                client.addFlowRegistry(registryId, registryName, registryUrl, description);
                            }
                        }
                    }

                    // if this controller isn't initialized or its empty, add the root group, otherwise update
                    final ProcessGroup rootGroup;
                    if (!flowAlreadySynchronized || existingFlowEmpty) {
                        logger.trace("Adding root process group");
                        rootGroup = addProcessGroup(controller, /* parent group */ null, rootGroupElement, encryptor, encodingVersion);
                    } else {
                        logger.trace("Updating root process group");
                        rootGroup = updateProcessGroup(controller, /* parent group */ null, rootGroupElement, encryptor, encodingVersion);
                    }

                    rootGroup.findAllRemoteProcessGroups().forEach(RemoteProcessGroup::initialize);

                    // If there are any Templates that do not exist in the Proposed Flow that do exist in the 'existing flow', we need
                    // to ensure that we also add those to the appropriate Process Groups, so that we don't lose them.
                    final Document existingFlowConfiguration = parseFlowBytes(existingFlow);
                    if (existingFlowConfiguration != null) {
                        final Element existingRootElement = (Element) existingFlowConfiguration.getElementsByTagName("flowController").item(0);
                        if (existingRootElement != null) {
                            final Element existingRootGroupElement = (Element) existingRootElement.getElementsByTagName("rootGroup").item(0);
                            if (existingRootElement != null) {
                                final FlowEncodingVersion existingEncodingVersion = FlowEncodingVersion.parse(existingFlowConfiguration.getDocumentElement());
                                addLocalTemplates(existingRootGroupElement, rootGroup, existingEncodingVersion);
                            }
                        }
                    }

                    // get all the reporting task elements
                    final Element reportingTasksElement = DomUtils.getChild(rootElement, "reportingTasks");
                    final List<Element> reportingTaskElements = new ArrayList<>();
                    if (reportingTasksElement != null) {
                        reportingTaskElements.addAll(DomUtils.getChildElementsByTagName(reportingTasksElement, "reportingTask"));
                    }

                    // get/create all the reporting task nodes and DTOs, but don't apply their scheduled state yet
                    final Map<ReportingTaskNode, ReportingTaskDTO> reportingTaskNodesToDTOs = new HashMap<>();
                    for (final Element taskElement : reportingTaskElements) {
                        final ReportingTaskDTO dto = FlowFromDOMFactory.getReportingTask(taskElement, encryptor);
                        final ReportingTaskNode reportingTask = getOrCreateReportingTask(controller, dto, flowAlreadySynchronized, existingFlowEmpty);
                        reportingTaskNodesToDTOs.put(reportingTask, dto);
                    }

                    final Element controllerServicesElement = DomUtils.getChild(rootElement, "controllerServices");
                    if (controllerServicesElement != null) {
                        final List<Element> serviceElements = DomUtils.getChildElementsByTagName(controllerServicesElement, "controllerService");

                        if (!flowAlreadySynchronized || existingFlowEmpty) {
                            // If the encoding version is null, we are loading a flow from NiFi 0.x, where Controller
                            // Services could not be scoped by Process Group. As a result, we want to move the Process Groups
                            // to the root Group. Otherwise, we want to use a null group, which indicates a Controller-level
                            // Controller Service.
                            final ProcessGroup group = (encodingVersion == null) ? rootGroup : null;
                            final Map<ControllerServiceNode, Element> controllerServices = ControllerServiceLoader.loadControllerServices(serviceElements, controller, group, encryptor);

                            // If we are moving controller services to the root group we also need to see if any reporting tasks
                            // reference them, and if so we need to clone the CS and update the reporting task reference
                            if (group != null) {
                                // find all the controller service ids referenced by reporting tasks
                                final Set<String> controllerServicesInReportingTasks = reportingTaskNodesToDTOs.keySet().stream()
                                        .flatMap(r -> r.getProperties().entrySet().stream())
                                        .filter(e -> e.getKey().getControllerServiceDefinition() != null)
                                        .map(e -> e.getValue())
                                        .collect(Collectors.toSet());

                                // find the controller service nodes for each id referenced by a reporting task
                                final Set<ControllerServiceNode> controllerServicesToClone = controllerServices.keySet().stream()
                                        .filter(cs -> controllerServicesInReportingTasks.contains(cs.getIdentifier()))
                                        .collect(Collectors.toSet());

                                // clone the controller services and map the original id to the clone
                                final Map<String, ControllerServiceNode> controllerServiceMapping = new HashMap<>();
                                for (ControllerServiceNode controllerService : controllerServicesToClone) {
                                    final ControllerServiceNode clone = ControllerServiceLoader.cloneControllerService(controller, controllerService);
                                    controller.addRootControllerService(clone);
                                    controllerServiceMapping.put(controllerService.getIdentifier(), clone);
                                }

                                // update the reporting tasks to reference the cloned controller services
                                updateReportingTaskControllerServices(reportingTaskNodesToDTOs.keySet(), controllerServiceMapping);

                                // enable all the cloned controller services
                                ControllerServiceLoader.enableControllerServices(controllerServiceMapping.values(), controller, autoResumeState);
                            }

                            // enable all the original controller services
                            ControllerServiceLoader.enableControllerServices(controllerServices, controller, encryptor, autoResumeState);
                        }
                    }

                    scaleRootGroup(rootGroup, encodingVersion);

                    // now that controller services are loaded and enabled we can apply the scheduled state to each reporting task
                    for (Map.Entry<ReportingTaskNode, ReportingTaskDTO> entry : reportingTaskNodesToDTOs.entrySet()) {
                        applyReportingTaskScheduleState(controller, entry.getValue(), entry.getKey(), flowAlreadySynchronized, existingFlowEmpty);
                    }
                }
            }

            // clear the snippets that are currently in memory
            logger.trace("Clearing existing snippets");
            final SnippetManager snippetManager = controller.getSnippetManager();
            snippetManager.clear();

            // if proposed flow has any snippets, load them
            logger.trace("Loading proposed snippets");
            final byte[] proposedSnippets = proposedFlow.getSnippets();
            if (proposedSnippets != null && proposedSnippets.length > 0) {
                for (final StandardSnippet snippet : SnippetManager.parseBytes(proposedSnippets)) {
                    snippetManager.addSnippet(snippet);
                }
            }

            // if auths are inheritable and we have a policy based authorizer, then inherit
            if (authInheritability.isInheritable() && managedAuthorizer != null) {
                logger.trace("Inheriting authorizations");
                final String proposedAuthFingerprint = new String(proposedFlow.getAuthorizerFingerprint(), StandardCharsets.UTF_8);
                managedAuthorizer.inheritFingerprint(proposedAuthFingerprint);
            }

            logger.debug("Finished syncing flows");
        } catch (final Exception ex) {
            throw new FlowSynchronizationException(ex);
        }
    }
}