package com.logistics.backend.furniture.controller;

import com.logistics.backend.furniture.model.ScriptConfig;
import com.logistics.backend.furniture.service.ScriptConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 话术后台管理接口：业务方可通过此接口增删改查话术，无需开发介入。
 */
@RestController
@RequestMapping("/api/furniture/scripts")
@CrossOrigin(origins = "*")
public class ScriptController {

    @Autowired
    private ScriptConfigService scriptService;

    @GetMapping
    public List<ScriptConfig> list() {
        return scriptService.listAll();
    }

    @GetMapping("/{key}")
    public ScriptConfig get(@PathVariable String key) {
        return scriptService.getScript(key);
    }

    @PostMapping
    public ScriptConfig add(@RequestBody ScriptConfig config) {
        return scriptService.addScript(config);
    }

    @PutMapping("/{key}")
    public ScriptConfig update(@PathVariable String key, @RequestBody ScriptConfig config) {
        return scriptService.updateScript(key, config.getContent());
    }

    @DeleteMapping("/{key}")
    public void delete(@PathVariable String key) {
        scriptService.deleteScript(key);
    }
}
