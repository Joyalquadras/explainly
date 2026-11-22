package com.explainly.support;

import com.explainly.common.Json;
import com.explainly.youtube.VideoMetadata;
import com.explainly.youtube.YouTubeMetadataClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAiConfig.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @MockitoBean
    protected YouTubeMetadataClient metadataClient;

    @BeforeEach
    void stubMetadata() {
        when(metadataClient.fetch(anyString())).thenAnswer(inv -> new VideoMetadata(inv.getArgument(0),
                "Spring Boot in 100 seconds", "Test Channel", "desc", "https://i.ytimg.com/vi/x/hqdefault.jpg", 120, null, true));
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return Json.MAPPER.readTree(result.getResponse().getContentAsString());
    }

    protected String guestToken() throws Exception {
        return json(mvc.perform(post("/api/auth/guest")).andReturn()).path("accessToken").asText();
    }

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    protected static String body(Object o) {
        return Json.write(o);
    }
}
