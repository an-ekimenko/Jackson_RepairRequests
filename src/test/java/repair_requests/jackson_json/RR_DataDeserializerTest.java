package repair_requests.jackson_json;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import repair_requests.model.RepairRequests_Data;

class RR_DataDeserializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        SimpleModule module = new SimpleModule();
        module.addDeserializer(
                RepairRequests_Data.class,
                new RR_DataDeserializer(List.of("_CF", "_PPS", "_VC"))
        );
        objectMapper.registerModule(module);
    }

    @Test
    @DisplayName("Should deserialize complete valid JSON object from 1C:TOiR")
    void testValidDeserialization() throws Exception {
        String json = """
                {
                  "Дата": "15.03.2026 9:05:30",
                  "ДатаНачала": "16.03.2026 10:00:00",
                  "ДатаОкончания": "18.03.2026 18:30:00",
                  "Номер": "1045",
                  "НомерЗаявкиНаПОНе": "PON-7788",
                  "Организация": "Белоруснефть-Гомельоблнефтепродукт",
                  "ОрганизацияКодНСИ": "12",
                  "Подразделение": "АЗС №45",
                  "ПодразделениеКодНСИ": "450",
                  "ТипЗаявки": "Аварийная",
                  "Автор": "Иванов И.И.",
                  "Статус": "В работе",
                  "КоличествоПросроченныхДней": "3",
                  "ПричинаНевыполнения": "Ожидание запчастей",
                  "ОбъектРемонта": "Кофемашина WMF",
                  "ОбъектРемонтаКодНСИ": "9901",
                  "ЗаявкаНаЗакупку": "ЗЗ-00123",
                  "ОписаниеРемонта": "Замена помпы",
                  "КлассификаторОРКодировкаСУИД": "AZS_EQ_CF",
                  "UnknownFieldFrom1C": "ShouldBeIgnored"
                }
                """;

        RepairRequests_Data result = objectMapper.readValue(json, RepairRequests_Data.class);

        assertNotNull(result);
        assertEquals(LocalDateTime.of(2026, 3, 15, 9, 5, 30), result.getDate());
        assertEquals(LocalDateTime.of(2026, 3, 16, 10, 0, 0), result.getDt_from());
        assertEquals(LocalDateTime.of(2026, 3, 18, 18, 30, 0), result.getDt_to());
        assertEquals(1045, result.getApplicationNumber());
        assertEquals("PON-7788", result.getApplicationNumber_PON());
        assertEquals(12, result.getOrganizationCodeMDM());
        assertEquals(450, result.getDepartmentCodeMDM());
        assertEquals(3, result.getNumberOfOverdueDays());
        assertEquals(9901, result.getRepairFacilityCodeMDM());
        assertEquals("AZS_EQ_CF", result.getClassifierCodeSUID());
    }

    @Test
    @DisplayName("Should return null when SUID code does not match configured equipment list")
    void testNonMatchingEquipmentReturnsNull() throws Exception {
        String json = """
                {
                  "Дата": "15.03.2026 9:05:30",
                  "Номер": "999",
                  "КлассификаторОРКодировкаСУИД": "AZS_OTHER_TRK"
                }
                """;

        RepairRequests_Data result = objectMapper.readValue(json, RepairRequests_Data.class);
        assertNull(result);
    }

    @Test
    @DisplayName("Should handle blank and missing dates gracefully by returning null")
    void testBlankAndMissingDates() throws Exception {
        String json = """
                {
                  "Дата": "",
                  "ДатаНачала": "   ",
                  "Номер": "500",
                  "КлассификаторОРКодировкаСУИД": "AZS_CF"
                }
                """;

        RepairRequests_Data result = objectMapper.readValue(json, RepairRequests_Data.class);

        assertNotNull(result);
        assertNull(result.getDate());
        assertNull(result.getDt_from());
        assertNull(result.getDt_to());
        assertEquals(500, result.getApplicationNumber());
    }

    @ParameterizedTest
    @CsvSource({
            "'00042', 42",
            "'№ 123-А', 123",
            "'-15 дн.', -15"
    })
    @DisplayName("Should sanitize dirty numeric strings and extract integer values")
    void testDirtyNumericParsing(String rawNumber, int expected) throws Exception {
        String json = String.format(
                "{\"Номер\": \"%s\", \"КлассификаторОРКодировкаСУИД\": \"AZS_CF\"}",
                rawNumber
        );
        RepairRequests_Data result = objectMapper.readValue(json, RepairRequests_Data.class);

        assertNotNull(result);
        assertEquals(expected, result.getApplicationNumber());
    }

    @ParameterizedTest
    @CsvSource({
            "'нет номера'",
            "'-'",
            "'99999999999999999999'",
            "''"
    })
    @DisplayName("Should return null on unparseable strings or Integer overflow")
    void testInvalidOrOverflowNumericParsing(String rawNumber) throws Exception {
        String json = String.format(
                "{\"КоличествоПросроченныхДней\": \"%s\", \"КлассификаторОРКодировкаСУИД\": \"AZS_CF\"}",
                rawNumber
        );
        RepairRequests_Data result = objectMapper.readValue(json, RepairRequests_Data.class);

        assertNotNull(result);
        assertNull(result.getNumberOfOverdueDays());
    }
}
