package interview.guide.modules.resume;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import interview.guide.infrastructure.persistence.DatabaseHealthService;
import interview.guide.infrastructure.persistence.DatabaseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ResumeHealthController.class)
class ResumeHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DatabaseHealthService databaseHealthService;

    @Test
    void returnsUnifiedHealthResponseWhenDatabaseIsAvailable() throws Exception {
        given(databaseHealthService.check()).willReturn(DatabaseStatus.up());

        mockMvc.perform(get("/api/resumes/health").header("X-Trace-Id", "test-trace-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "test-trace-id"))
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.message", is("success")))
                .andExpect(jsonPath("$.data.application", is("UP")))
                .andExpect(jsonPath("$.data.database", is("UP")))
                .andExpect(jsonPath("$.data.traceId", is("test-trace-id")));
    }

    @Test
    void returnsUnifiedValidationErrorForIllegalParameters() throws Exception {
        mockMvc.perform(get("/api/resumes/health").param("detail", "verbose"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(10001)))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}

