package com.swapnil.CoinXpert_chatbot.controller;

import com.swapnil.CoinXpert_chatbot.dto.Prompt;
import com.swapnil.CoinXpert_chatbot.response.ApiResponse;
import com.swapnil.CoinXpert_chatbot.service.ChatbotService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/chat")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/simple")
    public ResponseEntity<String> simpleChatHandler(@RequestBody Prompt prompt) {

        System.out.println("Prompt: "+prompt);
        String response = chatbotService.simpleChat(prompt.getPrompt());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse> getCoinDetails(@RequestBody Prompt prompt) {

        System.out.println("Prompt: "+prompt);
        String answer = chatbotService.getCoinDetails(prompt.getPrompt());

        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setMessage(answer);
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }
}