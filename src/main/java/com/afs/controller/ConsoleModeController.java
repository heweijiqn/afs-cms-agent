package com.afs.controller;

import com.afs.controller.filter.NoAuth;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConsoleModeController {
    private final Environment environment;

    public ConsoleModeController(Environment environment) {
        this.environment = environment;
    }

    @NoAuth
    @GetMapping("/cms/console/bootstrap")
    public Map<String, Object> bootstrap() {
        boolean local = environment.acceptsProfiles(Profiles.of("local-cms"));
        return Map.of("mode", "cms", "localCms", local, "hasArchiveData", local, "hasLogReport", local,
                "unifiedApi", true);
    }
}

