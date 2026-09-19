package com.swapnil.CoinXpert_chatbot.controller;


import com.swapnil.CoinXpert_chatbot.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping
    public ResponseEntity<ApiResponse> homeController(){

        ApiResponse apiResponse=new ApiResponse();
        apiResponse.setMessage("Welcome to chatbot");
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }
}
