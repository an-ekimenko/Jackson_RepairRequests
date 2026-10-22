package repair_requests.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Database entity class representing the 'RepairRequests' table.
 */
@Data
@NoArgsConstructor 
@AllArgsConstructor
@Entity 
@Table(name = "RepairRequests")
public class RepairRequests_Entity {
	
    @EmbeddedId 
    private RepairRequests_PK id;

    @Column(name = "dt_from") 
    private java.time.LocalDateTime dt_from;

    @Column(name = "dt_to") 
    private java.time.LocalDateTime dt_to;

    @Column(name = "ApplicationNumber_PON") 
    private String applicationNumber_PON;

    @Column(name = "ApplicationType") 
    private String applicationType;

    @Column(name = "Author") 
    private String author;

    @Column(name = "Status") 
    private String status;

    @Column(name = "NumberOfOverdueDays") 
    private Integer numberOfOverdueDays;

    @Column(name = "ReasonForNon_compliance") 
    private String reasonForNon_compliance;

    @Column(name = "RepairFacility") 
    private String repairFacility;

    @Column(name = "RepairFacilityCodeMDM") 
    private Integer repairFacilityCodeMDM;

    @Column(name = "PurchaseRequest") 
    private String purchaseRequest;

    @Column(name = "DescriptionOfRenovation") 
    private String descriptionOfRenovation;
}
