package io.github.lorencmarkja.tradelens.scenario;

import io.github.lorencmarkja.tradelens.scenario.ScenarioViews.ScenarioResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ScenarioController {

    private final ScenarioService scenario;
    private final DemoSeed seed;

    public ScenarioController(ScenarioService scenario, DemoSeed seed) {
        this.scenario = scenario;
        this.seed = seed;
    }

    @GetMapping("/scenario")
    public ScenarioResponse get() {
        return scenario.view();
    }

    @PostMapping("/demo/reset")
    public ScenarioResponse reset() {
        seed.reset();
        return scenario.view();
    }
}
