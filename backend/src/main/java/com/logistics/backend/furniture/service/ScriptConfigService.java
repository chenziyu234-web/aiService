package com.logistics.backend.furniture.service;

import com.logistics.backend.furniture.model.ScriptConfig;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 话术配置管理：支持后台增删改查，无需修改代码即可调整话术内容。
 */
@Service
public class ScriptConfigService {

    private final Map<String, ScriptConfig> scripts = new ConcurrentHashMap<>();

    public ScriptConfigService() {
        init("welcome", "引导话术",
                "嗨～我是您的家具物流小助手 😊 把收货地址发我（省市区+详细门牌就行），我帮您查超区费，特别快 📦");
        init("guide_address", "引导话术",
                "好哒～再麻烦您补一下完整地址哦 📝 需要「省市区县」加上「街道/小区/门牌」这一串，我才能帮您算得准 📍");
        init("result_surcharge", "结果话术",
                "查好啦～您的地址超出免费配送范围，需要加收超区费 {amount} 元 💡 {rule} 这是家具配送的超区专项费用，辛苦您理解一下，有任何疑问随时喊我～");
        init("result_no_surcharge", "结果话术",
                "太棒了 ✅ 您的地址在免费配送范围内，不用付超区费，家具可以正常安排配送～");
        init("address_unclear", "异常话术",
                "这个地址我还对不太上 😅 麻烦您再发一次「省市区县 + 街道/小区/门牌」这样完整的，我帮您精准查～");
        init("system_busy", "异常话术",
                "哎呀，系统这会儿有点忙 ⏳ 您稍等几分钟再试，或者先联系人工客服也行，别耽误您的事～");
        init("fallback", "兜底话术",
                "这块我帮您记不下啦～超区费以外的问题，建议直接找人工客服，他们会更专业地帮您 💬");
    }

    private void init(String key, String category, String content) {
        scripts.put(key, new ScriptConfig(key, content, category));
    }

    public String getContent(String key) {
        ScriptConfig config = scripts.get(key);
        return config != null ? config.getContent() : "";
    }

    public ScriptConfig getScript(String key) {
        return scripts.get(key);
    }

    public List<ScriptConfig> listAll() {
        return new ArrayList<>(scripts.values());
    }

    public ScriptConfig updateScript(String key, String newContent) {
        ScriptConfig existing = scripts.get(key);
        if (existing != null) {
            existing.setContent(newContent);
        }


        return existing;
    }

    public ScriptConfig addScript(ScriptConfig config) {
        scripts.put(config.getKey(), config);
        return config;
    }

    public void deleteScript(String key) {
        scripts.remove(key);
    }
}
