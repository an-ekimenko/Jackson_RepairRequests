package repair_requests.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A DTO class representing a repair request object mapped from JSON data.
 */
@Data
@NoArgsConstructor 
@AllArgsConstructor
public class RepairRequests_Data {

    @JsonProperty("Дата") 
    private java.time.LocalDateTime date;
    
    @JsonProperty("ДатаНачала") 
    private java.time.LocalDateTime dt_from;
    
    @JsonProperty("ДатаОкончания") 
    private java.time.LocalDateTime dt_to;
    
    @JsonProperty("Номер") 
    private Integer applicationNumber; 
    
    @JsonProperty("НомерЗаявкиНаПОНе") 
    private String applicationNumber_PON;
    
    @JsonProperty("Организация") 
    private String organization;
    
    @JsonProperty("ОрганизацияКодНСИ") 
    private Integer organizationCodeMDM;
    
    @JsonProperty("Подразделение") 
    private String department;
    
    @JsonProperty("ПодразделениеКодНСИ") 
    private Integer departmentCodeMDM;
    
    @JsonProperty("ТипЗаявки") 
    private String applicationType;
    
    @JsonProperty("Автор") 
    private String author;
    
    @JsonProperty("Статус") 
    private String status;
    
    @JsonProperty("КоличествоПросроченныхДней") 
    private Integer numberOfOverdueDays;
    
    @JsonProperty("ПричинаНевыполнения") 
    private String reasonForNon_compliance;
    
    @JsonProperty("ОбъектРемонта") 
    private String repairFacility;
    
    @JsonProperty("ОбъектРемонтаКодНСИ") 
    private Integer repairFacilityCodeMDM; 
    
    @JsonProperty("ЗаявкаНаЗакупку") 
    private String purchaseRequest;
    
    @JsonProperty("ОписаниеРемонта") 
    private String descriptionOfRenovation;
    
    @JsonProperty("КлассификаторОР") 
    private String repairFacilityClassifier;
    
    @JsonProperty("КлассификаторОРКодНСИ") 
    private Integer classifierCodeMDM;
    
    @JsonProperty("КлассификаторОРКодировкаСУИД") 
    private String classifierCodeSUID;
}
