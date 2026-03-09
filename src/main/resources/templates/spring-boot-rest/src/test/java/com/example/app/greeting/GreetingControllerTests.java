package com.example.app.greeting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GreetingController.class)
class GreetingControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void greetingDefaultName() throws Exception {
        mockMvc.perform(get("/greeting"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("Hello, World!"));
    }

    @Test
    void greetingWithName() throws Exception {
        mockMvc.perform(get("/greeting").param("name", "Spring"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Hello, Spring!")));
    }

}
