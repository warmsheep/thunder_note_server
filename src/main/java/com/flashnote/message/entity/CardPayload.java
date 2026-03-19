package com.flashnote.message.entity;

import java.util.List;

public class CardPayload {
    private String cardType; // MEDIA_TEXT or MESSAGE_COLLECTION
    private String title;
    private String summary;
    private List<CardItem> items;

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<CardItem> getItems() {
        return items;
    }

    public void setItems(List<CardItem> items) {
        this.items = items;
    }
}
