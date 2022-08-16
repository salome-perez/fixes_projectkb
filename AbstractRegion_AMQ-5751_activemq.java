public class AbstractRegion {
    @Override
    public void reapplyInterceptor() {
        destinationsLock.writeLock().lock();
        try {
            DestinationInterceptor destinationInterceptor = broker.getDestinationInterceptor();
            Map<ActiveMQDestination, Destination> map = getDestinationMap();
            for (ActiveMQDestination key : map.keySet()) {
                Destination destination = map.get(key);
                if (destination instanceof CompositeDestinationFilter) {
                    destination = ((CompositeDestinationFilter) destination).next;
                }
                if (destinationInterceptor != null) {
                    destination = destinationInterceptor.intercept(destination);
                }
                getDestinationMap().put(key, destination);
                Destination prev = destinations.put(key, destination);
                if (prev == null) {
                    updateRegionDestCounts(key, 1);
                }
            }
        } finally {
            destinationsLock.writeLock().unlock();
        }
    }

    @Override
    public void stop() throws Exception {
        started = false;
        destinationsLock.readLock().lock();
        try{
            for (Iterator<Destination> i = destinations.values().iterator(); i.hasNext();) {
                Destination dest = i.next();
                dest.stop();
            }
        } finally {
            destinationsLock.readLock().unlock();
        }

        destinationsLock.writeLock().lock();
        try {
            destinations.clear();
            regionStatistics.getAdvisoryDestinations().reset();
            regionStatistics.getDestinations().reset();
            regionStatistics.getAllDestinations().reset();
        } finally {
            destinationsLock.writeLock().unlock();
        }
    }

    protected List<Subscription> addSubscriptionsForDestination(ConnectionContext context, Destination dest) throws Exception {
        List<Subscription> rc = new ArrayList<Subscription>();
        // Add all consumers that are interested in the destination.
        for (Iterator<Subscription> iter = subscriptions.values().iterator(); iter.hasNext();) {
            Subscription sub = iter.next();
            if (sub.matches(dest.getActiveMQDestination())) {
                try {
                    ConnectionContext originalContext = sub.getContext() != null ? sub.getContext() : context;
                    dest.addSubscription(originalContext, sub);
                    rc.add(sub);
                } catch (SecurityException e) {
                    if (sub.isWildcard()) {
                        LOG.debug("Subscription denied for " + sub + " to destination " +
                            dest.getActiveMQDestination() +  ": " + e.getMessage());
                    } else {
                        throw e;
                    }
                }
            }
        }
        return rc;

    }

    @Override
    public void removeDestination(ConnectionContext context, ActiveMQDestination destination, long timeout)
            throws Exception {

        // No timeout.. then try to shut down right way, fails if there are
        // current subscribers.
        if (timeout == 0) {
            for (Iterator<Subscription> iter = subscriptions.values().iterator(); iter.hasNext();) {
                Subscription sub = iter.next();
                if (sub.matches(destination)) {
                    throw new JMSException("Destination still has an active subscription: " + destination);
                }
            }
        }

        if (timeout > 0) {
            // TODO: implement a way to notify the subscribers that we want to
            // take the down
            // the destination and that they should un-subscribe.. Then wait up
            // to timeout time before
            // dropping the subscription.
        }

        LOG.debug("{} removing destination: {}", broker.getBrokerName(), destination);

        destinationsLock.writeLock().lock();
        try {
            Destination dest = destinations.remove(destination);
            if (dest != null) {
                updateRegionDestCounts(destination, -1);

                // timeout<0 or we timed out, we now force any remaining
                // subscriptions to un-subscribe.
                for (Iterator<Subscription> iter = subscriptions.values().iterator(); iter.hasNext();) {
                    Subscription sub = iter.next();
                    if (sub.matches(destination)) {
                        dest.removeSubscription(context, sub, 0l);
                    }
                }
                destinationMap.removeAll(destination);
                dispose(context, dest);
                DestinationInterceptor destinationInterceptor = broker.getDestinationInterceptor();
                if (destinationInterceptor != null) {
                    destinationInterceptor.remove(dest);
                }

            } else {
                LOG.debug("Cannot remove a destination that doesn't exist: {}", destination);
            }
        } finally {
            destinationsLock.writeLock().unlock();
        }
    }

    @Override
    public Destination addDestination(ConnectionContext context, ActiveMQDestination destination,
            boolean createIfTemporary) throws Exception {

        destinationsLock.writeLock().lock();
        try {
            Destination dest = destinations.get(destination);
            if (dest == null) {
                if (destination.isTemporary() == false || createIfTemporary) {
                    // Limit the number of destinations that can be created if
                    // maxDestinations has been set on a policy
                    validateMaxDestinations(destination);

                    LOG.debug("{} adding destination: {}", broker.getBrokerName(), destination);
                    dest = createDestination(context, destination);
                    // intercept if there is a valid interceptor defined
                    DestinationInterceptor destinationInterceptor = broker.getDestinationInterceptor();
                    if (destinationInterceptor != null) {
                        dest = destinationInterceptor.intercept(dest);
                    }
                    dest.start();
                    destinations.put(destination, dest);
                    updateRegionDestCounts(destination, 1);
                    destinationMap.put(destination, dest);
                    addSubscriptionsForDestination(context, dest);
                }
                if (dest == null) {
                    throw new DestinationDoesNotExistException(destination.getQualifiedName());
                }
            }
            return dest;
        } finally {
            destinationsLock.writeLock().unlock();
        }
    }

}