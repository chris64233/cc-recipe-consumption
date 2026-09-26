package com.chris64233.recipeconsumption;

import tools.jackson.databind.ObjectMapper;
import com.chris64233.recipeconsumption.dto.AddVersionRequest;
import com.chris64233.recipeconsumption.dto.CompleteRequest;
import com.chris64233.recipeconsumption.dto.CreateBatchRequest;
import com.chris64233.recipeconsumption.dto.CreateOrderRequest;
import com.chris64233.recipeconsumption.dto.CreateRecipeRequest;
import com.chris64233.recipeconsumption.dto.IssueRequest;
import com.chris64233.recipeconsumption.dto.SubstitutionPreviewRequest;
import com.chris64233.recipeconsumption.domain.QualityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST 端到端：建档 → 领料（多批次/替代）→ 查询 → 完工核对的完整链路，
 * 以及关键错误状态码（404/409/422/400）与幂等行为。
 */
@AutoConfigureMockMvc
class ApiEndToEndTest extends AbstractServiceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        createStandardRecipe();
        createOrder("WO-1", "100");
        createBatch("A-1", MATERIAL_A, "150");
        createBatch("B-1", MATERIAL_B, "100");
        createBatch("C-1", MATERIAL_C, "100");
    }

    @Test
    void fullLifecycleOverHttp() throws Exception {
        // 领料：A 用替代 40B（折 50）+ 主料 150；C 100。
        String issueBody = objectMapper.writeValueAsString(new IssueRequest("IS-1", null, List.of(
                new IssueRequest.Line(MATERIAL_A, MATERIAL_B, new BigDecimal("40")),
                new IssueRequest.Line(MATERIAL_A, null, new BigDecimal("150")),
                new IssueRequest.Line(MATERIAL_C, null, new BigDecimal("100")))));
        mockMvc.perform(post("/api/orders/WO-1/issues")
                        .contentType(MediaType.APPLICATION_JSON).content(issueBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issueNo").value("IS-1"))
                .andExpect(jsonPath("$.lines[0].equivalentPrimaryQuantity").value(50.0000))
                .andExpect(jsonPath("$.lines[0].allocations[0].batchNo").value("B-1"));

        // 工单用料查询。
        mockMvc.perform(get("/api/orders/WO-1/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].substitutionRatio").value(0.25));

        // 替代试算。
        String previewBody = objectMapper.writeValueAsString(new SubstitutionPreviewRequest(List.of(
                new SubstitutionPreviewRequest.Line(MATERIAL_A, MATERIAL_B, new BigDecimal("10")))));
        mockMvc.perform(post("/api/orders/WO-1/substitutions/preview")
                        .contentType(MediaType.APPLICATION_JSON).content(previewBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].withinLimit").value(true));

        // 批次去向。
        mockMvc.perform(get("/api/batches/B-1/trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issues[0].orderNo").value("WO-1"));

        // 完工：实产 100，数量配平。
        String completeBody = objectMapper.writeValueAsString(
                new CompleteRequest("CP-1", new BigDecimal("100"), null, null));
        mockMvc.perform(post("/api/orders/WO-1/completions")
                        .contentType(MediaType.APPLICATION_JSON).content(completeBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.lines[0].balanced").value(true));

        // 产量差异查询返回完工实绩。
        mockMvc.perform(get("/api/orders/WO-1/variance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provisional").value(false));
    }

    @Test
    void duplicateIssueNoReturnsSameDocumentAndDoesNotDeductTwice() throws Exception {
        String body = objectMapper.writeValueAsString(new IssueRequest("IS-DUP", null, List.of(
                new IssueRequest.Line(MATERIAL_C, null, new BigDecimal("10")))));
        mockMvc.perform(post("/api/orders/WO-1/issues")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/orders/WO-1/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IssueRequest("IS-DUP", null, List.of(
                                new IssueRequest.Line(MATERIAL_C, null, new BigDecimal("90")))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].pickedQuantity").value(10.0000));
        mockMvc.perform(get("/api/batches/C-1"))
                .andExpect(jsonPath("$.availableQuantity").value(90.0000));
    }

    @Test
    void insufficientStockReturns422AndNothingIsDeducted() throws Exception {
        // C 只有 100，请求 101；A 也不应被扣。
        String body = objectMapper.writeValueAsString(new IssueRequest("IS-BAD", null, List.of(
                new IssueRequest.Line(MATERIAL_A, null, new BigDecimal("150")),
                new IssueRequest.Line(MATERIAL_C, null, new BigDecimal("101")))));
        mockMvc.perform(post("/api/orders/WO-1/issues")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("QUANTITY_NOT_BALANCED"));
        mockMvc.perform(get("/api/batches/A-1"))
                .andExpect(jsonPath("$.availableQuantity").value(150.0000));
    }

    @Test
    void unbalancedCompletionReturns422AndOrderStaysOpen() throws Exception {
        String body = objectMapper.writeValueAsString(new IssueRequest("IS-1", null, List.of(
                new IssueRequest.Line(MATERIAL_A, null, new BigDecimal("200")))));
        // A 库存 150，本身就会 422。改为只验证完工不平衡：先只领 C。
        mockMvc.perform(post("/api/orders/WO-1/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IssueRequest("IS-1", null, List.of(
                                new IssueRequest.Line(MATERIAL_C, null, new BigDecimal("100")))))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders/WO-1/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CompleteRequest("CP-BAD", new BigDecimal("100"), null, null))))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/orders/WO-1"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void issueOnCompletedOrderReturns409() throws Exception {
        String issueBody = objectMapper.writeValueAsString(new IssueRequest("IS-1", null, List.of(
                new IssueRequest.Line(MATERIAL_A, MATERIAL_B, new BigDecimal("40")),
                new IssueRequest.Line(MATERIAL_A, null, new BigDecimal("150")),
                new IssueRequest.Line(MATERIAL_C, null, new BigDecimal("100")))));
        mockMvc.perform(post("/api/orders/WO-1/issues")
                .contentType(MediaType.APPLICATION_JSON).content(issueBody)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/orders/WO-1/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CompleteRequest("CP-1", new BigDecimal("100"), null, null))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders/WO-1/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IssueRequest("IS-2", null, List.of(
                                new IssueRequest.Line(MATERIAL_C, null, new BigDecimal("1")))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void invalidRequestReturns400() throws Exception {
        // 缺 lines。
        mockMvc.perform(post("/api/orders/WO-1/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"issueNo\":\"IS-X\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrderReturns404() throws Exception {
        mockMvc.perform(get("/api/orders/NOPE/usage"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void quarantinedBatchIsNotUsableAndShownAsSuch() throws Exception {
        masterDataService.createBatch(new CreateBatchRequest(
                "Q-1", "Q", "冻结料", new BigDecimal("5"),
                QualityStatus.QUARANTINED, LocalDate.now().plusDays(10)));
        mockMvc.perform(get("/api/batches/Q-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usable").value(false))
                .andExpect(jsonPath("$.qualityStatus").value("QUARANTINED"));
    }
}
