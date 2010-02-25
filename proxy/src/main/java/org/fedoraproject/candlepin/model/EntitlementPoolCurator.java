/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.fedoraproject.candlepin.model;

import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

import org.hibernate.criterion.Restrictions;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * EntitlementPoolCurator
 */
public class EntitlementPoolCurator extends AbstractHibernateCurator<EntitlementPool> {

    private SubscriptionServiceAdapter subAdapter;
    

    @Inject
    protected EntitlementPoolCurator(SubscriptionServiceAdapter subAdapter) {
        super(EntitlementPool.class);
        this.subAdapter = subAdapter;
    }

    /**
     * Returns list of pools owned by the given Owner.
     * @param o Owner to filter
     * @return pools owned by the given Owner.
     */
    @SuppressWarnings("unchecked")
    public List<EntitlementPool> listByOwner(Owner o) {
        List<EntitlementPool> results = (List<EntitlementPool>) currentSession()
            .createCriteria(EntitlementPool.class)
            .add(Restrictions.eq("owner", o)).list();
        if (results == null) {
            return new LinkedList<EntitlementPool>();
        }
        else {
            return results;
        }
    }
    
    
    /**
     * Returns list of pools available for the consumer.
     *
     * @param c Consumer to filter
     * @return pools owned by the given Owner.
     */
    @SuppressWarnings("unchecked")
    public List<EntitlementPool> listAvailableEntitlementPools(Consumer c) {
        List<EntitlementPool> results = (List<EntitlementPool>) currentSession()
            .createCriteria(EntitlementPool.class)
            .add(Restrictions.eq("activeSubscription", Boolean.TRUE))
            .add(Restrictions.eq("owner", c.getOwner())).list();
            // FIXME: is start date now or earlier?
            // FIXME: is end date later?
            // FIXME: sort by enddate?
            // FIXME: currentmembers < maxmembers
            // FIXME: do we need to run through rules for each of these? (expensive!)
        if (results == null) {
            return new LinkedList<EntitlementPool>();
        }
        else {
            return results;
        }
    }
    
    /**
     * Before executing any entitlement pool query, check our underlying
     * subscription service and update the pool data. Must be careful to call
     * this before we do any pool query. Note that refreshing the pools doesn't
     * actually take any action, should a subscription be reduced, expired, or
     * revoked. Pre-existing entitlements will need to be dealt with separately
     * from this event.
     *
     * @param owner Owner to be refreshed.
     * @param product Products to refresh.
     */
    private void refreshPools(Owner owner, Product product) {
        List<Subscription> subs = subAdapter.getSubscriptions(owner, 
                product.getId().toString());
        List<EntitlementPool> pools = listByOwnerAndProductNoRefresh(owner, product);
        
        // Map all entitlement pools for this owner/product that have a
        // subscription ID associated with them.
        Map<Long, EntitlementPool> subToPoolMap = new HashMap<Long, EntitlementPool>();
        for (EntitlementPool p : pools) {
            if (p.getSubscriptionId() != null) {
                subToPoolMap.put(p.getSubscriptionId(), p);
            }
        }
        
        for (Subscription sub : subs) {
            // No pool exists for this subscription, create one:
            if (!subToPoolMap.containsKey(sub.getId())) {
                EntitlementPool newPool = new EntitlementPool(owner, product.getId(), 
                        sub.getQuantity(), sub.getStartDate(), sub.getEndDate());
                newPool.setSubscriptionId(sub.getId());
                create(newPool);
                subToPoolMap.remove(sub.getId());
            }
            else {
                EntitlementPool existingPool = subToPoolMap.get(sub.getId());
                
                // TODO: We're just updating the pool always now, would be much
                // better if we could check some kind of last modified date to
                // determine if a change has taken place:
                existingPool.setMaxMembers(sub.getQuantity());
                existingPool.setStartDate(sub.getStartDate());
                existingPool.setEndDate(sub.getEndDate());
                merge(existingPool);
            }
        }

        // Iterate pools whose subscription disappeared:
        for (Entry<Long, EntitlementPool> entry : subToPoolMap.entrySet()) {
            entry.getValue().setActiveSubscription(Boolean.FALSE);
            merge(entry.getValue());
        }

    }

    /**
     * List all entitlement pools for the given owner and product.
     * 
     * @param owner owner of the entitlement pool
     * @param product product filter.
     * @return list of EntitlementPools for the given owner, consumer, product
     * combination.
     */
    public List<EntitlementPool> listByOwnerAndProduct(Owner owner,
            Product product) {
        refreshPools(owner, product);
        return listByOwnerAndProductNoRefresh(owner, product);
    }
    
    @SuppressWarnings("unchecked")
    private List<EntitlementPool> listByOwnerAndProductNoRefresh(Owner owner,
        Product product) {
        return (List<EntitlementPool>) currentSession().createCriteria(
                EntitlementPool.class)
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.eq("productId", product.getId())).list();
    }
    
    /**
     * @param entitlementPool entitlement pool to search.
     * @return entitlements in the given pool.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public List<Entitlement> entitlementsIn(EntitlementPool entitlementPool) {
        return currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("pool", entitlementPool))
            .list();
    }
}
