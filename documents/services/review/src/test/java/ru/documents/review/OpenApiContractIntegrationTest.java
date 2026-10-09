package ru.documents.review;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void export() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String dir = System.getProperty("contract.dir");
        if (dir == null) {
            return;
        }
        ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        Path out = Path.of(dir, "review.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, om.writeValueAsString(om.readValue(json, Object.class)) + "\n");
    }
}
