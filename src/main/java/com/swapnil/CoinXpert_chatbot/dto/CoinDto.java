package com.swapnil.CoinXpert_chatbot.dto;

import lombok.Data;

import java.util.Date;


@Data
public class CoinDto {
    private String id;
    private String name;
    private String symbol;
    private String image;
    private double currentPrice;
    private double marketCap;
    private long marketCapRank;
    private double totalVolume;
    private double high24h;
    private double low24h;
    private double priceChange24h;
    private double priceChangePercentage24h;
    private double marketCapChange24h;
    private double marketCapChangePercentage24h;
    private double circulatingSupply;
    private double totalSupply;
    private double ath;
    private double athChangePercentage;
    private Date athDate;
    private double atl;
    private double atlChangePercentage;
    private Date atlDate;
    private Date lastUpdated;
}
