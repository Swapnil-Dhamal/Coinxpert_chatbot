package com.swapnil.CoinXpert_chatbot.service.serviceImpl;

import com.swapnil.CoinXpert_chatbot.dto.CoinDto;
import com.swapnil.CoinXpert_chatbot.service.ChatbotService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * NOTE: As of mid-2026 Google retired new-user access to the old
 * generateContent REST surface for several models (including 2.5 series)
 * and moved to the "Interactions API" (POST /v1beta2/interactions).
 * This class targets that new API. Docs: https://ai.google.dev/gemini-api/docs/interactions-overview
 */
@Service
public class ChatbotServiceImpl implements ChatbotService {

    @Value("${GEMINI_API_KEY}")
    private String geminiApiKey;

    @Value("${GEMINI_MODEL}")
    private static String aiModel;

    @Value("${GEMINI_API_URL}")
    private static String interactionUrl;

    private static final String GEMINI_MODEL = aiModel;
    private static final String INTERACTIONS_URL = interactionUrl;

    // Controls HOW every answer is formatted, regardless of which branch
    // (direct answer vs. function-call-backed answer) produces it.
    private static final String SYSTEM_INSTRUCTION = """
            You are CoinXpert, a crypto market data assistant embedded in a chat widget.

            Formatting rules (the client renders your output as Markdown):
            - Match the level of detail to what was actually asked.
              * A narrow question (e.g. "current price of bitcoin", "what's BTC's market cap")
                gets a short, direct answer: 1-3 sentences or a tiny bullet list with just the
                requested field(s). Do not dump the full stat sheet.
              * A broad/open question (e.g. "tell me about bitcoin", "price of bitcoin") gets a
                fuller breakdown: a one-line headline answer, then a bullet list of the other
                relevant stats (24h change, high/low, market cap, volume, ATH/ATL).
            - Use **bold** for the headline number(s) the user actually cares about.
            - Use bullet points (`* label: value`) for multi-field breakdowns; avoid tables unless
              comparing multiple coins side by side.
            - Use plain conversational sentences for anything that isn't market data (greetings,
              clarifying questions, error explanations).
            - Never invent numbers. If a field is missing from the data you were given, omit it
              rather than guessing.
            - Keep currency values in USD with a $ prefix and thousands separators unless the user
              asked for another currency.
            """;

    private final RestTemplate restTemplate = new RestTemplate();

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Full chatbot flow using the Interactions API:
     * 1. Send the prompt + a declared "getCoinDetails" function tool.
     * 2. If the response contains a function_call step, run makeApiRequest ourselves.
     * 3. Send a function_result back, referencing the same interaction via previous_interaction_id.
     * 4. Return the resulting model_output text.
     * 5. If no function_call was made, just return whatever text step came back.
     */
    @Override
    public String getCoinDetails(String prompt) {
        JSONObject tool = buildFunctionTool();
        JSONObject firstRequest = new JSONObject()
                .put("model", GEMINI_MODEL)
                .put("system_instruction", SYSTEM_INSTRUCTION)
                .put("input", prompt)
                .put("tools", new JSONArray().put(tool));

        JSONObject firstResponse = callGemini(firstRequest);
        String interactionId = firstResponse.optString("id", null);

        JSONObject functionCallStep = findStepByType(firstResponse, "function_call");

        // Case 1: model wants to call our function
        if (functionCallStep != null) {
            String callId = functionCallStep.optString("id", null);
            String functionName = functionCallStep.optString("name", "getCoinDetails");
            JSONObject args = functionCallStep.optJSONObject("arguments");
            String currencyName = args != null ? args.optString("currencyName", null) : null;

            if (currencyName == null || currencyName.isBlank()) {
                return "I couldn't figure out which coin you meant. Could you name it directly (e.g. bitcoin, ethereum)?";
            }

            CoinDto coinDto = makeApiRequest(currencyName);
            if (coinDto == null || coinDto.getId() == null) {
                return "I couldn't find market data for \"" + currencyName + "\". Double check the coin name/id.";
            }

            JSONObject functionResultPart = new JSONObject()
                    .put("type", "function_result")
                    .put("name", functionName)
                    .put("call_id", callId)
                    .put("result", new JSONArray().put(new JSONObject()
                            .put("type", "text")
                            .put("text", coinDtoToJson(coinDto).toString())));

            JSONObject followUpRequest = new JSONObject()
                    .put("model", GEMINI_MODEL)
                    .put("system_instruction", SYSTEM_INSTRUCTION)
                    .put("previous_interaction_id", interactionId)
                    .put("tools", new JSONArray().put(tool))
                    .put("input", new JSONArray().put(functionResultPart));

            JSONObject secondResponse = callGemini(followUpRequest);
            String finalText = extractModelOutputText(secondResponse);
            return finalText != null ? finalText : "Here's the data, but I had trouble phrasing a response.";
        }

        // Case 2: model answered directly (no coin lookup needed)
        String text = extractModelOutputText(firstResponse);
        return text != null ? text : "Sorry, I didn't understand that.";
    }

