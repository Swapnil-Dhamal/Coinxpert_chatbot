package com.swapnil.CoinXpert_chatbot.service;

import com.swapnil.CoinXpert_chatbot.dto.CoinDto;

public interface ChatbotService {

    // Returns the final natural-language chatbot answer (after any CoinGecko lookup).
    String getCoinDetails(String prompt);

    String simpleChat(String prompt);

    CoinDto makeApiRequest(String currencyName);
}