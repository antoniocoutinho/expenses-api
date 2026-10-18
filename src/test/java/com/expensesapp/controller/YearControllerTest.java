package com.expensesapp.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.expensesapp.dto.MonthSummaryResponse;
import com.expensesapp.dto.YearResponse;
import com.expensesapp.exception.ConflictException;
import com.expensesapp.exception.NotFoundException;
import com.expensesapp.service.YearService;

/**
 * MockMvc tests for {@link YearController}, backing the Year View dashboard
 * (spec 2.A) and Month View grid (spec 2.B).
 */
@WebMvcTest(YearController.class)
class YearControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private YearService yearService;

    @Nested
    @DisplayName("GET /api/years")
    class ListYears {

        @Test
        @DisplayName("returns 200 with the list of year cards")
        void returnsYearCards() throws Exception {
            when(yearService.listYears()).thenReturn(List.of(
                    YearResponse.builder().year(2025).build(),
                    YearResponse.builder().year(2026).build()));

            mockMvc.perform(get("/api/years"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].year").value(2025))
                    .andExpect(jsonPath("$[1].year").value(2026));
        }

        @Test
        @DisplayName("returns 200 with an empty array when there are no years")
        void returnsEmptyArray() throws Exception {
            when(yearService.listYears()).thenReturn(List.of());

            mockMvc.perform(get("/api/years"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/years")
    class CreateYear {

        @Test
        @DisplayName("returns 201 with the created year")
        void returns201OnSuccess() throws Exception {
            when(yearService.createYear(2027)).thenReturn(YearResponse.builder().year(2027).build());

            mockMvc.perform(post("/api/years")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"year\": 2027}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.year").value(2027));
        }

        @Test
        @DisplayName("returns 400 when the year field is missing")
        void returns400WhenYearMissing() throws Exception {
            mockMvc.perform(post("/api/years")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 409 when the year already exists")
        void returns409OnConflict() throws Exception {
            when(yearService.createYear(2026)).thenThrow(new ConflictException("Year 2026 already exists"));

            mockMvc.perform(post("/api/years")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"year\": 2026}"))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("DELETE /api/years/{year}")
    class DeleteYear {

        @Test
        @DisplayName("returns 204 on successful deletion")
        void returns204OnSuccess() throws Exception {
            mockMvc.perform(delete("/api/years/2026"))
                    .andExpect(status().isNoContent());

            verify(yearService).deleteYear(2026);
        }

        @Test
        @DisplayName("returns 404 when the year does not exist")
        void returns404WhenMissing() throws Exception {
            org.mockito.Mockito.doThrow(new NotFoundException("Year 1900 not found"))
                    .when(yearService).deleteYear(1900);

            mockMvc.perform(delete("/api/years/1900"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/years/{year}/months")
    class GetMonths {

        @Test
        @DisplayName("returns 200 with 12 month summaries")
        void returnsTwelveMonths() throws Exception {
            List<MonthSummaryResponse> months = new java.util.ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                months.add(MonthSummaryResponse.builder()
                        .month(i)
                        .monthName("Month" + i)
                        .totalIncome(BigDecimal.ZERO)
                        .totalExpense(BigDecimal.ZERO)
                        .build());
            }
            when(yearService.getMonthSummaries(2026)).thenReturn(months);

            mockMvc.perform(get("/api/years/2026/months"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(12));
        }

        @Test
        @DisplayName("returns 404 for a year that doesn't exist")
        void returns404WhenYearMissing() throws Exception {
            when(yearService.getMonthSummaries(1900)).thenThrow(new NotFoundException("Year 1900 not found"));

            mockMvc.perform(get("/api/years/1900/months"))
                    .andExpect(status().isNotFound());
        }
    }
}
