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
                "您好，我是家具物流专属智能客服，您可以直接发送收货地址，我帮您一键查询超区费哦~");
        init("guide_address", "引导话术",
                "麻烦您提供一下完整的收货地址（省市区+详细地址），我精准帮您核算超区费~");
        init("result_surcharge", "结果话术",
                "经查询，您的收货地址超出免费配送范围，需支付超区费{amount}元。{rule}，该费用为家具配送超区专项费用，感谢您的理解~");
        init("result_no_surcharge", "结果话术",
                "您的地址在正常配送范围内，无需支付超区费，正常安排家具配送即可~");
        init("address_unclear", "异常话术",
                "您输入的地址不够详细哦，请补充省市区+具体街道/小区名称，我帮您精准查询超区费~");
        init("system_busy", "异常话术",
                "当前查询系统临时繁忙，您可以稍后再试，或直接联系人工客服咨询~");
        init("fallback", "兜底话术",
                "关于超区费以外的问题，您可以联系人工客服为您详细解答哦~");
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
