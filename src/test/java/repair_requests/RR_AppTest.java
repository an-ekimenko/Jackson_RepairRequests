package repair_requests;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import repair_requests.hibernate.RepairRequests_DAO;
import repair_requests.jackson_json.RR_DataDeserializer;
import repair_requests.model.RepairRequests_Data;
import repair_requests.model.RepairRequests_Entity;
import repair_requests.network.RR_HttpClient;

@ExtendWith(MockitoExtension.class)
class RR_AppTest {

    @Mock
    private RR_HttpClient mockHttpClient;

    @Mock
    private RepairRequests_DAO mockDao;

    @Captor
    private ArgumentCaptor<List<RepairRequests_Entity>> entitiesCaptor;

    private RR_App app;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(RepairRequests_Data.class, new RR_DataDeserializer());
        mapper.registerModule(module);

        app = new RR_App(mockHttpClient, mapper, mockDao);
    }

    @Test
    @DisplayName("Should filter by allowed SUID codes (_CF, _PPS, _VC), deduplicate by PK, and persist batch")
    void testPipelineFiltersDeduplicatesAndPersists() throws Exception {
        String jsonPayload = """
                [
                  {
                    "Дата": "10.05.2026 12:00:00",
                    "Номер": "101",
                    "ОрганизацияКодНСИ": "1",
                    "ПодразделениеКодНСИ": "10",
                    "Статус": "Новая",
                    "КлассификаторОРКодировкаСУИД": "EQ_GROUP_CF"
                  },
                  {
                    "Дата": "10.05.2026 12:00:00",
                    "Номер": "101",
                    "ОрганизацияКодНСИ": "1",
                    "ПодразделениеКодНСИ": "10",
                    "Статус": "Закрыта",
                    "КлассификаторОРКодировкаСУИД": "EQ_GROUP_CF"
                  },
                  {
                    "Дата": "11.05.2026 14:30:00",
                    "Номер": "102",
                    "ОрганизацияКодНСИ": "1",
                    "ПодразделениеКодНСИ": "20",
                    "Статус": "В работе",
                    "КлассификаторОРКодировкаСУИД": "EQ_GROUP_PPS"
                  },
                  {
                    "Дата": "12.05.2026 08:15:00",
                    "Номер": "103",
                    "ОрганизацияКодНСИ": "2",
                    "ПодразделениеКодНСИ": "30",
                    "Статус": "Новая",
                    "КлассификаторОРКодировкаСУИД": "EQ_GROUP_VC"
                  },
                  {
                    "Дата": "12.05.2026 09:00:00",
                    "Номер": "104",
                    "ОрганизацияКодНСИ": "2",
                    "ПодразделениеКодНСИ": "30",
                    "Статус": "Новая",
                    "КлассификаторОРКодировкаСУИД": "EQ_GROUP_TRK"
                  }
                ]
                """;

        when(mockHttpClient.fetchRepairRequestsJson()).thenReturn(jsonPayload);

        int processedCount = app.run();

        // 5 input items -> 1 filtered out (_TRK), 1 deduplicated (Номер 101) -> 3 persisted
        assertEquals(3, processedCount);
        verify(mockDao, times(1)).saveOrUpdateBatch(entitiesCaptor.capture());

        List<RepairRequests_Entity> saved = entitiesCaptor.getValue();
        assertEquals(3, saved.size());

        // Verify duplicate replacement kept the latest record ("Закрыта") for application 101
        RepairRequests_Entity app101 = saved.stream()
                .filter(e -> e.getId().getApplicationNumber() == 101)
                .findFirst()
                .orElseThrow();
        assertEquals("Закрыта", app101.getStatus());
    }

    @Test
    @DisplayName("Should skip DAO call and return 0 when API returns empty payload")
    void testEmptyApiResponseSkipsPersistence() throws Exception {
        when(mockHttpClient.fetchRepairRequestsJson()).thenReturn("   ");

        int processedCount = app.run();

        assertEquals(0, processedCount);
        verify(mockDao, never()).saveOrUpdateBatch(anyList());
    }

    @Test
    @DisplayName("Should skip DAO call and return 0 when no records match allowed SUID suffixes")
    void testNoMatchingEquipmentSkipsPersistence() throws Exception {
        String jsonPayload = """
                [
                  {
                    "Дата": "12.05.2026 09:00:00",
                    "Номер": "205",
                    "ОрганизацияКодНСИ": "2",
                    "ПодразделениеКодНСИ": "30",
                    "КлассификаторОРКодировкаСУИД": "OTHER_SYSTEM_CODE"
                  }
                ]
                """;
        when(mockHttpClient.fetchRepairRequestsJson()).thenReturn(jsonPayload);

        int processedCount = app.run();

        assertEquals(0, processedCount);
        verify(mockDao, never()).saveOrUpdateBatch(anyList());
    }
}
