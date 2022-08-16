public class VersionsResource {
    public Response initiateRevertFlowVersion(@ApiParam("The process group id.") @PathParam("id") final String groupId,
        @ApiParam(value = "The controller service configuration details.", required = true) final VersionControlInformationEntity requestEntity) throws IOException {

        // Verify the request
        final RevisionDTO revisionDto = requestEntity.getProcessGroupRevision();
        if (revisionDto == null) {
            throw new IllegalArgumentException("Process Group Revision must be specified");
        }

        final VersionControlInformationDTO requestVersionControlInfoDto = requestEntity.getVersionControlInformation();
        if (requestVersionControlInfoDto == null) {
            throw new IllegalArgumentException("Version Control Information must be supplied.");
        }
        if (requestVersionControlInfoDto.getGroupId() == null) {
            throw new IllegalArgumentException("The Process Group ID must be supplied.");
        }
        if (!requestVersionControlInfoDto.getGroupId().equals(groupId)) {
            throw new IllegalArgumentException("The Process Group ID in the request body does not match the Process Group ID of the requested resource.");
        }
        if (requestVersionControlInfoDto.getBucketId() == null) {
            throw new IllegalArgumentException("The Bucket ID must be supplied.");
        }
        if (requestVersionControlInfoDto.getFlowId() == null) {
            throw new IllegalArgumentException("The Flow ID must be supplied.");
        }
        if (requestVersionControlInfoDto.getRegistryId() == null) {
            throw new IllegalArgumentException("The Registry ID must be supplied.");
        }
        if (requestVersionControlInfoDto.getVersion() == null) {
            throw new IllegalArgumentException("The Version of the flow must be supplied.");
        }

        // We will perform the updating of the Versioned Flow in a background thread because it can be a long-running process.
        // In order to do this, we will need some parameters that are only available as Thread-Local variables to the current
        // thread, so we will gather the values for these parameters up front.
        final boolean replicateRequest = isReplicateRequest();
        final ComponentLifecycle componentLifecycle = replicateRequest ? clusterComponentLifecycle : localComponentLifecycle;
        final NiFiUser user = NiFiUserUtils.getNiFiUser();

        // Step 0: Get the Versioned Flow Snapshot from the Flow Registry
        final VersionedFlowSnapshot flowSnapshot = serviceFacade.getVersionedFlowSnapshot(requestEntity.getVersionControlInformation(), true);

        // The flow in the registry may not contain the same versions of components that we have in our flow. As a result, we need to update
        // the flow snapshot to contain compatible bundles.
        BundleUtils.discoverCompatibleBundles(flowSnapshot.getFlowContents());

        // Step 1: Determine which components will be affected by updating the version
        final Set<AffectedComponentEntity> affectedComponents = serviceFacade.getComponentsAffectedByVersionChange(groupId, flowSnapshot, user);

        // build a request wrapper
        final InitiateChangeFlowVersionRequestWrapper requestWrapper = new InitiateChangeFlowVersionRequestWrapper(requestEntity, componentLifecycle, getAbsolutePath(), affectedComponents,
                replicateRequest, flowSnapshot);

        final Revision requestRevision = getRevision(requestEntity.getProcessGroupRevision(), groupId);
        return withWriteLock(
            serviceFacade,
            requestWrapper,
            requestRevision,
            lookup -> {
                // Step 2: Verify READ and WRITE permissions for user, for every component.
                final ProcessGroupAuthorizable groupAuthorizable = lookup.getProcessGroup(groupId);
                authorizeProcessGroup(groupAuthorizable, authorizer, lookup, RequestAction.READ, true, false, true, true);
                authorizeProcessGroup(groupAuthorizable, authorizer, lookup, RequestAction.WRITE, true, false, true, true);

                final VersionedProcessGroup groupContents = flowSnapshot.getFlowContents();
                final boolean containsRestrictedComponents = FlowRegistryUtils.containsRestrictedComponent(groupContents);
                if (containsRestrictedComponents) {
                    lookup.getRestrictedComponents().authorize(authorizer, RequestAction.WRITE, NiFiUserUtils.getNiFiUser());
                }
            },
            () -> {
                // Step 3: Verify that all components in the snapshot exist on all nodes
                // Step 4: Verify that Process Group is already under version control. If not, must start Version Control instead of updating flow
                serviceFacade.verifyCanRevertLocalModifications(groupId, flowSnapshot);
            },
            (revision, wrapper) -> {
                final VersionControlInformationEntity versionControlInformationEntity = wrapper.getVersionControlInformationEntity();
                final VersionControlInformationDTO versionControlInformationDTO = versionControlInformationEntity.getVersionControlInformation();

                // Ensure that the information passed in is correct
                final VersionControlInformationEntity currentVersionEntity = serviceFacade.getVersionControlInformation(groupId);
                if (currentVersionEntity == null) {
                    throw new IllegalStateException("Process Group cannot be reverted to the previous version of the flow because Process Group is not under Version Control.");
                }

                final VersionControlInformationDTO currentVersion = currentVersionEntity.getVersionControlInformation();
                if (!currentVersion.getBucketId().equals(versionControlInformationDTO.getBucketId())) {
                    throw new IllegalArgumentException("The Version Control Information provided does not match the flow that the Process Group is currently synchronized with.");
                }
                if (!currentVersion.getFlowId().equals(versionControlInformationDTO.getFlowId())) {
                    throw new IllegalArgumentException("The Version Control Information provided does not match the flow that the Process Group is currently synchronized with.");
                }
                if (!currentVersion.getRegistryId().equals(versionControlInformationDTO.getRegistryId())) {
                    throw new IllegalArgumentException("The Version Control Information provided does not match the flow that the Process Group is currently synchronized with.");
                }
                if (!currentVersion.getVersion().equals(versionControlInformationDTO.getVersion())) {
                    throw new IllegalArgumentException("The Version Control Information provided does not match the flow that the Process Group is currently synchronized with.");
                }

                final String idGenerationSeed = getIdGenerationSeed().orElse(null);

                // Create an asynchronous request that will occur in the background, because this request may
                // result in stopping components, which can take an indeterminate amount of time.
                final String requestId = UUID.randomUUID().toString();
                final AsynchronousWebRequest<VersionControlInformationEntity> request = new StandardAsynchronousWebRequest<>(requestId, groupId, user, "Stopping Affected Processors");

                // Submit the request to be performed in the background
                final Consumer<AsynchronousWebRequest<VersionControlInformationEntity>> updateTask = vcur -> {
                    try {
                        final VersionControlInformationEntity updatedVersionControlEntity = updateFlowVersion(groupId, wrapper.getComponentLifecycle(), wrapper.getExampleUri(),
                            wrapper.getAffectedComponents(), user, wrapper.isReplicateRequest(), revision, versionControlInformationEntity, wrapper.getFlowSnapshot(), request,
                            idGenerationSeed, false, true);

                        vcur.markComplete(updatedVersionControlEntity);
                    } catch (final ResumeFlowException rfe) {
                        // Treat ResumeFlowException differently because we don't want to include a message that we couldn't update the flow
                        // since in this case the flow was successfully updated - we just couldn't re-enable the components.
                        logger.error(rfe.getMessage(), rfe);
                        vcur.setFailureReason(rfe.getMessage());
                    } catch (final Exception e) {
                        logger.error("Failed to update flow to new version", e);
                        vcur.setFailureReason("Failed to update flow to new version due to " + e.getMessage());
                    }
                };

                requestManager.submitRequest("revert-requests", requestId, request, updateTask);

                // Generate the response.
                final VersionedFlowUpdateRequestDTO updateRequestDto = new VersionedFlowUpdateRequestDTO();
                updateRequestDto.setComplete(request.isComplete());
                updateRequestDto.setFailureReason(request.getFailureReason());
                updateRequestDto.setLastUpdated(request.getLastUpdated());
                updateRequestDto.setProcessGroupId(groupId);
                updateRequestDto.setRequestId(requestId);
                updateRequestDto.setState(request.getState());
                updateRequestDto.setPercentCompleted(request.getPercentComplete());
                updateRequestDto.setUri(generateResourceUri("versions", "revert-requests", requestId));


                final VersionedFlowUpdateRequestEntity updateRequestEntity = new VersionedFlowUpdateRequestEntity();
                final RevisionDTO groupRevision = serviceFacade.getProcessGroup(groupId).getRevision();
                updateRequestEntity.setProcessGroupRevision(groupRevision);
                updateRequestEntity.setRequest(updateRequestDto);

                return generateOkResponse(updateRequestEntity).build();
            });
    }
    public URI getExampleUri() {
        return exampleUri;
    }
    public Response initiateVersionControlUpdate(
        @ApiParam("The process group id.") @PathParam("id") final String groupId,
        @ApiParam(value = "The controller service configuration details.", required = true) final VersionControlInformationEntity requestEntity) {

        // Verify the request
        final RevisionDTO revisionDto = requestEntity.getProcessGroupRevision();
        if (revisionDto == null) {
            throw new IllegalArgumentException("Process Group Revision must be specified");
        }

        final VersionControlInformationDTO requestVersionControlInfoDto = requestEntity.getVersionControlInformation();
        if (requestVersionControlInfoDto == null) {
            throw new IllegalArgumentException("Version Control Information must be supplied.");
        }
        if (requestVersionControlInfoDto.getGroupId() == null) {
            throw new IllegalArgumentException("The Process Group ID must be supplied.");
        }
        if (!requestVersionControlInfoDto.getGroupId().equals(groupId)) {
            throw new IllegalArgumentException("The Process Group ID in the request body does not match the Process Group ID of the requested resource.");
        }
        if (requestVersionControlInfoDto.getBucketId() == null) {
            throw new IllegalArgumentException("The Bucket ID must be supplied.");
        }
        if (requestVersionControlInfoDto.getFlowId() == null) {
            throw new IllegalArgumentException("The Flow ID must be supplied.");
        }
        if (requestVersionControlInfoDto.getRegistryId() == null) {
            throw new IllegalArgumentException("The Registry ID must be supplied.");
        }
        if (requestVersionControlInfoDto.getVersion() == null) {
            throw new IllegalArgumentException("The Version of the flow must be supplied.");
        }

        // We will perform the updating of the Versioned Flow in a background thread because it can be a long-running process.
        // In order to do this, we will need some parameters that are only available as Thread-Local variables to the current
        // thread, so we will gather the values for these parameters up front.
        final boolean replicateRequest = isReplicateRequest();
        final ComponentLifecycle componentLifecycle = replicateRequest ? clusterComponentLifecycle : localComponentLifecycle;
        final NiFiUser user = NiFiUserUtils.getNiFiUser();


        // Workflow for this process:
        // 0. Obtain the versioned flow snapshot to use for the update
        //    a. Contact registry to download the desired version.
        //    b. Get Variable Registry of this Process Group and all ancestor groups
        //    c. Perform diff to find any new variables
        //    d. Get Variable Registry of any child Process Group in the versioned flow
        //    e. Perform diff to find any new variables
        //    f. Prompt user to fill in values for all new variables
        // 1. Determine which components would be affected (and are enabled/running)
        //    a. Component itself is modified in some way, other than position changing.
        //    b. Source and Destination of any Connection that is modified.
        //    c. Any Processor or Controller Service that references a Controller Service that is modified.
        // 2. Verify READ and WRITE permissions for user, for every component.
        // 3. Verify that all components in the snapshot exist on all nodes (i.e., the NAR exists)?
        // 4. Verify that Process Group is already under version control. If not, must start Version Control instead of updateFlow
        // 5. Verify that Process Group is not 'dirty'.
        // 6. Stop all Processors, Funnels, Ports that are affected.
        // 7. Wait for all of the components to finish stopping.
        // 8. Disable all Controller Services that are affected.
        // 9. Wait for all Controller Services to finish disabling.
        // 10. Ensure that if any connection was deleted, that it has no data in it. Ensure that no Input Port
        //    was removed, unless it currently has no incoming connections. Ensure that no Output Port was removed,
        //    unless it currently has no outgoing connections. Checking ports & connections could be done before
        //    stopping everything, but removal of Connections cannot.
        // 11. Update variable registry to include new variables
        //    (only new variables so don't have to worry about affected components? Or do we need to in case a processor
        //    is already referencing the variable? In which case we need to include the affected components above in the
        //    Set of affected components before stopping/disabling.).
        // 12. Update components in the Process Group; update Version Control Information.
        // 13. Re-Enable all affected Controller Services that were not removed.
        // 14. Re-Start all Processors, Funnels, Ports that are affected and not removed.

        // Step 0: Get the Versioned Flow Snapshot from the Flow Registry
        final VersionedFlowSnapshot flowSnapshot = serviceFacade.getVersionedFlowSnapshot(requestEntity.getVersionControlInformation(), true);

        // The flow in the registry may not contain the same versions of components that we have in our flow. As a result, we need to update
        // the flow snapshot to contain compatible bundles.
        BundleUtils.discoverCompatibleBundles(flowSnapshot.getFlowContents());

        // Step 1: Determine which components will be affected by updating the version
        final Set<AffectedComponentEntity> affectedComponents = serviceFacade.getComponentsAffectedByVersionChange(groupId, flowSnapshot, user);

        // build a request wrapper
        final InitiateChangeFlowVersionRequestWrapper requestWrapper = new InitiateChangeFlowVersionRequestWrapper(requestEntity, componentLifecycle, getAbsolutePath(), affectedComponents,
                replicateRequest, flowSnapshot);

        final Revision requestRevision = getRevision(requestEntity.getProcessGroupRevision(), groupId);
        return withWriteLock(
            serviceFacade,
            requestWrapper,
            requestRevision,
            lookup -> {
                // Step 2: Verify READ and WRITE permissions for user, for every component.
                final ProcessGroupAuthorizable groupAuthorizable = lookup.getProcessGroup(groupId);
                authorizeProcessGroup(groupAuthorizable, authorizer, lookup, RequestAction.READ, true, false, true, true);
                authorizeProcessGroup(groupAuthorizable, authorizer, lookup, RequestAction.WRITE, true, false, true, true);

                final VersionedProcessGroup groupContents = flowSnapshot.getFlowContents();
                final boolean containsRestrictedComponents = FlowRegistryUtils.containsRestrictedComponent(groupContents);
                if (containsRestrictedComponents) {
                    lookup.getRestrictedComponents().authorize(authorizer, RequestAction.WRITE, NiFiUserUtils.getNiFiUser());
                }
            },
            () -> {
                // Step 3: Verify that all components in the snapshot exist on all nodes
                // Step 4: Verify that Process Group is already under version control. If not, must start Version Control instead of updating flow
                // Step 5: Verify that Process Group is not 'dirty'
                serviceFacade.verifyCanUpdate(groupId, flowSnapshot, false, true);
            },
            (revision, wrapper) -> {
                final String idGenerationSeed = getIdGenerationSeed().orElse(null);

                // Create an asynchronous request that will occur in the background, because this request may
                // result in stopping components, which can take an indeterminate amount of time.
                final String requestId = UUID.randomUUID().toString();
                final AsynchronousWebRequest<VersionControlInformationEntity> request = new StandardAsynchronousWebRequest<>(requestId, groupId, user, "Stopping Affected Processors");

                // Submit the request to be performed in the background
                final Consumer<AsynchronousWebRequest<VersionControlInformationEntity>> updateTask = vcur -> {
                    try {
                        final VersionControlInformationEntity updatedVersionControlEntity = updateFlowVersion(groupId, wrapper.getComponentLifecycle(), wrapper.getExampleUri(),
                            wrapper.getAffectedComponents(), user, wrapper.isReplicateRequest(), revision, wrapper.getVersionControlInformationEntity(), wrapper.getFlowSnapshot(), request,
                            idGenerationSeed, true, true);

                        vcur.markComplete(updatedVersionControlEntity);
                    } catch (final ResumeFlowException rfe) {
                        // Treat ResumeFlowException differently because we don't want to include a message that we couldn't update the flow
                        // since in this case the flow was successfully updated - we just couldn't re-enable the components.
                        logger.error(rfe.getMessage(), rfe);
                        vcur.setFailureReason(rfe.getMessage());
                    } catch (final Exception e) {
                        logger.error("Failed to update flow to new version", e);
                        vcur.setFailureReason("Failed to update flow to new version due to " + e);
                    }
                };

                requestManager.submitRequest("update-requests", requestId, request, updateTask);

                // Generate the response.
                final VersionedFlowUpdateRequestDTO updateRequestDto = new VersionedFlowUpdateRequestDTO();
                updateRequestDto.setComplete(request.isComplete());
                updateRequestDto.setFailureReason(request.getFailureReason());
                updateRequestDto.setLastUpdated(request.getLastUpdated());
                updateRequestDto.setProcessGroupId(groupId);
                updateRequestDto.setRequestId(requestId);
                updateRequestDto.setUri(generateResourceUri("versions", "update-requests", requestId));
                updateRequestDto.setPercentCompleted(request.getPercentComplete());
                updateRequestDto.setState(request.getState());

                final VersionedFlowUpdateRequestEntity updateRequestEntity = new VersionedFlowUpdateRequestEntity();
                final RevisionDTO groupRevision = serviceFacade.getProcessGroup(groupId).getRevision();
                updateRequestEntity.setProcessGroupRevision(groupRevision);
                updateRequestEntity.setRequest(updateRequestDto);

                return generateOkResponse(updateRequestEntity).build();
            });
    }

    private VersionControlInformationEntity updateFlowVersion(final String groupId, final ComponentLifecycle componentLifecycle, final URI exampleUri,
        final Set<AffectedComponentEntity> affectedComponents, final NiFiUser user, final boolean replicateRequest, final Revision revision, final VersionControlInformationEntity requestEntity,
        final VersionedFlowSnapshot flowSnapshot, final AsynchronousWebRequest<VersionControlInformationEntity> asyncRequest, final String idGenerationSeed,
        final boolean verifyNotModified, final boolean updateDescendantVersionedFlows) throws LifecycleManagementException, ResumeFlowException {

        // Steps 6-7: Determine which components must be stopped and stop them.
        final Set<String> stoppableReferenceTypes = new HashSet<>();
        stoppableReferenceTypes.add(AffectedComponentDTO.COMPONENT_TYPE_PROCESSOR);
        stoppableReferenceTypes.add(AffectedComponentDTO.COMPONENT_TYPE_REMOTE_INPUT_PORT);
        stoppableReferenceTypes.add(AffectedComponentDTO.COMPONENT_TYPE_REMOTE_OUTPUT_PORT);
        stoppableReferenceTypes.add(AffectedComponentDTO.COMPONENT_TYPE_INPUT_PORT);
        stoppableReferenceTypes.add(AffectedComponentDTO.COMPONENT_TYPE_OUTPUT_PORT);

        final Set<AffectedComponentEntity> runningComponents = affectedComponents.stream()
            .filter(dto -> stoppableReferenceTypes.contains(dto.getComponent().getReferenceType()))
            .filter(dto -> "Running".equalsIgnoreCase(dto.getComponent().getState()))
            .collect(Collectors.toSet());

        logger.info("Stopping {} Processors", runningComponents.size());
        final CancellableTimedPause stopComponentsPause = new CancellableTimedPause(250, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        asyncRequest.setCancelCallback(stopComponentsPause::cancel);
        componentLifecycle.scheduleComponents(exampleUri, user, groupId, runningComponents, ScheduledState.STOPPED, stopComponentsPause);

        if (asyncRequest.isCancelled()) {
            return null;
        }
        asyncRequest.update(new Date(), "Disabling Affected Controller Services", 20);

        // Steps 8-9. Disable enabled controller services that are affected
        final Set<AffectedComponentEntity> enabledServices = affectedComponents.stream()
            .filter(dto -> AffectedComponentDTO.COMPONENT_TYPE_CONTROLLER_SERVICE.equals(dto.getComponent().getReferenceType()))
            .filter(dto -> "Enabled".equalsIgnoreCase(dto.getComponent().getState()))
            .collect(Collectors.toSet());

        logger.info("Disabling {} Controller Services", enabledServices.size());
        final CancellableTimedPause disableServicesPause = new CancellableTimedPause(250, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        asyncRequest.setCancelCallback(disableServicesPause::cancel);
        componentLifecycle.activateControllerServices(exampleUri, user, groupId, enabledServices, ControllerServiceState.DISABLED, disableServicesPause);

        if (asyncRequest.isCancelled()) {
            return null;
        }
        asyncRequest.update(new Date(), "Updating Flow", 40);

        logger.info("Updating Process Group with ID {} to version {} of the Versioned Flow", groupId, flowSnapshot.getSnapshotMetadata().getVersion());
        // If replicating request, steps 10-12 are performed on each node individually, and this is accomplished
        // by replicating a PUT to /nifi-api/versions/process-groups/{groupId}
        try {
            if (replicateRequest) {

                final URI updateUri;
                try {
                    updateUri = new URI(exampleUri.getScheme(), exampleUri.getUserInfo(), exampleUri.getHost(),
                        exampleUri.getPort(), "/nifi-api/versions/process-groups/" + groupId, null, exampleUri.getFragment());
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }

                final Map<String, String> headers = new HashMap<>();
                headers.put("content-type", MediaType.APPLICATION_JSON);

                final VersionedFlowSnapshotEntity snapshotEntity = new VersionedFlowSnapshotEntity();
                snapshotEntity.setProcessGroupRevision(dtoFactory.createRevisionDTO(revision));
                snapshotEntity.setRegistryId(requestEntity.getVersionControlInformation().getRegistryId());
                snapshotEntity.setVersionedFlow(flowSnapshot);
                snapshotEntity.setUpdateDescendantVersionedFlows(updateDescendantVersionedFlows);

                final NodeResponse clusterResponse;
                try {
                    logger.debug("Replicating PUT request to {} for user {}", updateUri, user);

                    if (getReplicationTarget() == ReplicationTarget.CLUSTER_NODES) {
                        clusterResponse = getRequestReplicator().replicate(user, HttpMethod.PUT, updateUri, snapshotEntity, headers).awaitMergedResponse();
                    } else {
                        clusterResponse = getRequestReplicator().forwardToCoordinator(
                            getClusterCoordinatorNode(), user, HttpMethod.PUT, updateUri, snapshotEntity, headers).awaitMergedResponse();
                    }
                } catch (final InterruptedException ie) {
                    logger.warn("Interrupted while replicating PUT request to {} for user {}", updateUri, user);
                    Thread.currentThread().interrupt();
                    throw new LifecycleManagementException("Interrupted while updating flows across cluster", ie);
                }

                final int updateFlowStatus = clusterResponse.getStatus();
                if (updateFlowStatus != Status.OK.getStatusCode()) {
                    final String explanation = getResponseEntity(clusterResponse, String.class);
                    logger.error("Failed to update flow across cluster when replicating PUT request to {} for user {}. Received {} response with explanation: {}",
                        updateUri, user, updateFlowStatus, explanation);
                    throw new LifecycleManagementException("Failed to update Flow on all nodes in cluster due to " + explanation);
                }

            } else {
                // Step 10: Ensure that if any connection exists in the flow and does not exist in the proposed snapshot,
                // that it has no data in it. Ensure that no Input Port was removed, unless it currently has no incoming connections.
                // Ensure that no Output Port was removed, unless it currently has no outgoing connections.
                serviceFacade.verifyCanUpdate(groupId, flowSnapshot, true, verifyNotModified);

                // Step 11-12. Update Process Group to the new flow and update variable registry with any Variables that were added or removed
                final VersionControlInformationDTO requestVci = requestEntity.getVersionControlInformation();

                final Bucket bucket = flowSnapshot.getBucket();
                final VersionedFlow flow = flowSnapshot.getFlow();

                final VersionedFlowSnapshotMetadata metadata = flowSnapshot.getSnapshotMetadata();
                final VersionControlInformationDTO vci = new VersionControlInformationDTO();
                vci.setBucketId(metadata.getBucketIdentifier());
                vci.setBucketName(bucket.getName());
                vci.setFlowDescription(flow.getDescription());
                vci.setFlowId(flow.getIdentifier());
                vci.setFlowName(flow.getName());
                vci.setGroupId(groupId);
                vci.setRegistryId(requestVci.getRegistryId());
                vci.setRegistryName(serviceFacade.getFlowRegistryName(requestVci.getRegistryId()));
                vci.setVersion(metadata.getVersion());
                vci.setState(flowSnapshot.isLatest() ? VersionedFlowState.UP_TO_DATE.name() : VersionedFlowState.STALE.name());

                serviceFacade.updateProcessGroupContents(user, revision, groupId, vci, flowSnapshot, idGenerationSeed, verifyNotModified, false, updateDescendantVersionedFlows);
            }
        } finally {
            if (!asyncRequest.isCancelled()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Re-Enabling {} Controller Services: {}", enabledServices.size(), enabledServices);
                }

                asyncRequest.update(new Date(), "Re-Enabling Controller Services", 60);

                // Step 13. Re-enable all disabled controller services
                final CancellableTimedPause enableServicesPause = new CancellableTimedPause(250, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                asyncRequest.setCancelCallback(enableServicesPause::cancel);
                final Set<AffectedComponentEntity> servicesToEnable = getUpdatedEntities(enabledServices, user);
                logger.info("Successfully updated flow; re-enabling {} Controller Services", servicesToEnable.size());

                try {
                    componentLifecycle.activateControllerServices(exampleUri, user, groupId, servicesToEnable, ControllerServiceState.ENABLED, enableServicesPause);
                } catch (final IllegalStateException ise) {
                    // Component Lifecycle will re-enable the Controller Services only if they are valid. If IllegalStateException gets thrown, we need to provide
                    // a more intelligent error message as to exactly what happened, rather than indicate that the flow could not be updated.
                    throw new ResumeFlowException("Failed to re-enable Controller Services because " + ise.getMessage(), ise);
                }
            }

            if (!asyncRequest.isCancelled()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Restart {} Processors: {}", runningComponents.size(), runningComponents);
                }

                asyncRequest.update(new Date(), "Restarting Processors", 80);

                // Step 14. Restart all components
                final Set<AffectedComponentEntity> componentsToStart = getUpdatedEntities(runningComponents, user);
                final CancellableTimedPause startComponentsPause = new CancellableTimedPause(250, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                asyncRequest.setCancelCallback(startComponentsPause::cancel);
                logger.info("Restarting {} Processors", componentsToStart.size());

                try {
                    componentLifecycle.scheduleComponents(exampleUri, user, groupId, componentsToStart, ScheduledState.RUNNING, startComponentsPause);
                } catch (final IllegalStateException ise) {
                    // Component Lifecycle will restart the Processors only if they are valid. If IllegalStateException gets thrown, we need to provide
                    // a more intelligent error message as to exactly what happened, rather than indicate that the flow could not be updated.
                    throw new ResumeFlowException("Failed to restart components because " + ise.getMessage(), ise);
                }
            }
        }

        asyncRequest.setCancelCallback(null);
        if (asyncRequest.isCancelled()) {
            return null;
        }
        asyncRequest.update(new Date(), "Complete", 100);

        return serviceFacade.getVersionControlInformation(groupId);
    }
}