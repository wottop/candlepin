/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.model;

import org.candlepin.exceptions.DuplicateEntryException;
import org.candlepin.jackson.HateoasArrayExclude;
import org.candlepin.jackson.HateoasInclude;
import org.candlepin.jackson.StringTrimmingConverter;
import org.candlepin.service.model.ConsumerInfo;
import org.candlepin.util.Util;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;



/**
 * A Consumer is the entity that uses a given Entitlement. It can be a user,
 * system, or anything else we want to track as using the Entitlement.
 *
 * Every Consumer has an Owner which may or may not own the Entitlement. The
 * Consumer's attributes or metadata is stored in a ConsumerInfo object which
 * boils down to a series of name/value pairs.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = Consumer.DB_TABLE)
@JsonFilter("ConsumerFilter")
public class Consumer extends AbstractHibernateObject implements Linkable, Owned, Named, ConsumerProperty,
    Eventful, ConsumerInfo {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_consumer";

    public static final int MAX_LENGTH_OF_CONSUMER_NAME = 255;

    /**
     * Commonly used/recognized consumer facts
     */
    public static final class Facts {
        /** Used by the rules.js */
        public static final String BAND_STORAGE_USAGE = "band.storage.usage";

        /** The number of cores per socket on a given consumer; also part of the cloud profile and rules.js */
        public static final String CPU_CORES_PER_SOCKET = "cpu.core(s)_per_socket";

        /** The number of sockets on a given consumer; also part of the cloud profile and rules.js */
        public static final String CPU_SOCKETS = "cpu.cpu_socket(s)";

        /** The developer SKU for a given consumer */
        public static final String DEV_SKU = "dev_sku";

        /** */
        public static final String DISTRIBUTOR_VERSION = "distributor_version";

        /** The consumer's system/hardware UUID; also part of the cloud profile */
        public static final String DMI_SYSTEM_UUID = "dmi.system.uuid";

        /** The amount of memory on a given consumer; also part of the cloud profile and rules.js */
        public static final String MEMORY_MEMTOTAL = "memory.memtotal";

        /** The version of the Candlepin certificate system to use for this consumer */
        public static final String SYSTEM_CERTIFICATE_VERSION = "system.certificate_version";

        /** Used by the rules.js */
        public static final String UNAME_MACHINE = "uname.machine";

        /** Whether or not the consumer is a virtual/guest system; also part of the cloud profile */
        public static final String VIRT_IS_GUEST = "virt.is_guest";

        /** The consumer's guest UUID; used to match them to a hypervisor on virt-who checkin */
        public static final String VIRT_UUID = "virt.uuid";

        // Cloud profile facts
        // These facts aren't used by Candlepin directly (other than to determine whether or not to
        // update the "last cloud profile update" timestamp), but are critical to the function of
        // some client applications, and must be maintained and passed through properly.
        public static final String DMI_BIOS_VENDOR = "dmi.bios.vendor";
        public static final String DMI_BIOS_VERSION = "dmi.bios.version";
        public static final String DMI_CHASSIS_ASSET_TAG = "dmi.chassis.asset_tag";
        public static final String DMI_SYSTEM_MANUFACTURER = "dmi.system.manufacturer";
        public static final String OCM_UNITS = "ocm.units";
    }


    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(nullable = false, unique = true)
    @Size(max = 255)
    @NotNull
    private String uuid;

    @Column(nullable = false)
    @Size(max = MAX_LENGTH_OF_CONSUMER_NAME)
    @NotNull
    private String name;

    // Represents the username used to register this consumer
    @Column
    @Size(max = 255)
    private String username;

    @Column(length = 32)
    @Size(max = 32)
    private String entitlementStatus;

    /**
     * Represents a 256 bit hash digest of the last calculated ComplianceStatus that was
     * generated by ComplianceRules.
     */
    @Column
    @Size(max = 64)
    private String complianceStatusHash;

    @Column(length = 255, nullable = true)
    @Type(type = "org.candlepin.hibernate.EmptyStringUserType")
    @Size(max = 255)
    private String serviceLevel;

    @Column(name = "sp_role", length = 255, nullable = true)
    @Type(type = "org.candlepin.hibernate.EmptyStringUserType")
    @Size(max = 255)
    private String role;

    @Column(name = "sp_usage", length = 255, nullable = true)
    @Type(type = "org.candlepin.hibernate.EmptyStringUserType")
    @Size(max = 255)
    private String usage;

    @ElementCollection
    @CollectionTable(name = "cp_sp_add_on", joinColumns = @JoinColumn(name = "consumer_id"))
    @Column(name = "add_on")
    private Set<String> addOns = new HashSet<>();

    @Column(name = "sp_status", length = 32)
    @Size(max = 32)
    private String systemPurposeStatus;

    /**
     * Represents a 256 bit hash digest of the last calculated system purpose status.
     */
    @Column(name = "sp_status_hash")
    @Size(max = 64)
    private String systemPurposeStatusHash;

    // for selecting Y/Z stream
    @Column(length = 255, nullable =  true)
    @Size(max = 255)
    private String releaseVer;

    /*
     * Because this object is used both as a Hibernate object, as well as a DTO to be
     * serialized and sent to callers, we do some magic with these two cert related
     * fields. The idCert is a database certificated that carries bytes, the identity
     * field is a DTO for transmission to the client carrying PEM in plain text, and is
     * not stored in the database.
     */
    @OneToOne
    @JoinColumn(name = "consumer_idcert_id")
    private IdentityCertificate idCert;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cont_acc_cert_id")
    private ContentAccessCertificate contentAccessCert;

    // Reference to the ConsumerType by ID
    @Column(name = "type_id")
    @NotNull
    private String typeId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false)
    private Owner owner;

    @Column(name = "entitlement_count")
    @NotNull
    private Long entitlementCount;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "consumer", fetch = FetchType.LAZY)
    private Set<Entitlement> entitlements;

    // TODO: FIXME:
    // Hibernate has a known issue with map-backed element collections that contain entries with
    // null values. Upon persist, and entries with null values will be silently discarded rather
    // than persisted, even if the configuration explicitly allows such a thing.
    @ElementCollection
    @CollectionTable(name = "cp_consumer_facts", joinColumns = @JoinColumn(name = "cp_consumer_id"))
    @MapKeyColumn(name = "mapkey")
    @Column(name = "element")
    //FIXME A cascade shouldn't be necessary here as ElementCollections cascade by default
    //See http://stackoverflow.com/a/7696147
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @JsonDeserialize(contentConverter = StringTrimmingConverter.class)
    private Map<String, String> facts;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "keypair_id")
    private KeyPairData keyPairData;

    private Date lastCheckin;

    @OneToMany(mappedBy = "consumer", orphanRemoval = true, cascade = { CascadeType.ALL })
    private Set<ConsumerInstalledProduct> installedProducts;

    @Transient
    private boolean canActivate;

    @BatchSize(size = 32)
    @OneToMany(mappedBy = "consumer",
        orphanRemoval = true, cascade = { CascadeType.ALL })
    private List<GuestId> guestIds;

    @OneToMany(mappedBy = "consumer",
        orphanRemoval = true, cascade = { CascadeType.ALL })
    private Set<ConsumerCapability> capabilities;

    @OneToOne(mappedBy = "consumer",
        orphanRemoval = true, cascade = { CascadeType.ALL })
    private HypervisorId hypervisorId;

    @Valid  // Enable validation.  See http://stackoverflow.com/a/13992948
    @ElementCollection
    @CollectionTable(name = "cp_consumer_content_tags", joinColumns = @JoinColumn(name = "consumer_id"))
    @Column(name = "content_tag")
    private Set<String> contentTags;

    // An instruction for the client to initiate an autoheal request.
    // WARNING: can't initialize to a default value here, we need to be able to see
    // if it was specified on an incoming update, so it must be null if no value came in.
    private Boolean autoheal;

    // This is normally used from the owner setting. If this consumer is manifest then it
    // can have a different setting as long as it exists in the owner's mode list.
    @Column(name = "content_access_mode")
    private String contentAccessMode;

    /**
     * Length of field is required by hypersonic in the unit tests only
     *
     * 4194304 bytes = 4 MB
     */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "annotations", length = 4194304)
    private String annotations;

    @Column(name = "rh_cloud_profile_modified")
    private Date rhCloudProfileModified;

    @Basic(fetch = FetchType.LAZY)
    @OneToMany(mappedBy = "consumer", orphanRemoval = true, cascade = { CascadeType.ALL })
    private Set<ConsumerActivationKey> activationKeys;

    @Column(name = "sp_service_type")
    @Type(type = "org.candlepin.hibernate.EmptyStringUserType")
    @Size(max = 255)
    private String serviceType;

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 1000)
    @CollectionTable(name = "cp_consumer_environments", joinColumns = @JoinColumn(name = "cp_consumer_id"))
    @MapKeyColumn(name = "priority")
    @Column(name = "environment_id")
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @Fetch(FetchMode.SELECT)
    private Map<String, String> environmentIds;

    public Consumer(String name, String userName, Owner owner, ConsumerType type) {
        this();

        this.name = name;
        this.username = userName;
        this.facts = new HashMap<>();
        this.installedProducts = new HashSet<>();
        this.guestIds = new ArrayList<>();
        this.autoheal = true;
        this.serviceLevel = "";
        this.entitlementCount = 0L;

        if (type != null) {
            this.setType(type);
        }

        if (owner != null) {
            this.setOwner(owner);
        }

        this.environmentIds = new LinkedHashMap<>();
    }

    public Consumer() {
        this.entitlements = new HashSet<>();
        this.setEntitlementCount(0L);
        this.environmentIds = new LinkedHashMap<>();
    }

    /**
     * @return the Consumer's UUID
     */
    @HateoasInclude
    public String getUuid() {
        return uuid;
    }

    @PrePersist
    public void ensureUUID() {
        if (uuid == null  || uuid.length() == 0) {
            this.uuid = Util.generateUUID();
        }
    }

    /**
     * @param uuid the UUID of this consumer.
     * @return this consumer.
     */
    public Consumer setUuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @HateoasInclude
    public String getId() {
        return id;
    }

    /**
     * @param id the db id.
     */
    public void setId(String id) {
        this.id = id;
    }

    @HateoasArrayExclude
    public IdentityCertificate getIdCert() {
        return idCert;
    }

    public void setIdCert(IdentityCertificate idCert) {
        this.idCert = idCert;
    }

    @HateoasArrayExclude
    @XmlTransient
    public ContentAccessCertificate getContentAccessCert() {
        return contentAccessCert;
    }

    public void setContentAccessCert(ContentAccessCertificate contentAccessCert) {
        this.contentAccessCert = contentAccessCert;
    }

    /**
     * @return the name of this consumer.
     */
    @HateoasInclude
    public String getName() {
        return name;
    }

    /**
     * @param name the name of this consumer.
     *
     * @return
     *  a reference to this consumer instance
     */
    public Consumer setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * @return the userName
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param userName the userName to set
     *
     * @return
     *  a reference to this consumer instance
     */
    public Consumer setUsername(String userName) {
        this.username = userName;
        return this;
    }

    /**
     * @return this consumers type.
     */
    public String getTypeId() {
        return this.typeId;
    }

    /**
     * Sets the ID of the consumer type to of this consumer.
     *
     * @param typeId
     *  The ID of the consumer type to use for this consumer
     *
     * @return
     *  a reference to this consumer instance
     */
    public Consumer setTypeId(String typeId) {
        this.typeId = typeId;
        this.updateRHCloudProfileModified();

        return this;
    }

    /**
     * Sets the consumer type of this consumer.
     *
     * @param type
     *  The ConsumerType instance to use as the type for this consumer
     *
     * @return
     *  a reference to this consumer instance
     */
    public Consumer setType(ConsumerType type) {
        if (type == null || type.getId() == null) {
            throw new IllegalArgumentException("type is null or has not been persisted");
        }

        return this.setTypeId(type.getId());
    }

    /**
     * @return the owner Id of this Consumer.
     */
    @Override
    public String getOwnerId() {
        return ownerId;
    }

    /**
     * Fetches the owner of this consumer, if the owner ID is set. This may perform a lazy lookup of the
     * owner, and should generally be avoided if the owner ID is sufficient.
     *
     * @return
     *  The owner of this consumer, if the owner ID is populated; null otherwise.
     */
    @Override
    public Owner getOwner() {
        return this.owner;
    }

    public Consumer setOwner(Owner owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner is null or lacks an ID");
        }

        this.owner = owner;
        this.ownerId = owner.getId();

        return this;
    }

    @Override
    public String toString() {
        return String.format("Consumer [id: %s, uuid: %s, name: %s]",
            this.getId(), this.getUuid(), this.getName());
    }

    /**
     * @return all facts about this consumer.
     */
    @HateoasArrayExclude
    public Map<String, String> getFacts() {
        return facts;
    }

    public boolean hasFact(String fact) {
        return facts != null && facts.containsKey(fact);
    }

    /**
     * Returns the value of the fact with the given key.
     * @param factKey specific fact to retrieve.
     * @return the value of the fact with the given key.
     */
    public String getFact(String factKey) {
        if (facts != null) {
            return facts.get(factKey);
        }
        return null;
    }

    /**
     * @param factsIn facts about this consumer.
     *
     * @return
     *  a reference to this consumer instance
     */
    public Consumer setFacts(Map<String, String> factsIn) {
        if (this.checkForCloudProfileFacts(factsIn)) {
            this.updateRHCloudProfileModified();
        }
        facts = factsIn;
        return this;
    }

    /**
     * Returns if the <code>otherFacts</code> are
     * the same as the facts of this consumer model entity.
     *
     * @param otherFacts the facts to compare
     * @return <code>true</code> if the facts are the same, <code>false</code> otherwise
     */
    public boolean factsAreEqual(Map<String, String> otherFacts) {
        if (this.getFacts() == null && otherFacts == null) {
            return true;
        }

        if (this.getFacts() == null || otherFacts == null) {
            return false;
        }

        if (this.getFacts().size() != otherFacts.size()) {
            return false;
        }

        for (Entry<String, String> entry : this.getFacts().entrySet()) {
            String myVal = entry.getValue();
            String otherVal = otherFacts.get(entry.getKey());

            if (myVal == null) {
                if (otherVal != null) {
                    return false;
                }
            }
            else if (!myVal.equals(otherVal)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Set a fact
     * @param name to set
     * @param value to set
     */
    public void setFact(String name, String value) {
        if (this.facts == null) {
            this.facts = new HashMap<>();
        }

        HashMap<String, String> fact = new HashMap<>();
        fact.put(name, value);
        if (this.checkForCloudProfileFacts(fact)) {
            this.updateRHCloudProfileModified();
        }

        this.facts.put(name, value);
    }

    public long getEntitlementCount() {
        if (entitlementCount == null) {
            return 0;
        }
        return entitlementCount.longValue();
    }

    public void setEntitlementCount(long count) {
        this.entitlementCount = count;
    }

    /**
     * @return Returns the entitlements.
     */
    @XmlTransient
    public Set<Entitlement> getEntitlements() {
        return entitlements;
    }

    /**
     * @param entitlementsIn The entitlements to set.
     */
    public void setEntitlements(Set<Entitlement> entitlementsIn) {
        entitlements = entitlementsIn;
    }

    /**
     * Add an Entitlement to this Consumer
     * @param entitlementIn to add to this consumer
     *
     */
    public void addEntitlement(Entitlement entitlementIn) {
        entitlementIn.setConsumer(this);
        this.entitlements.add(entitlementIn);
    }

    public void removeEntitlement(Entitlement entitlement) {
        this.entitlements.remove(entitlement);
    }

    /*
     * Only for internal use as a pojo for resource update.
     */
    public void setLastCheckin(Date lastCheckin) {
        this.lastCheckin = lastCheckin;
    }

    public KeyPairData getKeyPairData() {
        return keyPairData;
    }

    public Consumer setKeyPairData(KeyPairData keyPairData) {
        this.keyPairData = keyPairData;
        return this;
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof Consumer)) {
            return false;
        }

        Consumer another = (Consumer) anObject;

        return uuid.equals(another.getUuid());
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    @HateoasInclude
    public String getHref() {
        return "/consumers/" + getUuid();
    }

    public void setHref(String href) {
        /*
         * No-op, here to aid with updating objects which have nested objects that were
         * originally sent down to the client in HATEOAS form.
         */
    }

    public Date getLastCheckin() {
        return lastCheckin;
    }

    public boolean isCanActivate() {
        return canActivate;
    }

    public void setCanActivate(boolean canActivate) {
        this.canActivate = canActivate;
    }

    public Set<ConsumerInstalledProduct> getInstalledProducts() {
        return installedProducts;
    }

    public void setInstalledProducts(Set<ConsumerInstalledProduct> installedProducts) {
        this.installedProducts = installedProducts;
        this.updateRHCloudProfileModified();
    }

    public void addInstalledProduct(ConsumerInstalledProduct installed) {
        if (installedProducts == null) {
            installedProducts = new HashSet<>();
        }

        installed.setConsumer(this);
        installedProducts.add(installed);
        this.updateRHCloudProfileModified();
    }

    public Boolean isAutoheal() {
        return autoheal;
    }

    public void setAutoheal(Boolean autoheal) {
        this.autoheal = autoheal;
    }

    /**
     * @param guests the GuestIds to set
     */
    @JsonProperty
    public void setGuestIds(List<GuestId> guests) {
        this.guestIds = guests;
        this.updateRHCloudProfileModified();
    }

    /**
     * @return the guestIds
     */
    @JsonIgnore
    public List<GuestId> getGuestIds() {
        return guestIds;
    }

    public void addGuestId(GuestId guestId) {
        if (guestIds == null) {
            guestIds = new ArrayList<>();
        }
        guestId.setConsumer(this);
        guestIds.add(guestId);
        this.updateRHCloudProfileModified();
    }

    public String getEntitlementStatus() {
        return entitlementStatus;
    }

    public void setEntitlementStatus(String status) {
        this.entitlementStatus = status;
    }

    public String getServiceLevel() {
        return serviceLevel;
    }

    public void setServiceLevel(String level) {
        this.serviceLevel = level;
        this.updateRHCloudProfileModified();
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
        this.updateRHCloudProfileModified();
    }

    public String getUsage() {
        return usage;
    }

    public void setUsage(String usage) {
        this.usage = usage;
        this.updateRHCloudProfileModified();
    }

    public String getSystemPurposeStatus() {
        return this.systemPurposeStatus;
    }

    public void setSystemPurposeStatus(String systemPurposeStatus) {
        this.systemPurposeStatus = systemPurposeStatus;
    }

    @XmlTransient
    public String getSystemPurposeStatusHash() {
        return systemPurposeStatusHash;
    }

    public void setSystemPurposeStatusHash(String systemPurposeStatusHash) {
        this.systemPurposeStatusHash = systemPurposeStatusHash;
    }

    /**
     * Fetches the ID of the highest prioritized environment with which
     * this consumer is associated. If the consumer is not
     * associated with an environment, this method returns null.
     *
     * @return the ID of the environment for this consumer
     * TODO: This method is kept temporarily to support the existing functionality.
     * This needs to be removed in the later part of this epic.
     */
    public String getEnvironmentId() {
        return this.getEnvironmentIds() == null ? null : this.environmentIds.get("0");
    }

    /**
     * @param releaseVer the releaseVer to set
     */
    public void setReleaseVer(Release releaseVer) {
        if (releaseVer == null) {
            releaseVer = new Release();
        }
        this.releaseVer = releaseVer.getReleaseVer();
    }

    /**
     * @return the releaseVer
     */
    public Release getReleaseVer() {
        return new Release(releaseVer);
    }

    /**
     * @return the capabilities
     */
    public Set<ConsumerCapability> getCapabilities() {
        return capabilities;
    }

    /**
     * @param capabilities the capabilities to set
     */
    public void setCapabilities(Set<ConsumerCapability> capabilities) {
        if (capabilities == null) {
            return;
        }
        if (this.capabilities == null) {
            this.capabilities = new HashSet<>();
        }
        if (!this.capabilities.equals(capabilities)) {
            this.capabilities.clear();
            this.capabilities.addAll(capabilities);
            this.setUpdated(new Date());
            for (ConsumerCapability cc : this.capabilities) {
                cc.setConsumer(this);
            }
        }
    }

    /**
     * @return the hypervisorId
     */
    public HypervisorId getHypervisorId() {
        return hypervisorId;
    }

    /**
     * @param hypervisorId the hypervisorId to set
     *
     * @return
     *  a reference to this consumer instance
     */
    public Consumer setHypervisorId(HypervisorId hypervisorId) {
        if (hypervisorId != null) {
            hypervisorId.setConsumer(this);
        }

        this.hypervisorId = hypervisorId;
        this.updateRHCloudProfileModified();

        return this;
    }

    @XmlTransient
    public String getComplianceStatusHash() {
        return complianceStatusHash;
    }

    public void setComplianceStatusHash(String complianceStatusHash) {
        this.complianceStatusHash = complianceStatusHash;
    }

    public Set<String> getContentTags() {
        return contentTags;
    }

    public void setContentTags(Set<String> contentTags) {
        this.contentTags = contentTags;
    }

    @Override
    @XmlTransient
    public Consumer getConsumer() {
        return this;
    }

    public String getAnnotations() {
        return this.annotations;
    }

    public void setAnnotations(String annotations) {
        this.annotations = annotations;
    }

    public boolean isDev() {
        return !StringUtils.isEmpty(getFact(Facts.DEV_SKU));
    }

    @JsonIgnore
    public boolean isGuest() {
        return "true".equalsIgnoreCase(this.getFact(Facts.VIRT_IS_GUEST));
    }

    public String getContentAccessMode() {
        return this.contentAccessMode;
    }

    public void setContentAccessMode(String contentAccessMode) {
        this.contentAccessMode = contentAccessMode;
    }

    public Set<String> getAddOns() {
        return addOns;
    }

    public void setAddOns(Set<String> addOns) {
        this.addOns = addOns;
        this.updateRHCloudProfileModified();
    }

    public void addAddOn(String addOn) {
        this.addOns.add(addOn);
        this.updateRHCloudProfileModified();
    }

    public void removeAddOn(String addOn) {
        this.addOns.remove(addOn);
        this.updateRHCloudProfileModified();
    }

    public Date getRHCloudProfileModified() {
        return this.rhCloudProfileModified;
    }

    public void setRHCloudProfileModified(Date rhCloudProfileModified) {
        this.rhCloudProfileModified = rhCloudProfileModified;
    }

    public void updateRHCloudProfileModified() {
        this.rhCloudProfileModified = new Date();
    }

    /**
     * Check if the consumers facts have changed and contains the cloud profile facts.
     * It returns true if incoming facts are cloud profile facts
     *
     * @param incomingFacts incoming facts
     * @return a boolean
     */
    public boolean checkForCloudProfileFacts(Map<String, String> incomingFacts) {
        if (incomingFacts == null) {
            return false;
        }

        if (this.facts != null && this.facts.equals(incomingFacts)) {
            return false;
        }

        for (CloudProfileFacts profileFact : CloudProfileFacts.values()) {
            if (incomingFacts.containsKey(profileFact.getFact())) {
                if (this.facts == null ||
                    !this.facts.containsKey(profileFact.getFact()) ||
                    !this.facts.get(profileFact.getFact()).equals(incomingFacts.get(profileFact.getFact()))) {
                    return true;
                }
            }
        }

        return false;
    }

    public void setActivationKeys(Set<ConsumerActivationKey> activationKeys) {
        this.activationKeys = activationKeys;
    }

    public Set<ConsumerActivationKey> getActivationKeys() {
        return this.activationKeys;
    }

    public String getServiceType() {
        return serviceType;
    }

    public Consumer setServiceType(String serviceType) {
        this.serviceType = serviceType;
        return this;
    }

    public List<String> getEnvironmentIds() {
        return this.environmentIds.entrySet().stream()
            .sorted(Comparator.comparingInt(entry -> Integer.parseInt(entry.getKey())))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    public Consumer setEnvironmentIds(List<String> environmentIds) {
        if (this.environmentIds != null) {
            this.environmentIds.clear();
        }

        if (environmentIds != null) {
            HashSet<String> deDuplicatedIds = new HashSet<>(environmentIds);
            if (environmentIds.size() != deDuplicatedIds.size()) {
                throw new DuplicateEntryException("One or more environments were specified more than once.");
            }

            // Creating a new map instance and assigning it to the environmentIds.
            // This ensures that, we are deleting the existing entries and inserting the new entries.
            // This is done to avoid constraint (cp_consumer_environments_pkey) violation exception.
            // Whenever we try to update the existing data (changing priority), it actually updates
            // the environment Id and not the priority.
            LinkedHashMap<String, String> envMap = new LinkedHashMap<>();

            for (int i = 0; i < environmentIds.size(); i++) {
                envMap.put(String.valueOf(i), environmentIds.get(i));
            }

            this.environmentIds = envMap;
        }

        return this;
    }

    /**
     * It adds the consumer's environment.
     * It basically adds one after another with
     * the lowering priority.
     *
     * @param environment incoming environment
     * @return a consumer
     */
    public Consumer addEnvironment(Environment environment) {
        if (environment != null) {
            if (this.environmentIds == null) {
                this.environmentIds = new LinkedHashMap<>();
            }

            if (this.environmentIds.containsValue(environment.getId())) {
                throw new DuplicateEntryException("Environment " + environment.getId() +
                    " specified more than once.");
            }

            this.environmentIds.put(String.valueOf(this.environmentIds.size()), environment.getId());
        }
        return this;
    }

}
