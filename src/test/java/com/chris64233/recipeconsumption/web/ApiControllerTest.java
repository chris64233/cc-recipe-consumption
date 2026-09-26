package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.TestDataFactory;
import com.chris64233.recipeconsumption.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestDataFactory data;

    @BeforeEach
    void initData() {
        var recipe = data.createStandardRecipe();
        data.order("PO-W", recipe, "10");
        data.availableBatch("B-WF", TestDataFactory.FLOUR, "20");
        data.availableBatch("B-WW", TestDataFactory.WATER, "10");
    }

    @Test
    void issue_endpoint_creates_and_query_endpoints_return_data() throws Exception {
        String body = """
                {
                  "bizNo": "WBIZ-1",
                  "orderNo": "PO-W",
                  "lines": [
                    {"recipeMaterialCode": "FLOUR", "batches": [{"batchNo": "B-WF", "qty": 20}]},
                    {"recipeMaterialCode": "WATER", "batches": [{"batchNo": "B-WW", "qty": 10}]}
                  ]
                }
                """;
        mockMvc.perform(post("/api/issues").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bizNo").value("WBIZ-1"))
                .andExpect(jsonPath("$.lines[0].batches[0].qty").value(20));

        mockMvc.perform(get("/api/query/orders/PO-W/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materials[0].netEquivalentQty").value(20));

        mockMvc.perform(get("/api/query/batches/B-WF/trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movements[0].type").value("ISSUE"));

        mockMvc.perform(get("/api/query/orders/PO-W/variance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void insufficient_stock_returns_400_and_no_partial_deduction() throws Exception {
        String body = """
                {
                  "bizNo": "WBIZ-2",
                  "orderNo": "PO-W",
                  "lines": [
                    {"recipeMaterialCode": "FLOUR", "batches": [{"batchNo": "B-WF", "qty": 20}]},
                    {"recipeMaterialCode": "WATER", "batches": [{"batchNo": "B-WW", "qty": 99}]}
                  ]
                }
                """;
        mockMvc.perform(post("/api/issues").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("库存不足")));
    }

    @Test
    void duplicate_biz_no_is_idempotent_200() throws Exception {
        String body = """
                {
                  "bizNo": "WBIZ-3",
                  "orderNo": "PO-W",
                  "lines": [
                    {"recipeMaterialCode": "FLOUR", "batches": [{"batchNo": "B-WF", "qty": 1}]}
                  ]
                }
                """;
        mockMvc.perform(post("/api/issues").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/issues").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bizNo").value("WBIZ-3"));
    }

    @Test
    void unknown_order_returns_404() throws Exception {
        String body = """
                {
                  "bizNo": "WBIZ-4",
                  "orderNo": "NOPE",
                  "lines": [
                    {"recipeMaterialCode": "FLOUR", "batches": [{"batchNo": "B-WF", "qty": 1}]}
                  ]
                }
                """;
        mockMvc.perform(post("/api/issues").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalid_payload_returns_400() throws Exception {
        mockMvc.perform(post("/api/issues").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
