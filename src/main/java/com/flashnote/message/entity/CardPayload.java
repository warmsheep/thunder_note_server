package com.flashnote.message.entity;

import lombok.Data;

import java.util.List;

@Data
public class CardPayload {
    private String cardType;
    private String title;
    private String summary;
    private List<CardItem> items;
}
