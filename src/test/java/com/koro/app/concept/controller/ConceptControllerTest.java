package com.koro.app.concept.controller;

import com.koro.app.concept.dto.ConceptDetails;
import com.koro.app.concept.service.ConceptReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ConceptControllerTest {
    @Mock
    private ConceptReadService conceptReadService;
    @InjectMocks
    private ConceptController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void returnsExistingJsonContract() throws Exception {
        when(conceptReadService.findById("concept-1")).thenReturn(Optional.of(new ConceptDetails(
                "concept-1", "where", null,
                new ConceptDetails.CategoryDetails("category-1", "Daily Life", "Everyday words"), null)));

        mvc.perform(get("/api/v1/concepts/concept-1"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id":"concept-1","name":"where","description":null,
                         "category":{"id":"category-1","name":"Daily Life","description":"Everyday words"},
                         "referenceImage":null}
                        """));
    }

    @Test
    void missingConceptStillReturns404() throws Exception {
        when(conceptReadService.findById("missing")).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/concepts/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }
}