    @Override
    public String simpleChat(String prompt) {
        JSONObject request = new JSONObject()
                .put("model", GEMINI_MODEL)
                .put("system_instruction", SYSTEM_INSTRUCTION)
                .put("input", prompt);

        JSONObject response = callGemini(request);
        String text = extractModelOutputText(response);
        return text != null ? text : "Sorry, I couldn't generate a response.";
    }

    @Override
    @SuppressWarnings("unchecked")
    public CoinDto makeApiRequest(String currencyName) {
        String url = "https://api.coingecko.com/api/v3/coins/" + currencyName;

        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> responseEntity;
        try {
            responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        } catch (Exception e) {
            return null; // e.g. 404 for an unknown coin id
        }
        Map<String, Object> responseBody = responseEntity.getBody();

        if (responseBody == null) {
            return null;
        }

        CoinDto coinDto = new CoinDto();
        coinDto.setId((String) responseBody.get("id"));
        coinDto.setName((String) responseBody.get("name"));
        coinDto.setSymbol((String) responseBody.get("symbol"));

        Map<String, Object> image = (Map<String, Object>) responseBody.get("image");
        if (image != null) {
            coinDto.setImage((String) image.get("large"));
        }

        Object rank = responseBody.get("market_cap_rank");
        if (rank != null) {
            coinDto.setMarketCapRank(((Number) rank).longValue());
        }

        Map<String, Object> marketData = (Map<String, Object>) responseBody.get("market_data");
        if (marketData != null) {
            coinDto.setCurrentPrice(usd(marketData, "current_price"));
            coinDto.setMarketCap(usd(marketData, "market_cap"));
            coinDto.setTotalVolume(usd(marketData, "total_volume"));
            coinDto.setHigh24h(usd(marketData, "high_24h"));
            coinDto.setLow24h(usd(marketData, "low_24h"));
            coinDto.setPriceChange24h(num(marketData, "price_change_24h"));
            coinDto.setPriceChangePercentage24h(num(marketData, "price_change_percentage_24h"));
            coinDto.setMarketCapChange24h(num(marketData, "market_cap_change_24h"));
            coinDto.setMarketCapChangePercentage24h(num(marketData, "market_cap_change_percentage_24h"));
            coinDto.setCirculatingSupply(num(marketData, "circulating_supply"));
            coinDto.setTotalSupply(num(marketData, "total_supply"));
            coinDto.setAth(usd(marketData, "ath"));
            coinDto.setAthChangePercentage(usd(marketData, "ath_change_percentage"));
            coinDto.setAtl(usd(marketData, "atl"));
            coinDto.setAtlChangePercentage(usd(marketData, "atl_change_percentage"));
            coinDto.setAthDate(parseDate(usdString(marketData, "ath_date")));
            coinDto.setAtlDate(parseDate(usdString(marketData, "atl_date")));
        }

        coinDto.setLastUpdated(parseDate((String) responseBody.get("last_updated")));

        return coinDto;
    }

