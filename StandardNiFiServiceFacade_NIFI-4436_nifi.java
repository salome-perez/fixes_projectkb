public class StandardNiFiServiceFacade {
    @Override
    public void verifyActivateControllerServices(final String groupId, final ControllerServiceState state, final Collection<String> serviceIds) {
        processGroupDAO.verifyActivateControllerServices(state, serviceIds);
    }

    @Override
    public Set<AffectedComponentEntity> getComponentsAffectedByVersionChange(final String processGroupId, final VersionedFlowSnapshot updatedSnapshot, final NiFiUser user) {
        final ProcessGroup group = processGroupDAO.getProcessGroup(processGroupId);

        final NiFiRegistryFlowMapper mapper = new NiFiRegistryFlowMapper();
        final VersionedProcessGroup localContents = mapper.mapProcessGroup(group, controllerFacade.getControllerServiceProvider(), flowRegistryClient, true);

        final ComparableDataFlow localFlow = new StandardComparableDataFlow("Local Flow", localContents);
        final ComparableDataFlow proposedFlow = new StandardComparableDataFlow("Versioned Flow", updatedSnapshot.getFlowContents());

        final Set<String> ancestorGroupServiceIds = getAncestorGroupServiceIds(group);
        final FlowComparator flowComparator = new StandardFlowComparator(localFlow, proposedFlow, ancestorGroupServiceIds, new StaticDifferenceDescriptor());
        final FlowComparison comparison = flowComparator.compare();

        final Set<AffectedComponentEntity> affectedComponents = comparison.getDifferences().stream()
            .filter(difference -> difference.getDifferenceType() != DifferenceType.COMPONENT_ADDED) // components that are added are not components that will be affected in the local flow.
            .map(difference -> {
                final VersionedComponent localComponent = difference.getComponentA();

                final String state;
                switch (localComponent.getComponentType()) {
                    case CONTROLLER_SERVICE:
                        final String serviceId = ((InstantiatedVersionedControllerService) localComponent).getInstanceId();
                        state = controllerServiceDAO.getControllerService(serviceId).getState().name();
                        break;
                    case PROCESSOR:
                        final String processorId = ((InstantiatedVersionedProcessor) localComponent).getInstanceId();
                        state = processorDAO.getProcessor(processorId).getPhysicalScheduledState().name();
                        break;
                    case REMOTE_INPUT_PORT:
                        final InstantiatedVersionedRemoteGroupPort inputPort = (InstantiatedVersionedRemoteGroupPort) localComponent;
                        state = remoteProcessGroupDAO.getRemoteProcessGroup(inputPort.getInstanceGroupId()).getInputPort(inputPort.getInstanceId()).getScheduledState().name();
                        break;
                    case REMOTE_OUTPUT_PORT:
                        final InstantiatedVersionedRemoteGroupPort outputPort = (InstantiatedVersionedRemoteGroupPort) localComponent;
                        state = remoteProcessGroupDAO.getRemoteProcessGroup(outputPort.getInstanceGroupId()).getOutputPort(outputPort.getInstanceId()).getScheduledState().name();
                        break;
                    default:
                        state = null;
                        break;
                }

                return createAffectedComponentEntity((InstantiatedVersionedComponent) localComponent, localComponent.getComponentType().name(), state, user);
            })
            .collect(Collectors.toCollection(HashSet::new));

        for (final FlowDifference difference : comparison.getDifferences()) {
            final VersionedComponent localComponent = difference.getComponentA();
            if (localComponent == null) {
                continue;
            }

            // If any Process Group is removed, consider all components below that Process Group as an affected component
            if (difference.getDifferenceType() == DifferenceType.COMPONENT_REMOVED && localComponent.getComponentType() == org.apache.nifi.registry.flow.ComponentType.PROCESS_GROUP) {
                final String localGroupId = ((InstantiatedVersionedProcessGroup) localComponent).getInstanceId();
                final ProcessGroup localGroup = processGroupDAO.getProcessGroup(localGroupId);

                localGroup.findAllProcessors().stream()
                    .map(comp -> createAffectedComponentEntity(comp, user))
                    .forEach(affectedComponents::add);
                localGroup.findAllFunnels().stream()
                    .map(comp -> createAffectedComponentEntity(comp, user))
                    .forEach(affectedComponents::add);
                localGroup.findAllInputPorts().stream()
                    .map(comp -> createAffectedComponentEntity(comp, user))
                    .forEach(affectedComponents::add);
                localGroup.findAllOutputPorts().stream()
                    .map(comp -> createAffectedComponentEntity(comp, user))
                    .forEach(affectedComponents::add);
                localGroup.findAllRemoteProcessGroups().stream()
                    .flatMap(rpg -> Stream.concat(rpg.getInputPorts().stream(), rpg.getOutputPorts().stream()))
                    .map(comp -> createAffectedComponentEntity(comp, user))
                    .forEach(affectedComponents::add);
                localGroup.findAllControllerServices().stream()
                    .map(comp -> createAffectedComponentEntity(comp, user))
                    .forEach(affectedComponents::add);
            }

            if (localComponent.getComponentType() == org.apache.nifi.registry.flow.ComponentType.CONTROLLER_SERVICE) {
                final String serviceId = ((InstantiatedVersionedControllerService) localComponent).getInstanceId();
                final ControllerServiceNode serviceNode = controllerServiceDAO.getControllerService(serviceId);

                final List<ControllerServiceNode> referencingServices = serviceNode.getReferences().findRecursiveReferences(ControllerServiceNode.class);
                for (final ControllerServiceNode referencingService : referencingServices) {
                    affectedComponents.add(createAffectedComponentEntity(referencingService, user));
                }

                final List<ProcessorNode> referencingProcessors = serviceNode.getReferences().findRecursiveReferences(ProcessorNode.class);
                for (final ProcessorNode referencingProcessor : referencingProcessors) {
                    affectedComponents.add(createAffectedComponentEntity(referencingProcessor, user));
                }
            }
        }

        // Create a map of all connectable components by versioned component ID to the connectable component itself
        final Map<String, List<Connectable>> connectablesByVersionId = new HashMap<>();
        mapToConnectableId(group.findAllFunnels(), connectablesByVersionId);
        mapToConnectableId(group.findAllInputPorts(), connectablesByVersionId);
        mapToConnectableId(group.findAllOutputPorts(), connectablesByVersionId);
        mapToConnectableId(group.findAllProcessors(), connectablesByVersionId);

        final List<RemoteGroupPort> remotePorts = new ArrayList<>();
        for (final RemoteProcessGroup rpg : group.findAllRemoteProcessGroups()) {
            remotePorts.addAll(rpg.getInputPorts());
            remotePorts.addAll(rpg.getOutputPorts());
        }
        mapToConnectableId(remotePorts, connectablesByVersionId);

        // If any connection is added or modified, we need to stop both the source (if it exists in the flow currently)
        // and the destination (if it exists in the flow currently).
        for (final FlowDifference difference : comparison.getDifferences()) {
            VersionedComponent component = difference.getComponentA();
            if (component == null) {
                component = difference.getComponentB();
            }

            if (component.getComponentType() != org.apache.nifi.registry.flow.ComponentType.CONNECTION) {
                continue;
            }

            final VersionedConnection connection = (VersionedConnection) component;

            final String sourceVersionedId = connection.getSource().getId();
            final List<Connectable> sources = connectablesByVersionId.get(sourceVersionedId);
            if (sources != null) {
                for (final Connectable source : sources) {
                    affectedComponents.add(createAffectedComponentEntity(source, user));
                }
            }

            final String destinationVersionId = connection.getDestination().getId();
            final List<Connectable> destinations = connectablesByVersionId.get(destinationVersionId);
            if (destinations != null) {
                for (final Connectable destination : destinations) {
                    affectedComponents.add(createAffectedComponentEntity(destination, user));
                }
            }
        }

        return affectedComponents;
    }

    private PortEntity createInputPortEntity(final Port port, final NiFiUser user) {
        final RevisionDTO revision = dtoFactory.createRevisionDTO(revisionManager.getRevision(port.getIdentifier()));
        final PermissionsDTO permissions = dtoFactory.createPermissionsDto(port, user);
        final PortStatusDTO status = dtoFactory.createPortStatusDto(controllerFacade.getInputPortStatus(port.getIdentifier()));
        final List<BulletinDTO> bulletins = dtoFactory.createBulletinDtos(bulletinRepository.findBulletinsForSource(port.getIdentifier()));
        final List<BulletinEntity> bulletinEntities = bulletins.stream().map(bulletin -> entityFactory.createBulletinEntity(bulletin, permissions.getCanRead())).collect(Collectors.toList());
        return entityFactory.createPortEntity(dtoFactory.createPortDto(port), revision, permissions, status, bulletinEntities);
    }

            public RevisionUpdate<ProcessGroupDTO> update() {
                // update the Process Group
                processGroupDAO.updateProcessGroupFlow(groupId, user, proposedFlowSnapshot, versionControlInfo, componentIdSeed, verifyNotModified, updateSettings, updateDescendantVersionedFlows);

                // update the revisions
                final Set<Revision> updatedRevisions = revisions.stream()
                    .map(rev -> revisionManager.getRevision(rev.getComponentId()).incrementRevision(revision.getClientId()))
                    .collect(Collectors.toSet());

                // save
                controllerFacade.save();

                // gather details for response
                final ProcessGroupDTO dto = dtoFactory.createProcessGroupDto(processGroup);

                final Revision updatedRevision = revisionManager.getRevision(groupId).incrementRevision(revision.getClientId());
                final FlowModification lastModification = new FlowModification(updatedRevision, user.getIdentity());
                return new StandardRevisionUpdate<>(dto, lastModification, updatedRevisions);
            }

    private PortEntity createOutputPortEntity(final Port port, final NiFiUser user) {
        final RevisionDTO revision = dtoFactory.createRevisionDTO(revisionManager.getRevision(port.getIdentifier()));
        final PermissionsDTO permissions = dtoFactory.createPermissionsDto(port, user);
        final PortStatusDTO status = dtoFactory.createPortStatusDto(controllerFacade.getOutputPortStatus(port.getIdentifier()));
        final List<BulletinDTO> bulletins = dtoFactory.createBulletinDtos(bulletinRepository.findBulletinsForSource(port.getIdentifier()));
        final List<BulletinEntity> bulletinEntities = bulletins.stream().map(bulletin -> entityFactory.createBulletinEntity(bulletin, permissions.getCanRead())).collect(Collectors.toList());
        return entityFactory.createPortEntity(dtoFactory.createPortDto(port), revision, permissions, status, bulletinEntities);
    }

    private Set<String> getAncestorGroupServiceIds(final ProcessGroup group) {
        final Set<String> ancestorServiceIds;
        ProcessGroup parentGroup = group.getParent();

        if (parentGroup == null) {
            ancestorServiceIds = Collections.emptySet();
        } else {
            ancestorServiceIds = parentGroup.getControllerServices(true).stream()
                .map(cs -> {
                    // We want to map the Controller Service to its Versioned Component ID, if it has one.
                    // If it does not have one, we want to generate it in the same way that our Flow Mapper does
                    // because this allows us to find the Controller Service when doing a Flow Diff.
                    final Optional<String> versionedId = cs.getVersionedComponentId();
                    if (versionedId.isPresent()) {
                        return versionedId.get();
                    }

                    return UUID.nameUUIDFromBytes(cs.getIdentifier().getBytes(StandardCharsets.UTF_8)).toString();
                })
                .collect(Collectors.toSet());
        }

        return ancestorServiceIds;
    }

    @Override
    public ActivateControllerServicesEntity activateControllerServices(final NiFiUser user, final String processGroupId, final ControllerServiceState state,
        final Map<String, Revision> serviceRevisions) {

        final RevisionUpdate<ActivateControllerServicesEntity> updatedComponent = revisionManager.updateRevision(new StandardRevisionClaim(serviceRevisions.values()), user,
            new UpdateRevisionTask<ActivateControllerServicesEntity>() {
                @Override
                public RevisionUpdate<ActivateControllerServicesEntity> update() {
                    // schedule the components
                    processGroupDAO.activateControllerServices(state, serviceRevisions.keySet());

                    // update the revisions
                    final Map<String, Revision> updatedRevisions = new HashMap<>();
                    for (final Revision revision : serviceRevisions.values()) {
                        final Revision currentRevision = revisionManager.getRevision(revision.getComponentId());
                        updatedRevisions.put(revision.getComponentId(), currentRevision.incrementRevision(revision.getClientId()));
                    }

                    // save
                    controllerFacade.save();

                    // gather details for response
                    final ActivateControllerServicesEntity entity = new ActivateControllerServicesEntity();
                    entity.setId(processGroupId);
                    entity.setState(state.name());
                    return new StandardRevisionUpdate<>(entity, null, new HashSet<>(updatedRevisions.values()));
                }
            });

        return updatedComponent.getComponent();
    }

}