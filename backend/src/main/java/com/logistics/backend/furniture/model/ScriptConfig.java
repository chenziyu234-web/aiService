package com.logistics.backend.furniture.model;

public class ScriptConfig {

    private String key;
    private String content;
    private String category;

    public ScriptConfig() {}

    public ScriptConfig(String key, String content, String category) {
        this.key = key;
        this.content = content;
        this.category = category;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