    // ---------------------------------------------------------------
    // Gemini Interactions API helpers
    // ---------------------------------------------------------------

    private JSONObject callGemini(JSONObject requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiApiKey);

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody.toString(), headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity(INTERACTIONS_URL, requestEntity, String.class);

        return new JSONObject(response.getBody());
    }

    private JSONObject buildFunctionTool() {
        return new JSONObject()
                .put("type", "function")
                .put("name", "getCoinDetails")
                .put("description",
                        "Get current price, market cap, 24h high/low, all-time-high/low and other "
                                + "market data for a cryptocurrency, looked up by its CoinGecko id "
                                + "(e.g. 'bitcoin', 'ethereum', 'solana'). Call this whenever the user "
                                + "asks about a specific coin's price or market stats.")
                .put("parameters", new JSONObject()
                        .put("type", "object")
                        .put("properties", new JSONObject()
                                .put("currencyName", new JSONObject()
                                        .put("type", "string")
                                        .put("description",
                                                "The CoinGecko id of the cryptocurrency, e.g. 'bitcoin', 'ethereum'")))
                        .put("required", new JSONArray().put("currencyName")));
    }

    /** Finds the first step of the given type in the response's "steps" array. */
    private JSONObject findStepByType(JSONObject geminiResponse, String type) {
        JSONArray steps = geminiResponse.optJSONArray("steps");
        if (steps == null) return null;
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.getJSONObject(i);
            if (type.equals(step.optString("type"))) {
                return step;
            }
        }
        return null;
    }

    /** Extracts the text from the "model_output" step's content array. */
    private String extractModelOutputText(JSONObject geminiResponse) {
        JSONObject modelOutput = findStepByType(geminiResponse, "model_output");
        if (modelOutput == null) return null;

        JSONArray content = modelOutput.optJSONArray("content");
        if (content == null) return null;

        for (int i = 0; i < content.length(); i++) {
            JSONObject part = content.getJSONObject(i);
            if ("text".equals(part.optString("type"))) {
                return part.optString("text", null);
            }
        }
        return null;
    }

    private JSONObject coinDtoToJson(CoinDto coinDto) {
        return new JSONObject()
                .put("id", coinDto.getId())
                .put("name", coinDto.getName())
                .put("symbol", coinDto.getSymbol())
                .put("marketCapRank", coinDto.getMarketCapRank())
                .put("currentPriceUsd", coinDto.getCurrentPrice())
                .put("marketCapUsd", coinDto.getMarketCap())
                .put("totalVolumeUsd", coinDto.getTotalVolume())
                .put("high24hUsd", coinDto.getHigh24h())
                .put("low24hUsd", coinDto.getLow24h())
                .put("priceChangePercentage24h", coinDto.getPriceChangePercentage24h())
                .put("athUsd", coinDto.getAth())
                .put("atlUsd", coinDto.getAtl());
    }

    // ---------------------------------------------------------------
    // CoinGecko parsing helpers
    // ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private double usd(Map<String, Object> marketData, String key) {
        Map<String, Object> field = (Map<String, Object>) marketData.get(key);
        return (field == null || field.get("usd") == null) ? 0.0 : ((Number) field.get("usd")).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private String usdString(Map<String, Object> marketData, String key) {
        Map<String, Object> field = (Map<String, Object>) marketData.get(key);
        return (field == null) ? null : (String) field.get("usd");
    }

    private double num(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? 0.0 : ((Number) value).doubleValue();
    }

    private Date parseDate(String isoDate) {
        if (isoDate == null) return null;
        try {
            return Date.from(Instant.parse(isoDate));
        } catch (Exception e) {
            return null;
        }
    }
}