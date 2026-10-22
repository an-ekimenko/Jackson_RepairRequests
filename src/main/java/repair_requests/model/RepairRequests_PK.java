package repair_requests.model;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Embedded primary key class for the RepairRequests_Entity.
 */
@Data
@NoArgsConstructor 
@AllArgsConstructor
@Embeddable 
public class RepairRequests_PK implements Serializable {
    
    private static final long serialVersionUID = 1L;

    @Column(name = "Date")
    private java.time.LocalDateTime date;

    @Column(name = "ApplicationNumber")
    private Integer applicationNumber;

    @Column(name = "OrganizationCodeMDM")
    private Integer organizationCodeMDM;

    @Column(name = "DepartmentCodeMDM")
    private Integer departmentCodeMDM;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RepairRequests_PK that = (RepairRequests_PK) o;
        return Objects.equals(date, that.date) &&
               Objects.equals(applicationNumber, that.applicationNumber) &&
               Objects.equals(organizationCodeMDM, that.organizationCodeMDM) &&
               Objects.equals(departmentCodeMDM, that.departmentCodeMDM);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(date, applicationNumber, organizationCodeMDM, departmentCodeMDM);
    }
}
