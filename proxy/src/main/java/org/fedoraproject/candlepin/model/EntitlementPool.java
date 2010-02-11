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

import org.fedoraproject.candlepin.DateSource;

import org.hibernate.annotations.CollectionOfElements;
import org.hibernate.annotations.ForeignKey;

import java.util.Date;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Represents a pool of products eligible to be consumed (entitled).
 * For every Product there will be a corresponding Pool.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_entitlement_pool")
@SequenceGenerator(name="seq_entitlement_pool", sequenceName="seq_entitlement_pool", allocationSize=1)
public class EntitlementPool implements Persisted {
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="seq_entitlement_pool")
    private Long id;
    
    @ManyToOne
    @ForeignKey(name = "fk_entitlement_pool_owner")
    @JoinColumn(nullable = false)
    private Owner owner;
    
    @ManyToOne
    @ForeignKey(name = "fk_entitlement_pool_consumer")
    @JoinColumn(nullable = true)
    private Consumer consumer;

    private Boolean activeSubscription = Boolean.TRUE;

    // An identifier for the subscription this pool is associated with. Note that
    // this is not a database foreign key. The subscription identified could exist
    // in another system only accessible to us as a service. Actual implementations
    // of our SubscriptionService will be used to use this data.
    private Long subscriptionId;

    /* Indicates this pool was created as a result of granting an entitlement.
     * Allows us to know that we need to clean this pool up if that entitlement
     * if ever revoked. */
    @ManyToOne
    @ForeignKey(name = "fk_entitlement_pool_source_entitlement")
    @JoinColumn(nullable = true)
    private Entitlement sourceEntitlement;

    @Column(nullable = false)
    private Long maxMembers;

    @Column(nullable = false)
    private Long currentMembers;

    @Column(nullable = false)
    private Date startDate;
    
    @Column(nullable = false)
    private Date endDate;
    
    @Column(nullable = true)
    private String productId;    

    @CollectionOfElements
    @JoinTable(name="ENTITLEMENT_POOL_ATTRIBUTE")
    private Set<Attribute> attributes;

    public EntitlementPool() {
    }

    public EntitlementPool(Owner ownerIn, String productIdIn, Long maxMembersIn, 
            Date startDateIn, Date endDateIn) {
        this.owner = ownerIn;
        this.productId = productIdIn;
        this.maxMembers = maxMembersIn;
        this.startDate = startDateIn;
        this.endDate = endDateIn;
        
        // Always assume no current members if creating a new pool.
        this.currentMembers = new Long(0);
    }
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductId() {
        return productId;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumerIn) {
        this.consumer = consumerIn;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }
    
    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public Long getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(Long maxMembers) {
        this.maxMembers = maxMembers;
    }

    public Long getCurrentMembers() {
        return currentMembers;
    }

    public void setCurrentMembers(long currentMembers) {
        this.currentMembers = currentMembers;
    }
    
    public Owner getOwner() {
        return owner;
    }

    @XmlTransient
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    /**
     * Add 1 to the current members.
     */
    public void bumpCurrentMembers() {
        this.currentMembers = this.currentMembers + 1;
    }

	public Set<Attribute> getAttributes() {
		return attributes;
	}

	public void setAttributes(Set<Attribute> attributes) {
		this.attributes = attributes;
	}
	
	public boolean isExpired(DateSource dateSource) {
	    return getEndDate().before(dateSource.currentDate());
	}
	
    public boolean entitlementsAvailable() {
        if (isUnlimited()) {
            return true;
        }

        if (getCurrentMembers() < getMaxMembers()) {
            return true;
        }
        return false;
    }
	/**
	 * @return True if entitlement pool is unlimited.
	 */
	public boolean isUnlimited() {
		if (this.getMaxMembers() < 0) {
			return true;
		}
		return false;
	}

    public Entitlement getSourceEntitlement() {
        return sourceEntitlement;
    }

    public void setSourceEntitlement(Entitlement sourceEntitlement) {
        this.sourceEntitlement = sourceEntitlement;
    }

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public Boolean getActiveSubscription() {
        return activeSubscription;
    }

    public void setActiveSubscription(Boolean activeSubscription) {
        this.activeSubscription = activeSubscription;
    }

    public Boolean isActive() {
        return activeSubscription;
    }
}
